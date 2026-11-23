# StreamTube Android Port

This repository is the Android-port workspace for the Stream Tube desktop project.

## Source project assessment

The supplied desktop source is Rust (edition 2024) and uses GPUI for its desktop UI plus libmpv for playback. It targets Windows and Linux; it does not contain an Android Gradle application or an Android-compatible UI entry point. Therefore, the desktop executable cannot be turned into an Android APK by changing a build target alone.

## Porting approach

The Android app needs an Android-native application layer and a mobile playback integration. Reusable API, parsing, and business-logic modules from the Rust project can be evaluated individually, but desktop UI, windowing, platform integration, and player bindings need Android-specific replacements.

## Planned CI

After the Android application module and Gradle wrapper are added, GitHub Actions will build a debug APK on pushes and pull requests, and a release APK on tagged builds. No APK is published until an Android build succeeds.

## Current status

- [x] Android port repository created
- [ ] Add Android application module
- [ ] Port/adapt YouTube data and account flows
- [ ] Integrate Android video playback
- [ ] Add GitHub Actions APK build
- [ ] Build and test APK on an Android device

## Important

This repository is an independent porting workspace. It is not yet an installable Android app.