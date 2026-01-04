package com.purestream.data.demo

import com.purestream.data.model.*

object DemoData {
    const val DEMO_AUTH_TOKEN = "DEMO_MODE_TOKEN_NOT_REAL"
    val DEMO_VIDEO_RESOURCE_ID = com.purestream.R.raw.demo_video

    // Demo video duration: 7 seconds (7000ms)
    private const val DEMO_VIDEO_DURATION = 7000L

    // Helper function to create drawable resource name
    // Instead of URIs, we store just the drawable name which will be resolved at runtime
    // This matches how profile avatars work
    private fun getDrawableName(drawableName: String): String {
        return drawableName
    }

    // Demo Featured Movie for Home Screen - Cyber Runner
    val DEMO_FEATURED_MOVIE = Movie(
        ratingKey = "demo_cyber_runner",
        key = "/library/metadata/demo_cyber_runner",
        title = "Cyber Runner",
        summary = "In a dystopian future where corporations control everything, an elite hacker must infiltrate the world's most secure network to expose a conspiracy that threatens humanity's freedom. Racing against time and ruthless cyber-assassins, they discover the truth is more dangerous than anyone imagined.",
        thumbUrl = getDrawableName("demo_cyber_runner"),
        artUrl = getDrawableName("demo_cyber_runner_background"),
        logoUrl = getDrawableName("demo_cyber_runner_logo"),
        year = 2024,
        duration = DEMO_VIDEO_DURATION,
        rating = 9.2f,
        contentRating = "R",
        studio = "NeonWave Studios",
        profanityLevel = ProfanityLevel.HIGH
    )

    // Demo Movies - 8 diverse titles with your custom posters
    val DEMO_MOVIES = listOf(
        Movie(
            ratingKey = "demo_cyber_runner",
            key = "/library/metadata/demo_cyber_runner",
            title = "Cyber Runner",
            summary = "In a dystopian future where corporations control everything, an elite hacker must infiltrate the world's most secure network to expose a conspiracy that threatens humanity's freedom. Racing against time and ruthless cyber-assassins, they discover the truth is more dangerous than anyone imagined.",
            thumbUrl = getDrawableName("demo_cyber_runner"),
            artUrl = getDrawableName("demo_cyber_runner_background"),
            logoUrl = getDrawableName("demo_cyber_runner_logo"),
            year = 2024,
            duration = DEMO_VIDEO_DURATION,
            rating = 9.2f,
            contentRating = "R",
            studio = "NeonWave Studios",
            profanityLevel = ProfanityLevel.HIGH
        ),
        Movie(
            ratingKey = "demo_bubble_trouble",
            key = "/library/metadata/demo_bubble_trouble",
            title = "Bubble Trouble",
            summary = "When a brilliant but clumsy scientist's experiment goes hilariously wrong, the entire city becomes filled with giant, indestructible bubbles. Now he must team up with an unlikely group of misfits to pop the problem before the whole world is trapped in a sticky situation.",
            thumbUrl = getDrawableName("demo_bubble_trouble"),
            artUrl = getDrawableName("demo_bubble_trouble"),
            year = 2023,
            duration = DEMO_VIDEO_DURATION,
            rating = 7.8f,
            contentRating = "PG",
            studio = "Bubble Studios",
            profanityLevel = ProfanityLevel.NONE
        ),
        Movie(
            ratingKey = "demo_coffee_and_chance",
            key = "/library/metadata/demo_coffee_and_chance",
            title = "Coffee & Chance",
            summary = "Two strangers keep meeting by chance at the same coffee shop over the course of a year. As seasons change and lattes are shared, they slowly realize that fate might be giving them more than just a caffeine fix. A heartwarming romance about timing, connection, and the courage to take a chance on love.",
            thumbUrl = getDrawableName("demo_coffee_and_chance"),
            artUrl = getDrawableName("demo_coffee_and_chance"),
            year = 2024,
            duration = DEMO_VIDEO_DURATION,
            rating = 8.5f,
            contentRating = "PG-13",
            studio = "Autumn Films",
            profanityLevel = ProfanityLevel.LOW
        ),
        Movie(
            ratingKey = "demo_echoes_in_the_mist",
            key = "/library/metadata/demo_echoes_in_the_mist",
            title = "Echoes in the Mist",
            summary = "A paranormal investigator arrives at a remote coastal town where residents report hearing voices in the fog. As she delves deeper into the mystery, she uncovers a century-old tragedy that refuses to stay buried. The past and present collide in this atmospheric supernatural thriller.",
            thumbUrl = getDrawableName("demo_echoes_in_the_mist"),
            artUrl = getDrawableName("demo_echoes_in_the_mist"),
            year = 2023,
            duration = DEMO_VIDEO_DURATION,
            rating = 8.1f,
            contentRating = "PG-13",
            studio = "Midnight Pictures",
            profanityLevel = ProfanityLevel.MEDIUM
        ),
        Movie(
            ratingKey = "demo_the_attic_door",
            key = "/library/metadata/demo_the_attic_door",
            title = "The Attic Door",
            summary = "When a family moves into their inherited Victorian mansion, their young daughter discovers a locked door in the attic. Against her parents' warnings, she finds the key and opens it, unleashing something that has been waiting in the darkness for decades. Some doors should never be opened.",
            thumbUrl = getDrawableName("demo_the_attic_door"),
            artUrl = getDrawableName("demo_the_attic_door"),
            year = 2024,
            duration = DEMO_VIDEO_DURATION,
            rating = 7.6f,
            contentRating = "R",
            studio = "DarkHouse Entertainment",
            profanityLevel = ProfanityLevel.MEDIUM
        ),
        Movie(
            ratingKey = "demo_the_lost_kingdom",
            key = "/library/metadata/demo_the_lost_kingdom",
            title = "The Lost Kingdom",
            summary = "An archaeologist and a treasure hunter must put aside their rivalry when they discover a map leading to a legendary kingdom thought to be myth. Racing against a ruthless collector, they journey through treacherous jungles and ancient temples to find a civilization lost to time.",
            thumbUrl = getDrawableName("demo_the_lost_kingdom"),
            artUrl = getDrawableName("demo_the_lost_kingdom"),
            year = 2023,
            duration = DEMO_VIDEO_DURATION,
            rating = 8.7f,
            contentRating = "PG-13",
            studio = "Adventure Unlimited",
            profanityLevel = ProfanityLevel.LOW
        ),
        Movie(
            ratingKey = "demo_the_quiet_note",
            key = "/library/metadata/demo_the_quiet_note",
            title = "The Quiet Note",
            summary = "A once-celebrated pianist who lost her hearing in an accident finds an old letter in her grandmother's attic, revealing a family secret that spans three generations. As she pieces together the past, she discovers that some melodies transcend sound and speak directly to the heart.",
            thumbUrl = getDrawableName("demo_the_quiet_note"),
            artUrl = getDrawableName("demo_the_quiet_note"),
            year = 2024,
            duration = DEMO_VIDEO_DURATION,
            rating = 8.9f,
            contentRating = "PG",
            studio = "Harmony Productions",
            profanityLevel = ProfanityLevel.NONE
        ),
        Movie(
            ratingKey = "demo_wild_horizons",
            key = "/library/metadata/demo_wild_horizons",
            title = "Wild Horizons",
            summary = "A wildlife photographer embarks on a solo expedition to document endangered species in the Amazon rainforest. When his equipment fails and he becomes lost, he must rely on his survival skills and the wisdom of indigenous guides to make it out alive while capturing the journey of a lifetime.",
            thumbUrl = getDrawableName("demo_wild_horizons"),
            artUrl = getDrawableName("demo_wild_horizons"),
            year = 2023,
            duration = DEMO_VIDEO_DURATION,
            rating = 8.3f,
            contentRating = "PG-13",
            studio = "Wild Earth Films",
            profanityLevel = ProfanityLevel.LOW
        )
    )

    // Demo TV Shows - Same 8 titles adapted as series
    val DEMO_TV_SHOWS = listOf(
        TvShow(
            ratingKey = "demo_show_cyber_runner",
            key = "/library/metadata/demo_show_cyber_runner",
            title = "Cyber Runner",
            summary = "In a dystopian future where corporations control everything, an elite team of hackers fights to expose conspiracies and protect humanity's freedom. Each episode brings new cyber threats, thrilling heists, and dangerous revelations in the digital underworld.",
            thumbUrl = getDrawableName("demo_cyber_runner"),
            artUrl = getDrawableName("demo_cyber_runner_background"),
            year = 2024,
            seasonCount = 2,
            episodeCount = 20,
            rating = 9.0f,
            contentRating = "TV-MA",
            studio = "NeonWave Studios",
            profanityLevel = ProfanityLevel.HIGH
        ),
        TvShow(
            ratingKey = "demo_show_bubble_trouble",
            key = "/library/metadata/demo_show_bubble_trouble",
            title = "Bubble Trouble",
            summary = "A quirky scientist's lab becomes the source of weekly chaos when experiments go wrong in the most hilarious ways. With the help of his faithful friends, he tackles each bubbly disaster while learning valuable lessons about science, friendship, and cleaning up your messes.",
            thumbUrl = getDrawableName("demo_bubble_trouble"),
            artUrl = getDrawableName("demo_bubble_trouble"),
            year = 2023,
            seasonCount = 3,
            episodeCount = 36,
            rating = 7.9f,
            contentRating = "TV-G",
            studio = "Bubble Studios",
            profanityLevel = ProfanityLevel.NONE
        ),
        TvShow(
            ratingKey = "demo_show_coffee_and_chance",
            key = "/library/metadata/demo_show_coffee_and_chance",
            title = "Coffee & Chance",
            summary = "Follow the intertwining love stories of regulars at a cozy neighborhood coffee shop. Each season explores how chance encounters, missed connections, and second chances shape the romantic lives of baristas and customers alike.",
            thumbUrl = getDrawableName("demo_coffee_and_chance"),
            artUrl = getDrawableName("demo_coffee_and_chance"),
            year = 2024,
            seasonCount = 2,
            episodeCount = 16,
            rating = 8.4f,
            contentRating = "TV-14",
            studio = "Autumn Films",
            profanityLevel = ProfanityLevel.LOW
        ),
        TvShow(
            ratingKey = "demo_show_echoes_in_the_mist",
            key = "/library/metadata/demo_show_echoes_in_the_mist",
            title = "Echoes in the Mist",
            summary = "A paranormal investigation team travels to haunted locations across the country, uncovering dark histories and restless spirits. Each episode features a new mystery where the boundary between the living and the dead grows dangerously thin.",
            thumbUrl = getDrawableName("demo_echoes_in_the_mist"),
            artUrl = getDrawableName("demo_echoes_in_the_mist"),
            year = 2023,
            seasonCount = 4,
            episodeCount = 48,
            rating = 8.2f,
            contentRating = "TV-14",
            studio = "Midnight Pictures",
            profanityLevel = ProfanityLevel.MEDIUM
        ),
        TvShow(
            ratingKey = "demo_show_the_attic_door",
            key = "/library/metadata/demo_show_the_attic_door",
            title = "The Attic Door",
            summary = "An anthology horror series where each episode features a different family moving into a house with a mysterious attic. What they find behind the door changes their lives forever, revealing that every home has secrets best left undiscovered.",
            thumbUrl = getDrawableName("demo_the_attic_door"),
            artUrl = getDrawableName("demo_the_attic_door"),
            year = 2024,
            seasonCount = 1,
            episodeCount = 10,
            rating = 7.7f,
            contentRating = "TV-MA",
            studio = "DarkHouse Entertainment",
            profanityLevel = ProfanityLevel.MEDIUM
        ),
        TvShow(
            ratingKey = "demo_show_the_lost_kingdom",
            key = "/library/metadata/demo_show_the_lost_kingdom",
            title = "The Lost Kingdom",
            summary = "An adventure series following a team of archaeologists as they search for lost civilizations around the globe. From ancient temples to underwater ruins, each discovery brings them closer to uncovering the greatest mystery of all.",
            thumbUrl = getDrawableName("demo_the_lost_kingdom"),
            artUrl = getDrawableName("demo_the_lost_kingdom"),
            year = 2023,
            seasonCount = 3,
            episodeCount = 30,
            rating = 8.6f,
            contentRating = "TV-PG",
            studio = "Adventure Unlimited",
            profanityLevel = ProfanityLevel.LOW
        ),
        TvShow(
            ratingKey = "demo_show_the_quiet_note",
            key = "/library/metadata/demo_show_the_quiet_note",
            title = "The Quiet Note",
            summary = "A heartfelt drama about students at a prestigious music conservatory. Through triumphs and setbacks, they discover that the most important music comes from following your heart and supporting those who share your passion.",
            thumbUrl = getDrawableName("demo_the_quiet_note"),
            artUrl = getDrawableName("demo_the_quiet_note"),
            year = 2024,
            seasonCount = 2,
            episodeCount = 24,
            rating = 8.8f,
            contentRating = "TV-PG",
            studio = "Harmony Productions",
            profanityLevel = ProfanityLevel.NONE
        ),
        TvShow(
            ratingKey = "demo_show_wild_horizons",
            key = "/library/metadata/demo_show_wild_horizons",
            title = "Wild Horizons",
            summary = "Join wildlife photographers and conservationists on breathtaking expeditions to document endangered species and pristine ecosystems. Each episode showcases the beauty of nature and the urgent need to protect it for future generations.",
            thumbUrl = getDrawableName("demo_wild_horizons"),
            artUrl = getDrawableName("demo_wild_horizons"),
            year = 2023,
            seasonCount = 4,
            episodeCount = 40,
            rating = 8.5f,
            contentRating = "TV-G",
            studio = "Wild Earth Films",
            profanityLevel = ProfanityLevel.LOW
        )
    )

    // Demo Libraries
    val DEMO_LIBRARIES = listOf(
        PlexLibrary(
            key = "demo_library_movies",
            title = "Demo Movies",
            type = "movie",
            agent = "com.plexapp.agents.themoviedb",
            scanner = "Plex Movie Scanner",
            language = "en",
            uuid = "demo-uuid-movies-12345",
            updatedAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        ),
        PlexLibrary(
            key = "demo_library_shows",
            title = "Demo TV Shows",
            type = "show",
            agent = "com.plexapp.agents.thetvdb",
            scanner = "Plex Series Scanner",
            language = "en",
            uuid = "demo-uuid-shows-67890",
            updatedAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )
    )

    // Demo subtitle analysis with "fuck" profanity mute
    // Based on 7-second video, place the profanity mute around 3 seconds in
    val DEMO_SUBTITLE_ANALYSIS = SubtitleAnalysisResult(
        movieTitle = "Demo Video",
        episodeInfo = null,
        subtitleFileName = "demo_video_subs.srt",
        profanityLevel = ProfanityLevel.HIGH,
        detectedWords = listOf("fuck"),
        totalWordsCount = 42,
        profanityWordsCount = 1,
        profanityPercentage = 2.4f // 1/42 * 100
    )

    // Time range for muting the profanity in the demo video
    val DEMO_MUTING_TIMESTAMPS = listOf(
        TimeRange(
            startTime = 3000L, // 3 seconds
            endTime = 3500L    // 3.5 seconds (500ms duration for "fuck")
        )
    )

    // Map of demo movie ID to SubtitleAnalysisResult
    val DEMO_SUBTITLE_ANALYSIS_MAP = mapOf(
        "demo_cyber_runner" to SubtitleAnalysisResult(
            movieTitle = "Cyber Runner",
            episodeInfo = null,
            subtitleFileName = "cyber_runner_subs.srt",
            profanityLevel = ProfanityLevel.HIGH,
            detectedWords = listOf("fuck", "shit", "damn", "ass", "bastard", "hell", "bitch", "dick", "piss", "crap", "bloody"),
            totalWordsCount = 12450,
            profanityWordsCount = 42,
            profanityPercentage = 0.34f
        ),
        "demo_bubble_trouble" to SubtitleAnalysisResult(
            movieTitle = "Bubble Trouble",
            episodeInfo = null,
            subtitleFileName = "bubble_trouble_subs.srt",
            profanityLevel = ProfanityLevel.NONE,
            detectedWords = emptyList(),
            totalWordsCount = 8500,
            profanityWordsCount = 0,
            profanityPercentage = 0.0f
        ),
        "demo_coffee_and_chance" to SubtitleAnalysisResult(
            movieTitle = "Coffee & Chance",
            episodeInfo = null,
            subtitleFileName = "coffee_chance_subs.srt",
            profanityLevel = ProfanityLevel.LOW,
            detectedWords = listOf("hell", "damn", "crap"),
            totalWordsCount = 10200,
            profanityWordsCount = 5,
            profanityPercentage = 0.05f
        ),
        "demo_echoes_in_the_mist" to SubtitleAnalysisResult(
            movieTitle = "Echoes in the Mist",
            episodeInfo = null,
            subtitleFileName = "echoes_mist_subs.srt",
            profanityLevel = ProfanityLevel.MEDIUM,
            detectedWords = listOf("shit", "damn", "hell", "ass", "bloody"),
            totalWordsCount = 11300,
            profanityWordsCount = 15,
            profanityPercentage = 0.13f
        ),
        "demo_the_attic_door" to SubtitleAnalysisResult(
            movieTitle = "The Attic Door",
            episodeInfo = null,
            subtitleFileName = "attic_door_subs.srt",
            profanityLevel = ProfanityLevel.MEDIUM,
            detectedWords = listOf("shit", "bitch", "hell", "damn", "asshole"),
            totalWordsCount = 9800,
            profanityWordsCount = 22,
            profanityPercentage = 0.22f
        ),
        "demo_the_lost_kingdom" to SubtitleAnalysisResult(
            movieTitle = "The Lost Kingdom",
            episodeInfo = null,
            subtitleFileName = "lost_kingdom_subs.srt",
            profanityLevel = ProfanityLevel.LOW,
            detectedWords = listOf("damn", "hell"),
            totalWordsCount = 13500,
            profanityWordsCount = 8,
            profanityPercentage = 0.06f
        ),
        "demo_the_quiet_note" to SubtitleAnalysisResult(
            movieTitle = "The Quiet Note",
            episodeInfo = null,
            subtitleFileName = "quiet_note_subs.srt",
            profanityLevel = ProfanityLevel.NONE,
            detectedWords = emptyList(),
            totalWordsCount = 7600,
            profanityWordsCount = 0,
            profanityPercentage = 0.0f
        ),
        "demo_wild_horizons" to SubtitleAnalysisResult(
            movieTitle = "Wild Horizons",
            episodeInfo = null,
            subtitleFileName = "wild_horizons_subs.srt",
            profanityLevel = ProfanityLevel.LOW,
            detectedWords = listOf("damn", "bloody", "hell"),
            totalWordsCount = 8900,
            profanityWordsCount = 6,
            profanityPercentage = 0.07f
        )
    )

    fun getDemoSubtitleAnalysis(movieId: String): SubtitleAnalysisResult? {
        return DEMO_SUBTITLE_ANALYSIS_MAP[movieId]
    }

    fun isDemoToken(token: String?): Boolean = token == DEMO_AUTH_TOKEN
}
