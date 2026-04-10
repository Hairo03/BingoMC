# Roadmap

## Known Bugs

- NONE

## Goal Types

- Refactor `reach_y_level`, so that it becomes `reach_coordinate`, that supports both X, Y and Z coordinates. This would open up more interesting goal possibilities, such as "reach X=1000" (exploration) or "reach Y=-64" (bedrock)
  - Is it bad if its in a specific direction? Why would X+ be different from X-, Z+ or Z-?
  - Maybe a new `travel_away`, that requires traveling a certain distance from the world spawn point, regardless of direction?

- Add a `enter_biome` goal type, with a `biome` field that accepts biome namespaced keys (e.g. `minecraft:desert`), more exploration goals.

- Add a `reach_experience_level` goal type, with a `level` field that requires reaching a certain experience level.

- Consider adding a `distinct` field to `obtain_item_type`, to require obtaining a certain number of distinct items from that tag (e.g. 5 different types of logs from `minecraft:logs`), to encourage variety in goals that use item tags.

- Consider adding a `use_vehicle_type` goal type that requires using a specific type of vehicle (e.g. `minecraft:boat`), to add more variety to the transportation playstyle. https://minecraft.wiki/w/Entity_type_tag_(Java_Edition)
    - Could also have a `distinct` field for this as well, to require using a certain number of distinct vehicles from that tag.

- Consider adding a `kill_entity_type` goal type that requires killing a specific type of entity (e.g. `minecraft:aquatic` or `minecraft:skeletons`), to add more variety to the combat playstyle. https://minecraft.wiki/w/Entity_type_tag_(Java_Edition)
    - Could also have a `distinct` field for this as well, to require killing a certain number of distinct entities from that tag.

- Add a `get_status_effect` goal type, with a `status_effect` field that accepts status effect namespaced keys (e.g. `minecraft:levitation`), to encourage goals around potion brewing and beacon usage.

## Planned Features

- **More goal types**: Exploration, fishing, building playstyles are not well represented. Redo the default goal set and consider allowing more goals per round.
- **Ready check**: Configurable opt-in system where all players must confirm they are ready before the round starts.
- **Admin spectator mode**: Permission-based bypass so admins are excluded from joining automatically unless they opt in. If not joined, admins can spectate with a GUI. Clicking a goal-complete notification teleports the admin to that player.
- **Pause and resume**: Temporarily halt a round without resetting progress or the timer. Should survive server restarts.
- **Rejoin text**: Update the rejoin message to suit longer game sessions.
- **In-game goal editing**: Add, edit, and delete goals through the admin GUI without touching `goals.yml` manually.
- **Cross-platform support**: Geyser (Bedrock) and ViaVersion (older clients).
