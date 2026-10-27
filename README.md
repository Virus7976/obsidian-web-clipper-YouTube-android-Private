# Marginalia

Paste a YouTube link. Listen or watch. Read the transcript as it plays. Select exact words,
pick a colour, and clip it all to Obsidian with your Web Clipper templates. An Android app in
Kotlin and Jetpack Compose on Material 3 Expressive.

## What it does

- **Paste or share a link.** Paste, share from the YouTube app, or tap a YouTube link.
- **Plays in the app.** Audio or video, with background play and a media notification.
  If one stream fails it tries the next: YouTube's own app clients, NewPipe Extractor,
  Piped, then Invidious.
- **Reader.** Real selectable text. The word being spoken is marked. Tap a word to jump.
- **Highlight.** Drag the handles to take exact words, or tap Sentence or Paragraph. Five
  colours. Each highlight keeps a timestamp link and an optional note.
- **Obsidian syntax.** Yellow is `==text==`. Other colours are `<mark style="background: ...">`.
- **Your vaults.** Add a vault folder. Marginalia reads notes, folders and tags, and searches
  them (type `#` for tags, `/` for folders).
- **Save sheet.** Fill your own fields first (Big Idea), pick tags from your vault, pick a
  folder. If the video is already in the vault it asks: update it, new note, or another note.
  Updating only adds. It never changes what you wrote.
- **Templates.** Import or export a Web Clipper settings file.
- **Updates.** Update center in Settings. One tap: Android asks for approval, the download
  runs in the app with progress, and the install follows.
- **Look.** Fourteen palettes, a palette maker, Light, Dusk, Dark and OLED, your own font.

## Build

Needs JDK 21 and the Android SDK (platform 36).

```bash
JAVA_HOME=/path/to/jdk-21 ./gradlew assembleRelease
```

Unit tests: `./gradlew testDebugUnitTest`. Against the real YouTube:
`YT_LIVE=1 ./gradlew testDebugUnitTest`.

## Releases

Write `docs/releases/v<version>.md`, then run `scripts/release.ps1 -Version <version>`.
GitHub Actions builds and publishes the APK. See `.github/workflows/release.yml`.

A weekly workflow (`update-playback.yml`) bumps NewPipe Extractor, runs the tests and a build,
and saves the bump only if both pass. It never makes a release.

## Built to need few updates

- Playback uses other people's maintained code, in four tiers, so one broken source is not fatal.
- Captions use the same mobile client calls YouTube's own apps use. Client versions can be
  changed in Settings, Advanced.
- Templates are plain data, imported from a Web Clipper settings export.

## Notes

- The update center reads GitHub releases. For a private repo, add an access token in
  Settings, Updates, Update sources. Or make the repo public.
- NewPipe Extractor is GPL-3. This is fine for personal use. Check the licence before sharing
  the APK.
- Material 3 Expressive needs `material3 1.5.0-alpha`. The pair used is Compose BOM
  2026.02.01 with material3 1.5.0-alpha14.
- Not yet tried on a physical phone. Installing from the update center, the selection menu,
  and vault reading through Android's folder access are the parts most in need of a real test.
