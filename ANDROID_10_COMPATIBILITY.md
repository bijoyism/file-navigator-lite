# Android 10 compatibility notes

Minimum supported API: 29 (Android 10). The configured target is API 35. The project uses Java 17, AGP 8.7.3, and Gradle 8.9, a compatible combination for compile SDK 35. The relevant AGP compatibility table specifies Gradle 8.9 and JDK 17 for AGP 8.7. See the official [AGP 8.7 release notes](https://developer.android.com/build/releases/agp-8-7-0-release-notes).

## Storage access

- Folder access is granted using `ACTION_OPEN_DOCUMENT_TREE` and persisted with `takePersistableUriPermission` when supported.
- Browsing and mutations use document/content URIs and `DocumentsContract`, not hard-coded absolute storage paths.
- The app does not request `MANAGE_EXTERNAL_STORAGE` and has no legacy broad-storage permission.
- Providers can refuse write, rename, delete, or move requests. Failures are surfaced in the UI/history.
- Copy verifies the destination bytes against a SHA-256 digest before reporting success. Cross-provider moves copy and verify before attempting source deletion; source deletion failure triggers rollback of the destination copy where possible.
- Existing-name collisions use unique names or skip. Replacement is refused.
- Folder copy is unavailable. Folder move is available only when the provider supports native move.
- ZIP extraction rejects absolute and parent-traversal paths, extracts into the chosen destination root, limits the number of entries to 10,000 and total extracted bytes to 1 GiB, and tries to remove partial outputs after an error.

## Background work

WorkManager periodic scanning has a 15-minute minimum interval and is subject to OS scheduling and battery limits. It inspects direct children of each enabled rule's source folder; it does not provide an instant filesystem watcher and does not recurse into nested subfolders. The first scan establishes the baseline and does not notify for every existing match.

## Still required before calling this a verified release

- A successful `assembleDebug`, unit-test, ktlint, and Android lint run in GitHub Actions.
- Installation and functional operation testing on Android 10/API 29 and a current Android version.
- Testing actual storage-provider variations, including permission revocation, move fallback, ZIP extraction rollback, and notifications on Android 13+.

The source's minimum SDK setting does not substitute for device-level verification.
