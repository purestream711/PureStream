package com.purestream.utils

/**
 * Utility class for calculating RPG-style level progression based on total filtered profanity words.
 *
 * Level progression formula:
 * - Level 1 → 2: 3 words
 * - Level 2 → 3: 5 more words (8 total)
 * - Level 3 → 4: 7 more words (15 total)
 * - Pattern: Each level requires 2 more words than previous level
 *
 * Formula: wordsRequiredForLevel(n) = 2n + 1
 * Total words to reach level n: n² + 2n - 3
 */
object LevelCalculator {

    /**
     * Calculate the number of words required to advance FROM level N to level N+1.
     *
     * @param level The current level (starting from 1)
     * @return Number of words needed to reach the next level
     *
     * Examples:
     * - Level 1 → 2: 3 words
     * - Level 2 → 3: 5 words
     * - Level 3 → 4: 7 words
     */
    fun wordsRequiredForLevel(level: Int): Int {
        if (level < 1) return 3 // Default to first level requirement
        return (2 * level) + 1
    }

    /**
     * Calculate the cumulative total words needed to reach a specific level.
     *
     * @param level The target level
     * @return Total words needed to reach that level (starting from level 1)
     *
     * Examples:
     * - Level 1: 0 words (starting level)
     * - Level 2: 3 words
     * - Level 3: 8 words (3 + 5)
     * - Level 4: 15 words (3 + 5 + 7)
     */
    fun totalWordsToReachLevel(level: Int): Int {
        if (level <= 1) return 0
        // Formula: n² - 1
        return (level * level) - 1
    }

    /**
     * Calculate the current level and progress based on total words filtered.
     *
     * @param totalWords Total profanity words filtered across all time
     * @return Triple of (currentLevel, wordsIntoCurrentLevel, wordsRequiredForNextLevel)
     *
     * Example:
     * - 0 words → (1, 0, 3) = Level 1, 0/3 progress
     * - 3 words → (2, 0, 5) = Level 2, 0/5 progress
     * - 5 words → (2, 2, 5) = Level 2, 2/5 progress
     * - 8 words → (3, 0, 7) = Level 3, 0/7 progress
     */
    fun calculateLevel(totalWords: Int): Triple<Int, Int, Int> {
        if (totalWords < 0) {
            return Triple(1, 0, wordsRequiredForLevel(1))
        }

        // Find the current level by checking cumulative totals
        var currentLevel = 1
        while (totalWords >= totalWordsToReachLevel(currentLevel + 1)) {
            currentLevel++
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
        if (wordsRequiredForNextLevel <= 0) return 0f
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
