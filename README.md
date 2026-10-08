# MyNotes

A personal Android tablet app for handwritten notes with a stylus. Infinite canvas, folders,
undo, lasso, and PDF export or sharing.

- **Stylus buttons:** Settings → Stylus. By default the primary barrel button erases while held
  and the secondary button lassos while held. Either can be set to highlight, undo, or nothing.
- **Getting a note onto your Mac:** open the note, choose Send as PDF, and pick Quick Share,
  OneDrive, Drive, Gmail or any other app. To send several at once, long-press notes or folders in
  the list to select them and tap Send; every note becomes its own PDF and the destination app asks
  for a folder only once. Or set an export folder inside the Google Drive app and use Export; the
  PDF then syncs on its own. OneDrive does not offer its folders to the export-folder picker.
- **Note names:** a new note is named `YYYYMMDD - ` with the cursor ready for a title, so notes
  and their PDFs sort by day.

## Building

Requires JDK 17 (`brew install openjdk@17`) and the Android SDK.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds
the same APK on every push and attaches it to the run.
