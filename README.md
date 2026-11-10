# Harbour

A sleek, dark comic reader for Android tablets. Point it at the folder your comics live in and it
does the rest.

## What it does

- **Your folders, your library.** Pick one or more folders. Harbour finds every `.cbz` inside them,
  including subfolders, and each folder becomes a series.
- **Covers and stacks.** Series show as cover stacks; issues sort naturally (2 comes before 10).
- **Continue reading.** The home screen keeps your in-progress comics up front, with page progress.
- **A proper reader.** Full screen, swipe or tap the edges to turn pages, tap the middle for
  controls, pinch or double-tap to zoom, and a page slider for jumping around.
- **Manga mode.** Switch any series to right-to-left; Harbour remembers it per series.
- **Next issue.** On the last page, one tap opens the next issue in the series.
- **Mark as read.** Long-press an issue to mark it read or unread.

## Getting it onto your tablet

Every push to `main` builds the app on GitHub and publishes it as a release.

1. On the tablet, open `https://github.com/Shane572/Harbour/releases/latest`.
2. Tap **Harbour.apk** to download it, then open it.
3. If Android asks, allow your browser to install apps ("Install unknown apps").
4. Open Harbour and choose your comics folder.

After that, Harbour updates itself: when a newer build is released, the library shows
**Update available**, and one tap downloads and installs it over the top, keeping your folders
and reading progress. You can also check by hand under the settings (gear) button.

## Building it yourself

Open the project in Android Studio, or run `./gradlew assembleDebug` with the Android SDK
installed. The APK lands in `app/build/outputs/apk/debug/`.

## Releases

Every push to `main` is released straight away, so the tablet offers it next time Harbour opens.
The **Preview builds** switch in settings is left over from testing on an emulator; it does no harm.
The **Release a build** workflow can still promote an older preview build if one exists.
