# Third-party notices

Gesturing 0.3.0 includes adaptations of sitting geometry and the 26.3 NMS
invisible seat entity from GSit 3.8.0, commit
`04b792026acf95b63f198691d85218a1023e2f92`.

Upstream: https://github.com/Gecolay/GSit
Copyright (C) Gecolay and contributors.
License: GNU General Public License v3.0 only. See `LICENSE`.

Adapted files/areas:
- `core/src/main/java/dev/geco/gsit/service/SitService.java` — seat geometry.
- `mcv/v26_3/src/main/java/dev/geco/gsit/mcv/v26_3/entity/SeatEntity.java` —
  invisible rideable seat behavior.

Gesturing changes: removed GSit service/config dependencies, exposed a focused
shape calculator, integrated seat lifecycle and commands into Gesturing, and
restricted block seating to `/sit` (no block-click seating).
