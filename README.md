# Mahabharata Vyūha Warfare — Prototype 0.3.0

Target: Minecraft Java Edition 1.20.1, Forge 47.x, Java 17.

## Features
- `/vyuha preview line <count>` displays line-slot particles for 2–20 positions.
- `/vyuha form line <radius>` searches within 4–32 blocks for living mob entities registered under the `recruits` namespace, then issues navigation targets to arrange them into a mixed line. Different Recruits classes can be handled together because selection is by entity namespace, not class.
- This is an experimental first integration. It has not yet been compiled or tested in-game; navigation may be refused by some recruit states or blocked terrain.

## Build in GitHub Actions
1. Replace the project files in your GitHub repository with these files, including `.github/workflows/build.yml`.
2. Open Actions and run **Build Forge 1.20.1 mod**.
3. Download `vyuha-warfare-jar` after a successful run.
4. Close Minecraft, remove old `vyuha-warfare-*.jar` files, and copy the new JAR into the Forge 1.20.1 `mods` folder.

## In-game test
1. Use a backup or test Creative world.
2. Spawn/hire several Recruits of different classes nearby.
3. Run `/vyuha form line 16`.
4. The command issues navigation targets to nearby Recruits registered under the `recruits` namespace and emits green markers at their target slots.
5. `/vyuha preview line 5` remains available.

This mod does not include or redistribute Villager Recruits code. The public source repository identifies its license as All Rights Reserved; this project uses registry-level entity detection rather than copying its implementation.

## Next
Verify the build and in-game behavior, then improve selection, ownership filtering, slot persistence, pathing, and mixed-role formations.
