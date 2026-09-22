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

# Room migration test on the connected tablet (uses app/schemas as test assets)
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:connectedDebugAndroidTest

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
│   ├── MyNotesDatabase.kt        # Room v4, migrations 1→2→3→4, schemas exported to app/schemas
│   ├── DataModule.kt             # Hilt: database + DAOs only (repositories are @Inject singletons)
│   ├── Note.kt / NoteSummary.kt  # Entity (metadata + thumbnail); list projection with stroke count
│   ├── StrokeEntity.kt / StrokeDao.kt / StrokePacking.kt  # One row per stroke, float32 blobs
│   ├── Folder.kt / FolderTree.kt # Entity; pure tree helpers (descendants, cycle guard, paths)
│   ├── NoteDao.kt / FolderDao.kt # Id-based updates so the list never needs full entities
│   ├── NoteRepository.kt / FolderRepository.kt   # deleteTree() cascades in one transaction
│   ├── SettingsRepository.kt     # DataStore: theme, font, export folder, stylus buttons
│   └── StylusSettings.kt         # StylusButtonAction + StylusConfig
├── export/
│   ├── PdfLayout.kt              # Pure A4 layout maths (unit tested)
│   ├── PdfRenderer.kt            # Strokes → multi-page PdfDocument
│   ├── PdfExportService.kt       # Export to SAF folder / app storage, or share sheet
│   ├── NoteThumbnailRenderer.kt  # 320×240 PNG preview stored on the note
│   └── BackupService.kt          # Zip backup (manifest + JSON per note) and additive restore
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
        ├── StrokeOutline.kt      # Pure pressure → variable-width outline polygon (unit tested)
        ├── StrokeShapes.kt       # Emits a stroke into Compose or Android paths (fill or centreline)
        ├── CanvasView.kt         # Pointer input, stylus buttons, pan/zoom, committed-stroke bitmap layer
        ├── CanvasToolbar.kt
        └── fluentui-system-icons_*.kt
```

## Tech Stack

Jetpack Compose + Material 3, Navigation Compose, Hilt + KSP, Room 2.6 + Flow, DataStore,
kotlinx.serialization, Timber, DocumentFile + FileProvider. JUnit 4 for unit tests.

## Key Decisions

### Ink storage
Since schema v4 each stroke is a row in `strokes` (points and pressures as little-endian float32
blobs via `StrokePacking`), with a foreign key that cascades on note delete. A note of any length
therefore never approaches SQLite's 2 MB cursor-window limit, and saves are diffs. `StrokeCodec`
(lenient JSON) remains the interchange format for backups and was the v3 storage format; migration
3→4 decodes it row by row, drops `notes.content` and `category`, and adds `notes.thumbnail`.

### Saving
`NoteSaver` keeps a per-note baseline (stroke instance and ordinal by id) primed after load, and
on save deletes vanished ids, upserts new or changed instances, renders the thumbnail and bumps
`updatedAt` in one transaction. It runs in an application-wide scope because `viewModelScope` is
cancelled the moment the note screen is popped. Autosave fires three seconds after the last
change; `saveNow()` also runs on back, on dispose, on `ON_STOP` and in `onCleared()`.

### Backup
Settings → Backup writes a zip through the system file picker: `manifest.json` (folders and note
metadata) plus `notes/<id>.json` of strokes. Restore is additive: folders and notes get fresh ids,
strokes get fresh ids, and nothing is deleted. Enterprise cloud sign-in is deliberately absent.

### Stylus buttons
Hold semantics: `CanvasView` reads `currentEvent.buttons` at stroke start and maps
`isPrimaryPressed` / `isSecondaryPressed` through `StylusConfig` to a tool for that stroke only.
Compose folds `BUTTON_STYLUS_PRIMARY` into `isPrimaryPressed` (verified in the 1.8 bytecode).
Press-triggered actions (Undo) come through a `View.OnGenericMotionListener`, because button
presses while hovering never reach Compose pointer input. Pens that report `PointerType.Eraser`
always erase.

### Canvas coordinate system and rendering
Strokes are stored in content space. Screen → content: `(touch - pan) / zoom`. Shapes are cached
per stroke id while the `StrokeData` instance is identical. Committed, unselected strokes are
rendered once into a screen-sized `ImageBitmap` (`CommittedLayer`) whenever strokes, selection,
pan, zoom, theme or size change; a frame during inking is one blit plus the live stroke. Selected
strokes, the live stroke and the lasso draw on top. Font sizes are content pixels, the same on
screen and in the PDF.

### Pressure and prediction
Pen and brush strokes whose recorded pressures vary become filled outline polygons built by
`StrokeOutline` (Catmull-Rom resample, per-point width, round caps, filler circles on sharp
turns, all wound the same way so non-zero fill unions them). Strokes without pressure variation,
highlighter and lasso stay stroked centrelines, so finger input and old notes look as before.
`StrokeShapes.emit` is the single place that decides fill versus stroke; the PDF uses it too.
Pressure is smoothed at capture time. `MotionEventPredictor` is fed through `motionEventSpy`; once
per frame while inking the predicted points are drawn as a tail that is never stored. While a
stylus is down, touch pointers are ignored for the two-finger pan/zoom check so a resting palm
cannot cancel a stroke.

### PDF export
`PdfLayout.compute(bounds, referenceWidth)` fits content to the A4 width but never enlarges it
beyond what a canvas `referenceWidth` (the device width) would need, so a small sketch stays small.
Every stroke is drawn on every page and the page clips.

### Folders
Root notes use `folderId = 0`; root folders use `parentId = null`. Deleting a folder removes its
whole subtree and their notes in one transaction after a confirmation that states the counts.
`FolderTree.canMove` blocks moving a folder into itself or a descendant.

### Database
`exportSchema = true`; schemas are committed under `app/schemas` and served as androidTest assets
for `MigrationTest`, which runs on the connected tablet. There is no destructive fallback: every
entity change needs a migration and a case in that test. The migration's CREATE TABLE for
`strokes` must equal Room's `createSql` in `app/schemas/.../4.json`.

## Conventions

- No comments unless the WHY is non-obvious.
- Timber only; never `Log.*` or `println`.
- Anything launched in `viewModelScope` is wrapped in `runCatching` or the view model's
  `launchSafely`; an unhandled exception there crashes the app.
- Values read inside a running `pointerInput` coroutine go through `rememberUpdatedState`.
- UI text is en_GB.

## Roadmap

Phases 1–3 are done: data safety, stylus buttons, PDF share, pressure ink, bitmap layer,
prediction, per-stroke storage, thumbnails, backup/restore. Jetpack Ink stays an option if latency
is ever visible. Phase 4 (UI polish): single-row toolbar with colour/width popover (portrait
squeezes the colour row), long-press selection, page templates. Extras on request: handwriting
recognition, images, PDF annotation, shapes. Extras on request: handwriting recognition, images, PDF
annotation, shapes.
