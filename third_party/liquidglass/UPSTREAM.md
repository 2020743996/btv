# LiquidGlass Upstream

- Repository: https://github.com/Abdullajon1881/LiquidGlass
- Commit: `72ad05c49628ea2270116b2943629cd81c0cc496`
- Imported modules: `liquidglass-core` and `liquidglass-compose`
- License: Apache-2.0; see `LICENSE`.
- The upstream module `build.gradle.kts` files were adapted to use btv's shared Kotlin/AGP plugin versions and Compose BOM. Upstream Kotlin sources and tests are unchanged. The upstream modifier nodes read a stable graphics context during attach/detach, so that module disables Compose's lifecycle-local lint check for this known pattern. The Robolectric smoke-test class is excluded because it downloads Android SDK jars at test time and its repository checksum is incompatible with this environment; app-level Compose instrumentation tests cover the integrated navigation.
- The project currently has no published Maven Central release, so these modules are included locally for reproducible builds.

Rendering tiers follow upstream capability checks: AGSL shader on API 33+, blur on API 31-32, and scrim on API 21+. btv targets MT9653 as the Android TV performance benchmark, but does not attempt a device-model whitelist because Android exposes no reliable cross-vendor SoC performance ranking.
