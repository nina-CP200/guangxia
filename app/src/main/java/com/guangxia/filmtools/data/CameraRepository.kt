package com.guangxia.filmtools.data

import kotlinx.coroutines.flow.Flow

class CameraRepository(private val dao: CameraDao) {
    val cameras: Flow<List<CameraWithRolls>> = dao.observeCameras()
    suspend fun addCamera(name: String, model: String) = dao.insertCamera(CameraEntity(name = name.trim(), model = model.trim()))
    suspend fun updateCamera(camera: CameraEntity) = dao.updateCamera(camera)
    suspend fun deleteCamera(camera: CameraEntity) = dao.deleteCamera(camera)
    suspend fun loadFilm(film: FilmRollEntity) = dao.loadFilm(film)
    suspend fun updateFilm(film: FilmRollEntity) = dao.updateFilm(film)
    suspend fun unloadFilm(film: FilmRollEntity, epochDay: Long) = dao.updateFilm(film.copy(unloadedEpochDay = epochDay))
}
