# CrossFit Log

CrossFit Log is a private, offline Android workout journal. Record movements from a whiteboard, keep an optional photo visible while logging, search past training, and export or migrate your data.

There are no accounts, ads, analytics, servers, or network permission. Live workout data stays in an app-private SQLite database.

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
3. Optionally tap **Scan whiteboard** to recognize likely movement names.
4. Review the suggestions, deselect incorrect matches, and tap **Add selected**. Scanning never adds or saves movements without confirmation.
5. Enter or adjust movements. Load, result, and notes are optional and are not inferred from the photo.
6. Tap **Save**.

The whiteboard photo stays pinned while the form scrolls. Tap **Fold** to collapse it temporarily, or tap the photo for full-screen pinch zoom and pan. OCR works with both camera photos and images chosen with the system Photo Picker.

Movement autocomplete and OCR share the same fuzzy matcher. Their acceptance policies differ: autocomplete can be permissive because the user chooses a result, while OCR requires a high-confidence, unambiguous match. Existing session movements and duplicate OCR results are excluded from the review list.

### Movement catalog and matching

The app bundles a reference catalog of canonical CrossFit and functional-fitness movement names, based on the [official CrossFit movement library](https://www.crossfit.com/crossfit-movements) and supplemented with common functional-training modalities. The catalog is Kotlin reference data, not database content: it does not create workout records, appear in backups, or require a Room migration. Movement history is merged with the catalog in memory so autocomplete and OCR work even before the first session is saved.

Candidate names are normalized for case, whitespace, harmless punctuation, hyphens, common plurals, and joined words. A history spelling is treated as a catalog alias only when the match is conservative:

- Exact normalized or compact equivalents use catalog spelling, such as `Pull up` → `Pull-up` and `Dead lift` → `Deadlift`.
- Fuzzy aliases require at least **0.94** Jaro-Winkler similarity and a lead of at least **0.05** over the next catalog candidate.
- Names of three characters or fewer are never fuzzy-collapsed, protecting pairs such as `Run` and `Row`.
- Weak or ambiguous history names remain separate custom movements.
- Existing saved workout records are never rewritten when an alias is recognized.

OCR movement acceptance uses a **0.90** similarity threshold with a **0.04** ambiguity margin. It considers recognized lines, text elements, and short consecutive word windows so text such as `10 Pull Ups` or `24 cal Row` can yield movement suggestions without interpreting reps, rounds, loads, or workout structure.

### Find and edit training

- Open **History → Movement** to search by movement name. Search ignores case and harmless punctuation differences.
- Open **History → Training day** to select a date and see every session from that day.
- Open a session to review it. Tap **Edit** to change movements, date, notes, or photo, or to delete the session.

### Export or move data

Open **Export, backup & settings**:

- **Selected data (`.json`)**: choose 4 weeks, 12 weeks, this year, a custom range, or complete history. Save or copy readable workout data. Photos are excluded.
- **Backup (`.zip`)**: uses Android's system document picker to save a complete, checksummed database snapshot plus original photos and thumbnails to local storage, Google Drive, OneDrive, or another document provider. Restore validates the whole archive and then replaces the current log atomically.

Export a migration backup before uninstalling the app because Android may erase private app data.

## Storage and privacy

- Sessions and movements: Room-managed `crossfit-log.db` SQLite database in app-private storage.
- New whiteboard photos: JPEG, aspect ratio preserved, maximum dimension **1920 px**, quality **84%**.
- Thumbnails: JPEG, maximum dimension **480 px**, quality **78%**.
- Replacing, removing, or deleting a photo removes the app-managed files after the database change succeeds.
- Previously stored and restored full photos are not recompressed automatically.
- Whiteboard recognition uses the bundled Latin Google ML Kit model and does not upload photos or recognized text.
- Before recognition, the app corrects image rotation and bounds decoding. Smaller images are upscaled, and a grayscale, higher-contrast variant is created locally.
- ML Kit scans both the rotation-corrected original and enhanced variant. Recognized lines are merged and deduplicated; if enhancement recognition fails, the original result remains usable.

Handwriting, glare, low contrast, severe perspective distortion, and crowded whiteboards can still reduce recognition accuracy. OCR is assistance rather than authority: uncertain matches are omitted, and recognition failure leaves the workout draft unchanged.

## Developer setup

Requirements:

- Android Studio 2026.1 or newer
- Android SDK 37
- Android Studio's bundled JDK
- Android 8.0/API 26 or newer device or emulator

Open the repository in Android Studio, let Gradle sync, select the `app` configuration, and run it. Camera access is requested only when taking a photo; the system Photo Picker does not require broad media access.

Command-line checks:

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug
./gradlew connectedDebugAndroidTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Architecture

- Kotlin and Jetpack Compose UI
- Room/SQLite persistence with session and movement tables
- CameraX camera capture and Android Photo Picker import
- Bundled Google ML Kit Text Recognition v2 Latin model for offline OCR
- Apache Commons Text Jaro-Winkler movement matching
- Built-in CrossFit and functional-fitness movement reference catalog
- App-private JPEG photo and thumbnail storage
- Storage Access Framework export and restore
- Versioned, validated ZIP migration format

Room schema snapshots are checked into `app/schemas`. Update the database version and provide a migration whenever stored tables or columns change.
