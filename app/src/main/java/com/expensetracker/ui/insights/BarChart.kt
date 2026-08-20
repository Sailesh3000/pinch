package com.expensetracker.ui.insights

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.core.common.Formatters
import com.expensetracker.ui.theme.GradientTealEnd
import com.expensetracker.ui.theme.GradientTealStart
import com.expensetracker.ui.theme.PinchTeal

@Composable
fun BarChart(
    data: List<MonthlyBarData>,
    modifier: Modifier = Modifier,
) {
    if (data.isEmpty()) {
        Text(
            "No historical trend data",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val maxAmount = data.maxOf { it.amount }.coerceAtLeast(1.0)
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animProgress.snapTo(0f)
        animProgress.animateTo(1f, animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing))
    }

    val currentMonthBrush = Brush.horizontalGradient(listOf(GradientTealStart, GradientTealEnd))
    val defaultBarColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        data.forEach { bar ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (bar.isCurrentMonth) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (bar.isCurrentMonth) PinchTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(44.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(18.dp),
                ) {
                    val barWidth = (size.width * (bar.amount / maxAmount) * animProgress.value).toFloat().coerceAtLeast(6f)
                    if (bar.isCurrentMonth) {
                        drawRoundRect(
                            brush = currentMonthBrush,
                            topLeft = Offset.Zero,
                            size = Size(barWidth, size.height),
                            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                        )
                    } else {
                        drawRoundRect(
                            color = defaultBarColor,
                            topLeft = Offset.Zero,
                            size = Size(barWidth, size.height),
                            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = Formatters.money(bar.amount),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (bar.isCurrentMonth) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = if (bar.isCurrentMonth) PinchTeal else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

