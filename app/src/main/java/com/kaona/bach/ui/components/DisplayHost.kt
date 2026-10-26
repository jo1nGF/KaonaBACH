package com.kaona.bach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaona.bach.engine.CompositionalLanguage
import com.kaona.bach.engine.MaterialCatalog
import com.kaona.bach.ui.theme.BachTheme
import com.kaona.bach.ui.theme.LocalBachColors
import com.kaona.bach.viewmodel.UiState

/** Páginas do visor azul, na ordem dos botões do topo. */
object DisplayPages {
    val names = listOf("HOME", "MUSIC", "SEQ", "MIDI IN", "CONTROLS", "VISUAL", "SETTINGS", "PRESETS")
}

/**
 * O visor azul. Todas as páginas do módulo (HOME, MUSIC, SEQ, MIDI IN, CONTROLS, VISUAL, SETTINGS e
 * PRESETS) aparecem aqui dentro, com os botões de página no próprio visor; nada abre abaixo dele.
 * O conteúdo recebe a paleta do visor (fundo azul, texto azul-claro) e rola por dentro.
 */
@Composable
fun DisplayHost(
    page: Int,
    onPageSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = BachTheme.colors
    val displayColors = remember(base) {
        base.copy(
            panel = Color.Transparent,
            surface = Color(0xFF14304D),
            surfaceVariant = Color(0xFF24506F),
            textPrimary = base.displayText,
            textSecondary = base.displayText.copy(alpha = 0.65f),
            textLabel = base.displayText.copy(alpha = 0.85f)
        )
    }
    val scroll = rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0) }

    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp)
            .clip(shape)
            .background(base.displayBg)
            .border(3.dp, base.accent.copy(alpha = 0.6f), shape)
    ) {
        // Botões de página dentro do próprio visor
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DisplayPages.names.forEachIndexed { index, name ->
                DisplayTab(name, page == index) { onPageSelect(index) }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(base.accent.copy(alpha = 0.25f))
        )

        CompositionLocalProvider(LocalBachColors provides displayColors) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(10.dp),
                content = content
            )
        }
    }
}

@Composable
private fun DisplayTab(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = BachTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) colors.accent.copy(alpha = 0.28f) else Color.Transparent)
            .border(
                0.5.dp,
                if (selected) colors.accent else colors.displayText.copy(alpha = 0.25f),
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = if (selected) colors.accent else colors.displayText.copy(alpha = 0.85f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * Página HOME: o mostrador circular, as leituras principais, a escolha da linguagem composicional e,
 * no GROOVE MIX, o parceiro melódico.
 */
@Composable
fun HomePage(
    state: UiState,
    onLanguageSelect: (CompositionalLanguage) -> Unit,
    onPartnerSelect: (CompositionalLanguage) -> Unit
) {
    val colors = BachTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BachDisplay(
            bpm = state.bpm,
            language = state.currentLanguage,
            channels = state.midiChannels,
            trackActivity = state.trackActivity,
            currentBar = state.currentBar,
            currentBeat = state.currentBeat,
            isPlaying = state.isPlaying,
            isBreakActive = state.isBreakActive,
            materialName = MaterialCatalog.name(state.currentLanguage, state.material),
            externalClock = state.externalClock,
            beatsPerBar = state.music.meter.barTicks / state.music.meter.beatTicks
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ReadOut("LANGUAGE", state.currentLanguage.displayName.uppercase())
            ReadOut("MATERIAL", MaterialCatalog.name(state.currentLanguage, state.material))
            ReadOut("ROOT", "${state.rootNote.name.replace("s", "#")}${state.music.rootOctave}  ${state.scale.displayName}")
            ReadOut("METER", state.music.meter.label)
            ReadOut("PRESET", "${state.presetIndex + 1} ${state.presetName}")
            if (state.currentChord.isNotEmpty()) ReadOut("HARMONY", state.currentChord)
            if (state.isBreakActive) ReadOut("BREAK", "ACTIVE")
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    LanguageSelector(
        currentLanguage = state.currentLanguage,
        onLanguageSelect = onLanguageSelect
    )

    if (state.currentLanguage == CompositionalLanguage.GROOVE_MIX) {
        Spacer(modifier = Modifier.height(8.dp))
        GroovePartnerRow(current = state.groovePartner, onSelect = onPartnerSelect)
    }
}

@Composable
private fun ReadOut(label: String, value: String) {
    val colors = BachTheme.colors
    Column {
        Text(
            text = label,
            color = colors.textSecondary,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = colors.displayText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
