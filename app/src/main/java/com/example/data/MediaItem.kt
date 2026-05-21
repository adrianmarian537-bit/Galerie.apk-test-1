package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_items")
data class MediaItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String,
    val title: String,
    val dateAdded: Long = System.currentTimeMillis(),
    val dateModified: Long = System.currentTimeMillis(),
    val location: String = "",
    val faces: String = "",       // Comma separated, e.g., "Alice, Andrei"
    val objects: String = "",     // Comma separated, e.g., "Floare, Natură, Mașină"
    val year: Int = 2026,
    val category: String = "",    // Custom album or category
    val type: String = "IMAGE",   // "IMAGE" or "VIDEO"
    val duration: String = "",    // E.g., "0:42" if video
    val isFavorite: Boolean = false,
    val isInTrash: Boolean = false,
    val deletedTimestamp: Long = 0L // Timestamp of deletion (used to manage 30 days rule)
) {
    val remainingDays: Int
        get() {
            if (!isInTrash) return 30
            val elapsedMs = System.currentTimeMillis() - deletedTimestamp
            val elapsedDays = (elapsedMs / (1000 * 60 * 60 * 24)).toInt()
            val left = 30 - elapsedDays
            return if (left < 0) 0 else left
        }
}
