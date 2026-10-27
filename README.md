# Marginalia

Paste a YouTube link. Read the transcript. Hold a line to highlight it. Clip it to Obsidian
with your Web Clipper templates. An Android app in Kotlin and Jetpack Compose on
Material 3 Expressive.

## What it does

- **Paste or share a link.** Paste button, or share from the YouTube app, or tap a YouTube link.
- **Preview first.** Thumbnail, title, author, with "Pin player" and "Highlighting" switches.
- **Reader.** The video plays in YouTube's own embedded player. The transcript sits below,
  grouped into readable lines and split by chapters. The current line gets a red bar.
- **Highlight.** Hold a line. Keep your finger down and drag to take more lines. Hold a
  highlighted line to erase. Each highlight keeps a timestamp link and an optional note.
- **Clip to Obsidian.** Uses your Web Clipper templates: properties, `{{variables}}` and
  `|filters`. Four ways to finish, as in the clipper: Open Obsidian, Save file, Copy, Share.
- **Background play.** Audio continues with the screen off, with a media notification.
- **Customise.** Five looks, accent, motion, corner roundness, reading font and size,
  line length, highlight colour and format, templates, vaults.

## Build

Needs JDK 21 and the Android SDK (platform 36).

```bash
JAVA_HOME=/path/to/jdk-21 ./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/app-release.apk`. It is signed with the
local debug key so it installs directly.

Unit tests: `./gradlew testDebugUnitTest`. To also test against the real YouTube:
`YT_LIVE=1 ./gradlew testDebugUnitTest`.

## Built to need few updates

- Playback uses YouTube's embedded player, the surface YouTube keeps stable.
- Captions use the same mobile client calls YouTube's own apps use, with an Android client,
  then an iOS client as a fallback. Client versions can be changed in Settings, Advanced.
- Titles fall back to oEmbed. The publish date is best effort.
- Templates are plain data, imported from a Web Clipper settings export.

## Notes

- Material 3 Expressive needs `material3 1.5.0-alpha`. The pair used is Compose BOM
  2026.02.01 with material3 1.5.0-alpha14. Later builds need compileSdk 37 and AGP 9.1.
- Not yet tried on a physical phone.
