# Contributing to Olauncher

Olauncher aims to stay minimal — a distraction-free, ad-free launcher. Not every feature
request fits that goal; some niche features are kept hidden rather than exposed by default.

## Before opening a PR

- For anything beyond a small, obvious fix, please open an issue first describing what you'd
  like to do and why, so we can agree on scope before you invest time in code.
- Bigger feature ideas or scope questions are best raised as a GitHub Discussion (Ideas
  category) first.
- Keep PRs focused — one concern per PR.

## Building the project

- Android Studio, Kotlin, compileSdk 36 / minSdk 24 / targetSdk 36, JVM target 17.
- No CI currently runs on this repo, so please build and test locally before opening a PR:
  `./gradlew assembleDebug` and `./gradlew testDebugUnitTest`.

## Code style

- No ktlint/detekt config exists in this repo yet — match the style of the file you're editing.

## Licence

- GPLv3 — by contributing, you agree your changes are licensed under the same terms.
