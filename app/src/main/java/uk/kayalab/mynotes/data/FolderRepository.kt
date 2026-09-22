package uk.kayalab.mynotes.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepository @Inject constructor(
    private val database: MyNotesDatabase,
    private val folderDao: FolderDao,
    private val noteDao: NoteDao
) {
    val allFolders: Flow<List<Folder>> = folderDao.getAllFolders()

    suspend fun create(name: String, parentId: Long?): Long =
        folderDao.insert(Folder(name = name, parentId = parentId))

    suspend fun rename(id: Long, name: String) =
        folderDao.rename(id, name, System.currentTimeMillis())

    suspend fun moveTo(id: Long, parentId: Long?) =
        folderDao.moveTo(id, parentId, System.currentTimeMillis())

    /** Deletes the folder, every folder below it and every note inside any of them. */
    suspend fun deleteTree(folderId: Long, allFolders: List<Folder>) {
        val ids = FolderTree.subtreeIds(allFolders, folderId).toList()
        database.withTransaction {
            noteDao.deleteByFolderIds(ids)
            folderDao.deleteByIds(ids)
        }
    }
}
