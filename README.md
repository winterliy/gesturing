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

Copy `build/libs/gesturing-0.3.1.jar` into the server's `plugins` folder and
restart the server. `/sit` is command-only; right-clicking blocks does not sit.

### Fabric server mod

The Fabric build targets Minecraft 26.3 and Java 25. Build it with:

```sh
./gradlew -p fabric build
```

Copy `fabric/build/libs/gesturing-fabric-0.3.1.jar` into the Fabric server's
`mods` folder. The server must have Fabric Loader 0.20.1 or newer and Fabric
API 0.162.0+26.3 or newer. The Fabric build supports `/sit`, `/lay`,
`/bellyflop`, `/legsup`, `/spin`, `/crawl`, and `/stand`. Player-seat toggling
is currently Paper-only. Fabric GameTests exercise sitting, sit toggling,
four `/lay` directions, all pose commands, and `/stand` restoration.

## Verification

`./gradlew build` and `./gradlew -p fabric clean test build` pass. Run the
Fabric in-server command tests with:

```sh
JAVA_TOOL_OPTIONS=-Dfabric-api.gametest=true ./gradlew -p fabric runServer
```

These GameTests run the registered commands using a mock player in Minecraft
26.3. A separate visual client login was not tested.
