# MyNotes — Claude Code Guide

Android note-taking app with stylus-first handwriting/drawing canvas and OneDrive sync.

## Build & Run

```bash
# Build debug APK (from project root)
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug

# Compile-only check (faster)
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugSources

# Run from Android Studio normally — no special flags needed
```

All source changes must be on the `main` branch for Android Studio to pick them up.
The `.claude/worktrees/` directory is ephemeral; always commit and merge to `main` when done.

## Project Structure

```
app/src/main/java/uk/kayalab/mynotes/
├── MainActivity.kt              # Single activity, hosts NavGraph
├── MyNotesApplication.kt        # @HiltAndroidApp + WorkManager/Hilt wiring
├── data/
│   ├── MyNotesDatabase.kt       # Room DB v2, migration 1→2
│   ├── DataModule.kt            # Hilt @Module — DB, DAOs, Repositories
│   ├── Note.kt / Folder.kt      # Room entities (@Serializable)
│   ├── NoteDao.kt / FolderDao.kt
│   ├── NoteRepository.kt / FolderRepository.kt
│   └── SettingsRepository.kt    # DataStore preferences
├── sync/
│   ├── OneDriveAuthManager.kt   # MSAL single-account sign-in (lazy init)
│   ├── OneDriveClient.kt        # Ktor HTTP client → Microsoft Graph API
│   ├── SyncScheduler.kt         # Enqueues WorkManager jobs
│   └── SyncWorker.kt            # @HiltWorker background sync
├── ui/
│   ├── NavGraph.kt              # Compose Navigation: folders / note/{id} / settings
│   ├── FolderListScreen.kt      # Main screen — folder tree, note list, PDF export
│   ├── NoteScreen.kt            # Canvas host with toolbar, auto-save
│   ├── NoteViewModel.kt         # Stroke state, undo/redo, erase, lasso
│   ├── SettingsScreen.kt        # Dark theme, font, export folder, OneDrive
│   ├── SettingsViewModel.kt     # OneDrive sign-in, folder picker
│   └── canvas/
│       ├── CanvasView.kt        # Drawing surface — pointer input, pan/zoom
│       ├── CanvasToolbar.kt     # Tool/color/stroke-width picker
│       └── PdfExporter.kt       # Bounding-box layout → multi-page A4 PDF
└── util/
    ├── ExportManager.kt         # PNG + PDF export via SAF or internal storage
    └── Timestamp.kt
```

## Tech Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose |
| DI | Hilt (Dagger) + KSP |
| Database | Room v2 (SQLite) + Flow |
| Preferences | DataStore |
| Background work | WorkManager + `hilt-work` |
| Networking | Ktor (OkHttp engine) |
| Auth | MSAL (`com.microsoft.identity.client:msal:5.3.0`) |
| Serialization | kotlinx.serialization |
| Logging | Timber + Chucker (debug only) |
| File sharing | FileProvider + SAF (DocumentFile) |

## Key Architectural Decisions

### WorkManager + Hilt
`MyNotesApplication` implements `Configuration.Provider` and injects `HiltWorkerFactory`.
The WorkManager default auto-initializer is disabled in `AndroidManifest.xml` via
`tools:node="remove"` on `WorkManagerInitializer`. Without this, `@HiltWorker` classes
cannot be instantiated and sync crashes at runtime.

### Canvas coordinate system
Strokes are stored in **content space** (independent of pan/zoom).
Screen → content: `pos = (touchPos - panOffset) / zoomScale`
Content → screen: `screenPos = contentPos * zoomScale + panOffset`
`PdfExporter` works directly in content space and scales to fit A4 width.

### Two-finger pan/zoom
Detected inside `awaitEachGesture` — when `event.changes.count { it.pressed } >= 2`,
the in-progress stroke is cancelled and a pan/zoom sub-loop takes over.
The `PointerType` guard was deliberately removed because some devices report `Unknown`.

### Auto-save
`NoteScreen` has a `DisposableEffect` that calls `saveNote()` on dispose (navigate-away).
A `LaunchedEffect(strokes)` with a 3-second `delay` provides debounced mid-session saves.

### PDF export
Full bounding box of all stroke points is computed. Scale = `usablePageWidth / contentWidth`.
All strokes are rendered on every page with no pre-filtering — the PDF canvas clips
automatically. This guarantees no strokes are accidentally omitted.

### Database migrations
- v1 → v2: adds `isSynced` column to **both** `notes` AND `folders` tables.
  The migration script must include both ALTER TABLE statements.

## OneDrive Setup (per developer)

The app is pre-registered on Azure (`client_id` placeholder in `res/raw/auth_config_single_account.json`).
To enable sign-in on your machine you need to add your signing certificate hash:

```bash
# Get debug keystore SHA1 hash (base64)
keytool -exportcert -alias androiddebugkey \
  -keystore ~/.android/debug.keystore | openssl sha1 -binary | openssl base64
```

Replace `REPLACE_WITH_BASE64_SHA1_OF_SIGNING_CERT` in:
1. `app/src/main/res/raw/auth_config_single_account.json` → `redirect_uri`
2. `app/src/main/AndroidManifest.xml` → `BrowserTabActivity` intent-filter `android:path`

OneDrive sign-in works without this for builds that don't touch the sign-in button.
MSAL initialises lazily — the placeholder does not crash the app at startup.

## Coding Conventions

- **No comments** unless the WHY is non-obvious (hidden constraint, workaround, subtle invariant).
- **Logging**: Timber only — never `Log.*` or `println`.
- **Error handling**: coroutine failures inside `viewModelScope.launch {}` propagate as
  unhandled exceptions and **crash the app**. Always wrap risky calls in `try/catch` or
  use `runCatching` / `Result`.
- **State**: prefer `MutableStateFlow` in ViewModels; use `SnapshotStateList` (not
  `mutableStateOf<List>`) for collections that are appended to frequently in hot paths.
- **Dependency injection**: add new singletons with `@Singleton @Inject constructor(...)` —
  no `@Provides` needed unless the type comes from a library. Add to `DataModule` only for
  Room/database bindings.
- **Serialization**: `StrokeData` uses `@Serializable` + a custom `OffsetSerializer`;
  keep both in sync if the data class changes.

## Common Pitfalls

- **`rememberUpdatedState` inside a running gesture coroutine** does not update between
  loop iterations (recomposition doesn't run). Read mutable state vars directly.
- **`viewModelScope.launch {}` exceptions crash the app.** Unhandled coroutine exceptions
  in `launch {}` are treated as uncaught — always catch inside the lambda.
- **Room schema validation**: every field in every `@Entity` must be covered by a migration
  or the app crashes on upgrade. Adding `fallbackToDestructiveMigration()` only helps when
  no migration is defined; a partial migration that leaves a mismatch still crashes.
- **WorkManager + Hilt**: workers annotated `@HiltWorker` require the custom
  `HiltWorkerFactory` to be set via `Configuration.Provider`. The default WorkManager
  factory cannot create Hilt workers.
