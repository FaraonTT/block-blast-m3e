# Block Blast M3E

Block Blast clone for Android: Kotlin, Jetpack Compose, Material 3 Expressive (dynamic Material You colors, spring motion). No ads, no network, no trackers.

- Drag 3 pieces onto the 8x8 board
- Full rows/columns clear, combos give bonus points
- Best score is stored locally

## Build

Every push to `main` builds the APK via GitHub Actions: see **Actions** (artifact) and **Releases** (APK download).

Locally: `gradle assembleRelease` (Gradle 8.9+, JDK 17). The release APK is signed with the debug key so it installs directly.
