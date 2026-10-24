package com.kaona.bach.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaona.bach.engine.CompositionalLanguage
import com.kaona.bach.engine.DurationMode
import com.kaona.bach.engine.MaterialCatalog
import com.kaona.bach.engine.Meter
import com.kaona.bach.engine.MusicSettings
import com.kaona.bach.engine.NoteName
import com.kaona.bach.engine.VelocityMode
import com.kaona.bach.midi.MidiAssign
import com.kaona.bach.midi.MidiCommand
import com.kaona.bach.midi.MidiInSettings
import com.kaona.bach.midi.MidiMonitor
import com.kaona.bach.ui.theme.BachTheme
import com.kaona.bach.viewmodel.SignalSnapshot
import com.kaona.bach.viewmodel.UiState
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Linha de atalhos para as páginas do módulo: MUSIC, MIDI IN, CONTROLS e VISUAL. */
@Composable
fun ToolsRow(activePage: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(1 to "MUSIC", 2 to "MIDI IN", 3 to "CONTROLS", 4 to "VISUAL").forEach { (page, label) ->
            ToolChip(label, activePage == page) { onSelect(page) }
        }
    }
}

@Composable
private fun ToolChip(text: String, selected: Boolean, onClick: () -> Unit) {
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
            .padding(horizontal = 9.dp, vertical = 5.dp)
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
private fun ToolLabel(text: String) {
    Text(
        text = text,
        color = BachTheme.colors.textSecondary,
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 8.dp, bottom = 3.dp)
    )
}

@Composable
private fun ToolChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content
    )
}

@Composable
private fun PanelFrame(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = BachTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.panel, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(text = title, color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

/**
 * Página MUSIC: parâmetros comuns a todas as linguagens.
 * (SCALE e ORNAMENTS ficam em ⚙ Settings.)
 */
@Composable
fun MusicPanel(
    music: MusicSettings,
    rootNote: NoteName,
    onRootChange: (NoteName) -> Unit,
    onChange: ((MusicSettings) -> MusicSettings) -> Unit
) {
    PanelFrame("MUSIC") {
        ToolLabel("ROOT NOTE")
        ToolChipRow {
            NoteName.entries.forEach { note ->
                ToolChip(note.name.replace("s", "#"), note == rootNote) { onRootChange(note) }
            }
        }
        ToolLabel("ROOT OCTAVE (C1 a C6; FUGUE no mínimo C2)")
        ToolChipRow {
            for (octave in 1..6) {
                ToolChip("C$octave", music.rootOctave == octave) { onChange { it.copy(rootOctave = octave) } }
            }
        }

        ToolLabel("DURATION")
        ToolChipRow {
            DurationMode.entries.forEach { mode ->
                ToolChip(mode.label, music.duration == mode) { onChange { it.copy(duration = mode) } }
            }
        }

        ToolLabel("VELOCITY")
        ToolChipRow {
            VelocityMode.entries.forEach { mode ->
                ToolChip(mode.label, music.velocity == mode) { onChange { it.copy(velocity = mode) } }
            }
        }

        ToolLabel("METER")
        ToolChipRow {
            Meter.entries.forEach { meter ->
                ToolChip(meter.label, music.meter == meter) { onChange { it.copy(meter = meter) } }
            }
        }

        ToolLabel("SWING")
        ToolChipRow {
            listOf("0%" to 0f, "15%" to 0.15f, "33%" to 0.33f, "50%" to 0.5f, "66%" to 0.66f, "100%" to 1f)
                .forEach { (label, value) ->
                    ToolChip(label, kotlin.math.abs(music.swing - value) < 0.01f) {
                        onChange { it.copy(swing = value) }
                    }
                }
        }

        ToolLabel("PHRASE (compassos)")
        ToolChipRow {
            MusicSettings.PHRASE_OPTIONS.forEach { bars ->
                ToolChip(if (bars == 0) "INFINITE" else "$bars", music.phraseBars == bars) {
                    onChange { it.copy(phraseBars = bars) }
                }
            }
        }
    }
}

/** Página MIDI IN: clock, nota que define o ROOT, atribuição de CCs com aprendizado e monitor. */
@Composable
fun MidiInPanel(
    settings: MidiInSettings,
    learning: MidiCommand?,
    monitor: StateFlow<MidiMonitor>,
    onClockEnabled: (Boolean) -> Unit,
    onResetOnStop: (Boolean) -> Unit,
    onRootChannel: (Int) -> Unit,
    onAssign: (MidiCommand, MidiAssign) -> Unit,
    onClear: (MidiCommand) -> Unit,
    onLearn: (MidiCommand?) -> Unit
) {
    val colors = BachTheme.colors
    val info by monitor.collectAsState()

    PanelFrame("MIDI IN") {
        // Monitor: último CC recebido e contadores
        Spacer(modifier = Modifier.height(6.dp))
        val last = if (info.cc >= 0) "CH ${info.channel}  CC ${info.cc} = ${info.value}" else "—"
        Text(
            text = "$last   MSG ${info.messages}   CLK ${info.clocks}",
            color = colors.ledGreen,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        ToolLabel("CLOCK E TRANSPORTE (F8 clock, FA start, FB continue, FC stop)")
        ToolChipRow {
            ToolChip("MIDI CLOCK ${if (settings.clockEnabled) "ON" else "OFF"}", settings.clockEnabled) {
                onClockEnabled(!settings.clockEnabled)
            }
            ToolChip("RESET ON STOP ${if (settings.resetOnStop) "ON" else "OFF"}", settings.resetOnStop) {
                onResetOnStop(!settings.resetOnStop)
            }
        }

        ToolLabel("ROOT NOTES (canal que escuta notas)")
        ToolChipRow {
            ToolChip("OFF", settings.rootChannel < 0) { onRootChannel(-1) }
            ToolChip("OMNI", settings.rootChannel == 0) { onRootChannel(0) }
            for (ch in 1..16) {
                ToolChip("$ch", settings.rootChannel == ch) { onRootChannel(ch) }
            }
        }

        ToolLabel("ATRIBUIÇÕES (padrão: canal 1, CC 16 a 27)")
        MidiCommand.entries.forEach { command ->
            val a = settings.assignments.getOrNull(command.ordinal) ?: MidiAssign()
            val isLearning = learning == command

            Column(modifier = Modifier.padding(vertical = 3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = command.label,
                        color = if (isLearning) colors.ledYellow else colors.textPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(92.dp)
                    )
                    Text(
                        text = if (isLearning) "gire um controle…"
                        else "CH ${if (a.channel == 0) "OMNI" else "${a.channel}"}  CC ${if (a.cc < 0) "OFF" else "${a.cc}"}",
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                ToolChipRow {
                    ToolChip(if (isLearning) "CANCEL" else "LEARN", isLearning) {
                        onLearn(if (isLearning) null else command)
                    }
                    ToolChip("CLEAR", false) { onClear(command) }
                    ToolChip("CH ◀", false) { onAssign(command, a.copy(channel = (a.channel + 16) % 17)) }
                    ToolChip("CH ▶", false) { onAssign(command, a.copy(channel = (a.channel + 1) % 17)) }
                    ToolChip("CC ◀", false) { onAssign(command, a.copy(cc = if (a.cc <= -1) 127 else a.cc - 1)) }
                    ToolChip("CC ▶", false) { onAssign(command, a.copy(cc = if (a.cc >= 127) -1 else a.cc + 1)) }
                }
            }
        }

        ToolLabel("ALGORITHM (CC 24): 0-10 Fugue · 11-21 Canon · 22-31 Proba · 32-42 Tintinnabuli · 43-53 Minimal · 54-63 Cellular · 64-74 Tree · 75-85 Fractal · 86-95 Motif · 96-106 Hybrid · 107-117 Groove · 118-127 Groove Mix")
    }
}

/** Página CONTROLS: os oito valores ao mesmo tempo, em porcentagem. */
@Composable
fun ControlsPanel(state: UiState) {
    val colors = BachTheme.colors
    val language = state.currentLanguage
    val rows = listOf(
        "MATERIAL" to state.material,
        "MEMORY" to state.memory,
        "GROWTH" to state.growth,
        "MORPH" to state.morph,
        "DENSITY" to state.density,
        "TENSION" to state.tension,
        "INTERACTION" to state.interaction,
        "HUMANIZE" to state.humanize
    )

    PanelFrame("CONTROLS") {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${language.displayName.uppercase()} · ${MaterialCatalog.name(language, state.material)}",
            color = colors.displayAccent,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(6.dp))
        rows.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = colors.textLabel,
                    fontSize = 10.sp,
                    modifier = Modifier.width(92.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(value.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(colors.accent)
                    )
                }
                Text(
                    text = "${(value * 100).roundToInt()}%",
                    color = colors.textPrimary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .width(44.dp)
                        .padding(start = 6.dp)
                )
            }
        }
    }
}

/** Página VISUAL: os oito controles em círculo, com a superfície deformada pelos valores. */
@Composable
fun VisualPanel(state: UiState, signals: StateFlow<SignalSnapshot>) {
    val colors = BachTheme.colors
    val snapshot by signals.collectAsState()
    val values = listOf(
        state.material, state.memory, state.growth, state.morph,
        state.density, state.tension, state.interaction, state.humanize
    )
    val labels = listOf("MAT", "MEM", "GRW", "MRP", "DEN", "TNS", "INT", "HUM")

    PanelFrame("VISUAL") {
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(230.dp)) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f - 10.dp.toPx()

                fun point(i: Int, r: Float): Offset {
                    val a = -PI / 2 + i * 2 * PI / 8
                    return Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat())
                }

                // Grade: três octógonos e os raios
                for (ring in 1..3) {
                    val path = Path()
                    for (i in 0 until 8) {
                        val p = point(i, radius * ring / 3f)
                        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                    path.close()
                    drawPath(path, colors.textSecondary.copy(alpha = 0.25f), style = Stroke(1.dp.toPx()))
                }
                for (i in 0 until 8) {
                    drawLine(colors.textSecondary.copy(alpha = 0.2f), c, point(i, radius), 1.dp.toPx())
                }

                // Superfície deformada pelos valores
                val shape = Path()
                for (i in 0 until 8) {
                    val p = point(i, radius * (0.06f + 0.94f * values[i].coerceIn(0f, 1f)))
                    if (i == 0) shape.moveTo(p.x, p.y) else shape.lineTo(p.x, p.y)
                }
                shape.close()
                drawPath(shape, colors.accent.copy(alpha = 0.28f))
                drawPath(shape, colors.accent, style = Stroke(2.dp.toPx()))

                for (i in 0 until 8) {
                    drawCircle(colors.accent, 3.dp.toPx(), point(i, radius * (0.06f + 0.94f * values[i].coerceIn(0f, 1f))))
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = labels.indices.joinToString("  ") { "${labels[it]} ${(values[it] * 100).roundToInt()}" },
            color = colors.textSecondary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )

        // Atividade real das quatro vozes (os eventos efetivamente emitidos)
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val trackColors = listOf(colors.ledRed, colors.ledGreen, colors.ledYellow, colors.ledPink)
            for (track in 0..3) {
                val glow = rememberSignalGlow(snapshot.gate.getOrNull(track)?.count ?: 0, 320)
                val color = trackColors[track]
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .drawBehind {
                            val f = glow.value
                            val r = size.minDimension / 2f
                            if (f > 0f) drawCircle(color.copy(alpha = 0.5f * f), radius = r + 8.dp.toPx() * f)
                            drawCircle(color.copy(alpha = 0.15f + 0.8f * f), radius = r)
                            drawCircle(color, radius = r, style = Stroke(1.5.dp.toPx()))
                        }
                )
            }
        }
    }
}

/** Parceiro melódico do GROOVE MIX: ele fornece as alturas; o ritmo vem do GROOVE. */
@Composable
fun GroovePartnerRow(current: CompositionalLanguage, onSelect: (CompositionalLanguage) -> Unit) {
    val colors = BachTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "GROOVE MIX · PARTNER",
            color = colors.textSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 3.dp)
        )
        ToolChipRow {
            listOf(
                CompositionalLanguage.CANON,
                CompositionalLanguage.PROBA,
                CompositionalLanguage.CELLULAR,
                CompositionalLanguage.TREE,
                CompositionalLanguage.FRACTAL,
                CompositionalLanguage.MOTIF,
                CompositionalLanguage.HYBRID
            ).forEach { lang ->
                ToolChip(lang.displayName.uppercase(), lang == current) { onSelect(lang) }
            }
        }
    }
}
