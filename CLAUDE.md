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
- `ComicArchive.kt` — CBZ via `MiniZip.kt`; CBR via libarchive, unpacked to `cache/cbr` (last 3 kept);
  PDF via Android's `PdfRenderer` (PDFs are books in the library)
- `EpubBook.kt` — EPUB container/OPF/spine/contents parsing
- `ComicInfo.kt` — ComicInfo.xml inside CBZ/CBR (series, credits, summary) for About this issue
- `ShownPages.kt` — the reader's view of a comic: trimmed margins (`Trim`), split spreads; maps
  shown pages back to the file's pages, which progress and saved pages always use
- `GalleryStore.kt` — saved pages: JPEGs + `gallery.json` in `Harbour/Gallery` (outlive the app)
- `CoverCache.kt`, `ProgressStore.kt` (comics + books), `Updater.kt`, `StorageAccess.kt`, `HarbourApp.kt`
- `ui/` — `LibraryScreen` (main layout, Home, welcome), `Sidebar`, `ComicsSection` (grids,
  selection, Add to group), `BooksSection`, `SettingsSection`, `ReaderScreen` (paged / two-page /
  vertical), `BookReaderScreen` (WebView, CSS-column paging), `FolderBrowserScreen`, `Dialogs`, theme,
  `GallerySection` (Gallery page + full-screen `GalleryViewer`), `ComicInfoDialog`

## Current state (build 30)

Library: collapsible sidebar (Home, All comics, Will Read, Ungrouped, Gallery, groups, Books, Settings) with
hold-and-drag group reordering (`orderedGroups`, `reorderGroups`). Groups are made by hand
(long-press to select, Add to group); one group per comic; `Group.coverKey` = Set as group cover.
Home: Continue reading, 3 groups (most recently read first), Recently added. Tapping the sidebar
GROUPS heading opens `Section.AllGroups` (every comic group with counts, New group). Search bar with
refresh on every page; search is scoped to the current page (group, Will Read, Ungrouped, All
comics, Books; Home/Settings search everything); group name + title, whole-number issue matching.
Will Read = prefs key `willRead`. Delete from device (confirmed). Book groups (`Group.forBooks`)
live on the Books page (`BookGroupSection`), added via a book's long-press menu; comic views use
`orderedGroups` (comic groups only), books use `bookGroups`. Back steps through page history;
group pages have a back arrow.

Read order: per group (`Group.readOrder`), numbered at the top of the group page, drives Up next
and Next issue. Edited by hold-and-drag on the group page (`readOrderDrag` in ComicsSection): the
grid stays still, an accent bar marks the drop slot, change applied on release. Holding selects at
the long-press (undone if you then drag); a drag needs ~24dp of movement. Shane found live
reordering during drag and select-on-release both bad; don't go back to them.

Backup (`Backup.kt`): prefs (settings, progress, book_progress, library) + groups.json to
`Harbour/harbour-backup.json`; auto-saved in `MainActivity.onStop` only when there's data; Restore
(Settings / welcome screen) restarts the app.

Reading: CBZ, CBR (libarchive, charset forced to UTF-8), PDF books (`PdfRenderer`, read in the
comic reader, progress as page/pages), EPUB (WebView, CSS-column paging). Reader direction per
group (LTR, RTL, vertical, horizontal); the scrolling modes (`ContinuousReader`) zoom by laying
pages out bigger (scroll stays 1:1), live pinch via graphicsLayer, one-finger cross-axis pan;
rotation lock button in both readers (`rotationLocked`), one/two/auto pages, fit width, zoom, next issue. Finished items show a
book-with-tick badge and stay at 100% until Reset progress (reader options / Aa). Progress bars sit
under covers with a percentage.

Gallery: heart in the reader's top bar saves the page (spreads ask which page) at ~8 MP, trimmed if
trimming is on. Gallery page is a staggered wall of saved pages, filter by group; tap for the
full-screen viewer (swipe, zoom) with Read from here (`vm.readFrom`, `takePendingStart`), About this
issue, Set as wallpaper (home / lock / both) and Remove. About this issue is also the reader's info
button and a one-comic selection action.

Trim margins and Split spreads are global on/off icons in the reader top bar, like rotation lock
(`trimMargins`, `splitSpreads`). Spread detection reads every page's size once per comic
(`spreadsOf`, cached); the cover is never split; RTL shows the right half first.

Look: accent colours (incl. Crimson), dark/black, cover size, sort. Icons in `res/drawable`
(Shane picked stacked comics for All comics, glasses for Will Read, stack of novels for Books,
framed picture for Gallery, page-pulled-in for Trim, dashed-cut spread for Split).

Not yet: text selection or footnote links in books. Shane doesn't want auto-grouping. He asked
about AI character voices reading comics aloud: possible only online (vision model reads bubbles and
speakers, paid TTS voices per character). Read aloud with free offline voices (Piper/Kokoro via
sherpa-onnx, ML Kit for bubbles) was built in build 31 and reverted: Shane didn't like the voice stuff
(Kokoro took ~30 s per line on the tablet). Don't bring it back unless he asks.
