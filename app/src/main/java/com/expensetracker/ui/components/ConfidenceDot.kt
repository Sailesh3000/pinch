package com.expensetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.expensetracker.core.model.ConfidenceLevel
import com.expensetracker.ui.theme.Amber
import com.expensetracker.ui.theme.Coral
import com.expensetracker.ui.theme.PinchTeal

/**
 * Confidence indicator:
 * - HIGH   -> filled solid teal dot
 * - MEDIUM -> half-filled (amber) dot
 * - LOW    -> outlined (coral) dot
 */
@Composable
fun ConfidenceDot(
    level: ConfidenceLevel,
    modifier: Modifier = Modifier,
) {
    val dotColor = when (level) {
        ConfidenceLevel.HIGH -> PinchTeal
        ConfidenceLevel.MEDIUM -> Amber
        ConfidenceLevel.LOW -> Coral
    }
    Canvas(modifier = modifier.size(9.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        when (level) {
            ConfidenceLevel.HIGH -> drawCircle(color = dotColor, radius = radius, center = center)
            ConfidenceLevel.MEDIUM -> {
                drawArc(
                    color = dotColor,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(0f, 0f),
                    size = this.size,
                )
                drawCircle(
                    color = dotColor,
                    radius = radius,
                    center = center,
                    style = Stroke(width = size.minDimension * 0.18f),
                )
            }
            ConfidenceLevel.LOW -> drawCircle(
                color = dotColor,
                radius = radius,
                center = center,
                style = Stroke(width = size.minDimension * 0.20f),
            )
        }
    }
}

@Preview
@Composable
private fun ConfidenceDotsPreview() {
    Row {
        ConfidenceDot(ConfidenceLevel.HIGH)
        Spacer(Modifier.width(4.dp))
        ConfidenceDot(ConfidenceLevel.MEDIUM)
        Spacer(Modifier.width(4.dp))
        ConfidenceDot(ConfidenceLevel.LOW)
    }
}

