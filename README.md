# Room Mapper — Android

Room Mapper is a polished, native Android app for finding the strongest mobile-data spot inside a room. Mark a few points on a simple floor map, run a bounded verification, and compare Jio, Airtel, or another active route using a profile that matches how you use the phone.

## What is included

- Native Kotlin + Jetpack Compose UI with light/dark mode, large-text support, top navigation, and TalkBack labels.
- Guided room mapping that works without AR hardware plus a live ARCore spatial mode with the camera feed, tracking lock, feature-point cloud, surface hit-tests, anchored scan nodes, origin/path telemetry, and a guided fallback.
- Signal and radio readings when Android permissions and the phone expose them, with a clear permission-limited state when they do not.
- Route validation, latency/reliability sampling, bounded download/upload tests, profile-weighted scoring, and data-budget controls (25/100/300 MB).
- Local, private room data and the last 50 scan-history entries. No account or backend is required.
- Safe cancellation, network/permission error states, unit tests for scoring, lint, and a GitHub Actions workflow that uploads the debug APK.

## Build on GitHub Actions

1. Create a new GitHub repository and upload the contents of this folder (the files inside it, not the parent folder).
2. Push to any branch, or open **Actions → Android CI → Run workflow**.
3. When the job is green, open the run and download the artifact **`RoomNetworkMapper-debug-apk`**.
4. Install `app-debug.apk` on an Android phone. Allow camera access for **Live AR scan**, and phone/location access for detailed radio readings. Test on both Jio and Airtel SIM routes.

The workflow installs JDK 17, Android SDK 35, and Gradle 8.13. This project intentionally does not commit a generated `gradle-wrapper.jar`; the CI action provisions the pinned Gradle version instead.

## Local build (Android Studio or an installed Gradle)

```bash
gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Measurement behavior

Each verification first confirms that the active route is cellular and validated. It then performs four small latency probes plus bounded transfer samples. The selected budget is a cap, not a promise of a carrier speed. Results are point-in-time observations and are intentionally labelled as such; walls, device position, people, weather, and network load can change them.

The score is a transparent 0–100 comparison, not a carrier guarantee. Streaming emphasizes download, video calls emphasize upload and delay, gaming emphasizes delay/variation, and Balanced uses a practical blend.

## Permissions and privacy

`INTERNET` and `ACCESS_NETWORK_STATE` are required for the test itself. `READ_PHONE_STATE` plus location access are requested only to read the radio information Android makes available. Camera access is requested only when Live AR is selected and is used for local AR tracking and node placement. The app stores room geometry, preferences, and history in private on-device storage. See [`docs/PRIVACY.md`](docs/PRIVACY.md) and [`docs/DEVICE_TEST_MATRIX.md`](docs/DEVICE_TEST_MATRIX.md).

## Release gate

The included debug build is intended for first-device validation. Before publishing a release, follow [`docs/RELEASE_CHECKLIST.md`](docs/RELEASE_CHECKLIST.md), test on the exact Android phones/SIM combinations you support, and replace the debug artifact with a signed release build.
