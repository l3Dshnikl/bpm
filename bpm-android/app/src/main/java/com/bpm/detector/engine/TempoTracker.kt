package com.bpm.detector.engine

/**
 * Tempo tracker that filters raw onsets from the energy detector into stable beat events.
 *
 * The raw energy detector fires on every transient: kick, snare, hi-hat, bass note attack,
 * etc. Without filtering, the BPM reading locks onto whatever subdivision is loudest, which
 * is often 2× or 4× the actual beat (e.g. snare-only at 240 BPM when the song is 120 BPM).
 *
 * This class maintains a running estimate of the beat period and classifies each incoming
 * onset as one of:
 *   - subdivision (interval ≈ ½ beat or less) → skipped
 *   - normal beat  (interval ≈ 1 beat)         → accepted, estimate updated
 *   - missed beat  (interval ≈ 2 beats)         → accepted, estimate nudged
 *   - large deviation                           → skipped, estimate preserved
 *
 * The result is a BPM value computed from the median of the last [maxOnsets] accepted
 * beat intervals — robust to occasional false acceptances.
 */
class TempoTracker(private val maxOnsets: Int = 16) {

    // Running estimate of beat period in milliseconds. 0.0 = not yet bootstrapped.
    private var beatPeriodMs: Double = 0.0

    // Timestamp of the last accepted beat onset.
    private var lastAcceptedMs: Long = 0L

    // Circular buffer of accepted beat timestamps used for BPM calculation.
    private val onsets = ArrayDeque<Long>()

    /**
     * Submit a raw onset timestamp (from [android.os.SystemClock.elapsedRealtime]).
     * Returns the current BPM estimate, or 0.0 if not enough data yet.
     */
    fun processOnset(nowMs: Long): Double {
        if (lastAcceptedMs == 0L) {
            // Very first onset — just anchor the timestamp, nothing to measure yet.
            lastAcceptedMs = nowMs
            onsets.addLast(nowMs)
            return 0.0
        }

        val interval = nowMs - lastAcceptedMs

        if (beatPeriodMs == 0.0) {
            // Bootstrap phase: accept the first interval that falls in the 40–240 BPM range.
            if (interval in 250..1500) {
                beatPeriodMs = interval.toDouble()
                accept(nowMs)
            }
            return currentBpm()
        }

        return when {
            // ── Subdivision: interval is less than ~65 % of the beat period.
            // Likely a snare, hi-hat, 8th/16th note attack. Skip.
            interval < beatPeriodMs * 0.65 -> currentBpm()

            // ── Missed beat: interval is approximately 2 beats (180–220 % of estimate).
            // Accept this onset, but halve the interval when updating the estimate so we
            // don't drift toward half-tempo.
            interval >= beatPeriodMs * 1.8 && interval <= beatPeriodMs * 2.2 -> {
                beatPeriodMs = beatPeriodMs * 0.9 + (interval / 2.0) * 0.1
                accept(nowMs)
                currentBpm()
            }

            // ── Normal beat: interval is within ±35 % of current estimate.
            // Update the estimate with slow EMA (α = 0.15) for a stable reading.
            interval >= beatPeriodMs * 0.65 && interval <= beatPeriodMs * 1.35 -> {
                beatPeriodMs = beatPeriodMs * 0.85 + interval * 0.15
                accept(nowMs)
                currentBpm()
            }

            // ── Large deviation (tempo change or spurious transient).
            // Don't update the estimate; don't accept as a beat. Preserve current BPM.
            else -> currentBpm()
        }
    }

    /** Reset all state (called when the engine stops). */
    fun reset() {
        beatPeriodMs = 0.0
        lastAcceptedMs = 0L
        onsets.clear()
    }

    private fun accept(nowMs: Long) {
        lastAcceptedMs = nowMs
        onsets.addLast(nowMs)
        if (onsets.size > maxOnsets) onsets.removeFirst()
    }

    private fun currentBpm(): Double = BpmCalculator.calculateBpmFromOnsets(onsets)
}
