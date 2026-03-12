package com.bpm.detector.engine

/**
 * Formats BPM values with standard musical tempo markings.
 * Ported from bpm.py: format_bpm().
 */
object BpmFormatter {

    private val TEMPO_MARKINGS = listOf(
        20.0  to "Larghissimo",
        40.0  to "Grave",
        60.0  to "Largo",
        66.0  to "Larghetto",
        76.0  to "Adagio",
        108.0 to "Andante",
        120.0 to "Moderato",
        156.0 to "Allegro",
        176.0 to "Vivace",
        200.0 to "Presto",
        Double.MAX_VALUE to "Prestissimo",
    )

    /**
     * Returns a formatted string like "120.0 BPM (Allegro)".
     * Returns "-- BPM" for invalid (non-positive) values.
     */
    fun formatBpm(bpm: Double): String {
        if (bpm <= 0.0) return "-- BPM"
        val marking = getTempoMarking(bpm)
        return "%.1f BPM (%s)".format(bpm, marking)
    }

    /**
     * Returns just the tempo marking name, e.g. "Allegro".
     */
    fun getTempoMarking(bpm: Double): String {
        if (bpm <= 0.0) return ""
        return TEMPO_MARKINGS.first { bpm <= it.first }.second
    }
}
