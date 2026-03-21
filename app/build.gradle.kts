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
// Self-hosted backend
val selfhostedUrl: String = localProps["supabase.selfhosted.url"] ?: ""
val selfhostedAnonKey: String = localProps["supabase.selfhosted.anon.key"] ?: ""
val selfhostedSignupSecret: String = localProps["supabase.selfhosted.signup.secret"] ?: ""

// --- Multi-layer build-time encryption ---
// 32-byte master seed (split into two halves XOR'd together at runtime in C)
val masterSeedA = byteArrayOf(
    0x3A, 0xF1.toByte(), 0x82.toByte(), 0x4D, 0xC6.toByte(), 0x19, 0xE7.toByte(), 0x5B,
    0x90.toByte(), 0x2C, 0xD8.toByte(), 0x63, 0xAB.toByte(), 0x74, 0x0E, 0xF5.toByte(),
    0x48, 0xBC.toByte(), 0x31, 0x9A.toByte(), 0xE2.toByte(), 0x57, 0x0F, 0xC4.toByte(),
    0x86.toByte(), 0x6D, 0xA1.toByte(), 0x38, 0xDB.toByte(), 0x14, 0x7F, 0xE9.toByte()
)
val masterSeedB = byteArrayOf(
    0x71, 0x8C.toByte(), 0xD5.toByte(), 0x22, 0xA9.toByte(), 0x6E, 0x43, 0xB0.toByte(),
    0xFE.toByte(), 0x15, 0x97.toByte(), 0x5A, 0xC8.toByte(), 0x03, 0x64, 0x8F.toByte(),
    0x2D, 0xE6.toByte(), 0x5C, 0xF3.toByte(), 0xAA.toByte(), 0x39, 0x76, 0x81.toByte(),
    0xBD.toByte(), 0x4A, 0xDE.toByte(), 0x07, 0x95.toByte(), 0x6B, 0x10, 0xA4.toByte()
)
// Actual master seed = A XOR B
val masterSeed = ByteArray(32) { i -> (masterSeedA[i].toInt() xor masterSeedB[i].toInt()).toByte() }

// AES S-box for substitution step
val SBOX = intArrayOf(
    0x63,0x7c,0x77,0x7b,0xf2,0x6b,0x6f,0xc5,0x30,0x01,0x67,0x2b,0xfe,0xd7,0xab,0x76,
    0xca,0x82,0xc9,0x7d,0xfa,0x59,0x47,0xf0,0xad,0xd4,0xa2,0xaf,0x9c,0xa4,0x72,0xc0,
    0xb7,0xfd,0x93,0x26,0x36,0x3f,0xf7,0xcc,0x34,0xa5,0xe5,0xf1,0x71,0xd8,0x31,0x15,
    0x04,0xc7,0x23,0xc3,0x18,0x96,0x05,0x9a,0x07,0x12,0x80,0xe2,0xeb,0x27,0xb2,0x75,
    0x09,0x83,0x2c,0x1a,0x1b,0x6e,0x5a,0xa0,0x52,0x3b,0xd6,0xb3,0x29,0xe3,0x2f,0x84,
    0x53,0xd1,0x00,0xed,0x20,0xfc,0xb1,0x5b,0x6a,0xcb,0xbe,0x39,0x4a,0x4c,0x58,0xcf,
    0xd0,0xef,0xaa,0xfb,0x43,0x4d,0x33,0x85,0x45,0xf9,0x02,0x7f,0x50,0x3c,0x9f,0xa8,
    0x51,0xa3,0x40,0x8f,0x92,0x9d,0x38,0xf5,0xbc,0xb6,0xda,0x21,0x10,0xff,0xf3,0xd2,
    0xcd,0x0c,0x13,0xec,0x5f,0x97,0x44,0x17,0xc4,0xa7,0x7e,0x3d,0x64,0x5d,0x19,0x73,
    0x60,0x81,0x4f,0xdc,0x22,0x2a,0x90,0x88,0x46,0xee,0xb8,0x14,0xde,0x5e,0x0b,0xdb,
    0xe0,0x32,0x3a,0x0a,0x49,0x06,0x24,0x5c,0xc2,0xd3,0xac,0x62,0x91,0x95,0xe4,0x79,
    0xe7,0xc8,0x37,0x6d,0x8d,0xd5,0x4e,0xa9,0x6c,0x56,0xf4,0xea,0x65,0x7a,0xae,0x08,
    0xba,0x78,0x25,0x2e,0x1c,0xa6,0xb4,0xc6,0xe8,0xdd,0x74,0x1f,0x4b,0xbd,0x8b,0x8a,
    0x70,0x3e,0xb5,0x66,0x48,0x03,0xf6,0x0e,0x61,0x35,0x57,0xb9,0x86,0xc1,0x1d,0x9e,
    0xe1,0xf8,0x98,0x11,0x69,0xd9,0x8e,0x94,0x9b,0x1e,0x87,0xe9,0xce,0x55,0x28,0xdf,
    0x8c,0xa1,0x89,0x0d,0xbf,0xe6,0x42,0x68,0x41,0x99,0x2d,0x0f,0xb0,0x54,0xbb,0x16
)

// Derive a 16-byte key from master seed + index
fun deriveKey(seed: ByteArray, index: Int): ByteArray {
    val key = ByteArray(16)
    for (i in 0 until 16) {
        val s = seed[(index * 16 + i) % seed.size].toInt() and 0xFF
        val mix = seed[(i * 7 + index * 3 + 5) % seed.size].toInt() and 0xFF
        key[i] = ((s xor mix xor (index + i * 13 + 0xA5)) and 0xFF).toByte()
    }
    return key
}

// CRC-8 for integrity check
fun crc8(data: ByteArray): Int {
    var crc = 0xFF
    for (b in data) {
        crc = crc xor (b.toInt() and 0xFF)
        for (bit in 0 until 8) {
            crc = if (crc and 0x80 != 0) (crc shl 1) xor 0x1D else crc shl 1
            crc = crc and 0xFF
        }
    }
    return crc
}

// 3-step encrypt: XOR with derived key -> S-box substitution -> bit rotation
fun multiLayerEncrypt(plain: String, secretIndex: Int): Map<String, String> {
    val bytes = plain.toByteArray(Charsets.UTF_8)
    val totalLen = bytes.size
    val check = crc8(bytes)
    val key = deriveKey(masterSeed, secretIndex)

    // Split into even/odd indexed bytes
    val partA = ByteArray((totalLen + 1) / 2) // even indices
    val partB = ByteArray(totalLen / 2)        // odd indices
    for (i in bytes.indices) {
        if (i % 2 == 0) partA[i / 2] = bytes[i]
        else partB[i / 2] = bytes[i]
    }

    fun encryptPart(part: ByteArray): String {
        val out = ByteArray(part.size)
        for (i in part.indices) {
            // Step 1: XOR with derived key
            var v = (part[i].toInt() and 0xFF) xor (key[i % key.size].toInt() and 0xFF)
            // Step 2: S-box substitution
            v = SBOX[v and 0xFF]
            // Step 3: bit rotation left by 3
            v = ((v shl 3) or (v ushr 5)) and 0xFF
            out[i] = v.toByte()
        }
        return out.joinToString(",") { "0x${(it.toInt() and 0xFF).toString(16).padStart(2, '0')}" }
    }

    return mapOf(
        "A" to encryptPart(partA),
        "A_LEN" to partA.size.toString(),
        "B" to encryptPart(partB),
        "B_LEN" to partB.size.toString(),
        "TOTAL_LEN" to totalLen.toString(),
        "CHECK" to check.toString()
    )
}

android {
    namespace = "com.pocketpass.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pocketpass.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "1.7.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    flavorDimensions += "backend"
    productFlavors {
        create("selfhosted") {
            dimension = "backend"
        }
    }

    // Inject multi-layer encrypted secrets into CMake (6 defines per secret, 18 total)
    productFlavors.configureEach {
        if (name != "selfhosted") return@configureEach
        val urlEnc = multiLayerEncrypt(selfhostedUrl, 0)
        val keyEnc = multiLayerEncrypt(selfhostedAnonKey, 1)
        val secretEnc = multiLayerEncrypt(selfhostedSignupSecret, 2)
        externalNativeBuild {
            cmake {
                arguments(
                    "-DENC_URL_A=${urlEnc["A"]}", "-DENC_URL_A_LEN=${urlEnc["A_LEN"]}",
                    "-DENC_URL_B=${urlEnc["B"]}", "-DENC_URL_B_LEN=${urlEnc["B_LEN"]}",
                    "-DENC_URL_TOTAL_LEN=${urlEnc["TOTAL_LEN"]}", "-DENC_URL_CHECK=${urlEnc["CHECK"]}",
                    "-DENC_KEY_A=${keyEnc["A"]}", "-DENC_KEY_A_LEN=${keyEnc["A_LEN"]}",
                    "-DENC_KEY_B=${keyEnc["B"]}", "-DENC_KEY_B_LEN=${keyEnc["B_LEN"]}",
                    "-DENC_KEY_TOTAL_LEN=${keyEnc["TOTAL_LEN"]}", "-DENC_KEY_CHECK=${keyEnc["CHECK"]}",
                    "-DENC_SECRET_A=${secretEnc["A"]}", "-DENC_SECRET_A_LEN=${secretEnc["A_LEN"]}",
                    "-DENC_SECRET_B=${secretEnc["B"]}", "-DENC_SECRET_B_LEN=${secretEnc["B_LEN"]}",
                    "-DENC_SECRET_TOTAL_LEN=${secretEnc["TOTAL_LEN"]}", "-DENC_SECRET_CHECK=${secretEnc["CHECK"]}"
                )
            }
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("pocketpass-release.jks")
            storePassword = localProps["keystore.password"] ?: ""
            keyAlias = localProps["keystore.alias"] ?: "pocketpass"
            keyPassword = localProps["keystore.key.password"] ?: ""
        }
    }

    buildTypes {
        debug {
            externalNativeBuild {
                cmake {
                    arguments("-DALLOW_DEBUG=1")
                }
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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

    // Supabase
    implementation(platform("io.github.jan-tennert.supabase:bom:3.3.0"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")

    // Ktor engine (required by supabase-kt — OkHttp supports WebSockets for Realtime)
    implementation("io.ktor:ktor-client-okhttp:3.1.1")

    // KotlinX Serialization (used by supabase-kt)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // WorkManager for periodic background sync (SpotPass)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Tink for client-side encryption (AES-256-GCM, X25519, HKDF)
    implementation("com.google.crypto.tink:tink-android:1.16.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
