# Mahabharata Vyūha Warfare — Prototype 0.1

Target: Minecraft Java Edition 1.20.1, Minecraft Forge 47.x, Java 17.

## What this prototype does
- Registers a minimal Forge mod named `vyuha_warfare`.
- Logs a startup message so you can verify that the mod loads.
- Establishes the project structure for future formation systems.

## What it does NOT do yet
- It does not integrate with Villager Recruits.
- It does not create troop groups or implement playable formations yet.
- No prebuilt JAR is included in this source archive. Build locally after Gradle downloads the Forge dependencies.

## Build on Windows
1. Install a JDK 17 (not just a JRE).
2. Install Git if you want version control.
3. Install Gradle 8.x, or generate/add a Gradle wrapper using a Gradle installation.
4. Open Command Prompt in this project folder.
5. Run:
   `gradle build`
6. The mod JAR should appear in:
   `build\libs\vyuha-warfare-0.1.0.jar`

If Gradle reports a ForgeGradle or Java toolchain issue, share the full error text.

## Install for testing
After a successful build, copy the JAR from `build\libs` into the `mods` folder used by your Forge 1.20.1 game profile. Launch the game and check the log for the startup message.

## Next milestone
Inspect the original Villager Recruits source and its license, then design mixed-unit formation membership and a first line-formation test.
