# StreamTube Android

Android port of [Stream Tube Desktop](https://github.com/Avaneesh-Inamdar/Stream-Tube-Desktop), the Rust/GPUI native YouTube client.

The Android port keeps the original service/API behavior where practical and replaces desktop-only components (GPUI, libmpv, Windows/Linux file dialogs and process integration) with Android equivalents (Jetpack Compose, Media3 ExoPlayer, SQLite and Android storage).

## Ported into the Android prototype

### Search and browsing
- YouTube Innertube WEB search
- Search filters encoded using the same protobuf sp parameter as the desktop client
- Search continuation / pagination
- YouTube search suggestions with Google fallback
- Search results include channel IDs, view/date metadata, live and Shorts flags when present

### Playback
- Native Media3 ExoPlayer playback; no YouTube iframe
- HLS resolution using the desktop client's visionOS → iOS → Android VR client fallback order
- Anonymous visitor ID, including the iOS visitor retry behavior
- HLS master playlist parsing for quality variants and audio renditions
- Four-hour in-memory stream cache; live streams are not cached
- In-player quality cap selector

### Services
- SponsorBlock segment lookup and automatic skipping of the desktop-default categories (Sponsor, Self Promotion and Interaction Reminder)
- SponsorBlock viewed-time reporting
- DeArrow community title lookup (off by default, as in the desktop settings)
- Return YouTube Dislike vote lookup

### Account and library
- YouTube TV device-code sign-in flow
- Encrypted token storage, token refresh and TV Innertube account-profile lookup
- Signed-in YouTube Home and subscriptions feed endpoints
- Local SQLite watch history, subscriptions and Watch Later
- Anonymous local subscriptions via YouTube channel RSS feeds

### Android settings
- Persistent toggles for SponsorBlock, DeArrow titles, Return YouTube Dislike, autoplay-next preference and watch history

## Still to port

The desktop app has a much larger feature set. These are not yet complete in Android:
- yt-dlp/FFmpeg fallback and full adaptive/direct stream fallback behavior
- Quality/codec persistence, separate audio-language selection, audio-only playback and full caption styling
- SponsorBlock category-specific actions, voting, contribution stats and all segment UI
- DeArrow thumbnail replacement
- Local recommendations algorithm, subscription synchronization and full channel/playlist browsing
- Watch history sync/removal, likes, comments/replies, chapters, timestamps and related-video continuation
- Shorts player/feed, live streams/chat and background/PiP playback
- Downloads manager, offline playback, import/export and backups
- Proxy/IPv4 controls, content filters, annotations, heatmap, stats-for-nerds, keyboard/gesture parity and updater

## Build

GitHub Actions builds a debug APK on pushes to main.

1. Open the [Actions page](https://github.com/HishamHani/-StreamTube-Android/actions).
2. Select a successful Android APK run.
3. Download the streamtube-android-debug artifact and install the APK.

## Important notes

- YouTube Innertube and device-code endpoints are undocumented and can change or rate-limit requests.
- Not all videos expose the same HLS streams. Age, region, live status, kids restrictions, account state and YouTube experiments can affect playback.
- The YouTube TV sign-in flow is experimental. Tokens can be stored and account endpoints are wired, but the flow must be tested with a real Google account before it should be considered reliable.
- This is an alpha Android port. It is not yet full feature parity with the desktop app.

## Source

Desktop reference: https://github.com/Avaneesh-Inamdar/Stream-Tube-Desktop
