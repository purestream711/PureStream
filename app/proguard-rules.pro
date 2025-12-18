# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers for debugging crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# LibVLC ProGuard rules
-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.medialibrary.** { *; }
-dontwarn org.videolan.**

# Retrofit and OkHttp
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Gson serialization
-keep class com.google.gson.** { *; }
-keep class com.purestream.data.model.** { *; }
-keepclassmembers class com.purestream.data.model.** {
    <fields>;
    <init>(...);
}

# Room database
-keep class androidx.room.** { *; }
-keep class com.purestream.data.database.** { *; }
-keep class com.purestream.data.database.entities.** { *; }
-keep class com.purestream.data.database.dao.** { *; }

# Keep data model classes for serialization
-keep class com.purestream.data.model.** {
    <fields>;
    <init>(...);
}

# Keep ViewModel classes
-keep class com.purestream.ui.viewmodel.** { *; }

# Keep Repository classes
-keep class com.purestream.data.repository.** { *; }

# Keep API service interfaces
-keep interface com.purestream.data.api.** { *; }

# Compose navigation
-keep class androidx.navigation.** { *; }
-keep class androidx.compose.** { *; }

# Coil image loading
-keep class coil.** { *; }
-dontwarn coil.**

# ZXing QR code library
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.google.zxing.**

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Remove debug logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Keep application class
-keep class com.purestream.PureStreamApplication { *; }

# Keep MainActivity
-keep class com.purestream.MainActivity { *; }