# Mahabharata Vyūha Warfare — Prototype 0.2.1 (build fix)

Target: Minecraft Java Edition 1.20.1, Minecraft Forge 47.x, Java 17.

## Feature
Adds `/vyuha preview line <count>` where count is 2–20. It displays particle markers for a horizontal line of formation slots around the player. This is a geometry test, not yet a recruit-control system.

## Build with GitHub Actions
1. Replace the files in your GitHub repository with the contents of this project, including `.github/workflows/build.yml`.
2. Open Actions and run **Build Forge 1.20.1 mod**.
3. Download the `vyuha-warfare-jar` artifact from a successful run.
4. Replace the old JAR in your Forge 1.20.1 `mods` folder with the new JAR.

## Patch note
Fixed `IntegerArgumentType` to import from `com.mojang.brigadier.arguments.IntegerArgumentType`.

## In-game test
Run `/vyuha preview line 5` in a Creative world. Green particle markers should appear in a line around the player. Try counts 10 and 20 too.
