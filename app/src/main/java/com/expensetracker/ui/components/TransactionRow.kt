package com.expensetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.core.common.Formatters
import com.expensetracker.core.model.ConfidenceLevel
import com.expensetracker.core.model.SourceType
import com.expensetracker.core.model.Transaction
import com.expensetracker.core.model.TransactionType
import com.expensetracker.ui.theme.Coral
import com.expensetracker.ui.theme.PinchTeal

/**
 * Single transaction card row.
 * Shows squircle category icon, payee, time + source, color-coded amount, and
 * confidence indicator in a clean card container.
 */
@Composable
fun TransactionRow(
    transaction: Transaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCredit = transaction.txnType == TransactionType.CREDIT
    val amountColor = if (isCredit) PinchTeal else MaterialTheme.colorScheme.onSurface
    val amountPrefix = if (isCredit) "+" else "-"
    val confidenceLevel = when {
        transaction.confidenceScore >= 0.75f -> ConfidenceLevel.HIGH
        transaction.confidenceScore >= 0.50f -> ConfidenceLevel.MEDIUM
        else -> ConfidenceLevel.LOW
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIcon(
                iconKey = transaction.categoryIconKey,
                colorHex = transaction.categoryColorHex,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.merchantName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(Formatters.timeOfDay(transaction.timestamp))
                        append(" · ")
                        append(transaction.sourcePackage.substringAfterLast('.'))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = amountPrefix + " " + Formatters.money(transaction.amount, transaction.currency),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                    ),
                    color = amountColor,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    ConfidenceDot(level = confidenceLevel)
                    Spacer(modifier = Modifier.width(6.dp))
                    SourceBadge(sourceType = transaction.sourceType)
                }
            }
        }
    }
}

@Composable
fun SourceBadge(sourceType: SourceType, modifier: Modifier = Modifier) {
    val isSms = sourceType == SourceType.SMS_NOTIFICATION
    Icon(
        imageVector = if (isSms) Icons.Filled.Sms else Icons.Filled.Notifications,
        contentDescription = if (isSms) "SMS" else "App notification",
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = modifier.size(13.dp),
    )
}

