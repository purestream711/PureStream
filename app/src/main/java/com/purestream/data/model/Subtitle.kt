package com.purestream.data.model

data class SubtitleEntry(
    val index: Int,
    val startTime: Long, // milliseconds
    val endTime: Long,   // milliseconds
    val originalText: String,
    val filteredText: String? = null,
    val hasProfanity: Boolean = false,
    val detectedWords: List<String> = emptyList()
) {
    val duration: Long get() = endTime - startTime
    val displayText: String get() = filteredText ?: originalText
}

data class ParsedSubtitle(
    val entries: List<SubtitleEntry>,
    val totalProfanityEntries: Int,
    val profanityTimestamps: List<TimeRange>
) {
    fun toSrtString(): String {
        return entries.joinToString("\n\n") { entry ->
            "${entry.index}\n${formatTime(entry.startTime)} --> ${formatTime(entry.endTime)}\n${entry.displayText}"
        }
    }
    
    private fun formatTime(milliseconds: Long): String {
        val hours = milliseconds / (1000 * 60 * 60)
        val minutes = (milliseconds % (1000 * 60 * 60)) / (1000 * 60)
        val seconds = (milliseconds % (1000 * 60)) / 1000
        val ms = milliseconds % 1000
        
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, ms)
    }
}

data class TimeRange(
    val startTime: Long,
    val endTime: Long
) {
    fun contains(timeMs: Long): Boolean = timeMs in startTime..endTime
    fun duration(): Long = endTime - startTime
}

data class FilteredSubtitleResult(
    val originalSubtitle: ParsedSubtitle,
    val filteredSubtitle: ParsedSubtitle,
    val mutingTimestamps: List<TimeRange>,
    val profanityStats: ProfanityStats
)

data class ProfanityStats(
    val totalEntries: Int,
    val profanityEntries: Int,
    val profanityPercentage: Float,
    val detectedWords: Map<String, Int>, // word -> count
    val profanityLevel: ProfanityLevel
)