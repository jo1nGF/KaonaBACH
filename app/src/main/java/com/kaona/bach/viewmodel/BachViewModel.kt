package com.kaona.bach.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaona.bach.engine.*
import com.kaona.bach.midi.BachMidiService
import com.kaona.bach.midi.MidiOutputManager
import com.kaona.bach.model.PresetManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Sinal de uma saída: [count] sobe a cada emissão (a UI usa para piscar) e [level] é o último valor (0-127). */
data class SignalState(val count: Int = 0, val level: Int = 0)

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
    val gateSignals: List<SignalState> = List(4) { SignalState() },
    val modSignals: List<SignalState> = List(4) { SignalState() },
    val pitchSignals: List<SignalState> = List(4) { SignalState() },
    val breakCharacter: BreakCharacter = BreakCharacter.AUTO,
    val breakBars: Int = 2,
    val isBreakActive: Boolean = false,
    val presetIndex: Int = 0,
    val presetName: String = "Init",
    val isMidiConnected: Boolean = false,
    val showSettings: Boolean = false,
    val showPresets: Boolean = false
)

class BachViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = BachEngine()
    private val midiOutput = MidiOutputManager()
    private val presetManager = PresetManager(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var clockJob: Job? = null
    private val ppqn = 96

    init {
        presetManager.initFactoryPresets()
    }

    fun play() {
        if (_uiState.value.isPlaying) return
        engine.play()
        _uiState.value = _uiState.value.copy(isPlaying = true)
        BachMidiService.sendStart()
        startClock()
    }

    fun stop() {
        clockJob?.cancel()
        clockJob = null
        engine.stop()
        midiOutput.allNotesOff()
        BachMidiService.sendStop()
        _uiState.value = _uiState.value.copy(isPlaying = false, currentBar = 0, currentBeat = 0)
    }

    fun togglePlayStop() {
        if (_uiState.value.isPlaying) stop() else play()
    }

    fun reset() {
        stop()
        engine.reset()
        _uiState.value = _uiState.value.copy(currentBar = 0, currentBeat = 0)
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
        engine.setLanguage(language)
        _uiState.value = _uiState.value.copy(currentLanguage = language)
    }

    fun setBpm(bpm: Int) {
        engine.setBpm(bpm)
        _uiState.value = _uiState.value.copy(bpm = bpm.coerceIn(30, 300))
    }

    fun setRoot(root: NoteName) {
        engine.setRoot(root)
        _uiState.value = _uiState.value.copy(rootNote = root)
    }

    fun setScale(scale: Scale) {
        engine.setScale(scale)
        _uiState.value = _uiState.value.copy(scale = scale)
    }

    fun updateParam(param: String, value: Float) {
        engine.updateParam(param, value)
        _uiState.value = when (param) {
            "material" -> _uiState.value.copy(material = value)
            "memory" -> _uiState.value.copy(memory = value)
            "growth" -> _uiState.value.copy(growth = value)
            "morph" -> _uiState.value.copy(morph = value)
            "density" -> _uiState.value.copy(density = value)
            "tension" -> _uiState.value.copy(tension = value)
            "interaction" -> _uiState.value.copy(interaction = value)
            "humanize" -> _uiState.value.copy(humanize = value)
            else -> _uiState.value
        }
    }

    fun setMidiChannel(track: Int, channel: Int) {
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

    fun setBreakCharacter(character: BreakCharacter) {
        engine.setBreakConfig(BreakConfig(character, _uiState.value.breakBars))
        _uiState.value = _uiState.value.copy(breakCharacter = character)
    }

    fun setBreakBars(bars: Int) {
        engine.setBreakConfig(BreakConfig(_uiState.value.breakCharacter, bars))
        _uiState.value = _uiState.value.copy(breakBars = bars)
    }

    fun toggleSettings() {
        _uiState.value = _uiState.value.copy(showSettings = !_uiState.value.showSettings)
    }

    fun togglePresets() {
        _uiState.value = _uiState.value.copy(showPresets = !_uiState.value.showPresets)
    }

    fun savePreset(index: Int, name: String) {
        val state = engine.getState()
        val preset = presetManager.engineStateToPreset(state, index, name)
        presetManager.savePreset(preset)
        _uiState.value = _uiState.value.copy(presetIndex = index, presetName = name)
    }

    fun loadPreset(index: Int) {
        val preset = presetManager.loadPreset(index) ?: return
        val state = presetManager.presetToEngineState(preset)
        engine.setState(state)
        _uiState.value = _uiState.value.copy(
            bpm = preset.bpm,
            currentLanguage = try { CompositionalLanguage.valueOf(preset.language) } catch (_: Exception) { CompositionalLanguage.FUGUE },
            rootNote = try { NoteName.valueOf(preset.rootNote) } catch (_: Exception) { NoteName.C },
            scale = try { Scale.valueOf(preset.scaleName) } catch (_: Exception) { Scale.MAJOR },
            material = preset.material,
            memory = preset.memory,
            growth = preset.growth,
            morph = preset.morph,
            density = preset.density,
            tension = preset.tension,
            interaction = preset.interaction,
            humanize = preset.humanize,
            midiChannels = preset.midiChannels.toIntArray(),
            breakCharacter = try { BreakCharacter.valueOf(preset.breakCharacter) } catch (_: Exception) { BreakCharacter.AUTO },
            breakBars = preset.breakBars,
            presetIndex = preset.index,
            presetName = preset.name
        )
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
        clockJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val bpm = _uiState.value.bpm
                val tickIntervalUs = (60_000_000.0 / (bpm * ppqn)).toLong()

                val events = engine.processTick()
                val engineState = engine.getState()

                if (events.isNotEmpty()) {
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

                        midiOutput.sendNoteEvent(event, channel, bpm, gateOn, modOn, pitchOn)

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

                    _uiState.update { cur ->
                        val activity = cur.trackActivity.copyOf()
                        for (t in 0..3) if (lastNote[t] >= 0) activity[t] = lastNote[t]
                        cur.copy(
                            gateSignals = cur.gateSignals.bump(gateHits, gateLevel),
                            modSignals = cur.modSignals.bump(modHits, modLevel),
                            pitchSignals = cur.pitchSignals.bump(pitchHits, pitchLevel),
                            trackActivity = activity
                        )
                    }
                }

                if (engineState.currentTick % ppqn == 0L) {
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                currentBar = engineState.currentBar,
                                currentBeat = engineState.currentBeat,
                                isBreakActive = engineState.isBreakActive,
                                isMidiConnected = BachMidiService.isConnected
                            )
                        }
                    }
                }

                if (engineState.currentTick % (ppqn / 24) == 0L) {
                    BachMidiService.sendClock()
                }

                if (tickIntervalUs > 1000) {
                    delay(tickIntervalUs / 1000)
                } else {
                    val end = System.nanoTime() + tickIntervalUs * 1000
                    @Suppress("ControlFlowWithEmptyBody")
                    while (System.nanoTime() < end) { }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stop()
        midiOutput.destroy()
    }
}
