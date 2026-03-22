-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.pocketpass.app.data.**$$serializer { *; }
-keepclassmembers class com.pocketpass.app.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.pocketpass.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keepattributes Signature
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

-keep class com.pocketpass.app.data.** { *; }
-keep class com.pocketpass.app.service.ExchangePayload { *; }

-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

-keep class com.google.android.filament.** { *; }
-keep class io.github.sceneview.** { *; }

-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.pocketpass.app.data.NativeKeys { *; }

-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

-dontwarn com.google.accompanist.**

-dontwarn androidx.compose.**

-keep class com.pocketpass.app.util.Screen { *; }
-keep class com.pocketpass.app.util.Screen$* { *; }

-dontwarn kotlin.**
-dontwarn kotlinx.**

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
