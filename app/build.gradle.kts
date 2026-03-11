plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Read local.properties without java.util.Properties (unavailable in AGP 9 Kotlin DSL)
val localPropsFile = rootProject.file("local.properties")
val localProps: Map<String, String> = if (localPropsFile.exists()) {
    localPropsFile.readLines()
        .filter { it.contains("=") && !it.trimStart().startsWith("#") }
        .associate {
            val (key, value) = it.split("=", limit = 2)
            key.trim() to value.trim()
        }
} else emptyMap()
val supabaseUrl: String = localProps["supabase.url"] ?: ""
val supabaseAnonKey: String = localProps["supabase.anon.key"] ?: ""

// XOR-encrypt a string at build time so no plaintext key appears in the .so binary.
// Returns a comma-separated list of byte values for a C array initializer.
val xorMask = byteArrayOf(0x5A, 0xC3.toByte(), 0x7E, 0x91.toByte(), 0xF0.toByte(), 0x2D, 0xB8.toByte(), 0x64)
fun xorEncrypt(plain: String): String {
    val bytes = plain.toByteArray(Charsets.UTF_8)
    return bytes.mapIndexed { i, b ->
        val encrypted = (b.toInt() xor xorMask[i % xorMask.size].toInt()) and 0xFF
        "0x${encrypted.toString(16).padStart(2, '0')}"
    }.joinToString(",")
}
val encSupabaseUrl = xorEncrypt(supabaseUrl)
val encSupabaseAnonKey = xorEncrypt(supabaseAnonKey)

android {
    namespace = "com.pocketpass.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pocketpass.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }

        externalNativeBuild {
            cmake {
                arguments(
                    "-DENC_SUPABASE_URL=$encSupabaseUrl",
                    "-DENC_SUPABASE_URL_LEN=${supabaseUrl.length}",
                    "-DENC_SUPABASE_ANON_KEY=$encSupabaseAnonKey",
                    "-DENC_SUPABASE_ANON_KEY_LEN=${supabaseAnonKey.length}"
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "**/*.map"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.activity:activity-compose:1.7.2")
    implementation(platform("androidx.compose:compose-bom:2023.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // JSON Serialization
    implementation("com.google.code.gson:gson:2.10.1")

    // Permissions Helper
    implementation("com.google.accompanist:accompanist-permissions:0.31.2-alpha")

    // Modern WebKit for WebViewAssetLoader
    implementation("androidx.webkit:webkit:1.9.0")

    // DataStore for saving local Mii data
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Room Database
    val room_version = "2.7.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // Coil for async image loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // SceneView for 3D Mii rendering (Filament + Compose)
    implementation("io.github.sceneview:sceneview:2.2.1")

    // ZXing for QR code generation and scanning
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Supabase
    implementation(platform("io.github.jan-tennert.supabase:bom:3.3.0"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")

    // Ktor engine (required by supabase-kt — OkHttp supports WebSockets for Realtime)
    implementation("io.ktor:ktor-client-okhttp:3.1.1")

    // KotlinX Serialization (used by supabase-kt)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // WorkManager for periodic background sync (SpotPass)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
