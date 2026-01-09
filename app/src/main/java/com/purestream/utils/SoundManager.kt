package com.purestream.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import com.purestream.R

class SoundManager private constructor(private val appContext: Context) {
    
    private val hapticManager = HapticManager.getInstance(appContext)
    
    private var themeMediaPlayer: MediaPlayer? = null
    private var currentThemeUrl: String? = null
    private var fadeAnimator: ValueAnimator? = null

    companion object {
        @Volatile
        private var INSTANCE: SoundManager? = null
        private var hasEverPlayedStartupSound = false
        
        fun getInstance(context: Context): SoundManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SoundManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    enum class Sound {
        CLICK, STARTUP, LEVEL_UP, BADGE_SELECT, LEVELUP_CLICK
    }
    
    private val soundPool: SoundPool
    private val soundIds = mutableMapOf<Sound, Int>()
    private var volume = 1.0f
    private var enabled = true
    private var isStartupSoundLoaded = false
    private var startupSoundCallback: (() -> Unit)? = null
    private var hasPlayedStartupSound = false
    
    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(audioAttributes)
            .build()
        
        loadSounds(appContext)
    }
    
    private fun loadSounds(context: Context) {
        try {
            android.util.Log.d("SoundManager", "Starting to load navigation sounds...")
            
            soundIds[Sound.CLICK] = soundPool.load(context, R.raw.click, 1)
            soundIds[Sound.STARTUP] = soundPool.load(context, R.raw.startup_sound, 1)
            soundIds[Sound.LEVEL_UP] = soundPool.load(context, R.raw.levelup_sound, 1)
            soundIds[Sound.BADGE_SELECT] = soundPool.load(context, R.raw.badge_select, 1)
            soundIds[Sound.LEVELUP_CLICK] = soundPool.load(context, R.raw.levelup_click, 1)
            
            // Set up load completion listener
            soundPool.setOnLoadCompleteListener { soundPool, sampleId, status ->
                if (status == 0) {
                    android.util.Log.d("SoundManager", "Sound loaded successfully: $sampleId")
                    // Check if this is the startup sound
                    if (sampleId == soundIds[Sound.STARTUP]) {
                        android.util.Log.d("SoundManager", "Startup sound loaded, triggering callback")
                        isStartupSoundLoaded = true
                        if (!hasEverPlayedStartupSound) {
                            hasPlayedStartupSound = true
                            hasEverPlayedStartupSound = true
                            startupSoundCallback?.invoke()
                        }
                        startupSoundCallback = null // Clear callback after use
                    }
                } else {
                    android.util.Log.e("SoundManager", "Failed to load sound: $sampleId, status: $status")
                }
            }
            
            android.util.Log.d("SoundManager", "Navigation sounds loading initiated. Sound IDs: $soundIds")
        } catch (e: Exception) {
            android.util.Log.e("SoundManager", "Failed to load navigation sounds: ${e.message}", e)
        }
    }
    
    fun playSound(sound: Sound) {
        // Trigger haptic feedback for relevant interaction sounds
        when (sound) {
            Sound.CLICK -> hapticManager.performClickHaptic()
            Sound.LEVEL_UP, Sound.BADGE_SELECT, Sound.LEVELUP_CLICK -> hapticManager.performSuccessHaptic()
            else -> { /* No haptic for other sounds */ }
        }

        if (!enabled) {
            android.util.Log.d("SoundManager", "Sound disabled, not playing: $sound")
            return
        }
        
        android.util.Log.i("SoundManager", "*** PLAYING SOUND: $sound ***")
        playNavigationSound(sound)
    }
    
    private fun playNavigationSound(sound: Sound) {
        try {
            val soundId = soundIds[sound]
            if (soundId != null) {
                // Adjust volume based on specific sound requirements
                val soundVolume = when (sound) {
                    Sound.CLICK, Sound.LEVELUP_CLICK -> volume * 0.05f
                    Sound.LEVEL_UP -> volume * 0.25f
                    Sound.BADGE_SELECT -> volume * 0.5f
                    else -> volume
                }

                android.util.Log.d("SoundManager", "Playing sound: $sound with ID: $soundId")
                val streamId = soundPool.play(soundId, soundVolume, soundVolume, 1, 0, 1.0f)
                if (streamId == 0) {
                    android.util.Log.e("SoundManager", "Failed to play sound $sound - stream ID is 0")
                } else {
                    android.util.Log.d("SoundManager", "Sound $sound playing on stream: $streamId")
                }
            } else {
                android.util.Log.e("SoundManager", "Sound ID not found for: $sound")
            }
        } catch (e: Exception) {
            android.util.Log.e("SoundManager", "Failed to play sound $sound: ${e.message}", e)
        }
    }
    
    
    fun setVolume(newVolume: Float) {
        volume = newVolume.coerceIn(0.0f, 1.0f)
    }
    
    fun setEnabled(isEnabled: Boolean) {
        enabled = isEnabled
        if (!enabled) {
            stopAllSounds()
        }
    }
    
    fun playStartupSoundWhenReady(callback: () -> Unit) {
        if (hasEverPlayedStartupSound) {
            android.util.Log.d("SoundManager", "Startup sound already played this app session, ignoring callback")
            return
        }
        
        if (isStartupSoundLoaded) {
            // Sound already loaded, play immediately
            android.util.Log.d("SoundManager", "Startup sound already loaded, playing immediately")
            hasPlayedStartupSound = true
            hasEverPlayedStartupSound = true
            callback()
        } else {
            // Sound not loaded yet, store callback
            android.util.Log.d("SoundManager", "Startup sound not loaded yet, storing callback")
            startupSoundCallback = callback
        }
    }
    
    fun playThemeMusic(url: String, fadeDuration: Long = 2000L) {
        if (!enabled || url.isBlank()) return
        
        // If already playing the same URL, do nothing
        if (currentThemeUrl == url && themeMediaPlayer?.isPlaying == true) {
             android.util.Log.d("SoundManager", "Theme music already playing for url: $url")
             return
        }

        // Cancel any ongoing fade animation immediately to prevent it from calling releaseMediaPlayer() later
        fadeAnimator?.cancel()
        fadeAnimator = null

        // Immediately release the old player synchronously
        releaseMediaPlayer() 

        try {
            android.util.Log.d("SoundManager", "Starting theme music: $url")
            currentThemeUrl = url
            themeMediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = false
                setVolume(0f, 0f) // Start silent for fade in
                prepareAsync()
                setOnPreparedListener { mp ->
                    mp.start()
                    fadeIn(mp, fadeDuration)
                }
                setOnErrorListener { _, what, extra ->
                    android.util.Log.e("SoundManager", "MediaPlayer error: $what, $extra")
                    true
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SoundManager", "Failed to play theme music: ${e.message}", e)
            currentThemeUrl = null
        }
    }

    fun stopThemeMusic(fadeDuration: Long = 1000L, url: String? = null) {
        // If a specific URL is provided, only stop if it matches current
        if (url != null && currentThemeUrl != url) {
             android.util.Log.d("SoundManager", "Ignoring stop request for $url as current is $currentThemeUrl")
             return
        }

        val mp = themeMediaPlayer ?: return
        if (mp.isPlaying) {
             fadeOut(mp, fadeDuration)
        } else {
             releaseMediaPlayer()
        }
    }

    private fun fadeIn(mp: MediaPlayer, duration: Long) {
        // Run on UI thread to ensure ValueAnimator works
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofFloat(0f, 0.1f).apply { // Max volume 0.1f for background (soft)
            this.duration = duration
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val v = animation.animatedValue as Float
                try {
                    if (mp.isPlaying) mp.setVolume(v, v)
                } catch (e: Exception) {
                    // Ignore if mp released
                }
            }
            start()
        }
    }

    private fun fadeOut(mp: MediaPlayer, duration: Long) {
        // Run on UI thread to ensure ValueAnimator works
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofFloat(0.1f, 0f).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val v = animation.animatedValue as Float
                try {
                    if (mp.isPlaying) mp.setVolume(v, v)
                } catch (e: Exception) {
                    // Ignore
                }
            }
            // On end, release
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    releaseMediaPlayer()
                }
            })
            start()
        }
    }

    private fun releaseMediaPlayer() {
        try {
            if (themeMediaPlayer?.isPlaying == true) {
                themeMediaPlayer?.stop()
            }
            themeMediaPlayer?.release()
        } catch (e: Exception) {
            android.util.Log.e("SoundManager", "Error releasing MediaPlayer: ${e.message}")
        } finally {
            themeMediaPlayer = null
            currentThemeUrl = null
        }
    }

    fun stopAllSounds() {
        soundPool.autoPause()
    }
    
    fun release() {
        stopAllSounds()
        soundPool.release()
        INSTANCE = null
    }
}