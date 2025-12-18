package com.purestream.data.api

import android.content.Context
import android.os.Build
import android.provider.Settings
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val PLEX_BASE_URL = "https://plex.tv/api/v2/"

    // Default to a safe fallback until init() is called
    private var deviceName: String = getFallbackDeviceName()

    // Get actual Android version (e.g., "12", "11")
    private val androidVersion: String = Build.VERSION.RELEASE ?: "10.0"

    /**
     * Call this from your MainActivity.onCreate or Application.onCreate
     * to set the real user-friendly device name (e.g. "Living Room TV")
     */
    fun init(context: Context) {
        deviceName = getRealDeviceName(context)
        android.util.Log.i("ApiClient", "Device Name initialized as: $deviceName")
    }

    /**
     * Tries to get the user-set device name (Settings > About > Device Name).
     * If not set, falls back to "Manufacturer Model" (e.g. "OnePlus KB2007").
     */
    private fun getRealDeviceName(context: Context): String {
        try {
            // 1. Try to get the user-configured device name (Bluetooth/Network name)
            // This is usually "John's Phone" or "Living Room TV"
            val userDeviceName = if (Build.VERSION.SDK_INT >= 25) {
                Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            } else {
                // Fallback for older devices
                Settings.Secure.getString(context.contentResolver, "bluetooth_name")
            }

            if (!userDeviceName.isNullOrBlank()) {
                return userDeviceName
            }
        } catch (e: Exception) {
            // Permission issues or setting not found, fall back to model
        }

        // 2. Fallback: Just manufacturer name (e.g. "OnePlus", "Google", "onn.")
        return getFallbackDeviceName()
    }

    private fun getFallbackDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        return if (!manufacturer.isNullOrBlank()) {
            manufacturer.replaceFirstChar { it.uppercase() }
        } else {
            "Android TV"
        }
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            val requestBuilder = originalRequest.newBuilder()
                .addHeader("X-Plex-Product", "Pure Stream")
                .addHeader("X-Plex-Version", "1.0")
                .addHeader("X-Plex-Client-Identifier", "com.purestream")
                .addHeader("X-Plex-Platform", "Android")
                .addHeader("X-Plex-Platform-Version", androidVersion) // Updated to use real OS version
                .addHeader("X-Plex-Device", deviceName) // FIXED: Uses actual device name (e.g. Chromecast)
                .addHeader("X-Plex-Device-Name", deviceName)
                .addHeader("Accept", "application/json")
            chain.proceed(requestBuilder.build())
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(PLEX_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val plexApiService: PlexApiService = retrofit.create(PlexApiService::class.java)

    fun createServerApiService(serverUrl: String, isLocal: Boolean = false): PlexApiService {
        // Use shorter timeouts for local connections to detect failures faster
        val timeoutClient = if (isLocal) {
            okHttpClient.newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)  // Faster local detection
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()
        } else {
            okHttpClient.newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS) // Longer for remote
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        val serverRetrofit = Retrofit.Builder()
            .baseUrl(if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/")
            .client(timeoutClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return serverRetrofit.create(PlexApiService::class.java)
    }
}