# NK PLAYER — Master Design System

## Product
Local-first Android audio/video player. Playback reliability, readable metadata, and quick access to controls take priority over decorative effects.

## Visual direction
Dark-first cinematic media utility with restrained neon accents.
- Canvas: #080B14
- Elevated surface: #131A2A
- Raised/selected surface: #17283B
- Primary accent: electric cyan #48E5FF
- Secondary accent: violet #9B72FF
- Primary text: #FFFFFF; secondary text: #CBD5E1; borders: #29344A
- Use gradients only for hero/header surfaces. Cards stay calm and consistent.

## Compose tokens
| Token | Value | Purpose |
|---|---|---|
| Ink | #080B14 | App canvas |
| Panel | #131A2A | Cards, navigation, dialogs |
| Raised | #17283B | Selected rows and raised panels |
| AccentPrimary | #48E5FF | Primary actions, progress, focus |
| AccentSecondary | #9B72FF | Secondary emphasis |
| TextPrimary | #FFFFFF | Titles and important labels |
| TextSecondary | #CBD5E1 | Supporting copy |
| Border | #29344A | Outlines and separators |

Theme accents must not change success, warning, error, or disabled semantics.

## Layout and type
- Use 4dp increments; common gaps are 8, 12, 16, 20 and 24dp.
- Cards use 14–24dp corner radii with consistent component padding.
- Prefer 14–16sp body text. Metadata below 12sp must never carry essential instructions.
- Use uppercase labels sparingly; media titles remain readable and ellipsize where needed.
- Use Material icons with meaningful content descriptions for actions.

## Interaction and accessibility
- Aim for 48dp minimum tap targets.
- Import, play/pause, previous/next, subtitles, full-screen and theme actions need clear feedback and accessible labels.
- Media lists must scroll within available space without hiding player controls.
- Show actionable empty states and recoverable playback errors.
- Preserve selected URI access; do not request broad storage access when the system picker is enough.
- Test font scaling, portrait/landscape, small screens, long filenames, empty library, denied notification permission, playback errors and subtitles.
- Never show fake equalizer controls, dead buttons, misleading progress, or claims of device verification without device tests.

## Component rules
- Media row: media-type icon, readable filename, selected/playing state, minimum 48dp interaction height.
- Mini player: title, actual player state, previous/play/pause/next, seek and duration; reflect queue transitions immediately.
- Import: system document picker, provider display name, persisted URI access, stored MIME type.
- Subtitle picker: validate SRT/VTT using extension and provider MIME type; report unsupported formats.
- Theme card: active border/checkmark and readable label; do not rely on color alone.

## Release checklist
- CI build and static checks pass.
- Playback state and selected track follow Media3 listener events.
- Audio/video classification uses provider MIME type with extension fallback.
- No clipped controls on small screens or large font scale.
- Background playback, notification actions, audio focus, PiP and subtitles are tested on real devices.
- Release signing is separate from the debug CI artifact.

## Reusable Compose implementation

Implementation lives in `app/src/main/java/com/nkplayer/app/NKDesignSystem.kt`.
- `NKDesignTokens`: semantic palette, spacing, corner radii and motion durations.
- `NKGlassCard`: reusable translucent surface with a hairline border and restrained elevation.
- `NKAuroraBackdrop`: lightweight gradient backdrop for hero and header surfaces.

## Reference adaptation policy

The four reviewed references are React/web libraries. Their source components are not Android-compatible and are not installed in this project. Their visual principles are adapted to native Compose:
- Liquid glass: translucent layered surfaces, edge highlights and background-aware contrast; no always-on WebGL refraction or expensive runtime blur.
- Glass UI: semantic theme roles and reusable controls rather than one-off hardcoded styles.
- Liquefy: motion should feel spring-like and tactile, but remain subtle and respect reduced-motion/accessibility needs.
- Viberdy: aurora gradients and spatial depth are reserved for hero/featured surfaces, not every list row.

## Screen rollout

1. Home: aurora hero, shared glass statistics and consistent spacing.
2. Audio and Video libraries: shared header, search treatment, selected media-row state and calm surfaces.
3. Studio: shared theme cards and reusable glass panels around sound settings.
4. Playback shell: shared glass mini-player and navigation surface.
5. Fullscreen video: keep video canvas clear; controls should be high contrast and gesture-first, with no decorative glass over the viewing area unless controls are visible.

## Performance and accessibility guardrails

- No new runtime dependencies for the visual system.
- Avoid full-screen blur, WebGL refraction, continuous background animation and large shadow stacks.
- Keep 48dp minimum interactive targets and semantic content descriptions.
- Use motion for state changes and touch feedback, not decoration that runs indefinitely.
- Preserve all existing Media3 playback, queue, subtitle, PiP, audio-effect and local-library behavior.
