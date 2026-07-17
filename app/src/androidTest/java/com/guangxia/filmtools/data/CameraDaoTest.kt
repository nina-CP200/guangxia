package com.guangxia.filmtools.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class CameraDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: CameraDao

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        dao = database.cameraDao()
    }

    @After fun close() = database.close()

    @Test fun cameraFilmLifecycleAndCascadeDelete() = runBlocking {
        val cameraId = dao.insertCamera(CameraEntity(name = "M6", model = "Leica"))
        val filmId = dao.loadFilm(FilmRollEntity(cameraId = cameraId, filmType = "Gold 200", iso = 200, loadedEpochDay = LocalDate.now().toEpochDay()))
        val loaded = dao.currentFilm(cameraId)!!
        assertEquals(filmId, loaded.id)
        dao.updateFilm(loaded.copy(unloadedEpochDay = LocalDate.now().toEpochDay()))
        assertNull(dao.currentFilm(cameraId))
        val camera = dao.observeCameras().first().single()
        assertEquals(1, camera.history.size)
        dao.deleteCamera(camera.camera)
        assertEquals(0, dao.observeCameras().first().size)
    }

    @Test(expected = IllegalStateException::class)
    fun onlyOneCurrentFilmPerCamera() = runBlocking {
        val cameraId = dao.insertCamera(CameraEntity(name = "F3"))
        dao.loadFilm(FilmRollEntity(cameraId = cameraId, filmType = "HP5", iso = 400, loadedEpochDay = 1))
        dao.loadFilm(FilmRollEntity(cameraId = cameraId, filmType = "Portra", iso = 400, loadedEpochDay = 2))
    }
}
