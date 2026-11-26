# Familiar Controls

A Fabric mod for Minecraft 26.2 that restores familiar control semantics: **left click drives the main hand**, **right click drives the off hand only** (no cross-hand fallback).

## Controls

- **Left click**
  - Entity/air (on press): scene interaction → item action → attack.
  - Block (short release): scene interaction → placement → item action if the earlier steps pass.
  - Block (hold): mine/break.
  - Items with a use duration (bow, food, etc.) start using on press and release on let go.
- **Right click**
  - Off hand only; never falls back to the main hand.
- **Sprint**
  - Shift-to-sprint is self-managed and gated by the vanilla sprint binding, so bind sprint to Left/Right Shift.
  - Crouch remains on the vanilla C key; this mod does not remap it.

## Build

Requires JDK 25.

```sh
./gradlew build
```

The distributable jar is `build/libs/familiar-controls-<version>.jar`. Put it in `mods/` alongside Fabric Loader 0.20.1+ and Fabric API 0.161.0+26.2.

## Releases

Nightly prereleases are published by `.github/workflows/nightly-release.yml` at 18:00 UTC (02:00 Asia/Taipei) and on manual dispatch. The workflow uses the `LUICL_TOKEN` repository secret so releases are created under the luicl identity.

## License

CC0-1.0. See [LICENSE](LICENSE).
