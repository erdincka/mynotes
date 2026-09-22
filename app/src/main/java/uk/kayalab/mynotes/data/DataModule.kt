package uk.kayalab.mynotes.data

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
    fun provideDatabase(@ApplicationContext context: Context): MyNotesDatabase =
        Room.databaseBuilder(context, MyNotesDatabase::class.java, MyNotesDatabase.DATABASE_NAME)
            .addMigrations(MyNotesDatabase.MIGRATION_1_2, MyNotesDatabase.MIGRATION_2_3, MyNotesDatabase.MIGRATION_3_4, MyNotesDatabase.MIGRATION_4_5)
            .build()

    @Provides
    fun provideNoteDao(database: MyNotesDatabase): NoteDao = database.noteDao()

    @Provides
    fun provideFolderDao(database: MyNotesDatabase): FolderDao = database.folderDao()

    @Provides
    fun provideStrokeDao(database: MyNotesDatabase): StrokeDao = database.strokeDao()
}
