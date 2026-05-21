package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items WHERE isInTrash = 0 ORDER BY dateAdded DESC")
    fun getAllMedia(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE isInTrash = 1 ORDER BY deletedTimestamp DESC")
    fun getTrashMedia(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE isInTrash = 0 AND isFavorite = 1 ORDER BY dateAdded DESC")
    fun getFavoriteMedia(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    suspend fun getMediaById(id: Int): MediaItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(mediaItem: MediaItem)

    @Update
    suspend fun updateMedia(mediaItem: MediaItem)

    @Delete
    suspend fun deleteMedia(mediaItem: MediaItem)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteMediaById(id: Int)

    @Query("DELETE FROM media_items WHERE isInTrash = 1")
    suspend fun emptyTrash()

    @Query("DELETE FROM media_items WHERE isInTrash = 1 AND :currentTimestamp - deletedTimestamp > 2592000000") // 30 days in ms = 30 * 24 * 60 * 60 * 1000 = 2,592,000,000
    suspend fun pruneOldTrash(currentTimestamp: Long)
}
