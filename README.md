# Mahabharata Vyūha Warfare — Prototype 0.9.0

Forge 1.20.1 source project. This revision defines a fixed 64×64-block Vajravyūha footprint and a 280-slot gameplay roster.

## Vajra 2.0 roster
- Nomad: 24
- Horseman: 40
- Shieldman: 80
- Standard Recruit: 64
- Bowman: 40
- Crossbowman: 32
- Total: 280

These are gameplay-balancing targets, not a claim of historical troop counts. Partial mode assigns available units to their role's slots and leaves missing slots empty. Full mode requires the full roster. Surplus recruits are assigned to a square reserve behind the formation.

## Commands
- `/vyuha form vajra partial 16` — partial deployment
- `/vyuha form vajra full 16` — full 280-unit roster required
- `/vyuha form vajra 16` — partial deployment shortcut
- `/vyuha release` — release the tracked formation

## Build
Use GitHub Actions workflow `Build Forge 1.20.1 mod`. This source has not been compiled or tested in-game in this environment. Validate unit classification and slot geometry with Recruits 1.14.0 before treating the build as release-ready.
