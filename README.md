# StreamTube Android Port

A mobile-first Android port workspace for the Stream Tube desktop project.

## Current Android foundation

- Native Android application module using Kotlin and Jetpack Compose
- Dark Stream Tube-inspired mobile interface
- Bottom navigation for Home, Explore, Subscriptions, Downloads and Settings
- Internet permission declared for upcoming service integration
- GitHub Actions workflow that builds and uploads a debug APK artifact

## Build

The GitHub Actions workflow runs on pushes to `main`, pull requests, and manual dispatch.

It builds the debug APK with JDK 17 and Gradle 8.9:

```bash
gradle --no-daemon :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Important source-project notes

The supplied desktop project is Rust (edition 2024), uses GPUI for its desktop UI and libmpv for playback, and targets Windows and Linux. It contains no Android or Gradle project. A direct target switch cannot produce a working Android app.

This repository therefore starts an Android-native implementation. Desktop-only UI, windowing, native player bindings, and platform integrations must be replaced or adapted. The original project's API and data logic can be evaluated for reuse module by module.

## Porting roadmap

- [x] Create Android project structure
- [x] Add a touch-friendly Compose application shell
- [x] Add GitHub Actions debug APK build
- [ ] Connect search and video catalogue data
- [ ] Implement video playback using an Android-compatible player integration
- [ ] Port subscriptions, local library and history
- [ ] Add SponsorBlock, DeArrow and Return YouTube Dislike integrations
- [ ] Add downloads, captions, background playback and picture-in-picture
- [ ] Test on physical Android devices

## Current limitations

The current app is a buildable UI foundation, not a feature-complete port. Search, playback, sign-in, downloads and the desktop app's integrations are not connected yet. A successful CI build will provide a debug APK for installation and testing; it does not imply those features are complete.
