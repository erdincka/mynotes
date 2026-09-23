package uk.kayalab.mynotes.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import uk.kayalab.mynotes.ui.canvas.StrokeCodec

@Database(
    entities = [Note::class, Folder::class, StrokeEntity::class],
    version = 6,
    exportSchema = true
)
abstract class MyNotesDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun strokeDao(): StrokeDao

    companion object {
        const val DATABASE_NAME = "mynotes_db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE folders ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Removes isSynced from both tables (SQLite pre-3.35 requires table recreation).
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE notes_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        content TEXT NOT NULL,
                        folderId INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        category TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO notes_new SELECT id, name, content, folderId, createdAt, updatedAt, category FROM notes")
                db.execSQL("DROP TABLE notes")
                db.execSQL("ALTER TABLE notes_new RENAME TO notes")

                db.execSQL("""
                    CREATE TABLE folders_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        parentId INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO folders_new SELECT id, name, parentId, createdAt, updatedAt, sortOrder FROM folders")
                db.execSQL("DROP TABLE folders")
                db.execSQL("ALTER TABLE folders_new RENAME TO folders")
            }
        }

        /**
         * Moves ink out of notes.content into one row per stroke, drops the unused category
         * column and adds a thumbnail. The JSON is read before notes is recreated, and the
         * strokes table is created afterwards so no foreign-key cascade can fire mid-way.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val inkByNote = ArrayList<Pair<Long, String>>()
                db.query("SELECT id, content FROM notes").use { cursor ->
                    while (cursor.moveToNext()) inkByNote.add(cursor.getLong(0) to cursor.getString(1))
                }

                db.execSQL("""
                    CREATE TABLE notes_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        folderId INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        thumbnail BLOB
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO notes_new (id, name, folderId, createdAt, updatedAt) SELECT id, name, folderId, createdAt, updatedAt FROM notes")
                db.execSQL("DROP TABLE notes")
                db.execSQL("ALTER TABLE notes_new RENAME TO notes")

                db.execSQL(STROKES_CREATE_SQL)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_strokes_noteId` ON `strokes` (`noteId`)")

                for ((noteId, content) in inkByNote) {
                    val strokes = StrokeCodec.decode(content).getOrElse { emptyList() }
                    strokes.forEachIndexed { index, stroke ->
                        val entity = StrokeEntity.from(stroke, noteId, index)
                        val values = ContentValues().apply {
                            put("id", entity.id)
                            put("noteId", entity.noteId)
                            put("ordinal", entity.ordinal)
                            put("tool", entity.tool)
                            put("color", entity.color)
                            put("strokeWidth", entity.strokeWidth)
                            put("points", entity.points)
                            put("pressures", entity.pressures)
                            put("text", entity.text)
                            put("fontSize", entity.fontSize)
                            put("fontFamily", entity.fontFamily)
                        }
                        db.insert("strokes", SQLiteDatabase.CONFLICT_REPLACE, values)
                    }
                }
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN template TEXT NOT NULL DEFAULT 'grid'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN recognizedText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE strokes ADD COLUMN imageName TEXT")
                db.execSQL("ALTER TABLE strokes ADD COLUMN imageWidth REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE strokes ADD COLUMN imageHeight REAL NOT NULL DEFAULT 0")
            }
        }

        // Must match the schema Room generates for StrokeEntity (see app/schemas/…/4.json).
        private const val STROKES_CREATE_SQL =
            "CREATE TABLE IF NOT EXISTS `strokes` (`id` INTEGER NOT NULL, `noteId` INTEGER NOT NULL, " +
                "`ordinal` INTEGER NOT NULL, `tool` TEXT NOT NULL, `color` TEXT NOT NULL, `strokeWidth` REAL NOT NULL, " +
                "`points` BLOB NOT NULL, `pressures` BLOB NOT NULL, `text` TEXT, `fontSize` REAL NOT NULL, " +
                "`fontFamily` TEXT NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
    }
}
