package com.guangxia.filmtools.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.guangxia.filmtools.camera.RawCameraReading
import com.guangxia.filmtools.core.DistanceUnit
import com.guangxia.filmtools.core.ExposureCalculator
import com.guangxia.filmtools.core.MeterReading
import com.guangxia.filmtools.core.MeteringMode
import com.guangxia.filmtools.data.AppSettings
import com.guangxia.filmtools.data.CameraEntity
import com.guangxia.filmtools.data.CameraRepository
import com.guangxia.filmtools.data.CameraWithRolls
import com.guangxia.filmtools.data.FilmRollEntity
import com.guangxia.filmtools.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val cameraRepository: CameraRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val cameras: StateFlow<List<CameraWithRolls>> = cameraRepository.cameras.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _meterReading = MutableStateFlow<MeterReading?>(null)
    val meterReading = _meterReading.asStateFlow()
    private val samples = ArrayDeque<RawCameraReading>()
    private val _cameraError = MutableStateFlow<String?>(null)
    val cameraError = _cameraError.asStateFlow()
    var exposureLocked = false
        private set

    fun acceptCameraReading(raw: RawCameraReading) {
        // A saved lock state can be restored before a new ViewModel has received its
        // first camera result. Accept that first result, then freeze subsequent values.
        if (!shouldAcceptCameraReading(exposureLocked, _meterReading.value != null)) return
        if (samples.size == 7) samples.removeFirst()
        samples.addLast(raw)
        val ordered = samples.sortedBy { ExposureCalculator.ev100(it.aperture, it.exposureSeconds, it.iso) }
        val middle = ordered[ordered.size / 2]
        val rawEv = ExposureCalculator.ev100(middle.aperture, middle.exposureSeconds, middle.iso)
        _meterReading.value = MeterReading(
            aperture = middle.aperture,
            exposureSeconds = middle.exposureSeconds,
            sensorIso = middle.iso,
            ev100 = rawEv + settings.value.evCalibration,
            status = middle.status,
        )
    }

    fun setCameraError(message: String?) { _cameraError.value = message }
    fun setExposureLocked(value: Boolean) { exposureLocked = value }
    fun clearMeterSamples() { samples.clear() }

    fun setMeteringMode(value: MeteringMode) = viewModelScope.launch {
        clearMeterSamples()
        settingsRepository.setMeteringMode(value)
    }
    fun setCalibration(value: Double) = viewModelScope.launch { settingsRepository.setCalibration(value) }
    fun setDistanceUnit(value: DistanceUnit) = viewModelScope.launch { settingsRepository.setDistanceUnit(value) }

    fun addCamera(name: String, model: String) = viewModelScope.launch { cameraRepository.addCamera(name, model) }
    fun updateCamera(camera: CameraEntity) = viewModelScope.launch { cameraRepository.updateCamera(camera) }
    fun deleteCamera(camera: CameraEntity) = viewModelScope.launch { cameraRepository.deleteCamera(camera) }
    fun loadFilm(film: FilmRollEntity, onError: (String) -> Unit = {}) = viewModelScope.launch {
        runCatching { cameraRepository.loadFilm(film) }.onFailure { onError(it.message ?: "装卷失败") }
    }
    fun updateFilm(film: FilmRollEntity) = viewModelScope.launch { cameraRepository.updateFilm(film) }
    fun unloadFilm(film: FilmRollEntity, epochDay: Long) = viewModelScope.launch { cameraRepository.unloadFilm(film, epochDay) }
}

internal fun shouldAcceptCameraReading(exposureLocked: Boolean, hasReading: Boolean): Boolean =
    !exposureLocked || !hasReading

class MainViewModelFactory(
    private val cameraRepository: CameraRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(cameraRepository, settingsRepository) as T
}
