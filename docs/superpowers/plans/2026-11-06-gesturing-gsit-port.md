# Gesturing GSit Feature Port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the requested GSit 3.8.0 Minecraft 26.3 gesture and seat mechanics into a standalone Gesturing plugin that runs on Paper 26.3 and GLAP1 26.3.

**Architecture:** Use Paperweight userdev for the matching Paper 26.3 server internals and port only the GSit 26.3 pose renderer, seat entities, player-seat chain, and crawl collision entities. Keep commands, ownership/state cleanup, and seat selection in Gesturing-owned services; never declare or bundle GSit as a runtime dependency.

**Tech Stack:** Java 25, Gradle 9.8.2 wrapper, Paperweight userdev 2.0.0-beta.23, Paper/GLAP 26.3, JUnit Jupiter.

**Spec:** `docs/superpowers/specs/2026-11-06-gesturing-gsit-feature-port-design.md`

## Global Constraints

- Runtime targets Paper 26.3 and GLAP1 26.3 only.
- GSit must not be installed or bundled at runtime.
- `/sit` chooses the supporting block under the player and works for every block material, including stairs and slabs.
- Right-clicking blocks never starts a sitting gesture.
- Right-clicking another player may start player seating; players can opt out.
- Gestures stop and clean temporary entities on sneak, movement, damage, teleport, quit, and plugin disable.
- Ported GSit-derived code retains upstream notices; Gesturing is GPL-3.0-only.
- Preserve the required four co-author trailers on any commit.

## Review Focus

- Upper slabs and upside-down stairs: seat height and clearance use actual block shape; tested by `SeatGeometryTest`.
- Block click versus `/sit`: only the command sits on blocks; tested in a Paper/GLAP server session.
- Player sitting chains: opt-out, dismount, disconnect, and target quit remove all intermediary entities; tested in a GLAP server session.
- Pose packet visibility and viewer cleanup: self and nearby clients see transitions and restoration; tested in Paper and GLAP sessions.
- Crawling under terrain and on partial-height floors: collision entities update without leaks; tested in a server session.

---

### Task 1: NMS Build Setup, Licensing, and Pure Geometry Tests

**Files:**
- Modify: `build.gradle.kts`
- Create: `LICENSE`
- Create: `THIRD_PARTY_NOTICES.md`
- Create: `src/test/java/dev/ledge/gesturing/sit/SeatGeometryTest.java`
- Create: `src/main/java/dev/ledge/gesturing/sit/SeatGeometry.java`

**Interfaces:**
- Produces: `SeatGeometry.Offset offsetFor(BlockData data)` and a pure data type containing x/y/z offsets and yaw; no world/entity access in the geometry calculator.

- [ ] Add JUnit Jupiter and Paperweight userdev, pin Paper dev bundle to `26.3.build.218`, and configure the plugin artifact to reobfuscate for Paper/GLAP runtime.
- [ ] Write `SeatGeometryTest` cases for full blocks, bottom/top slabs, straight stairs in four directions, and inner/outer stair corners.
- [ ] Run the focused tests and confirm failures identify the missing geometry behavior.
- [ ] Implement `SeatGeometry.offsetFor(BlockData)` from GSit's 26.3 `SitService` geometry and add tests for all supported stair shapes and slab halves.
- [ ] Add the full GPL-3.0 license text and a provenance notice identifying GSit 3.8.0 (`04b792026acf95b63f198691d85218a1023e2f92`), the source paths ported, and Gesturing modifications.
- [ ] Run `./gradlew test` and confirm geometry tests pass.

### Task 2: Port 26.3 Seat and Pose Entities

**Files:**
- Create: `src/main/java/dev/ledge/gesturing/nms/v26_3/SeatEntity.java`
- Create: `src/main/java/dev/ledge/gesturing/nms/v26_3/PlayerSeatEntity.java`
- Create: `src/main/java/dev/ledge/gesturing/nms/v26_3/BoxEntity.java`
- Create: `src/main/java/dev/ledge/gesturing/nms/v26_3/PoseRenderer.java`
- Create: `src/main/java/dev/ledge/gesturing/nms/v26_3/CrawlRenderer.java`
- Modify: `THIRD_PARTY_NOTICES.md`

**Interfaces:**
- Consumes: `SeatGeometry.offsetFor(BlockData)` from Task 1.
- Produces: create/remove lifecycle methods for block-seat entities, player-seat intermediaries, pose NPC packet sets, and crawl box packet sets.

- [ ] Port the GSit 26.3 seat armor stand, player-seat intermediary, and invisible crawl-box entity behavior, preserving attribution headers.
- [ ] Port pose packet construction for LAY, BELLYFLOP, LEGS_UP, and SPIN, including equipment/name visibility and packet cleanup.
- [ ] Add a regression test around pure pose mapping and packet-state decisions before production integration; verify it fails for missing mappings.
- [ ] Implement pose mapping so lay/legs-up use sleeping, belly flop uses swimming, and spin uses spin-attack pose plus its spin flag.
- [ ] Run focused unit tests and `./gradlew compileJava`.

### Task 3: Gesturing State, Commands, and Player/Block Seating

**Files:**
- Modify: `src/main/java/dev/ledge/gesturing/GesturingPlugin.java`
- Create: `src/main/java/dev/ledge/gesturing/gesture/GestureManager.java`
- Create: `src/main/java/dev/ledge/gesturing/sit/SeatManager.java`
- Create: `src/main/java/dev/ledge/gesturing/crawl/CrawlManager.java`
- Modify: `src/main/resources/plugin.yml`
- Create: `src/test/java/dev/ledge/gesturing/gesture/GestureCommandParserTest.java`

**Interfaces:**
- `GestureManager.startPose(Player, PoseType, @Nullable String direction)`, `startSit(Player)`, `startPlayerSit(Player, Player)`, and `stop(Player, StopReason)` own a player's single active gesture and clean all associated entities.
- `SeatManager.sitAtSupportingBlock(Player)` resolves the supporting block beneath the player, computes seat geometry, and seats there for every material.
- `CrawlManager.toggle(Player)` starts/stops crawling; configurable automatic double-sneak/corridor starts are kept separate from the command toggle.

- [ ] Add command-parser tests for `/lay` direction validation and command-name/argument dispatch; run to observe expected failures.
- [ ] Register `/sit`, `/lay`, `/bellyflop`, `/legsup`, `/spin`, `/crawl`, and `/stand`; preserve `/sit toggle`, `/sit playertoggle`, and `/crawl toggle` where applicable.
- [ ] Implement `/sit` using the supporting block and geometry calculator; do not add block-interaction listeners for sitting.
- [ ] Implement the requested pose commands, direction mapping, tab completion, and stop/toggle behavior.
- [ ] Implement player right-click sitting with opt-out and safe cleanup for both players and intermediate entities.
- [ ] Implement crawl command, double-sneak/corridor behavior, box updates, and cleanup on all stop conditions.
- [ ] Run `./gradlew test` and `./gradlew build`; confirm plugin.yml contains no GSit dependency.

### Task 4: Paper 26.3 and GLAP1 26.3 Integration Verification

**Files:**
- Modify if needed: implementation files from Tasks 1–3.
- Build: `build/libs/gesturing-0.3.0.jar`

**Interfaces:**
- Consumes: full Gesturing plugin from Tasks 1–3.
- Produces: a build report and startup/runtime logs from two isolated temporary servers; do not alter the user's existing server or worlds.

- [ ] Start a clean temporary Paper 26.3 server with only Gesturing and verify startup, all command registrations, and absence of a GSit dependency.
- [ ] Exercise `/sit` on a full block, stair, bottom slab, and top slab; verify block right-click alone does not sit.
- [ ] Exercise `/lay` in all four directions, `/bellyflop`, `/legsup`, `/spin`, `/crawl`, `/stand`, player sitting, and opt-out.
- [ ] Verify movement, sneak, damage, teleport, disconnect, and plugin shutdown restore players and remove temporary entities.
- [ ] Repeat startup and the same feature checks on a clean temporary GLAP1 26.3 server using the existing GLAP1 build artifacts.
- [ ] Record any behavior not verifiable without a graphical Minecraft client and report that limitation clearly.
- [ ] Run final `./gradlew clean build` and `git diff --check`.
