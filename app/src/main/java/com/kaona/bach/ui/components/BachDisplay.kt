package com.kaona.bach.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaona.bach.engine.CompositionalLanguage
import com.kaona.bach.ui.theme.BachTheme

/**
 * Circular display that mimics the hardware module's round OLED screen.
 * Shows BPM, current compositional language, channel status, and activity.
 */
@Composable
fun BachDisplay(
    bpm: Int,
    language: CompositionalLanguage,
    channels: IntArray,
    trackActivity: IntArray,
    currentBar: Int,
    currentBeat: Int,
    isPlaying: Boolean,
    isBreakActive: Boolean,
    modifier: Modifier = Modifier,
    materialName: String = "",
    externalClock: Boolean = false,
    beatsPerBar: Int = 4
) {
    val colors = BachTheme.colors

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(180.dp)
            .clip(CircleShape)
            .background(colors.displayBg)
            .border(3.dp, colors.accent.copy(alpha = 0.6f), CircleShape)
    ) {
        // Decorative arcs
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width / 2f - 8f

            // Outer decorative ring
            drawCircle(
                color = colors.accent.copy(alpha = 0.15f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.5f)
            )

            // Inner decorative ring
            drawCircle(
                color = colors.displayAccent.copy(alpha = 0.2f),
                radius = radius * 0.75f,
                center = center,
                style = Stroke(width = 1f)
            )

            // Beat indicator dots
            for (beat in 0 until beatsPerBar.coerceAtLeast(1)) {
                val angle = Math.toRadians(beat * 360.0 / beatsPerBar.coerceAtLeast(1) - 90.0)
                val dotRadius = if (beat == currentBeat && isPlaying) 5f else 3f
                val dotColor = if (beat == currentBeat && isPlaying) {
                    colors.displayAccent
                } else {
                    colors.displayAccent.copy(alpha = 0.3f)
                }
                drawCircle(
                    color = dotColor,
                    radius = dotRadius,
                    center = Offset(
                        center.x + (radius * 0.88f * kotlin.math.cos(angle)).toFloat(),
                        center.y + (radius * 0.88f * kotlin.math.sin(angle)).toFloat()
                    )
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            // BPM display
            Text(
                text = if (externalClock) "$bpm*" else "$bpm",
                color = colors.displayText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            // Language name
            Text(
                text = language.displayName.uppercase(),
                color = if (isBreakActive) Color(0xFFFF6644) else colors.displayAccent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            if (materialName.isNotEmpty()) {
                Text(
                    text = materialName,
                    color = colors.displayText.copy(alpha = 0.8f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (isBreakActive) {
                Text(
                    text = "BREAK",
                    color = Color(0xFFFF4422),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Channel status grid (2x2)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (row in 0..1) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        for (col in 0..1) {
                            val track = row * 2 + col
                            val isActive = trackActivity[track] > 0 && isPlaying
                            val channelLabel = "Ch ${channels.getOrElse(track) { track + 1 }}"

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(width = 48.dp, height = 18.dp)
                                    .background(
                                        if (isActive) colors.displayAccent.copy(alpha = 0.3f)
                                        else colors.displayBg.copy(alpha = 0.5f)
                                    )
                                    .border(
                                        0.5.dp,
                                        colors.displayAccent.copy(alpha = 0.5f)
                                    )
                            ) {
                                Text(
                                    text = channelLabel,
                                    color = if (isActive) colors.displayText else colors.displayText.copy(alpha = 0.5f),
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Bar counter
            if (isPlaying) {
                Text(
                    text = "Bar ${currentBar + 1}",
                    color = colors.displayText.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
