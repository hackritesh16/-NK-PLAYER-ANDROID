# NK PLAYER — Android

A native Android audio/video player built with Kotlin, Jetpack Compose, Material 3 and AndroidX Media3, with a dark glassmorphism interface.

## Current development milestone: v0.5.0 (glass UI)

### Interface
- Glassmorphism on every screen: animated gradient background, real source-backed backdrop blur on Android 12+ (API 31+), frosted translucent cards with hairline borders, floating glass navigation bar and mini player. Android 12 is the minimum supported Android version.
- Brand fonts: Unbounded (headings) and Manrope (text). They are downloaded automatically by the `fetchFonts` Gradle task, so the first build needs internet access.
- Full-screen Now Playing screen with cover art (embedded artwork or a video frame) as a blurred backdrop, seek bar, shuffle / repeat / speed / sleep timer, and subtitle controls for video.
- Cover-art and video thumbnails in the library lists.
- Five saved accent themes: Neon Blue, Cyber Purple, Emerald Green, Crimson Red and Golden Amber (each re-tints the whole gradient).

### Player
- Home, Audio, Video and Studio tabs.
- Import local audio/video through Android's document picker with persisted access (no broad storage permission). The library is stored locally with Preferences DataStore, including provider MIME types.
- Search, import and remove media without deleting the original device file.
- Media3 playback queue with next/previous navigation; playback failures show a recoverable message.
- MediaSessionService for background playback and system media controls; Android 13+ notification-permission request.
- Full-screen video mode and Picture-in-Picture when a video is playing and the user leaves the app.
- Embedded subtitle track selection, plus external `.srt` and `.vtt` files.
- Studio tab: system Equalizer with presets and per-band sliders, Bass Boost and Virtualizer. Effects live in the playback service and their settings persist. Availability depends on the device.
- Shuffle, repeat (off / all / one), playback speed (0.5x to 2x) and a sleep timer (15/30/45/60 min).

## Build and download

The **Actions** tab runs `gradle assembleDebug` for pushes and pull requests to `main`, and can be started by hand with **Run workflow** on any branch. When it succeeds, open the run and download the artifact named `nk-player-debug-apk`.

- [Latest build runs](https://github.com/hackritesh16/-NK-PLAYER-ANDROID/actions)

Minimum supported OS: Android 12 (API 31). Local build requirements: JDK 17, Android SDK Platform 35, Gradle 8.9 and internet access (for the font download and Maven dependency resolution). Haze provides the Compose backdrop-capture and blur effect.

```bash
gradle assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Still to implement or verify

This is an active development build, not a production release. The glass UI needs real-device testing.
- Verify background playback, notification controls, audio focus and playback restoration on a real device.
- Verify full-screen video and Picture-in-Picture, including orientation and system UI.
- Validate backdrop-blur quality and performance on real Android 12 (API 31) and newer devices. API 31 blur is explicitly enabled despite Haze's conservative default, so test for rendering/invalidation issues. Complete the glass restyle of the Equalizer card.
- Video gestures (volume, brightness, seek), resume position, and subtitle sync/offset and more formats.
- Wider format support: Media3 relies on the device's codecs, so some audio codecs and containers may not play. A LibVLC or FFmpeg-based engine is the path to "every format".
- Robust MIME-type detection (video/audio filtering currently also falls back to a filename-extension heuristic), accessibility polish, automated UI tests, release signing and a release APK.

## Privacy

Media is selected through Android's document picker. The library stores selected URI strings and display names locally; media files are not uploaded to a server.
