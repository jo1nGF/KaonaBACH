package com.kaona.bach.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaona.bach.ui.theme.BachTheme
import kotlin.math.*

/**
 * Rotary knob component that mimics the hardware encoders on the B.A.C.H. module.
 * Dark circular knob with gold indicator arc and position marker.
 */
@Composable
fun ParameterKnob(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 72
) {
    val colors = BachTheme.colors

    // O pointerInput(Unit) só é criado uma vez; sem isso ele guardaria o valor
    // e o callback da primeira composição e o knob travaria/voltaria ao arrastar.
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    var dragValue by remember { mutableStateOf(value) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(size.dp + 20.dp)
    ) {
        Text(
            text = label,
            color = colors.textLabel,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragValue = currentValue }
                    ) { change, dragAmount ->
                        change.consume()
                        // Arrastar para cima aumenta, para baixo diminui
                        dragValue = (dragValue - dragAmount.y / 300f).coerceIn(0f, 1f)
                        currentOnValueChange(dragValue)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = this.size.width / 2f
                val centerY = this.size.height / 2f
                val radius = min(centerX, centerY) - 4f

                // Outer shadow ring
                drawCircle(
                    color = Color(0xFF1A1A1A),
                    radius = radius + 3f,
                    center = Offset(centerX, centerY)
                )

                // Main knob body - gradient for 3D effect
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF3A3A3A),
                            Color(0xFF2A2A2A),
                            Color(0xFF1E1E1E)
                        ),
                        center = Offset(centerX - radius * 0.2f, centerY - radius * 0.2f),
                        radius = radius * 1.5f
                    ),
                    radius = radius,
                    center = Offset(centerX, centerY)
                )

                // Inner circle detail
                drawCircle(
                    color = Color(0xFF222222),
                    radius = radius * 0.35f,
                    center = Offset(centerX, centerY)
                )

                // Value arc (gold) - from 225° to position
                val startAngle = 135f // Bottom-left
                val sweepAngle = value * 270f // 270° total range

                drawArc(
                    color = colors.accent.copy(alpha = 0.8f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = 3f, cap = StrokeCap.Round),
                    topLeft = Offset(centerX - radius + 6f, centerY - radius + 6f),
                    size = androidx.compose.ui.geometry.Size(
                        (radius - 6f) * 2f,
                        (radius - 6f) * 2f
                    )
                )

                // Position indicator line
                val indicatorAngle = Math.toRadians((startAngle + sweepAngle).toDouble())
                val innerR = radius * 0.4f
                val outerR = radius * 0.85f
                drawLine(
                    color = colors.accent,
                    start = Offset(
                        centerX + innerR * cos(indicatorAngle).toFloat(),
                        centerY + innerR * sin(indicatorAngle).toFloat()
                    ),
                    end = Offset(
                        centerX + outerR * cos(indicatorAngle).toFloat(),
                        centerY + outerR * sin(indicatorAngle).toFloat()
                    ),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }
        }

        Text(
            text = "${(value * 100).toInt()}",
            color = colors.textSecondary,
            fontSize = 9.sp,
            textAlign = TextAlign.Center
        )
    }
}
