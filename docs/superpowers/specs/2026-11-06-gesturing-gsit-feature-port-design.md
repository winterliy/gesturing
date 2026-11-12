# Gesturing GSit Feature Port Design

## Intent

Expand Gesturing into a standalone Paper 26.3 / GLAP1 plugin with the requested
GSit-style poses and seating behavior. The server must not need GSit installed.

## Goals

- Port the required GSit implementation patterns for sitting, lying and crawling.
- Support `/sit` on the block under the player for every block material, including
  stairs and slabs with their distinct shapes.
- Do not start sitting by right-clicking blocks.
- Keep player-to-player sitting available through the interaction described below.
- Add `/bellyflop`, `/legsup`, `/spin`, and `/crawl`, plus the previously agreed
  directional `/lay <east|west|south|north>` and `/stand`.
- Preserve safe exit and cleanup on movement, sneaking, damage, teleport, quit,
  and plugin disable.
- Build and verify against Paper 26.3 and the user's GLAP1 26.3 server, with no
  GSit runtime jar.

## Non-goals

- Copying all of GSit, its configuration surface, localization, update checks,
  region integrations, PlaceholderAPI support, or administrative commands.
- Block right-click seating.
- Support Minecraft versions other than 26.3 in this port.

## Approaches

1. Use Paper's public pose API and simple seat entities. This avoids version-
   specific server internals, but does not reproduce GSit's pose rendering and
   seat placement as closely.
2. Port the required GSit 26.3 internals into Gesturing. This best matches the
   requested GSit behavior, but requires maintaining version-specific internals
   and GPL-3.0-only attribution and licensing.

**Selected:** approach 2, as requested by the user. Port only code necessary for
the goals above and adapt it to Gesturing's command/configuration surface.

## Behavior

- `/sit` seats the player at their current location using the supporting block
  beneath them. Every block material is eligible; geometry such as stair shape,
  slab half, and block height determines the seat offset and facing where the
  upstream implementation supports it. It does not require or trigger on a
  block click.
- Clicking another player sits on that player, matching GSit's player-sit
  interaction. The receiving player can opt out through the supported toggle.
- `/lay <direction>` lies on the back facing the requested cardinal direction.
- `/bellyflop` lies face-down; `/legsup` lies on the back with legs raised;
  `/spin` spins in place; `/crawl` toggles crawling.
- Sneaking exits a gesture. Movement, damage, teleport, disconnect, and plugin
  shutdown also stop it and remove any temporary seat entities.
- Commands expose normal per-feature permissions. Block-click sit permissions
  and behavior are omitted.

## Licensing and provenance

The upstream [GSit repository](https://github.com/Gecolay/GSit) identifies its
license as GPL-3.0-only. Any ported implementation must preserve upstream
copyright and license notices, identify Gesturing's modifications, include the
GPL-3.0 license text, and license the resulting derivative under GPL-3.0-only.
If a binary is later distributed, its corresponding source must be made
available under the GPL terms.

## Verification

- Compile and package Gesturing using Java 25 against Paper API 26.3.
- Run Paper 26.3 with Gesturing as the only gesture plugin and verify command
  registration and basic pose transitions.
- Run the same scenarios on a clean temporary GLAP1 26.3 server.
- Confirm no GSit dependency is declared, bundled, or needed at runtime.
