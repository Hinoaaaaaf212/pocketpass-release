package com.pocketpass.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.ui.theme.AeroButton
import com.pocketpass.app.ui.theme.AeroCard
import com.pocketpass.app.ui.theme.BackgroundGradient
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.RegionFlags
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    onProfileSaved: () -> Unit
) {
    val soundManager = LocalSoundManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userPreferences = remember { UserPreferences(context) }

    var age by remember { mutableStateOf("") }
    var hobbies by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var regionExpanded by remember { mutableStateOf(false) }

    val backgroundBrush = Brush.verticalGradient(
        colors = BackgroundGradient
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        AeroCard(
            modifier = Modifier
                .fillMaxWidth(0.9f),
            cornerRadius = 32.dp,
            containerColor = OffWhite
        ) {
          Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
                text = "About You",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = DarkText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tell others a little about yourself!",
                style = MaterialTheme.typography.bodyMedium,
                color = DarkText.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            val textFieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PocketPassGreen,
                unfocusedBorderColor = DarkText.copy(alpha = 0.3f),
                cursorColor = PocketPassGreen
            )

            OutlinedTextField(
                value = age,
                onValueChange = { newVal ->
                    val digits = newVal.filter { it.isDigit() }.take(2)
                    age = if (digits.isNotEmpty() && digits.toInt() > 99) "99" else digits
                },
                label = { Text("Age (Optional)") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = hobbies,
                onValueChange = { hobbies = com.pocketpass.app.ui.theme.formatHobbiesInput(it) },
                label = { Text("Hobbies (Optional)") },
                supportingText = { Text("Separate hobbies with spaces") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = textFieldColors
            )

            if (hobbies.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                com.pocketpass.app.ui.theme.HobbyChips(hobbies)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Region Dropdown
            Box {
                OutlinedTextField(
                    value = if (region.isNotBlank()) "${RegionFlags.getFlagForRegion(region)} $region" else "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Region (Required)") },
                    trailingIcon = {
                        Icon(Icons.Filled.ArrowDropDown, "Dropdown")
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { regionExpanded = !regionExpanded },
                    colors = textFieldColors,
                    enabled = false
                )
                androidx.compose.material3.DropdownMenu(
                    expanded = regionExpanded,
                    onDismissRequest = { regionExpanded = false },
                    modifier = Modifier.height(250.dp)
                ) {
                    LazyColumn {
                        items(RegionFlags.supportedRegions, key = { it }) { regionOption ->
                            DropdownMenuItem(
                                text = {
                                    Text("${RegionFlags.getFlagForRegion(regionOption)} $regionOption")
                                },
                                onClick = {
                                    soundManager.playSelect()
                                    region = regionOption
                                    regionExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            AeroButton(
                onClick = {
                    soundManager.playSuccess()
                    coroutineScope.launch {
                        // Username already set from signup — only save the remaining fields
                        userPreferences.saveProfileDetails(age, hobbies, region)
                        onProfileSaved()
                    }
                },
                containerColor = PocketPassGreen,
                contentColor = OffWhite,
                cornerRadius = 24.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = region.isNotBlank()
            ) {
                Text(
                    text = "Finish",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
          }
        }
    }
}