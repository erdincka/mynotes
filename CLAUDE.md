# MyNotes — Claude Code Guide

Personal Android tablet note-taking app: stylus-first handwriting on an infinite canvas, folders,
PDF export and sharing. No cloud sync code lives in the app; PDFs reach other devices through the
system share sheet or an export folder that a cloud app syncs.

## Build & Run

There is no Android Studio on this Mac. Gradle needs the Homebrew JDK:

```bash
# Compile only (fastest check)
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileDebugKotlin

# Unit tests (pure Kotlin, no device needed)
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest

# Debug APK → app/build/outputs/apk/debug/app-debug.apk
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug

# Install on the tablet (USB debugging on)
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions (`.github/workflows/android.yml`) runs the unit tests and uploads a debug APK on
every push to `main`. All work lands on `main`; run the tests before every push.

## Project Structure

```
app/src/main/java/uk/kayalab/mynotes/
├── MainActivity.kt               # Single activity, applies theme, hosts NavGraph
├── MyNotesApplication.kt         # @HiltAndroidApp, Timber
├── data/
│   ├── MyNotesDatabase.kt        # Room v3, migrations 1→2→3, schemas exported to app/schemas
│   ├── DataModule.kt             # Hilt: database + DAOs only (repositories are @Inject singletons)
│   ├── Note.kt / NoteSummary.kt  # Entity; summary projection used by the list (no ink blob)
│   ├── Folder.kt / FolderTree.kt # Entity; pure tree helpers (descendants, cycle guard, paths)
│   ├── NoteDao.kt / FolderDao.kt # Id-based updates so the list never needs full entities
│   ├── NoteRepository.kt / FolderRepository.kt   # deleteTree() cascades in one transaction
│   ├── SettingsRepository.kt     # DataStore: theme, font, export folder, stylus buttons
│   └── StylusSettings.kt         # StylusButtonAction + StylusConfig
├── export/
│   ├── PdfLayout.kt              # Pure A4 layout maths (unit tested)
│   ├── PdfRenderer.kt            # Strokes → multi-page PdfDocument
│   └── PdfExportService.kt       # Export to SAF folder / app storage, or share sheet
└── ui/
    ├── NavGraph.kt               # folders / note/{id} / settings
    ├── FolderListViewModel.kt    # List state, search, selection, delete confirmation, move guard
    ├── FolderListScreen.kt       # Tree or flat search results, dialogs
    ├── FolderTreeItems.kt        # Row composables, overflow menus, Move/Delete/Name dialogs
    ├── NoteViewModel.kt          # Strokes, undo/redo, load state, autosave
    ├── NoteSaver.kt              # App-scoped save so leaving the screen cannot cancel a write
    ├── NoteScreen.kt             # Canvas host, toolbar, save-on-exit / on-stop
    ├── SettingsScreen.kt / SettingsViewModel.kt
    ├── theme/MyNotesTheme.kt     # Material 3, dynamic colour on Android 12+, LocalIsDarkTheme
    └── canvas/
        ├── StrokeData.kt         # StrokeData, CanvasTool, StrokeCodec (the only JSON entry point)
        ├── StrokeGeometry.kt     # Pure erase / lasso / move / bounds (unit tested)
        ├── CanvasView.kt         # Pointer input, stylus buttons, pan/zoom, cached path drawing
        ├── CanvasToolbar.kt
        └── fluentui-system-icons_*.kt
```

## Tech Stack

Jetpack Compose + Material 3, Navigation Compose, Hilt + KSP, Room 2.6 + Flow, DataStore,
kotlinx.serialization, Timber, DocumentFile + FileProvider. JUnit 4 for unit tests.

## Key Decisions

### Ink storage and the codec
Strokes live as JSON in `notes.content`. `StrokeCodec` is the only reader and writer: it ignores
unknown keys so an older build can open notes written by a newer one, and it returns a failed
`Result` rather than an empty list on bad data. `NoteViewModel` turns a failure into
`NoteLoadState.Unreadable`, which disables editing and saving so the original bytes are never
overwritten. Never call `Json` directly on note content.

### Saving
`NoteSaver` runs saves in an application-wide scope. `viewModelScope` is cancelled the moment the
note screen is popped, which used to race the final write. Autosave fires three seconds after the
last change; `saveNow()` also runs on back, on dispose, on `ON_STOP` and in `onCleared()`, all of
which are no-ops when nothing changed.

### Stylus buttons
Hold semantics: `CanvasView` reads `currentEvent.buttons` at stroke start and maps
`isPrimaryPressed` / `isSecondaryPressed` through `StylusConfig` to a tool for that stroke only.
Compose folds `BUTTON_STYLUS_PRIMARY` into `isPrimaryPressed` (verified in the 1.8 bytecode).
Press-triggered actions (Undo) come through a `View.OnGenericMotionListener`, because button
presses while hovering never reach Compose pointer input. Pens that report `PointerType.Eraser`
always erase.

### Canvas coordinate system
Strokes are stored in content space. Screen → content: `(touch - pan) / zoom`. Built `Path`s are
cached per stroke id and reused while the `StrokeData` instance is identical, so a frame only
rebuilds paths for strokes that changed. Font sizes are content pixels, the same on screen and in
the PDF.

### PDF export
`PdfLayout.compute(bounds, referenceWidth)` fits content to the A4 width but never enlarges it
beyond what a canvas `referenceWidth` (the device width) would need, so a small sketch stays small.
Every stroke is drawn on every page and the page clips.

### Folders
Root notes use `folderId = 0`; root folders use `parentId = null`. Deleting a folder removes its
whole subtree and their notes in one transaction after a confirmation that states the counts.
`FolderTree.canMove` blocks moving a folder into itself or a descendant.

### Database
`exportSchema = true`; schemas are committed under `app/schemas`. There is no destructive
fallback: every entity change needs a migration. Migration 2→3 recreates both tables to drop the
old `isSynced` columns.

## Conventions

- No comments unless the WHY is non-obvious.
- Timber only; never `Log.*` or `println`.
- Anything launched in `viewModelScope` is wrapped in `runCatching` or the view model's
  `launchSafely`; an unhandled exception there crashes the app.
- Values read inside a running `pointerInput` coroutine go through `rememberUpdatedState`.
- UI text is en_GB.

## Roadmap

Phase 2 (canvas feel): pressure-sensitive width, motion prediction, stylus-only tuning, possibly
Jetpack Ink. Phase 3 (storage): UUID ids, one file per note. Phase 4 (UI polish): long-press
selection, page templates, thumbnails. Extras on request: handwriting recognition, images, PDF
annotation, shapes.
