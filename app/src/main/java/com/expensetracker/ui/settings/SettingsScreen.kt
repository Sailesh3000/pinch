package com.expensetracker.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.extraction.ModelSource
import com.expensetracker.ingestion.TransactionNotificationListenerService
import com.expensetracker.ui.theme.Coral
import com.expensetracker.ui.theme.CoralSoft
import com.expensetracker.ui.theme.MintGlow
import com.expensetracker.ui.theme.MistTeal
import com.expensetracker.ui.theme.PinchTeal

private const val PRIVACY_POLICY_URL =
    "https://raw.githubusercontent.com/Sailesh3000/pinch/main/docs/PRIVACY_POLICY.md"
private const val TERMS_OF_SERVICE_URL =
    "https://raw.githubusercontent.com/Sailesh3000/pinch/main/docs/TERMS_OF_SERVICE.md"

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    LifecycleResumeEffect(Unit) {
        val enabled = notificationListenerEnabled(context)
        viewModel.refreshPermissionStatus(enabled)
        viewModel.refreshAiStatus()
        onPauseOrDispose { }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.let { viewModel.exportTransactions(it) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Branded Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "settings",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.8).sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "permissions & engine.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Permission Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (uiState.listenerEnabled) MintGlow else CoralSoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.NotificationsActive,
                                    contentDescription = null,
                                    tint = if (uiState.listenerEnabled) PinchTeal else Coral,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Notification Listener",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                )
                                Text(
                                    text = if (uiState.listenerEnabled) "Active · Tracking transactions" else "Disabled · Not capturing",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (uiState.listenerEnabled) PinchTeal else Coral,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.listenerEnabled) MaterialTheme.colorScheme.surfaceVariant else PinchTeal,
                                contentColor = if (uiState.listenerEnabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                            ),
                        ) {
                            Text(
                                "Manage Android notification access",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }

            // AI Engine Card
            item {
                AiEngineCard(uiState = uiState, onDownload = { viewModel.downloadModel() })
            }

            // Data Management Card
            item {
                DataManagementCard(
                    isExporting = uiState.isExporting,
                    exportResult = uiState.exportResult,
                    onExport = { exportLauncher.launch("pinch_transactions.csv") },
                )
            }

            // Monitored Packages Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "MONITORED APPS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    )
                }
            }

            items(uiState.monitoredPackages, key = { it.id }) { pkg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                pkg.appLabel,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Text(
                                pkg.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = pkg.isEnabled,
                            onCheckedChange = { viewModel.setPackageEnabled(pkg.packageName, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PinchTeal,
                            ),
                        )
                    }
                }
            }

            // Add Package
            item {
                var packageName by remember { mutableStateOf("") }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            "Add Custom Package",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = packageName,
                            onValueChange = { packageName = it },
                            placeholder = { Text("e.g. com.example.bank") },
                            singleLine = true,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PinchTeal,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (viewModel.addCustomPackage(packageName)) packageName = ""
                            },
                            enabled = packageName.isNotBlank(),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = PinchTeal),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Add App Package")
                        }
                    }
                }
            }

            // Legal Links
            item {
                LegalCard(
                    onPrivacyClick = { uriHandler.openUri(PRIVACY_POLICY_URL) },
                    onTermsClick = { uriHandler.openUri(TERMS_OF_SERVICE_URL) },
                )
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun AiEngineCard(uiState: SettingsUiState, onDownload: () -> Unit) {
    var continueWithRegex by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MintGlow),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.SmartToy,
                        contentDescription = null,
                        tint = PinchTeal,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "On-Device AI Engine",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = "Active: ${uiState.aiEngineName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PinchTeal,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = uiState.aiTierExplanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Bundled model (Play Asset Delivery) — nothing to download.
            if (uiState.modelSource == ModelSource.BUNDLED) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = PinchTeal, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AI Model: Bundled with app",
                        style = MaterialTheme.typography.bodySmall,
                        color = PinchTeal,
                    )
                }
            }

            // Bundled already has its own confirmation row above; showing the
            // download prompt on top of it contradicts "AI Model: Bundled with
            // app" and is exactly the "why does it still ask to download"
            // confusion this branch used to cause.
            if (uiState.nanoAvailable == false && uiState.modelSource != ModelSource.BUNDLED) {
                when {
                    uiState.modelSource == ModelSource.DOWNLOADED -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = PinchTeal, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Qwen2.5-0.5B model installed locally",
                                style = MaterialTheme.typography.bodySmall,
                                color = PinchTeal,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "AI Model: Downloaded separately",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    uiState.modelDownloading -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column {
                            Text(
                                text = "Downloading model...",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (uiState.modelDownloadProgress >= 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { uiState.modelDownloadProgress / 100f },
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                                    color = PinchTeal,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${uiState.modelDownloadProgress}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Model unavailable — show actionable error/decline states.
                    continueWithRegex -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Using regex extraction. Transactions will still be tracked, but categorization may be less accurate. You can download the AI model anytime from Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    uiState.downloadError != null -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Model download failed: ${uiState.downloadError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Coral,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onDownload,
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = PinchTeal),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Retry Download")
                            }
                            OutlinedButton(
                                onClick = { continueWithRegex = true },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Continue with regex")
                            }
                        }
                    }

                    else -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Your device doesn't support Gemini Nano. Download the on-device AI model for better accuracy, or continue with regex-based extraction.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onDownload,
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = PinchTeal),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Download AI Model")
                            }
                            OutlinedButton(
                                onClick = { continueWithRegex = true },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Continue with regex")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DataManagementCard(
    isExporting: Boolean,
    exportResult: ExportResult?,
    onExport: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CoralSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = Coral,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Data Management",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = "Your data lives on-device. Export it anytime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = onExport,
                enabled = !isExporting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
            ) {
                Icon(
                    imageVector = Icons.Filled.IosShare,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isExporting) "Exporting..." else "Export to CSV")
            }

            exportResult?.let { result ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = result.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.success) PinchTeal else Coral,
                )
            }
        }
    }
}

@Composable
private fun LegalCard(onPrivacyClick: () -> Unit, onTermsClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                "LEGAL",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(onClick = onPrivacyClick, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth()) {
                Text("Privacy Policy")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onTermsClick, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth()) {
                Text("Terms of Service")
            }
        }
    }
}

private fun notificationListenerEnabled(context: android.content.Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    val expected = context.packageName + "/" + TransactionNotificationListenerService::class.java.name
    return flat.split(":").any { it == expected }
}