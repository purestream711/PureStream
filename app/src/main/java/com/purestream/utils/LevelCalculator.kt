package com.purestream.utils

/**
 * Utility class for calculating RPG-style level progression based on total filtered profanity words.
 *
 * New level progression requirements:
 * - Level 1 → 2: 5 words
 * - Level 30: Reach 10,000 total words
 * - Formula: totalWordsToReachLevel(n) = 5 * (n - 1)^2.257272
 * - Max Level: 30
 */
object LevelCalculator {

    /**
     * Calculate the number of words required to advance FROM level N to level N+1.
     *
     * @param level The current level (starting from 1)
     * @return Number of words needed to reach the next level
     */
    fun wordsRequiredForLevel(level: Int): Int {
        if (level < 1) return 5 // Default to first level requirement
        if (level >= 30) return 0 // Max level reached
        return totalWordsToReachLevel(level + 1) - totalWordsToReachLevel(level)
    }

    /**
     * Calculate the cumulative total words needed to reach a specific level.
     *
     * @param level The target level
     * @return Total words needed to reach that level (starting from level 1)
     *
     * Examples:
     * - Level 1: 0 words (starting level)
     * - Level 2: 5 words
     * - Level 30: 10,000 words
     */
    fun totalWordsToReachLevel(level: Int): Int {
        if (level <= 1) return 0
        // Formula: 5 * (level - 1)^2.257272
        // Derived from constraints: W(1)=0, W(2)=5, W(30)=10000
        return (5.0 * Math.pow((level - 1).toDouble(), 2.257272)).toInt()
    }

    /**
     * Calculate the current level and progress based on total words filtered.
     *
     * @param totalWords Total profanity words filtered across all time
     * @return Triple of (currentLevel, wordsIntoCurrentLevel, wordsRequiredForNextLevel)
     */
    fun calculateLevel(totalWords: Int): Triple<Int, Int, Int> {
        if (totalWords < 0) {
            return Triple(1, 0, wordsRequiredForLevel(1))
        }

        // Find the current level by checking cumulative totals
        var currentLevel = 1
        while (currentLevel < 30 && totalWords >= totalWordsToReachLevel(currentLevel + 1)) {
            currentLevel++
        }
        
        // Cap at level 30
        if (currentLevel >= 30) {
            return Triple(30, 0, 0) // Max level reached
        }

        // Calculate progress into current level
        val wordsAtLevelStart = totalWordsToReachLevel(currentLevel)
        val wordsIntoLevel = totalWords - wordsAtLevelStart
        val wordsRequired = wordsRequiredForLevel(currentLevel)

        return Triple(currentLevel, wordsIntoLevel, wordsRequired)
    }

    /**
     * Calculate progress percentage for display in progress bar.
     *
     * @param wordsIntoLevel Words filtered in the current level
     * @param wordsRequiredForNextLevel Total words required to reach next level
     * @return Progress as a float between 0.0 and 1.0
     */
    fun calculateProgress(wordsIntoLevel: Int, wordsRequiredForNextLevel: Int): Float {
        // If required is 0 (max level), show full progress
        if (wordsRequiredForNextLevel <= 0) return 1f
        val progress = wordsIntoLevel.toFloat() / wordsRequiredForNextLevel.toFloat()
        return progress.coerceIn(0f, 1f)
    }

    /**
     * Get a human-readable description of the level progression.
     * Useful for debugging or displaying level information.
     */
    fun getLevelDescription(totalWords: Int): String {
        val (level, wordsInto, wordsRequired) = calculateLevel(totalWords)
        return "Level $level: $wordsInto/$wordsRequired words to next level ($totalWords total filtered)"
    }
}
