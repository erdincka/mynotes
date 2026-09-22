package uk.kayalab.mynotes.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MyNotesDatabase::class.java)

    @Test
    fun migrate3To4MovesInkIntoStrokeRowsAndDropsContent() {
        helper.createDatabase(dbName, 3).use { db ->
            db.insert("folders", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("id", 1L); put("name", "Work"); putNull("parentId"); put("createdAt", 1L); put("updatedAt", 1L); put("sortOrder", 0)
            })
            db.insert("notes", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("id", 10L); put("name", "Lecture"); put("folderId", 1L); put("createdAt", 5L); put("updatedAt", 6L); put("category", "default")
                put("content", """[{"id":100,"points":["0.0,0.0","10.0,5.0"],"pressures":[0.5,0.8],"color":"#FF000000","strokeWidth":4.0,"tool":"pen"},""" +
                    """{"id":101,"points":["3.0,4.0"],"tool":"text","text":"Hi","fontSize":40.0,"fontFamily":"Serif"}]""")
            })
            db.insert("notes", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("id", 11L); put("name", "Empty"); put("folderId", 0L); put("createdAt", 7L); put("updatedAt", 8L); put("category", "default"); put("content", "")
            })
        }

        val db = helper.runMigrationsAndValidate(dbName, 4, true, MyNotesDatabase.MIGRATION_3_4)

        db.query("SELECT COUNT(*) FROM strokes").use { it.moveToFirst(); assertEquals(2, it.getInt(0)) }
        db.query("SELECT id, ordinal, tool, text, points FROM strokes WHERE noteId = 10 ORDER BY ordinal").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(100L, c.getLong(0)); assertEquals(0, c.getInt(1)); assertEquals("pen", c.getString(2))
            assertEquals(listOf(0f, 0f, 10f, 5f), StrokePacking.unpackFloats(c.getBlob(4)))
            assertTrue(c.moveToNext())
            assertEquals(101L, c.getLong(0)); assertEquals("text", c.getString(2)); assertEquals("Hi", c.getString(3))
        }
        db.query("SELECT name, folderId FROM notes ORDER BY id").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("Lecture", c.getString(0)); assertEquals(1L, c.getLong(1))
            assertTrue(c.moveToNext()); assertEquals("Empty", c.getString(0))
        }
        db.query("PRAGMA table_info(notes)").use { c ->
            val columns = generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList()
            assertTrue("content" !in columns)
            assertTrue("thumbnail" in columns)
        }
        // The helper opens the raw database; Room itself turns foreign keys on when it opens.
        db.execSQL("PRAGMA foreign_keys=ON")
        db.execSQL("DELETE FROM notes WHERE id = 10")
        db.query("SELECT COUNT(*) FROM strokes").use { it.moveToFirst(); assertEquals("strokes cascade on note delete", 0, it.getInt(0)) }
        db.close()

        val db5 = helper.runMigrationsAndValidate(dbName, 5, true, MyNotesDatabase.MIGRATION_4_5)
        db5.query("SELECT template FROM notes WHERE id = 11").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("grid", c.getString(0))
        }
        db5.close()
    }
}
