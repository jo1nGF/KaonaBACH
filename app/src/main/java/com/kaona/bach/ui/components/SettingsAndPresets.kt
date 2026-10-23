package com.kaona.bach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaona.bach.engine.BreakCharacter
import com.kaona.bach.engine.NoteName
import com.kaona.bach.engine.Scale
import com.kaona.bach.ui.theme.BachTheme

/**
 * Settings panel for configuring root note, scale, break character,
 * break duration, and MIDI channels per track.
 */
@Composable
fun SettingsPanel(
    rootNote: NoteName,
    scale: Scale,
    ornaments: Float,
    breakCharacter: BreakCharacter,
    breakBars: Int,
    midiChannels: IntArray,
    onRootChange: (NoteName) -> Unit,
    onScaleChange: (Scale) -> Unit,
    onOrnamentsChange: (Float) -> Unit,
    onBreakCharChange: (BreakCharacter) -> Unit,
    onBreakBarsChange: (Int) -> Unit,
    onMidiChannelChange: (Int, Int) -> Unit
) {
    val colors = BachTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.panel, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "SETTINGS",
            color = colors.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Root Note selection
        Text(
            text = "ROOT NOTE",
            color = colors.textSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            NoteName.entries.forEach { note ->
                val isSelected = note == rootNote
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isSelected) colors.accent.copy(alpha = 0.25f)
                            else colors.surface
                        )
                        .border(
                            0.5.dp,
                            if (isSelected) colors.accent else colors.surfaceVariant,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { onRootChange(note) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    val displayName = when (note) {
                        NoteName.Cs -> "C#"
                        NoteName.Ds -> "D#"
                        NoteName.Fs -> "F#"
                        NoteName.Gs -> "G#"
                        NoteName.As -> "A#"
                        else -> note.name
                    }
                    Text(
                        text = displayName,
                        color = if (isSelected) colors.accent else colors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Scale selection
        Text(
            text = "SCALE",
            color = colors.textSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Scale.entries.forEach { s ->
                val isSelected = s == scale
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isSelected) colors.accent.copy(alpha = 0.25f)
                            else colors.surface
                        )
                        .border(
                            0.5.dp,
                            if (isSelected) colors.accent else colors.surfaceVariant,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { onScaleChange(s) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = s.displayName,
                        color = if (isSelected) colors.accent else colors.textSecondary,
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Ornaments (appoggiatura, mordente, turn, trinado; flams no Groove)
        Text(
            text = "ORNAMENTS",
            color = colors.textSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("OFF" to 0f, "10%" to 0.1f, "25%" to 0.25f, "50%" to 0.5f, "75%" to 0.75f, "100%" to 1f)
                .forEach { (label, value) ->
                    val isSelected = kotlin.math.abs(ornaments - value) < 0.01f
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isSelected) colors.accent.copy(alpha = 0.25f)
                                else colors.surface
                            )
                            .border(
                                0.5.dp,
                                if (isSelected) colors.accent else colors.surfaceVariant,
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { onOrnamentsChange(value) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) colors.accent else colors.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Break Character
        Text(
            text = "BREAK CHARACTER",
            color = colors.textSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BreakCharacter.entries.forEach { bc ->
                val isSelected = bc == breakCharacter
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isSelected) colors.accent.copy(alpha = 0.25f)
                            else colors.surface
                        )
                        .border(
                            0.5.dp,
                            if (isSelected) colors.accent else colors.surfaceVariant,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { onBreakCharChange(bc) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = bc.displayName,
                        color = if (isSelected) colors.accent else colors.textSecondary,
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Break Duration
        Text(
            text = "BREAK DURATION (BARS)",
            color = colors.textSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(1, 2, 4, 8).forEach { bars ->
                val isSelected = bars == breakBars
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isSelected) colors.accent.copy(alpha = 0.25f)
                            else colors.surface
                        )
                        .border(
                            0.5.dp,
                            if (isSelected) colors.accent else colors.surfaceVariant,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { onBreakBarsChange(bars) }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$bars",
                        color = if (isSelected) colors.accent else colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // MIDI Channel assignment per track
        Text(
            text = "MIDI CHANNELS",
            color = colors.textSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        for (track in 0..3) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Text(
                    text = "Track ${track + 1}:",
                    color = colors.textLabel,
                    fontSize = 10.sp,
                    modifier = Modifier.width(60.dp)
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (ch in 1..16) {
                        val isSelected = midiChannels.getOrElse(track) { track + 1 } == ch
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (isSelected) colors.displayAccent.copy(alpha = 0.3f)
                                    else colors.surface
                                )
                                .border(
                                    0.5.dp,
                                    if (isSelected) colors.displayAccent else colors.surfaceVariant,
                                    RoundedCornerShape(3.dp)
                                )
                                .clickable { onMidiChannelChange(track, ch) }
                        ) {
                            Text(
                                text = "$ch",
                                color = if (isSelected) colors.displayText else colors.textSecondary,
                                fontSize = 8.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Presets panel for loading and saving presets (up to 128, matching hardware SD card storage)
 */
@Composable
fun PresetsPanel(
    currentIndex: Int,
    onLoadPreset: (Int) -> Unit,
    onSavePreset: (Int, String) -> Unit
) {
    val colors = BachTheme.colors
    var selectedSlot by remember { mutableIntStateOf(currentIndex) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.panel, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "PRESETS (128 SLOTS)",
            color = colors.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Preset grid (show first 32 for usability)
        for (row in 0..3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                for (col in 0..7) {
                    val idx = row * 8 + col
                    val isSelected = idx == selectedSlot
                    val isCurrent = idx == currentIndex

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    isCurrent -> colors.accent.copy(alpha = 0.3f)
                                    isSelected -> colors.displayAccent.copy(alpha = 0.2f)
                                    else -> colors.surface
                                }
                            )
                            .border(
                                0.5.dp,
                                when {
                                    isCurrent -> colors.accent
                                    isSelected -> colors.displayAccent
                                    else -> colors.surfaceVariant
                                },
                                RoundedCornerShape(3.dp)
                            )
                            .clickable { selectedSlot = idx }
                    ) {
                        Text(
                            text = "${idx + 1}",
                            color = if (isCurrent) colors.accent else colors.textSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Load/Save buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.displayAccent.copy(alpha = 0.2f))
                    .border(1.dp, colors.displayAccent, RoundedCornerShape(6.dp))
                    .clickable { onLoadPreset(selectedSlot) }
            ) {
                Text(
                    text = "LOAD #${selectedSlot + 1}",
                    color = colors.displayText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.accent.copy(alpha = 0.2f))
                    .border(1.dp, colors.accent, RoundedCornerShape(6.dp))
                    .clickable { onSavePreset(selectedSlot, "Preset ${selectedSlot + 1}") }
            ) {
                Text(
                    text = "SAVE #${selectedSlot + 1}",
                    color = colors.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
