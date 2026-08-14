# Billiards Trajectory Analyzer (v1.1)

Android app (Kotlin, single module) that imports a pool table photo, auto-detects
the table/balls/pockets with lightweight local pixel analysis, lets you correct
anything by hand, and calculates a long, color-coded shot trajectory — including
cushion banks and second-ball collisions. Fully offline, no ML models, no cloud.

## ⚠️ About the APK

This build sandbox has no Android SDK and its network is locked to a small
allow-list (github/npm/pypi/crates only) — `dl.google.com` / `maven.google.com` /
`services.gradle.org` are all blocked here, which is where the Android Gradle
Plugin, platform SDK, and Gradle itself get downloaded from. That makes it
**technically impossible to compile a real APK inside this sandbox** — I won't
fake one. What you get instead is a complete, careful, ready-to-build project
in the archive below. Building it yourself is one step:

```
1. Open the project folder in Android Studio (Koala or newer, JDK 17) → let it sync → Run ▶
   -- or, from a terminal with Gradle 8.7+ installed --
2. gradle wrapper && ./gradlew assembleDebug
   APK lands at app/build/outputs/apk/debug/app-debug.apk
```
I re-reviewed every file for correctness, but I genuinely could not execute a
compiler here, so if Android Studio flags anything, send me the error and I'll
fix it in one pass.

## What's new in this pass

- **Automatic detection** (`Detector.kt`): pure pixel-scan, no OpenCV/ML —
  dominant-color clustering finds the felt (table boundary), a snap-to-dark
  search locates the 6 pockets near their expected corner/mid-rail spots, and
  a flood-fill connected-components pass finds ball-sized blobs (with the
  brightest one flagged as the cue ball). Runs once on import (background
  thread) and again on demand via the **DETECT** button — never continuously.
- **Manual correction stays first-class** — every detected item is fully
  draggable/removable, exactly as before, since detection is approximate by design.
- **Richer trajectory engine** (`Physics.kt`):
  - Ghost-ball aim point, cut angle, cue *and* object-ball angle shown separately.
  - Obstruction checks on **both** legs — cue→target *and* target→pocket — each
    with its own collision marker and an approximate dashed secondary deflection path.
  - Single-cushion bank shots via the mirror-reflection method.
  - Continuous 0–100 power slider (plus LOW/MED/HIGH presets) driving a simple
    friction/travel-distance cap; underpowered shots visibly stop short.
  - Pocket entry-direction arrow, and final-resting-position markers for both balls.
- **Settings screen** (gear icon): toggle auto-detect on import, toggle guide
  overlays, set a default power level. Stored in plain SharedPreferences —
  no database.

## Workflow

Home `[ANALYZE IMAGE]` → picks a photo → Analysis screen auto-runs detection →
correct anything with `TABLE / POCKETS / BALLS / CUE / TARGET / POCKET` modes →
set `POWER` (slider or presets) and `BANK` if needed → `CALCULATE` → read the
trajectory + angle/confidence HUD → `RESET` to start over, `DETECT` to re-run
detection at any time.

## Project structure

```
app/src/main/java/com/billiards/analyzer/
  MainActivity.kt        Home screen + system image picker
  AnalysisActivity.kt     Controls, background detection trigger, bitmap loading
  SettingsActivity.kt      Auto-detect / guides / default power
  AppSettings.kt            SharedPreferences wrapper
  TableView.kt              Custom View: rendering + touch + trajectory drawing
  Detector.kt                Local pixel-scan table/ball/pocket detection
  Physics.kt                  All geometry/physics calculations
  Models.kt                    Data classes + PointF vector helpers
```

## Performance & privacy (unchanged guarantees)

No continuous/real-time processing — detection and physics only run on
explicit user action (import / DETECT / CALCULATE). No network permissions,
no accounts, no ads, no analytics, no background services, no root/Accessibility
Service. Bitmaps are downsampled on load and recycled on exit. minSdk 24 / targetSdk 34.

## Getting an APK without installing Android Studio

This project includes `.github/workflows/build-apk.yml`. If you push this
folder to a GitHub repository, GitHub's own servers (which have full Android
SDK access, unlike this sandbox) will automatically build a debug APK for you:

1. Create a free GitHub account and a new empty repository.
2. Upload/push this project folder into it.
3. Open the repo's **Actions** tab → the "Build APK" workflow runs automatically.
4. When it finishes (green check), open that run → **Artifacts** →
   download `BilliardsTrajectoryAnalyzer-debug-apk` → unzip it to get the `.apk`.
5. Copy the `.apk` to your Android phone and install it (allow "install from
   unknown sources" if prompted).
