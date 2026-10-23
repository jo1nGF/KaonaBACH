package com.kaona.bach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaona.bach.engine.CompositionalLanguage
import com.kaona.bach.ui.theme.BachTheme
import com.kaona.bach.viewmodel.BachViewModel
import com.kaona.bach.viewmodel.UiState

/**
 * Header section with B.A.C.H. title
 */
@Composable
fun HeaderSection() {
    val colors = BachTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "B . A . C . H .",
            color = colors.accent,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            letterSpacing = 4.sp
        )
    }
}

/**
 * Language selector - scrollable row of compositional language options
 */
@Composable
fun LanguageSelector(
    currentLanguage: CompositionalLanguage,
    onLanguageSelect: (CompositionalLanguage) -> Unit
) {
    val colors = BachTheme.colors

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "COMPOSITIONAL LANGUAGE",
            color = colors.textSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CompositionalLanguage.entries.forEach { lang ->
                val isSelected = lang == currentLanguage
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isSelected) colors.accent.copy(alpha = 0.25f)
                            else colors.surface
                        )
                        .border(
                            1.dp,
                            if (isSelected) colors.accent
                            else colors.surfaceVariant,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onLanguageSelect(lang) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = lang.displayName,
                        color = if (isSelected) colors.accent else colors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Bottom bar with MIDI status and settings toggle
 */
@Composable
fun BottomBar(
    isMidiConnected: Boolean,
    onSettingsToggle: () -> Unit,
    onSequenceToggle: () -> Unit = {}
) {
    val colors = BachTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.panel, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // MIDI status
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (isMidiConnected) colors.ledGreen else colors.ledRed.copy(alpha = 0.4f)
                )
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (isMidiConnected) "MIDI Connected" else "MIDI Waiting...",
            color = colors.textSecondary,
            fontSize = 10.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "KAONA",
            color = colors.accent.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif
        )

        Spacer(modifier = Modifier.weight(1f))

        // Sequence button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(colors.surfaceVariant)
                .clickable(onClick = onSequenceToggle)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "♪ SEQ",
                color = colors.textPrimary,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Settings button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(colors.surfaceVariant)
                .clickable(onClick = onSettingsToggle)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "⚙ Settings",
                color = colors.textPrimary,
                fontSize = 10.sp
            )
        }
    }
}
