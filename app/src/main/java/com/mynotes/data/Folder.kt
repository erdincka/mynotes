package com.mynotes.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Represents a folder in the hierarchical structure.
 * A folder can contain notes and other sub-folders.
 */
@Serializable
@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    
    // null indicates this is a root folder
    val parentId: Long?,
    
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    
    // Local-only metadata for UI ordering/sync status
    val sortOrder: Int = 0,
    val isSynced: Boolean = false
)