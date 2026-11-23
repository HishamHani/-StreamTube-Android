# StreamTube Android

An Android-first porting workspace for Stream Tube, the Rust/GPUI desktop YouTube client.

## Implemented in the current Android prototype

- Native Kotlin + Jetpack Compose mobile interface
- YouTube search through the Innertube WEB endpoint (no Google sign-in required for search)
- Search results with thumbnails, titles, channel names and duration
- In-app embedded YouTube playback using the YouTube iframe player
- Experimental Google/YouTube device-code sign-in UI; it stores tokens using Android encrypted shared preferences

## Not implemented yet

- Authenticated account feeds and subscription synchronization
- Native stream extraction / ExoPlayer playback (current player is the official embedded YouTube player)
- SponsorBlock, DeArrow and Return YouTube Dislike integration
- Downloads, offline library, watch history synchronization, comments, Shorts and live chat
- Full migration of desktop Rust business logic

## Build

GitHub Actions builds a debug APK on pushes to `main` and manual workflow dispatches.

1. Open the **Actions** tab.
2. Select **Android APK**.
3. Open a successful run.
4. Download the `streamtube-android-debug` artifact and install the APK.

## Important notes

- YouTube's Innertube endpoints are undocumented and may change or rate-limit requests.
- Embedded playback can be restricted for some videos or regions.
- The device-code sign-in endpoint is an undocumented YouTube TV flow and may be changed or disabled by Google. A successful sign-in currently stores the account tokens, but authenticated feeds are not wired into the app yet.
- This is an alpha port, not feature parity with the desktop app.

## Source

Desktop project: https://github.com/Avaneesh-Inamdar/Stream-Tube-Desktop
