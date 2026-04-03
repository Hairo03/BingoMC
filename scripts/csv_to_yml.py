#!/usr/bin/env python3
"""
Developer script: converts random_goals.csv -> src/main/resources/random_goals.yml

Usage:
    python3 scripts/csv_to_yml.py

Run from the project root. The output file is bundled in the plugin JAR.
Admins can also edit the copy that gets written to the plugin data folder at first run.
"""

import csv
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).parent
PROJECT_ROOT = SCRIPT_DIR.parent
CSV_FILE = PROJECT_ROOT / "random_goals.csv"
OUT_FILE = PROJECT_ROOT / "src" / "main" / "resources" / "random_goals.yml"

# Fields each type uses (controls which CSV columns end up in the YAML entry)
TYPE_FIELDS = {
    "craft_item":          ["material", "amount"],
    "consume_item":        ["material", "amount"],
    "obtain_item":         ["material", "amount"],
    "obtain_item_type":    ["material_type", "amount"],
    "kill_entity":         ["entity_type", "amount"],
    "use_vehicle":         ["entity_type"],
    "unlock_advancement":  ["advancement_key"],
    "change_dimension":    ["dimension"],
    "reach_y_level":       ["level", "direction"],
    "enter_structure":     ["structure"],
}

def strip(value: str) -> str:
    return value.strip()

def parse_points(value: str) -> int:
    """Convert '25p' -> 25."""
    cleaned = value.strip().rstrip("p").strip()
    return int(cleaned)

def make_id(difficulty: str, playstyle: str, goal_type: str, row: dict) -> str:
    """Generate a snake_case id from difficulty + playstyle + type + key identifier."""
    parts = [difficulty, playstyle, goal_type]

    if goal_type in ("craft_item", "consume_item", "obtain_item"):
        key = row.get("material", "")
    elif goal_type == "obtain_item_type":
        key = row.get("material_type", "")
    elif goal_type in ("kill_entity", "use_vehicle"):
        key = row.get("entity_type", "")
    elif goal_type == "unlock_advancement":
        key = row.get("advancement_key", "")
    elif goal_type == "change_dimension":
        key = row.get("dimension", "")
    elif goal_type == "reach_y_level":
        level = row.get("level", "0")
        direction = row.get("direction", "").lower()
        key = f"y{level}_{direction}"
    elif goal_type == "enter_structure":
        key = row.get("structure", "")
    else:
        key = ""

    # Strip namespace prefix and convert to snake_case identifier
    key = re.sub(r"^minecraft:", "", key)
    key = re.sub(r"[^a-z0-9]+", "_", key.lower()).strip("_")
    if key:
        parts.append(key)

    return "_".join(parts)

def yaml_scalar(value) -> str:
    """Render a Python value as a YAML scalar (no full serializer needed)."""
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, int):
        return str(value)
    s = str(value)
    # Quote strings that could be misread as YAML booleans/nulls or contain special chars
    needs_quotes = (
        s in ("true", "false", "null", "yes", "no", "on", "off")
        or s.startswith(("@", "&", "*", "?", "|", "-", "<", ">", "=", "!", "%", "`"))
        or ":" in s
        or "#" in s
        or s == ""
    )
    if needs_quotes:
        escaped = s.replace("'", "''")
        return f"'{escaped}'"
    return s

def write_entry(f, entry: dict):
    f.write("  - ")
    first = True
    for key, value in entry.items():
        if first:
            f.write(f"{key}: {yaml_scalar(value)}\n")
            first = False
        else:
            f.write(f"    {key}: {yaml_scalar(value)}\n")

def main():
    if not CSV_FILE.exists():
        print(f"ERROR: CSV file not found: {CSV_FILE}", file=sys.stderr)
        sys.exit(1)

    with open(CSV_FILE, newline="", encoding="utf-8") as fh:
        reader = csv.DictReader(fh)
        # Strip whitespace from header names
        reader.fieldnames = [strip(h) for h in reader.fieldnames]
        rows = list(reader)

    entries = []
    skipped = 0
    seen_ids = set()

    for i, raw in enumerate(rows, start=2):  # start=2: CSV row numbers (1 = header)
        row = {k: strip(v) for k, v in raw.items()}

        goal_type = row.get("type", "")
        if not goal_type:
            print(f"  WARNING: row {i} has no type — skipping")
            skipped += 1
            continue

        if goal_type not in TYPE_FIELDS:
            print(f"  WARNING: row {i} has unknown type '{goal_type}' — skipping")
            skipped += 1
            continue

        difficulty = row.get("difficulty", "").lower()
        playstyle  = row.get("playstyle", "").lower()

        try:
            points = parse_points(row.get("points", "0"))
        except ValueError:
            print(f"  WARNING: row {i} has invalid points '{row.get('points')}' — skipping")
            skipped += 1
            continue

        goal_id = row.get("id", "")
        if not goal_id:
            goal_id = make_id(difficulty, playstyle, goal_type, row)

        # Deduplicate IDs
        base_id = goal_id
        suffix = 1
        while goal_id in seen_ids:
            goal_id = f"{base_id}_{suffix}"
            suffix += 1
        seen_ids.add(goal_id)

        # Derive icon from material field (use material as icon for item-based goals)
        icon = row.get("icon", "") or row.get("material", "")

        entry = {"id": goal_id, "difficulty": difficulty, "playstyle": playstyle,
                 "type": goal_type, "points": points}

        if icon:
            entry["icon"] = icon

        # Add type-specific fields
        for field in TYPE_FIELDS[goal_type]:
            value = row.get(field, "")
            if not value:
                print(f"  WARNING: row {i} ({goal_id}) missing required field '{field}'")
                continue
            # Parse numeric fields
            if field in ("amount", "level"):
                try:
                    entry[field] = int(value)
                except ValueError:
                    entry[field] = value
            else:
                entry[field] = value

        entries.append(entry)

    OUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    with open(OUT_FILE, "w", encoding="utf-8") as f:
        f.write("# Auto-generated from random_goals.csv — do not edit directly.\n")
        f.write("# Run scripts/csv_to_yml.py to regenerate.\n")
        f.write("# Admins can also manually add/edit goals in the plugin data folder copy.\n")
        f.write("random_goals:\n")
        for entry in entries:
            write_entry(f, entry)

    print(f"Written {len(entries)} goals to {OUT_FILE}  ({skipped} skipped)")

if __name__ == "__main__":
    main()
