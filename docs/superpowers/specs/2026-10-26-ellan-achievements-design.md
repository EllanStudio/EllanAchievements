# EllanAchievements 设计规格

- 日期：2026-10-26
- 状态：待审阅
- 仓库：`EllanStudio/EllanAchievements`（公开）

## 1. 目标

为艾尔岚服务器的五类玩法增加成就，并显示在原版 L 键成就界面中：

| 分页 | 玩法插件 | 插件归属 |
|---|---|---|
| 森罗厨房 | KaleidoscopeCookery（`KaleidoscopeCookeryPlugin`） | 上游 `Nicoppara/KaleidoscopeCookery_Plugin` 的 fork |
| 森罗酒馆 | KaleidoscopeTavern | EllanStudio 自有（`src/paper` 为自有 Paper 移植层） |
| 酿酒 | TheBrewingProject | 第三方 |
| 垂钓 | CustomFishing | 第三方 |
| 农耕 | CustomCrops | 第三方 |

成功标准：

1. 玩家在 survival、redstone、spawn 任一服完成玩法动作，相应成就进度推进；完成后原版弹窗、聊天公告、奖励发放各一次。
2. 成就在原版 L 界面中按分页显示，图标可使用任意 CraftEngine 物品（含森罗物品）。
3. 进度与完成状态跨服一致。
4. 运营人员只改 YAML 即可新增、调整、下架成就，无需改代码。
5. 任何玩法插件升级或缺失，只影响该玩法的成就，不影响其他玩法，也不影响玩法本身。

## 2. 可持续性约束

这是本项目的首要约束，所有设计决策服从以下规则：

1. **第三方插件零改动**：TheBrewingProject、CustomFishing、CustomCrops 只使用其公开 API 事件。
2. **上游 fork 最小改动**：KaleidoscopeCookery 只增加一个通用事件（见 §8.1），并向上游提 PR；合并后 fork 与上游无差异。烹饪插件中依赖 UltimateAdvancementAPI 的成就代码保持原样不动，服务器不安装 UltimateAdvancementAPI，使其保持休眠。
3. **自有项目正式加接口**：KaleidoscopeTavern 只在 `src/paper` 中增加事件，不改 `src/main`（原 mod 源码）。
4. **只依赖公开、稳定的 API**：成就注册使用 Bukkit API，不使用 NMS、不依赖 UltimateAdvancementAPI 等发包库。
5. **源码与配置以 Git 为准**：线上 jar 只部署 CI 构建产物，禁止在服务器上直接修改 jar；成就配置也在仓库内版本管理。

## 3. 总体架构

```
 对接层 adapters（每个玩法一个，互相独立）      核心 core（不依赖任何玩法插件）
 ┌──────────────────────────────┐
 │ cooking   KaleidoscopeCookery  │──┐
 │ tavern    KaleidoscopeTavern   │  │  GameplayEvent
 │ brewing   TheBrewingProject    │──┼─▶ (player, trigger, attributes, amount)
 │ fishing   CustomFishing        │  │        │
 │ farming   CustomCrops          │  │        ▼
 │ generic   原版事件 + CE 物品     │──┘   rules：匹配 filter、计数
 └──────────────────────────────┘          │
                                            ▼
                         progress：玩家 PDC 计数 → 授予原版成就进度步数
                                            │
                                            ▼
                         display：生成并注册原版成就（含 CE 图标）；完成后发奖励
```

### 3.1 模块与职责

| 模块 | 职责 | 依赖 |
|---|---|---|
| `core.event` | `GameplayEvent` 数据结构与分发总线 | 无 |
| `core.config` | 解析、校验成就 YAML，产出不可变的成就定义 | 无（校验用的外部 ID 查询通过接口注入） |
| `core.rules` | 将 `GameplayEvent` 匹配到成就并计算新计数 | `core.event`、`core.config` |
| `core.progress` | 玩家进度模型（计数、去重集合、已领奖集合）及其 JSON 编解码 | 无 |
| `paper.storage` | 进度在玩家 PDC 中的读写 | Paper API |
| `paper.display` | 生成成就 JSON、注册/注销、授予进度步数、刷新在线玩家 | Paper API、`paper.icon` |
| `paper.icon` | 将配置的图标解析为物品组件（原版或 CraftEngine） | CraftEngine API（可选） |
| `paper.reward` | 发放经验、金币、物品、命令奖励；防重复 | Paper API、ExcellentEconomy（可选）、CraftEngine（可选） |
| `paper.adapter.*` | 各玩法对接层，把外部事件翻译成 `GameplayEvent` | 各自的玩法插件（全部 softdepend） |
| `paper.command` | `/ea` 管理命令 | Paper API |

`core.*` 为纯 Java，不引用 Bukkit，可完整单元测试。

### 3.2 技术栈

- Paper 26.2，Java 25，Gradle（Kotlin DSL），GitHub Actions 构建。
- `plugin.yml` 中所有玩法插件、CraftEngine、ExcellentEconomy 均为 `softdepend`。

## 4. 玩法事件（GameplayEvent）

`GameplayEvent(player, trigger, attributes: Map<String, Object>, amount: long)`。`amount` 默认为 1。

### 4.1 触发类型与属性

| trigger | 来源 | 属性 |
|---|---|---|
| `fish_catch` | CustomFishing `FishingResultEvent`（仅 `Result.SUCCESS`） | `loot`（战利品 ID）、`group`（战利品分组，列表）、`type`（`item`/`entity`/`block`）、`size`（数值，可缺省）、`new_record`（布尔）；`amount` = `getAmount()` |
| `crop_plant` | CustomCrops `CropPlantEvent` | `crop`（作物 ID） |
| `crop_harvest` | CustomCrops `CropBreakEvent`，仅玩家破坏且为最终生长阶段 | `crop`、`season`（取自 CustomCrops 世界季节，取不到时缺省） |
| `crop_quality` | CustomCrops `QualityCropActionEvent`，仅当上下文中有玩家 | `item`（品质作物物品 ID）、`quality`（品质等级，1 起，按 `qualityCrops` 中的位置） |
| `dish_cook` | 烹饪 `PotExtractDishEvent`、`StockpotExtractDishEvent`、`SteamerBreakFullEvent`、`ShawarmaExtractEvent` | `dish`（产物 CE 物品 ID）、`station`（`pot`/`stockpot`/`steamer`/`shawarma`）；蒸笼每个产物一个事件 |
| `cooking_stir_fry` | 烹饪 `PotStirFryEvent` | `count`（本次翻炒序号） |
| `cooking_grind` | 烹饪 `MillstoneGrindCompleteEvent` | `product`（产物物品 ID） |
| `cooking_sickle` | 烹饪 `SickleHarvestEvent` | `block`（方块类型） |
| `cooking_action` | 烹饪 `KaleidoscopeGameplayEvent`（§8.1 新增） | `action`（动作名，如 `stir_fry_in_pot`） |
| `tavern_press` | 酒馆 `TavernPressEvent`（§8.2 新增） | `recipe`、`product` |
| `tavern_shake` | 酒馆 `TavernShakeEvent` | `recipe`、`product` |
| `tavern_tap` | 酒馆 `TavernBarrelTapEvent` | `recipe`、`product` |
| `tavern_drink` | 酒馆 `TavernDrinkEvent` | `drink`（饮品物品 ID） |
| `brew_obtain` | 玩家背包获得 TheBrewingProject 的酒（拾取、从容器取出），且玩家在该酒的 `getBrewers()` 中 | `recipe`（最接近的配方 ID）、`quality`（`bad`/`good`/`excellent`）、`score`（数值） |
| `brew_drink` | TheBrewingProject `BrewConsumeEvent` | `recipe`、`quality`、`score` |
| `item_obtain` | 原版：拾取、合成、熔炼取出、从容器取出、背包点击放入 | `item`（物品 ID，CE 物品优先返回 CE ID） |
| `item_kill` | 原版 `EntityDeathEvent`，击杀者为玩家 | `weapon`（主手物品 ID）、`entity`（实体类型） |

说明：

- TheBrewingProject 没有"酿造完成"事件，`brew_obtain` 以"拿到自己参与酿造的酒"判定，同一瓶酒只计一次：计数时在该物品 PDC 写入标记 `ellanachievements:brew_counted`，带此标记的酒不再计数（因此在箱子间来回搬运不会重复计数）。写入标记可能影响该酒与未标记同款酒的堆叠，需在 test 服验证；若影响玩家体验，改为只在从 TheBrewingProject 的酒桶/蒸馏器界面取出时计数。
- `item_obtain` 用于迁移烹饪成就中的"持有物品"条件，同时也可用于任意 CE 物品成就。

## 5. 成就配置

### 5.1 文件布局

```
config/achievements/       # 仓库内的权威配置，部署时复制到 plugins/EllanAchievements/achievements/
  cooking.yml   tavern.yml   brewing.yml   fishing.yml   farming.yml
```

每个文件对应 L 界面中的一个分页。

### 5.2 格式

```yaml
tab:
  id: fishing                         # 分页 ID，永不更改
  title: "垂钓"
  description: "与鱼相伴的日子"
  icon: { item: "customfishing:golden_carp" }
  background: "minecraft:block/prismarine"

achievements:
  hundred_fish:
    parent: first_catch               # 同分页内的成就 ID；缺省时挂在分页根节点下
    title: "百尾渔夫"
    description: "累计钓上 100 条鱼"
    icon: { item: "customfishing:tuna" }
    frame: task                       # task | goal | challenge，默认 task
    hidden: false                     # 完成前是否隐藏，默认 false
    announce: true                    # 是否聊天公告，默认 true
    toast: true                       # 是否右上角弹窗，默认 true
    trigger: fish_catch
    filter:
      group: ["river", "lake"]        # 值为列表表示"任一匹配"
      size: ">=50"                    # 数值属性支持 >, >=, <, <=, ==
    goal: 100                         # 默认 1
    count: total                      # total（累计 amount）| unique（按属性去重）
    unique-by: loot                   # count: unique 时必填
    # unique-groups:                  # 可选：把多个属性值归为同一组，按组去重
    #   hat: ["kaleidoscopecookery:straw_hat", "kaleidoscopecookery:straw_hat_flower"]
    rewards:
      exp: 50
      coins: 500                      # ExcellentEconomy 的 ellan_coin
      items: ["kaleidoscopecookery:fish_rice*2", "minecraft:diamond*1"]
      commands: ["lp user {player} permission set ellan.title.angler"]
    disabled: false                   # true 时不注册，保留玩家记录
```

### 5.3 规则

- 成就完整 ID 为 `<tab.id>/<成就 id>`，对应原版成就键 `ellanachievements:<tab.id>/<成就 id>`。**上线后永不更改、永不复用。**
- `filter` 中各属性之间为"且"，同一属性的列表值为"或"。字符串比较不区分大小写。
- `unique` 计数以 `unique-by` 属性的值去重；属性缺省的事件不计入。
- 配置了 `unique-groups` 时，按"值所属的组名"去重：值不属于任何组的事件不计入；`goal` 通常等于组数。
- 图标 `item` 接受原版物品 ID 或 CraftEngine 物品 ID；无法解析时使用 `minecraft:barrier` 并报警。

### 5.4 加载时校验

在启动和 `/ea reload` 时执行，以下问题均报出"文件 / 成就 / 字段 / 原因"：

1. YAML 语法错误、必填字段缺失、`frame`/`count`/`trigger` 取值非法；
2. 成就 ID 或分页 ID 重复；`parent` 不存在、跨分页或形成循环；
3. `filter` 使用了该 `trigger` 不支持的属性；数值比较表达式非法；
4. 物品 ID（图标、奖励、`item`/`dish`/`weapon` 等过滤值）在原版与 CraftEngine 中均不存在；
5. CustomFishing 战利品 ID、CustomCrops 作物 ID、TheBrewingProject 配方 ID 在对应插件中不存在（对应插件未加载时跳过此项并提示）；
6. `trigger` 对应的对接层未启用（成就仍注册，但提示"当前无法推进"）。

第 1–3 类错误使该成就被跳过；其子成就随之跳过。第 4–6 类为警告，成就照常注册。

## 6. 进度、显示与奖励

### 6.1 进度存储

- 玩家 PDC 键 `ellanachievements:progress`，值为 JSON：

```json
{ "v": 1,
  "counters": { "fishing/hundred_fish": 37 },
  "uniques":  { "cooking/ten_dishes": ["kaleidoscopecookery:mapo_tofu"] },
  "rewarded": ["fishing/first_catch"] }
```

- 完成状态即原版成就状态。PDC 与原版成就均由 SparrowSync 跨服同步（已确认三服配置 `advancements: true`、`persistent-data: true`），本插件不另建数据库。
- 读到无法识别的 `v` 或损坏的 JSON：原值写入日志备份，本次会话按空进度处理且**不回写**，并向在线管理员报警。

### 6.2 原版成就生成

- 每个成就生成一份原版成就 JSON，条件全部为 `minecraft:impossible`，由插件授予。
- 进度步数：`steps = min(goal, 100)`；计数 `c` 对应应授予步数 `floor(c * steps / goal)`。`goal ≤ 100` 时显示真实数字，否则按百分之一粒度显示。
- 分页根节点为一个不可见进度的根成就（`tab` 配置生成），玩家首次进服即授予，使分页可见。
- 图标：原版物品直接写 `id`；CraftEngine 物品通过 CraftEngine API 构建物品，提取其 `item_model` 与 `custom_model_data` 组件写入 `components`。
- 注册使用 `Bukkit.getUnsafe().loadAdvancement`；启动与重载时先移除本命名空间下全部成就再注册，然后为在线玩家重新同步成就数据。
- 启动日志打印成就配置的 SHA-256，用于核对三服配置一致。

### 6.3 推进流程

1. 对接层发布 `GameplayEvent`（主线程）。
2. `rules` 找出 `trigger` 相同、未禁用、`filter` 匹配、且玩家尚未完成的成就，计算新计数。
3. `storage` 写回 PDC。
4. `display` 将原版进度补齐到应授予步数；最后一步授予时原版成就完成。

### 6.4 进服对齐

玩家进服时，对每个成就按当前 `goal` 重新计算应授予步数并补齐（只补不撤）。因此调低 `goal` 后已达标的玩家会直接完成。

### 6.5 奖励

- 唯一入口：监听 `PlayerAdvancementDoneEvent`，仅处理 `ellanachievements` 命名空间（含管理员 `/advancement grant`）。
- 发放前检查 `rewarded`，发放后立即写入；同一成就对同一玩家最多发放一次，跨服同步后亦然。
- 物品放不进背包时掉落在玩家脚下；金币通过 ExcellentEconomy API 存入 `ellan_coin`，ExcellentEconomy 不可用时跳过金币并记日志；命令以控制台执行，`{player}` 替换为玩家名。

## 7. 烹饪成就迁移

- 来源：烹饪插件 jar 内 `advancements/kaleidoscope.json`（38 个，源自原 mod `KaleidoscopeMods/KaleidoscopeCookery`）。
- 迁移 34 个到 `cooking.yml`：根成就 `root`（森罗厨房）成为分页 `tab` 定义（标题、描述、图标沿用），其余 33 个成为成就，ID、标题、描述、父子关系、边框保持一致；氮气系列 4 个（`nitrogen_100/300/1000/3000`，仅相互构成父子链）因插件未实现对应玩法，不迁移。
- 条件映射（原版语义：`requirements` 外层为"且"，内层为"或"）：

| 原条件类型 | 成就数 | 新 trigger |
|---|---|---|
| `event` | 20 | `cooking_action`，`filter.action` = 原事件名 |
| `inventory` | 12（共 17 个条件） | `item_obtain`；`requirements` 只有一组时 → `filter.item` = 该组全部物品，`goal: 1`（如 `dark_cuisine`）；有多组时 → `count: unique` + `unique-by: item` + `unique-groups`（每组一个组名）+ `goal` = 组数（如 `dual_chili` 为 2，`farmer_set` 为 4，其中草帽组含普通与带花两种） |
| `kill` | 2 | `item_kill`，`filter.weapon` = 原物品列表 |

- 迁移脚本放在仓库 `tools/` 下并提交，可重复执行以核对与原定义的一致性。

## 8. 外部项目改动

### 8.1 KaleidoscopeCookery（上游 fork）

- 唯一改动：在 `net.kaleidoscope.cookery.api.KaleidoscopeAdvancements#recordEvent(Player, String)` 中，在原有逻辑之外调用 `Bukkit.getPluginManager().callEvent(new KaleidoscopeGameplayEvent(player, eventName))`。
- 新增类 `net.kaleidoscope.cookery.api.event.KaleidoscopeGameplayEvent`（不可取消，字段：`player`、`action`）。
- 以独立提交合入 fork，并向 `Nicoppara/KaleidoscopeCookery_Plugin` 提 PR。
- 本插件对该事件按类名探测：不存在时 `cooking_action` 不可用，其余烹饪触发照常工作。

### 8.2 KaleidoscopeTavern（自有）

在 `src/paper` 下新增包 `com.github.ysbbbbbb.kaleidoscopetavern.paper.api.event`：

| 事件 | 触发时机 | 字段 |
|---|---|---|
| `TavernPressEvent` | 压榨桶产出液体 | `player`、`location`、`recipeId`、`product` |
| `TavernShakeEvent` | 调酒器完成调制 | `player`、`location`、`recipeId`、`product` |
| `TavernBarrelTapEvent` | 从酒桶取出饮品 | `player`、`location`、`recipeId`、`product` |
| `TavernDrinkEvent` | 玩家饮用酒馆饮品 | `player`、`drink` |

事件均不可取消，在对应 Service 的成功分支中触发，并在 `src/paperTest` 中补充测试。

## 9. 错误处理

- **对接层隔离**：每个对接层在 `onEnable` 中独立初始化并捕获 `Throwable`（含 `NoClassDefFoundError`、`NoSuchMethodError`）；失败只停用该层。启动日志输出对接状态表（启用/未安装/不兼容及原因）。
- **事件处理隔离**：对接层监听器捕获全部异常并记日志，绝不向玩法插件抛出；同一异常每分钟最多记一次堆栈。
- **配置错误**：见 §5.4。
- **进度数据损坏**：见 §6.1。

## 10. 管理命令

`/ea`，权限 `ellanachievements.admin`（默认 OP）。

| 命令 | 作用 |
|---|---|
| `/ea status` | 对接状态、已加载/跳过的成就数、配置哈希 |
| `/ea reload` | 重新校验并注册，刷新在线玩家 |
| `/ea progress <玩家> [成就]` | 查看计数、去重集合、完成与领奖状态 |
| `/ea set <玩家> <成就> <计数>` | 设置计数并对齐原版进度 |
| `/ea reset <玩家> <成就>` | 清除计数与去重集合并撤销原版成就；不清除 `rewarded` |

`set`、`reset` 写入 `plugins/EllanAchievements/audit.log`（时间、操作者、玩家、成就、前后值）。

## 11. 测试

- **core 单元测试**：filter 匹配（列表、数值比较、大小写）、total/unique 计数、步数换算、配置校验（重复 ID、缺失 parent、循环、非法属性）、进度 JSON 编解码与版本处理。
- **对接层测试**：以测试替身构造外部事件，断言产出的 `GameplayEvent`。
- **奖励测试**：重复完成、已领奖记录存在时不重复发放。
- **迁移测试**：迁移脚本输出与原 `kaleidoscope.json` 的 ID、父子关系一致。
- **上线前 test 服验收**：每个分页至少完成一个成就；检查 L 界面分页、森罗图标、进度条、弹窗与公告；在 survival 与 redstone 间切服确认进度一致；重复触发确认奖励只发一次。

## 12. 第一版范围

包含：

- 本插件全部模块与 `/ea` 命令；
- `cooking.yml`：迁移 34 个；
- `tavern.yml`、`brewing.yml`、`fishing.yml`、`farming.yml`：每个 5–10 个示例成就，供运营后续修改；
- §8 的两处外部改动及烹饪插件上游 PR；
- CI 构建与 README（含部署步骤）。

不包含：

- 排行榜、成就点数、GUI 菜单；
- 自定义成就界面或按玩家显示不同成就树；
- 氮气系列成就；
- 历史数据回溯（如按 CustomFishing 既有统计折算）。

## 13. 部署

1. 合并后由 CI 构建 jar。
2. 先部署烹饪插件 fork 与酒馆插件的新版本，再部署本插件；三服（survival、redstone、spawn）使用同一 jar 与同一份 `config/achievements/`。
3. 确认三服启动日志中的配置哈希一致。
