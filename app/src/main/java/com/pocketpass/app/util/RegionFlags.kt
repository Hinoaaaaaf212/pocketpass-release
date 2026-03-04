package com.pocketpass.app.util

object RegionFlags {
    private val regionToFlagMap = mapOf(
        "United States" to "🇺🇸",
        "United Kingdom" to "🇬🇧",
        "Canada" to "🇨🇦",
        "Australia" to "🇦🇺",
        "Japan" to "🇯🇵",
        "Germany" to "🇩🇪",
        "France" to "🇫🇷",
        "Italy" to "🇮🇹",
        "Spain" to "🇪🇸",
        "Mexico" to "🇲🇽",
        "Brazil" to "🇧🇷",
        "Argentina" to "🇦🇷",
        "China" to "🇨🇳",
        "South Korea" to "🇰🇷",
        "India" to "🇮🇳",
        "Russia" to "🇷🇺",
        "Netherlands" to "🇳🇱",
        "Belgium" to "🇧🇪",
        "Switzerland" to "🇨🇭",
        "Sweden" to "🇸🇪",
        "Norway" to "🇳🇴",
        "Denmark" to "🇩🇰",
        "Finland" to "🇫🇮",
        "Poland" to "🇵🇱",
        "Ireland" to "🇮🇪",
        "New Zealand" to "🇳🇿",
        "Singapore" to "🇸🇬",
        "Hong Kong" to "🇭🇰",
        "Taiwan" to "🇹🇼",
        "Thailand" to "🇹🇭",
        "Vietnam" to "🇻🇳",
        "Philippines" to "🇵🇭",
        "Indonesia" to "🇮🇩",
        "Malaysia" to "🇲🇾",
        "South Africa" to "🇿🇦",
        "Turkey" to "🇹🇷",
        "Greece" to "🇬🇷",
        "Portugal" to "🇵🇹",
        "Austria" to "🇦🇹",
        "Czech Republic" to "🇨🇿",
        "Chile" to "🇨🇱",
        "Colombia" to "🇨🇴",
        "Peru" to "🇵🇪",
        "Israel" to "🇮🇱",
        "UAE" to "🇦🇪",
        "Saudi Arabia" to "🇸🇦",
        "Egypt" to "🇪🇬"
    )

    val supportedRegions = regionToFlagMap.keys.sorted()

    fun getFlagForRegion(region: String): String {
        return regionToFlagMap[region] ?: "🌍"
    }
}
