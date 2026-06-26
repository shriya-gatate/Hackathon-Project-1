package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom-drawn circular gauge for Missed Deadline Risk.
 */
@Composable
fun RiskGauge(
    riskScore: Double, // 0.0 to 1.0
    modifier: Modifier = Modifier
) {
    val animatedRisk = animateFloatAsState(
        targetValue = riskScore.toFloat(),
        animationSpec = tween(durationMillis = 1000),
        label = "RiskGaugeAnimation"
    )

    val colorScheme = MaterialTheme.colorScheme
    val lowRiskColor = Color(0xFF4CAF50) // Green
    val medRiskColor = Color(0xFFFF9800) // Orange
    val highRiskColor = Color(0xFFF44336) // Red

    val activeColor = when {
        riskScore < 0.3 -> lowRiskColor
        riskScore < 0.7 -> medRiskColor
        else -> highRiskColor
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val center = Offset(width / 2f, height / 2f)
            val radius = (size.minDimension / 2f) - 16.dp.toPx()

            // Draw base track arc
            drawArc(
                color = colorScheme.outlineVariant.copy(alpha = 0.3f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
                size = Size(radius * 2f, radius * 2f),
                topLeft = Offset(center.x - radius, center.y - radius)
            )

            // Draw active risk arc
            val sweepAngle = 270f * animatedRisk.value
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(lowRiskColor, medRiskColor, highRiskColor),
                    center = center
                ),
                startAngle = 135f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
                size = Size(radius * 2f, radius * 2f),
                topLeft = Offset(center.x - radius, center.y - radius)
            )

            // Draw a subtle moving marker/pointer
            val markerAngleRad = Math.toRadians((135f + sweepAngle).toDouble())
            val markerX = center.x + radius * cos(markerAngleRad).toFloat()
            val markerY = center.y + radius * sin(markerAngleRad).toFloat()

            drawCircle(
                color = Color.White,
                radius = 6.dp.toPx(),
                center = Offset(markerX, markerY)
            )
            drawCircle(
                color = activeColor,
                radius = 4.dp.toPx(),
                center = Offset(markerX, markerY)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(riskScore * 100).toInt()}%",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = activeColor
            )
            Text(
                text = "Miss Risk",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Custom-drawn Donut Pie Chart for category distribution.
 */
@Composable
fun CategoryDistributionChart(
    categoryData: Map<String, Int>, // Category name -> focus minutes
    modifier: Modifier = Modifier
) {
    val totalMinutes = categoryData.values.sum().coerceAtLeast(1)
    
    val colorPalette = listOf(
        Color(0xFF673AB7), // Deep Purple
        Color(0xFF2196F3), // Blue
        Color(0xFF009688), // Teal
        Color(0xFFFF9800), // Orange
        Color(0xFFE91E63)  // Pink
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            if (categoryData.isEmpty()) {
                Text(
                    text = "No focus data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = (size.minDimension / 2f) - 10.dp.toPx()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    var currentStartAngle = -90f

                    categoryData.entries.forEachIndexed { index, entry ->
                        val sweepAngle = (entry.value.toFloat() / totalMinutes) * 360f
                        val color = colorPalette[index % colorPalette.size]

                        drawArc(
                            color = color,
                            startAngle = currentStartAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round),
                            size = Size(radius * 2f, radius * 2f),
                            topLeft = Offset(center.x - radius, center.y - radius)
                        )
                        currentStartAngle += sweepAngle
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$totalMinutes",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Total Mins",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1.2f),
            verticalArrangement = Arrangement.Center
        ) {
            categoryData.entries.forEachIndexed { index, entry ->
                val color = colorPalette[index % colorPalette.size]
                val percentage = ((entry.value.toFloat() / totalMinutes) * 100).toInt()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Canvas(modifier = Modifier.size(10.dp)) {
                        drawCircle(color = color)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${entry.key} ($percentage%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Native Canvas-drawn Bar Chart for weekly focus minutes.
 */
@Composable
fun FocusWeeklyBarChart(
    weeklySessions: List<Int>, // 7 values for Mon - Sun focus minutes
    modifier: Modifier = Modifier
) {
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    val maxVal = weeklySessions.maxOrNull()?.coerceAtLeast(30) ?: 60
    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = modifier) {
        Text(
            text = "Daily Focus Distribution",
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val spacing = 16.dp.toPx()
            val barCount = 7
            val availableWidth = canvasWidth - (spacing * (barCount + 1))
            val barWidth = availableWidth / barCount

            // Draw horizontal reference line
            drawLine(
                color = colorScheme.outlineVariant.copy(alpha = 0.5f),
                start = Offset(0f, canvasHeight - 20.dp.toPx()),
                end = Offset(canvasWidth, canvasHeight - 20.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )

            weeklySessions.forEachIndexed { index, mins ->
                val barHeight = ((mins.toFloat() / maxVal) * (canvasHeight - 40.dp.toPx())).coerceAtLeast(4.dp.toPx())
                val x = spacing + index * (barWidth + spacing)
                val y = canvasHeight - 20.dp.toPx() - barHeight

                // Draw Bar with rounded top
                drawRect(
                    color = colorScheme.primary,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight)
                )

                // Optional: Draw text count on top of bar if not zero
                if (mins > 0) {
                    // Just simple representation
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
