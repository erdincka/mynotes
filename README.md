# MyNotes

A personal Android tablet app for handwritten notes with a stylus. Infinite canvas, folders,
undo, lasso, and PDF export or sharing.

- **Stylus buttons:** Settings → Stylus. By default the primary barrel button erases while held
  and the secondary button lassos while held. Either can be set to highlight, undo, or nothing.
- **Getting a note onto your Mac:** open the note, tap Share, and pick Quick Share, Drive, Gmail
  or any other app. Or set an export folder inside the Google Drive or OneDrive app and use
  Export; the PDF then syncs on its own.

## Building

Requires JDK 17 (`brew install openjdk@17`) and the Android SDK.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds
the same APK on every push and attaches it to the run.
