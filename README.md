# MineColonies MTR Integration (MVP)

This addon targets:
- Minecraft `1.20.1`
- Forge `47.x`
- MineColonies `release/1.20` line
- MTR `3.2.2-hotfix-1` (`3.x.x` tag, Forge `1.20-1.20.1`)

## What is implemented

- Custom MineColonies navigator registration for citizens.
- Long-distance target interception (`>=96` blocks) and MTR route request via `RailwayDataRouteFinderModule`.
- Transit plan state machine (request route -> walk to boarding platform -> ride -> disembark -> walk to final target).
- Mixin into `TrainServer.simulateCar` for NPC handling.
- Server-side NPC boarding/movement/dismount synchronized to train car position.

## Important build note

`build.gradle` intentionally keeps MineColonies/MTR dependencies as placeholders because exact deobf coordinates differ by pack/source.

Set one of these approaches:

1. `fg.deobf("curse.maven:...:<file_id>")` for both mods.
2. Local deobf jars from your dev environment.

Then run:

```bash
gradle genIntellijRuns
```

or

```bash
gradle build
```

## Next hardening steps

- Multi-leg transfers (currently first train leg is prioritized).
- Smarter platform selection/radius.
- Seat slotting to reduce NPC overlap.
- Safety fallbacks when route becomes invalid mid-travel.
