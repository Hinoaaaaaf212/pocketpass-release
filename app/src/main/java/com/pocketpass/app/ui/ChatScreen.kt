package com.pocketpass.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketpass.app.data.CachedMessage
import com.pocketpass.app.data.MessageRepository
import com.pocketpass.app.data.StreakSync
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.data.calculateStreak
import com.pocketpass.app.data.getCurrentTier
import com.pocketpass.app.data.hasSentMessageToday
import com.pocketpass.app.ui.theme.BackgroundGradient
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.LightText
import com.pocketpass.app.ui.theme.LocalAppDimensions
import com.pocketpass.app.ui.theme.LocalDarkMode
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.gamepadFocusable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    friendId: String,
    friendName: String,
    friendAvatarHex: String,
    onBack: () -> Unit
) {
    val soundManager = LocalSoundManager.current
    val context = LocalContext.current
    val isDark = LocalDarkMode.current
    val coroutineScope = rememberCoroutineScope()

    val messageRepo = remember { MessageRepository(context) }
    val userPrefs = remember { UserPreferences(context) }
    val myUserId = messageRepo.currentUserId ?: ""

    val messages by messageRepo.getConversationFlow(friendId).collectAsState(initial = emptyList())
    val listState = rememberLazyListState()

    var messageText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    // Streak state
    val streakDays = remember(messages) { calculateStreak(messages, myUserId, friendId) }
    val streakTier = remember(streakDays) { getCurrentTier(streakDays) }
    val sentToday = remember(messages) { hasSentMessageToday(messages, myUserId) }

    // Streak celebration
    var showStreakCelebration by remember { mutableStateOf(false) }
    var celebrationTier by remember { mutableStateOf<com.pocketpass.app.data.StreakTier?>(null) }

    LaunchedEffect(streakTier) {
        if (streakTier != null) {
            val claimed = userPrefs.claimedStreakRewardsFlow.first()
            val rewardKey = "${friendId}_${streakTier.name}"
            if (rewardKey !in claimed) {
                celebrationTier = streakTier
                showStreakCelebration = true
                userPrefs.claimStreakReward(friendId, streakTier)
                // Sync to Supabase
                if (myUserId.isNotEmpty()) {
                    val updatedClaimed = userPrefs.claimedStreakRewardsFlow.first()
                    StreakSync.pushStreak(myUserId, friendId, streakDays, updatedClaimed)
                }
            }
        }
    }

    // Load conversation from server and mark as read
    LaunchedEffect(friendId) {
        withContext(Dispatchers.IO) {
            messageRepo.loadConversation(friendId)
            messageRepo.markConversationRead(friendId)
        }
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Listen for incoming messages and play sound
    LaunchedEffect(Unit) {
        messageRepo.incomingMessages.collect { msg ->
            if (msg.senderId == friendId) {
                soundManager.playMessageReceived()
                withContext(Dispatchers.IO) {
                    messageRepo.markConversationRead(friendId)
                }
            }
        }
    }

    val sendMessage = {
        val text = messageText.trim()
        if (text.isNotEmpty() && !isSending) {
            isSending = true
            messageText = ""
            soundManager.playTap()
            coroutineScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        messageRepo.sendMessage(friendId, text)
                    }
                } catch (_: Exception) {
                } finally {
                    isSending = false
                }
            }
        }
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isDark) Color(0xFF2A2A2A).copy(alpha = 0.9f)
                            else Color(0xFFF8F8F8).copy(alpha = 0.9f)
                        )
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

                    // Friend avatar
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
                        if (friendAvatarHex.isNotEmpty()) {
                            MiiAvatarViewer(hexData = friendAvatarHex)
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = friendName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DarkText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Streak badge
                    if (streakDays >= 3 && streakTier != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("\uD83D\uDD25", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = "${streakDays}",
                            color = Color(streakTier.color.toInt()),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Spacer(modifier = Modifier.weight(0.01f))
                }

                // Streak celebration banner
                if (showStreakCelebration && celebrationTier != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(celebrationTier!!.color.toInt()).copy(alpha = 0.15f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("\uD83D\uDD25", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${celebrationTier!!.label} Streak! ${streakDays} days",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(celebrationTier!!.color.toInt()),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "You earned ${celebrationTier!!.reward} tokens!",
                                    color = Color(0xFFFFD700),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { showStreakCelebration = false }) {
                                Text(
                                    text = "\u2715",
                                    color = DarkText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Streak warning — active streak but haven't sent today
                if (streakDays >= 3 && !sentToday && !showStreakCelebration) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF3E0))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Send a message to keep your \uD83D\uDD25${streakDays} streak!",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE65100),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Messages list
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    if (messages.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No messages yet.\nSay hello!",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MediumText,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    items(messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            isFromMe = message.senderId == myUserId,
                            friendAvatarHex = friendAvatarHex
                        )
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                // Input bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isDark) Color(0xFF2A2A2A).copy(alpha = 0.95f)
                            else Color(0xFFF8F8F8).copy(alpha = 0.95f)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { if (it.length <= 500) messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text("Type a message...", color = MediumText)
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PocketPassGreen,
                            unfocusedBorderColor = if (isDark) Color(0xFF444444) else Color(0xFFCCCCCC),
                            focusedContainerColor = if (isDark) Color(0xFF333333) else OffWhite,
                            unfocusedContainerColor = if (isDark) Color(0xFF333333) else OffWhite,
                            focusedTextColor = DarkText,
                            unfocusedTextColor = DarkText
                        ),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendMessage() })
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val sendFocus = remember { FocusRequester() }
                    IconButton(
                        onClick = sendMessage,
                        enabled = messageText.trim().isNotEmpty() && !isSending,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (messageText.trim().isNotEmpty() && !isSending) PocketPassGreen
                                else if (isDark) Color(0xFF444444) else Color(0xFFDDDDDD),
                                CircleShape
                            )
                            .gamepadFocusable(
                                focusRequester = sendFocus,
                                shape = CircleShape,
                                onSelect = sendMessage
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = "Send",
                            tint = if (messageText.trim().isNotEmpty() && !isSending) Color.White else MediumText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Miiverse-style speech bubble ──

@Composable
private fun MessageBubble(
    message: CachedMessage,
    isFromMe: Boolean,
    friendAvatarHex: String
) {
    val isDark = LocalDarkMode.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // Friend's avatar on the left for their messages
        if (!isFromMe) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (friendAvatarHex.isNotEmpty()) {
                    MiiAvatarViewer(hexData = friendAvatarHex)
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        Column(
            horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start
        ) {
            // Speech bubble
            Card(
                shape = if (isFromMe) {
                    RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                } else {
                    RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
                },
                colors = CardDefaults.cardColors(
                    containerColor = if (isFromMe) PocketPassGreen
                    else if (isDark) Color(0xFF3A3A3A) else OffWhite
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.widthIn(max = LocalAppDimensions.current.chatBubbleMaxWidth)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isFromMe) Color.White else DarkText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            // Timestamp + read indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatMessageTime(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = LightText
                )
                if (isFromMe && message.readAt != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Read",
                        style = MaterialTheme.typography.labelSmall,
                        color = PocketPassGreen,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun formatMessageTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val dayMs = 24 * 60 * 60 * 1000L

    return if (diff < dayMs) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
    } else {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}
