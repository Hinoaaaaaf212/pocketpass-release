package com.pocketpass.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.pocketpass.app.data.EventEffectManager
import com.pocketpass.app.data.ShopCategory
import com.pocketpass.app.data.ShopItem
import com.pocketpass.app.data.ShopItems
import com.pocketpass.app.data.SpotPassRepository
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.ui.theme.AeroButton
import com.pocketpass.app.ui.theme.AeroCard
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.ErrorText
import com.pocketpass.app.ui.theme.GreenText
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.ui.theme.TokenGold
import com.pocketpass.app.data.EventEffect
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.gamepadFocusable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ShopScreen(
    onBack: () -> Unit
) {
    val soundManager = LocalSoundManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userPreferences = remember { UserPreferences(context) }
    val tokenBalance by userPreferences.tokenBalanceFlow.collectAsState(initial = 0)
    val ownedItems by userPreferences.ownedShopItemsFlow.collectAsState(initial = emptySet())
    val currentCardStyle by userPreferences.cardStyleFlow.collectAsState(initial = "classic")

    var selectedCategory by remember { mutableStateOf<ShopCategory?>(null) }
    var selectedItem by remember { mutableStateOf<ShopItem?>(null) }
    var purchaseResult by remember { mutableStateOf<String?>(null) }

    // Shop discount effect
    var activeEffects by remember { mutableStateOf<List<EventEffect>>(emptyList()) }
    LaunchedEffect(Unit) {
        activeEffects = withContext(Dispatchers.IO) {
            try { SpotPassRepository(context).getActiveEffects() } catch (_: Exception) { emptyList() }
        }
    }
    val shopPriceMultiplier = EventEffectManager.getShopPriceMultiplier(activeEffects)
    val hasDiscount = shopPriceMultiplier < 1f

    LaunchedEffect(purchaseResult) {
        if (purchaseResult != null) {
            kotlinx.coroutines.delay(2000)
            purchaseResult = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val backFocus = remember { FocusRequester() }
                        IconButton(
                            onClick = {
                                soundManager.playBack()
                                if (selectedCategory != null) {
                                    selectedCategory = null
                                } else {
                                    onBack()
                                }
                            },
                            modifier = Modifier.gamepadFocusable(
                                focusRequester = backFocus,
                                shape = CircleShape,
                                onSelect = {
                                    soundManager.playBack()
                                    if (selectedCategory != null) {
                                        selectedCategory = null
                                    } else {
                                        onBack()
                                    }
                                }
                            )
                        ) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = DarkText)
                        }
                        Text(
                            text = when (selectedCategory) {
                                ShopCategory.CARD_BORDER -> "Card Borders"
                                ShopCategory.HAT -> "Hats"
                                ShopCategory.COSTUME -> "Costumes"
                                null -> "Shop"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = DarkText,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    TokenBalanceChip(balance = tokenBalance)
                }

                purchaseResult?.let { msg ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (msg.startsWith("Not")) Color(0xFFFFCDD2) else Color(0xFFC8E6C9))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(msg, color = if (msg.startsWith("Not")) ErrorText else DarkText, fontWeight = FontWeight.Medium)
                    }
                }

                when (selectedCategory) {
                    null -> {
                        ShopCategoryList(
                            onSelectCategory = { category ->
                                soundManager.playNavigate()
                                selectedCategory = category
                            }
                        )
                    }
                    ShopCategory.CARD_BORDER -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(ShopItems.cardThemes, key = { it.id }) { item ->
                                val isOwned = item.id in ownedItems
                                val isEquipped = currentCardStyle == item.id
                                ShopItemCard(
                                    item = item,
                                    isOwned = isOwned,
                                    isEquipped = isEquipped,
                                    priceMultiplier = shopPriceMultiplier,
                                    onClick = {
                                        soundManager.playTap()
                                        if (isOwned) {
                                            coroutineScope.launch {
                                                userPreferences.saveCardStyle(item.id)
                                            }
                                            soundManager.playSelect()
                                        } else {
                                            selectedItem = item
                                        }
                                    }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                    ShopCategory.HAT, ShopCategory.COSTUME -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (selectedCategory == ShopCategory.HAT) "\uD83C\uDFA9" else "\uD83D\uDC54",
                                    style = MaterialTheme.typography.displayMedium
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Coming soon!",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MediumText,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "New items are being added regularly.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MediumText
                                )
                            }
                        }
                    }
                }
            }
    }

    selectedItem?.let { item ->
        val discountedPrice = (item.price * shopPriceMultiplier).toInt().coerceAtLeast(1)
        ShopItemDialog(
            item = item,
            tokenBalance = tokenBalance,
            priceMultiplier = shopPriceMultiplier,
            onDismiss = { selectedItem = null },
            onBuy = {
                coroutineScope.launch {
                    val spent = userPreferences.spendTokens(discountedPrice)
                    if (spent) {
                        userPreferences.purchaseShopItem(item.id)
                        userPreferences.saveCardStyle(item.id)
                        soundManager.playSuccess()
                        purchaseResult = "Purchased ${item.name}!"
                    } else {
                        soundManager.playBack()
                        purchaseResult = "Not enough tokens!"
                    }
                    selectedItem = null
                }
            }
        )
    }
}

@Composable
private fun ShopCategoryList(
    onSelectCategory: (ShopCategory) -> Unit
) {
    val soundManager = LocalSoundManager.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ShopCategoryCard(
                icon = "\uD83C\uDFA8",
                title = "Card Borders",
                description = "Customize the border style of your encounter card",
                itemCount = ShopItems.cardThemes.size,
                previewGradient = listOf(Color(0xFF4CAF50), Color(0xFF81C784), Color(0xFF2196F3), Color(0xFFE91E63)),
                onClick = { onSelectCategory(ShopCategory.CARD_BORDER) }
            )
        }

        item {
            ShopCategoryCard(
                icon = "\uD83C\uDFA9",
                title = "Hats",
                description = "Accessorize your Piip with fun headwear",
                itemCount = 0,
                comingSoon = true,
                onClick = { onSelectCategory(ShopCategory.HAT) }
            )
        }

        item {
            ShopCategoryCard(
                icon = "\uD83D\uDC54",
                title = "Costumes",
                description = "Dress up your Piip in unique outfits",
                itemCount = 0,
                comingSoon = true,
                onClick = { onSelectCategory(ShopCategory.COSTUME) }
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun ShopCategoryCard(
    icon: String,
    title: String,
    description: String,
    itemCount: Int,
    previewGradient: List<Color>? = null,
    comingSoon: Boolean = false,
    onClick: () -> Unit
) {
    AeroCard(
        modifier = Modifier
            .fillMaxWidth()
            .gamepadFocusable(
                shape = RoundedCornerShape(16.dp),
                onSelect = onClick
            ),
        cornerRadius = 16.dp,
        elevation = 4.dp,
        containerColor = if (comingSoon) OffWhite.copy(alpha = 0.7f) else OffWhite,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (previewGradient != null) Brush.linearGradient(previewGradient)
                        else Brush.linearGradient(listOf(Color(0xFFE0E0E0), Color(0xFFBDBDBD)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.headlineLarge
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MediumText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (comingSoon) "Coming soon" else "$itemCount items",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (comingSoon) MediumText else GreenText,
                    fontWeight = FontWeight.Bold
                )
            }

            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Open",
                tint = if (comingSoon) MediumText else GreenText,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun ShopItemCard(
    item: ShopItem,
    isOwned: Boolean,
    isEquipped: Boolean,
    priceMultiplier: Float = 1f,
    onClick: () -> Unit
) {
    val discountedPrice = (item.price * priceMultiplier).toInt().coerceAtLeast(1)
    val hasDiscount = priceMultiplier < 1f
    AeroCard(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.9f),
        cornerRadius = 16.dp,
        elevation = 4.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            val colors = item.previewColors?.map { Color(it.toInt()) } ?: listOf(Color.Gray, Color.LightGray)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(colors))
                    .then(
                        if (isEquipped) Modifier.border(3.dp, PocketPassGreen, RoundedCornerShape(12.dp))
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!isOwned) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "Locked",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else if (isEquipped) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(PocketPassGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Equipped",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${item.icon} ${item.name}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = DarkText,
                textAlign = TextAlign.Center
            )

            if (isEquipped) {
                Text(
                    text = "Equipped",
                    style = MaterialTheme.typography.labelSmall,
                    color = GreenText,
                    fontWeight = FontWeight.Bold
                )
            } else if (isOwned) {
                Text(
                    text = "Owned",
                    style = MaterialTheme.typography.labelSmall,
                    color = MediumText
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "\uD83E\uDE99",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    if (hasDiscount) {
                        Text(
                            text = "${item.price}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF999999),
                            textDecoration = TextDecoration.LineThrough
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$discountedPrice",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    } else {
                        Text(
                            text = "${item.price}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TokenGold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopItemDialog(
    item: ShopItem,
    tokenBalance: Int,
    priceMultiplier: Float = 1f,
    onDismiss: () -> Unit,
    onBuy: () -> Unit
) {
    val discountedPrice = (item.price * priceMultiplier).toInt().coerceAtLeast(1)
    val hasDiscount = priceMultiplier < 1f
    val canAfford = tokenBalance >= discountedPrice
    val colors = item.previewColors?.map { Color(it.toInt()) } ?: listOf(Color.Gray, Color.LightGray)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${item.icon} ${item.name}", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(colors))
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(item.description, color = MediumText)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Price: ", fontWeight = FontWeight.Medium, color = DarkText)
                    if (hasDiscount) {
                        Text(
                            "\uD83E\uDE99 ${item.price}",
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF999999),
                            textDecoration = TextDecoration.LineThrough
                        )
                        Text(
                            " \uD83E\uDE99 $discountedPrice",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    } else {
                        Text("\uD83E\uDE99 ${item.price}", fontWeight = FontWeight.Bold, color = TokenGold)
                    }
                }
                if (!canAfford) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Not enough tokens!",
                        color = Color(0xFFE53935),
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            AeroButton(
                onClick = onBuy,
                enabled = canAfford
            ) {
                Text("Buy for \uD83E\uDE99 $discountedPrice")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
