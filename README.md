# NK PLAYER — Android

A native Android audio/video player built with Kotlin, Jetpack Compose, Material 3 and AndroidX Media3.

## Current development milestone: v0.3.0

### Implemented in source
- Dark/neon interface with Home, Audio, Video and Studio tabs.
- Import local audio/video using Android's document picker, with persisted access to user-selected files (no broad storage permission requested).
- Custom NK PLAYER launcher icon and in-app brand mark, with adaptive icon support on Android 8.0+.
- Android 13+ notification-permission request for notification-related features; playback can still be used if declined.
- Search, import and remove media from the in-app library without deleting the original device file; persist the library locally with Preferences DataStore, including provider MIME types so document-picker video URIs are not classified by filename alone.
- Player UI observes Media3 play/pause, playback-state and queue-transition events; selected track follows previous/next navigation and playback failures show a recoverable message.
- Five saved accent themes: Neon Blue, Cyber Purple, Emerald Green, Crimson Red and Golden Amber.
- Media3 playback queue: selecting an item queues the visible list and supports next/previous queue navigation.
- Playback UI connects to a MediaSessionService, providing the foundation for background playback and system media controls.
- Full-screen video player mode and Android Picture-in-Picture entry when a video is playing and the user leaves the app.
- In-player selection of supported embedded subtitle tracks, with an option to turn subtitles off.
- Load external `.srt` and `.vtt` subtitle files for the currently playing video through Android's document picker.
- First playback-UI polish pass: now-playing status, previous/play-pause/next controls, seek bar and elapsed/total time labels.
- Home-screen redesign pass: gradient media hero, clearer import action, library section/count, compact status cards and a first-run import tip.
- Audio/video library UI pass: distinct gradient headers, file counts, descriptive labels and a quick import action on each tab.
- Studio UI pass: premium header with active-theme status, selectable accent-theme preview cards and a clearly marked planned audio-effects section.
- GitHub Actions assembles a debug APK and uploads it as a downloadable workflow artifact.

## Build and download

The repository's **Actions** tab runs `gradle assembleDebug` for pushes to `main`. When the workflow succeeds, open the latest successful run and download the artifact named `nk-player-debug-apk`.

- [Latest build runs](https://github.com/hackritesh16/-NK-PLAYER-ANDROID/actions)
- [Source code](https://github.com/hackritesh16/-NK-PLAYER-ANDROID)

Local build requirements: JDK 17, Android SDK Platform 35 and Gradle 8.9.

```bash
gradle assembleDebug
```

The output APK is normally generated at `app/build/outputs/apk/debug/app-debug.apk`.

## UI/UX Pro Max skill

The UI/UX Pro Max skill is installed under `.agents/skills/ui-ux-pro-max/` with searchable data, Python tools, references, and Jetpack Compose guidance. The NK PLAYER design system is documented in `design-system/nk-player/MASTER.md`.

## Still to implement or verify

This is an active development build, not yet a production-ready release. The latest code must pass CI and still needs real-device testing. The following remain planned or incomplete:
- Verify background playback, notification controls, audio focus and playback restoration on a real device (manifest declarations are present; device behavior still needs testing).
- Verify full-screen video and Picture-in-Picture behavior on real devices, including orientation and system UI interactions.
- Subtitle synchronization/offset controls and broader subtitle-format support.
- Audio equalizer and effects lifecycle (the Studio screen currently labels these as planned; no fake controls are presented).
- Robust metadata/MIME-type detection, format-specific error handling and accessibility polish.
- Continue the broader UI redesign across Audio, Video and Studio tabs; verify responsive layout and accessibility polish.
- Automated UI tests, release signing and release APK.

The video/audio classification used for list filtering is currently a filename-extension heuristic; it does not guarantee that every file format is playable.

## Privacy

Media is selected through Android's document picker. The library stores selected URI strings and display names locally; media files are not uploaded to a server.
