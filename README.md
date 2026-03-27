# BingoMC

A Minecraft plugin that implements a Bingo gamemode for Paper/Spigot servers. Players complete various in-game objectives to achieve bingo patterns as a competitive gamemode.

## Features

- **Goal-Based Gameplay**: Track and verify player completion of various in-game objectives
- **Multiple Goal Types**:
  - **Block Count Goal**: Require players to collect a specific amount of a block
  - **Item Consume Goal**: Track consumption of specific items (eating, drinking, etc.)
  - **Item Craft Goal**: Register crafting of specific items
  - **Vehicle Usage Goal**: Track vehicle interactions (horses, boats, minecarts)
- **Event-Driven Architecture**: Goals trigger on inventory changes, periodic checks, and custom events
- **Real-time Tracking**: Monitor player progress with timers and consume tracking
- **Boss Bar Display**: Visual feedback for players on their progress

## Requirements

- **Java**: 21 or higher
- **Minecraft Server**: Paper 1.21.4 or compatible version
- **Libraries**:
  - Paper API 1.21.4

## Setup & Installation

### Building from Source

1. **Clone the Repository**
   ```bash
   git clone <repository-url>
   cd BingoMC
   ```

2. **Build the Plugin**
   ```bash
   ./gradlew build
   ```
   On Windows:
   ```bash
   gradlew.bat build
   ```

3. **Locate the JAR**
   The compiled plugin will be available at:
   ```
   build/libs/BingoMC-1.0.0-SNAPSHOT.jar
   ```

4. **Install on Server**
   - Copy the JAR file to your Paper server's `plugins/` directory
   - Restart the server
   - The plugin will auto-generate necessary configuration files

### Requirements Before First Run

- Ensure your Paper server is running version 1.21.4 or compatible
- Have proper write permissions for the `plugins/` directory

## Usage

### Commands

- `/bingo start` - Initialize and start a bingo game session (operators only)
- `/bingo test` - Create and load a personal test world (Slime world functionality not working yet)

### In-Game Gameplay

Once a bingo session is started, players will:
1. Receive goal objectives (No way to track them yet)
2. Complete objectives by performing in-game actions
3. Achieve points by completing required goals

## Architecture Overview

### Goal System

The plugin uses a flexible goal management system built on the `PlayerGoal` interface:

- **Goals** define win conditions and track completion status
- **GoalTrigger** enums determine when goals are checked (inventory changes, periodic checks, events)
- **GoalManager** orchestrates goal registration, tracking, and completion verification
- **Event Listeners** respond to Minecraft events and update goal progress

## Development

### Adding New Goal Types

1. Create a new class in `src/main/java/com/hairo/bingomc/goals/impl/`
2. Implement the `PlayerGoal` interface
3. Define appropriate `GoalTrigger` types
4. Register in `GoalManager`
5. Add listener handling if needed in `GoalEventListener.java`

### Testing

Use `/bingo test` to create and load a personal Slime world for testing world functionality. (WIP)

## Version Information

- **Plugin Version**: 1.0.0-SNAPSHOT
- **Author**: Hairo
- **Minecraft API Version**: 1.21

## Dependencies

| Dependency | Version | Purpose |
|-----------|---------|---------|
| Paper API | 1.21.4-R0.1-SNAPSHOT | Minecraft server API |

## Future Enhancements

Potential improvements for future versions:
- Database support for persistent goal tracking
- Multi-round bingo sessions
- Leaderboard system
- Customizable goal configurations
- Web dashboard for monitoring

## Support

For issues, questions, or contributions, please refer to the project repository.
