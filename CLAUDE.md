# Harbour

Shane's personal comic and book reader for his Android tablet (Honor Pad 8, MagicUI / Android 12).
Kotlin + Jetpack Compose, single activity, no Android Studio needed: GitHub Actions builds
the release APK on every push to `main` and publishes it as a GitHub Release (`build-N`,
versionCode N). Release, not debug: debug Compose builds are noticeably slower on the tablet.

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
  `file://` URIs; comics are opened straight from the file.
- Compile locally before pushing when you can: `./gradlew assembleRelease` with `JAVA_HOME`
  set to a JDK 17 and `ANDROID_HOME` set to `%LOCALAPPDATA%\Android\Sdk`. Android Studio's
  bundled JDK on Shane's PC is 25, which Gradle 8.11 can't run on. Gradle also needs
  `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<short path without spaces>` on that PC.
- Crashes are written to `filesDir/last_crash.txt` by `HarbourApp.kt` and shown on next launch
  with a Copy button. Ask Shane to paste that when something breaks.
- Only touch this repository. Shane also has a company GitHub org (Kinore-Engineering); never
  read or change anything there.

## Map

- `MainActivity.kt` — `HarbourRoot()`: switches between `Screen`s, immersive mode while reading
- `MainViewModel.kt` — `Screen` back stack, sidebar `Section`, library, groups, progress, settings
- `Models.kt` — `Comic`, `Book` (both `LibraryItem`), `Group`, progress, setting enums
- `LibraryRepository.kt` — saved folders, scanning for `.cbz`/`.cbr`/`.epub`, library caches
- `GroupStore.kt` — Shane's own groups (`groups.json`). Groups are made by hand and are
  deliberately unrelated to folders on disk; one group per comic.
- `ComicArchive.kt` — CBZ via `MiniZip.kt`; CBR via libarchive, unpacked to `cache/cbr` (last 3 kept)
- `EpubBook.kt` — EPUB container/OPF/spine/contents parsing
- `CoverCache.kt`, `ProgressStore.kt` (comics + books), `Updater.kt`, `StorageAccess.kt`, `HarbourApp.kt`
- `ui/` — `LibraryScreen` (main layout, Home, welcome), `Sidebar`, `ComicsSection` (grids,
  selection, Add to group), `BooksSection`, `SettingsSection`, `ReaderScreen` (paged / two-page /
  vertical), `BookReaderScreen` (WebView, CSS-column paging), `FolderBrowserScreen`, `Dialogs`, theme

## Current state (build 14)

Works: collapsible sidebar (Home, All comics, Ungrouped, groups, Books, Settings); hand-made
groups via long-press selection; CBZ and CBR comics; reader with direction per group (LTR, RTL,
vertical/webtoon), one/two/auto pages, fit width, zoom, next issue; EPUB books with font, size,
spacing, margins, page colour, contents and progress; accent colour, dark/black, cover size,
sorting; in-app updates. Build 14 added: search bar with refresh on every page (matches group
name + title, whole-number issue matching), per-group Read Order (`Group.readOrder`, drives Up next
and Next issue), Jump back in (last 3 groups opened, Home), and Delete from device (with confirm).

Not yet: grouping books, text selection or footnote links in books, pinch zoom in vertical mode.
