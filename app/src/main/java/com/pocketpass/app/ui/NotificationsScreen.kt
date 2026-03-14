package com.pocketpass.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketpass.app.data.NotificationRepository
import com.pocketpass.app.data.SupabaseNotification
import com.pocketpass.app.ui.theme.AeroCard
import com.pocketpass.app.ui.theme.BackgroundGradient
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.LocalDarkMode
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.gamepadFocusable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onNavigateToChat: (friendId: String, friendName: String, friendAvatarHex: String) -> Unit = { _, _, _ -> },
    onNavigateToFriends: () -> Unit = {}
) {
    val soundManager = LocalSoundManager.current
    val isDark = LocalDarkMode.current
    val coroutineScope = rememberCoroutineScope()

    val notifRepo = remember { NotificationRepository() }
    var notifications by remember { mutableStateOf<List<SupabaseNotification>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            notifications = notifRepo.getNotifications()
        }
        isLoading = false
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(modifier = Modifier.fillMaxSize()) {
        CheckeredBackground(
            modifier = Modifier.fillMaxSize(),
            gradientColors = BackgroundGradient
        )

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
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
                        text = "Notifications",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DarkText,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f)
                    )
                    if (notifications.any { !it.read }) {
                        TextButton(
                            onClick = {
                                soundManager.playTap()
                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) {
                                        notifRepo.markAllAsRead()
                                    }
                                    // Refresh list
                                    notifications = notifications.map { it.copy(read = true) }
                                }
                            }
                        ) {
                            Text(
                                text = "Mark all read",
                                style = MaterialTheme.typography.labelMedium,
                                color = PocketPassGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = PocketPassGreen)
                        }
                    }
                    notifications.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "No Notifications",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = DarkText
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "You're all caught up!",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MediumText,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(notifications, key = { it.id }) { notification ->
                                NotificationCard(
                                    notification = notification,
                                    onClick = {
                                        soundManager.playSelect()
                                        // Mark as read
                                        if (!notification.read) {
                                            coroutineScope.launch {
                                                withContext(Dispatchers.IO) {
                                                    notifRepo.markAsRead(notification.id)
                                                }
                                                notifications = notifications.map {
                                                    if (it.id == notification.id) it.copy(read = true) else it
                                                }
                                            }
                                        }
                                        // Navigate based on type
                                        when (notification.type) {
                                            "new_message" -> {
                                                if (notification.relatedUserId != null) {
                                                    onNavigateToChat(
                                                        notification.relatedUserId,
                                                        notification.relatedUserName,
                                                        notification.relatedUserAvatarHex
                                                    )
                                                }
                                            }
                                            "friend_request", "friend_accepted" -> {
                                                onNavigateToFriends()
                                            }
                                        }
                                    }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: SupabaseNotification,
    onClick: () -> Unit
) {
    val isDark = LocalDarkMode.current

    val icon = when (notification.type) {
        "friend_request" -> "👋"
        "friend_accepted" -> "🤝"
        "new_message" -> "💬"
        "new_encounter" -> "🌟"
        else -> "🔔"
    }

    AeroCard(
        modifier = Modifier
            .fillMaxWidth()
            .gamepadFocusable(
                shape = RoundedCornerShape(16.dp),
                onSelect = onClick
            )
            .clickable(onClick = onClick),
        cornerRadius = 16.dp,
        elevation = if (!notification.read) 3.dp else 1.dp,
        containerColor = if (!notification.read) {
            if (isDark) Color(0xFF1A3A1A) else Color(0xFFEFF8EF)
        } else {
            OffWhite
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Unread indicator dot
            if (!notification.read) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(PocketPassGreen, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Type icon
            Text(
                text = icon,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Avatar (if available)
            if (notification.relatedUserAvatarHex.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    MiiAvatarViewer(hexData = notification.relatedUserAvatarHex)
                }
                Spacer(modifier = Modifier.width(10.dp))
            }

            // Title + body
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (!notification.read) FontWeight.Bold else FontWeight.SemiBold,
                    color = DarkText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (notification.body.isNotEmpty()) {
                    Text(
                        text = notification.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MediumText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Timestamp
            val timeText = notification.createdAt?.let { formatNotificationTime(it) } ?: ""
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelSmall,
                color = MediumText
            )
        }
    }
}

private fun formatNotificationTime(isoTimestamp: String): String {
    return try {
        val instant = Instant.parse(isoTimestamp)
        val millis = instant.toEpochMilli()
        val now = System.currentTimeMillis()
        val diff = now - millis
        when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Now"
            diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m"
            diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h"
            diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(millis))
        }
    } catch (_: Exception) {
        ""
    }
}
