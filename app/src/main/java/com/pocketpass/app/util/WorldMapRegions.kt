package com.pocketpass.app.util

import androidx.compose.ui.graphics.Path

enum class Continent(val label: String) {
    NORTH_AMERICA("N. America"),
    SOUTH_AMERICA("S. America"),
    EUROPE("Europe"),
    AFRICA("Africa"),
    MIDDLE_EAST("Middle East"),
    SOUTH_ASIA("S. Asia"),
    EAST_ASIA("E. Asia"),
    SOUTHEAST_ASIA("SE. Asia"),
    OCEANIA("Oceania")
}

data class WorldRegion(
    val name: String,
    val x: Float,
    val y: Float,
    val continent: Continent
)

/**
 * Region dot coordinates are normalized (0..1) relative to the world_map.png asset.
 * Positions were manually placed using the dot_placer.html tool.
 */
object WorldMapRegions {

    val regions: List<WorldRegion> = listOf(
        // -- NORTH_AMERICA --
        WorldRegion("Canada", 0.216f, 0.302f, Continent.NORTH_AMERICA),
        WorldRegion("United States", 0.201f, 0.415f, Continent.NORTH_AMERICA),
        WorldRegion("Mexico", 0.190f, 0.523f, Continent.NORTH_AMERICA),
        WorldRegion("Guatemala", 0.219f, 0.558f, Continent.NORTH_AMERICA),
        WorldRegion("El Salvador", 0.222f, 0.567f, Continent.NORTH_AMERICA),
        WorldRegion("Honduras", 0.229f, 0.560f, Continent.NORTH_AMERICA),
        WorldRegion("Nicaragua", 0.233f, 0.571f, Continent.NORTH_AMERICA),
        WorldRegion("Costa Rica", 0.236f, 0.585f, Continent.NORTH_AMERICA),
        WorldRegion("Panama", 0.249f, 0.593f, Continent.NORTH_AMERICA),
        WorldRegion("Cuba", 0.255f, 0.524f, Continent.NORTH_AMERICA),
        WorldRegion("Jamaica", 0.258f, 0.543f, Continent.NORTH_AMERICA),
        WorldRegion("Haiti", 0.271f, 0.540f, Continent.NORTH_AMERICA),
        WorldRegion("Dominican Republic", 0.278f, 0.539f, Continent.NORTH_AMERICA),
        WorldRegion("Puerto Rico", 0.290f, 0.542f, Continent.NORTH_AMERICA),
        WorldRegion("Trinidad and Tobago", 0.304f, 0.581f, Continent.NORTH_AMERICA),

        // -- SOUTH_AMERICA --
        WorldRegion("Venezuela", 0.291f, 0.597f, Continent.SOUTH_AMERICA),
        WorldRegion("Colombia", 0.268f, 0.615f, Continent.SOUTH_AMERICA),
        WorldRegion("Ecuador", 0.253f, 0.644f, Continent.SOUTH_AMERICA),
        WorldRegion("Peru", 0.261f, 0.684f, Continent.SOUTH_AMERICA),
        WorldRegion("Bolivia", 0.294f, 0.720f, Continent.SOUTH_AMERICA),
        WorldRegion("Brazil", 0.345f, 0.693f, Continent.SOUTH_AMERICA),
        WorldRegion("Paraguay", 0.314f, 0.756f, Continent.SOUTH_AMERICA),
        WorldRegion("Uruguay", 0.323f, 0.809f, Continent.SOUTH_AMERICA),
        WorldRegion("Argentina", 0.298f, 0.830f, Continent.SOUTH_AMERICA),
        WorldRegion("Chile", 0.280f, 0.824f, Continent.SOUTH_AMERICA),

        // -- EUROPE --
        WorldRegion("Iceland", 0.433f, 0.251f, Continent.EUROPE),
        WorldRegion("Norway", 0.502f, 0.281f, Continent.EUROPE),
        WorldRegion("Sweden", 0.515f, 0.277f, Continent.EUROPE),
        WorldRegion("Finland", 0.542f, 0.262f, Continent.EUROPE),
        WorldRegion("Estonia", 0.542f, 0.300f, Continent.EUROPE),
        WorldRegion("Latvia", 0.542f, 0.313f, Continent.EUROPE),
        WorldRegion("Lithuania", 0.539f, 0.325f, Continent.EUROPE),
        WorldRegion("Ireland", 0.459f, 0.340f, Continent.EUROPE),
        WorldRegion("United Kingdom", 0.474f, 0.346f, Continent.EUROPE),
        WorldRegion("Denmark", 0.505f, 0.324f, Continent.EUROPE),
        WorldRegion("Netherlands", 0.492f, 0.346f, Continent.EUROPE),
        WorldRegion("Belgium", 0.489f, 0.358f, Continent.EUROPE),
        WorldRegion("Luxembourg", 0.494f, 0.365f, Continent.EUROPE),
        WorldRegion("Germany", 0.505f, 0.351f, Continent.EUROPE),
        WorldRegion("Poland", 0.528f, 0.346f, Continent.EUROPE),
        WorldRegion("Czech Republic", 0.517f, 0.362f, Continent.EUROPE),
        WorldRegion("Slovakia", 0.529f, 0.368f, Continent.EUROPE),
        WorldRegion("France", 0.485f, 0.380f, Continent.EUROPE),
        WorldRegion("Switzerland", 0.499f, 0.381f, Continent.EUROPE),
        WorldRegion("Austria", 0.517f, 0.377f, Continent.EUROPE),
        WorldRegion("Hungary", 0.530f, 0.380f, Continent.EUROPE),
        WorldRegion("Romania", 0.544f, 0.383f, Continent.EUROPE),
        WorldRegion("Ukraine", 0.561f, 0.362f, Continent.EUROPE),
        WorldRegion("Slovenia", 0.517f, 0.385f, Continent.EUROPE),
        WorldRegion("Croatia", 0.521f, 0.389f, Continent.EUROPE),
        WorldRegion("Serbia", 0.534f, 0.398f, Continent.EUROPE),
        WorldRegion("Bulgaria", 0.547f, 0.407f, Continent.EUROPE),
        WorldRegion("Portugal", 0.457f, 0.423f, Continent.EUROPE),
        WorldRegion("Spain", 0.468f, 0.425f, Continent.EUROPE),
        WorldRegion("Italy", 0.512f, 0.406f, Continent.EUROPE),
        WorldRegion("Greece", 0.542f, 0.432f, Continent.EUROPE),
        WorldRegion("Turkey", 0.573f, 0.428f, Continent.EUROPE),
        WorldRegion("Cyprus", 0.571f, 0.453f, Continent.EUROPE),
        WorldRegion("Malta", 0.519f, 0.451f, Continent.EUROPE),
        WorldRegion("Russia", 0.667f, 0.252f, Continent.EUROPE),

        // -- MIDDLE_EAST --
        WorldRegion("Lebanon", 0.579f, 0.446f, Continent.MIDDLE_EAST),
        WorldRegion("Israel", 0.577f, 0.466f, Continent.MIDDLE_EAST),
        WorldRegion("Jordan", 0.581f, 0.474f, Continent.MIDDLE_EAST),
        WorldRegion("Iraq", 0.599f, 0.459f, Continent.MIDDLE_EAST),
        WorldRegion("Kuwait", 0.613f, 0.483f, Continent.MIDDLE_EAST),
        WorldRegion("Iran", 0.631f, 0.465f, Continent.MIDDLE_EAST),
        WorldRegion("Bahrain", 0.621f, 0.496f, Continent.MIDDLE_EAST),
        WorldRegion("Qatar", 0.625f, 0.503f, Continent.MIDDLE_EAST),
        WorldRegion("UAE", 0.634f, 0.513f, Continent.MIDDLE_EAST),
        WorldRegion("Oman", 0.640f, 0.536f, Continent.MIDDLE_EAST),
        WorldRegion("Saudi Arabia", 0.606f, 0.510f, Continent.MIDDLE_EAST),

        // -- SOUTH_ASIA --
        WorldRegion("Pakistan", 0.674f, 0.483f, Continent.SOUTH_ASIA),
        WorldRegion("India", 0.707f, 0.522f, Continent.SOUTH_ASIA),
        WorldRegion("Nepal", 0.716f, 0.486f, Continent.SOUTH_ASIA),
        WorldRegion("Bangladesh", 0.736f, 0.510f, Continent.SOUTH_ASIA),
        WorldRegion("Sri Lanka", 0.713f, 0.596f, Continent.SOUTH_ASIA),
        WorldRegion("Kazakhstan", 0.660f, 0.365f, Continent.SOUTH_ASIA),
        WorldRegion("Uzbekistan", 0.651f, 0.404f, Continent.SOUTH_ASIA),

        // -- EAST_ASIA --
        WorldRegion("Mongolia", 0.758f, 0.364f, Continent.EAST_ASIA),
        WorldRegion("China", 0.780f, 0.449f, Continent.EAST_ASIA),
        WorldRegion("North Korea", 0.830f, 0.403f, Continent.EAST_ASIA),
        WorldRegion("South Korea", 0.837f, 0.423f, Continent.EAST_ASIA),
        WorldRegion("Japan", 0.868f, 0.423f, Continent.EAST_ASIA),
        WorldRegion("Taiwan", 0.826f, 0.507f, Continent.EAST_ASIA),
        WorldRegion("Hong Kong", 0.807f, 0.518f, Continent.EAST_ASIA),

        // -- SOUTHEAST_ASIA --
        WorldRegion("Myanmar", 0.756f, 0.530f, Continent.SOUTHEAST_ASIA),
        WorldRegion("Laos", 0.773f, 0.531f, Continent.SOUTHEAST_ASIA),
        WorldRegion("Thailand", 0.771f, 0.557f, Continent.SOUTHEAST_ASIA),
        WorldRegion("Vietnam", 0.792f, 0.566f, Continent.SOUTHEAST_ASIA),
        WorldRegion("Cambodia", 0.782f, 0.568f, Continent.SOUTHEAST_ASIA),
        WorldRegion("Malaysia", 0.808f, 0.623f, Continent.SOUTHEAST_ASIA),
        WorldRegion("Singapore", 0.783f, 0.631f, Continent.SOUTHEAST_ASIA),
        WorldRegion("Philippines", 0.835f, 0.570f, Continent.SOUTHEAST_ASIA),
        WorldRegion("Indonesia", 0.780f, 0.655f, Continent.SOUTHEAST_ASIA),

        // -- OCEANIA --
        WorldRegion("Australia", 0.870f, 0.783f, Continent.OCEANIA),
        WorldRegion("New Zealand", 0.950f, 0.919f, Continent.OCEANIA),

        // -- AFRICA --
        WorldRegion("Morocco", 0.462f, 0.466f, Continent.AFRICA),
        WorldRegion("Algeria", 0.487f, 0.473f, Continent.AFRICA),
        WorldRegion("Tunisia", 0.505f, 0.447f, Continent.AFRICA),
        WorldRegion("Egypt", 0.566f, 0.493f, Continent.AFRICA),
        WorldRegion("Senegal", 0.436f, 0.558f, Continent.AFRICA),
        WorldRegion("Ghana", 0.476f, 0.596f, Continent.AFRICA),
        WorldRegion("Nigeria", 0.501f, 0.588f, Continent.AFRICA),
        WorldRegion("Cameroon", 0.512f, 0.611f, Continent.AFRICA),
        WorldRegion("Ethiopia", 0.594f, 0.592f, Continent.AFRICA),
        WorldRegion("Uganda", 0.573f, 0.629f, Continent.AFRICA),
        WorldRegion("Kenya", 0.588f, 0.634f, Continent.AFRICA),
        WorldRegion("Rwanda", 0.565f, 0.645f, Continent.AFRICA),
        WorldRegion("Tanzania", 0.579f, 0.666f, Continent.AFRICA),
        WorldRegion("South Africa", 0.543f, 0.792f, Continent.AFRICA)
    )

    val TOTAL_REGIONS = regions.size

    // Continent silhouette paths are no longer used (replaced by PNG background).
    fun continentPath(continent: Continent, w: Float, h: Float): Path = Path()
}
