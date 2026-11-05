# Gesturing

Gesturing is a standalone 26.3 plugin for Paper and GLAP1. It does not depend
on GSit at runtime. Shape-aware seats and the invisible NMS seat entity are
adapted from GSit 3.8.0 under GPL-3.0-only; attribution and license are in
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

Copy `build/libs/gesturing-0.3.0.jar` into the server's `plugins` folder and
restart the server. `/sit` is command-only; right-clicking blocks does not sit.
