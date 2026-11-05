# Gesturing

Gesturing is a standalone 26.3 plugin for Paper and GLAP1, with a separate
Fabric server mod. It does not depend on GSit at runtime. Shape-aware seats
and the invisible NMS seat entity are adapted from GSit 3.8.0 under
GPL-3.0-only; attribution and license are in
`THIRD_PARTY_NOTICES.md` and `LICENSE`.

## Requirements

- Paper 26.3 or GLAP1 for Minecraft 26.3
- Java 25 or newer

## Commands

- `/sit` — sit on the supporting block below you, including stairs and slabs.
- `/sit playertoggle` — allow or prevent other players from sitting on you.
- `/sit toggle` — toggle block sitting.
- `/lay <east|west|south|north>` — lie down facing the selected direction.
- `/bellyflop`, `/legsup`, `/spin`, `/crawl` — start the corresponding pose.
- `/stand` — stop the current gesture and restore the previous pose.

Moving, sneaking, taking damage, teleporting, or leaving the server also ends
the pose. The four directions are available through tab completion.

## Build and install

```sh
./gradlew build
```

Copy `build/libs/gesturing-0.3.2.jar` into the server's `plugins` folder and
restart the server. `/sit` is command-only; right-clicking blocks does not sit.

### Fabric server mod

The Fabric build targets Minecraft 26.3 and Java 25. Build it with:

```sh
./gradlew -p fabric build
```

Copy `fabric/build/libs/gesturing-fabric-0.3.2.jar` into the Fabric server's
`mods` folder. The server must have Fabric Loader 0.20.1 or newer and Fabric
API 0.162.0+26.3 or newer. The Fabric build supports `/sit`, `/lay`,
`/bellyflop`, `/legsup`, `/spin`, `/crawl`, and `/stand`. Player-seat toggling
is currently Paper-only. Fabric GameTests exercise full blocks, slabs, stairs,
directional blocks, sit toggling, four `/lay` directions, every pose, invalid
inputs, and cleanup after movement, teleport, sneaking, damage notification,
and disconnect.

## Verification

### Current status: 0.3.2

Gesturing 0.3.2 is released for Minecraft 26.3. The release includes the
Paper/GLAP plugin and Fabric server mod:

- [GitHub release](https://github.com/winterliy/gesturing/releases/tag/v0.3.2)
- [Paper/GLAP plugin JAR](https://github.com/winterliy/gesturing/releases/download/v0.3.2/gesturing-0.3.2.jar)
- [Fabric server mod JAR](https://github.com/winterliy/gesturing/releases/download/v0.3.2/gesturing-fabric-0.3.2.jar)

The latest changes align sitting direction with directional support blocks and
expand block, command, and pose-cleanup coverage.

### Completed checks

- `./gradlew clean test build` — passed.
- `./gradlew -p fabric clean test build` — passed.
- Fabric 26.3 in-server GameTests — all 3 required tests passed. The tests
  execute registered commands with a mock player and cover full blocks,
  bottom/top slabs, stairs, directional blocks, all four `/lay` directions,
  poses, invalid input, and cleanup after movement, teleport, sneaking, damage
  notification, and disconnect.
- Paper 26.3 and GLAP 26.3 startup checks — both loaded and enabled the 0.3.2
  plugin. All seven command help entries were present, and console invocation
  correctly rejected player-only commands.

Run the Fabric in-server command tests with:

```sh
JAVA_TOOL_OPTIONS=-Dfabric-api.gametest=true ./gradlew -p fabric runServer
```

### Remaining validation plan

- [ ] Join Paper and GLAP with a Minecraft 26.3 client and visually try `/sit`,
  `/sit toggle`, all four `/lay` directions, `/bellyflop`, `/legsup`, `/spin`,
  `/crawl`, and `/stand`.
- [ ] On Paper and GLAP, try sitting on stairs, both slab halves, full blocks,
  and directional blocks facing north, south, east, and west; confirm the seat
  position and facing look right in-game.
- [ ] Verify Paper/GLAP player-to-player sitting, `/sit playertoggle`, and
  permission behavior with both permitted and unpermitted players.
- [ ] Join a Fabric 26.3 server with a client and confirm pose and seat updates
  are visible, then check that movement, sneaking, damage, teleport, and
  disconnect restore the player as expected.
- [ ] Check tab completion and command behavior with a regular player account
  on both implementations.
