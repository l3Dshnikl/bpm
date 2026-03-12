package com.bpm.detector.engine

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Manages microphone recording and energy-based beat onset detection.
 *
 * Algorithm (pure Kotlin, no external libraries):
 * 1. Record 16-bit PCM mono at 44100 Hz via AudioRecord.
 * 2. Process overlapping frames (frame=1024 samples, hop=512 samples, ~23 ms each).
 * 3. Compute RMS energy per frame.
 * 4. Maintain a ~1-second sliding window of RMS history (43 frames).
 * 5. Detect an onset when RMS > mean(history) × 1.5 AND ≥300 ms since last onset.
 * 6. Track the last 8 onset timestamps; emit BPM via StateFlow on each new onset.
 */
class AudioEngine(private val scope: CoroutineScope) {

    companion object {
        private const val SAMPLE_RATE = 44_100
        private const val HOP_SIZE = 512
        private const val HISTORY_FRAMES = 43       // ~1 second of RMS history
        private const val ONSET_THRESHOLD = 1.5f    // multiplier over mean energy
        private const val REFRACTORY_MS = 300L      // min ms between onsets
        private const val ONSET_BUFFER_SIZE = 8     // onsets kept for BPM averaging
    }

    private val _bpmFlow = MutableStateFlow(0.0)
    val bpmFlow: StateFlow<Double> = _bpmFlow

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private var audioRecord: AudioRecord? = null
    private var processingJob: Job? = null

    fun start() {
        if (_isRecording.value) return

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBuffer, HOP_SIZE * 4)

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return
        }

        audioRecord = record
        record.startRecording()
        _isRecording.value = true

        processingJob = scope.launch(Dispatchers.Default) {
            processAudio(record)
        }
    }

    private suspend fun processAudio(record: AudioRecord) {
        val pcmBuffer = ShortArray(HOP_SIZE)
        val rmsHistory = ArrayDeque<Float>()
        val onsetTimestamps = ArrayDeque<Long>()
        var lastOnsetMs = 0L

        while (isActive && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val read = record.read(pcmBuffer, 0, HOP_SIZE)
            if (read <= 0) continue

            // Compute RMS energy for this hop
            val sumSq = pcmBuffer.take(read).sumOf { it.toLong() * it.toLong() }
            val rms = sqrt(sumSq.toDouble() / read).toFloat()

            rmsHistory.addLast(rms)
            if (rmsHistory.size > HISTORY_FRAMES) rmsHistory.removeFirst()

            if (rmsHistory.size < HISTORY_FRAMES) continue

            val meanEnergy = rmsHistory.average().toFloat()
            val now = SystemClock.elapsedRealtime()

            if (rms > meanEnergy * ONSET_THRESHOLD && (now - lastOnsetMs) > REFRACTORY_MS) {
                lastOnsetMs = now
                onsetTimestamps.addLast(now)
                if (onsetTimestamps.size > ONSET_BUFFER_SIZE) onsetTimestamps.removeFirst()

                if (onsetTimestamps.size >= 2) {
                    _bpmFlow.value = BpmCalculator.calculateBpmFromOnsets(onsetTimestamps)
                }
            }
        }
    }

    fun stop() {
        processingJob?.cancel()
        processingJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        _isRecording.value = false
        _bpmFlow.value = 0.0
    }
}
