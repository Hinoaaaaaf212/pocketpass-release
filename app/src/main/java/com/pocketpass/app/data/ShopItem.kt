package com.pocketpass.app.data

enum class ShopCategory { CARD_BORDER, HAT, COSTUME }

data class ShopItem(
    val id: String,
    val name: String,
    val description: String,
    val category: ShopCategory,
    val price: Int,
    val icon: String,
    val previewColors: List<Long>? = null
)

object ShopItems {
    fun getAll(): List<ShopItem> = cardThemes

    val cardThemes = listOf(
        // Free defaults (price = 0, always owned)
        ShopItem("classic", "Classic", "The original green theme", ShopCategory.CARD_BORDER, 0, "\uD83D\uDFE9", listOf(0xFF4CAF50, 0xFF81C784)),
        ShopItem("gradient", "Sunny", "Warm yellow tones", ShopCategory.CARD_BORDER, 0, "\u2600\uFE0F", listOf(0xFFFFC107, 0xFFFFEB3B)),
        ShopItem("cool", "Cool", "Calm blue vibes", ShopCategory.CARD_BORDER, 0, "\u2744\uFE0F", listOf(0xFF2196F3, 0xFF03A9F4)),
        ShopItem("warm", "Warm", "Fiery orange glow", ShopCategory.CARD_BORDER, 0, "\uD83D\uDD25", listOf(0xFFFF5722, 0xFFFF9800)),
        // Premium themes
        ShopItem("ocean", "Ocean", "Deep sea gradient", ShopCategory.CARD_BORDER, 5, "\uD83C\uDF0A", listOf(0xFF006064, 0xFF00BCD4)),
        ShopItem("sunset", "Sunset", "Purple-pink twilight", ShopCategory.CARD_BORDER, 5, "\uD83C\uDF05", listOf(0xFF9C27B0, 0xFFE91E63)),
        ShopItem("forest", "Forest", "Dark emerald tones", ShopCategory.CARD_BORDER, 5, "\uD83C\uDF32", listOf(0xFF1B5E20, 0xFF4CAF50)),
        ShopItem("royal", "Royal", "Gold and purple luxury", ShopCategory.CARD_BORDER, 8, "\uD83D\uDC51", listOf(0xFF4A148C, 0xFFFFD700)),
        ShopItem("neon", "Neon", "Cyberpunk neon glow", ShopCategory.CARD_BORDER, 8, "\uD83D\uDC9C", listOf(0xFFE040FB, 0xFF00E5FF)),
        ShopItem("midnight", "Midnight", "Dark starry night", ShopCategory.CARD_BORDER, 10, "\uD83C\uDF19", listOf(0xFF1A237E, 0xFF283593)),
        ShopItem("cherry", "Cherry Blossom", "Soft pink petals", ShopCategory.CARD_BORDER, 10, "\uD83C\uDF38", listOf(0xFFF48FB1, 0xFFFCE4EC)),
        ShopItem("rainbow", "Rainbow", "All the colors", ShopCategory.CARD_BORDER, 15, "\uD83C\uDF08", listOf(0xFFFF0000, 0xFFFF9800, 0xFFFFEB3B, 0xFF4CAF50, 0xFF2196F3, 0xFF9C27B0)),
    )

    fun findById(id: String): ShopItem? = getAll().find { it.id == id }
}
