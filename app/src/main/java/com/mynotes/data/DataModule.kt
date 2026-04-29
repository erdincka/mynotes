package com.mynotes.data

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MyNotesDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            MyNotesDatabase::class.java,
            MyNotesDatabase.DATABASE_NAME
        )
        .addMigrations(MyNotesDatabase.MIGRATION_1_2, MyNotesDatabase.MIGRATION_2_3)
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun provideNoteDao(database: MyNotesDatabase): NoteDao {
        return database.noteDao()
    }

    @Provides
    @Singleton
    fun provideFolderDao(database: MyNotesDatabase): FolderDao {
        return database.folderDao()
    }

    @Provides
    @Singleton
    fun provideNoteRepository(noteDao: NoteDao): NoteRepository {
        return NoteRepository(noteDao)
    }

    @Provides
    @Singleton
    fun provideFolderRepository(folderDao: FolderDao): FolderRepository {
        return FolderRepository(folderDao)
    }
}