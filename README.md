# Mahabharata Vyūha Warfare — Prototype 0.2

Target: Minecraft Java Edition 1.20.1, Minecraft Forge 47.x, Java 17.

## Feature
Adds `/vyuha preview line <count>` where count is 2–20. It displays particle markers for a horizontal line of formation slots around the player. This is a geometry test, not yet a recruit-control system.

## Build
The GitHub Actions workflow in `.github/workflows/build.yml` uses JDK 17 and Gradle 8.8:
1. Upload these project files to your GitHub repository, including the hidden `.github` folder.
2. Open Actions and run **Build Forge 1.20.1 mod**.
3. Download the `vyuha-warfare-jar` artifact from the successful run.
4. Extract the artifact and copy the JAR into the Forge 1.20.1 `mods` folder, replacing the old version.

## In-game test
1. Create or enter a Creative world.
2. Run `/vyuha preview line 5`.
3. Look for green particle markers arranged in a line around you.
4. Try counts 10 and 20.

If the command is unknown, ensure the new JAR is installed and inspect `latest.log`.

## Next
Use these formation slots to develop mixed-unit membership and then integrate with Villager Recruits after source/license review.
