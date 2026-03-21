package com.pocketpass.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.pocketpass.app.data.Encounter
import com.pocketpass.app.data.UserPreferences

val LocalEncounters = staticCompositionLocalOf<List<Encounter>> { emptyList() }
val LocalUserPreferences = staticCompositionLocalOf<UserPreferences> { error("UserPreferences not provided") }
