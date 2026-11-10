# Harbour

A sleek, dark comic and book reader for Android tablets.

## What it does

- **Your folders, your groups.** Point Harbour at the folders your comics and books live in. It finds
  every `.cbz`, `.cbr` and `.epub` inside them. Folders don't decide anything: you make your own groups
  by long-pressing comics to select them and tapping **Add to group**.
- **A sidebar that gets out of the way.** Home, All comics, Ungrouped, your groups, Books and Settings,
  collapsible down to an icon rail.
- **A proper comic reader.** Full screen, swipe or tap the edges, pinch or double-tap to zoom, page
  slider. Left to right, right to left (manga) or vertical scroll (webtoons), remembered per group.
  One page, two pages, or two in landscape. Whole page or fit to width. Next issue on the last page.
- **Books.** EPUBs open in a paginated reader with your choice of size, font, line spacing, margins
  and page colour (dark, black, sepia, light), plus contents and a progress slider.
- **Make it yours.** Accent colour, dark or pure black background, cover size, and sorting.

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

Open the project in Android Studio, or run `./gradlew assembleRelease` with JDK 17 and the
Android SDK installed. The APK lands in `app/build/outputs/apk/release/`.

## Releases

Every push to `main` is released straight away, so the tablet offers it next time Harbour opens.
