package com.purestream.data.model

import com.purestream.R

enum class Achievement(
    val title: String,
    val description: String,
    val criteria: String,
    val iconResId: Int
) {
    FIRST_FILTER(
        "First Filter",
        "And so it begins...",
        "Filter your first profanity word",
        R.drawable.badge_first_filter
    ),
    LEVELED_UP(
        "Leveled Up",
        "Gaining experience",
        "Achieve your profile's first level up",
        R.drawable.badge_leveled_up
    ),
    FAMILY_PROTECTOR(
        "Family Protector",
        "Guardian of the living room",
        "Watch 5 movies with a R or PG-13 rating",
        R.drawable.badge_family_protector
    ),
    MARATHON_RUNNER(
        "Marathon Runner",
        "Endurance viewer",
        "Watch 10 movies in a week",
        R.drawable.badge_marathon_runner
    ),
    POWER_USER(
        "Power User",
        "Welcome to the club",
        "Become a Pro subscriber",
        R.drawable.badge_power_user
    ),
    CLEAN_SWEEP(
        "Clean Sweep",
        "Strictly business",
        "Use Strict filter for 30 days",
        R.drawable.badge_clean_sweep
    ),
    FILTER_MASTER(
        "Filter Master",
        "Master of content purity",
        "Filter 100 words in a single movie",
        R.drawable.badge_filter_master
    ),
    SILENCE_IS_GOLDEN(
        "Silence is Golden",
        "A true connoisseur of quiet",
        "Filter 1000 total profanity words",
        R.drawable.badge_silence_is_golden
    ),
    MAXED_OUT(
        "Maxed Out",
        "Reached the summit",
        "Reach the maximum level (Level 30)",
        R.drawable.badge_maxed_out
    ),
    COMPLETIONIST(
        "Completionist",
        "Legendary status achieved",
        "Unlock all other achievements",
        R.drawable.badge_completionist
    );
}
