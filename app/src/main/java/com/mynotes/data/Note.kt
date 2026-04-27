package com.mynotes.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "notes")
@Serializable
data class Note(
     @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val content: String = "", // JSON serialized strokes/metadata
    val folderId: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val category: String = "default"
)