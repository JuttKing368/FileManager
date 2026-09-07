package com.filemanager.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Modern ring-style storage indicator (spec 2: "circular or modern
 * progress indicator"). Animates in on first composition.
 */
@Composable
fun StorageRing(
    percentUsed: Int,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 160.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 14.dp
) {
    val animatedPercent by animateFloatAsState(
        targetValue = percentUsed.coerceIn(0, 100).toFloat(),
        animationSpec = tween(durationMillis = 800),
        label = "storageRingProgress"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = when {
        percentUsed >= 90 -> MaterialTheme.colorScheme.error
        percentUsed >= 75 -> androidx.compose.ui.graphics.Color(0xFFF5A524)
        else -> MaterialTheme.colorScheme.primary
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val diameter = this.size.minDimension - strokeWidth.toPx()
            val topLeft = androidx.compose.ui.geometry.Offset(
                (this.size.width - diameter) / 2f,
                (this.size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * (animatedPercent / 100f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
        }
        Text(
            text = "$percentUsed%",
            style = MaterialTheme.typography.headlineLarge
        )
    }
}
