# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers for debugging crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations, signatures, and exceptions for reflection
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkExpressionValueIsNotNull(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkParameterIsNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkFieldIsNotNull(...);
    public static void checkReturnedValueIsNotNull(...);
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# LibVLC ProGuard rules
-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.medialibrary.** { *; }
-dontwarn org.videolan.**

# Retrofit
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep all data model classes for Gson serialization
-keep class com.purestream.data.model.** { *; }
-keepclassmembers class com.purestream.data.model.** {
    <fields>;
    <init>(...);
}

# Keep API response classes
-keep class com.purestream.data.api.** { *; }
-keep interface com.purestream.data.api.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep Room database classes
-keep class com.purestream.data.database.** { *; }
-keep class com.purestream.data.database.entities.** { *; }
-keep class com.purestream.data.database.dao.** { *; }

# Keep Room generated classes
-keep class com.purestream.data.database.AppDatabase_Impl { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep class * extends androidx.room.Dao { *; }

# Keep TypeConverters
-keep class com.purestream.data.database.Converters { *; }
-keepclassmembers class com.purestream.data.database.Converters {
    public <methods>;
}

# Keep Repository classes
-keep class com.purestream.data.repository.** { *; }

# Keep Manager classes (CRITICAL for singletons)
-keep class com.purestream.data.manager.** { *; }

# Keep Navigation destinations
-keep class com.purestream.navigation.** { *; }

# Keep Workers (WorkManager uses reflection)
-keep class com.purestream.workers.** { *; }

# AndroidX Lifecycle
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(...);
}
-keepclassmembers class ** extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keepclassmembers class ** extends androidx.lifecycle.AndroidViewModel {
    <init>(...);
}

# Keep ViewModel classes
-keep class com.purestream.ui.viewmodel.** { *; }

# Jetpack Compose
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material.** { *; }
-keep class androidx.compose.material3.** { *; }
-dontwarn androidx.compose.**

# Compose Navigation
-keep class androidx.navigation.** { *; }
-keepnames class androidx.navigation.fragment.NavHostFragment

# Coil image loading
-keep class coil.** { *; }
-keep interface coil.** { *; }
-keep enum coil.** { *; }
-dontwarn coil.**

# ZXing QR code library
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.google.zxing.**

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

# Keep Parcelables
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Google Play Billing
-keep class com.android.billingclient.api.** { *; }
-keep class com.purestream.data.billing.** { *; }

# Keep custom Application class
-keep class com.purestream.PureStreamApplication { *; }

# Keep MainActivity
-keep class com.purestream.MainActivity { *; }

# Keep all native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep all constructors used by reflection
-keepclassmembers class * {
    public <init>(...);
}

# Keep custom views
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# Keep crash reporting info
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Gemini AI (if you're using it)
-keep class com.google.ai.client.generativeai.** { *; }
-dontwarn com.google.ai.client.generativeai.**

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(...);
}
-keep class androidx.work.** { *; }

# Keep profanity filter classes
-keep class com.purestream.profanity.** { *; }

# Keep utility classes
-keep class com.purestream.utils.** { *; }
