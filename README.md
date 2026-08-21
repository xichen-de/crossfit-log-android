# CrossFit Log

[![Android CI](https://github.com/xichen-de/crossfit-log-android/actions/workflows/android-ci.yml/badge.svg?branch=main)](https://github.com/xichen-de/crossfit-log-android/actions/workflows/android-ci.yml)

CrossFit Log is a private, offline Android workout journal. Record movements from a whiteboard, keep an optional photo visible while logging, search past training, and export or migrate your data.

There are no accounts, ads, analytics, servers, or network permission. Workout data stays in an app-private SQLite database.

## Screenshots

<table>
  <tr>
    <td align="center"><strong>Session list</strong></td>
    <td align="center"><strong>Session editor</strong></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/session-list.png" alt="Recorded workout sessions" width="320"></td>
    <td><img src="docs/screenshots/session-editor.png" alt="Editing a session with a pinned whiteboard photo" width="320"></td>
  </tr>
  <tr>
    <td align="center"><strong>History search</strong></td>
    <td align="center"><strong>Export and backup</strong></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/history-search.png" alt="Movement history search results" width="320"></td>
    <td><img src="docs/screenshots/export-range.png" alt="Data export and migration backup options" width="320"></td>
  </tr>
</table>

## Use the app

### Record a session

1. Tap **New session**.
2. Take or choose an optional whiteboard photo.
3. Optionally tap **Scan whiteboard** to recognize likely movement names, then review and confirm the suggestions — nothing is added automatically.
4. Enter or adjust movements, load, results, and notes.
5. Tap **Save**.

The whiteboard photo stays pinned while the form scrolls, and supports full-screen pinch zoom. Movement name autocomplete and OCR both use fuzzy matching against a bundled catalog of CrossFit and functional-fitness movements, so near-misses (typos, plurals, alternate spellings) still resolve to the right movement.

### Find and edit training

- **History → Movement**: search past sessions by movement name.
- **History → Training day**: browse every session logged on a given date.
- Open a session to review, edit, or delete it.

### Export or move data

Open **Export, backup & settings**:

- **Selected data (`.json`)**: export a date range or your complete history as readable workout data (photos excluded).
- **Backup (`.zip`)**: save a complete, checksummed snapshot — database, photos, and thumbnails — to local storage or a cloud provider via Android's document picker. Restoring validates the archive and replaces the current log atomically.

Export a migration backup before uninstalling the app, since Android may erase private app data.

## Privacy

- Data lives in an app-private Room/SQLite database; nothing leaves the device.
- Whiteboard recognition runs fully offline via bundled Google ML Kit — no photos or text are uploaded.
- OCR is assistance, not authority: uncertain matches are omitted, and a failed scan leaves your draft unchanged.

## Developer setup

Requirements:

- Android Studio 2026.1 or newer
- Android SDK 37
- Android Studio's bundled JDK
- Android 8.0/API 26 or newer device or emulator

Open the repository in Android Studio, let Gradle sync, select the `app` configuration, and run it.

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug
./gradlew connectedDebugAndroidTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Architecture

- Kotlin and Jetpack Compose UI
- Room/SQLite persistence with session and movement tables
- CameraX capture and Android Photo Picker import
- Bundled Google ML Kit Text Recognition v2 (Latin) for offline OCR
- Apache Commons Text Jaro-Winkler movement matching
- Storage Access Framework export and restore, with a versioned ZIP migration format

Room schema snapshots are checked into `app/schemas`. Update the database version and provide a migration whenever stored tables or columns change.

## License

CrossFit Log is available under the [MIT License](LICENSE).
