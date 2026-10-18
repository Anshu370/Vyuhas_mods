# Mahabharata Vyūha Warfare — Prototype 0.7.0 — Category-aware Vajravyūha

Target: Minecraft Java 1.20.1, Forge 47.4.x, Java 17. Compatibility target: Villager Recruits 1.14.0.

This is a separate addon. It does not bundle, copy, or redistribute Recruits source. It uses reflection to request Recruits' native hold-position state. The upstream repository says “All Rights Reserved unless otherwise explicitly stated”; see https://github.com/talhanation/recruits.

## Commands
- `/vyuha preview line <count>` — visual preview (2–40 slots).
- `/vyuha form line <radius>` — preserve the working line formation.
- `/vyuha form vajra <radius>` — category-aware Vajra layout. The radius is the search radius for nearby commandable recruits, not the formation width.
- `/vyuha release` — switches tracked soldiers to Recruits' native stationary Hold Position state at their current locations.

## Vajra role rules in 0.7.0
The formation assigns six role groups in a fixed order and reserves slots for each group, leaving unfilled slots empty:
1. Nomads — eight heavy-unit/elephant-and-chariot-inspired stations.
2. Horsemen — twenty cavalry-wing stations.
3. Shieldmen — outer infantry band.
4. Standard recruits — next infantry band.
5. Bowmen — inner ranged band.
6. Crossbowmen — innermost ranged band.

Recruits exceeding their category's reserved capacity are placed in a square reserve behind the formation. The addon never fills a missing horseman slot with an infantry recruit. All assigned units use Recruits' native hold-position methods where those methods are available.

## Classification note
The addon tries to classify roles using entity registry IDs and held equipment (bow, crossbow, shield). If Recruits 1.14.0 represents all table variants using one entity ID and does not expose the role through equipment, the corresponding category cannot be reliably inferred by this generic classifier; in that case we will need to inspect your exact version's entity data and add a version-specific adapter. Nomad classification currently relies on the entity ID containing `nomad`.

## Build and test
1. Extract this ZIP and upload/replace the project files in the existing GitHub repository, including `.github/workflows/build.yml`.
2. Run GitHub Actions workflow `Build Forge 1.20.1 mod`.
3. Download artifact `vyuha-warfare-jar`; remove the previous Vyūha Warfare JAR before installing the new one. Keep Recruits installed.
4. Test in a backed-up or Creative world on a broad, flat area. Ensure your recruits are owned by you and listening for commands.
5. Run `/vyuha form vajra 16`, wait 10–15 seconds, then inspect each category. Run `/vyuha release` before testing another formation.

## Limitations
- This ZIP has not been compiled in this environment; use GitHub Actions to compile it.
- Terrain checking validates the target surface but does not pathfind around obstacles.
- Combat AI can temporarily pull soldiers away from their positions.
- Role classification needs in-game verification against Recruits 1.14.0. Send the command feedback and relevant `latest.log` output if any role is misclassified or not assigned.
