# File Navigator Lite

File Navigator Lite is an independent Android file manager and rule-based organizer built with Kotlin, Jetpack Compose, Material 3, Coroutines, DataStore, and WorkManager. It does not copy the implementation of `w2sv/FileNavigator`.

**Package:** `com.bijoyism.filenavigator`  
**Minimum Android:** 10 (API 29)  
**Build configuration:** Java 17, Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compile SDK 35.

## Features in this source release

### File manager

- Choose an accessible folder through Android's Storage Access Framework (SAF), persist its URI grant, and browse subfolders.
- Search the current folder, sort by name/size/modified date/type, and switch between list and grid views.
- File details, MIME/extension-based labels, opening files with installed apps, and sharing through Android's Sharesheet.
- Multi-select files, create folders, rename, delete with confirmation, copy, and move.
- Create ZIP archives from selected files and extract ZIP entries into a chosen folder.
- Favorites and recent open/share activity.

### Organization rules

- Create, edit, enable/disable, and delete persistent rules.
- Select source and destination folders and match extensions, MIME types, filename keywords, and optional min/max size.
- Choose copy or move and unique-name or skip conflict handling. Replacement is deliberately refused rather than risking silent data loss.
- Preview matching files before manual execution. Each affected item records success/failure in local history.
- Manually scan now, or enable periodic WorkManager scanning. Newly detected matching files can generate notifications with actions to organize, open a matching file, review the rule, or dismiss.

### History and privacy

- Keep a bounded local operation history, view errors, and undo supported copy/create/rename/move operations where the storage provider permits it.
- Settings for periodic scans, notifications, quiet mode, and dark theme.
- No account, analytics, ad SDK, file upload, or network activity is included in the app's runtime code. Build tooling downloads Gradle/dependencies during CI.

## Important limitations

Android's SAF provider defines which folders and operations are available. File Navigator Lite does not request `MANAGE_EXTERNAL_STORAGE` and does not promise unrestricted access to the filesystem.

- The app can browse only user-granted folder trees; Android/data, Android/obb, protected system locations, and some provider-managed locations may be unavailable.
- Organization and detection currently inspect direct files in each selected source folder, not nested descendants. File search is also limited to the current folder.
- Periodic scans use WorkManager and may be delayed by Doze, battery optimization, or Android background limits. They are not instant file watchers.
- Copying supports files. Moving files prefers a provider-native move, otherwise performs verified copy-then-delete. Folder moves work only when the provider supports a native move; folder copy is not implemented.
- ZIP creation accepts files only. Extraction writes files into the selected destination root (not the archive's nested directory structure), rejects unsafe paths, limits archives to 10,000 entries and 1 GiB of extracted data, and rolls back created files when possible.
- Safe replacement of an existing destination is not supported. Unique-name and skip policies are available.
- Undo is best-effort and depends on provider support and whether the original state still exists. A permanent delete cannot be reliably undone.
- A full Android Gradle build and device-level test have not yet completed in the authoring environment. See `BUILD_STATUS.md`; do not treat the source archive as a verified release APK.

## Build with GitHub Actions (no PC required)

The repository includes `.github/workflows/build-apk.yml`. GitHub Actions will install JDK 17, Gradle 8.9, Android SDK Platform 35 and Build Tools 35.0.0; then run formatting, unit tests, Android lint, and `assembleDebug`. The APK is uploaded as the artifact `file-navigator-lite-debug-apk` only if the build step succeeds. For phone-only instructions, see `BUILD_APK_FROM_ANDROID_PHONE.md`.

1. Create a new empty GitHub repository in your browser.
2. Extract this archive on your phone.
3. Upload the extracted project files to the repository (or push them from Termux with Git).
4. Open the repository's **Actions** tab and run **Build Android APK**. If GitHub asks you to enable workflows, enable them.
5. Open the completed workflow run and download `file-navigator-lite-debug-apk` under **Artifacts**. Extract that artifact archive to get `app-debug.apk`.

Do not upload this source ZIP as a single file and expect GitHub Actions to build it; the workflow files must be in the repository root with `.github/workflows/build-apk.yml` in its expected path.

### Build locally

Requirements: JDK 17, Android SDK Platform 35, Build Tools 35.0.0, and internet access for Gradle/Maven dependencies.

```sh
./gradlew ktlintFormat
./gradlew ktlintCheck
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

The included `gradlew` is a small bootstrap script because the standard Gradle wrapper JAR could not be retrieved in this authoring environment. GitHub Actions generates the official wrapper using the configured Gradle distribution before build tasks. For normal repository maintenance, generate and commit the standard Gradle wrapper JAR from a trusted Gradle 8.9 distribution.

Expected APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Install and test on a Redmi 9i

1. Download the successful GitHub Actions APK artifact and extract it.
2. Open `app-debug.apk` on the phone and allow installation from that file manager/browser if Android asks.
3. Open File Navigator Lite and select a disposable test folder from **Files**.
4. Try subfolder navigation, search and sorting, create-folder/rename, and opening/sharing a sample file.
5. Select a small test file and try copy/move into a second user-selected folder. Verify the destination before trying move on anything valuable.
6. Create a rule with a small test extension (for example, `txt`), choose separate test source/destination folders, preview matches, and execute it.
7. Try ZIP creation/extraction on disposable files, then inspect **Activity** and test Undo only for operations that show an Undo action.
8. Enable notifications and automatic scanning in **Settings** only after granting access to test folders. Scans may run later than the 15-minute minimum interval due to Android's scheduler.

Back up important files before testing destructive actions. Some document providers silently differ in which operations they support; errors are shown rather than treated as success.

## Source layout

```text
app/src/main/java/com/bijoyism/filenavigator/
├── MainActivity.kt
├── data/          # DataStore repositories and local records
├── domain/        # Rule matching, conflict resolution, scan diff
├── storage/       # SAF listing, copy/move/delete/rename, ZIP operations
├── ui/            # Compose screens and browser ViewModel
├── notifications/ # Rule notifications and action receiver
└── worker/        # Periodic detection and user-triggered rule execution
```

## License

MIT. See `LICENSE`.
