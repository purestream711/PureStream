package com.purestream.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.purestream.ui.theme.AccentPurple

/**
 * Circular level indicator showing current level and progress to next level.
 *
 * Displays a circular progress ring with:
 * - Grey track (background circle)
 * - Purple arc showing progress (0% = no fill, 100% = full circle)
 * - Level number centered inside the circle
 *
 * @param currentLevel The current level number to display
 * @param progress Progress towards next level (0f to 1f)
 * @param modifier Optional modifier for the component
 * @param size Diameter of the circular indicator
 * @param strokeWidth Width of the progress ring
 * @param progressColor Color for the progress arc (default: AccentPurple)
 * @param trackColor Color for the background track (default: semi-transparent grey)
 */
@Composable
fun CircularLevelIndicator(
    currentLevel: Int,
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    strokeWidth: Dp = 4.dp,
    progressColor: Color = AccentPurple,
    trackColor: Color = Color.Gray.copy(alpha = 0.3f)
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Canvas for drawing circular progress
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = size.toPx()
            val strokeWidthPx = strokeWidth.toPx()
            val radius = (diameter - strokeWidthPx) / 2f
            val center = Offset(diameter / 2f, diameter / 2f)

            // Background track (full grey circle)
            drawArc(
                color = trackColor,
                startAngle = -90f,  // Start at top (12 o'clock)
                sweepAngle = 360f,  // Full circle
                useCenter = false,
                topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2),
                size = Size(diameter - strokeWidthPx, diameter - strokeWidthPx),
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )

            // Progress arc (purple, fills clockwise from top)
            if (progress > 0f) {
                val clampedProgress = progress.coerceIn(0f, 1f)
                drawArc(
                    color = progressColor,
                    startAngle = -90f,  // Start at top (12 o'clock)
                    sweepAngle = 360f * clampedProgress,  // Fill based on progress
                    useCenter = false,
                    topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2),
                    size = Size(diameter - strokeWidthPx, diameter - strokeWidthPx),
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }
        }

        // Level number centered in the circle
        Text(
            text = "$currentLevel",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}
