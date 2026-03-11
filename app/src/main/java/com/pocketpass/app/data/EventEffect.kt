package com.pocketpass.app.data

import org.json.JSONObject

enum class EventEffectType {
    TOKEN_MULTIPLIER,
    PUZZLE_DROP_BOOST,
    SHOP_DISCOUNT,
    WALK_BONUS,
    SPECIAL_GREETING
}

data class EventEffect(
    val type: EventEffectType,
    val value: Float = 1f,
    val message: String? = null,
    val color: String? = null,
    val bgColor: String? = null
)

fun parseEventEffect(json: String?): EventEffect? {
    if (json.isNullOrBlank()) return null
    return try {
        val obj = JSONObject(json)
        val typeStr = obj.optString("type", "")
        val type = when (typeStr) {
            "token_multiplier" -> EventEffectType.TOKEN_MULTIPLIER
            "puzzle_drop_boost" -> EventEffectType.PUZZLE_DROP_BOOST
            "shop_discount" -> EventEffectType.SHOP_DISCOUNT
            "walk_bonus" -> EventEffectType.WALK_BONUS
            "special_greeting" -> EventEffectType.SPECIAL_GREETING
            else -> return null
        }
        EventEffect(
            type = type,
            value = obj.optDouble("value", 1.0).toFloat(),
            message = obj.optString("message", null),
            color = obj.optString("color", null),
            bgColor = obj.optString("bg_color", null)
        )
    } catch (_: Exception) {
        null
    }
}
