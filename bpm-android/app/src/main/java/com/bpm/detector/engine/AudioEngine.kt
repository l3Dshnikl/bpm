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
 * Pipeline per audio hop (~11 ms):
 *  1. Read HOP_SIZE 16-bit PCM samples from AudioRecord.
 *  2. Compute RMS energy of the hop using a tight for-loop (zero allocation).
 *  3. Apply EMA smoothing to suppress single-sample noise spikes.
 *  4. Maintain a ~1-second FloatArray circular buffer of smoothed RMS values.
 *  5. Detect an onset when:
 *       a. smoothedRms is RISING (derivative > 0) — prevents decay-tail triggers
 *       b. smoothedRms > mean(history) × ONSET_THRESHOLD
 *       c. ≥ REFRACTORY_MS since the last raw onset
 *  6. Pass each valid raw onset to TempoTracker, which filters subdivisions and
 *     compensates for missed beats before updating the BPM StateFlow.
 *
 * Genre robustness summary:
 *  - Rock/EDM:   snare/hi-hat subdivisions (2×/4×) filtered by TempoTracker
 *  - Jazz:       missed beats compensated; EMA + derivative suppresses brush noise
 *  - Classical:  large-deviation onsets discarded; estimate preserved
 *  - Slow (40 BPM): 1-second history window covers the full beat period
 *  - Fast (240 BPM): 250 ms refractory ceiling supports Prestissimo
 */
class AudioEngine(private val scope: CoroutineScope) {

    companion object {
        private const val SAMPLE_RATE = 44_100
        private const val HOP_SIZE = 512              // ~11.6 ms per hop
        private const val HISTORY_FRAMES = 86         // ~1 s of RMS history (fix: was 43 ≈ 0.5 s)
        private const val ONSET_THRESHOLD = 1.5f      // multiplier over mean energy
        private const val REFRACTORY_MS = 250L        // fix: was 300 → supports up to 240 BPM
        private const val EMA_ALPHA = 0.3f            // exponential smoothing coefficient
        private const val BPM_MIN = 40.0
        private const val BPM_MAX = 240.0
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

        // Zero-allocation circular buffer for RMS history (fix: was ArrayDeque<Float> → boxing)
        val rmsHistory = FloatArray(HISTORY_FRAMES)
        var historyHead = 0
        var historyFilled = 0

        val tempoTracker = TempoTracker()
        var smoothedRms = 0f
        var prevSmoothedRms = 0f
        var lastRawOnsetMs = 0L

        while (isActive && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val read = record.read(pcmBuffer, 0, HOP_SIZE)
            if (read <= 0) continue

            // ── RMS via plain loop — zero allocation (fix: was pcmBuffer.take(read).sumOf{...})
            var sumSq = 0L
            for (i in 0 until read) {
                val s = pcmBuffer[i].toLong()
                sumSq += s * s
            }
            val rms = sqrt(sumSq.toDouble() / read).toFloat()

            // ── EMA smoothing: reduces single-sample noise spikes
            smoothedRms = EMA_ALPHA * rms + (1f - EMA_ALPHA) * smoothedRms

            // ── Store in circular buffer
            rmsHistory[historyHead] = smoothedRms
            historyHead = (historyHead + 1) % HISTORY_FRAMES
            if (historyFilled < HISTORY_FRAMES) historyFilled++

            // Wait until history is warm
            if (historyFilled < HISTORY_FRAMES) {
                prevSmoothedRms = smoothedRms
                continue
            }

            // ── Mean of history (used as adaptive baseline)
            var sum = 0f
            for (v in rmsHistory) sum += v
            val meanEnergy = sum / HISTORY_FRAMES

            val now = SystemClock.elapsedRealtime()

            // ── Onset conditions:
            //    a) energy is rising (derivative check — prevents decay-tail triggers)
            //    b) exceeds adaptive threshold
            //    c) outside refractory window
            if (smoothedRms > prevSmoothedRms
                && smoothedRms > meanEnergy * ONSET_THRESHOLD
                && (now - lastRawOnsetMs) > REFRACTORY_MS
            ) {
                lastRawOnsetMs = now
                val bpm = tempoTracker.processOnset(now)
                if (bpm in BPM_MIN..BPM_MAX) {
                    _bpmFlow.value = bpm
                }
            }

            prevSmoothedRms = smoothedRms
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
