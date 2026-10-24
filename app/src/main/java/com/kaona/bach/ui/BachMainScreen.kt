package com.kaona.bach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kaona.bach.engine.CompositionalLanguage
import com.kaona.bach.engine.MaterialCatalog
import com.kaona.bach.ui.components.*
import com.kaona.bach.ui.theme.BachTheme
import com.kaona.bach.viewmodel.BachViewModel

@Composable
fun BachMainScreen(viewModel: BachViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BachTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        HeaderSection()

        Spacer(modifier = Modifier.height(4.dp))

        // CV Input buttons row
        CVInputButtonsRow(state = state, signals = viewModel.signals)

        Spacer(modifier = Modifier.height(8.dp))

        // Transport controls
        TransportControls(
            isPlaying = state.isPlaying,
            isBreakActive = state.isBreakActive,
            onPlayStop = { viewModel.togglePlayStop() },
            onReset = { viewModel.reset() },
            onBreak = { viewModel.triggerBreak() },
            onBreakRelease = { viewModel.releaseBreak() }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Main content: Knobs + Display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left column: Parameter knobs
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ParameterKnob(
                        label = "Material",
                        value = state.material,
                        onValueChange = { viewModel.updateParam("material", it) },
                        onReset = { viewModel.resetParam("material") }
                    )
                    ParameterKnob(
                        label = "Density",
                        value = state.density,
                        onValueChange = { viewModel.updateParam("density", it) },
                        onReset = { viewModel.resetParam("density") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ParameterKnob(
                        label = "Memory",
                        value = state.memory,
                        onValueChange = { viewModel.updateParam("memory", it) },
                        onReset = { viewModel.resetParam("memory") }
                    )
                    ParameterKnob(
                        label = "Tension",
                        value = state.tension,
                        onValueChange = { viewModel.updateParam("tension", it) },
                        onReset = { viewModel.resetParam("tension") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ParameterKnob(
                        label = "Growth",
                        value = state.growth,
                        onValueChange = { viewModel.updateParam("growth", it) },
                        onReset = { viewModel.resetParam("growth") }
                    )
                    ParameterKnob(
                        label = "Interaction",
                        value = state.interaction,
                        onValueChange = { viewModel.updateParam("interaction", it) },
                        onReset = { viewModel.resetParam("interaction") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ParameterKnob(
                        label = "Morph",
                        value = state.morph,
                        onValueChange = { viewModel.updateParam("morph", it) },
                        onReset = { viewModel.resetParam("morph") }
                    )
                    ParameterKnob(
                        label = "Humanize",
                        value = state.humanize,
                        onValueChange = { viewModel.updateParam("humanize", it) },
                        onReset = { viewModel.resetParam("humanize") }
                    )
                }
            }

            // Right column: Display + BPM + Presets
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
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

                BpmControl(
                    bpm = state.bpm,
                    onBpmChange = { viewModel.setBpm(it) }
                )

                PresetNavigation(
                    presetIndex = state.presetIndex,
                    presetName = state.presetName,
                    onPrev = { viewModel.prevPreset() },
                    onNext = { viewModel.nextPreset() },
                    onPresetTap = { viewModel.togglePresets() }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Output jacks
        OutputJacksSection(
            channels = state.midiChannels,
            trackMutes = state.trackMutes,
            outputEnabled = state.outputEnabled,
            signals = viewModel.signals,
            onToggleOutput = { type, track -> viewModel.toggleOutput(type, track) },
            onToggleRow = { viewModel.toggleOutputRow(it) },
            onToggleMute = { viewModel.toggleTrackMute(it) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (state.showSettings) {
            SettingsPanel(
                rootNote = state.rootNote,
                scale = state.scale,
                ornaments = state.ornaments,
                breakCharacter = state.breakCharacter,
                breakBars = state.breakBars,
                breakLive = state.breakLive,
                midiChannels = state.midiChannels,
                onRootChange = { viewModel.setRoot(it) },
                onScaleChange = { viewModel.setScale(it) },
                onOrnamentsChange = { viewModel.setOrnaments(it) },
                onBreakCharChange = { viewModel.setBreakCharacter(it) },
                onBreakBarsChange = { viewModel.setBreakBars(it) },
                onBreakLive = { viewModel.setBreakLive() },
                onMidiChannelChange = { track, ch -> viewModel.setMidiChannel(track, ch) }
            )
        }

        if (state.showSequence) {
            SequencePanel(
                sequence = state.sequence,
                language = state.currentLanguage,
                isPlaying = state.isPlaying,
                playingStep = state.playingStep,
                currentChord = state.currentChord,
                onStepChange = { index, step -> viewModel.updateSequenceStep(index, step) },
                onEnabledChange = { viewModel.setSequenceEnabled(it) },
                onClear = { viewModel.clearSequence() },
                onLoadFactory = { form, speed -> viewModel.loadFactorySequence(form, speed) },
                onLoadUser = { viewModel.loadUserSequence(it) },
                onSaveUser = { viewModel.saveUserSequence(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        when (state.toolPage) {
            1 -> {
                MusicPanel(
                    music = state.music,
                    rootNote = state.rootNote,
                    onRootChange = { viewModel.setRoot(it) },
                    onChange = { transform -> viewModel.updateMusic(transform) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            2 -> {
                MidiInPanel(
                    settings = state.midiIn,
                    learning = state.learning,
                    monitor = viewModel.midiMonitor,
                    onClockEnabled = { viewModel.setClockEnabled(it) },
                    onResetOnStop = { viewModel.setResetOnStop(it) },
                    onRootChannel = { viewModel.setRootChannel(it) },
                    onAssign = { command, assign -> viewModel.setAssign(command, assign) },
                    onClear = { viewModel.clearAssign(it) },
                    onLearn = { viewModel.startLearn(it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            3 -> {
                ControlsPanel(state = state)
                Spacer(modifier = Modifier.height(12.dp))
            }
            4 -> {
                VisualPanel(state = state, signals = viewModel.signals)
                Spacer(modifier = Modifier.height(12.dp))
            }
            else -> {}
        }

        if (state.showPresets) {
            PresetsPanel(
                currentIndex = state.presetIndex,
                onLoadPreset = { viewModel.loadPreset(it) },
                onSavePreset = { idx, name -> viewModel.savePreset(idx, name) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        ToolsRow(activePage = state.toolPage, onSelect = { viewModel.setToolPage(it) })

        Spacer(modifier = Modifier.height(8.dp))

        LanguageSelector(
            currentLanguage = state.currentLanguage,
            onLanguageSelect = { viewModel.setLanguage(it) }
        )

        if (state.currentLanguage == CompositionalLanguage.GROOVE_MIX) {
            Spacer(modifier = Modifier.height(6.dp))
            GroovePartnerRow(
                current = state.groovePartner,
                onSelect = { viewModel.setGroovePartner(it) }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        BottomBar(
            isMidiConnected = state.isMidiConnected,
            onSettingsToggle = { viewModel.toggleSettings() },
            onSequenceToggle = { viewModel.toggleSequence() }
        )
    }
}
