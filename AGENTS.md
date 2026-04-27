# MyNotes app - Development Standards

This is a personal note taking app designed for Android with stylus for handwriting and drawing.
As the expert, you should challenge assumptions and ensure alignment with industry best practices.

## Core Philosophy

We rely on your expertise to make reasonable and responsible decisions.
When you question our approach, do so directly with specific alternatives.
We want to stay current with stable standards while being flexible to new approaches.

## Tech Stack

- **Framework**: Pure Android (Kotlin + Jetpack Compose)
- **Validation**: Kotlinx Serialization + javax.validation constraints for metadata
- **Logging**: Timber (structured logging with tags) + Chucker for network debugging
- **Deployment**: GitHub Actions (CI/CD) with Play Store signing via Secrets
- **Dependency Management**: Version Catalog (`gradle/libs.versions.toml`)
- **Database**: Room (SQLite) with Flow for reactive updates
- **Network**: Ktor client for OneDrive API integration
- **Image/PDF**: Android Graphics PDFRenderer + Android Canvas for PNG export
- **File Storage**: Storage Access Framework (SAF) + OneDrive REST API

## Development Standards

### Code Quality

**Linting & Type Checking** (must run before commit):

- `ktlint` for Kotlin code style (baseline for legacy code)
- `detekt` for static analysis and complexity checks
- Android Lint with strict configuration
- `mypy` for any Python tooling (CI scripts)
- Pre-commit hooks via `git-hooks` or `lefthook`

### Dependency Management

- Version Catalog (`gradle/libs.versions.toml`) for centralized dependency versions
- Strict version pinning for production, flexible for dev (`1.+` only in libs.versions)
- Avoid dynamic versions (`+`) in production builds
- Prefer stable releases over alpha/beta for core dependencies

### Logging

- Use `Timber` exclusively - never `android.util.Log` or `print()`
- Tag convention: `ClassName.methodName` (max 23 chars for Android)
- Structured logging: include correlation IDs for async operations
- Separate logging trees for debug (verbose) vs release (warnings/errors only)
- Network requests logged via Chucker in debug builds only

### Error Handling

- Gracefully handle errors and provide concise and informative feedback to user
- Use sealed class hierarchies: `Result<Success, Error>` pattern
- Never crash on user input validation errors
- Recoverable errors: show Snackbar with action; Non-recoverable: show dialog with "Report" option
- Log all non-validation errors with full stack trace (debug builds)
- Wrap platform exceptions in domain-specific error types

### Health Checks

- Database integrity check on app startup (Room validation)
- OneDrive connectivity test before sync operations
- Storage space check before PDF/PNG export
- Background service health monitoring for auto-sync
- Periodic (daily) database vacuum and integrity verification

## Critical Feature Implementation Notes

### Stylus & Input Handling
- Use `MotionEvent` with `getToolType()`, `getButtonState()`, `getPressure()`
- Support `Tool.TYPE_STYLUS`, `Tool.TYPE_ERASER`, `Tool.TYPE_MOUSE`
- Implement `onGenericMotionEvent` for hover events (cursor preview)
- Pointer icon customization via `PointerIcon.setSystemIcon()`
- Palm rejection via `event.isFromSource(InputDevice.SOURCE_STYLUS)`

### Infinite Canvas Architecture
- Use `Canvas` with viewport transformation (pan/zoom matrix)
- Implement spatial indexing (R-tree or quad-tree) for hit detection
- Chunk rendering for large canvases (visible region + buffer)
- Object pooling for drawing primitives to avoid GC pressure

### Sync & Conflict Resolution
- OneDrive sync via Microsoft Graph API (REST)
- Last-write-wins with vector clocks for conflict detection
- Offline-first: all changes local, sync in background
- Delta sync: only upload changed regions/files

## Context7 Integration

Use Context7 MCP for library documentation when:
- Adding new dependencies
- Configuring frameworks
- API syntax questions

```python
# Example: resolve and query
mcp-server-context7_resolve-library-id(libraryName="fastapi", query="dependency injection")
```

---

**Reminder**: When in doubt, ask before implementing. We value your expertise in identifying gaps and recommending improvements.
