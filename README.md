<div align="center">

<img src="docs/icon.svg" width="112" height="112" alt="Photos icon">

# Photos for Android

**Photos. Now on Android.**<br>
Your whole library in one beautiful grid, memories that play themselves, Live Text, subject lifting, and an editor that never touches your originals.

[![Latest release](https://img.shields.io/github/v/release/shatheitguy/photos-android?style=for-the-badge&label=Download&color=ff5e7e)](https://github.com/shatheitguy/photos-android/releases/latest)
[![Website](https://img.shields.io/badge/Website-visit-8a5cff?style=for-the-badge)](https://shatheitguy.github.io/photos-android/)
[![Android 10+](https://img.shields.io/badge/Android-10%2B-34A853?style=for-the-badge&logo=android&logoColor=white)](#-install)

![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?logo=jetpackcompose&logoColor=white)
![No ads](https://img.shields.io/badge/ads-none-lightgrey)
![No tracking](https://img.shields.io/badge/tracking-none-lightgrey)

[Features](#-features) · [Install](#-install) · [Build](#%EF%B8%8F-build-from-source) · [Author](#-author)

</div>

---

## ✨ Features

**Everything you love about Photos.** Designed to feel instantly familiar, built natively for Android.

### 🖼️ One library, beautifully organised
_Every photo and video in a single grid, newest at the bottom._

- Years, Months, Days and All Photos
- Filter Favorites, Photos, Videos, Screenshots
- Pinch to change grid size
- Long-press any photo for the iPhone preview menu
- Drag to select many — auto-scrolls

### 🎞️ Memories
_Your best moments turned into slideshows, automatically._

- On This Day
- Weekends and busy days
- Best of the month, Year in Review
- Featured Photos every day

### 🎨 A real photo editor
_Every iPhone adjustment, on the same ruler dial. Edits are saved as a copy._

- Auto, Exposure, Brilliance, Highlights, Shadows, Contrast, Black Point, Vibrance, Warmth, Tint, Sharpness, Definition, Noise Reduction, Vignette…
- 10 filters: Vivid, Dramatic, Mono, Noir…
- Crop with ratios, rotate and flip

### 👆 A viewer that feels right
_Swipe, zoom, scrub — every gesture you expect._

- Scrub the thumbnail strip to fly through photos, with a soft tick for each one
- Pinch and double-tap to zoom
- ••• menu: Copy, Duplicate, Hide, Move to Album
- Videos and GIFs play inline

### ✨ Live Text & Lift Subject
_The clever bits, right on your phone. Nothing is uploaded._

- Select, copy or share text found in any photo
- Long-press a person, pet or object to lift it out
- Copy, Share or Add Sticker

### 📱 Made for tablets too
_An iPad-style sidebar beside your photos on big screens._

- Library, Albums, Search, Media Types, Utilities
- iOS delete cards, no extra Android prompts

### 📚 Albums and utilities
_Folders, favorites and smart media types in one place._

- Videos, Selfies, Screenshots, Panoramas
- Duplicates finder
- Hidden and Recently Deleted, locked with your PIN

### 🔍 Search
_Find anything by month, year, weekday, album or type._

- Try “November”, “2025”, “videos”
- Moments grouped by month

## 🤝 Files, made to work with Photos.

An iPhone-style file manager: open a picture and it opens right here in Photos, plus “Save to Files” in every share sheet.

👉 [Get Files for Android](https://github.com/shatheitguy/files-android)

## 🔒 Your photos stay on your phone.

No accounts, no cloud, no tracking — ever. The only thing the apps fetch from the internet is new versions from GitHub.

✅ Photos never leave your phone · ✅ No ads · ✅ No analytics · ✅ Deletes ask first, like iPhone · ✅ Live Text runs on-device · ✅ Locked Hidden album

## 📥 Install

**Up and running in a minute.**

1. **Download**: Get `Photos.apk` from the [latest release](https://github.com/shatheitguy/photos-android/releases/latest).
2. **Install**: Open the file on your phone. If Android asks, allow installing from this source.
3. **Allow access**: Open Photos and tap **Continue** so it can show your photos and videos.
4. **Make it default**: Open any photo from another app, choose **Photos** and tap **Always**.

> Requires **Android 10 or newer**.

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

## 👤 Author

**Hi, I'm Sharqan.** I build Android apps that feel as calm and polished as the ones on iPhone, with no ads, no tracking, and everything staying on your phone.

<table>
  <tr>
    <td><img src="https://github.com/shatheitguy.png" width="72" height="72" alt="Sharqan Ahamed"></td>
    <td>
      <b>Sharqan Ahamed</b><br>
      Designer and developer of Files and Photos for Android<br>
      <a href="https://github.com/shatheitguy">github.com/shatheitguy</a>
    </td>
  </tr>
</table>

If you enjoy the app, ⭐ the repo. It really helps!

---

<sub>Photos for Android is an independent project and isn't affiliated with Apple or Google. The app icon is an original design.</sub>
