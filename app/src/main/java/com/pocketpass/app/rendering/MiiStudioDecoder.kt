package com.pocketpass.app.rendering

import android.util.Base64
import android.util.Log
import kotlin.math.pow

/**
 * Decodes Mii Studio format data (base64-encoded, XOR-scrambled 47 bytes)
 * and extracts the favorite color index (0-11).
 */
object MiiStudioDecoder {
    private const val TAG = "MiiStudioDecoder"

    /** Default color index (blue) used when decoding fails. */
    private const val DEFAULT_COLOR_INDEX = 5

    /**
     * 12 favorite colors in linear-space RGBA (converted from sRGB).
     * sRGB → linear: L = ((sRGB/255)^2.2)
     */
    val FAVORITE_COLORS: Array<FloatArray> = arrayOf(
        // 0: Red     #CC0000
        floatArrayOf(srgbToLinear(0xCC), srgbToLinear(0x00), srgbToLinear(0x00), 1f),
        // 1: Orange  #EC6A0A
        floatArrayOf(srgbToLinear(0xEC), srgbToLinear(0x6A), srgbToLinear(0x0A), 1f),
        // 2: Yellow  #F6DA00
        floatArrayOf(srgbToLinear(0xF6), srgbToLinear(0xDA), srgbToLinear(0x00), 1f),
        // 3: Lime    #44C80A
        floatArrayOf(srgbToLinear(0x44), srgbToLinear(0xC8), srgbToLinear(0x0A), 1f),
        // 4: Green   #00A44A
        floatArrayOf(srgbToLinear(0x00), srgbToLinear(0xA4), srgbToLinear(0x4A), 1f),
        // 5: Blue    #3D7DFC
        floatArrayOf(srgbToLinear(0x3D), srgbToLinear(0x7D), srgbToLinear(0xFC), 1f),
        // 6: Cyan    #4ACEDC
        floatArrayOf(srgbToLinear(0x4A), srgbToLinear(0xCE), srgbToLinear(0xDC), 1f),
        // 7: Pink    #F96ACE
        floatArrayOf(srgbToLinear(0xF9), srgbToLinear(0x6A), srgbToLinear(0xCE), 1f),
        // 8: Purple  #6A2CDA
        floatArrayOf(srgbToLinear(0x6A), srgbToLinear(0x2C), srgbToLinear(0xDA), 1f),
        // 9: Brown   #704818
        floatArrayOf(srgbToLinear(0x70), srgbToLinear(0x48), srgbToLinear(0x18), 1f),
        // 10: White  #F0F0F0
        floatArrayOf(srgbToLinear(0xF0), srgbToLinear(0xF0), srgbToLinear(0xF0), 1f),
        // 11: Black  #484848
        floatArrayOf(srgbToLinear(0x48), srgbToLinear(0x48), srgbToLinear(0x48), 1f),
    )

    /**
     * sRGB color names for UI display.
     */
    val COLOR_NAMES: Array<String> = arrayOf(
        "Red", "Orange", "Yellow", "Lime", "Green", "Blue",
        "Cyan", "Pink", "Purple", "Brown", "White", "Black"
    )

    /**
     * sRGB hex colors for UI display (e.g., color picker circles).
     */
    val SRGB_COLORS: Array<Long> = arrayOf(
        0xFFCC0000, 0xFFEC6A0A, 0xFFF6DA00, 0xFF44C80A,
        0xFF00A44A, 0xFF3D7DFC, 0xFF4ACEDC, 0xFFF96ACE,
        0xFF6A2CDA, 0xFF704818, 0xFFF0F0F0, 0xFF484848
    )

    /**
     * 12 pants colors in linear-space RGBA — darker shades of the favorite colors,
     * matching the 3DS StreetPass Mii Plaza pants style.
     */
    val PANTS_COLORS: Array<FloatArray> = arrayOf(
        // 0: Red     #8A0000
        floatArrayOf(srgbToLinear(0x8A), srgbToLinear(0x00), srgbToLinear(0x00), 1f),
        // 1: Orange  #A04800
        floatArrayOf(srgbToLinear(0xA0), srgbToLinear(0x48), srgbToLinear(0x00), 1f),
        // 2: Yellow  #A89400
        floatArrayOf(srgbToLinear(0xA8), srgbToLinear(0x94), srgbToLinear(0x00), 1f),
        // 3: Lime    #2E8800
        floatArrayOf(srgbToLinear(0x2E), srgbToLinear(0x88), srgbToLinear(0x00), 1f),
        // 4: Green   #006E32
        floatArrayOf(srgbToLinear(0x00), srgbToLinear(0x6E), srgbToLinear(0x32), 1f),
        // 5: Blue    #2A54AC
        floatArrayOf(srgbToLinear(0x2A), srgbToLinear(0x54), srgbToLinear(0xAC), 1f),
        // 6: Cyan    #328C96
        floatArrayOf(srgbToLinear(0x32), srgbToLinear(0x8C), srgbToLinear(0x96), 1f),
        // 7: Pink    #AA488C
        floatArrayOf(srgbToLinear(0xAA), srgbToLinear(0x48), srgbToLinear(0x8C), 1f),
        // 8: Purple  #481E96
        floatArrayOf(srgbToLinear(0x48), srgbToLinear(0x1E), srgbToLinear(0x96), 1f),
        // 9: Brown   #4C3010
        floatArrayOf(srgbToLinear(0x4C), srgbToLinear(0x30), srgbToLinear(0x10), 1f),
        // 10: White  #B0B0B0
        floatArrayOf(srgbToLinear(0xB0), srgbToLinear(0xB0), srgbToLinear(0xB0), 1f),
        // 11: Black  #303030
        floatArrayOf(srgbToLinear(0x30), srgbToLinear(0x30), srgbToLinear(0x30), 1f),
    )

    /**
     * sRGB hex colors for pants UI display.
     */
    val SRGB_PANTS_COLORS: Array<Long> = arrayOf(
        0xFF8A0000, 0xFFA04800, 0xFFA89400, 0xFF2E8800,
        0xFF006E32, 0xFF2A54AC, 0xFF328C96, 0xFFAA488C,
        0xFF481E96, 0xFF4C3010, 0xFFB0B0B0, 0xFF303030
    )

    /**
     * Convert an sRGB byte value (0-255) to linear-space float.
     * Uses the standard sRGB transfer function (gamma ≈ 2.2).
     */
    private fun srgbToLinear(srgb: Int): Float {
        val s = srgb / 255f
        return if (s <= 0.04045f) {
            s / 12.92f
        } else {
            ((s + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        }
    }

    /**
     * Extract the favorite color index (0-11) from base64-encoded Studio Mii data.
     *
     * Studio format XOR scrambling:
     *   encoded[0] is the first byte (used as-is for XOR chain)
     *   For each subsequent byte: raw[i] = ((encoded[i] - 7 + 256) % 256) XOR encoded[i-1]
     *
     * The favorite color is at decoded index 22 (1-based: 22nd value).
     */
    fun extractFavoriteColor(avatarBase64: String): Int? {
        return try {
            val encoded = Base64.decode(avatarBase64, Base64.DEFAULT)
            if (encoded.size < 23) {
                Log.w(TAG, "Mii data too short: ${encoded.size} bytes (need at least 23)")
                return null
            }

            // Decode XOR scrambling up to index 22
            val decoded = IntArray(23)
            decoded[0] = encoded[0].toInt() and 0xFF
            for (i in 1..22) {
                val enc = encoded[i].toInt() and 0xFF
                val prev = encoded[i - 1].toInt() and 0xFF
                decoded[i] = ((enc - 7 + 256) % 256) xor prev
            }

            val colorIndex = decoded[22]
            if (colorIndex in 0..11) {
                Log.d(TAG, "Extracted favorite color: $colorIndex (${COLOR_NAMES[colorIndex]})")
                colorIndex
            } else {
                Log.w(TAG, "Invalid favorite color index: $colorIndex")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode Mii data", e)
            null
        }
    }

    /**
     * Extract the gender from base64-encoded Studio Mii data.
     * Gender is at decoded index 1: 0 = male, 1 = female.
     *
     * @return true if male, false if female, null if data can't be decoded
     */
    fun isMale(avatarBase64: String?): Boolean {
        if (avatarBase64.isNullOrBlank()) return true // default to male
        return try {
            val encoded = Base64.decode(avatarBase64, Base64.DEFAULT)
            if (encoded.size < 2) return true

            val decoded = IntArray(2)
            decoded[0] = encoded[0].toInt() and 0xFF
            val enc1 = encoded[1].toInt() and 0xFF
            val prev1 = encoded[0].toInt() and 0xFF
            decoded[1] = ((enc1 - 7 + 256) % 256) xor prev1

            val gender = decoded[1] // 0 = male, 1 = female
            gender == 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract gender from Mii data", e)
            true
        }
    }

    /**
     * Get the linear-space RGBA color array for a Mii's favorite color (shirt).
     * Falls back to blue (index 5) if the data can't be decoded.
     */
    fun getColorFromAvatarData(avatarBase64: String?): FloatArray {
        if (avatarBase64.isNullOrBlank()) return FAVORITE_COLORS[DEFAULT_COLOR_INDEX]
        val index = extractFavoriteColor(avatarBase64) ?: DEFAULT_COLOR_INDEX
        return FAVORITE_COLORS[index]
    }

    /**
     * Get the linear-space RGBA pants color for a Mii's favorite color.
     * Returns a darker shade matching the 3DS pants style.
     */
    fun getPantsColorFromAvatarData(avatarBase64: String?): FloatArray {
        if (avatarBase64.isNullOrBlank()) return PANTS_COLORS[DEFAULT_COLOR_INDEX]
        val index = extractFavoriteColor(avatarBase64) ?: DEFAULT_COLOR_INDEX
        return PANTS_COLORS[index]
    }
}
