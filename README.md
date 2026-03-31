# BingoMC

BingoMC is a Paper plugin that runs a competitive Bingo-style gamemode with per-player worlds, configurable goals, scoring, and in-game UI tools.

## Features

- **Per-player World Sets**: Creates personal overworld/nether/end worlds for each player per round using Multiverse
- **Inventory Isolation**: Isolates players using Multiverse-Inventories world groups
- **Portal Routing**: Links each player's own dimensions via Multiverse-NetherPortals
- **Config-driven Goals**: Loads and validates goals from `goals.yml`
- **Scoring & Ranking**: Tracks points and broadcasts final rankings at round end
- **Round Timer**: Displays a live boss bar timer and ends rounds automatically
- **Round Setup UI**: Start flow includes GUI-based seed and time-limit selection
- **Goals UIs**: Includes player viewer and admin goals interfaces via InvUI

## Requirements

- **Java**: 21 or higher
- **Minecraft Server**: Paper 1.21.4 or compatible version
- **Server Plugins**:
   - Multiverse-Core
   - Multiverse-NetherPortals
   - Multiverse-Inventories

## Setup & Installation

### Building from Source

```bash
./gradlew build
```

Windows:

```bash
gradlew.bat build
```

### Locate the JAR

```text
build/libs/BingoMC-1.0.0-SNAPSHOT.jar
```

### Install on Server

1. Install required plugins:
   - Multiverse-Core
   - Multiverse-NetherPortals
   - Multiverse-Inventories
2. Copy BingoMC jar into your server `plugins/` directory.
3. Start the server once to generate plugin data files.
4. Edit `plugins/BingoMC/goals.yml` as needed.
5. Restart the server, or run `/bingo goals reload` when no round is active.

### Requirements Before First Run

- Ensure your server is running Paper 1.21.4-compatible builds
- Ensure Multiverse dependencies are installed and loaded before BingoMC
- Ensure the server can write to `plugins/BingoMC/`

## Commands

- `/bingo` - Show usage
- `/bingo start` - Open round setup UI and start round (player only, op only)
- `/bingo stop` - Stop active round (op only)
- `/bingo goals` - Open player goals viewer UI
- `/bingo goals admin` - Open goals admin UI (`bingomc.goals.admin`)
- `/bingo goals validate` - Validate `goals.yml`
- `/bingo goals reload` - Reload goals from disk (not allowed during active round)

## Permissions

- `bingomc.goals.admin`
  - Allows opening goals admin UI and goals management workflow
  - Default: `op`

## Round Flow

1. Operator runs `/bingo start`.
2. Start GUI collects seed and time limit.
3. Plugin provisions personal world sets for all online players.
4. Players are teleported to their own overworld.
5. Timer starts and periodic/event goal evaluation runs.
6. At timeout (or `/bingo stop`), players return to main world.
7. Final score ranking is broadcast.
8. Managed Bingo worlds are rotated and cleaned by world service lifecycle.

Late joiners during an active round are not auto-enrolled and are told to wait for the next round.

## Goals Configuration (`goals.yml`)

Goals are loaded from `plugins/BingoMC/goals.yml`.

Supported `type` values:

- `craft_item`
- `consume_item`
- `use_vehicle`
- `obtain_item`
- `kill_entity`
- `unlock_advancement`

### Common Fields

- `id` (unique string)
- `type`
- `points` (integer >= 1)
- `enabled` (`true` or `false`)

### Type-Specific Fields

- `material` + optional `amount` for item-based goals
- `entity_type` + optional `amount` for entity goals
- `advancement_key` for advancement goals

If `goals.yml` is invalid at startup, plugin startup is aborted.

## TODO

- Fix Nether/End, didn't keep inventory and gamemode changed.
- Add more goal types for other playstyles, e.g. exploration, fishing, building, etc.
 - Redo default goals, and maybe allow even more goals per round.
- Rejoining during an active round works, but the message is wrong.
- Rejoining after round end doesn't work, you get put in your world instead of main world.
- Leaving during preparation will cause issues, as you will keep your `applyPreparationState`
- Add a configurable ready check before round start, so servers can choose whether all players must confirm they are ready.
- Add a permission-based admin join bypass, allowing admins to be excluded from joining the round automatically unless they explicitly opt in.
   - If admins didn't join, add ability to spectate, maybe with GUI
   - When a player completes a goal, admin should see link to tp to said player on click
- Change rejoining text to fit longer games
- Ability to change, add and delete goals in-game through admin GUI
- Cross-platform (Geyser, VIA Versions)

## Dependencies

| Dependency | Version | Purpose |
|-----------|---------|---------|
| Paper API | 1.21.4-R0.1-SNAPSHOT | Minecraft server API |
| Multiverse-Core | 5.5.3 | Per-player world lifecycle |
| Multiverse-NetherPortals | 5.0.4 | Per-player Nether/End portal routing |
| Multiverse-Inventories | 5.3.2 | Per-player inventory isolation |
| InvUI | 2.0.0-beta.1 | GUI framework (bundled in plugin jar) |
