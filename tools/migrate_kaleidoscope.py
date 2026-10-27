"""Converts KaleidoscopeCookery's built-in advancements into config/achievements/cooking.yml (spec §7).

Usage: python3 -I tools/migrate_kaleidoscope.py [tools/kaleidoscope.json] [config/achievements/cooking.yml]

The source is advancements/kaleidoscope.json from the KaleidoscopeCookeryPlugin jar. Re-run after the cooking
plugin changes its advancements, then review the diff; hand edits to cooking.yml are overwritten.
"""
import json
import os
import sys

TAB_ID = "cooking"
BACKGROUND = "minecraft:block/spruce_planks"
# Nitrogen flight is not implemented by the plugin, so these can never be earned.
SKIPPED = {"nitrogen_100", "nitrogen_300", "nitrogen_1000", "nitrogen_3000"}
# Items the Paper port renamed or moved (checked against the server's CraftEngine resources, 2026-10-27):
# tomato, chili and lettuce come from CustomCrops; the shovel has oil/no-oil variants; tea seeds do not exist.
RENAMES = {
    "kaleidoscopecookery:tomato_seed": ["customcrops:tomato_seeds"],
    "kaleidoscopecookery:lettuce_seed": ["customcrops:lettuce_seeds"],
    "kaleidoscopecookery:chili_seed": ["customcrops:pepper_seeds"],
    "kaleidoscopecookery:red_chili": ["customcrops:pepper"],
    "kaleidoscopecookery:kitchen_shovel": ["kaleidoscopecookery:kitchen_shovel_no_oil",
                                           "kaleidoscopecookery:kitchen_shovel_has_oil"],
    "kaleidoscopecookery:tea_seed": [],
}


def _rename(item):
    return RENAMES.get(item, [item])


def _items(criteria, names):
    items = []
    for name in names:
        for source_item in criteria[name]["items"]:
            for item in _rename(source_item):
                if item not in items:
                    items.append(item)
    return items


def _condition(adv):
    """Maps the source criteria onto trigger/filter/count fields (vanilla semantics: outer AND, inner OR)."""
    criteria = adv["criteria"]
    groups = adv["requirements"]
    kinds = {criterion["type"] for criterion in criteria.values()}
    if len(kinds) != 1:
        raise ValueError("%s mixes criterion types %s" % (adv["id"], kinds))
    kind = kinds.pop()
    if kind == "event":
        (criterion,) = criteria.values()
        return {"trigger": "cooking_action", "filter": {"action": criterion["event"]}}
    if kind == "kill":
        return {"trigger": "item_kill", "filter": {"weapon": _items(criteria, criteria.keys())}}
    if kind == "inventory":
        if len(groups) == 1:
            return {"trigger": "item_obtain", "filter": {"item": _items(criteria, groups[0])}}
        unique_groups = {}
        for group in groups:
            name = group[0][4:] if group[0].startswith("has_") else group[0]
            unique_groups[name] = _items(criteria, group)
        return {
            "trigger": "item_obtain",
            "filter": {"item": [item for members in unique_groups.values() for item in members]},
            "goal": len(groups),
            "count": "unique",
            "unique-by": "item",
            "unique-groups": unique_groups,
        }
    raise ValueError("%s has unsupported criterion type %s" % (adv["id"], kind))


def migrate(source):
    advancements = source["advancements"]
    root = next(adv for adv in advancements if adv["id"] == "root")
    out = {
        "tab": {
            "id": TAB_ID,
            "title": root["title"],
            "description": root["description"],
            "icon": {"item": root["icon"]},
            "background": BACKGROUND,
        },
        "achievements": {},
    }
    for adv in advancements:
        if adv["id"] == "root" or adv["id"] in SKIPPED:
            continue
        ach = {}
        if adv["parent"] not in (None, "root"):
            ach["parent"] = adv["parent"]
        ach["title"] = adv["title"]
        ach["description"] = adv["description"]
        ach["icon"] = {"item": (_rename(adv["icon"]) or [adv["icon"]])[0]}
        if adv["frame"] != "task":
            ach["frame"] = adv["frame"]
        if adv["hidden"]:
            ach["hidden"] = True
        if not adv["showToast"]:
            ach["toast"] = False
        if not adv["announceChat"]:
            ach["announce"] = False
        ach.update(_condition(adv))
        out["achievements"][adv["id"]] = ach
    return out


def _scalar(value):
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, int):
        return str(value)
    if isinstance(value, list):
        return "[" + ", ".join(_scalar(item) for item in value) + "]"
    if isinstance(value, dict):
        return "{ " + ", ".join("%s: %s" % (key, _scalar(item)) for key, item in value.items()) + " }"
    return json.dumps(value, ensure_ascii=False)


def _emit(lines, mapping, indent):
    for key, value in mapping.items():
        if isinstance(value, dict) and key not in ("icon",):
            lines.append("%s%s:" % ("  " * indent, key))
            _emit(lines, value, indent + 1)
        else:
            lines.append("%s%s: %s" % ("  " * indent, key, _scalar(value)))


def dump(out):
    lines = ["# Generated by tools/migrate_kaleidoscope.py from the KaleidoscopeCookery built-in advancements.",
             "# Edit freely after generation; re-running the script overwrites this file."]
    for section in ("tab", "achievements"):
        lines.append("%s:" % section)
        _emit(lines, out[section], 1)
    return "\n".join(lines) + "\n"


def main(argv):
    here = os.path.dirname(os.path.abspath(__file__))
    source = argv[1] if len(argv) > 1 else os.path.join(here, "kaleidoscope.json")
    target = argv[2] if len(argv) > 2 else os.path.join(here, "..", "config", "achievements", "cooking.yml")
    with open(source, encoding="utf-8") as f:
        out = migrate(json.load(f))
    with open(target, "w", encoding="utf-8") as f:
        f.write(dump(out))
    print("wrote %d achievements to %s" % (len(out["achievements"]), target))


if __name__ == "__main__":
    main(sys.argv)
