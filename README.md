<div align="center">
  <h1>Her Music 💕</h1>
  <p><strong>A deeply connected music streaming experience crafted for couples, with real-time synchronized playback, private Her & Him space, time-synced lyrics, and offline listening.</strong></p>

  <p>
    <a href="package/Her-Music-arm64-v8a.apk"><img src="https://img.shields.io/badge/Download-APK-FF4081?style=for-the-badge&logo=android&logoColor=white" alt="Download APK" /></a>
    <img src="https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform" />
    <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
    <img src="https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  </p>
</div>

---

## ✨ Features

### 💕 Her & Him Private Couple Space
- **Instant Pair Sync**: Generate a code (`HER-XXXX` or `HIM-XXXX`) and link your spaces in 1–2 seconds with zero server setup required.
- **Listen Together (Live Sync)**: Listen to the exact same song at the exact same millisecond across two devices with live seek bar synchronization.
- **Tune In With 1 Tap**: See what your partner is listening to in real time and tune into their audio session immediately.
- **HerChat & HimChat**: Direct messaging built into your music player with one-tap song sharing inside the chat.
- **Songs from Her / Songs from Him**: Push songs directly to your partner's library and notification shade.
- **Daily Couple Quiz & Love Notes**: Share thoughts, answers to daily relationship questions, and leave love notes.
- **Heart Bursts**: Tap to send animated floating heart bursts directly to your partner's screen.

### 🎵 Core Music Experience
- **Vast Music Catalog**: Stream high-fidelity music from YouTube Music ad-free.
- **Offline Downloads**: Save tracks, playlists, and albums directly to your device for offline listening.
- **Live Synchronized Lyrics**: Time-synced lyrics with word-by-word animation and instant translation support.
- **Audio Equalizer**: Built-in 10-band equalizer with bass boost, virtualizer, and parametric audio profiles.
- **Modern Jetpack Compose UI**: Material 3 dark aesthetics, customizable dynamic themes, and a clean player interface.

---

## 📦 Prebuilt Packages & Installation

Prebuilt APKs are bundled directly in the repository:

| Package | Architecture | Size | Link |
|---|---|---|---|
| **Her-Music-arm64-v8a.apk** | 64-bit ARM (recommended) | ~47 MB (Optimized Release) | [📥 Download APK](package/Her-Music-arm64-v8a.apk) |
| **Her.apk (Universal)** | All Architectures | ~121 MB | Built via `./gradlew assembleUniversalFossDebug` |

### How to Install
1. Download [Her-Music-arm64-v8a.apk](package/Her-Music-arm64-v8a.apk) to your Android device.
2. Open the file from your Downloads or File Manager and tap **Install**.
3. Launch **Her Music**, navigate to the **Couple Space** tab, and enter your partner's code to connect!

---

## 🛠️ Technical Stack & Architecture

- **Architecture**: Clean Architecture with MVVM, Android Jetpack Compose, Kotlin Coroutines & Flow.
- **Audio Engine**: Media3 ExoPlayer with custom audio processors, chunking data source, and cache manager.
- **Local Storage**: Room Database v46 with KSP schema tracking and Android DataStore preferences.
- **Network & Realtime Sync**: Dual-layer sync engine combining an instant low-latency real-time cloud relay with Firebase Realtime Database auto-discovery.
- **Dependency Injection**: Hilt (Dagger).

---

## 🚀 Building From Source

### Prerequisites
- JDK 21 (Temurin or OpenJDK 21)
- Android SDK (API Level 36/37)
- Gradle 9.3+

### Build Commands
```bash
# Clone the repository
git clone https://github.com/ankitxrishav/Her-Music-app.git
cd Her-Music-app

# Build 64-bit ARM APK (Recommended)
./gradlew assembleArm64FossDebug

# Build Universal APK (All ABIs)
./gradlew assembleUniversalFossDebug
```

Compiled APKs will be located in:
`app/build/outputs/apk/arm64Foss/debug/app-arm64-foss-debug.apk`
`app/build/outputs/apk/universalFoss/debug/app-universal-foss-debug.apk`

---

## 📄 License & Attribution

This project is licensed under the GPLv3 License. Built with love for couples who cherish music together.
