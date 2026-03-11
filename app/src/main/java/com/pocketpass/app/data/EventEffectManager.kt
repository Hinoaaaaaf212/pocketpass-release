package com.pocketpass.app.data

/** Stateless resolver — picks the highest value when multiple events share the same effect type. */
object EventEffectManager {

    fun getTokenMultiplier(effects: List<EventEffect>): Int =
        effects.filter { it.type == EventEffectType.TOKEN_MULTIPLIER }
            .maxOfOrNull { it.value.toInt() }
            ?: 1

    fun getPuzzleDropChance(effects: List<EventEffect>): Float =
        effects.filter { it.type == EventEffectType.PUZZLE_DROP_BOOST }
            .maxOfOrNull { it.value }
            ?: TokenSystem.PUZZLE_PIECE_DROP_CHANCE

    fun getShopPriceMultiplier(effects: List<EventEffect>): Float {
        val discount = effects.filter { it.type == EventEffectType.SHOP_DISCOUNT }
            .maxOfOrNull { it.value }
            ?: return 1f
        return (1f - discount / 100f).coerceIn(0.01f, 1f)
    }

    fun getWalkTokenCap(effects: List<EventEffect>): Int =
        effects.filter { it.type == EventEffectType.WALK_BONUS }
            .maxOfOrNull { it.value.toInt() }
            ?: TokenSystem.MAX_STEP_TOKENS_PER_DAY

    fun getSpecialGreeting(effects: List<EventEffect>): String? =
        effects.firstOrNull { it.type == EventEffectType.SPECIAL_GREETING }?.message

    fun getSpecialGreetingColor(effects: List<EventEffect>): String? =
        effects.firstOrNull { it.type == EventEffectType.SPECIAL_GREETING }?.color

    fun getSpecialGreetingBgColor(effects: List<EventEffect>): String? =
        effects.firstOrNull { it.type == EventEffectType.SPECIAL_GREETING }?.bgColor
}
