![Olauncher](https://repository-images.githubusercontent.com/278638069/db0acb80-661b-11eb-803e-926cae5dccb4)


# Olauncher | Minimal AF Launcher
AF stands for Ad-Free

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80">](https://f-droid.org/packages/app.olauncher)
[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"
    alt="Get it on Play Store"
    height="80">](https://play.google.com/store/apps/details?id=app.olauncher)

### Install using [F-Droid](https://f-droid.org/packages/app.olauncher), [Play Store](https://play.google.com/store/apps/details?id=app.olauncher) or the [latest APK](https://github.com/tanujnotes/Olauncher/releases/).

- To maintain the simplicity of the launcher, a few niche features are available but hidden.

- Please check out the **[About](https://tanujnotes.substack.com/p/olauncher-minimal-af-launcher?utm_source=github)** page in the Olauncher settings for a complete list of features and **FAQs**.

##

### Accessibility Intents

Olauncher exposes two intents that allow external apps (such as physical key mappers or accessibility services) to trigger launcher actions without touch input.

| Intent Action | Description |
|---|---|
| `app.olauncher.ACTION_OPEN_APP_DRAWER` | Opens the app drawer from the home screen |
| `app.olauncher.ACTION_DISMISS_KEYGUARD` | Shows the PIN/pattern/password entry to unlock the device (Android 8.0+) |

**ADB example:**
```bash
adb shell am start -a app.olauncher.ACTION_OPEN_APP_DRAWER -n app.olauncher/.MainActivity
adb shell am start -a app.olauncher.ACTION_DISMISS_KEYGUARD -n app.olauncher/.MainActivity
```

**Android example (Kotlin):**
```kotlin
val intent = Intent("app.olauncher.ACTION_OPEN_APP_DRAWER")
intent.setPackage("app.olauncher")
startActivity(intent)
```

##

License: [GNU GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html)

Dev: [X/twitter](https://x.com/tanujnotes) • [Bluesky](https://bsky.app/profile/tanujnotes.bsky.social)

##

### My other apps:

- [Pro Launcher](https://play.google.com/store/apps/details?id=app.prolauncher) - Pro version of Olauncher with extra features like widgets, weather, folders, etc.

- [Note to Self](https://play.google.com/store/apps/details?id=com.makenotetoself) - Free and [open source](https://github.com/jeerovan/ntsapp) notes app with chat like interface and end-to-end encryption.

- [Pentastic](https://play.google.com/store/apps/details?id=app.pentastic) - Minimal todo lists. Free and [open source](https://github.com/tanujnotes/Pentastic).
