package com.kaona.bach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaona.bach.ui.theme.BachTheme

/**
 * Transport control buttons (Reset, Play/Stop, Break) matching hardware layout.
 */
@Composable
fun TransportControls(
    isPlaying: Boolean,
    isBreakActive: Boolean,
    onPlayStop: () -> Unit,
    onReset: () -> Unit,
    onBreak: () -> Unit,
    onBreakRelease: () -> Unit
) {
    val colors = BachTheme.colors

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        // RESET button (small white)
        TransportButton(
            label = "RESET",
            color = colors.ledWhite,
            size = 36,
            onClick = onReset
        )

        Spacer(modifier = Modifier.weight(1f))

        // PLAY/STOP button (pink, like hardware)
        TransportButton(
            label = if (isPlaying) "STOP" else "PLAY",
            color = if (isPlaying) Color(0xFFFF6688) else colors.buttonPlay,
            size = 48,
            isActive = isPlaying,
            onClick = onPlayStop
        )

        Spacer(modifier = Modifier.weight(1f))

        // BREAK button (yellow/amber)
        val breakInteraction = remember { MutableInteractionSource() }
        val isBreakPressed by breakInteraction.collectIsPressedAsState()

        TransportButton(
            label = "BREAK",
            color = if (isBreakActive) Color(0xFFFF8800) else colors.buttonBreak,
            size = 48,
            isActive = isBreakActive,
            onClick = {
                if (isBreakActive) onBreakRelease() else onBreak()
            }
        )
    }
}

@Composable
fun TransportButton(
    label: String,
    color: Color,
    size: Int = 44,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) color
                    else color.copy(alpha = 0.7f)
                )
                .border(
                    width = 2.dp,
                    color = if (isActive) color else color.copy(alpha = 0.4f),
                    shape = CircleShape
                )
                .clickable(onClick = onClick)
        ) {
            // Inner highlight for 3D button effect
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size((size * 0.5f).dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.5f))
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = BachTheme.colors.textSecondary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * BPM control with increment/decrement
 */
@Composable
fun BpmControl(
    bpm: Int,
    onBpmChange: (Int) -> Unit
) {
    val colors = BachTheme.colors

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .background(colors.surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Decrease
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(colors.surfaceVariant)
                .clickable { onBpmChange(bpm - 1) }
        ) {
            Text("-", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "BPM",
                color = colors.textSecondary,
                fontSize = 8.sp
            )
            Text(
                text = "$bpm",
                color = colors.accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Increase
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(colors.surfaceVariant)
                .clickable { onBpmChange(bpm + 1) }
        ) {
            Text("+", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Preset navigation with prev/next buttons
 */
@Composable
fun PresetNavigation(
    presetIndex: Int,
    presetName: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPresetTap: () -> Unit
) {
    val colors = BachTheme.colors

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        // LED indicator
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(colors.ledRed)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = "PRESETS",
            color = colors.textSecondary,
            fontSize = 8.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        // PREV
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.surfaceVariant)
                .clickable(onClick = onPrev)
        ) {
            Text("◀", color = colors.textPrimary, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Preset display
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .background(colors.surface, RoundedCornerShape(4.dp))
                .border(0.5.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                .clickable(onClick = onPresetTap)
        ) {
            Text(
                text = "${presetIndex + 1}: $presetName",
                color = colors.textPrimary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // NEXT
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.surfaceVariant)
                .clickable(onClick = onNext)
        ) {
            Text("▶", color = colors.textPrimary, fontSize = 12.sp)
        }
    }
}
