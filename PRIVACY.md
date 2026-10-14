# Privacy and permissions

File Navigator Lite is designed for local-only file management.

- Folder access begins with the user selecting a location through Android's Storage Access Framework.
- The app requests notification permission on Android 13+ only when the user enables notifications in Settings.
- No `MANAGE_EXTERNAL_STORAGE`, broad legacy storage permission, analytics, ads, account, tracking, or runtime file-upload feature is included.
- Organization rules, chosen root URI, favorites, seen-file scan keys, settings, and a bounded activity history are stored locally with Preferences DataStore.
- File contents are read only for user-requested operations such as opening, sharing, copying, ZIP creation/extraction, or executing a selected organization rule.
- Opening and sharing hands a content URI to an installed app only in response to a user action or a notification action.
- Notification text may include the rule name and matching file names. Users can turn notifications off or enable quiet mode.
- A user can revoke SAF permissions in Android settings. The app reports operation/scan errors rather than bypassing Android's storage restrictions.

The build workflow downloads Android/Gradle dependencies in GitHub Actions. The app itself does not contact a project backend or upload files.
