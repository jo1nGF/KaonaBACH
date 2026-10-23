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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaona.bach.engine.ChordQuality
import com.kaona.bach.engine.ChordStep
import com.kaona.bach.engine.CompositionalLanguage
import com.kaona.bach.engine.FactorySequences
import com.kaona.bach.engine.HarmonicSequence
import com.kaona.bach.ui.theme.BachTheme

/**
 * Página SEQUENCE: progressão harmônica de até 64 posições.
 *  - Toque numa célula para selecioná-la e edite raiz, qualidade, duração e inversão.
 *  - A leitura vai da posição 1 até a primeira posição vazia e recomeça.
 *  - FUGUE e GROOVE ignoram a progressão; as demais linguagens a seguem.
 */
@Composable
fun SequencePanel(
    sequence: HarmonicSequence,
    language: CompositionalLanguage,
    isPlaying: Boolean,
    playingStep: Int,
    currentChord: String,
    onStepChange: (Int, ChordStep) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    onLoadFactory: (Int, Int) -> Unit,
    onLoadUser: (Int) -> Unit,
    onSaveUser: (Int) -> Unit
) {
    val colors = BachTheme.colors

    var cursor by remember { mutableStateOf(0) }
    var speed by remember { mutableStateOf(1) }
    var userSlot by remember { mutableStateOf(1) }

    val step = sequence.steps[cursor.coerceIn(0, HarmonicSequence.SIZE - 1)]
    val bypassed = language == CompositionalLanguage.FUGUE || language == CompositionalLanguage.GROOVE
    val status = when {
        sequence.steps[0].isEmpty -> "EMPTY AT STEP 1"
        !sequence.enabled -> "SEQUENCE OFF"
        bypassed -> "BYPASSED BY ${language.displayName.uppercase()}"
        else -> "ACTIVE"
    }
    val statusColor = if (status == "ACTIVE") colors.ledGreen else colors.ledYellow

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.panel, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        // Cabeçalho
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "SEQUENCE",
                color = colors.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = status,
                color = statusColor,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.weight(1f))
            SeqChip(if (sequence.enabled) "ON" else "OFF", sequence.enabled) {
                onEnabledChange(!sequence.enabled)
            }
            Spacer(modifier = Modifier.width(4.dp))
            SeqChip("CLEAR", false) { onClear() }
        }

        // Acorde atual (HARMONY)
        if (isPlaying && currentChord.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "HARMONY  $currentChord",
                color = colors.ledGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Grade das 64 posições
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (i in 0 until HarmonicSequence.SIZE) {
                SeqCell(
                    number = i + 1,
                    step = sequence.steps[i],
                    selected = i == cursor,
                    playing = isPlaying && sequence.isActive && i == playingStep,
                    onClick = { cursor = i }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Editor da posição selecionada
        Text(
            text = "STEP ${cursor + 1}",
            color = colors.textSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))

        SeqLabel("ROOT")
        SeqChipRow {
            for (r in -1..11) {
                SeqChip(ChordStep.ROOT_LABELS[r + 1], step.root == r) {
                    onStepChange(cursor, step.copy(root = r))
                }
            }
        }

        SeqLabel("QUALITY")
        SeqChipRow {
            ChordQuality.entries.forEach { q ->
                SeqChip(q.label, step.quality == q) {
                    onStepChange(cursor, step.copy(quality = q))
                }
            }
        }

        SeqLabel("LENGTH")
        SeqChipRow {
            ChordStep.LENGTH_LABELS.forEachIndexed { idx, label ->
                SeqChip(label, step.length == idx) {
                    onStepChange(cursor, step.copy(length = idx))
                }
            }
        }

        SeqLabel("INVERSION")
        SeqChipRow {
            for (inv in 0..3) {
                SeqChip("$inv", step.inversion == inv) {
                    onStepChange(cursor, step.copy(inversion = inv))
                }
            }
            SeqChip("VOICING", step.inversion == ChordStep.VOICING) {
                onStepChange(cursor, step.copy(inversion = ChordStep.VOICING))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Presets de fábrica: 32 formas x 3 velocidades
        SeqLabel("FACTORY")
        SeqChipRow {
            FactorySequences.speedLabels.forEachIndexed { idx, label ->
                SeqChip(label, speed == idx) { speed = idx }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        SeqChipRow {
            FactorySequences.names.forEachIndexed { idx, name ->
                SeqChip(name, false) {
                    onLoadFactory(idx, speed)
                    cursor = 0
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Slots de usuário
        SeqLabel("USER SLOTS")
        Row(verticalAlignment = Alignment.CenterVertically) {
            SeqChip("◀", false) { userSlot = if (userSlot > 1) userSlot - 1 else 128 }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "USER $userSlot",
                color = colors.textPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(6.dp))
            SeqChip("▶", false) { userSlot = if (userSlot < 128) userSlot + 1 else 1 }
            Spacer(modifier = Modifier.weight(1f))
            SeqChip("LOAD", false) {
                onLoadUser(userSlot)
                cursor = 0
            }
            Spacer(modifier = Modifier.width(4.dp))
            SeqChip("SAVE", false) { onSaveUser(userSlot) }
        }
    }
}

@Composable
private fun SeqLabel(text: String) {
    Text(
        text = text,
        color = BachTheme.colors.textSecondary,
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 6.dp, bottom = 3.dp)
    )
}

@Composable
private fun SeqChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content
    )
}

@Composable
private fun SeqChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = BachTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) colors.accent.copy(alpha = 0.25f) else colors.surface)
            .border(
                0.5.dp,
                if (selected) colors.accent else colors.surfaceVariant,
                RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = if (selected) colors.accent else colors.textSecondary,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SeqCell(
    number: Int,
    step: ChordStep,
    selected: Boolean,
    playing: Boolean,
    onClick: () -> Unit
) {
    val colors = BachTheme.colors
    val border: Color = when {
        playing -> colors.ledGreen
        selected -> colors.accent
        else -> colors.surfaceVariant
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(width = 44.dp, height = 42.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (playing) colors.ledGreen.copy(alpha = 0.15f) else colors.surface)
            .border(if (selected || playing) 1.5.dp else 0.5.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = "$number",
            color = colors.textSecondary,
            fontSize = 7.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = ChordStep.ROOT_LABELS[(step.root + 1).coerceIn(0, 12)],
            color = if (step.isEmpty) colors.textSecondary.copy(alpha = 0.5f) else colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}
