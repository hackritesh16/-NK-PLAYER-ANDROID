# NK PLAYER — Android

Native Android media-player project built with Kotlin, Jetpack Compose, Material 3 and AndroidX Media3 ExoPlayer.

## Current milestone: v0.2.0

- Dark interface with Home, Audio, Video and Studio tabs.
- Import local audio/video through Android's document picker.
- Search the imported list and play selected items with Media3.
- Persist imported URI/name entries and theme selection using Preferences DataStore.
- Request persistable URI access for imported documents.
- Five accent themes: Neon Blue, Cyber Purple, Emerald Green, Crimson Red and Golden Amber.
- GitHub Actions workflow to run a debug APK build on pushes and pull requests.

## Build

Requirements:
- JDK 17
- Android SDK Platform 35
- Gradle 8.9 (the CI workflow installs Gradle directly)

From the repository root:

```bash
gradle assembleDebug
```

The debug APK, if the build succeeds, is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Important limitations

This is an early development milestone, not a production-ready player. The project has not yet been verified by a successful Android build or device test in this session. GitHub Actions is configured to provide that check.

Still to implement:
- MediaSessionService for reliable background playback and notification controls.
- Persistent playback queue and proper next/previous queue behavior.
- Full-screen video, picture-in-picture and orientation handling.
- Subtitle selection and synchronization controls.
- Audio equalizer and audio-session lifecycle handling.
- More robust media metadata, MIME-type detection and error handling.
- UI/instrumentation tests and release signing.

Video/audio classification currently uses the URI's last path segment extension as a simple UI heuristic; it is not a guarantee of decoder support.

## Privacy

The app uses Android's document picker and stores the selected URI strings and display names locally in Preferences DataStore. It does not upload media files to a server.
