package com.bpm.detector.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bpm.detector.engine.AudioEngine
import com.bpm.detector.engine.BpmCalculator
import com.bpm.detector.engine.BpmFormatter
import com.bpm.detector.engine.TapState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DetectionMode { TAP, MICROPHONE }

data class MicUiState(
    val bpm: Double = 0.0,
    val formattedBpm: String = "-- BPM",
    val tempoMarking: String = "",
    val bpmMs: Double = 0.0,
    val isRecording: Boolean = false,
    val permissionGranted: Boolean = false,
)

data class TapUiState(
    val bpm: Double = 0.0,
    val formattedBpm: String = "-- BPM",
    val tempoMarking: String = "",
    val bpmMs: Double = 0.0,
    val tapCount: Int = 0,
)

class BpmViewModel(application: Application) : AndroidViewModel(application) {

    private val audioEngine = AudioEngine(viewModelScope)
    private val tapState = TapState()

    private val _selectedMode = MutableStateFlow(DetectionMode.TAP)
    val selectedMode: StateFlow<DetectionMode> = _selectedMode

    private val _micUiState = MutableStateFlow(MicUiState())
    val micUiState: StateFlow<MicUiState> = _micUiState

    private val _tapUiState = MutableStateFlow(TapUiState())
    val tapUiState: StateFlow<TapUiState> = _tapUiState

    init {
        viewModelScope.launch {
            audioEngine.bpmFlow.collect { bpm ->
                _micUiState.update {
                    it.copy(
                        bpm = bpm,
                        formattedBpm = BpmFormatter.formatBpm(bpm),
                        tempoMarking = BpmFormatter.getTempoMarking(bpm),
                        bpmMs = BpmCalculator.bpmToMs(bpm),
                    )
                }
            }
        }
        viewModelScope.launch {
            audioEngine.isRecording.collect { recording ->
                _micUiState.update { it.copy(isRecording = recording) }
            }
        }
    }

    fun selectMode(mode: DetectionMode) {
        if (mode == _selectedMode.value) return
        if (_selectedMode.value == DetectionMode.MICROPHONE) {
            audioEngine.stop()
        }
        _selectedMode.value = mode
    }

    // ── Tap mode ────────────────────────────────────────────────────────────

    fun onTap() {
        tapState.recordTap(SystemClock.elapsedRealtime())
        val bpm = tapState.currentBpm()
        _tapUiState.update {
            TapUiState(
                bpm = bpm,
                formattedBpm = BpmFormatter.formatBpm(bpm),
                tempoMarking = BpmFormatter.getTempoMarking(bpm),
                bpmMs = BpmCalculator.bpmToMs(bpm),
                tapCount = tapState.tapCount,
            )
        }
    }

    fun resetTaps() {
        tapState.reset()
        _tapUiState.value = TapUiState()
    }

    // ── Mic mode ─────────────────────────────────────────────────────────────

    fun onMicPermissionResult(granted: Boolean) {
        _micUiState.update { it.copy(permissionGranted = granted) }
        if (granted) audioEngine.start()
    }

    fun startMic() {
        if (_micUiState.value.permissionGranted) audioEngine.start()
    }

    fun stopMic() {
        audioEngine.stop()
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.stop()
    }
}
