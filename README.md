<div align="center">
  <h1>QALAUNCHER</h1>
  <p><strong>A minimal Android launcher with Niagara-style layout and liquid glass aesthetics</strong></p>
  <p>
    <img src="https://img.shields.io/badge/version-v0.0.1-blue" alt="Version v0.0.1" />
    <img src="https://img.shields.io/badge/minSdk-24-brightgreen" alt="minSdk 24" />
    <img src="https://img.shields.io/badge/Kotlin-Android-7F52FF" alt="Kotlin Android" />
  </p>
</div>

---

## 🪟 Overview

QALAUNCHER is a minimal, clean Android home screen launcher inspired by **Niagara Launcher** and **olauncher**. It replaces your cluttered app drawer with a streamlined vertical app list, a side alphabet index for quick navigation, and a beautiful frosted glass (blur) effect.

Born from the desire for a launcher that's both minimal in UI and rich in visual polish.

## ✨ Features

### 🏠 Niagara-Style Home Screen
- **Vertical app list** — Your favorite apps arranged in a clean vertical column
- **Alphabet index (A-Z, 0-9, #)** — Always visible on the right side; tap any letter to open the full app drawer
- **Up to 8 configurable app slots** — Each with its own icon, custom label, and support for pinned shortcuts
- **Glass card backgrounds** — Each app slot sits on a rounded frosted glass panel

### 🪟 Liquid Glass Effect
- **Full-screen frosted blur** — System-level blur on Android 12+ (`FLAG_BLUR_BEHIND`, radius 120) or RenderScript BlurView on older devices
- **Deep tint overlay** — 80% opaque dark background (`#CC0D0D14`) creates a rich, immersive glass look
- **Uniform coverage** — The glass effect spans the entire screen, including the bottom brand area

### 🎨 Gesture Navigation
| Gesture | Action |
|---------|--------|
| Swipe up / left / right | Open app drawer |
| Swipe down | Expand notifications or open search |
| Tap clock | Open alarm app |
| Tap date | Open calendar |
| Long press clock/date | Set custom clock/calendar app |
| Long press empty slot | Select app for that slot |
| Long press home screen | Open settings |
| Double tap (lock mode) | Lock device |

### 🖼️ Wallpaper & Theming
- **Custom default wallpaper** — Sets a default background image on first launch (changeable anytime)
- **Daily wallpaper** — Auto-updates wallpaper every 4 hours via WorkManager
- **Light / Dark / System theme** — Full theme support with automatic switching
- **Custom text size scaling** — Adjust font sizes to your preference
- **Hidden apps** — Hide apps from the drawer
- **Status bar toggle** — Show or hide the status bar

### 🔍 App Drawer
- **Search** with instant filtering
- **Alphabet index** — Drag or tap to jump to sections
- **App info, uninstall, hide** — Long press for options
- **Rename apps** — Custom labels for any app
- **Private Space support** (Android 15+)
- **Pinned shortcuts** support

### 🔐 Lock Screen Features
- **Device admin lock** — Double-tap to lock (Android < 9)
- **Accessibility lock** — Lock via accessibility service (Android 9+)
- **Home button recents** — Smart home-button behavior

## 📱 Screenshots

> _Screenshots coming soon_

```
┌──────────────────────┐
│          12:34       │  ← Minimal clock
│        Thu, 6 Jun    │  ← Date
│                      │
│  📱  Messages    A   │  ← Glass card items
│  📱  Settings    B   │     with alphabet index
│  📱  Chrome      C   │     on the right
│  📱  +           D   │
│                      │
│ ━━━━━━━━━━━━━━━━━    │  ← Brand logo
│ QALAUNCHER 0.0.1     │
└──────────────────────┘
```

## 🛠️ Building

### Prerequisites
- **Android Studio** Hedgehog (2023.1.1) or later
- **JDK 17** or later
- **Android SDK** 35

### Steps

```bash
# Clone the repository
git clone https://github.com/kubobeem/jlancher.git
cd jlancher

# Build debug APK
./gradlew assembleDebug

# The APK will be at:
# app/build/outputs/apk/debug/app-debug.apk
```

### Installing

```bash
# Via ADB (device must be connected)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Or simply drag the APK onto your emulator / device
```

## 🚀 Usage

1. **Install** the APK on your device
2. **Set as default launcher** when prompted (or go to Settings → Apps → Default apps → Home app)
3. **Long press** an empty slot (+) to assign an app
4. **Swipe** anywhere on the home screen to open the full app drawer
5. **Tap the alphabet index** (A-Z on the right edge) to open the app drawer
6. **Swipe down** to expand notifications (configurable to search)
7. **Long press** the home screen to enter Settings

### Home Screen Slots

| Action | Behavior |
|--------|----------|
| Tap app | Launch the app |
| Tap empty slot (+) | Shows "Long press to select app" |
| Long press slot | Open app picker for that slot |
| Clock / Date tap | Open clock / calendar |
| Long press clock | Reset or change clock app |
| Long press date | Reset or change calendar app |

## ⚙️ Settings

Access settings by **long pressing** anywhere on the home screen.

| Setting | Options |
|---------|---------|
| Home apps count | 0–8 slots |
| App alignment | Left / Center / Right / Bottom |
| Status bar | Show / Hide |
| Date/time display | Show / Hide / Date only |
| Theme | Light / Dark / System |
| Text size scale | 0.5× – 1.5× (tablet: up to 2.0×) |
| Auto-show keyboard | On / Off |
| Swipe down action | Notifications / Search |
| Daily wallpaper | On / Off (auto-updates every 4h) |
| Lock mode | On / Off (accessibility or device admin) |
| Home button recents | On / Off |
| Hidden apps | Manage hidden apps |

## 📁 Project Structure

```
app/src/main/java/app/olauncher/
├── data/
│   ├── AppModel.kt              # App data models
│   ├── Constants.kt             # Constants & flags
│   └── Prefs.kt                 # SharedPreferences wrapper
├── helper/
│   ├── Utils.kt                 # Utility functions
│   ├── WallpaperWorker.kt       # Daily wallpaper worker
│   └── ...
├── listener/
│   ├── OnSwipeTouchListener.kt  # Gesture detection
│   └── ...
├── ui/
│   ├── HomeFragment.kt          # Main home screen
│   ├── AppDrawerFragment.kt     # App list/drawer
│   ├── SettingsFragment.kt      # Settings screen
│   ├── NiagaraHomeAdapter.kt    # Home app list adapter
│   └── AppDrawerAdapter.kt      # Drawer app adapter
├── MainActivity.kt
└── MainViewModel.kt

app/src/main/res/
├── drawable/
│   ├── glass_card.xml           # Glass card background
│   ├── haikei.jpg               # Default wallpaper
│   └── brand_gradient.xml       # Brand logo gradient
├── layout/
│   ├── fragment_home.xml        # Home screen layout
│   ├── fragment_app_drawer.xml  # App drawer layout
│   ├── fragment_settings.xml    # Settings layout
│   └── adapter_*.xml            # RecyclerView adapters
├── values/
│   ├── styles.xml               # Light theme & styles
│   ├── colors.xml               # Color palette
│   └── strings.xml              # String resources
└── values-night/
    └── styles.xml               # Dark theme
```

## 🧩 Dependencies

- **Kotlin** + AndroidX (AppCompat, RecyclerView, Lifecycle, Navigation)
- **BlurView** (Dimezis/BlurView) — Fallback blur for Android < 12
- **WorkManager** — Daily wallpaper updates
- **Material Components**

## 🤝 Credits & License

QALAUNCHER is a fork of **[olauncher](https://github.com/tanujnotes/Olauncher)** by Tanuj Notes, modified and extended with:
- Niagara-style vertical app list with glass card backgrounds
- Configurable alphabet index (A–Z) on the home screen
- Enhanced liquid glass (blur) effect with uniform coverage
- Default wallpaper support
- Branded UI elements and custom theme colors

### License

This project is distributed under the same license as the original olauncher. See the original project for details.

---

<div align="center">
  <p>Made with ❤️ for minimalists who love beautiful software</p>
  <p><sub>QALAUNCHER 0.0.1</sub></p>
</div>
