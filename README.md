<div align="center">

<img src="docs/icon.svg" width="112" height="112" alt="Photos icon">

# Photos for Android

**The iPhone Photos experience, rebuilt natively for Android.**<br>
One beautiful library, memories that play themselves, Live Text, subject lifting and a full photo editor that never touches your originals.

[![Latest release](https://img.shields.io/github/v/release/shatheitguy/photos-android?style=for-the-badge&label=Download&color=ff5e7e)](https://github.com/shatheitguy/photos-android/releases/latest)
[![Website](https://img.shields.io/badge/Website-visit-8a5cff?style=for-the-badge)](https://shatheitguy.github.io/photos-android/)
[![Android 10+](https://img.shields.io/badge/Android-10%2B-34A853?style=for-the-badge&logo=android&logoColor=white)](#-install)

![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?logo=jetpackcompose&logoColor=white)
![No ads](https://img.shields.io/badge/ads-none-lightgrey)
![No tracking](https://img.shields.io/badge/tracking-none-lightgrey)

[Features](#-features) · [What's new](#-whats-new-in-16) · [Install](#-install) · [Build](#%EF%B8%8F-build-from-source) · [Author](#-author)

</div>

---

## ✨ Features

### 🖼️ Library
- One continuous grid of every photo and video, newest at the bottom, just like iPhone.
- **Years · Months · Days · All Photos**, plus filters for Favorites, Photos, Videos and Screenshots.
- Pinch to change the grid size, or switch on the **Aspect Ratio Grid** to see whole photos.
- Long-press and drag to select many at once. The grid scrolls itself when you reach the edge.
- Remembers your view, filter and grid size.

### 🔍 Photo viewer
- Swipe, pinch, double-tap to zoom, swipe down to close, swipe up for details.
- **Scrub the thumbnail strip** and the photo follows your finger.
- Floating glass buttons: Share · Favorite · Info · Edit · Delete.
- **•••** menu: Copy, Duplicate, Hide, Copy to Album, Move to Album, Use As.
- **Live Text:** tap the text button to select, copy or share any text in a photo.
- **Lift the subject:** long-press a person, pet or object, then Copy, Share or **Add Sticker**.
- Plays videos and animated GIFs.

### 🎨 Editing
| Adjust | Filters | Crop |
|---|---|---|
| Auto, Exposure, Brilliance, Highlights, Shadows, Contrast, Brightness, Black Point, Saturation, Vibrance, Warmth, Tint, Sharpness, Definition, Noise Reduction, Vignette, all on the iPhone-style ruler dial | Vivid, Vivid Warm, Vivid Cool, Dramatic, Dramatic Warm, Dramatic Cool, Mono, Silvertone, Noir | Drag corners and edges, Freeform · Original · Square · 16:9 · 4:3 · 3:2 · 9:16 · 3:4, Rotate and Flip |

Press and hold to compare with the original. Edits are saved as a **copy** in `Pictures/Photos Edits`.

### 📚 Albums
- **Memories** that play as slideshows: On This Day, weekends, Best of the month, Year in Review.
- Featured Photos, your folders (Camera, WhatsApp, Screenshots…) and Favorites.
- Media types: Videos, Selfies, Screenshots, Screen Recordings, Panoramas, Animated.
- Utilities: Duplicates, **Hidden** and **Recently Deleted**, locked with your PIN or fingerprint.

### 🗑️ Deleting, the iPhone way
- An iOS glass card asks before anything is deleted, then items wait in Recently Deleted for 30 days.
- Turn on **Delete Without Asking** in Settings to skip Android's extra "Allow" prompt.

### 📱 Tablets
- An **iPad-style layout** with a sidebar (Library, Albums, Search, My Albums, Media Types, Utilities) beside your photos.

### ⚙️ And more
- Light, dark or automatic appearance.
- Shows up in **Open with** when you open a picture from another app.
- **Check for Updates** downloads and installs new versions right inside the app.
- Works hand in hand with **[Files for Android](https://github.com/shatheitguy/files-android)**.

## 🆕 What's new in 1.6
- **Crop** with handles, ratios, rotate and flip.
- All **16 iPhone adjustments** on a ruler dial.
- **Live Text** and **Lift the subject**.
- A **•••** menu in the viewer.
- Fixed swiping between photos sometimes stopping after flicking the thumbnail strip.

## 📥 Install
1. Download **`Photos.apk`** from the [latest release](https://github.com/shatheitguy/photos-android/releases/latest).
2. Open it on your phone and allow installing from this source if Android asks.
3. Open **Photos** and tap **Continue** to give it access to your photos and videos.

> Requires **Android 10 or newer**. Live Text and lifting subjects use Google Play services on your phone. Nothing is uploaded.

## 🛠️ Build from source
You need JDK 17 and the Android SDK (platform 35).

```bash
git clone https://github.com/shatheitguy/photos-android.git
cd photos-android
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`.

<details>
<summary><b>Project layout</b></summary>

```
app/src/main/java/com/photos/gallery/
├── MainActivity.kt          Entry point, handles "Open with"
├── data/
│   ├── Models.kt            Media, albums, memories
│   ├── PhotosStore.kt       MediaStore loading, favorites, hidden, trash, copy/move
│   └── ImageLoader.kt       Thumbnail + full image loading with a memory cache
└── ui/
    ├── Root.kt              Permissions, navigation, tab bar, tablet layout
    ├── Library.kt           Library (Years / Months / Days / All Photos)
    ├── Albums.kt            Albums and album screens
    ├── Viewer.kt            Full-screen photo / video viewer
    ├── ViewerMenu.kt        ••• menu and album picker
    ├── Lift.kt              Live Text and subject lifting
    ├── Editor.kt            Editor, filters and crop
    ├── EditAdjust.kt        The 16 adjustments and the ruler dial
    ├── Confirm.kt           iOS delete confirmation cards
    ├── ForYou.kt            Memories and the slideshow player
    ├── Search.kt            Search
    ├── Tablet.kt            iPad-style sidebar layout
    ├── Updates.kt           Settings and in-app updates
    └── Theme.kt, IosIcons.kt, IosPopup.kt, Components.kt
```
</details>

## 🔒 Privacy
No accounts, no cloud, no ads, no analytics. Your photos never leave your phone. The only network use is checking GitHub for new versions.

## 👤 Author

<table>
  <tr>
    <td><img src="https://github.com/shatheitguy.png" width="72" height="72" alt="Sharqan Ahamed"></td>
    <td>
      <b>Sharqan Ahamed</b><br>
      Designer and developer of Photos and Files for Android<br>
      <a href="https://github.com/shatheitguy">github.com/shatheitguy</a>
    </td>
  </tr>
</table>

If you enjoy the app, ⭐ the repo. It really helps!

---

<sub>Photos for Android is an independent project and isn't affiliated with Apple or Google. The app icon is an original design.</sub>
