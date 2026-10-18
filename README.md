# Mahabharata Vyūha Warfare — Prototype 0.5.0

Target: Minecraft Java 1.20.1, Forge 47.4.x, Java 17. Compatibility target: Villager Recruits 1.14.0.

This is a separate addon. It does not bundle, copy, or redistribute Recruits source. It uses reflection against Recruits' public runtime methods and optional fields so it can integrate with Recruits' native state/hold-position goal rather than running a competing vanilla-navigation controller. The upstream repository says “All Rights Reserved unless otherwise explicitly stated”; see https://github.com/talhanation/recruits.

## Commands
- `/vyuha preview line <count>` — visual preview (2–40 slots).
- `/vyuha form line <radius>` — find nearby Recruits entities (radius 4–32), require that the executing player can command them, assign unique terrain-checked line slots, then request Recruits-native behaviour by setting hold position and its “back to position” state.
- `/vyuha release` — ends the addon’s tracked formation and switches these soldiers to Recruits’ native “Hold your Position” state at their current locations.

## Why this design differs from 0.4.x
The earlier prototype called `Mob.getNavigation().moveTo(...)` directly and tracked movement itself. Public Recruits source shows its formation flow sets each recruit's hold-position target, switches its follow state to 3 (“back to position”), and marks formation flags; Recruits' own hold-position goal then manages path retries. 0.5.0 uses reflection to request this existing behaviour rather than fighting it with an independent controller. The code does not import Recruits classes or include Recruits source.

## Build and test
1. Extract this ZIP and replace project files in the existing GitHub repository (include `.github/workflows/build.yml`).
2. Run GitHub Actions workflow `Build Forge 1.20.1 mod`.
3. Download artifact `vyuha-warfare-jar`; remove the previous Vyūha Warfare JAR before installing the new one. Keep `recruits-1.14.0.jar` installed.
4. Use a backed-up or Creative test world. Hire/own recruits with command listening enabled; stand on a broad, flat area with enough room.
5. Run `/vyuha form line 16`, wait 10–15 seconds, and see whether soldiers settle into slots.
6. Run `/vyuha release` to order them to hold their current positions using Recruits' own behaviour.

## Important limitations
- The public repository default branch is newer than the installed Recruits 1.14.0. The reflection-based method names were confirmed in the public code inspected, but not independently verified against every 1.14.0 binary symbol. If the method names differ, the chat command may report fewer assigned troops and the log will say which method was unavailable.
- A successful compile does not prove runtime compatibility. Build using Actions, test in game, and share chat output or `latest.log` if it fails.
- Slots use a simple line and terrain height/collision checks, not yet obstacle-aware route planning. Combat AI can temporarily pull recruits out of formation.
