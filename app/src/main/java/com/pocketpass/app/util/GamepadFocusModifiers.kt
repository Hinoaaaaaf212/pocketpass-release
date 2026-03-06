package com.pocketpass.app.util

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import com.pocketpass.app.ui.theme.GamepadFocusBorder

@Composable
fun Modifier.gamepadFocusable(
    focusRequester: FocusRequester = remember { FocusRequester() },
    shape: Shape = RoundedCornerShape(12.dp),
    onSelect: (() -> Unit)? = null
): Modifier {
    val gamepadState = LocalGamepadState.current
    var isFocused by remember { mutableStateOf(false) }

    val borderAlpha by animateFloatAsState(
        targetValue = if (isFocused && gamepadState.isGamepadActive) 1f else 0f,
        animationSpec = tween(150),
        label = "focusBorder"
    )

    return this
        .focusRequester(focusRequester)
        .onFocusChanged { focusState ->
            isFocused = focusState.isFocused
        }
        .then(
            if (borderAlpha > 0f) {
                Modifier.border(
                    width = 3.dp,
                    color = GamepadFocusBorder.copy(alpha = borderAlpha),
                    shape = shape
                )
            } else Modifier
        )
        .onKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown) {
                val keyCode = keyEvent.nativeKeyEvent.keyCode
                if (keyCode == AndroidKeyEvent.KEYCODE_BUTTON_A ||
                    keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                    keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER
                ) {
                    onSelect?.invoke()
                    return@onKeyEvent onSelect != null
                }
            }
            false
        }
        .focusable()
}

fun Modifier.gamepadGridNavigation(
    columns: Int,
    currentIndex: Int,
    totalItems: Int,
    focusRequesters: List<FocusRequester>
): Modifier = this.onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

    val targetIndex = when (event.nativeKeyEvent.keyCode) {
        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> (currentIndex - 1).coerceAtLeast(0)
        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> (currentIndex + 1).coerceAtMost(totalItems - 1)
        AndroidKeyEvent.KEYCODE_DPAD_UP -> (currentIndex - columns).coerceAtLeast(0)
        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> (currentIndex + columns).coerceAtMost(totalItems - 1)
        else -> return@onPreviewKeyEvent false
    }

    if (targetIndex != currentIndex && targetIndex in focusRequesters.indices) {
        try {
            focusRequesters[targetIndex].requestFocus()
        } catch (_: Exception) {}
        true
    } else false
}
