# Mahabharata Vyūha Warfare — Prototype 0.4.0

Target: Minecraft Java Edition 1.20.1, Forge 47.x, Java 17. This is a standalone compatibility addon; it does not bundle or copy Villager Recruits source.

## Commands
- `/vyuha preview line <count>` — visual preview (2–20 slots).
- `/vyuha form line <radius>` — find nearby live entities registered in the `recruits` namespace (radius 4–32), assign unique line slots, and start a lightweight controller.
- `/vyuha release` — stop the controller for the command-issuing player; soldiers return to normal Recruits AI.

## Prototype behaviour
- Each recruit is assigned a separate slot along a line in front of the player.
- Slots use the heightmap and basic collision checks to avoid placing targets inside blocks.
- The controller periodically checks the recruits and reissues navigation orders when a recruit is not near its slot and its current navigation path has ended. It does not overwrite active paths every tick.
- This remains experimental: it cannot guarantee exact formation holding if Recruits AI, combat, terrain, or pathfinding overrides movement. Selection is namespace-based and currently does not filter by owner/team.

## Build with GitHub Actions
1. Extract this ZIP and replace the project files in your existing GitHub repository, including `.github/workflows/build.yml`.
2. Open Actions and run **Build Forge 1.20.1 mod**.
3. Download `vyuha-warfare-jar` after a successful run.
4. Close Minecraft and remove the previous `vyuha-warfare-*.jar` before installing the new JAR in your Forge 1.20.1 `mods` folder.

## Test
1. Back up your world or use a Creative test world.
2. Gather several Recruits on relatively flat terrain.
3. Run `/vyuha form line 16`. Wait several seconds and watch whether they settle into assigned slots.
4. Run `/vyuha release` to stop the controller.

The upstream Recruits repository identifies its source as All Rights Reserved. This addon uses registry-level entity detection and does not include its code. The source has not been compiled or tested in-game in this environment; build with Actions and report any build error before testing.
