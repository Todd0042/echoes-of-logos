# VoiceRPG: Echoes of the Logos - ProGuard & R8 Optimization Rules

# 1. Attribute Preservation for Reflection, Serialization, and Stack Traces
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 2. Gson Serialization Engine
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# 3. Game Data Models, Persistence & Save Serialization
# Keep all data models so reflection/Gson does not strip fields or obfuscate property names
-keep class com.voicerpg.android.model.** {
    <fields>;
    <methods>;
}

# Keep enum definitions and their built-in value methods
-keepclassmembers enum com.voicerpg.android.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

# 4. Persistence Managers & Atomic Operations
-keep class com.voicerpg.android.engine.SaveManager { *; }
-keep class androidx.core.util.AtomicFile { *; }

# 5. Audio & Speech Subsystems
-keep class com.voicerpg.android.audio.** { *; }

# 6. Jetpack Compose & AndroidX Lifecycle
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

# 7. Kotlin Coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class * {
    @kotlin.jvm.JvmField *;
}
