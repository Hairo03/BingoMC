# Roadmap

## Known Bugs

- Rejoining during an active round shows the wrong message.
- Rejoining after a round ends puts you back in your bingo world instead of the main world.
- Leaving during the preparation phase leaves the player in `applyPreparationState` (frozen/blocked controls persist).

## Planned Features

- **More goal types**: Exploration, fishing, building playstyles are not well represented. Redo the default goal set and consider allowing more goals per round.
- **Ready check**: Configurable opt-in system where all players must confirm they are ready before the round starts.
- **Admin spectator mode**: Permission-based bypass so admins are excluded from joining automatically unless they opt in. If not joined, admins can spectate with a GUI. Clicking a goal-complete notification teleports the admin to that player.
- **Pause and resume**: Temporarily halt a round without resetting progress or the timer. Should survive server restarts.
- **Rejoin text**: Update the rejoin message to suit longer game sessions.
- **In-game goal editing**: Add, edit, and delete goals through the admin GUI without touching `goals.yml` manually.
- **Cross-platform support**: Geyser (Bedrock) and ViaVersion (older clients).
