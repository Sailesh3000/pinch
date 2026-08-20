package com.expensetracker.ui.insights

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.core.common.Formatters
import com.expensetracker.core.database.dao.CategorySpend

@Composable
fun DonutChart(
    categories: List<CategorySpend>,
    modifier: Modifier = Modifier,
) {
    val total = categories.sumOf { it.totalAmount }
    if (total <= 0.0 || categories.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "No spending captured yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(categories) {
        animProgress.snapTo(0f)
        animProgress.animateTo(1f, animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }

    val fallbackColors = listOf(
        Color(0xFF1B8A6B), Color(0xFF3B82F6), Color(0xFFF59E0B), Color(0xFFEC4899),
        Color(0xFF8B5CF6), Color(0xFF10B981), Color(0xFFEF4444), Color(0xFF6B7280),
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            val canvasSize = size.minDimension
            val strokeWidth = canvasSize * 0.14f
            val radius = (canvasSize - strokeWidth) / 2f
            val topLeft = Offset(
                (size.width - canvasSize) / 2f + strokeWidth / 2f,
                (size.height - canvasSize) / 2f + strokeWidth / 2f,
            )
            val arcSize = Size(radius * 2, radius * 2)

            var startAngle = -90f
            val gapAngle = if (categories.size > 1) 2.5f else 0f

            categories.forEachIndexed { index, category ->
                val rawSweep = (category.totalAmount / total * 360f).toFloat() * animProgress.value
                val sweep = (rawSweep - gapAngle).coerceAtLeast(0.1f)
                val color = try {
                    Color(android.graphics.Color.parseColor(category.colorHex))
                } catch (_: Exception) {
                    fallbackColors[index % fallbackColors.size]
                }
                drawArc(
                    color = color,
                    startAngle = startAngle + (gapAngle / 2f),
                    sweepAngle = sweep.coerceAtMost(359.99f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                startAngle += rawSweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "TOTAL SPENT",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 0.8.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = Formatters.money(total),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

