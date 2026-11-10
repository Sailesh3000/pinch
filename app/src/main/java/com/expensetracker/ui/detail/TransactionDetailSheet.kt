package com.expensetracker.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.core.common.Formatters
import com.expensetracker.core.model.Category
import com.expensetracker.core.model.Transaction
import com.expensetracker.core.model.TransactionType
import com.expensetracker.ui.components.CategoryChipPicker
import com.expensetracker.ui.components.ConfidenceDot
import com.expensetracker.ui.theme.Coral
import com.expensetracker.ui.theme.CoralSoft
import com.expensetracker.ui.theme.MintGlow
import com.expensetracker.ui.theme.MistTeal
import com.expensetracker.ui.theme.PinchTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    transaction: Transaction,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onUpdateCategory: (transactionId: Long, categoryId: Long) -> Unit,
    onDelete: () -> Unit,
    onUpdateAmount: (transactionId: Long, amount: Double) -> Unit = { _, _ -> },
) {
    var showRawText by remember { mutableStateOf(false) }
    var editingAmount by remember { mutableStateOf(false) }
    var amountText by remember(transaction.id) { mutableStateOf(plainAmount(transaction.amount)) }
    val isCredit = transaction.txnType == TransactionType.CREDIT

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            // Amount & Type Header (tap pencil to correct a mis-extracted amount)
            if (editingAmount) {
                val parsed = amountText.toDoubleOrNull()
                val valid = parsed != null && parsed > 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount") },
                        isError = !valid,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PinchTeal),
                    )
                    IconButton(
                        enabled = valid,
                        onClick = {
                            if (parsed != null && parsed != transaction.amount) {
                                onUpdateAmount(transaction.id, parsed)
                            }
                            editingAmount = false
                        },
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Save amount", tint = PinchTeal)
                    }
                    IconButton(onClick = {
                        amountText = plainAmount(transaction.amount)
                        editingAmount = false
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel edit")
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = (if (isCredit) "+" else "-") + " " + Formatters.money(transaction.amount, transaction.currency),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                        ),
                        color = if (isCredit) PinchTeal else MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = {
                        amountText = plainAmount(transaction.amount)
                        editingAmount = true
                    }) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit amount",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${transaction.merchantName} · ${Formatters.fullDate(transaction.timestamp)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Captured via ${transaction.sourcePackage.substringAfterLast('.')} (${transaction.sourceType.dbValue})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Category section
            Text(
                "CATEGORY",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            CategoryChipPicker(
                categories = categories,
                selectedCategoryId = transaction.categoryId,
                onSelect = { categoryId ->
                    if (categoryId != transaction.categoryId) {
                        onUpdateCategory(transaction.id, categoryId)
                        onDismiss()
                    }
                },
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Confidence Info Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Extraction Confidence",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ConfidenceDot(level = confidenceLevelOf(transaction.confidenceScore))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${(transaction.confidenceScore * 100).toInt()}% (${transaction.confidenceTier.dbValue})",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = PinchTeal,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { transaction.confidenceScore },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                        color = PinchTeal,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Extracted 100% locally on-device without cloud transmission.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (transaction.mergedFromDualSource) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MintGlow),
                ) {
                    Text(
                        text = "Merged from two sources (bank SMS alert + app notification).",
                        style = MaterialTheme.typography.bodySmall,
                        color = PinchTeal,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            if (transaction.rawNotificationText.isNotBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(onClick = { showRawText = !showRawText }) {
                    Text(
                        if (showRawText) "Hide raw notification" else "View raw notification",
                        style = MaterialTheme.typography.labelMedium.copy(color = PinchTeal),
                    )
                }
                if (showRawText) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = transaction.rawNotificationText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, Coral.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Coral),
            ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = Coral, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Not an expense / Delete", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/** 250.0 -> "250", 99.5 -> "99.5": editable text without a trailing ".0". */
private fun plainAmount(amount: Double): String =
    java.math.BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString()

private fun confidenceLevelOf(score: Float): com.expensetracker.core.model.ConfidenceLevel = when {
    score >= 0.75f -> com.expensetracker.core.model.ConfidenceLevel.HIGH
    score >= 0.50f -> com.expensetracker.core.model.ConfidenceLevel.MEDIUM
    else -> com.expensetracker.core.model.ConfidenceLevel.LOW
}

