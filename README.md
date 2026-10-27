# EllanAchievements

艾尔岚服务器的玩法成就插件：把**森罗厨房、森罗酒馆、酿酒、垂钓、农耕**五类玩法变成原版成就，显示在原版
`L` 键成就界面里，图标可以直接使用 CraftEngine 物品（含森罗物品）。

- 运行环境：Paper / Leaf `26.2`，Java 25。
- 设计规格：[`docs/superpowers/specs/2026-10-26-ellan-achievements-design.md`](docs/superpowers/specs/2026-10-26-ellan-achievements-design.md)
- 加成就只改 YAML，不改代码；任何一个玩法插件升级或缺失，只影响它自己的成就。

## 玩法对接（`/ea status` 中的状态）

| 对接 | 依赖插件 | 触发类型 | 说明 |
|---|---|---|---|
| `generic` | 无 | `item_obtain`、`item_kill` | 拾取或从任何容器 / 合成 / 熔炉取出物品；用某物品击杀 |
| `fishing` | CustomFishing | `fish_catch` | 公开 API 事件 |
| `farming` | CustomCrops | `crop_plant`、`crop_harvest`、`crop_quality` | 公开 API 事件；只算玩家亲手收获的成熟作物 |
| `cooking` | KaleidoscopeCookeryPlugin | `dish_cook`、`cooking_stir_fry`、`cooking_grind`、`cooking_sickle`、`cooking_action` | 公开 API 事件；`cooking_action` 需要带 `KaleidoscopeGameplayEvent` 的版本，旧版本只缺这一项 |
| `tavern` | KaleidoscopeTavern | `tavern_press`、`tavern_shake`、`tavern_tap`、`tavern_drink` | 需要 [KaleidoscopeTavern#73](https://github.com/EllanStudio/KaleidoscopeTavern/pull/73) 之后的版本 |
| `brewing` | TheBrewingProject | `brew_obtain`、`brew_drink` | 反射访问 API；"获得"只计自己参与酿造的酒，同一瓶只计一次 |

状态含义：`enabled` 正常；`not installed` 该服没有装对应插件；`incompatible: …` 插件版本的接口变了，
只停用这一项，其他照常。烹饪、酒馆、酿酒没有发布到 Maven，是按类名绑定事件的，所以它们接口变化时也只会
显示 `incompatible`，不会让整个插件加载失败。

## 成就配置

文件放在 `config/achievements/`（仓库内的权威版本），首次启动时复制到 `plugins/EllanAchievements/achievements/`。
每个文件是 L 界面里的一个分页。

```yaml
tab:
  id: fishing                         # 分页 id，永不更改
  title: "垂钓"
  description: "与鱼相伴的日子"
  icon: { item: "customfishing:gold_fish" }   # 原版物品或任意 CraftEngine 物品
  background: "minecraft:block/prismarine"

achievements:
  hundred_fish:
    parent: first_catch               # 同分页内的成就 id；不写则挂在分页根下
    title: "百尾渔夫"
    description: "累计钓上 100 条鱼"
    icon: { item: "customfishing:tuna_fish" }
    frame: task                       # task | goal | challenge
    hidden: false                     # 完成前是否隐藏
    announce: true                    # 聊天公告
    toast: true                       # 右上角弹窗
    trigger: fish_catch
    filter:
      group: ["river", "lake"]        # 列表 = 任一匹配；不同属性之间 = 同时满足
      size: ">=50"                    # 数值属性支持 > >= < <= ==
    goal: 100                         # 默认 1；≤100 时 L 界面显示真实进度
    count: total                      # total（累计）| unique（按属性去重）
    unique-by: loot                   # count: unique 时必填
    # unique-groups:                  # 可选：几个值算同一组，按组去重
    #   hat: ["kaleidoscopecookery:straw_hat", "kaleidoscopecookery:straw_hat_flower"]
    rewards:
      exp: 50
      coins: 500                      # ExcellentEconomy 的 ellan_coin
      items: ["kaleidoscopecookery:fish_rice*2", "minecraft:diamond"]
      commands: ["lp user {player} permission set ellan.title.angler"]
    disabled: false                   # true 时不注册，保留玩家记录
```

各触发类型可用的筛选属性：

| trigger | 属性 |
|---|---|
| `fish_catch` | `loot`、`group`、`type`、`size`（数值）、`new_record` |
| `crop_plant` | `crop` |
| `crop_harvest` | `crop`、`season`（spring/summer/autumn/winter） |
| `crop_quality` | `item`、`quality`（数值，第几档品质） |
| `dish_cook` | `dish`、`station`（pot/stockpot/steamer/shawarma） |
| `cooking_stir_fry` | `count`（数值） |
| `cooking_grind` | `product` |
| `cooking_sickle` | `block` |
| `cooking_action` | `action`（如 `stir_fry_in_pot`、`lit_the_stove`） |
| `tavern_press` / `tavern_shake` / `tavern_tap` | `recipe`、`product` |
| `tavern_drink` | `drink` |
| `brew_obtain` / `brew_drink` | `recipe`、`quality`（bad/good/excellent）、`score`（数值） |
| `item_obtain` | `item` |
| `item_kill` | `weapon`、`entity` |

### 必须遵守的规则

- **成就 id（`分页id/成就id`）上线后永不改名、永不复用。** 要下架就写 `disabled: true`。
- **三台服务器（survival、redstone、spawn）的成就配置必须完全一致**，否则 SparrowSync 同步的进度会对不上。
  启动和 `/ea reload` 时日志会打印 `config sha256=…`，部署后核对三台一致。
- `/ea reload` 会校验配置：语法、字段、父子关系、触发属性错误的成就会被**跳过**（日志里写明文件 / 成就 / 字段）；
  物品、鱼、作物、酒配方 id 找不到只是**警告**，成就照常注册。上线前确保没有警告。

### 烹饪成就

`config/achievements/cooking.yml` 的前 33 个成就由 `tools/migrate_kaleidoscope.py` 从烹饪插件自带的成就定义
（`tools/kaleidoscope.json`）迁移而来（氮气系列 4 个因插件没有该玩法而不迁移），之后**手工维护**，
不要再运行迁移脚本覆盖。烹饪插件里那套依赖 UltimateAdvancementAPI 的成就**不要启用**（不要装 UltimateAdvancementAPI），否则会重复。

### 写成就文案

标题、描述里提到的作物、鱼、菜、酒，一律用游戏里的实际名称（CraftEngine 的中文翻译、TheBrewingProject 配方名的中间档）。
例如 `redpacket` 作物叫"摇钱树"，`raspberry` 叫"覆盆子"，`shawarma_spit` 叫"旋风烤肉塔"。

## 进度与奖励

- 计数存在玩家自定义数据（PDC）`ellanachievements:progress`，完成状态就是原版成就本身；两者都由 SparrowSync
  跨服同步，不需要额外数据库。
- 奖励只在原版"成就完成"时发放一次（包括管理员 `/advancement grant`），发放前先记账，切服、重启、重置都不会重复发。
- 玩家进度数据损坏或版本不认识时，原数据保留不覆盖，本次会话暂停该玩家的成就，并提醒在线管理员。

## 管理命令

权限 `ellanachievements.admin`（默认 OP）。

| 命令 | 作用 |
|---|---|
| `/ea status` | 对接状态、已加载成就数、配置哈希 |
| `/ea reload` | 重新校验配置并刷新成就（会触发一次数据包重载） |
| `/ea progress <玩家> [成就]` | 查看计数、去重集合、完成与领奖状态 |
| `/ea set <玩家> <成就> <计数>` | 设置计数并对齐原版进度 |
| `/ea reset <玩家> <成就>` | 清除计数并撤销成就；领奖记录保留（再次完成不发奖） |

`set`、`reset` 写入 `plugins/EllanAchievements/audit.log`。

## 构建

```bash
./gradlew clean check shadowJar     # 产物：paper/build/libs/EllanAchievements-<版本>.jar
```

`core` 是纯 Java（配置、规则、进度、成就 JSON），不依赖 Bukkit，全部有单元测试；`paper` 是 Paper 层与各玩法对接。
线上只部署 CI 构建的 jar，不要在服务器上直接改 jar。

## 部署

1. 先更新玩法插件：带成就事件的森罗酒馆（#73 之后）；烹饪插件如需 `cooking_action`，需带 `KaleidoscopeGameplayEvent`。
2. 三台服放同一个 `EllanAchievements-<版本>.jar`，`plugins/EllanAchievements/achievements/` 放同一份 `config/achievements/*.yml`。
3. 重启后检查日志：`Adapters: {…}` 全部为 `enabled`（或确实没装的 `not installed`），`0 errors, 0 warnings`，三台的
   `config sha256` 相同。
