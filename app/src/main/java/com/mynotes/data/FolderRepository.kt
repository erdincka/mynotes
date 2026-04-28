package com.mynotes.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FolderRepository @Inject constructor(
    private val folderDao: FolderDao
) {
    val allFolders: Flow<List<Folder>> = folderDao.getAllFolders()

    fun getFoldersByParent(parentId: Long?): Flow<List<Folder>> = folderDao.getFoldersByParent(parentId)

    suspend fun getFolderById(id: Long): Folder? = withContext(Dispatchers.IO) {
        folderDao.getFolderById(id)
    }

    suspend fun insert(folder: Folder): Long = withContext(Dispatchers.IO) {
        folderDao.insert(folder)
    }

    suspend fun update(folder: Folder) = withContext(Dispatchers.IO) {
        folderDao.update(folder)
    }

    suspend fun delete(folder: Folder) = withContext(Dispatchers.IO) {
        folderDao.delete(folder)
    }

    suspend fun markAsSynced(id: Long) = withContext(Dispatchers.IO) {
        folderDao.markAsSynced(id)
    }

    suspend fun markAsUnsynced(id: Long) = withContext(Dispatchers.IO) {
        folderDao.markAsUnsynced(id)
    }
}
