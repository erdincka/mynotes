# MyNotes App Development Plan

## 1. Tech Stack
- **Framework**: Pure Android (Kotlin + Jetpack Compose)
- **Validation**: Kotlinx Serialization + javax.validation constraints for metadata
- **Logging**: Timber (structured logging with tags) + Chucker for network debugging
- **Deployment**: GitHub Actions (CI/CD) with Play Store signing via Secrets
- **Dependency Management**: Version Catalog (`gradle/libs.versions.toml`)
- **Database**: Room (SQLite) with Flow for reactive updates
- **Network**: Ktor client for OneDrive API integration
- **Image/PDF**: Android Graphics PDFRenderer + Android Canvas for PNG export
- **File Storage**: Storage Access Framework (SAF) + OneDrive REST API

## 1.2 Project Structure
```
mynotes/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/ (Kotlin)
│   │   │   └── res/
│   │   └── test/
│   └── build.gradle.kts
├── data/
│   ├── dao/
│   ├── db/
│   └── entities/
├── sync/
│   └── onedrive/
└── utils/
    └── canvas/
```

## 2. Key Features Implementation

### 2.1 Infinite Canvas
```kotlin
// CanvasView.kt
@Composable
fun CanvasView(
    note: Note,
    onDrawAction: (Path, Color) -> Unit,
    onUndo: () -> Unit
) {
    val density = LocalDensity.current
    val strokeWidth = 5f * density.scaleFactor

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInteraction(onPointerMove = { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Start drawing
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // Continue drawing
                    }
                }
            })
    ) {
        drawPath(note.content, strokeWidth)
    }
}
```

### 2.2 Folder Hierarchy
```kotlin
// Folder.kt
@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey val id: Long,
    val name: String,
    val parentId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)
```

### 2.3 OneDrive Sync
```kotlin
// OneDriveSync.kt
class OneDriveSync(private val client: KtorClient) {
    suspend fun uploadNote(note: Note) {
        val file = File(note.id.toString() + ".txt")
        client.post("https://api.onedrive.com/v1.0/me/drive/items") {
            body = file.readBytes()
            headers("Authorization") = "Bearer $token"
        }
    }
}
```

## 3. Development Standards

### 3.1 Code Quality
- **Linting**: ktlint for Kotlin, detekt for static analysis
- **Type Checking**: mypy for Python CI scripts
- **Logging**: Timber with structured logging

### 3.2 Error Handling
```kotlin
// Example error handling
try {
    // Database operation
} catch (e: SQLiteException) {
    Timber.e("Database error: ${e.message}")
    showErrorSnackbar("Failed to save note")
}
```

## 4. Implementation Roadmap

### Phase 1: Core Functionality
1. Implement Room database with Notes and Folders entities
2. Create infinite canvas with basic drawing capabilities
3. Build folder tree UI with expand/collapse functionality

### Phase 2: Advanced Features
1. Add stylus support with pressure sensitivity
2. Implement text input with formatting options
3. Add export functionality (PDF/PNG)

### Phase 3: Sync & Collaboration
1. Integrate OneDrive API with Ktor
2. Implement conflict resolution using vector clocks
3. Add background sync service with WorkManager

## 5. Testing Strategy

### Unit Tests
```kotlin
// NoteDaoTest.kt
@RunWith(JUnit4::class)
class NoteDaoTest {
    @Test
    fun testNoteCreation() {
        val note = Note(name = "Test", content = "Sample text")
        val result = noteDao.insert(note)
        assertEquals(1, result)
    }
}
```

### Integration Tests
```kotlin
// SyncTest.kt
@RunWith(AndroidJUnit4::class)
class SyncTest {
    @Test
    fun testOneDriveUpload() {
        val note = Note(name = "Test", content = "Sample text")
        val result = syncService.uploadNote(note)
        assertTrue(result.isSuccessful)
    }
}
```

## 6. Best Practices

### 6.1 Performance
- Use object pooling for drawing primitives
- Implement lazy loading for large folder hierarchies
- Use Compose's `remember` for state management

### 6.2 Security
- Encrypt sensitive note content using Android Keystore
- Implement OAuth 2.0 for OneDrive authentication
- Use HTTPS for all network communications

## 7. Future Enhancements
1. Add handwriting recognition (ML Kit)
2. Implement version history for notes
3. Add collaboration features with real-time sync
4. Integrate with Google Keep for cross-platform support

## 8. Compliance
- Follow Android Design Guidelines
- Implement accessibility features (TalkBack, screen readers)
- Comply with GDPR for user data handling