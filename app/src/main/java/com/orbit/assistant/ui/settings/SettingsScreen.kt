package com.orbit.assistant.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.assistant.BuildConfig
import com.orbit.assistant.data.preferences.AutonomyLevel
import com.orbit.assistant.data.preferences.ExecutionBehavior
import com.orbit.assistant.ui.components.OrbitCard
import com.orbit.assistant.ui.theme.OrbitTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = OrbitTheme.colors
    val snackbarHostState = remember { SnackbarHostState() }

    var showLicensesDialog by remember { mutableStateOf(false) }
    var showAppsDialog by remember { mutableStateOf(false) }
    var brandsDraft by remember(state.settings.preferredBrands) {
        mutableStateOf(state.settings.preferredBrands)
    }
    var categoriesDraft by remember(state.settings.preferredCategories) {
        mutableStateOf(state.settings.preferredCategories)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.background(colors.background)) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
                HorizontalDivider(color = colors.borderSubtle)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Appearance
            item {
                SectionLabel("Appearance")
                OrbitCard(containerColor = colors.surface, borderColor = colors.borderSubtle) {
                    SettingsSwitchRow(
                        title = "Dark theme",
                        subtitle = "Deep charcoal interface (default)",
                        checked = state.settings.isDarkMode,
                        onCheckedChange = viewModel::setDarkMode
                    )
                }
            }

            // Agent Settings
            item {
                SectionLabel("Agent settings")
                OrbitCard(containerColor = colors.surface, borderColor = colors.borderSubtle) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Autonomy level",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.settings.autonomyLevel.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AutonomyLevel.entries.forEach { level ->
                                val selected = state.settings.autonomyLevel == level
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setAutonomyLevel(level) },
                                    label = { Text(level.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.accentContainer,
                                        selectedLabelColor = colors.accentPrimary
                                    )
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = colors.borderSubtle)
                    SettingsSwitchRow(
                        title = "Ask before sensitive actions",
                        subtitle = "Pause for confirmation on account or privacy changes",
                        checked = state.settings.confirmSensitiveActions,
                        onCheckedChange = viewModel::setConfirmSensitive
                    )
                    HorizontalDivider(color = colors.borderSubtle)
                    SettingsSwitchRow(
                        title = "Confirm before purchases",
                        subtitle = "Always require human approval before checkout steps",
                        checked = state.settings.confirmBeforePurchases,
                        onCheckedChange = viewModel::setConfirmPurchases
                    )
                    HorizontalDivider(color = colors.borderSubtle)
                    SettingsSwitchRow(
                        title = "Confirm before sending messages",
                        subtitle = "Review drafts before sending SMS, email, or chat",
                        checked = state.settings.confirmBeforeSendingMessages,
                        onCheckedChange = viewModel::setConfirmMessages
                    )
                    HorizontalDivider(color = colors.borderSubtle)
                    SettingsSwitchRow(
                        title = "Confirm before destructive actions",
                        subtitle = "Require confirmation before deleting data or cancelling orders",
                        checked = state.settings.confirmBeforeDestructiveActions,
                        onCheckedChange = viewModel::setConfirmDestructive
                    )
                }
            }

            // App Permissions
            item {
                SectionLabel("App permissions")
                OrbitCard(containerColor = colors.surface, borderColor = colors.borderSubtle) {
                    state.permissions.forEachIndexed { index, perm ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.openPermissionSettings(perm.id) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = perm.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.textPrimary
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(
                                                if (perm.isGranted) colors.statusSuccessContainer
                                                else colors.surfaceElevated
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = perm.statusLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (perm.isGranted) colors.statusSuccess else colors.textSecondary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = perm.rationale,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = "Open system settings for ${perm.title}",
                                tint = colors.textTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (index < state.permissions.lastIndex) {
                            HorizontalDivider(color = colors.borderSubtle)
                        }
                    }
                }
            }

            // Discovered Apps
            item {
                SectionLabel("Installed app discovery")
                OrbitCard(
                    onClick = { showAppsDialog = true },
                    containerColor = colors.surface,
                    borderColor = colors.borderSubtle
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Apps, null, tint = colors.accentPrimary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Discovered launchable apps (${state.discoveredApps.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "${state.discoveredApps.count { it.isSupported }} supported integration targets detected",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            // Personalization
            item {
                SectionLabel("Personalization")
                OrbitCard(containerColor = colors.surface, borderColor = colors.borderSubtle) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Preferred shopping apps",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Blinkit", "Zepto", "Swiggy Instamart", "BigBasket", "Amazon").forEach { app ->
                                val selected = app in state.settings.preferredShoppingApps
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.togglePreferredShoppingApp(app) },
                                    label = { Text(app) },
                                    leadingIcon = if (selected) {
                                        { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }

                        OutlinedTextField(
                            value = brandsDraft,
                            onValueChange = {
                                brandsDraft = it
                                viewModel.setPreferredBrands(it)
                            },
                            label = { Text("Preferred brands") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = categoriesDraft,
                            onValueChange = {
                                categoriesDraft = it
                                viewModel.setPreferredCategories(it)
                            },
                            label = { Text("Preferred product categories") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Text(
                            text = "Preferred language",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("English", "Hindi", "Telugu", "Tamil").forEach { lang ->
                                FilterChip(
                                    selected = state.settings.preferredLanguage == lang,
                                    onClick = { viewModel.setPreferredLanguage(lang) },
                                    label = { Text(lang) }
                                )
                            }
                        }

                        Text(
                            text = "Default execution behavior",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExecutionBehavior.entries.forEach { behavior ->
                                FilterChip(
                                    selected = state.settings.defaultExecutionBehavior == behavior,
                                    onClick = { viewModel.setExecutionBehavior(behavior) },
                                    label = { Text(behavior.label) }
                                )
                            }
                        }
                    }
                }
            }

            // Privacy & Local Data
            item {
                SectionLabel("Privacy & local data")
                OrbitCard(containerColor = colors.surface, borderColor = colors.borderSubtle) {
                    SettingsSwitchRow(
                        title = "Retain execution screenshots locally",
                        subtitle = "Keep simulated/observed screen frames in local cache",
                        checked = state.settings.retainScreenshotsLocally,
                        onCheckedChange = viewModel::setScreenshotRetention
                    )
                    HorizontalDivider(color = colors.borderSubtle)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.clearTaskHistory() }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, null, tint = colors.statusError)
                        Column {
                            Text(
                                text = "Clear task history",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.statusError
                            )
                            Text(
                                text = "Remove all locally persisted tasks from Room",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                    HorizontalDivider(color = colors.borderSubtle)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.clearAgentMemory() }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, null, tint = colors.statusWarning)
                        Column {
                            Text(
                                text = "Clear agent memory",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Reset learned shopping apps, brands, and category preferences",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            // About
            item {
                SectionLabel("About")
                OrbitCard(containerColor = colors.surface, borderColor = colors.borderSubtle) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("App version", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                            Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Agent runtime", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                            Text("Demo Runtime v0.1 (Offline)", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                        }
                    }
                    HorizontalDivider(color = colors.borderSubtle)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLicensesDialog = true }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.Info, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Open-source licenses",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text("Open-source licenses") },
            text = {
                Text(
                    "Orbit uses Jetpack Compose, Material 3, AndroidX Room, DataStore, Navigation Compose, Hilt, Kotlinx Serialization, Coroutines, Retrofit, OkHttp, and Coil under Apache 2.0."
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) { Text("Close") }
            }
        )
    }

    if (showAppsDialog) {
        AlertDialog(
            onDismissRequest = { showAppsDialog = false },
            title = { Text("Discovered Apps") },
            text = {
                LazyColumn(
                    modifier = Modifier.height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.discoveredApps.size) { index ->
                        val app = state.discoveredApps[index]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.appName, style = MaterialTheme.typography.bodyMedium)
                                Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                            }
                            if (app.isSupported) {
                                Text("Supported", style = MaterialTheme.typography.labelSmall, color = colors.accentPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppsDialog = false }) { Text("Done") }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = OrbitTheme.colors.textTertiary,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = OrbitTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.textPrimary,
                checkedTrackColor = colors.accentPrimary
            )
        )
    }
}
