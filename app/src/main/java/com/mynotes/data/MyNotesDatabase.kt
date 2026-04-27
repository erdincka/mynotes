package com.mynotes.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Note::class, Folder::class],
    version = 1,
    exportSchema = false
)
abstract class MyNotesDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao

    companion object {
         const val DATABASE_NAME = "mynotes_db"
     }
}