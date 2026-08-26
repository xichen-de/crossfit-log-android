<p align="center">
  <img src="design/final-icon.svg" alt="CrossFit Log logo" width="128" height="128">
</p>

<h1 align="center">CrossFit Log</h1>

<p align="center">
  A private, offline workout journal for Android.
</p>

<p align="center">
  <a href="https://github.com/xichen-de/crossfit-log-android/actions/workflows/android-ci.yml"><img src="https://github.com/xichen-de/crossfit-log-android/actions/workflows/android-ci.yml/badge.svg?branch=main" alt="Android CI"></a>
</p>

CrossFit Log is a local Android app for recording and reviewing CrossFit workouts. It stores flexible sessions with movements, loads, results, and notes, and can scan movement names straight from a whiteboard photo.

## Screenshots

<table>
  <tr>
    <td><img src="docs/screenshots/session-list.png" alt="Session list" width="320"></td>
    <td><img src="docs/screenshots/session-editor.png" alt="Session editor" width="320"></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/history-search.png" alt="History search" width="320"></td>
    <td><img src="docs/screenshots/export-range.png" alt="Export and backup" width="320"></td>
  </tr>
</table>

## What it supports

- Flexible workout sessions with movements, loads, results, and notes
- Whiteboard photo capture with offline movement-name scanning
- Duplicate, edit, delete, and search previous sessions
- Export workout data or create a complete migration backup
- Fully offline operation with no accounts, ads, analytics, or network permission

## Developer setup

Open the project in Android Studio, let Gradle sync, and run the `app` configuration on an Android 8.0/API 26 or newer device or emulator.

To verify the project from a terminal:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew lintRelease assembleRelease
./gradlew connectedDebugAndroidTest
```

When changing the Room database, increment its version, add a migration, and commit the updated schema from `app/schemas`.

## Built with

- Kotlin and Jetpack Compose with Material 3
- Room/SQLite local session persistence
- CameraX for whiteboard capture
- ML Kit text recognition for offline movement scanning
- Coroutines and StateFlow

## Privacy

The app does not request internet access. Sessions and photos remain on the device and no usage data is collected.

## Install

Download the APK from [GitHub Releases](https://github.com/xichen-de/crossfit-log-android/releases/latest). Android may ask you to allow installation from your browser or file manager.

Updates preserve app data. Create a backup before uninstalling the app or moving to another device.

## License

CrossFit Log is available under the [MIT License](LICENSE).
