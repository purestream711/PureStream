package com.purestream.profanity

import com.purestream.data.model.FilteredSubtitleResult
import com.purestream.data.model.ParsedSubtitle
import com.purestream.data.model.ProfanityFilterLevel
import com.purestream.data.model.ProfanityLevel
import com.purestream.data.model.ProfanityStats
import com.purestream.data.model.TimeRange
import com.purestream.data.model.SubtitleEntry as ModelSubtitleEntry
import java.io.BufferedReader
import java.io.StringReader
import java.util.regex.Pattern

class SubtitleParser {
    
    fun parseSrt(srtContent: String): ParsedSubtitle {
        val entries = mutableListOf<ModelSubtitleEntry>()
        val reader = BufferedReader(StringReader(srtContent))
        var currentIndex = 0
        var currentTimestamp: Pair<Long, Long>? = null
        val currentTextLines = mutableListOf<String>()
        
        reader.useLines { lines ->
            lines.forEach { line ->
                val trimmedLine = line.trim()
                
                when {
                    // Empty line indicates end of subtitle block
                    trimmedLine.isEmpty() -> {
                        val timestamp = currentTimestamp
                        if (currentIndex > 0 && timestamp != null && currentTextLines.isNotEmpty()) {
                            val text = currentTextLines.joinToString("\n")
                            val cleanText = cleanSubtitleText(text)
                            
                            entries.add(
                                ModelSubtitleEntry(
                                    index = currentIndex,
                                    startTime = timestamp.first,
                                    endTime = timestamp.second,
                                    originalText = cleanText
                                )
                            )
                        }
                        
                        // Reset for next entry
                        currentIndex = 0
                        currentTimestamp = null
                        currentTextLines.clear()
                    }
                    
                    // Check if it's a subtitle index (just digits)
                    trimmedLine.matches("\\d+".toRegex()) -> {
                        currentIndex = trimmedLine.toIntOrNull() ?: 0
                    }
                    
                    // Check if it's a timestamp line
                    trimmedLine.contains("-->") -> {
                        currentTimestamp = parseTimestamp(trimmedLine)
                    }
                    
                    // Otherwise, it's subtitle text
                    else -> {
                        currentTextLines.add(trimmedLine)
                    }
                }
            }
        }
        
        // Handle last entry if file doesn't end with empty line
        val finalTimestamp = currentTimestamp
        if (currentIndex > 0 && finalTimestamp != null && currentTextLines.isNotEmpty()) {
            val text = currentTextLines.joinToString("\n")
            val cleanText = cleanSubtitleText(text)
            
            entries.add(
                ModelSubtitleEntry(
                    index = currentIndex,
                    startTime = finalTimestamp.first,
                    endTime = finalTimestamp.second,
                    originalText = cleanText
                )
            )
        }
        
        return ParsedSubtitle(
            entries = entries,
            totalProfanityEntries = 0,
            profanityTimestamps = emptyList()
        )
    }
    
    private fun parseTimestamp(timestampLine: String): Pair<Long, Long>? {
        // Format: "00:01:23,456 --> 00:01:25,789"
        val pattern = Pattern.compile("(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})")
        val matcher = pattern.matcher(timestampLine)
        
        return if (matcher.find()) {
            val startHours = matcher.group(1).toLong()
            val startMinutes = matcher.group(2).toLong()
            val startSeconds = matcher.group(3).toLong()
            val startMs = matcher.group(4).toLong()
            
            val endHours = matcher.group(5).toLong()
            val endMinutes = matcher.group(6).toLong()
            val endSeconds = matcher.group(7).toLong()
            val endMs = matcher.group(8).toLong()
            
            val startTime = (startHours * 3600 + startMinutes * 60 + startSeconds) * 1000 + startMs
            val endTime = (endHours * 3600 + endMinutes * 60 + endSeconds) * 1000 + endMs
            
            Pair(startTime, endTime)
        } else {
            null
        }
    }
    
    private fun cleanSubtitleText(text: String): String {
        return text
            // Remove HTML tags
            .replace(Regex("<[^>]*>"), "")
            // Remove subtitle formatting tags (properly escaped)
            .replace(Regex("\\{[^}]*\\}"), "")
            // Remove speaker names in brackets
            .replace(Regex("^\\[[^\\]]*\\]\\s*"), "")
            // Clean up extra whitespace
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    fun filterSubtitle(
        subtitle: ParsedSubtitle, 
        profanityFilter: ProfanityFilter,
        filterLevel: ProfanityFilterLevel
    ): FilteredSubtitleResult {
        if (filterLevel == ProfanityFilterLevel.NONE) {
            return FilteredSubtitleResult(
                originalSubtitle = subtitle,
                filteredSubtitle = subtitle,
                mutingTimestamps = emptyList(),
                profanityStats = ProfanityStats(
                    totalEntries = subtitle.entries.size,
                    profanityEntries = 0,
                    profanityPercentage = 0f,
                    detectedWords = emptyMap(),
                    profanityLevel = ProfanityLevel.NONE
                )
            )
        }
        
        val mutingTimestamps = mutableListOf<TimeRange>()
        val allDetectedWords = mutableMapOf<String, Int>()
        var profanityEntryCount = 0
        
        val filteredEntries = subtitle.entries.map { entry ->
            val filterResult = profanityFilter.filterText(entry.originalText, filterLevel)
            
            if (filterResult.hasProfanity) {
                profanityEntryCount++
                
                // Add to muting timestamps
                mutingTimestamps.add(
                    TimeRange(
                        startTime = entry.startTime,
                        endTime = entry.endTime
                    )
                )
                
                // Count detected words
                filterResult.detectedWords.forEach { word ->
                    allDetectedWords[word] = allDetectedWords.getOrDefault(word, 0) + 1
                }
                
                entry.copy(
                    filteredText = filterResult.filteredText,
                    hasProfanity = true,
                    detectedWords = filterResult.detectedWords
                )
            } else {
                entry
            }
        }
        
        val filteredSubtitle = ParsedSubtitle(
            entries = filteredEntries,
            totalProfanityEntries = profanityEntryCount,
            profanityTimestamps = mutingTimestamps
        )
        
        // Determine overall profanity level based on detection
        val profanityLevel = when {
            profanityEntryCount == 0 -> ProfanityLevel.NONE
            profanityEntryCount <= 2 -> ProfanityLevel.LOW
            profanityEntryCount <= 10 -> ProfanityLevel.MEDIUM
            else -> ProfanityLevel.HIGH
        }
        
        val profanityPercentage = if (subtitle.entries.isNotEmpty()) {
            (profanityEntryCount.toFloat() / subtitle.entries.size.toFloat()) * 100f
        } else {
            0f
        }
        
        return FilteredSubtitleResult(
            originalSubtitle = subtitle,
            filteredSubtitle = filteredSubtitle,
            mutingTimestamps = mutingTimestamps,
            profanityStats = ProfanityStats(
                totalEntries = subtitle.entries.size,
                profanityEntries = profanityEntryCount,
                profanityPercentage = profanityPercentage,
                detectedWords = allDetectedWords,
                profanityLevel = profanityLevel
            )
        )
    }
}