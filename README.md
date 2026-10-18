# Mahabharata Vyūha Warfare — Prototype 1.1.0

Forge 1.20.1 source project for the Vajravyūha formation.

## Vajra blueprint
- Fixed battlefield footprint: 64×64 blocks.
- Nomad: 24 heavy-unit slots.
- Horseman: 40 cavalry slots, explicitly divided into four diamond slopes with 10 positions per slope.
- Shieldman: 80 outer infantry slots.
- Standard Recruit: 64 next-layer slots.
- Bowman: 40 inner ranged slots.
- Crossbowman: 32 innermost ranged slots.
- Total main formation capacity: 300 troops.
- Current cavalry count stays at 40; this revision changes placement, not the roster.

The geometry generator now constructs each diamond perimeter from four unique edges. Non-cavalry rings use a perimeter list without duplicated corners. Cavalry are interpolated evenly along each of the four slopes, excluding the sharp front/rear endpoints. Slot spacing is 1.4 blocks so the outer radius remains inside the nominal 64×64 footprint.

These are gameplay design targets, not claims about historically exact troop counts. Partial mode assigns available troops to their own role slots and leaves missing slots empty. Full mode requires the complete 300-slot roster. Surplus recruits go into a square reserve behind the formation.

## Commands
- `/vyuha form vajra partial 16` — partial deployment
- `/vyuha form vajra full 16` — full roster required
- `/vyuha form vajra 16` — partial deployment shortcut
- `/vyuha release` — release the tracked formation
- `/vyuha form line 16` — existing line formation

## Build
Use GitHub Actions workflow `Build Forge 1.20.1 mod`. This source ZIP has not been compiled or tested in-game in this environment. Verify the build and test with Forge 1.20.1 and Villager Recruits 1.14.0.


Version 1.1.0 changes: adds four cavalry corner guards (44 horsemen total) and splits the 24 Nomad positions into two inner diamond rings of 12 each to reduce center clustering. Full deployment now requires 300 units.
