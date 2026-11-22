package com.shubworks.luxora

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val LaunchBackground = Color(0xFF090D12)
private val LaunchWhite = Color(0xFFF4F7FA)
private val LaunchCyan = Color(0xFF8CEBFA)

@Composable
fun LaunchAnimation(onFinished: () -> Unit) {
    var start by remember { mutableStateOf(false) }

    val reveal by animateFloatAsState(
        targetValue = if (start) 1f else 0f,
        animationSpec = tween(850, easing = FastOutSlowInEasing),
        label = "launch_reveal"
    )
    val fade by animateFloatAsState(
        targetValue = if (start) 0f else 1f,
        animationSpec = tween(380, delayMillis = 850, easing = FastOutSlowInEasing),
        label = "launch_fade"
    )

    LaunchedEffect(Unit) {
        start = true
        delay(1320)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LaunchBackground)
            .alpha(fade.coerceIn(0f, 1f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Canvas(
                modifier = Modifier.size(148.dp)
            ) {
                val cx = size.width * 0.48f
                val cy = size.height * 0.53f

                // A restrained beam that opens outward from the torch head.
                val beam = Path().apply {
                    moveTo(cx + 17.dp.toPx(), cy - 18.dp.toPx())
                    lineTo(size.width * 0.92f, size.height * 0.12f)
                    lineTo(size.width * 0.92f, size.height * 0.88f)
                    lineTo(cx + 17.dp.toPx(), cy + 18.dp.toPx())
                    close()
                }
                drawPath(
                    beam,
                    color = LaunchCyan.copy(alpha = 0.10f + 0.16f * reveal)
                )

                val rayStartX = cx + 27.dp.toPx()
                val rayEndX = rayStartX + (size.width * 0.43f * reveal)
                listOf(-25f, 0f, 25f).forEach { angle ->
                    val radians = Math.toRadians(angle.toDouble())
                    val yOffset = (size.height * 0.24f * kotlin.math.sin(radians)).toFloat()
                    drawLine(
                        color = LaunchCyan.copy(alpha = 0.35f + 0.65f * reveal),
                        start = Offset(rayStartX, cy + yOffset * 0.35f),
                        end = Offset(rayEndX, cy + yOffset),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Minimal flashlight body, angled gently upward.
                val body = Path().apply {
                    moveTo(size.width * 0.16f, size.height * 0.68f)
                    lineTo(size.width * 0.48f, size.height * 0.39f)
                    lineTo(size.width * 0.61f, size.height * 0.52f)
                    lineTo(size.width * 0.29f, size.height * 0.81f)
                    close()
                }
                drawPath(body, LaunchWhite)
                drawLine(
                    color = LaunchBackground,
                    start = Offset(size.width * 0.31f, size.height * 0.55f),
                    end = Offset(size.width * 0.43f, size.height * 0.67f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = LaunchWhite,
                    start = Offset(size.width * 0.47f, size.height * 0.39f),
                    end = Offset(size.width * 0.56f, size.height * 0.30f),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = LaunchCyan,
                    radius = 3.dp.toPx(),
                    center = Offset(size.width * 0.61f, size.height * 0.52f)
                )
            }

            Text(
                text = "AMBIENT LIGHT INSTRUMENT",
                color = LaunchWhite.copy(alpha = 0.72f * fade),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )
        }
    }
}
