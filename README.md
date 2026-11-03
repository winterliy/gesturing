# Gesturing

Paper plugin starter for gesture features on GLAP1-compatible servers. Targets Minecraft 26.3 and Java 25.

## Build

```sh
./gradlew build
```

The plugin JAR is created in `build/libs/`.
# Gesturing

Gesturing adds short `/sit` and directional `/lay` commands for the GLAP1 server.
It targets the same Paper-compatible API version as `~/Desktop/GLAP1` (Minecraft
26.3, Java 25) and delegates the actual pose/seat behavior to GSit.

## Requirements

- GLAP1 for Minecraft 26.3
- Java 25 or newer
- GSit 3.6.0 or newer, installed in the server's `plugins` directory

GSit is a required plugin dependency. Gesturing does not copy or bundle GSit's
code; it forwards commands to GSit's namespaced commands so GSit handles its
own permissions, pose events, and safe dismount behavior.

## Commands

- `/sit` — sit using GSit's normal sit behavior.
- `/lay <east|west|south|north>` — face the selected compass direction, then
  lie down using GSit.

Tab completion suggests the four supported directions. Bukkit yaw values are
mapped as south `0°`, west `90°`, north `180°`, and east `-90°`.

## Build

```sh
./gradlew build
```

Copy `build/libs/gesturing-<version>.jar` and the GSit plugin jar to the
GLAP1 server's `plugins` directory, then restart the server.
