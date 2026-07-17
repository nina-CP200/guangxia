package com.guangxia.filmtools.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CameraEntity::class, FilmRollEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cameraDao(): CameraDao
}
