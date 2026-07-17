package com.guangxia.filmtools

import android.app.Application
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.room.Room
import com.guangxia.filmtools.data.AppDatabase
import com.guangxia.filmtools.data.CameraRepository
import com.guangxia.filmtools.data.SettingsRepository

class GuangXiaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The meter is the launch destination, so begin CameraX discovery while Compose draws.
        runCatching { ProcessCameraProvider.getInstance(this) }
    }

    val database by lazy { Room.databaseBuilder(this, AppDatabase::class.java, "guangxia.db").build() }
    val cameraRepository by lazy { CameraRepository(database.cameraDao()) }
    val settingsRepository by lazy { SettingsRepository(this) }
}
