# ── PocketPass ProGuard Rules ──

# ── kotlinx.serialization ──
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all @Serializable classes and their serializers
-keep,includedescriptorclasses class com.pocketpass.app.data.**$$serializer { *; }
-keepclassmembers class com.pocketpass.app.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.pocketpass.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Gson ──
-keepattributes Signature
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Keep data classes used with Gson
-keep class com.pocketpass.app.data.IgdbGame { *; }
-keep class com.pocketpass.app.data.IgdbGameResponse { *; }
-keep class com.pocketpass.app.data.PuzzleProgress { *; }
-keep class com.pocketpass.app.service.ExchangePayload { *; }

# ── Supabase / Ktor / OkHttp ──
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# ── Room ──
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# ── SceneView / Filament ──
-keep class com.google.android.filament.** { *; }
-keep class io.github.sceneview.** { *; }

# ── JNI native methods (NativeKeys) ──
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.pocketpass.app.data.NativeKeys { *; }

# ── Tink (crypto) ──
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# ── Accompanist ──
-dontwarn com.google.accompanist.**

# ── Compose ──
-dontwarn androidx.compose.**

# ── Kotlin ──
-dontwarn kotlin.**
-dontwarn kotlinx.**

# ── Strip debug/verbose/info logs in release builds ──
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
