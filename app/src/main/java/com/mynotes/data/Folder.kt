package com.mynotes.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val parentId: Long?, // null = root folder
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
)
