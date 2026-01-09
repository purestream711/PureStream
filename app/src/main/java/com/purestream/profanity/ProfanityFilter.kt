package com.purestream.profanity

import com.purestream.data.model.ProfanityFilterLevel
import com.purestream.data.model.ProfanityLevel
import java.util.regex.Pattern

class ProfanityFilter {
    
    companion object {
        private val FLEXIBLE_MATCH_WORDS = setOf(
            "fuck", "bitch", "bastard", "whore", "cunt", "shit"
        )

        private val WORD_EXCEPTIONS = mapOf(
            "shit" to setOf("shiitake", "mishit", "megahit", "shitepoke"),
            "cunt" to setOf("scunthorpe")
        )

        // MILD: Only the worst of the worst profanity
        private val MILD_WORDS = setOf(
            "fuck", "fucking", "fucked", "fucker", "fuckers", "fucks", "fuckin'", "fuckin'",
            "motherfuck", "motherfucker", "motherfuckers", "motherfucking",
            "bitch", "bitches", "bitching", "bitchin'", "bitchin'",
            "ass", "asses", "asshole", "assholes",
            "arse", "arses", "arsehole", "arseholes"
        )
        
        // MODERATE: Worst of the worst + some lesser profanity
        private val MODERATE_WORDS = MILD_WORDS + setOf(
            "shit", "shits", "shitting", "shitter", "shite", "shitty", "shittier", "bullshit", "dipshit", "dipshits",
            "dickwad", "dickwads",
            "cunt", "cunts", "pussy", "pussies",
            "cock", "cocks", "cocksucker", "cocksuckers",
            "dick", "dicks", "dickhead", "dickheads",
            "whore", "whores", "slut", "sluts", "bastard", "bastards",
            "piss", "pissed", "pissing", "damn", "damned"
        )
        
        // STRICT: Everything including mild religious and casual profanity
        private val STRICT_WORDS = MODERATE_WORDS + setOf(
            "god", "gods", "hell", "hells", "goddamn", "goddam", "goddammit", "god damn", "god dammit",
            "oh my god", "omg", "jesus", "christ", "lord", "jesus christ", "holy shit",
            "fag", "fagot", "faggot",
            "retard", "retards", "gay", "homo", "queer", "lesbian", "tranny", "nigga",
            "nigger", "son of a bitch", "spic", "chink", "wetback"
        )
        
        private val REPLACEMENT_MAP = mapOf(
            // MILD level replacements
            "fuck" to "frick", "fucking" to "freaking", "fucked" to "messed up", "fucker" to "jerk",
            "fuckers" to "jerks", "fucks" to "messes up", "fuckin'" to "freakin'", "fuckin'" to "freakin'",
            "motherfuck" to "jerk", "motherfucker" to "jerk", "motherfuckers" to "jerks", "motherfucking" to "freaking",
            "bitch" to "jerk", "bitches" to "jerks", "bitching" to "complaining", "bitchin'" to "complainin'", "bitchin'" to "complainin'",
            "ass" to "butt", "asses" to "butts", "asshole" to "jerk", "assholes" to "jerks",
            "arse" to "butt", "arses" to "butts", "arsehole" to "jerk", "arseholes" to "jerks",
            
            // MODERATE level replacements
            "shit" to "crap", "shits" to "craps", "shitting" to "crapping", "shitter" to "crapper",
            "shite" to "crap", "shitty" to "crappy", "shittier" to "crappier", "bullshit" to "nonsense", "dipshit" to "silly person", "dipshits" to "silly people",
            "dickwad" to "jerkwad", "dickwads" to "jerkwads",
            "cunt" to "person", "cunts" to "people", "pussy" to "cat", "pussies" to "cats",
            "cock" to "rooster", "cocks" to "roosters", "cocksucker" to "jerk", "cocksuckers" to "jerks",
            "dick" to "jerk", "dicks" to "jerks", "dickhead" to "jerk", "dickheads" to "jerks",
            "whore" to "mean person", "whores" to "mean people", "slut" to "mean person", "sluts" to "mean people",
            "bastard" to "jerk", "bastards" to "jerks",
            "piss" to "ticked", "pissed" to "ticked off", "pissing" to "getting ticked",
            "damn" to "darn", "damned" to "darned",
            
            // STRICT level replacements
            "god" to "gosh", "gods" to "goshes", "hell" to "heck", "hells" to "hecks",
            "goddamn" to "gosh darn", "goddam" to "gosh darn", "goddammit" to "gosh darnit",
            "god damn" to "gosh darn", "god dammit" to "gosh darnit", "oh my god" to "oh my gosh",
            "omg" to "omg", "jesus" to "jeepers", "christ" to "crikey", "lord" to "goodness",
            "jesus christ" to "jeepers crikey", "holy shit" to "holy crap",
            "fag" to "person", "fagot" to "person", "faggot" to "person",
            "retard" to "silly person", "retards" to "silly people", "gay" to "happy",
            "homo" to "person", "queer" to "strange", "lesbian" to "person", "tranny" to "person",
            "nigga" to "person", "nigger" to "person", "son of a bitch" to "mean person",
            "spic" to "person", "chink" to "person", "wetback" to "person"
        )
    }
    
    private val customFilteredWords = mutableSetOf<String>()
    private val whitelistedWords = mutableSetOf<String>()
    
    fun addCustomFilteredWords(words: List<String>) {
        customFilteredWords.addAll(words.map { it.lowercase() })
    }
    
    fun removeCustomFilteredWords(words: List<String>) {
        customFilteredWords.removeAll(words.map { it.lowercase() }.toSet())
    }
    
    fun addWhitelistedWords(words: List<String>) {
        whitelistedWords.addAll(words.map { it.lowercase() })
    }
    
    fun removeWhitelistedWords(words: List<String>) {
        whitelistedWords.removeAll(words.map { it.lowercase() }.toSet())
    }
    
    fun setCustomFilteredWords(words: List<String>) {
        customFilteredWords.clear()
        customFilteredWords.addAll(words.map { it.lowercase() })
    }
    
    fun setWhitelistedWords(words: List<String>) {
        whitelistedWords.clear()
        whitelistedWords.addAll(words.map { it.lowercase() })
    }
    
    private fun isFalsePositive(text: String, start: Int, end: Int, word: String): Boolean {
        val exceptions = WORD_EXCEPTIONS[word.lowercase()] ?: return false
        
        // Expand to full word
        var wordStart = start
        while (wordStart > 0 && Character.isLetter(text[wordStart - 1])) {
            wordStart--
        }
        
        var wordEnd = end
        while (wordEnd < text.length && Character.isLetter(text[wordEnd])) {
            wordEnd++
        }
        
        val fullWord = text.substring(wordStart, wordEnd).lowercase()
        return exceptions.any { exception -> fullWord.contains(exception) }
    }

    private fun matchCase(original: String, replacement: String): String {
        if (original.isEmpty() || replacement.isEmpty()) return replacement
        
        // Check for ALL CAPS (if length > 1)
        if (original.length > 1 && original == original.uppercase()) {
            return replacement.uppercase()
        }
        
        // Check for Title Case
        if (Character.isUpperCase(original[0])) {
            // If replacement is empty, return empty
            if (replacement.length == 1) return replacement.uppercase()
            return replacement[0].uppercase() + replacement.substring(1)
        }
        
        return replacement // Default to lowercase (as defined in map)
    }

    fun filterText(text: String, filterLevel: ProfanityFilterLevel): FilterResult {
        // Always detect ALL profanity words (predefined + custom) regardless of filter level for consistent analysis
        val allProfanityWords = STRICT_WORDS + customFilteredWords  // Include custom words in detection
        val detectedWords = mutableListOf<String>()
        var filteredText = text
        var hasProfanity = false
        
        // First pass: Detect ALL profanity words (regardless of filter level)
        allProfanityWords.forEach { word ->
            if (!whitelistedWords.contains(word.lowercase())) {
                val lowercaseWord = word.lowercase()
                val isFlexible = FLEXIBLE_MATCH_WORDS.contains(lowercaseWord)
                
                val pattern = if (isFlexible) {
                    Pattern.compile(Pattern.quote(word), Pattern.CASE_INSENSITIVE)
                } else {
                    Pattern.compile("\\b${Pattern.quote(word)}\\b", Pattern.CASE_INSENSITIVE)
                }
                
                val matcher = pattern.matcher(text)
                
                while (matcher.find()) {
                    if (isFlexible && isFalsePositive(text, matcher.start(), matcher.end(), word)) {
                        continue
                    }
                    hasProfanity = true
                    if (!detectedWords.contains(word)) {
                        detectedWords.add(word)
                    }
                }
            }
        }
        
        // Second pass: Apply filtering based on filter level
        if (filterLevel == ProfanityFilterLevel.NONE) {
            // NONE: Don't filter predefined words, but still filter custom words
            // Custom words should be filtered regardless of filter level
            customFilteredWords.forEach { word ->
                if (!whitelistedWords.contains(word.lowercase())) {
                    val pattern = Pattern.compile("\\b${Pattern.quote(word)}\\b", Pattern.CASE_INSENSITIVE)
                    val matcher = pattern.matcher(filteredText)
                    
                    if (matcher.find()) {
                        // Replace custom words with [filtered] as requested
                        filteredText = matcher.replaceAll("\u200B[filtered]\u200C")
                    }
                }
            }
            return FilterResult(filteredText, hasProfanity, detectedWords)
        }
        
        val wordsToFilter = getWordsToFilter(filterLevel).sortedByDescending { it.length }
        
        // Apply replacements for words that should be filtered at this level
        wordsToFilter.forEach { word ->
            if (!whitelistedWords.contains(word.lowercase()) && detectedWords.contains(word)) {
                val lowercaseWord = word.lowercase()
                val isFlexible = FLEXIBLE_MATCH_WORDS.contains(lowercaseWord)
                
                val pattern = if (isFlexible) {
                    Pattern.compile(Pattern.quote(word), Pattern.CASE_INSENSITIVE)
                } else {
                    Pattern.compile("\\b${Pattern.quote(word)}\\b", Pattern.CASE_INSENSITIVE)
                }
                
                val matcher = pattern.matcher(filteredText)
                
                // We need to handle replacements carefully with flexible matching to avoid infinite loops or double replacements
                // Using appendReplacement is safer than replaceAll when we have conditional logic (exceptions)
                val sb = StringBuffer()
                var foundMatch = false
                while (matcher.find()) {
                    val originalText = matcher.group()
                    if (isFlexible && isFalsePositive(filteredText, matcher.start(), matcher.end(), word)) {
                        matcher.appendReplacement(sb, originalText)
                        continue
                    }
                    
                    foundMatch = true
                    val baseReplacement = if (customFilteredWords.contains(word)) {
                        "[filtered]"
                    } else {
                        REPLACEMENT_MAP[word.lowercase()] ?: "***"
                    }
                    val casedReplacement = matchCase(originalText, baseReplacement)
                    matcher.appendReplacement(sb, "\u200B$casedReplacement\u200C")
                }
                matcher.appendTail(sb)
                
                if (foundMatch) {
                    filteredText = sb.toString()
                }
            }
        }
        
        return FilterResult(filteredText, hasProfanity, detectedWords)
    }
    
    fun analyzeProfanityLevel(text: String): ProfanityLevel {
        val lowercaseText = text.lowercase()
        
        // Updated analysis to respect flexible matching
        val strictCount = STRICT_WORDS.count { word ->
            val isFlexible = FLEXIBLE_MATCH_WORDS.contains(word)
            val regex = if (isFlexible) {
                 Pattern.quote(word).toRegex(RegexOption.IGNORE_CASE)
            } else {
                 "\\b${Pattern.quote(word)}\\b".toRegex(RegexOption.IGNORE_CASE)
            }
            lowercaseText.contains(regex)
        }
        val moderateCount = MODERATE_WORDS.count { word ->
            val isFlexible = FLEXIBLE_MATCH_WORDS.contains(word)
            val regex = if (isFlexible) {
                 Pattern.quote(word).toRegex(RegexOption.IGNORE_CASE)
            } else {
                 "\\b${Pattern.quote(word)}\\b".toRegex(RegexOption.IGNORE_CASE)
            }
            lowercaseText.contains(regex)
        }
        val mildCount = MILD_WORDS.count { word ->
            val isFlexible = FLEXIBLE_MATCH_WORDS.contains(word)
            val regex = if (isFlexible) {
                 Pattern.quote(word).toRegex(RegexOption.IGNORE_CASE)
            } else {
                 "\\b${Pattern.quote(word)}\\b".toRegex(RegexOption.IGNORE_CASE)
            }
            lowercaseText.contains(regex)
        }
        
        return when {
            strictCount > moderateCount -> ProfanityLevel.HIGH
            moderateCount > mildCount -> ProfanityLevel.MEDIUM
            mildCount > 0 -> ProfanityLevel.LOW
            else -> ProfanityLevel.NONE
        }
    }
    
    private fun getWordsToFilter(filterLevel: ProfanityFilterLevel): Set<String> {
        val baseWords = when (filterLevel) {
            ProfanityFilterLevel.MILD -> MILD_WORDS
            ProfanityFilterLevel.MODERATE -> MODERATE_WORDS
            ProfanityFilterLevel.STRICT -> STRICT_WORDS
            ProfanityFilterLevel.NONE -> emptySet()
        }
        
        return baseWords + customFilteredWords
    }
}

data class FilterResult(
    val filteredText: String,
    val hasProfanity: Boolean,
    val detectedWords: List<String>
)

