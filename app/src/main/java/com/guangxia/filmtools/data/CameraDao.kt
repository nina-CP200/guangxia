package com.guangxia.filmtools.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CameraDao {
    @Transaction
    @Query("SELECT * FROM cameras ORDER BY createdAt DESC")
    abstract fun observeCameras(): Flow<List<CameraWithRolls>>

    @Insert abstract suspend fun insertCamera(camera: CameraEntity): Long
    @Update abstract suspend fun updateCamera(camera: CameraEntity)
    @Delete abstract suspend fun deleteCamera(camera: CameraEntity)
    @Update abstract suspend fun updateFilm(film: FilmRollEntity)
    @Query("SELECT * FROM film_rolls WHERE cameraId = :cameraId AND unloadedEpochDay IS NULL LIMIT 1")
    abstract suspend fun currentFilm(cameraId: Long): FilmRollEntity?
    @Insert protected abstract suspend fun insertFilmInternal(film: FilmRollEntity): Long

    @Transaction
    open suspend fun loadFilm(film: FilmRollEntity): Long {
        check(currentFilm(film.cameraId) == null) { "这台相机已有在机胶卷" }
        return insertFilmInternal(film)
    }
}
