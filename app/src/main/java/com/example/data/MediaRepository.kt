package com.example.data

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class MediaRepository(private val mediaDao: MediaDao) {

    val allMedia: Flow<List<MediaItem>> = mediaDao.getAllMedia()
    val trashMedia: Flow<List<MediaItem>> = mediaDao.getTrashMedia()
    val favoriteMedia: Flow<List<MediaItem>> = mediaDao.getFavoriteMedia()

    suspend fun getMediaById(id: Int): MediaItem? = mediaDao.getMediaById(id)

    suspend fun saveMedia(item: MediaItem) = withContext(Dispatchers.IO) {
        mediaDao.insertMedia(item)
    }

    suspend fun updateMedia(item: MediaItem) = withContext(Dispatchers.IO) {
        mediaDao.updateMedia(item)
    }

    // Move to trash (30 days retention policy)
    suspend fun moveToTrash(id: Int) = withContext(Dispatchers.IO) {
        val item = mediaDao.getMediaById(id)
        if (item != null) {
            val updated = item.copy(
                isInTrash = true,
                deletedTimestamp = System.currentTimeMillis()
            )
            mediaDao.insertMedia(updated)
            Log.d("MediaRepository", "Moved media ID $id to Bin")
        }
    }

    // Recover from trash
    suspend fun restoreFromTrash(id: Int) = withContext(Dispatchers.IO) {
        val item = mediaDao.getMediaById(id)
        if (item != null) {
            val updated = item.copy(
                isInTrash = false,
                deletedTimestamp = 0L
            )
            mediaDao.insertMedia(updated)
            Log.d("MediaRepository", "Restored media ID $id from Bin")
        }
    }

    suspend fun deletePermanently(id: Int) = withContext(Dispatchers.IO) {
        mediaDao.deleteMediaById(id)
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        mediaDao.emptyTrash()
    }

    suspend fun pruneTrash() = withContext(Dispatchers.IO) {
        mediaDao.pruneOldTrash(System.currentTimeMillis())
    }

    // Prepopulate database with gorgeous default photos/videos
    suspend fun prepopulateIfEmpty() = withContext(Dispatchers.IO) {
        val current = mediaDao.getAllMedia().firstOrNull() ?: emptyList()
        val trash = mediaDao.getTrashMedia().firstOrNull() ?: emptyList()
        if (current.isEmpty() && trash.isEmpty()) {
            Log.d("MediaRepository", "Prepopulating smart gallery database...")
            val defaults = listOf(
                MediaItem(
                    filePath = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=800&auto=format&fit=crop",
                    title = "Apus peste Munți",
                    location = "Brașov",
                    objects = "munte, zăpadă, copaci, natură, peisaj",
                    year = 2026,
                    category = "Natură",
                    type = "IMAGE"
                ),
                MediaItem(
                    filePath = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=800&auto=format&fit=crop",
                    title = "Portret de Toamnă",
                    location = "București",
                    objects = "persoană, zâmbet, portret, om",
                    faces = "George",
                    year = 2026,
                    category = "Oameni",
                    type = "IMAGE"
                ),
                MediaItem(
                    filePath = "https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=800&auto=format&fit=crop",
                    title = "Pisică Curioasă",
                    location = "Cluj",
                    objects = "animal, pisică, drăguț",
                    faces = "Mimi",
                    year = 2025,
                    category = "Animale",
                    type = "IMAGE",
                    isFavorite = true
                ),
                MediaItem(
                    filePath = "https://images.unsplash.com/photo-1502602898657-3e91760cbb34?w=800&auto=format&fit=crop",
                    title = "Turnul Eiffel Noaptea",
                    location = "Paris",
                    objects = "turn, oraș, lumini, arhitectură, călătorie",
                    year = 2024,
                    category = "Călătorii",
                    type = "IMAGE"
                ),
                MediaItem(
                    filePath = "https://images.unsplash.com/photo-1513104890138-7c749659a591?w=800&auto=format&fit=crop",
                    title = "Pizza Italiană",
                    location = "Rome",
                    objects = "mâncare, pizza, delicios, bucătărie",
                    year = 2025,
                    category = "Mâncare",
                    type = "IMAGE"
                ),
                MediaItem(
                    filePath = "https://images.unsplash.com/photo-1490750967868-88aa4486c946?w=800&auto=format&fit=crop",
                    title = "Flori de Câmp",
                    location = "Sibiu",
                    objects = "flori, natură, lalele, primăvară",
                    year = 2026,
                    category = "Natură",
                    type = "IMAGE"
                ),
                MediaItem(
                    filePath = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=800&auto=format&fit=crop",
                    title = "Zâmbet Strălucitor",
                    location = "București",
                    objects = "portret, persoană, femeie",
                    faces = "Andreea",
                    year = 2026,
                    category = "Oameni",
                    type = "IMAGE"
                ),
                MediaItem(
                    filePath = "https://images.unsplash.com/photo-1503376780353-7e6692767b70?w=800&auto=format&fit=crop",
                    title = "Supercar în Alpi",
                    location = "Chamonix",
                    objects = "mașină, drum, munte, mașină sport",
                    year = 2025,
                    category = "Mașini",
                    type = "IMAGE"
                ),
                MediaItem(
                    filePath = "https://assets.mixkit.co/videos/preview/mixkit-forest-stream-in-the-sunlight-529-large.mp4",
                    title = "Râu de Munte",
                    location = "Brașov",
                    objects = "apă, video, pădure, munte, natură",
                    year = 2026,
                    category = "Natură",
                    type = "VIDEO",
                    duration = "0:15"
                ),
                MediaItem(
                    filePath = "https://assets.mixkit.co/videos/preview/mixkit-waves-breaking-in-the-ocean-1527-large.mp4",
                    title = "Valuri Oceanice",
                    location = "Constanța",
                    objects = "mare, valuri, video, vacanță, peisaj",
                    year = 2026,
                    category = "Vacanță",
                    type = "VIDEO",
                    duration = "0:10",
                    isFavorite = true
                ),
                MediaItem(
                    filePath = "https://assets.mixkit.co/videos/preview/mixkit-curvy-road-lined-with-autumn-trees-4366-large.mp4",
                    title = "Drum de Toamnă",
                    location = "Sibiu",
                    objects = "drum, copaci, toamnă, video, călătorie",
                    year = 2025,
                    category = "Călătorii",
                    type = "VIDEO",
                    duration = "0:12"
                )
            )
            for (item in defaults) {
                mediaDao.insertMedia(item)
            }
            Log.d("MediaRepository", "Database pre-populated with success")
        }
    }
}
