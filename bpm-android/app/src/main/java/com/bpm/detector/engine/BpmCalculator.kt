package com.bpm.detector.engine

/**
 * Pure stateless/stateful BPM calculation logic.
 * Ported from bpm.py: calculate_bpm(), bpm_to_ms().
 */
object BpmCalculator {

    /**
     * Calculates BPM from a list of tap timestamps in milliseconds.
     * Returns 0.0 if fewer than 2 taps are provided.
     */
    fun calculateBpm(tapTimesMs: List<Long>): Double {
        if (tapTimesMs.size < 2) return 0.0
        val intervals = (1 until tapTimesMs.size).map { i ->
            (tapTimesMs[i] - tapTimesMs[i - 1]).toDouble()
        }
        val avgIntervalMs = intervals.average()
        return 60_000.0 / avgIntervalMs
    }

    /**
     * Calculates BPM from an ArrayDeque of onset timestamps in milliseconds.
     * Used by AudioEngine for microphone-based detection.
     */
    fun calculateBpmFromOnsets(onsetTimesMs: ArrayDeque<Long>): Double {
        if (onsetTimesMs.size < 2) return 0.0
        val list = onsetTimesMs.toList()
        val intervals = (1 until list.size).map { i ->
            (list[i] - list[i - 1]).toDouble()
        }
        val avgIntervalMs = intervals.average()
        return 60_000.0 / avgIntervalMs
    }

    /**
     * Converts BPM to milliseconds per beat.
     * Returns 0.0 for invalid (non-positive) BPM values.
     */
    fun bpmToMs(bpm: Double): Double {
        if (bpm <= 0.0) return 0.0
        return 60_000.0 / bpm
    }
}

/**
 * Mutable state holder for tap tempo mode.
 * Keeps the last [maxTaps] timestamps to produce a responsive but stable average.
 */
class TapState(private val maxTaps: Int = 16) {

    private val tapTimes = ArrayDeque<Long>()

    val tapCount: Int get() = tapTimes.size

    fun recordTap(timestampMs: Long) {
        tapTimes.addLast(timestampMs)
        if (tapTimes.size > maxTaps) tapTimes.removeFirst()
    }

    fun currentBpm(): Double = BpmCalculator.calculateBpm(tapTimes.toList())

    fun reset() = tapTimes.clear()
}
