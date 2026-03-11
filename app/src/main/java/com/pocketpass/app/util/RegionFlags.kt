package com.pocketpass.app.util

object RegionFlags {
    private val regionToFlagMap = mapOf(
        // North America
        "United States" to "\uD83C\uDDFA\uD83C\uDDF8",
        "Canada" to "\uD83C\uDDE8\uD83C\uDDE6",
        "Mexico" to "\uD83C\uDDF2\uD83C\uDDFD",
        "Costa Rica" to "\uD83C\uDDE8\uD83C\uDDF7",
        "Cuba" to "\uD83C\uDDE8\uD83C\uDDFA",
        "Dominican Republic" to "\uD83C\uDDE9\uD83C\uDDF4",
        "El Salvador" to "\uD83C\uDDF8\uD83C\uDDFB",
        "Guatemala" to "\uD83C\uDDEC\uD83C\uDDF9",
        "Haiti" to "\uD83C\uDDED\uD83C\uDDF9",
        "Honduras" to "\uD83C\uDDED\uD83C\uDDF3",
        "Jamaica" to "\uD83C\uDDEF\uD83C\uDDF2",
        "Nicaragua" to "\uD83C\uDDF3\uD83C\uDDEE",
        "Panama" to "\uD83C\uDDF5\uD83C\uDDE6",
        "Puerto Rico" to "\uD83C\uDDF5\uD83C\uDDF7",
        "Trinidad and Tobago" to "\uD83C\uDDF9\uD83C\uDDF9",

        // South America
        "Argentina" to "\uD83C\uDDE6\uD83C\uDDF7",
        "Bolivia" to "\uD83C\uDDE7\uD83C\uDDF4",
        "Brazil" to "\uD83C\uDDE7\uD83C\uDDF7",
        "Chile" to "\uD83C\uDDE8\uD83C\uDDF1",
        "Colombia" to "\uD83C\uDDE8\uD83C\uDDF4",
        "Ecuador" to "\uD83C\uDDEA\uD83C\uDDE8",
        "Paraguay" to "\uD83C\uDDF5\uD83C\uDDFE",
        "Peru" to "\uD83C\uDDF5\uD83C\uDDEA",
        "Uruguay" to "\uD83C\uDDFA\uD83C\uDDFE",
        "Venezuela" to "\uD83C\uDDFB\uD83C\uDDEA",

        // Western Europe
        "United Kingdom" to "\uD83C\uDDEC\uD83C\uDDE7",
        "France" to "\uD83C\uDDEB\uD83C\uDDF7",
        "Germany" to "\uD83C\uDDE9\uD83C\uDDEA",
        "Italy" to "\uD83C\uDDEE\uD83C\uDDF9",
        "Spain" to "\uD83C\uDDEA\uD83C\uDDF8",
        "Portugal" to "\uD83C\uDDF5\uD83C\uDDF9",
        "Netherlands" to "\uD83C\uDDF3\uD83C\uDDF1",
        "Belgium" to "\uD83C\uDDE7\uD83C\uDDEA",
        "Switzerland" to "\uD83C\uDDE8\uD83C\uDDED",
        "Austria" to "\uD83C\uDDE6\uD83C\uDDF9",
        "Ireland" to "\uD83C\uDDEE\uD83C\uDDEA",
        "Luxembourg" to "\uD83C\uDDF1\uD83C\uDDFA",

        // Northern Europe
        "Sweden" to "\uD83C\uDDF8\uD83C\uDDEA",
        "Norway" to "\uD83C\uDDF3\uD83C\uDDF4",
        "Denmark" to "\uD83C\uDDE9\uD83C\uDDF0",
        "Finland" to "\uD83C\uDDEB\uD83C\uDDEE",
        "Iceland" to "\uD83C\uDDEE\uD83C\uDDF8",
        "Estonia" to "\uD83C\uDDEA\uD83C\uDDEA",
        "Latvia" to "\uD83C\uDDF1\uD83C\uDDFB",
        "Lithuania" to "\uD83C\uDDF1\uD83C\uDDF9",

        // Eastern Europe
        "Poland" to "\uD83C\uDDF5\uD83C\uDDF1",
        "Czech Republic" to "\uD83C\uDDE8\uD83C\uDDFF",
        "Slovakia" to "\uD83C\uDDF8\uD83C\uDDF0",
        "Hungary" to "\uD83C\uDDED\uD83C\uDDFA",
        "Romania" to "\uD83C\uDDF7\uD83C\uDDF4",
        "Bulgaria" to "\uD83C\uDDE7\uD83C\uDDEC",
        "Croatia" to "\uD83C\uDDED\uD83C\uDDF7",
        "Serbia" to "\uD83C\uDDF7\uD83C\uDDF8",
        "Slovenia" to "\uD83C\uDDF8\uD83C\uDDEE",
        "Ukraine" to "\uD83C\uDDFA\uD83C\uDDE6",
        "Russia" to "\uD83C\uDDF7\uD83C\uDDFA",

        // Southern Europe / Mediterranean
        "Greece" to "\uD83C\uDDEC\uD83C\uDDF7",
        "Turkey" to "\uD83C\uDDF9\uD83C\uDDF7",
        "Cyprus" to "\uD83C\uDDE8\uD83C\uDDFE",
        "Malta" to "\uD83C\uDDF2\uD83C\uDDF9",

        // East Asia
        "Japan" to "\uD83C\uDDEF\uD83C\uDDF5",
        "China" to "\uD83C\uDDE8\uD83C\uDDF3",
        "South Korea" to "\uD83C\uDDF0\uD83C\uDDF7",
        "North Korea" to "\uD83C\uDDF0\uD83C\uDDF5",
        "Taiwan" to "\uD83C\uDDF9\uD83C\uDDFC",
        "Hong Kong" to "\uD83C\uDDED\uD83C\uDDF0",
        "Mongolia" to "\uD83C\uDDF2\uD83C\uDDF3",

        // Southeast Asia
        "Thailand" to "\uD83C\uDDF9\uD83C\uDDED",
        "Vietnam" to "\uD83C\uDDFB\uD83C\uDDF3",
        "Philippines" to "\uD83C\uDDF5\uD83C\uDDED",
        "Indonesia" to "\uD83C\uDDEE\uD83C\uDDE9",
        "Malaysia" to "\uD83C\uDDF2\uD83C\uDDFE",
        "Singapore" to "\uD83C\uDDF8\uD83C\uDDEC",
        "Myanmar" to "\uD83C\uDDF2\uD83C\uDDF2",
        "Cambodia" to "\uD83C\uDDF0\uD83C\uDDED",
        "Laos" to "\uD83C\uDDF1\uD83C\uDDE6",

        // South Asia
        "India" to "\uD83C\uDDEE\uD83C\uDDF3",
        "Pakistan" to "\uD83C\uDDF5\uD83C\uDDF0",
        "Bangladesh" to "\uD83C\uDDE7\uD83C\uDDE9",
        "Sri Lanka" to "\uD83C\uDDF1\uD83C\uDDF0",
        "Nepal" to "\uD83C\uDDF3\uD83C\uDDF5",

        // Central Asia
        "Kazakhstan" to "\uD83C\uDDF0\uD83C\uDDFF",
        "Uzbekistan" to "\uD83C\uDDFA\uD83C\uDDFF",

        // Middle East
        "Israel" to "\uD83C\uDDEE\uD83C\uDDF1",
        "UAE" to "\uD83C\uDDE6\uD83C\uDDEA",
        "Saudi Arabia" to "\uD83C\uDDF8\uD83C\uDDE6",
        "Qatar" to "\uD83C\uDDF6\uD83C\uDDE6",
        "Kuwait" to "\uD83C\uDDF0\uD83C\uDDFC",
        "Bahrain" to "\uD83C\uDDE7\uD83C\uDDED",
        "Oman" to "\uD83C\uDDF4\uD83C\uDDF2",
        "Jordan" to "\uD83C\uDDEF\uD83C\uDDF4",
        "Lebanon" to "\uD83C\uDDF1\uD83C\uDDE7",
        "Iraq" to "\uD83C\uDDEE\uD83C\uDDF6",
        "Iran" to "\uD83C\uDDEE\uD83C\uDDF7",

        // Oceania
        "Australia" to "\uD83C\uDDE6\uD83C\uDDFA",
        "New Zealand" to "\uD83C\uDDF3\uD83C\uDDFF",

        // Africa
        "South Africa" to "\uD83C\uDDFF\uD83C\uDDE6",
        "Egypt" to "\uD83C\uDDEA\uD83C\uDDEC",
        "Nigeria" to "\uD83C\uDDF3\uD83C\uDDEC",
        "Kenya" to "\uD83C\uDDF0\uD83C\uDDEA",
        "Ghana" to "\uD83C\uDDEC\uD83C\uDDED",
        "Ethiopia" to "\uD83C\uDDEA\uD83C\uDDF9",
        "Tanzania" to "\uD83C\uDDF9\uD83C\uDDFF",
        "Morocco" to "\uD83C\uDDF2\uD83C\uDDE6",
        "Algeria" to "\uD83C\uDDE9\uD83C\uDDFF",
        "Tunisia" to "\uD83C\uDDF9\uD83C\uDDF3",
        "Senegal" to "\uD83C\uDDF8\uD83C\uDDF3",
        "Uganda" to "\uD83C\uDDFA\uD83C\uDDEC",
        "Rwanda" to "\uD83C\uDDF7\uD83C\uDDFC",
        "Cameroon" to "\uD83C\uDDE8\uD83C\uDDF2"
    )

    val supportedRegions = regionToFlagMap.keys.sorted()

    fun getFlagForRegion(region: String): String {
        return regionToFlagMap[region] ?: "🌍"
    }
}
