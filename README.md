# Photos for Android

A gallery app for Android with the look and feel of the new iPhone Photos app — built natively with Kotlin and Jetpack Compose.

**Website:** https://shatheitguy.github.io/photos-android/
**Download:** [Latest APK](https://github.com/shatheitguy/photos-android/releases/latest)

---

## Features

### Library
- One continuous grid of every photo and video, newest at the bottom — just like iPhone.
- **Years · Months · Days · All Photos** views from the filter button.
- Filter to **Favorites, Photos, Videos or Screenshots**.
- **Pinch** the grid to change the number of columns.
- **Long-press and drag** to select many photos at once — the grid auto-scrolls when you reach the edge.

### Photo viewer
- Swipe between photos, pinch or double-tap to zoom, swipe down to close, swipe up for info.
- **Scrub the thumbnail strip** at the bottom and the photo follows your finger.
- Floating glass buttons: Share · Favorite · Info · Edit · Delete.
- **•••** menu: Copy, Duplicate, Hide, Copy to Album, Move to Album, Use As.
- **Live Text:** when a photo has text in it, tap the text button to select, copy or share it.
- **Lift the subject:** long-press a person, pet or object to cut it out — Copy, Share or Add Sticker.
- Plays videos and animated GIFs.

### Editing
- Adjust with the iPhone set: Auto, Exposure, Brilliance, Highlights, Shadows, Contrast, Brightness, Black Point, Saturation, Vibrance, Warmth, Tint, Sharpness, Definition, Noise Reduction and Vignette — each on a ruler dial.
- 10 filters: Vivid, Vivid Warm, Vivid Cool, Dramatic (+ Warm/Cool), Mono, Silvertone, Noir.
- **Crop** with handles and ratios (Freeform, Original, Square, 16:9, 4:3, 3:2, 9:16, 3:4), Rotate and Flip.
- Press and hold the photo to compare with the original.
- Edits are saved as a **copy** in `Pictures/Photos Edits` — originals are never changed.

### Albums
- **Memories** that play as a slideshow (On This Day, weekends, Best of the month, Year in Review).
- Featured Photos.
- Your folders (Camera, WhatsApp, Screenshots…), Favorites.
- Media types: Videos, Selfies, Screenshots, Screen Recordings, Panoramas, Animated.
- Utilities: Duplicates, **Hidden** and **Recently Deleted** — locked with your phone's PIN / fingerprint.

### Search
- Search by month, year, day of the week, album name, or type (`videos`, `screenshots`, `favorites`).

### Everything else
- Follows your phone's light / dark mode.
- Shows up in **Open with** when you open a photo or video from another app.
- Deleting asks first with an iOS-style card, then moves items to Recently Deleted for 30 days. Turn on **Delete Without Asking** in Settings to skip Android's extra "Allow" prompt.
- **Tablets** get the iPad layout: a sidebar with Library, Albums, Search, My Albums, Media Types and Utilities beside the photos.
- No accounts, no ads, no tracking. Your photos never leave your phone — the only network use is checking GitHub for app updates.
- **Settings** (••• on Albums): appearance, Memories and Featured Photos, Aspect Ratio Grid, Hidden album and passcode, and **Check for Updates** — new versions download and install from inside the app.
- Remembers your Library view, filter and grid size.

## Files app

Looking for a file manager to go with Photos? **[Files for Android](https://github.com/shatheitguy/files-android)** opens your pictures in Photos and adds “Save to Files” to every share sheet. Website: https://shatheitguy.github.io/files-android/

## Install

1. Download `Photos.apk` from the [latest release](https://github.com/shatheitguy/photos-android/releases/latest).
2. Open it on your phone and allow installing from this source if Android asks.
3. Open **Photos** and tap **Continue** to give it access to your photos and videos.

Requires **Android 10 or newer**.

## Build from source

Requirements: JDK 17 and the Android SDK (platform 35).

```bash
git clone https://github.com/shatheitguy/photos-android.git
cd photos-android
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`.

## Project layout

```
app/src/main/java/com/photos/gallery/
├── MainActivity.kt          Entry point, handles "Open with"
├── data/
│   ├── Models.kt            Media, albums, memories
│   ├── PhotosStore.kt       MediaStore loading, favorites, hidden, trash
│   └── ImageLoader.kt       Thumbnail + full image loading with a memory cache
└── ui/
    ├── Root.kt              Permissions, navigation, floating tab bar
    ├── Library.kt           Library tab (Years / Months / Days / All Photos)
    ├── Albums.kt            Albums tab, album screens
    ├── Search.kt            Search
    ├── Viewer.kt            Full-screen photo / video viewer
    ├── Editor.kt            Photo editor
    ├── ForYou.kt            Memories + slideshow player
    ├── Components.kt        Shared UI (grid, bars, buttons)
    ├── IosIcons.kt          Hand-drawn icon set
    └── Theme.kt             Colours and type
```

## Notes

- Favorites and Hidden are stored inside the app, so they don't sync with Google Photos.
- This is an independent project and isn't affiliated with Apple or Google. The app icon is an original design.
