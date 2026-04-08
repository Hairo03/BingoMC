# BingoMC

A competitive Bingo gamemode plugin for Paper Minecraft servers.

Players compete to complete randomized goals across isolated per-player worlds. Each player gets their own Overworld, Nether, and End — all generated from the same seed — so everyone starts on equal footing. Goals are completed for points, and the highest score at the end of the round wins.

## Features

- Per-player world sets (Overworld + Nether + End) provisioned each round via Multiverse-Core
- Inventory isolation per world group via Multiverse-Inventories
- Nether and End portals route to each player's own dimension via Multiverse-NetherPortals
- 10 configurable goal types (see [Configuration](#configuration))
- 28 default goals across 4 difficulty tiers: Easy (25 pts), Medium (50 pts), Hard (100 pts), Impossible (200 pts)
- One-click goal randomization from a curated pool (`random_goals.yml`), balanced by difficulty and playstyle
- Preparation phase with movement blocking before the round timer starts
- Boss bar countdown timer during the round
- Live goal sidebar with pinnable goals and an in-game goal viewer GUI
- Final score rankings broadcast at round end

## Requirements

- Java 21+
- Paper 1.21.x (built against 1.21.11)
- [Multiverse-Core](https://dev.bukkit.org/projects/multiverse-core) 5.5.3
- [Multiverse-NetherPortals](https://dev.bukkit.org/projects/multiverse-netherportals) 5.0.4
- [Multiverse-Inventories](https://dev.bukkit.org/projects/multiverse-inventories) 5.3.2

## Installation

**Build from source:**

```bash
./gradlew build
# Windows:
gradlew.bat build
```

Output: `build/libs/BingoMC-1.0.0-SNAPSHOT-dev.jar`

**Server setup:**

1. Install Multiverse-Core, Multiverse-NetherPortals, and Multiverse-Inventories on your server.
2. Copy the BingoMC JAR into `plugins/`.
3. Start the server once to generate `plugins/BingoMC/config.yml`, `plugins/BingoMC/goals.yml`, and `plugins/BingoMC/random_goals.yml`.
4. Edit `goals.yml` as needed (the defaults are usable out of the box).
5. Use `/bingo goals reload` to apply changes without restarting (only works when no round is active).

## Configuration

### `config.yml`

```yaml
preparation-countdown-seconds: 60
```

The number of seconds players are frozen in place before the round timer starts.

### `random_goals.yml`

A curated pool of goals the randomizer draws from. Copied to the plugin data folder on first run. Admins can add or edit entries manually — invalid entries are logged as warnings at startup but do not abort the plugin.

Format is identical to `goals.yml` with two extra fields per entry:

| Field | Description |
|---|---|
| `difficulty` | `easy`, `normal`, `advanced`, `hard`, or `extreme` — controls tier balance |
| `playstyle` | Freeform tag (e.g. `adventurer`, `fighter`, `crafter`) — ensures variety within each tier |

The randomizer picks 5 easy / 7 normal / 8 advanced / 5 hard / 3 extreme goals (28 total) using a round-robin across playstyles within each tier. Trigger the randomizer from `/bingo goals admin`.

Developers can regenerate the bundled pool from `random_goals.csv` using `scripts/csv_to_yml.py` (not bundled in the JAR).

### `goals.yml`

Goals are defined as a list under the `goals` key. Common fields:

| Field | Description |
|---|---|
| `id` | Unique string identifier |
| `type` | Goal type (see table below) |
| `difficulty` | `easy`, `medium`, `hard`, or `impossible` — display only |
| `points` | Integer score value for completing this goal |
| `enabled` | `true` or `false` |
| `icon` | Minecraft material namespaced key for the GUI icon (e.g. `minecraft:oak_log`) |

**Goal types and their type-specific fields:**

| Type | Required fields | Optional fields |
|---|---|---|
| `obtain_item` | `material` | `amount` (default: 1) |
| `obtain_item_type` | `material_type` (item tag, e.g. `minecraft:logs`) | `amount` (default: 1) |
| `consume_item` | `material` | `amount` (default: 1) |
| `craft_item` | `material` | `amount` (default: 1) |
| `kill_entity` | `entity_type` | `amount` (default: 1) |
| `unlock_advancement` | `advancement_key` | — |
| `change_dimension` | `dimension` (`minecraft:overworld` / `minecraft:the_nether` / `minecraft:the_end`) | — |
| `enter_structure` | `structure` (namespaced key) | — |
| `use_vehicle` | `entity_type` | — |
| `reach_y_level` | `level` (integer), `direction` (`UP` or `DOWN`) | — |

Run `/bingo export` to generate `plugins/BingoMC/goal-options.csv` — a full list of valid values for `material`, `entity_type`, `advancement_key`, `material_type`, `structure`, and `dimension` fields.

The plugin will refuse to load if `goals.yml` contains validation errors. Use `/bingo goals validate` to check before reloading.

## Commands & Permissions

| Command | Permission | Default | Description |
|---|---|---|---|
| `/bingo` | — | everyone | Show usage |
| `/bingo start` | `bingomc.start` | op | Open round setup GUI and start a round |
| `/bingo stop` | `bingomc.stop` | op | Stop the active round |
| `/bingo goals` | `bingomc.goals.user` | all players | Open the goals viewer GUI |
| `/bingo goals admin` | `bingomc.goals.admin` | op | Open the goals admin GUI |
| `/bingo goals validate` | `bingomc.goals.validate` | op | Validate `goals.yml` and report errors |
| `/bingo goals reload` | `bingomc.goals.reload` | op | Reload goals from disk (blocked during active round) |
| `/bingo export` | `bingomc.export` | op | Export valid goal option values to `goal-options.csv` |

## Round Flow

1. Operator runs `/bingo start` to open the round setup GUI (seed and time limit selection).
2. All online players are registered as participants.
3. Per-player world sets are provisioned — each player gets a private Overworld, Nether, and End with the chosen seed.
4. Players are teleported to their private Overworld spawn.
5. Preparation phase begins: movement is blocked for the configured countdown.
6. Round starts: goals are tracked via game events and periodic checks. Boss bar shows remaining time.
7. Round ends when the timer expires or an operator runs `/bingo stop`.
8. Players are teleported back to the main world.
9. Final scores are ranked and broadcast to all players.
10. Bingo worlds are marked for cleanup before the next round.

Players who join during an active round are told to wait for the next round.

## Dependencies

| Dependency | Version | Bundled |
|---|---|---|
| Paper API | 1.21.11-R0.1-SNAPSHOT | No — provided by server |
| Multiverse-Core | 5.5.3 | No — must be installed on server |
| Multiverse-NetherPortals | 5.0.4 | No — must be installed on server |
| Multiverse-Inventories | 5.3.2 | No — must be installed on server |
| [InvUI](https://github.com/NichtStudioCode/InvUI) | 2.0.0-beta.1 | Yes |
| [scoreboard-library](https://github.com/MegavexNetwork/scoreboard-library) | 2.7.1 | Yes |
