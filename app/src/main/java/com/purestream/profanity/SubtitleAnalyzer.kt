package com.purestream.profanity

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.StringReader

class SubtitleAnalyzer(private val profanityFilter: ProfanityFilter) {
    
    data class SyncOptions(
        val offsetMilliseconds: Long = 0L,
        val speedAdjustmentRatio: Double = 1.0,
        val validateTimings: Boolean = true,
        val repairMalformed: Boolean = true
    )
    
    suspend fun analyzeSubtitles(
        subtitleContent: String,
        filterLevel: com.purestream.data.model.ProfanityFilterLevel,
        syncOptions: SyncOptions = SyncOptions()
    ): SubtitleAnalysisResult = withContext(Dispatchers.Default) {
        
        val rawEntries = parseSubtitles(subtitleContent)
        val subtitleEntries = if (syncOptions.validateTimings || syncOptions.repairMalformed) {
            validateAndRepairSubtitles(rawEntries, syncOptions)
        } else {
            rawEntries
        }
        
        val synchronizedEntries = applySynchronization(subtitleEntries, syncOptions)
        val filteredEntries = mutableListOf<SubtitleEntry>()
        val profanityTimestamps = mutableListOf<ProfanityEvent>()
        
        synchronizedEntries.forEach { entry ->
            val filterResult = profanityFilter.filterText(entry.text, filterLevel)
            
            val filteredEntry = SubtitleEntry(
                startTime = entry.startTime,
                endTime = entry.endTime,
                text = filterResult.filteredText
            )
            filteredEntries.add(filteredEntry)
            
            if (filterResult.hasProfanity) {
                profanityTimestamps.add(
                    ProfanityEvent(
                        startTime = entry.startTime,
                        endTime = entry.endTime,
                        originalText = entry.text,
                        filteredText = filterResult.filteredText,
                        detectedWords = filterResult.detectedWords
                    )
                )
            }
        }
        
        SubtitleAnalysisResult(
            filteredSubtitles = filteredEntries,
            profanityEvents = profanityTimestamps,
            totalProfanityCount = profanityTimestamps.size
        )
    }
    
    private fun parseSubtitles(content: String): List<SubtitleEntry> {
        val entries = mutableListOf<SubtitleEntry>()
        val reader = BufferedReader(StringReader(content))
        
        try {
            var line: String?
            var currentEntry: SubtitleEntry? = null
            
            while (reader.readLine().also { line = it } != null) {
                line?.let { currentLine ->
                    when {
                        // SRT format: timestamp line
                        currentLine.contains("-->") -> {
                            val times = parseTimestamp(currentLine)
                            if (times != null) {
                                currentEntry = SubtitleEntry(
                                    startTime = times.first,
                                    endTime = times.second,
                                    text = ""
                                )
                            } else {
                                // Invalid timestamp format, skip this entry
                            }
                        }
                        // Text line
                        currentLine.isNotBlank() && !currentLine.matches(Regex("\\d+")) -> {
                            currentEntry?.let { entry ->
                                val updatedText = if (entry.text.isEmpty()) {
                                    currentLine
                                } else {
                                    "${entry.text} $currentLine"
                                }
                                currentEntry = entry.copy(text = updatedText)
                            }
                        }
                        // Empty line - end of subtitle entry
                        currentLine.isBlank() -> {
                            currentEntry?.let { entry ->
                                if (entry.text.isNotEmpty()) {
                                    entries.add(entry)
                                }
                            }
                            currentEntry = null
                        }
                        // Number line (subtitle sequence number) - ignore
                        else -> {
                            // Do nothing for subtitle sequence numbers or other lines
                        }
                    }
                }
            }
            
            // Add last entry if exists
            currentEntry?.let { entry ->
                if (entry.text.isNotEmpty()) {
                    entries.add(entry)
                }
            }
            
        } catch (e: Exception) {
            // Handle parsing errors gracefully
            e.printStackTrace()
        } finally {
            reader.close()
        }
        
        return entries
    }
    
    private fun parseTimestamp(timestampLine: String): Pair<Long, Long>? {
        try {
            // Format: 00:01:23,456 --> 00:01:25,789
            val parts = timestampLine.split("-->")
            if (parts.size == 2) {
                val startTime = timeToMilliseconds(parts[0].trim())
                val endTime = timeToMilliseconds(parts[1].trim())
                return Pair(startTime, endTime)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
    
    private fun timeToMilliseconds(timeString: String): Long {
        // Format: 00:01:23,456 or 00:01:23.456
        val cleanTime = timeString.replace(',', '.')
        val parts = cleanTime.split(':')
        
        if (parts.size == 3) {
            val hours = parts[0].toLongOrNull() ?: 0
            val minutes = parts[1].toLongOrNull() ?: 0
            val secondsParts = parts[2].split('.')
            val seconds = secondsParts[0].toLongOrNull() ?: 0
            val milliseconds = if (secondsParts.size > 1) {
                (secondsParts[1].padEnd(3, '0').take(3).toLongOrNull() ?: 0)
            } else {
                0
            }
            
            return hours * 3600000 + minutes * 60000 + seconds * 1000 + milliseconds
        }
        
        return 0
    }
    
    private fun validateAndRepairSubtitles(entries: List<SubtitleEntry>, syncOptions: SyncOptions): List<SubtitleEntry> {
        val repairedEntries = mutableListOf<SubtitleEntry>()
        var lastEndTime = 0L
        
        entries.forEach { entry ->
            var repairedEntry = entry
            
            if (syncOptions.repairMalformed) {
                if (entry.startTime < 0) {
                    repairedEntry = repairedEntry.copy(startTime = 0)
                }
                
                if (entry.endTime <= entry.startTime) {
                    val duration = 2000L
                    repairedEntry = repairedEntry.copy(endTime = entry.startTime + duration)
                }
                
                if (syncOptions.validateTimings && entry.startTime < lastEndTime) {
                    repairedEntry = repairedEntry.copy(startTime = lastEndTime + 100)
                    if (repairedEntry.endTime <= repairedEntry.startTime) {
                        repairedEntry = repairedEntry.copy(endTime = repairedEntry.startTime + 2000)
                    }
                }
                
                val cleanText = entry.text
                    .replace(Regex("<[^>]*>"), "")
                    .replace(Regex("\\{[^}]*\\}"), "")
                    .replace(Regex("\\[[^\\]]*\\]"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                
                if (cleanText != entry.text) {
                    repairedEntry = repairedEntry.copy(text = cleanText)
                }
            }
            
            if (repairedEntry.text.isNotBlank()) {
                repairedEntries.add(repairedEntry)
                lastEndTime = repairedEntry.endTime
            }
        }
        
        return removeDuplicateEntries(repairedEntries)
    }
    
    private fun removeDuplicateEntries(entries: List<SubtitleEntry>): List<SubtitleEntry> {
        val uniqueEntries = mutableListOf<SubtitleEntry>()
        var lastEntry: SubtitleEntry? = null
        
        entries.forEach { entry ->
            val isDuplicate = lastEntry?.let { last ->
                entry.text.trim().equals(last.text.trim(), ignoreCase = true) &&
                kotlin.math.abs(entry.startTime - last.startTime) < 1000
            } ?: false
            
            if (!isDuplicate) {
                uniqueEntries.add(entry)
                lastEntry = entry
            }
        }
        
        return uniqueEntries
    }
    
    private fun applySynchronization(entries: List<SubtitleEntry>, syncOptions: SyncOptions): List<SubtitleEntry> {
        if (syncOptions.offsetMilliseconds == 0L && syncOptions.speedAdjustmentRatio == 1.0) {
            return entries
        }
        
        return entries.map { entry ->
            val adjustedStartTime = ((entry.startTime * syncOptions.speedAdjustmentRatio).toLong() + syncOptions.offsetMilliseconds)
                .coerceAtLeast(0L)
            val adjustedEndTime = ((entry.endTime * syncOptions.speedAdjustmentRatio).toLong() + syncOptions.offsetMilliseconds)
                .coerceAtLeast(adjustedStartTime + 100L)
                
            entry.copy(
                startTime = adjustedStartTime,
                endTime = adjustedEndTime
            )
        }
    }
    
    fun detectTimingIssues(subtitleContent: String): TimingAnalysisResult {
        val entries = parseSubtitles(subtitleContent)
        val issues = mutableListOf<String>()
        var overlappingCount = 0
        var gapCount = 0
        var invalidDurationCount = 0
        
        entries.forEachIndexed { index, entry ->
            if (entry.endTime <= entry.startTime) {
                invalidDurationCount++
                issues.add("Entry ${index + 1}: Invalid duration (${entry.startTime} -> ${entry.endTime})")
            }
            
            if (index > 0) {
                val previousEntry = entries[index - 1]
                if (entry.startTime < previousEntry.endTime) {
                    overlappingCount++
                    issues.add("Entry ${index + 1}: Overlaps with previous entry")
                } else if (entry.startTime - previousEntry.endTime > 5000) {
                    gapCount++
                }
            }
        }
        
        return TimingAnalysisResult(
            totalEntries = entries.size,
            overlappingEntries = overlappingCount,
            largeGaps = gapCount,
            invalidDurations = invalidDurationCount,
            issues = issues
        )
    }
    
    fun generateFilteredSubtitleFile(analysisResult: SubtitleAnalysisResult): String {
        val builder = StringBuilder()
        
        analysisResult.filteredSubtitles.forEachIndexed { index, entry ->
            builder.append("${index + 1}\n")
            builder.append("${millisecondsToTime(entry.startTime)} --> ${millisecondsToTime(entry.endTime)}\n")
            builder.append("${entry.text}\n\n")
        }
        
        return builder.toString()
    }
    
    private fun millisecondsToTime(milliseconds: Long): String {
        val hours = milliseconds / 3600000
        val minutes = (milliseconds % 3600000) / 60000
        val seconds = (milliseconds % 60000) / 1000
        val millis = milliseconds % 1000
        
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }
}

data class SubtitleEntry(
    val startTime: Long, // milliseconds
    val endTime: Long,   // milliseconds
    val text: String
)

data class ProfanityEvent(
    val startTime: Long,
    val endTime: Long,
    val originalText: String,
    val filteredText: String,
    val detectedWords: List<String>
)

data class SubtitleAnalysisResult(
    val filteredSubtitles: List<SubtitleEntry>,
    val profanityEvents: List<ProfanityEvent>,
    val totalProfanityCount: Int
)

data class TimingAnalysisResult(
    val totalEntries: Int,
    val overlappingEntries: Int,
    val largeGaps: Int,
    val invalidDurations: Int,
    val issues: List<String>
)