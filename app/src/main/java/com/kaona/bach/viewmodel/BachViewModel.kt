package com.kaona.bach.viewmodel

import android.app.Application
import android.os.Process
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaona.bach.engine.*
import com.kaona.bach.midi.BachMidiService
import com.kaona.bach.midi.MidiAssign
import com.kaona.bach.midi.MidiCommand
import com.kaona.bach.midi.MidiInSettings
import com.kaona.bach.midi.MidiMonitor
import com.kaona.bach.midi.languageFromCc
import com.kaona.bach.midi.MidiOutputManager
import com.kaona.bach.model.Preset
import com.kaona.bach.model.PresetManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import java.util.concurrent.locks.LockSupport

/** Sinal de uma saída: [count] sobe a cada emissão (a UI usa para piscar) e [level] é o último valor (0-127). */
data class SignalState(val count: Int = 0, val level: Int = 0)

/**
 * Sinais das saídas, em um fluxo próprio: só os componentes que mostram os LEDs recompõem a
 * cada nota, em vez da tela inteira.
 */
data class SignalSnapshot(
    val gate: List<SignalState> = List(4) { SignalState() },
    val mod: List<SignalState> = List(4) { SignalState() },
    val pitch: List<SignalState> = List(4) { SignalState() }
)

@Immutable
data class UiState(
    val isPlaying: Boolean = false,
    val bpm: Int = 120,
    val currentLanguage: CompositionalLanguage = CompositionalLanguage.FUGUE,
    val rootNote: NoteName = NoteName.C,
    val scale: Scale = Scale.MAJOR,
    val material: Float = 0.5f,
    val memory: Float = 0.5f,
    val growth: Float = 0.5f,
    val morph: Float = 0.5f,
    val density: Float = 0.5f,
    val tension: Float = 0.5f,
    val interaction: Float = 0.5f,
    val humanize: Float = 0.0f,
    val currentBar: Int = 0,
    val currentBeat: Int = 0,
    val midiChannels: IntArray = intArrayOf(1, 2, 3, 4),
    val trackMutes: BooleanArray = booleanArrayOf(false, false, false, false),
    val trackActivity: IntArray = intArrayOf(0, 0, 0, 0),
    /** outputEnabled[tipo][voz]; tipo 0 = GATE, 1 = MOD, 2 = PITCH */
    val outputEnabled: List<List<Boolean>> = List(3) { List(4) { true } },
    val breakCharacter: BreakCharacter = BreakCharacter.AUTO,
    val breakBars: Int = 2,
    val isBreakActive: Boolean = false,
    val presetIndex: Int = 0,
    val presetName: String = "Init",
    val isMidiConnected: Boolean = false,
    val showSettings: Boolean = false,
    val showPresets: Boolean = false,
    val ornaments: Float = 0f,
    val sequence: HarmonicSequence = HarmonicSequence(),
    val currentChord: String = "",
    val playingStep: Int = -1,
    val showSequence: Boolean = false,
    val breakLive: Boolean = false,
    val music: MusicSettings = MusicSettings(),
    val groovePartner: CompositionalLanguage = CompositionalLanguage.CANON,
    /** 0 = nenhuma; 1 = MUSIC; 2 = MIDI IN; 3 = CONTROLS; 4 = VISUAL */
    val toolPage: Int = 0,
    /** Página mostrada no visor azul: 0 HOME, 1 MUSIC, 2 SEQ, 3 MIDI IN, 4 CONTROLS, 5 VISUAL, 6 SETTINGS, 7 PRESETS */
    val page: Int = 0,
    val externalClock: Boolean = false,
    val midiIn: MidiInSettings = MidiInSettings(),
    val learning: MidiCommand? = null
)

@Stable
class BachViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = BachEngine()
    private val midiOutput = MidiOutputManager()
    private val presetManager = PresetManager(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _signals = MutableStateFlow(SignalSnapshot())
    val signals: StateFlow<SignalSnapshot> = _signals.asStateFlow()

    private var clockJob: Job? = null
    private val ppqn = 96

    // Thread dedicada e de prioridade alta para o clock: não disputa com a UI nem com o pool padrão
    private val clockDispatcher: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(
                Runnable {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                    runnable.run()
                },
                "bach-clock"
            )
        }.asCoroutineDispatcher()

    private val _monitor = MutableStateFlow(MidiMonitor())
    val midiMonitor: StateFlow<MidiMonitor> = _monitor.asStateFlow()

    // Valor de referência dos knobs: o do último preset carregado/salvo, ou o de fábrica
    private var referenceParams = BachParams()

    private var msgCount = 0
    private var clkCount = 0
    private var extPulses = 0
    private var extLastQuarterNanos = 0L
    private var extLastPulseNanos = 0L
    private val commandHigh = BooleanArray(MidiCommand.entries.size)

    init {
        presetManager.initFactoryPresets()
        presetManager.loadMidiIn()?.let { saved -> _uiState.update { it.copy(midiIn = saved) } }
        presetManager.loadAutosave()?.let { applyPreset(it) }
        registerMidiCallbacks()
        startAutosave()
        startExternalClockWatchdog()
    }

    fun play() {
        if (_uiState.value.isPlaying) return
        midiOutput.allNotesOff()
        engine.play()
        val chord = engine.chordDisplayAt(engine.getState().currentTick)
        _uiState.value = _uiState.value.copy(
            isPlaying = true,
            currentChord = chord?.label ?: "",
            playingStep = chord?.step ?: -1
        )
        BachMidiService.sendStart()
        startClock()
    }

    fun stop() {
        clockJob?.cancel()
        clockJob = null
        engine.stop()
        midiOutput.allNotesOff()
        BachMidiService.sendStop()
        _uiState.value = _uiState.value.copy(
            isPlaying = false,
            trackActivity = intArrayOf(0, 0, 0, 0),
            currentChord = "",
            playingStep = -1
        )
    }

    fun togglePlayStop() {
        if (_uiState.value.isPlaying) stop() else play()
    }

    /** RESET reinicia a forma e volta a SEQUENCE à posição 1; se estava tocando, continua tocando. */
    fun reset() {
        val wasPlaying = _uiState.value.isPlaying
        stop()
        engine.reset()
        _uiState.update { it.copy(currentBar = 0, currentBeat = 0) }
        if (wasPlaying) play()
    }

    fun triggerBreak() {
        engine.triggerBreak()
        _uiState.value = _uiState.value.copy(isBreakActive = true)
    }

    fun releaseBreak() {
        engine.releaseBreak()
        _uiState.value = _uiState.value.copy(isBreakActive = false)
    }

    fun setLanguage(language: CompositionalLanguage) {
        markDirty()
        // Ao trocar de algoritmo, MATERIAL volta ao centro da escolha mais próxima
        val centered = MaterialCatalog.center(language, _uiState.value.material)
        engine.setLanguage(language)
        engine.updateParam("material", centered)
        _uiState.value = _uiState.value.copy(currentLanguage = language, material = centered)
    }

    fun setBpm(bpm: Int) {
        markDirty()
        engine.setBpm(bpm)
        _uiState.value = _uiState.value.copy(bpm = bpm.coerceIn(30, 300))
    }

    fun setRoot(root: NoteName) {
        markDirty()
        engine.setRoot(root)
        _uiState.value = _uiState.value.copy(rootNote = root)
        updateMusic { it } // no C6 só a nota C é válida
    }

    fun setScale(scale: Scale) {
        markDirty()
        engine.setScale(scale)
        _uiState.value = _uiState.value.copy(scale = scale)
    }

    fun updateParam(param: String, value: Float) {
        engine.updateParam(param, value)
        markDirty()
        _uiState.update {
            when (param) {
                "material" -> it.copy(material = value)
                "memory" -> it.copy(memory = value)
                "growth" -> it.copy(growth = value)
                "morph" -> it.copy(morph = value)
                "density" -> it.copy(density = value)
                "tension" -> it.copy(tension = value)
                "interaction" -> it.copy(interaction = value)
                "humanize" -> it.copy(humanize = value)
                else -> it
            }
        }
    }

    fun setMidiChannel(track: Int, channel: Int) {
        markDirty()
        engine.setMidiChannel(track, channel)
        val channels = _uiState.value.midiChannels.copyOf()
        channels[track] = channel
        _uiState.value = _uiState.value.copy(midiChannels = channels)
    }

    fun toggleTrackMute(track: Int) {
        engine.toggleTrackMute(track)
        val mutes = _uiState.value.trackMutes.copyOf()
        mutes[track] = !mutes[track]
        _uiState.value = _uiState.value.copy(trackMutes = mutes)
    }

    /** Liga/desliga uma única saída (tipo 0 = GATE, 1 = MOD, 2 = PITCH) de uma voz. */
    fun toggleOutput(type: Int, track: Int) {
        _uiState.update { cur ->
            cur.copy(
                outputEnabled = cur.outputEnabled.mapIndexed { t, row ->
                    if (t == type) row.mapIndexed { i, on -> if (i == track) !on else on } else row
                }
            )
        }
    }

    /** Liga todas as vozes de um tipo; se já estiverem todas ligadas, desliga todas. */
    fun toggleOutputRow(type: Int) {
        _uiState.update { cur ->
            val target = !cur.outputEnabled[type].all { it }
            cur.copy(
                outputEnabled = cur.outputEnabled.mapIndexed { t, row ->
                    if (t == type) List(row.size) { target } else row
                }
            )
        }
    }

    private fun List<SignalState>.bump(hits: IntArray, levels: IntArray): List<SignalState> =
        mapIndexed { i, s -> if (hits[i] > 0) SignalState(s.count + hits[i], levels[i]) else s }

    private fun applyBreakConfig(character: BreakCharacter, bars: Int, live: Boolean) {
        markDirty()
        engine.setBreakConfig(BreakConfig(character, bars, live))
        _uiState.update { it.copy(breakCharacter = character, breakBars = bars, breakLive = live) }
    }

    fun setBreakCharacter(character: BreakCharacter) {
        val ui = _uiState.value
        applyBreakConfig(character, ui.breakBars, ui.breakLive)
    }

    fun setBreakBars(bars: Int) {
        applyBreakConfig(_uiState.value.breakCharacter, bars, false)
    }

    /** LIVE: o Break não tem fim marcado e fecha num segundo toque em BREAK. */
    fun setBreakLive() {
        val ui = _uiState.value
        applyBreakConfig(ui.breakCharacter, ui.breakBars, true)
    }

    /** Muda a página do visor azul: 0 HOME, 1 MUSIC, 2 SEQ, 3 MIDI IN, 4 CONTROLS, 5 VISUAL, 6 SETTINGS, 7 PRESETS. */
    fun setPage(page: Int) {
        _uiState.update { it.copy(page = page.coerceIn(0, 7)) }
    }

    fun toggleSettings() {
        setPage(if (_uiState.value.page == 6) 0 else 6)
    }

    fun toggleSequence() {
        setPage(if (_uiState.value.page == 2) 0 else 2)
    }

    fun setOrnaments(value: Float) {
        markDirty()
        engine.setOrnaments(value)
        _uiState.update { it.copy(ornaments = value) }
    }

    private fun applySequence(sequence: HarmonicSequence, restart: Boolean = false) {
        markDirty()
        engine.setSequence(sequence, restart)
        _uiState.update { it.copy(sequence = sequence) }
    }

    fun setSequenceEnabled(enabled: Boolean) {
        applySequence(_uiState.value.sequence.copy(enabled = enabled))
    }

    fun updateSequenceStep(index: Int, step: ChordStep) {
        applySequence(_uiState.value.sequence.withStep(index, step))
    }

    fun clearSequence() {
        applySequence(HarmonicSequence(enabled = _uiState.value.sequence.enabled), restart = true)
    }

    fun loadFactorySequence(form: Int, speed: Int) {
        applySequence(FactorySequences.build(form, speed), restart = true)
    }

    fun saveUserSequence(slot: Int) {
        presetManager.saveUserSequence(slot, _uiState.value.sequence.encode())
    }

    fun loadUserSequence(slot: Int) {
        val encoded = presetManager.loadUserSequence(slot) ?: return
        applySequence(HarmonicSequence.decode(encoded, true), restart = true)
    }

    fun togglePresets() {
        setPage(if (_uiState.value.page == 7) 0 else 7)
    }

    fun savePreset(index: Int, name: String) {
        val state = engine.getState()
        val preset = presetManager.engineStateToPreset(state, index, name)
        presetManager.savePreset(preset)
        referenceParams = state.params
        _uiState.value = _uiState.value.copy(presetIndex = index, presetName = name)
    }

    fun loadPreset(index: Int) {
        val preset = presetManager.loadPreset(index) ?: return
        applyPreset(preset)
    }

    private fun applyPreset(preset: Preset) {
        markDirty()
        val state = presetManager.presetToEngineState(preset)
        engine.setState(state)
        referenceParams = state.params
        _uiState.update {
            it.copy(
                bpm = preset.bpm,
                currentLanguage = state.language,
                rootNote = state.rootNote,
                scale = state.scale,
                material = preset.material,
                memory = preset.memory,
                growth = preset.growth,
                morph = preset.morph,
                density = preset.density,
                tension = preset.tension,
                interaction = preset.interaction,
                humanize = preset.humanize,
                midiChannels = (preset.midiChannels ?: listOf(1, 2, 3, 4)).toIntArray(),
                breakCharacter = state.breakConfig.character,
                breakBars = preset.breakBars,
                breakLive = preset.breakLive,
                presetIndex = preset.index,
                presetName = preset.name,
                ornaments = preset.ornaments,
                sequence = state.sequence,
                music = state.music,
                groovePartner = state.groovePartner
            )
        }
    }

    // ---------------- MUSIC, telas e referência dos knobs ----------------

    fun updateMusic(transform: (MusicSettings) -> MusicSettings) {
        markDirty()
        var m = transform(_uiState.value.music)
        m = m.copy(swing = m.swing.coerceIn(0f, 1f), rootOctave = m.rootOctave.coerceIn(1, 6))
        // O alcance do ROOT vai de C1 a C6: no C6 só a nota C é válida
        if (m.rootOctave >= 6 && _uiState.value.rootNote != NoteName.C) m = m.copy(rootOctave = 5)
        engine.setMusic(m)
        _uiState.update { it.copy(music = m) }
    }

    fun setGroovePartner(language: CompositionalLanguage) {
        markDirty()
        engine.setGroovePartner(language)
        _uiState.update { it.copy(groovePartner = language) }
    }

    fun setToolPage(page: Int) {
        // 1 MUSIC, 2 MIDI IN, 3 CONTROLS, 4 VISUAL -> páginas do visor
        val target = when (page) {
            1 -> 1
            2 -> 3
            3 -> 4
            4 -> 5
            else -> 0
        }
        setPage(if (_uiState.value.page == target) 0 else target)
    }

    /** Clique no knob: volta ao valor de referência (o do preset ou o de fábrica). */
    fun resetParam(param: String) {
        val p = referenceParams
        val value = when (param) {
            "material" -> p.material
            "memory" -> p.memory
            "growth" -> p.growth
            "morph" -> p.morph
            "density" -> p.density
            "tension" -> p.tension
            "interaction" -> p.interaction
            "humanize" -> p.humanize
            else -> return
        }
        updateParam(param, value)
    }

    // ---------------- MIDI IN ----------------

    private fun updateMidiIn(transform: (MidiInSettings) -> MidiInSettings) {
        val updated = transform(_uiState.value.midiIn)
        _uiState.update { it.copy(midiIn = updated) }
        presetManager.saveMidiIn(updated)
    }

    fun setClockEnabled(enabled: Boolean) = updateMidiIn { it.copy(clockEnabled = enabled) }

    fun setResetOnStop(enabled: Boolean) = updateMidiIn { it.copy(resetOnStop = enabled) }

    fun setRootChannel(channel: Int) = updateMidiIn { it.copy(rootChannel = channel) }

    fun setAssign(command: MidiCommand, assign: MidiAssign) = updateMidiIn { settings ->
        settings.copy(
            assignments = settings.assignments.mapIndexed { i, a -> if (i == command.ordinal) assign else a }
        )
    }

    fun clearAssign(command: MidiCommand) = setAssign(command, MidiAssign(1, -1))

    /** Aprendizado: o próximo CC recebido (canal e número) é atribuído ao comando. */
    fun startLearn(command: MidiCommand?) {
        _uiState.update { it.copy(learning = if (it.learning == command) null else command) }
    }

    private fun registerMidiCallbacks() {
        BachMidiService.onMidiControlChange = { ch, cc, value ->
            viewModelScope.launch(Dispatchers.Main) { handleCc(ch, cc, value) }
        }
        BachMidiService.onMidiNoteOn = { ch, note, _ ->
            viewModelScope.launch(Dispatchers.Main) { handleNoteOn(ch, note) }
        }
        BachMidiService.onMidiClockReceived = { onExternalClockPulse() }
        BachMidiService.onMidiStartReceived = {
            viewModelScope.launch(Dispatchers.Main) {
                if (_uiState.value.midiIn.clockEnabled) {
                    reset()
                    play()
                }
            }
        }
        BachMidiService.onMidiContinueReceived = {
            viewModelScope.launch(Dispatchers.Main) {
                if (_uiState.value.midiIn.clockEnabled) reset()
            }
        }
        BachMidiService.onMidiStopReceived = {
            viewModelScope.launch(Dispatchers.Main) {
                val settings = _uiState.value.midiIn
                if (settings.clockEnabled) {
                    stop()
                    if (settings.resetOnStop) reset()
                }
            }
        }
    }

    private fun handleCc(channel: Int, cc: Int, value: Int) {
        msgCount++
        _monitor.update { it.copy(channel = channel, cc = cc, value = value, messages = msgCount) }

        val learning = _uiState.value.learning
        if (learning != null) {
            setAssign(learning, MidiAssign(channel, cc))
            _uiState.update { it.copy(learning = null) }
            return
        }

        val settings = _uiState.value.midiIn
        for (command in MidiCommand.entries) {
            val a = settings.assignments.getOrNull(command.ordinal) ?: continue
            if (a.cc < 0 || a.cc != cc) continue
            if (a.channel != 0 && a.channel != channel) continue
            applyMidiCommand(command, value)
        }
    }

    private fun applyMidiCommand(command: MidiCommand, value: Int) {
        // RESET, BREAK e PLAY/STOP agem na borda de subida (0-63 = 0, 64-127 = 1)
        val high = value >= 64
        val wasHigh = commandHigh[command.ordinal]
        commandHigh[command.ordinal] = high

        when (command) {
            MidiCommand.ALGORITHM -> setLanguage(languageFromCc(value))
            MidiCommand.RESET -> if (high && !wasHigh) reset()
            MidiCommand.BREAK -> if (high && !wasHigh) {
                if (_uiState.value.isBreakActive) releaseBreak() else triggerBreak()
            }
            MidiCommand.PLAY_STOP -> if (high && !wasHigh) togglePlayStop()
            else -> command.paramName?.let { updateParam(it, value / 127f) }
        }
    }

    /** Uma nota recebida define o ROOT (e a oitava, de C1 a C6). */
    private fun handleNoteOn(channel: Int, note: Int) {
        msgCount++
        _monitor.update { it.copy(channel = channel, messages = msgCount) }

        val rootChannel = _uiState.value.midiIn.rootChannel
        if (rootChannel < 0) return
        if (rootChannel != 0 && rootChannel != channel) return

        val pc = note % 12
        var octave = (note / 12 - 1).coerceIn(1, 6)
        if (octave == 6 && pc != 0) octave = 5
        val root = NoteName.entries[pc]
        engine.setRoot(root)
        _uiState.update { it.copy(rootNote = root) }
        updateMusic { it.copy(rootOctave = octave) }
    }

    /** Clock MIDI (F8): 24 pulsos por semínima; o andamento é medido numa semínima completa. */
    private fun onExternalClockPulse() {
        val now = System.nanoTime()
        clkCount++
        if (clkCount % 24 == 0) _monitor.update { it.copy(clocks = clkCount) }

        if (!_uiState.value.midiIn.clockEnabled) return
        extLastPulseNanos = now
        extPulses++
        if (extPulses % 24 == 0) {
            if (extLastQuarterNanos != 0L) {
                val bpm = (60_000_000_000.0 / (now - extLastQuarterNanos)).roundToInt().coerceIn(40, 240)
                engine.setBpm(bpm)
                _uiState.update { it.copy(bpm = bpm, externalClock = true) }
            }
            extLastQuarterNanos = now
        }
    }

    /** Sem pulsos por ~2,5 s, o relógio interno volta a valer (o asterisco do BPM some). */
    private fun startExternalClockWatchdog() {
        viewModelScope.launch {
            while (true) {
                delay(500)
                if (_uiState.value.externalClock && System.nanoTime() - extLastPulseNanos > 2_500_000_000L) {
                    extLastQuarterNanos = 0L
                    extPulses = 0
                    _uiState.update { it.copy(externalClock = false) }
                }
            }
        }
    }

    /** O módulo salva continuamente os ajustes e os retoma ao reiniciar. */
    private val dirty = MutableStateFlow(0)

    private fun markDirty() {
        dirty.update { it + 1 }
    }

    /** O módulo salva os ajustes e os retoma ao reiniciar; só grava depois de 0,8 s sem mudanças. */
    private fun startAutosave() {
        viewModelScope.launch {
            dirty.collectLatest {
                delay(800)
                val ui = _uiState.value
                presetManager.saveAutosave(
                    presetManager.engineStateToPreset(engine.getState(), ui.presetIndex, ui.presetName)
                )
            }
        }
    }

    fun nextPreset() {
        val next = (_uiState.value.presetIndex + 1) % 128
        loadPreset(next)
    }

    fun prevPreset() {
        val prev = if (_uiState.value.presetIndex > 0) _uiState.value.presetIndex - 1 else 127
        loadPreset(prev)
    }

    private fun startClock() {
        clockJob = viewModelScope.launch(clockDispatcher) {
            // Agenda absoluta: cada tick é calculado a partir do anterior, então o tempo gasto
            // processando ou dormindo não se acumula (sem drift).
            var nextTickNanos = System.nanoTime()

            try {
            while (isActive) {
                val bpm = _uiState.value.bpm
                val tickIntervalNanos = (60_000_000_000.0 / (bpm * ppqn)).toLong()

                val events = engine.processTick()
                val engineState = engine.getState()

                // Fecha as notas cujo tempo acabou (precisão de tick)
                midiOutput.onTick(engineState.currentTick)

                if (events.isNotEmpty() && isActive) {
                    val enabled = _uiState.value.outputEnabled
                    val gateHits = IntArray(4)
                    val gateLevel = IntArray(4)
                    val modHits = IntArray(4)
                    val modLevel = IntArray(4)
                    val pitchHits = IntArray(4)
                    val pitchLevel = IntArray(4)
                    val lastNote = IntArray(4) { -1 }

                    for (event in events) {
                        val track = event.track.coerceIn(0, 3)
                        val gateOn = enabled[0].getOrElse(track) { true }
                        val modOn = enabled[1].getOrElse(track) { true }
                        val pitchOn = enabled[2].getOrElse(track) { true }
                        val channel = engineState.midiChannels.getOrElse(event.track) { event.track + 1 }

                        midiOutput.sendNoteEvent(event, channel, gateOn, modOn, pitchOn)

                        if (gateOn) {
                            gateHits[track]++
                            gateLevel[track] = event.velocity
                            lastNote[track] = event.midiNote
                            if (pitchOn) {
                                pitchHits[track]++
                                pitchLevel[track] = event.midiNote
                            }
                        }
                        if (modOn) {
                            modHits[track]++
                            modLevel[track] = event.modValue
                        }
                    }

                    _signals.update { cur ->
                        cur.copy(
                            gate = cur.gate.bump(gateHits, gateLevel),
                            mod = cur.mod.bump(modHits, modLevel),
                            pitch = cur.pitch.bump(pitchHits, pitchLevel)
                        )
                    }

                    // A tela principal só é recomposta quando uma voz toca pela primeira vez
                    val activityNow = _uiState.value.trackActivity
                    if ((0..3).any { lastNote[it] >= 0 && activityNow[it] == 0 }) {
                        _uiState.update { cur ->
                            val activity = cur.trackActivity.copyOf()
                            for (t in 0..3) if (lastNote[t] >= 0 && activity[t] == 0) activity[t] = lastNote[t]
                            cur.copy(trackActivity = activity)
                        }
                    }
                }

                if (engineState.currentTick % RhythmUtils.SIXTEENTH == 0L) {
                    val chord = engine.chordDisplayAt(engineState.currentTick)
                    val label = chord?.label ?: ""
                    val stepIndex = chord?.step ?: -1
                    val cur = _uiState.value
                    if (cur.currentChord != label || cur.playingStep != stepIndex) {
                        _uiState.update { it.copy(currentChord = label, playingStep = stepIndex) }
                    }
                }

                if (engineState.currentTick % engineState.music.meter.beatTicks == 0L) {
                    // StateFlow é thread-safe: não precisa (nem deve) esperar a thread principal
                    _uiState.update {
                        it.copy(
                            currentBar = engineState.currentBar,
                            currentBeat = engineState.currentBeat,
                            isBreakActive = engineState.isBreakActive,
                            isMidiConnected = BachMidiService.isConnected
                        )
                    }
                }

                if (engineState.currentTick % (ppqn / 24) == 0L) {
                    BachMidiService.sendClock()
                }

                // Próximo tick; se ficou muito atrasado (app pausado), ressincroniza
                nextTickNanos += tickIntervalNanos
                if (System.nanoTime() - nextTickNanos > 50_000_000L) {
                    nextTickNanos = System.nanoTime()
                }

                // Dorme quase tudo e gira só nos últimos ~150 µs para acertar o instante
                while (isActive) {
                    val remaining = nextTickNanos - System.nanoTime()
                    if (remaining <= 0L) break
                    if (remaining > 300_000L) LockSupport.parkNanos(remaining - 150_000L)
                }
            }
            } finally {
                // Garante que nenhuma nota fique presa se o stop() chegar no meio de um tick
                midiOutput.allNotesOff()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stop()
        midiOutput.destroy()
        clockDispatcher.close()
        BachMidiService.onMidiControlChange = null
        BachMidiService.onMidiNoteOn = null
        BachMidiService.onMidiClockReceived = null
        BachMidiService.onMidiStartReceived = null
        BachMidiService.onMidiContinueReceived = null
        BachMidiService.onMidiStopReceived = null
    }
}
