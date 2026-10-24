package com.kaona.bach.model

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kaona.bach.engine.*
import com.kaona.bach.midi.MidiInSettings

/**
 * Manages saving/loading of presets (up to 128, matching hardware).
 */
data class Preset(
    val index: Int = 0,
    val name: String = "Init",
    val language: String = "FUGUE",
    val bpm: Int = 120,
    val rootNote: String = "C",
    val scaleName: String = "MAJOR",
    val material: Float = 0.5f,
    val memory: Float = 0.5f,
    val growth: Float = 0.5f,
    val morph: Float = 0.5f,
    val density: Float = 0.5f,
    val tension: Float = 0.5f,
    val interaction: Float = 0.5f,
    val humanize: Float = 0.0f,
    val midiChannels: List<Int> = listOf(1, 2, 3, 4),
    val breakCharacter: String = "AUTO",
    val breakBars: Int = 2,
    val ornaments: Float = 0f,
    val sequence: String = "",
    val sequenceEnabled: Boolean = true,
    val durationMode: String = "VARIABLE",
    val velocityMode: String = "MODULATED",
    val meter: String = "M44",
    val swing: Float = 0f,
    val phraseBars: Int = 0,
    val rootOctave: Int = 4,
    val groovePartner: String = "CANON",
    val breakLive: Boolean = false
)

class PresetManager(private val context: Context) {
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("bach_presets", Context.MODE_PRIVATE)
    private val maxPresets = 128

    fun savePreset(preset: Preset) {
        val json = gson.toJson(preset)
        prefs.edit().putString("preset_${preset.index}", json).apply()
    }

    fun loadPreset(index: Int): Preset? {
        val json = prefs.getString("preset_$index", null) ?: return null
        return try {
            gson.fromJson(json, Preset::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /** O módulo salva continuamente o estado e o retoma ao reiniciar. */
    fun saveAutosave(preset: Preset) {
        prefs.edit().putString("autosave", gson.toJson(preset)).apply()
    }

    fun loadAutosave(): Preset? {
        val json = prefs.getString("autosave", null) ?: return null
        return try {
            gson.fromJson(json, Preset::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun saveMidiIn(settings: MidiInSettings) {
        prefs.edit().putString("midi_in", gson.toJson(settings)).apply()
    }

    fun loadMidiIn(): MidiInSettings? {
        val json = prefs.getString("midi_in", null) ?: return null
        return try {
            gson.fromJson(json, MidiInSettings::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /** Slots de usuário da SEQUENCE (progressões salvas à parte dos presets). */
    fun saveUserSequence(slot: Int, encoded: String) {
        prefs.edit().putString("sequence_$slot", encoded).apply()
    }

    fun loadUserSequence(slot: Int): String? = prefs.getString("sequence_$slot", null)

    fun getAllPresets(): List<Preset> {
        val presets = mutableListOf<Preset>()
        for (i in 0 until maxPresets) {
            loadPreset(i)?.let { presets.add(it) }
        }
        return presets
    }

    fun presetToEngineState(preset: Preset): EngineState {
        return EngineState(
            bpm = preset.bpm,
            language = try { CompositionalLanguage.valueOf(preset.language) } catch (e: Exception) { CompositionalLanguage.FUGUE },
            params = BachParams(
                material = preset.material,
                memory = preset.memory,
                growth = preset.growth,
                morph = preset.morph,
                density = preset.density,
                tension = preset.tension,
                interaction = preset.interaction,
                humanize = preset.humanize
            ),
            rootNote = try { NoteName.valueOf(preset.rootNote) } catch (e: Exception) { NoteName.C },
            scale = try { Scale.valueOf(preset.scaleName) } catch (e: Exception) { Scale.MAJOR },
            midiChannels = preset.midiChannels.toIntArray(),
            breakConfig = BreakConfig(
                character = try { BreakCharacter.valueOf(preset.breakCharacter) } catch (e: Exception) { BreakCharacter.AUTO },
                bars = preset.breakBars,
                isLive = preset.breakLive
            ),
            music = MusicSettings(
                duration = try { DurationMode.valueOf(preset.durationMode) } catch (e: Exception) { DurationMode.VARIABLE },
                velocity = try { VelocityMode.valueOf(preset.velocityMode) } catch (e: Exception) { VelocityMode.MODULATED },
                meter = try { Meter.valueOf(preset.meter) } catch (e: Exception) { Meter.M44 },
                swing = preset.swing,
                phraseBars = preset.phraseBars,
                rootOctave = preset.rootOctave.coerceIn(1, 6)
            ),
            groovePartner = try { CompositionalLanguage.valueOf(preset.groovePartner) } catch (e: Exception) { CompositionalLanguage.CANON },
            ornaments = preset.ornaments,
            sequence = HarmonicSequence.decode(preset.sequence, preset.sequenceEnabled)
        )
    }

    fun engineStateToPreset(state: EngineState, index: Int, name: String = "Preset $index"): Preset {
        return Preset(
            index = index,
            name = name,
            language = state.language.name,
            bpm = state.bpm,
            rootNote = state.rootNote.name,
            scaleName = state.scale.name,
            material = state.params.material,
            memory = state.params.memory,
            growth = state.params.growth,
            morph = state.params.morph,
            density = state.params.density,
            tension = state.params.tension,
            interaction = state.params.interaction,
            humanize = state.params.humanize,
            midiChannels = state.midiChannels.toList(),
            breakCharacter = state.breakConfig.character.name,
            breakBars = state.breakConfig.bars,
            ornaments = state.ornaments,
            sequence = state.sequence.encode(),
            sequenceEnabled = state.sequence.enabled,
            durationMode = state.music.duration.name,
            velocityMode = state.music.velocity.name,
            meter = state.music.meter.name,
            swing = state.music.swing,
            phraseBars = state.music.phraseBars,
            rootOctave = state.music.rootOctave,
            groovePartner = state.groovePartner.name,
            breakLive = state.breakConfig.isLive
        )
    }

    /**
     * Initialize factory presets
     */
    fun initFactoryPresets() {
        if (prefs.getBoolean("factory_initialized", false)) return

        val factoryPresets = listOf(
            Preset(0, "Fugue Classic", "FUGUE", 120, "C", "MAJOR", 0.5f, 0.6f, 0.5f, 0.3f, 0.6f, 0.4f, 0.5f, 0.1f),
            Preset(1, "Canon Simple", "CANON", 100, "G", "MAJOR", 0.3f, 0.7f, 0.4f, 0.2f, 0.5f, 0.3f, 0.7f, 0.05f),
            Preset(2, "Markov Jazz", "PROBA", 130, "D", "DORIAN", 0.6f, 0.4f, 0.6f, 0.5f, 0.7f, 0.6f, 0.4f, 0.15f),
            Preset(3, "Tintinnabuli Peace", "TINTINNABULI", 72, "A", "MINOR", 0.4f, 0.8f, 0.3f, 0.1f, 0.4f, 0.2f, 0.6f, 0.05f),
            Preset(4, "Minimal Phase", "MINIMAL", 140, "E", "PENTATONIC_MAJOR", 0.5f, 0.9f, 0.3f, 0.4f, 0.5f, 0.3f, 0.5f, 0.0f),
            Preset(5, "Cellular Rule 30", "CELLULAR", 110, "C", "MINOR", 0.12f, 0.5f, 0.5f, 0.3f, 0.6f, 0.5f, 0.3f, 0.1f),
            Preset(6, "L-System Tree", "TREE", 95, "F", "LYDIAN", 0.3f, 0.6f, 0.4f, 0.5f, 0.5f, 0.4f, 0.5f, 0.08f),
            Preset(7, "Fractal Chaos", "FRACTAL", 108, "B", "HARMONIC_MINOR", 0.7f, 0.3f, 0.7f, 0.6f, 0.8f, 0.8f, 0.4f, 0.2f),
            Preset(8, "Motif Weave", "MOTIF", 116, "D", "MIXOLYDIAN", 0.5f, 0.6f, 0.5f, 0.4f, 0.6f, 0.5f, 0.6f, 0.1f),
            Preset(9, "Groove Pulse", "GROOVE", 125, "C", "PENTATONIC_MINOR", 0.4f, 0.5f, 0.6f, 0.3f, 0.7f, 0.5f, 0.5f, 0.15f),
            Preset(10, "Groove Mix Funk", "GROOVE_MIX", 118, "G", "BLUES", 0.6f, 0.4f, 0.5f, 0.5f, 0.8f, 0.6f, 0.4f, 0.2f),
            Preset(11, "Hybrid Textures", "HYBRID", 90, "A", "WHOLE_TONE", 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.1f)
        )

        factoryPresets.forEach { savePreset(it) }
        prefs.edit().putBoolean("factory_initialized", true).apply()
    }
}
