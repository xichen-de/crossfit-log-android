# Developer guide

This guide covers building, testing, and releasing CrossFit Log, and how the code fits together. For what the app does from a user's point of view, see the [README](../README.md).

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 25 | Gradle's daemon toolchain is pinned in `gradle/gradle-daemon-jvm.properties`; Gradle can download it automatically. CI uses Temurin 25. |
| Android SDK | Platform 37 | `compileSdk` and `targetSdk` are 37. `minSdk` is 26 (Android 8.0). |
| Android Studio | A release that supports AGP 9.1 | Recent stable versions work. |
| Gradle | 9.3.1 | Provided by the wrapper; always use `./gradlew`. |

Main dependencies: Kotlin 2.2 with Jetpack Compose (Material 3), Room 2.7 with KSP, Paging 3, CameraX, ML Kit text recognition (bundled model), kotlinx.serialization, and Apache Commons Text for Jaro-Winkler similarity. Versions are in [`app/build.gradle.kts`](../app/build.gradle.kts).

## Build and run

Open the project in Android Studio, let Gradle sync, and run the `app` configuration on a device or emulator with API 26 or higher.

Build variants:

| Variant | Application ID | Signing | Notes |
|---|---|---|---|
| `debug` | `dev.xichen.crossfitlog.debug` | Debug key | Installs next to the release app with separate data. Backups work across variants. |
| `release` | `dev.xichen.crossfitlog` | `keystore.properties` if present, otherwise unsigned | R8 minification and resource shrinking are on. |

### Local release signing

To sign release builds locally, create `keystore.properties` in the repository root. Both this file and `*.jks` are git-ignored.

```properties
storeFile=/absolute/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Android only installs an update when it's signed with the same key as the installed app. Users who switch between self-built and official APKs must back up, uninstall, reinstall, and restore.

## Testing

```sh
./gradlew testDebugUnitTest            # JVM unit tests: domain logic, codecs, UI state helpers
./gradlew lintRelease                  # Android lint for the release variant
./gradlew assembleDebug assembleRelease
./gradlew connectedDebugAndroidTest    # instrumented tests; needs a running device or emulator
```

- **Unit tests** (`app/src/test`) cover movement matching and normalisation, OCR suggestion policy, the backup and export codecs, and editor state rules.
- **Instrumented tests** (`app/src/androidTest`) cover Room queries, photo import, backup and restore round trips, session sharing, and an end-to-end Compose flow (create → edit → duplicate → history → delete).
- CI doesn't run instrumented tests. Run `connectedDebugAndroidTest` locally before merging changes to data, backup, or UI flows.
- For Compose tests, reach lazy-list items with `performScrollToNode(...)` on the list, not `performScrollTo()`: items outside the viewport aren't composed.

## Continuous integration and releases

[`android-ci.yml`](../.github/workflows/android-ci.yml) runs on every push and pull request to `main`:

```sh
./gradlew testDebugUnitTest lintRelease assembleDebug assembleDebugAndroidTest assembleRelease
```

It then fails the build if the merged release manifest contains `INTERNET` or `ACCESS_NETWORK_STATE`.

[`android-release.yml`](../.github/workflows/android-release.yml) publishes a GitHub release when a tag matching `v*` is pushed:

1. The tag must look like `v1.2.3` or `v1.2.3-rc.1`. A suffix marks the release as a pre-release.
2. `versionName` is the tag without the `v`. `versionCode` is `major × 1,000,000 + minor × 1,000 + patch`, so minor and patch must each be 999 or lower. Version codes must always increase, so never reuse or move a tag.
3. Signing uses the repository secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`.
4. The workflow uploads `crossfit-log-<version>.apk` and `SHA256SUMS`. It refuses to overwrite an existing release.

To cut a release:

```sh
git tag v1.2.3
git push origin v1.2.3
```

Local builds without the `CROSSFIT_LOG_VERSION_NAME` and `CROSSFIT_LOG_VERSION_CODE` environment variables use the fallback version in `app/build.gradle.kts`.

## Architecture

The app is a single activity. Compose screens receive state from ViewModels, which read and write through a repository over Room. There is no dependency-injection framework: `CrossFitLogApplication` is the service locator.

```
dev.xichen.crossfitlog
├── CrossFitLogApplication   service locator: database, repository, photo store, OCR, backup, export
├── MainActivity             hosts CrossFitLogApp (navigation)
├── domain/                  pure Kotlin: models, movement catalog, fuzzy matching, OCR suggestions
├── data/local/              Room entities and DAO, DatabaseController, PhotoStore
├── data/repository/         WorkoutRepository: entity ↔ domain mapping, queries
├── data/backup/             backup archive format (BackupCodec) and backup/restore (BackupService)
├── data/export/             JSON export (DataExport) and single-session sharing (SessionShare)
├── ocr/                     image preprocessing and the ML Kit recogniser
└── ui/                      Compose screens, ViewModels, and theme
```

Navigation routes (`ui/CrossFitLogApp.kt`): `sessions`, `editor/{id|new}`, `duplicate/{sourceId}`, `details/{id}`, `history`, and `settings`.

### Things to know before changing code

**The database instance can be replaced.** Restore closes the live Room database, swaps the files, and opens a new instance (`DatabaseController`). `CrossFitLogApplication.repository` is therefore a getter that builds a repository for the *current* database. Never cache a DAO or repository outside a ViewModel. After a restore, the app pops the back stack so every ViewModel is rebuilt; if you add screens reachable from Settings, keep that working.

**Long-running work belongs in ViewModels.** `targetSdk` 37 means the portrait lock is ignored on large screens, so activities get recreated. Backup, restore, and export run in `SettingsViewModel`, so a rotation can't cancel a restore halfway through or lose a prepared backup. Editor drafts are persisted to `SavedStateHandle` as JSON so they survive process death.

**Photo files.** `PhotoStore` keeps photos in `filesDir/photos` (longest side at most 1920 px, JPEG quality 84) and thumbnails in `filesDir/photos/thumbnails` (at most 480 px, quality 78). Every import gets a new unique filename, so:

- an unsaved edit never overwrites the photo that the saved session still references, and the editor cleans up whichever file loses;
- UI code can use `photoLocation()` and `thumbnailLocation()`, which don't touch the disk, and the in-memory image cache is keyed by path. Call `clearLocalImageCache()` if files can change under an existing name, as restore does.

Camera captures go to `cacheDir/camera-*.jpg` and are deleted after import. The original, unresized image is kept in `cacheDir/whiteboard-ocr` while the draft is open, so scanning can use full resolution.

**Movement matching** (`domain/MovementMatcher.kt`) is shared by autocomplete, OCR suggestions, and catalog alias resolution. Names are normalised (accents removed, lowercase, punctuation to spaces, simple plural stripping) and scored with Jaro-Winkler, with an `i`→`l` variant for common OCR confusion. `MovementMatcher` caches normalised candidates per list *instance*, so pass the same list object when ranking many queries. `MovementCatalog` holds the built-in names; suggestions merge the user's history with the catalog, and close aliases resolve to the catalog spelling.

**OCR** (`ocr/`) runs two ML Kit passes, one on the original image and one on a grayscale, contrast-enhanced copy, then merges the recognised lines. `WhiteboardMovementSuggester` only accepts confident, unambiguous matches of 1- to 5-word fragments against known movement names.

### Data model

Room database `crossfit-log.db`, schema version 1, exported to [`app/schemas`](../app/schemas):

- `workout_sessions`: `id` (UUID), `session_time`, `session_note`, `photo_filename`, `thumbnail_filename`, `created_at`, `updated_at`
- `movement_records`: `id` (UUID), `session_id` (FK, cascading delete), `name`, `normalized_name` (indexed; used by search and suggestions), `load`, `result`, `note`, `display_order`

Load and result are free text by design. Workouts vary too much to model them.

### Changing the schema

1. Update the entities, increment `version` in `@Database`, **and** `CrossFitDatabase.SCHEMA_VERSION`.
2. Add a Room `Migration` and register it in `CrossFitDatabase.create`.
3. Build once and commit the new JSON file in `app/schemas`.
4. **Update restore.** `BackupService.validateDatabase` rejects backups whose `PRAGMA user_version` differs from `SCHEMA_VERSION`. Without a change there, every backup made by an earlier version stops restoring. Migrate older snapshots in the staging directory before validating them, and add a test that restores a version-1 backup.

### Backup format (`formatVersion` 2)

A zip archive:

```
manifest.json                     { format: "crossfit-log", formatVersion: 2, exportedAt, applicationVersion,
                                    sessionCount, files: [{ path, size, sha256 }] }
database.sqlite                   consistent copy of the Room database
photos/<file>.jpg                 one per session with a photo
photos/thumbnails/<file>.jpg
```

Creating a backup checkpoints the WAL, copies the database file inside a transaction (failing if a write slipped in after the checkpoint), and copies the referenced photos into a staging directory before zipping. JPEGs are stored without recompression.

Restore extracts to a staging directory and validates everything before touching live data: safe entry paths, no duplicate entries, size limits (250 MB per entry, 1 GB total), SHA-256 of every file, SQLite `quick_check` and `foreign_key_check`, schema version, UUIDs, one photo and thumbnail per referenced session, and no orphaned files. The swap is journalled in `filesDir/.restore-rollback`; if the app dies mid-restore, the previous data is rolled back on the next start.

### Export format (`exportSchemaVersion` 1)

Exports and shared sessions are JSON with `type: "crossfit-log-export"`. They contain `exportedAt`, `range { label, startDate, endDate }`, `sessionCount`, `movementCount`, and a list of `sessions { id, date, sessionNote, updatedAt, movements [{ name, load, result, note }] }` in chronological order. Timestamps are ISO-8601 in UTC. Photos and internal filenames are never included. Tools outside the app read this format, so keep changes backward compatible or bump `exportSchemaVersion`.

## Privacy constraints

These are product guarantees. Please keep them when you change code:

- **No network access.** The manifest removes `INTERNET` and `ACCESS_NETWORK_STATE`, which ML Kit pulls in for optional telemetry, and CI enforces this. Don't add dependencies that need the network. The text-recognition model is bundled; don't switch to the Play-services-downloaded variant.
- **No automatic cloud backup.** `allowBackup="false"` and the backup and data-extraction rules exclude everything. Users move data only through explicit backups.
- **The camera permission is requested only when the user opens the camera.**
- **Exports and shared sessions never contain photos or filenames.**
