# BingoMC — Agent Guide

Competitive Bingo gamemode plugin for Paper Minecraft servers. Players complete goals in isolated per-player worlds (Overworld + Nether + End) to earn points within a time limit.

## Build

```bash
./gradlew build          # Linux/macOS
gradlew.bat build        # Windows
```

Output: `build/libs/BingoMC-1.0.0-SNAPSHOT-dev.jar`

There are no automated tests. Verification requires deploying to a live Paper 1.21.x server with Multiverse-Core, Multiverse-NetherPortals, and Multiverse-Inventories installed.

## Architecture

### Entry point and wiring

`BingoMC.java` is the plugin entry point and manual DI root. All services are instantiated and connected in `onEnable()`. The plugin self-disables if Multiverse APIs fail to initialize or `goals.yml` has validation errors.

### Round lifecycle

`RoundService` owns game state (`gameRunning`, `gamePreparing`) and drives the full lifecycle:
- `startRound()` — provisions worlds, teleports players, runs preparation countdown
- `launchRound()` — starts the timer, calls `GoalManager.onRoundStart()` per player
- `endRound()` — teleports back, broadcasts rankings, marks worlds for cleanup

`RoundTaskTicker` manages Bukkit scheduler tasks. When the timer expires it fires a custom `TimerExpiredEvent`, caught in `BingoMC.java` and forwarded to `RoundService`. `RoundPresenter` handles all messaging (boss bar, titles, action bars, broadcasts).

### Goal system

Goals are stateless; `GoalManager` holds all mutable state (completions, progress, points).

**Evaluation flow:** `GoalEventListener` catches Bukkit events → calls `GoalManager.evaluate(player, trigger)` → manager skips goals whose `triggers()` set doesn't include the trigger → calls `isComplete(player)` on the rest.

**Goal interfaces** (`goals/core/`):

| Interface | Purpose |
|---|---|
| `PlayerGoal` | Base: `id()`, `triggers()`, `isComplete(Player)`, `descriptionText()`, `iconMaterial()` |
| `AmountBasedGoal` | Progress tracking: `currentProgress(Player)`, `amount()` — manager shows action bar progress |
| `ConsumeAwareGoal` | Receives `onItemConsumed(Player, Material)` before evaluate fires — for consumption counting |
| `RoundAwareGoal` | `onRoundStart(Player)` to capture baselines; `onRoundReset()` to clear per-round state |

**Loading:** `GoalConfigService` parses a YAML file via a `Map<String, GoalFactory>` (one lambda per type), validates eagerly, returns `GoalLoadResult`. The key methods are:

- `loadGoals()` — loads `goals.yml` from the data folder
- `loadGoalsFrom(File)` — loads `goals` list from any file
- `loadGoalsFrom(File, String listKey)` — loads from a named list key (used by `RandomGoalPoolService` to validate `random_goals.yml` with `listKey = "random_goals"`)

`GoalsService` registers results from `loadGoals()` into `GoalManager`.

**Goal pool and randomization:**

`RandomGoalPoolService` loads `random_goals.yml` (bundled, copied to data folder on first run). On `loadPool()`, it reads difficulty/playstyle metadata from the raw YAML, delegates validation to `GoalConfigService.loadGoalsFrom(file, "random_goals")`, and merges them into `List<RandomGoalEntry>`. Invalid entries are logged as warnings; the pool is never empty-aborted.

`GoalRandomizerService.randomize()` draws a balanced set from the pool (5 easy / 7 normal / 8 advanced / 5 hard / 3 extreme), using round-robin across shuffled playstyle buckets within each tier. It writes the selected raw YAML maps directly to `goals.yml` (no re-serialization), then calls `GoalsService.reloadGoals(false)`. Returns `null` on success or an error string on failure.

The developer script `scripts/csv_to_yml.py` (not bundled) regenerates `src/main/resources/random_goals.yml` from `random_goals.csv`.

### Adding a new goal type

1. Create a class in `goals/impl/` implementing `PlayerGoal` plus any optional interfaces above.
2. Add a factory lambda to `factories` in `GoalConfigService` constructor.
3. If a new trigger is needed, add it to `GoalTrigger` and fire it from a new `@EventHandler` in `GoalEventListener`.
4. Add the type's fields to the `goals.yml` table in `README.md`.

### World management

`BingoWorldService` wraps the Multiverse API trio. Each player gets a `PlayerWorldSet` record (three world name strings). Previous-round worlds are deleted at the start of the next round, not immediately on round end.

### GUI and sidebar

- **InvUI** (`xyz.xenondevs.invui`) — chest-based GUIs (`GoalsViewerGui`, `GoalsAdminGui`, `NewGameGui`). Bundled in the shadow JAR.
- **ScoreboardLibrary** (`net.megavex`) — live per-player sidebar via `GoalsSidebar`. Bundled and relocated to `com.hairo.bingomc.libs.scoreboardlibrary` to avoid conflicts.

`GoalsSidebar` is registered as the completion callback on `GoalManager` and updates the sidebar on every goal completion.

`GoalsAdminGui` includes a two-step confirm Randomize button (bottom-center slot). It uses `Item.builder()` with `setItemProvider()` + `notifyWindows()` to toggle between the idle and confirm states without reopening the inventory.

### Text formatting

All user-facing text uses the Adventure API (`net.kyori.adventure.text.Component`). The `prefixed()` helper in `BingoMC.java` prepends `[Bingo]` in gold bold; it is passed as `Function<Component, Component>` to any service that sends messages.
