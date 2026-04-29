package uk.kayalab.mynotes.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Note::class, Folder::class],
    version = 3,
    exportSchema = false
)
abstract class MyNotesDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao

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
    }
}
