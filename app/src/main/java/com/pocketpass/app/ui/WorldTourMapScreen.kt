package com.pocketpass.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.pocketpass.app.ui.theme.LocalEncounters
import com.pocketpass.app.ui.theme.LocalUserPreferences
import com.pocketpass.app.data.WorldTourMilestone
import com.pocketpass.app.ui.theme.AeroButton
import com.pocketpass.app.ui.theme.AeroCard
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.GreenText
import com.pocketpass.app.ui.theme.LocalDarkMode
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.util.Continent
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.RegionFlags
import com.pocketpass.app.util.WorldMapRegions
import com.pocketpass.app.util.gamepadFocusable
import kotlinx.coroutines.launch
import kotlin.math.sqrt

@Composable
fun WorldTourMapScreen(onBack: () -> Unit, isDualScreen: Boolean = false) {
    val soundManager = LocalSoundManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = LocalUserPreferences.current
    val encounters = LocalEncounters.current
    val claimedMilestones by prefs.claimedWorldTourMilestonesFlow.collectAsState(initial = emptySet())

    val visitedRegions = remember(encounters) {
        encounters.map { it.origin }.filter { it.isNotBlank() }.toSet()
    }
    val regionEncounterCounts = remember(encounters) {
        encounters.filter { it.origin.isNotBlank() }.groupingBy { it.origin }.eachCount()
    }
    val visitedCount = visitedRegions.size
    val totalRegions = WorldMapRegions.TOTAL_REGIONS

    // Pre-compute continent data once when encounters change
    val continentData = remember(visitedRegions) {
        Continent.entries.map { continent ->
            val allRegions = WorldMapRegions.regions.filter { it.continent == continent }
            val visited = allRegions.count { it.name in visitedRegions }
            val sorted = allRegions.sortedWith(
                compareByDescending<com.pocketpass.app.util.WorldRegion> { it.name in visitedRegions }
                    .thenBy { it.name }
            )
            Triple(continent, visited to allRegions.size, sorted)
        }
    }

    val mapBitmap = remember {
        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
        context.assets.open("world_map.png").use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
        }
    }

    var tooltipRegion by remember { mutableStateOf<String?>(null) }
    var tooltipOffset by remember { mutableStateOf(IntOffset.Zero) }

    Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val backFocus = remember { FocusRequester() }
                    IconButton(
                        onClick = { soundManager.playBack(); onBack() },
                        modifier = Modifier.gamepadFocusable(
                            focusRequester = backFocus,
                            shape = CircleShape,
                            onSelect = { soundManager.playBack(); onBack() }
                        )
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = DarkText)
                    }
                    Text(
                        text = "World Tour",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DarkText,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Map Card
                    item(key = "map") {
                        AeroCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = OffWhite
                        ) {
                            val density = LocalDensity.current
                            var scale by remember { mutableStateOf(1f) }
                            var offsetX by remember { mutableStateOf(0f) }
                            var offsetY by remember { mutableStateOf(0f) }

                            val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                                val newScale = (scale * zoomChange).coerceIn(1f, 4f)
                                // Clamp pan so the map doesn't go out of bounds
                                // At scale S, the map is S times larger; max pan = (S-1)/2 * dimension
                                // We apply pan in the scaled coordinate space
                                val maxX = (newScale - 1f) * 500f  // approximate half-width in px
                                val maxY = (newScale - 1f) * 300f
                                offsetX = (offsetX + panChange.x).coerceIn(-maxX, maxX)
                                offsetY = (offsetY + panChange.y).coerceIn(-maxY, maxY)
                                scale = newScale
                                tooltipRegion = null // dismiss tooltip while zooming
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2700f / 1568f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .transformable(state = transformState)
                                    .pointerInput(visitedRegions, scale, offsetX, offsetY) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                // Double-tap to reset zoom
                                                scale = 1f
                                                offsetX = 0f
                                                offsetY = 0f
                                                tooltipRegion = null
                                            },
                                            onTap = { offset ->
                                                val w = size.width.toFloat()
                                                val h = size.height.toFloat()
                                                val tapRadius = 24.dp.toPx() / scale

                                                // Convert tap position from screen space to map space
                                                val mapX = (offset.x - w / 2f - offsetX) / scale + w / 2f
                                                val mapY = (offset.y - h / 2f - offsetY) / scale + h / 2f

                                                val nearest = WorldMapRegions.regions.minByOrNull { region ->
                                                    val rx = region.x * w
                                                    val ry = region.y * h
                                                    val dx = mapX - rx
                                                    val dy = mapY - ry
                                                    sqrt(dx * dx + dy * dy)
                                                }

                                                if (nearest != null) {
                                                    val rx = nearest.x * w
                                                    val ry = nearest.y * h
                                                    val dx = mapX - rx
                                                    val dy = mapY - ry
                                                    val dist = sqrt(dx * dx + dy * dy)
                                                    if (dist <= tapRadius) {
                                                        tooltipRegion = nearest.name
                                                        // Convert back to screen space for tooltip
                                                        val screenX = (rx - w / 2f) * scale + w / 2f + offsetX
                                                        val screenY = (ry - h / 2f) * scale + h / 2f + offsetY
                                                        with(density) {
                                                            tooltipOffset = IntOffset(
                                                                screenX.toInt(),
                                                                (screenY - 48.dp.toPx()).toInt()
                                                            )
                                                        }
                                                    } else {
                                                        tooltipRegion = null
                                                    }
                                                }
                                            }
                                        )
                                    }
                            ) {
                                // Map + dots layer with zoom/pan transform
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(
                                            scaleX = scale,
                                            scaleY = scale,
                                            translationX = offsetX,
                                            translationY = offsetY
                                        )
                                ) {
                                    if (mapBitmap != null) {
                                        Image(
                                            bitmap = mapBitmap,
                                            contentDescription = "World Map",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.FillBounds
                                        )
                                    }

                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val w = size.width
                                        val h = size.height
                                        val visitedDotColor = Color(0xFF4CFF50)
                                        val unvisitedDotColor = Color.White.copy(alpha = 0.5f)
                                        val glowColor = Color(0xFF4CFF50).copy(alpha = 0.30f)
                                        val dotRadius = 2.5f.dp.toPx()
                                        val visitedDotRadius = 3.5f.dp.toPx()
                                        val glowRadius = 9.dp.toPx()

                                        WorldMapRegions.regions.forEach { region ->
                                            val cx = region.x * w
                                            val cy = region.y * h
                                            val isVisited = region.name in visitedRegions

                                            if (isVisited) {
                                                drawCircle(color = glowColor, radius = glowRadius, center = Offset(cx, cy))
                                                drawCircle(color = visitedDotColor, radius = visitedDotRadius, center = Offset(cx, cy))
                                            } else {
                                                drawCircle(color = unvisitedDotColor, radius = dotRadius, center = Offset(cx, cy))
                                            }
                                        }
                                    }
                                }

                                // Tooltip stays in screen space (outside the transform)
                                if (tooltipRegion != null) {
                                    Popup(
                                        alignment = Alignment.TopStart,
                                        offset = tooltipOffset
                                    ) {
                                        val region = tooltipRegion ?: return@Popup
                                        val flag = RegionFlags.getFlagForRegion(region)
                                        val count = regionEncounterCounts[region] ?: 0
                                        val isVisited = region in visitedRegions

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xEEFFFFFF))
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = if (isVisited) "$flag $region ($count)" else "$flag $region",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isVisited) GreenText else MediumText
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!isDualScreen) {
                    // Progress Card
                    item(key = "progress") {
                        AeroCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = OffWhite
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Progress",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkText
                                    )
                                    Text(
                                        text = "$visitedCount / $totalRegions",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = GreenText
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = if (totalRegions > 0) visitedCount.toFloat() / totalRegions else 0f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = PocketPassGreen,
                                    trackColor = Color(0xFFE0E0E0)
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                continentData.forEach { (continent, counts, _) ->
                                    val (visited, total) = counts
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = continent.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MediumText,
                                            modifier = Modifier.width(80.dp)
                                        )
                                        LinearProgressIndicator(
                                            progress = if (total > 0) visited.toFloat() / total else 0f,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp)),
                                            color = PocketPassGreen,
                                            trackColor = Color(0xFFE0E0E0)
                                        )
                                        Text(
                                            text = "$visited/$total",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MediumText,
                                            modifier = Modifier.width(36.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Milestones Card
                    item(key = "milestones") {
                        AeroCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = OffWhite
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "Milestones",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkText
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                val isDark = LocalDarkMode.current
                                WorldTourMilestone.entries.forEach { milestone ->
                                    val unlocked = milestone.isUnlocked(visitedRegions)
                                    val claimed = milestone.name in claimedMilestones

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                when {
                                                    claimed -> PocketPassGreen.copy(alpha = 0.10f)
                                                    unlocked && isDark -> Color(0xFF3A3520)
                                                    unlocked -> Color(0xFFFFF9C4)
                                                    isDark -> Color(0xFF2A2A2A)
                                                    else -> Color(0xFFF5F5F5)
                                                }
                                            )
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        claimed -> PocketPassGreen
                                                        unlocked -> Color(0xFFFFC107)
                                                        isDark -> Color(0xFF4A4A4A)
                                                        else -> Color(0xFFBDBDBD)
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (claimed) {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = if (unlocked) "!" else "?",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = milestone.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (unlocked || claimed) DarkText else MediumText
                                            )
                                            Text(
                                                text = if (milestone == WorldTourMilestone.ALL_CONTINENTS)
                                                    "Meet people from every continent"
                                                else
                                                    "Meet people from ${milestone.threshold} regions",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MediumText
                                            )
                                        }

                                        if (unlocked && !claimed) {
                                            AeroButton(
                                                onClick = {
                                                    soundManager.playSelect()
                                                    scope.launch {
                                                        prefs.claimWorldTourMilestone(
                                                            milestone.name,
                                                            milestone.reward
                                                        )
                                                    }
                                                }
                                            ) {
                                                Text("+${milestone.reward}")
                                            }
                                        } else if (claimed) {
                                            Text(
                                                text = "+${milestone.reward}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = GreenText
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Region List by Continent
                    continentData.forEach { (continent, counts, sortedRegions) ->
                        val (cVisited, cTotal) = counts
                        item(key = "continent_${continent.name}") {
                            AeroCard(
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = OffWhite
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text(
                                        text = "${continent.label} ($cVisited/$cTotal)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkText
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    sortedRegions.forEach { region ->
                                        val isVisited = region.name in visitedRegions
                                        val count = regionEncounterCounts[region.name] ?: 0
                                        val flag = RegionFlags.getFlagForRegion(region.name)

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = flag, style = MaterialTheme.typography.bodyMedium)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = region.name,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isVisited) DarkText else MediumText
                                                )
                                            }
                                            if (isVisited) {
                                                Text(
                                                    text = "$count",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = GreenText
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    } // end if (!isDualScreen)
                }
            }
    }
}

// ── Secondary screen: progress, milestones, region list ──

@Composable
fun WorldTourSecondaryScreen() {
    val scope = rememberCoroutineScope()
    val soundManager = LocalSoundManager.current
    val prefs = LocalUserPreferences.current
    val encounters = LocalEncounters.current
    val claimedMilestones by prefs.claimedWorldTourMilestonesFlow.collectAsState(initial = emptySet())

    val visitedRegions = remember(encounters) {
        encounters.map { it.origin }.filter { it.isNotBlank() }.toSet()
    }
    val regionEncounterCounts = remember(encounters) {
        encounters.filter { it.origin.isNotBlank() }.groupingBy { it.origin }.eachCount()
    }
    val visitedCount = visitedRegions.size
    val totalRegions = WorldMapRegions.TOTAL_REGIONS

    val continentData = remember(visitedRegions) {
        Continent.entries.map { continent ->
            val allRegions = WorldMapRegions.regions.filter { it.continent == continent }
            val visited = allRegions.count { it.name in visitedRegions }
            val sorted = allRegions.sortedWith(
                compareByDescending<com.pocketpass.app.util.WorldRegion> { it.name in visitedRegions }
                    .thenBy { it.name }
            )
            Triple(continent, visited to allRegions.size, sorted)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Progress
            item(key = "progress") {
                AeroCard(modifier = Modifier.fillMaxWidth(), containerColor = OffWhite) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = DarkText)
                            Text("$visitedCount / $totalRegions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = GreenText)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = if (totalRegions > 0) visitedCount.toFloat() / totalRegions else 0f,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = PocketPassGreen, trackColor = Color(0xFFE0E0E0)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        continentData.forEach { (continent, counts, _) ->
                            val (visited, total) = counts
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(continent.label, style = MaterialTheme.typography.bodySmall, color = MediumText, modifier = Modifier.width(72.dp))
                                LinearProgressIndicator(
                                    progress = if (total > 0) visited.toFloat() / total else 0f,
                                    modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                                    color = PocketPassGreen, trackColor = Color(0xFFE0E0E0)
                                )
                                Text("$visited/$total", style = MaterialTheme.typography.labelSmall, color = MediumText,
                                    modifier = Modifier.width(32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            }
                        }
                    }
                }
            }

            // Milestones
            item(key = "milestones") {
                AeroCard(modifier = Modifier.fillMaxWidth(), containerColor = OffWhite) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val isDark = LocalDarkMode.current
                        Text("Milestones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = DarkText)
                        Spacer(modifier = Modifier.height(8.dp))
                        WorldTourMilestone.entries.forEach { milestone ->
                            val unlocked = milestone.isUnlocked(visitedRegions)
                            val claimed = milestone.name in claimedMilestones
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(when {
                                        claimed -> PocketPassGreen.copy(alpha = 0.10f)
                                        unlocked && isDark -> Color(0xFF3A3520)
                                        unlocked -> Color(0xFFFFF9C4)
                                        isDark -> Color(0xFF2A2A2A)
                                        else -> Color(0xFFF5F5F5)
                                    })
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(28.dp).clip(CircleShape).background(when {
                                        claimed -> PocketPassGreen; unlocked -> Color(0xFFFFC107); isDark -> Color(0xFF4A4A4A); else -> Color(0xFFBDBDBD)
                                    }),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (claimed) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    else Text(if (unlocked) "!" else "?", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(milestone.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                                        color = if (unlocked || claimed) DarkText else MediumText)
                                }
                                if (unlocked && !claimed) {
                                    AeroButton(onClick = { soundManager.playSelect(); scope.launch { prefs.claimWorldTourMilestone(milestone.name, milestone.reward) } }) {
                                        Text("+${milestone.reward}", style = MaterialTheme.typography.labelSmall)
                                    }
                                } else if (claimed) {
                                    Text("+${milestone.reward}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = GreenText)
                                }
                            }
                        }
                    }
                }
            }

            // Region lists
            continentData.forEach { (continent, counts, sortedRegions) ->
                val (cVisited, cTotal) = counts
                item(key = "continent_${continent.name}") {
                    AeroCard(modifier = Modifier.fillMaxWidth(), containerColor = OffWhite) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("${continent.label} ($cVisited/$cTotal)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = DarkText)
                            Spacer(modifier = Modifier.height(6.dp))
                            sortedRegions.forEach { region ->
                                val isVisited = region.name in visitedRegions
                                val count = regionEncounterCounts[region.name] ?: 0
                                val flag = RegionFlags.getFlagForRegion(region.name)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(flag, style = MaterialTheme.typography.bodySmall)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(region.name, style = MaterialTheme.typography.bodySmall, color = if (isVisited) DarkText else MediumText)
                                    }
                                    if (isVisited) Text("$count", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = GreenText)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}
