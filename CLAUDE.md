# Harbour

Shane's personal comic reader for his Android tablet (Honor Pad 8, MagicUI / Android 12).
Kotlin + Jetpack Compose, single activity, no Android Studio needed: GitHub Actions builds
the APK on every push to `main` and publishes it as a GitHub Release (`build-N`, versionCode N).

## How Shane gets updates

- Each push to `main` → `.github/workflows/build.yml` → release `build-N` marked latest.
- Harbour checks `api.github.com/repos/Shane572/Harbour/releases/latest` on launch and shows
  **Update available**; one tap installs over the top (PackageInstaller session, `Updater.kt`).
- The repo must stay **public** for that check to work without a login.
- Signing uses the committed `app/harbour-debug.keystore` so every build installs as an update.
  Never change or regenerate it, or Shane has to uninstall and lose his reading progress.
- Put `[skip ci]` in a commit message for changes that shouldn't produce a release (docs, notes).

## Ground rules

- Keep it simple. Shane wants fewer, working builds rather than process. No emulator tests.
- Every build must open. Before pushing, read through the change for things that compile but
  break at runtime. Example that shipped once: the root composable and the `Application` class
  were both named `HarbourApp`; `setContent { HarbourApp() }` resolved to the class constructor
  (no default args beats default args) and the app showed a black screen. Check new top-level
  names don't clash, check new Screens are handled in every `when`, and check manifest entries.
- Do **not** use Android's system folder picker (`OpenDocumentTree` / SAF). It is broken on this
  tablet. Folder choosing is Harbour's own browser (`ui/FolderBrowserScreen.kt`) using
  All files access (`MANAGE_EXTERNAL_STORAGE`, see `StorageAccess.kt`). Library roots are
  `file://` URIs. (`LibraryRepository` still has SAF scanning code from early builds; unused.)
- Crashes are written to `filesDir/last_crash.txt` by `HarbourApp.kt` and shown on next launch
  with a Copy button. Ask Shane to paste that when something breaks.
- Only touch this repository. Shane also has a company GitHub org (Kinore-Engineering); never
  read or change anything there.

## Map

- `MainActivity.kt` — `HarbourRoot()` composable, screen switching, immersive reader mode
- `MainViewModel.kt` — `Screen` back stack, library state, progress, updates, preview toggle
- `LibraryRepository.kt` — saved folders, scanning for `.cbz`, library cache
- `MiniZip.kt` / `ComicArchive.kt` — random-access ZIP reading straight from a file descriptor
- `CoverCache.kt`, `ProgressStore.kt`, `Updater.kt`, `StorageAccess.kt`, `HarbourApp.kt`
- `ui/` — `LibraryScreen`, `SeriesScreen`, `ReaderScreen`, `FolderBrowserScreen`, `UpdateUi`, theme

## Current state (build 9)

Works: library of `.cbz` comics by folder/series with covers, continue reading, reader with
swipe/tap/zoom/page slider, right-to-left per series, next issue, mark read, in-app updates.

Not yet: EPUB books, CBR, double-page spreads, webtoon scroll, theming. Shane wants to work on
features and customisation next. The "Preview builds" switch in settings is left over from an
emulator experiment and can be removed.
