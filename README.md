# Gesturing

Gesturing is a standalone Minecraft 1.20.6 plugin for Paper and GLAP1, with a separate
Fabric server mod. It does not depend on GSit at runtime. Shape-aware seats
and the invisible NMS seat entity are adapted from GSit 3.8.0 under
GPL-3.0-only; attribution and license are in
`THIRD_PARTY_NOTICES.md` and `LICENSE`.

## Requirements

- Paper 26.2 (build 135 tested) or GLAP1 for Minecraft 1.20.6
- Java 21 or newer

## Commands

- `/sit` — sit on the supporting block below you, including stairs and slabs.
- `/sit playertoggle` — allow or prevent other players from sitting on you.
- `/sit toggle` — toggle block sitting.
- `/lay [east|west|south|north]` — lie down; without a direction you lie facing where you look.
- `/bellyflop`, `/legsup`, `/spin`, `/crawl` — start the corresponding pose.
- `/stand` — stop the current gesture and return to the spot you stood on before it.

`/lay`, `/legsup`, `/bellyflop` and `/spin` work like GSit: you sit on an invisible seat while every nearby
player (you included) sees a packet-only copy of you in the pose. No bed is placed in the world, the real
player never sleeps, and there is no bed screen, darkening, spawn-point change or sleep advancement.
`/crawl` sends a client-only barrier above your head so the client itself enters the crawling pose.

Moving, sneaking, taking damage, teleporting, or leaving the server also ends
the pose. The four directions are available through tab completion.

## Build and install

```sh
./gradlew build
```

Copy `build/libs/gesturing-0.3.2.jar` into the server's `plugins` folder and
restart the server. `/sit` is command-only; right-clicking blocks does not sit.

### Fabric server mod

The Fabric build targets Minecraft 1.20.6 and Java 25. Build it with:

```sh
./gradlew -p fabric build
```

Copy `fabric/build/libs/gesturing-fabric-0.3.2.jar` into the Fabric server's
`mods` folder. The server must have Fabric Loader 0.20.1 or newer and Fabric
API 0.100.0+1.20.6 or newer. The Fabric build supports `/sit`, `/lay`,
`/bellyflop`, `/legsup`, `/spin`, `/crawl`, and `/stand`. Player-seat toggling
is currently Paper-only. Fabric GameTests exercise full blocks, slabs, stairs,
directional blocks, sit toggling, four `/lay` directions, every pose, invalid
inputs, and cleanup after movement, teleport, sneaking, damage notification,
and disconnect.

## Verification

### Branch `1.20.6` (2026-11-09)

- Paper 1.20.6 build 151 (dev bundle `1.20.6-R0.1-SNAPSHOT`, Java 21): plugin enables on a fresh isolated server.
- Fabric 1.20.6 (Fabric API 0.100.8): `All 2 required tests passed`.
- API differences from 1.21.11: `moveTo` instead of `absSnapTo`, `startRiding(entity, force)`, `hurt(source, amount)`, `GameProfile.getName()/getProperties()`,
  player entity-data ids shifted (skin 17, main hand 18 as a byte, shoulders are `CompoundTag`), teleport packet built from the entity,
  no `setRiptiding`, GameTests use `@GameTest(template = EMPTY_STRUCTURE)` and `ServerLivingEntityEvents.ALLOW_DAMAGE`.
- Not yet checked with a real 1.20.6 client.

### Branch `1.21.11` (2026-11-09)

- Paper 1.21.11 build 132 (dev bundle `1.21.11-R0.1-SNAPSHOT`, Java 21): plugin enables on a fresh isolated server.
- Fabric 1.21.11 (Fabric API 0.141.6, Mojang mappings, Java 21): `All 3 required tests passed`.
- The 26.x code compiled unchanged; only the build setup differs (Java 21, `fabric-loom` with Mojang mappings).
- Not yet checked with a real 1.21.11 client.

### Branch `26.1.2` (2026-11-09)

- Paper 26.1.2 build 74: plugin builds and enables on a fresh isolated server.
- Fabric 26.1.2 (Fabric API 0.155.3+26.1.2): `All 3 required tests passed`.
- API differences from 26.2: `Blocks.BED.white()` is `Blocks.WHITE_BED`, and `Player.unsetFixedPose` does not exist.
- Not yet checked with a real 26.1.2 client.

### Branch `26.2` (2026-11-09)

- Paper 26.2 build 135: plugin builds and enables on a fresh isolated server.
- Fabric 26.2 (Fabric API 0.100.0+1.20.6): `All 3 required tests passed`.
- API differences from 26.3: `ClientboundMoveEntityPacket.PosRot` takes three `short` deltas instead of `VecDelta`, and
  `setPermanentlyInvulnerable` is `setInvulnerable`.
- Not yet checked with a real 26.2 client. The 26.3 in-game results below were taken on branch `26.3`.

### In-game check (2026-11-08, branch `26.3`)

A real Minecraft 26.3 client (offline test account) joined an isolated Paper 26.3 build 218 server and the
Fabric dev server. Every command was typed in chat and checked on third-person screenshots:

| Command | Paper 26.3 | Fabric 26.3 |
|---|---|---|
| `/sit` | seated, "Press Left Shift to dismount" | seated |
| `/lay`, `/lay <direction>` | lying copy, no bed / bed screen / darkening | lying copy |
| `/legsup` | lying copy with legs up | lying copy with legs up |
| `/bellyflop` | face-down copy | face-down copy |
| `/spin` | riptide spin copy | riptide spin copy |
| `/crawl` | crawling pose | crawling pose |
| `/stand` height | back to 151.0 from 150.375 | back to 151.0 from 150.375 |

- Fabric GameTests: all 3 required tests pass (`JAVA_TOOL_OPTIONS=-Dfabric-api.gametest=true ./gradlew -p fabric runServer`).
- The pose copy uses the player's own skin data; offline test accounts have none, so the copy shows a default skin.

### Not yet checked

- Player-to-player sitting and `/sit playertoggle` with two real players (Paper only).
- A second observer player watching someone else's pose.
