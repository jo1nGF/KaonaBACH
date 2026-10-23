package com.kaona.bach.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaona.bach.ui.theme.BachTheme
import com.kaona.bach.viewmodel.SignalSnapshot
import com.kaona.bach.viewmodel.SignalState
import com.kaona.bach.viewmodel.UiState
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

private fun noteLabel(midi: Int): String {
    val n = midi.coerceIn(0, 127)
    return "${NOTE_NAMES[n % 12]}${n / 12 - 1}"
}

/**
 * Brilho que sobe a 1 a cada novo sinal ([signalCount] aumenta) e decai sozinho.
 * Devolve um State para ser lido só na fase de desenho (sem recompor a tela a cada frame).
 */
@Composable
fun rememberSignalGlow(signalCount: Int, decayMillis: Int = 260): State<Float> {
    val glow = remember { Animatable(0f) }
    LaunchedEffect(signalCount) {
        if (signalCount > 0) {
            glow.snapTo(1f)
            glow.animateTo(0f, animationSpec = tween(decayMillis, easing = LinearEasing))
        }
    }
    return glow.asState()
}

/**
 * Luzes coloridas do topo. O brilho de cada LED mostra o nível do parâmetro
 * (muda na hora ao girar o knob) e todos piscam a cada gate emitido.
 */
@Composable
fun CVInputButtonsRow(state: UiState, signals: StateFlow<SignalSnapshot>) {
    val colors = BachTheme.colors
    val snapshot by signals.collectAsState()

    val leds = listOf(
        Triple("MAT", colors.ledRed, state.material),
        Triple("MEM", colors.ledRed, state.memory),
        Triple("GRW", colors.ledGreen, state.growth),
        Triple("MRP", colors.ledGreen, state.morph),
        Triple("DEN", colors.ledYellow, state.density),
        Triple("TNS", colors.ledYellow, state.tension),
        Triple("INT", colors.ledPink, state.interaction),
        Triple("HUM", colors.ledPink, state.humanize),
    )

    val gateHits = snapshot.gate.sumOf { it.count }
    val flash = rememberSignalGlow(gateHits, 240)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        leds.forEach { (label, color, value) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .drawBehind {
                            val f = flash.value
                            val level = value.coerceIn(0f, 1f)
                            val r = size.minDimension / 2f

                            // Halo do sinal (pisca a cada gate, mais forte com o parâmetro alto)
                            if (f > 0f) {
                                drawCircle(
                                    color = color.copy(alpha = 0.5f * f * (0.4f + 0.6f * level)),
                                    radius = r + 8.dp.toPx() * f
                                )
                            }
                            // Corpo: brilho proporcional ao valor do parâmetro
                            drawCircle(
                                color = color.copy(alpha = (0.12f + 0.78f * level + 0.2f * f).coerceIn(0f, 1f)),
                                radius = r
                            )
                            // Contorno
                            drawCircle(
                                color = color.copy(alpha = 0.35f + 0.65f * level),
                                radius = r,
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = label,
                    color = colors.textLabel,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "${(value * 100).roundToInt()}",
                    color = color.copy(alpha = 0.85f),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Saídas GATE / MOD / PITCH das 4 vozes.
 *  - Toque numa tomada: liga/desliga só aquela saída daquela voz.
 *  - Toque no nome da linha (GATE/MOD/PITCH): liga ou desliga a linha inteira.
 *  - Toque em "Ch n": muta a voz toda.
 * Cada tomada pisca quando emite sinal e mostra o último valor (nota ou CC).
 */
@Composable
fun OutputJacksSection(
    channels: IntArray,
    trackMutes: BooleanArray,
    outputEnabled: List<List<Boolean>>,
    signals: StateFlow<SignalSnapshot>,
    onToggleOutput: (Int, Int) -> Unit,
    onToggleRow: (Int) -> Unit,
    onToggleMute: (Int) -> Unit
) {
    val colors = BachTheme.colors
    val haptic = LocalHapticFeedback.current
    val snapshot by signals.collectAsState()
    val signalRows = listOf(snapshot.gate, snapshot.mod, snapshot.pitch)

    val rows = listOf(
        Triple("GATE", colors.gate, 0),
        Triple("MOD", colors.mod, 1),
        Triple("PITCH", colors.pitch, 2)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.panel, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text(
            text = "OUTPUTS",
            color = colors.textSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Cabeçalho: botão de mute por voz
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(52.dp))
            for (track in 0..3) {
                val muted = trackMutes.getOrElse(track) { false }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (muted) colors.ledRed.copy(alpha = 0.2f) else colors.surfaceVariant
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleMute(track)
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (muted) "MUTE" else "Ch ${channels.getOrElse(track) { track + 1 }}",
                            color = if (muted) colors.ledRed else colors.textLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        rows.forEach { (name, color, type) ->
            val rowEnabled = outputEnabled.getOrElse(type) { emptyList() }
            val anyOn = rowEnabled.any { it }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.width(52.dp)
                ) {
                    Text(
                        text = name,
                        color = if (anyOn) color else colors.textSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleRow(type)
                            }
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                }

                for (track in 0..3) {
                    val enabled = rowEnabled.getOrElse(track) { true }
                    val muted = trackMutes.getOrElse(track) { false }
                    val signal = signalRows.getOrElse(type) { emptyList() }
                        .getOrElse(track) { SignalState() }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.weight(1f)
                    ) {
                        SignalJack(
                            color = color,
                            type = type,
                            enabled = enabled,
                            muted = muted,
                            signal = signal,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleOutput(type, track)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "GATE dispara a nota · MOD envia CC1 · PITCH define a altura " +
                "(desligado = nota fixa C4). PITCH precisa do GATE para soar. " +
                "Toque no nome da linha para ligar/desligar todas as vozes.",
            color = colors.textSecondary,
            fontSize = 8.sp,
            lineHeight = 11.sp
        )
    }
}

/**
 * Uma tomada: apagada/cinza = desligada, colorida = ligada, brilha ao emitir sinal.
 */
@Composable
private fun SignalJack(
    color: Color,
    type: Int,
    enabled: Boolean,
    muted: Boolean,
    signal: SignalState,
    onClick: () -> Unit
) {
    val colors = BachTheme.colors
    val live = enabled && !muted
    val glow = rememberSignalGlow(if (live) signal.count else 0)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .drawBehind {
                val f = if (live) glow.value else 0f
                val r = size.minDimension / 2f

                if (f > 0f) {
                    drawCircle(color = color.copy(alpha = 0.45f * f), radius = r + 6.dp.toPx() * f)
                }

                val fill = when {
                    !enabled -> Color(0xFF1C1C1C)
                    muted -> color.copy(alpha = 0.06f)
                    else -> color.copy(alpha = 0.14f + 0.8f * f)
                }
                drawCircle(color = fill, radius = r)

                val ring = when {
                    !enabled -> Color(0xFF444444)
                    muted -> color.copy(alpha = 0.2f)
                    else -> color.copy(alpha = 0.55f + 0.45f * f)
                }
                drawCircle(
                    color = ring,
                    radius = r,
                    style = Stroke(width = if (enabled) 2.dp.toPx() else 1.dp.toPx())
                )

                // Traço diagonal = saída desligada
                if (!enabled) {
                    drawLine(
                        color = Color(0xFF555555),
                        start = Offset(r * 0.55f, r * 1.45f),
                        end = Offset(r * 1.45f, r * 0.55f),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        // Valor vivo do sinal: CC1 no MOD, nota no PITCH
        if (live && signal.count > 0 && type != 0) {
            Text(
                text = if (type == 1) signal.level.toString() else noteLabel(signal.level),
                color = colors.textPrimary.copy(alpha = 0.85f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
