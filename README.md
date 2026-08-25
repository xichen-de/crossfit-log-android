# CrossFit Log

[![Android CI](https://github.com/xichen-de/crossfit-log-android/actions/workflows/android-ci.yml/badge.svg?branch=main)](https://github.com/xichen-de/crossfit-log-android/actions/workflows/android-ci.yml)

A private, offline workout journal for Android.

## Features

- Record flexible workout sessions with movements, loads, results, and notes.
- Capture a whiteboard photo and scan movement names offline.
- Duplicate, edit, delete, and search previous sessions.
- Export workout data or create a complete migration backup.
- No accounts, ads, analytics, servers, or network permission.

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

## Install

Download the APK from [GitHub Releases](https://github.com/xichen-de/crossfit-log-android/releases/latest). Android may ask you to allow installation from your browser or file manager.

Updates preserve app data. Create a backup before uninstalling the app or moving to another device.

## Build

Requires Android Studio 2026.1 or newer, Android SDK 37, and JDK 25.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew lintRelease assembleRelease
./gradlew connectedDebugAndroidTest
```

## License

[MIT](LICENSE)
