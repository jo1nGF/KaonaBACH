package com.kaona.bach.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import java.util.Random

/**
 * CanonEngine - Implements canonical imitation across multiple voices.
 * A leading voice generates melody and following voices imitate with
 * configurable delay and transformation types.
 */
class CanonEngine : CompositionEngine {

    private data class VoiceState(
        var position: Int = 0,
        var material: MutableList<Int> = mutableListOf(),
        var entryTick: Long = 0L,
        var active: Boolean = false,
        var transposition: Int = 0
    )

    private enum class CanonType {
        STRICT,           // Exact imitation
        TRANSPOSITION,    // Imitation at interval
        RETROGRADE,       // Backwards imitation
        INVERSION,        // Inverted intervals
        AUGMENTATION,     // Doubled note durations
        CONTRARY_MOTION   // Contrary motion
    }

    // Voice ranges
    private val voiceRanges = listOf(
        60..84,  // Soprano
        48..72,  // Alto
        36..60,  // Tenor
        24..48   // Bass
    )

    // Internal state
    private val voices = Array(4) { VoiceState() }
    private var leaderMelody: MutableList<Int> = mutableListOf()
    private var leaderRhythm: MutableList<Int> = mutableListOf()
    private var lastGeneratedTick: Long = -1L
    private var phrasePosition: Int = 0
    private var currentPhraseLength: Int = 16
    private var melodicDirection: Int = 1
    private var lastLeaderDegree: Int = 0
    private var generationCount: Int = 0
    private var initialized: Boolean = false

    override fun generate(tick: Long, state: EngineState, random: Random): List<NoteEvent> {
        val params = state.params
        val interval = getTickInterval(params.density)

        if (tick % interval != 0L) return emptyList()
        if (tick == lastGeneratedTick) return emptyList()
        lastGeneratedTick = tick

        if (!initialized) {
            initialize(tick, state, random)
            initialized = true
        }

        val events = mutableListOf<NoteEvent>()
        val canonType = getCanonType(params.morph)
        val delayTicks = getEntryDelay(params.growth, params.interaction, interval)
        val phraseLen = (8 + params.growth * 24).toInt().coerceIn(8, 32)
        currentPhraseLength = phraseLen

        // --- Generate leader melody (Voice 0) ---
        generateLeaderNote(tick, state, random)

        // --- Process all voices ---
        for (v in 0 until 4) {
            if (state.trackMutes.getOrElse(v) { false }) continue

            val voice = voices[v]
            if (!voice.active) continue
            if (tick < voice.entryTick) continue

            val material = if (v == 0) {
                leaderMelody
            } else {
                getTransformedMaterial(canonType, v, state)
            }

            if (material.isEmpty()) continue

            val pos = voice.position % material.size
            if (pos >= material.size) continue

            val degree = material[pos]
            val range = voiceRanges[v]
            val midiNote = scaleDegreeToMidi(degree, state.rootNote, state.scale, range)

            val baseVelocity = (75 + (params.tension * 30).toInt() - v * 5).coerceIn(40, 120)
            val velocity = RhythmUtils.humanizeVelocity(baseVelocity, params.humanize)

            val baseDuration = when {
                canonType == CanonType.AUGMENTATION && v > 0 -> RhythmUtils.HALF
                params.growth > 0.6f -> RhythmUtils.QUARTER
                else -> RhythmUtils.EIGHTH
            }

            val startTick = RhythmUtils.humanizeTiming(tick, params.humanize)
            val modValue = (64 + (params.morph * 30 - 15)).toInt().coerceIn(0, 127)

            events.add(
                NoteEvent(
                    track = v,
                    midiNote = midiNote.coerceIn(0, 127),
                    velocity = velocity,
                    durationTicks = baseDuration,
                    startTick = startTick,
                    modValue = modValue
                )
            )

            voice.position++

            // Reset phrase if needed
            if (voice.position >= material.size && v == 0) {
                startNewPhrase(tick, state, random)
            }
        }

        generationCount++
        return events
    }

    override fun reset() {
        for (v in voices) {
            v.position = 0
            v.material.clear()
            v.entryTick = 0L
            v.active = false
            v.transposition = 0
        }
        leaderMelody.clear()
        leaderRhythm.clear()
        lastGeneratedTick = -1L
        phrasePosition = 0
        currentPhraseLength = 16
        melodicDirection = 1
        lastLeaderDegree = 0
        generationCount = 0
        initialized = false
    }

    // --- Initialization ---

    private fun initialize(tick: Long, state: EngineState, random: Random) {
        val params = state.params
        val interval = getTickInterval(params.density)
        val delay = getEntryDelay(params.growth, params.interaction, interval)

        for (v in 0 until 4) {
            voices[v].active = true
            voices[v].entryTick = tick + v * delay
            voices[v].position = 0
            voices[v].transposition = getVoiceTransposition(v)
        }

        // Pre-generate initial leader melody
        currentPhraseLength = (8 + params.growth * 24).toInt().coerceIn(8, 32)
        generateInitialMelody(state, random)
    }

    private fun generateInitialMelody(state: EngineState, random: Random) {
        leaderMelody.clear()
        val params = state.params
        val length = currentPhraseLength
        var degree = 0
        val scaleSize = state.scale.degrees.size

        for (i in 0 until length) {
            leaderMelody.add(degree)

            val stepProb = 0.7f - params.tension * 0.3f
            val step = if (random.nextFloat() < stepProb) {
                // Stepwise
                melodicDirection * (if (random.nextFloat() < 0.7f) 1 else -1)
            } else {
                // Skip
                val skip = random.nextInt(2) + 2 * melodicDirection
                skip
            }

            degree += step

            // Keep within reasonable range
            if (degree > scaleSize * 2) {
                melodicDirection = -1
                degree = scaleSize * 2 - 1
            } else if (degree < -scaleSize) {
                melodicDirection = 1
                degree = -scaleSize + 1
            }

            // Occasionally change direction based on material
            if (random.nextFloat() < params.material * 0.3f) {
                melodicDirection *= -1
            }
        }

        lastLeaderDegree = degree
    }

    private fun generateLeaderNote(tick: Long, state: EngineState, random: Random) {
        // Extend leader melody if followers need more material
        val maxFollowerNeed = voices.maxOfOrNull {
            if (it.active && it != voices[0]) it.position + currentPhraseLength else 0
        } ?: 0

        while (leaderMelody.size < maxFollowerNeed + currentPhraseLength) {
            val params = state.params
            val stepProb = 0.7f - params.tension * 0.3f
            val step = if (random.nextFloat() < stepProb) {
                melodicDirection
            } else {
                random.nextInt(2) + 2 * melodicDirection
            }

            lastLeaderDegree += step
            val scaleSize = state.scale.degrees.size

            if (lastLeaderDegree > scaleSize * 2) {
                melodicDirection = -1
                lastLeaderDegree = scaleSize * 2 - 1
            } else if (lastLeaderDegree < -scaleSize) {
                melodicDirection = 1
                lastLeaderDegree = -scaleSize + 1
            }

            if (random.nextFloat() < params.material * 0.3f) {
                melodicDirection *= -1
            }

            leaderMelody.add(lastLeaderDegree)
        }
    }

    // --- Transformation functions ---

    private fun getTransformedMaterial(type: CanonType, voice: Int, state: EngineState): List<Int> {
        if (leaderMelody.isEmpty()) return emptyList()
        val transposition = voices[voice].transposition

        val memoryLen = (leaderMelody.size * state.params.memory.coerceIn(0.2f, 1.0f)).toInt()
            .coerceAtLeast(4)
        val source = leaderMelody.take(memoryLen)

        return when (type) {
            CanonType.STRICT -> source.map { it + transposition }

            CanonType.TRANSPOSITION -> {
                val interval = (voice * 3) + 1
                source.map { it + interval }
            }

            CanonType.RETROGRADE -> source.reversed().map { it + transposition }

            CanonType.INVERSION -> {
                if (source.isEmpty()) return emptyList()
                val pivot = source[0]
                source.map { pivot - (it - pivot) + transposition }
            }

            CanonType.AUGMENTATION -> {
                // Each note appears twice (rhythmic augmentation in pitch domain)
                source.flatMap { listOf(it + transposition, it + transposition) }
            }

            CanonType.CONTRARY_MOTION -> {
                if (source.isEmpty()) return emptyList()
                val intervals = source.zipWithNext { a, b -> b - a }
                val result = mutableListOf(source[0] + transposition)
                for (interval in intervals) {
                    result.add(result.last() - interval)
                }
                result
            }
        }
    }

    private fun startNewPhrase(tick: Long, state: EngineState, random: Random) {
        // Memory controls how much of the old melody persists
        val keepLen = (leaderMelody.size * state.params.memory).toInt()
        val kept = if (keepLen > 0) leaderMelody.takeLast(keepLen) else emptyList()

        leaderMelody.clear()
        leaderMelody.addAll(kept)

        voices[0].position = 0
        generateInitialMelody(state, random)
    }

    // --- Utility ---

    private fun getCanonType(morph: Float): CanonType {
        return when {
            morph < 0.167f -> CanonType.STRICT
            morph < 0.333f -> CanonType.TRANSPOSITION
            morph < 0.5f -> CanonType.RETROGRADE
            morph < 0.667f -> CanonType.INVERSION
            morph < 0.833f -> CanonType.AUGMENTATION
            else -> CanonType.CONTRARY_MOTION
        }
    }

    private fun getEntryDelay(growth: Float, interaction: Float, interval: Int): Long {
        // Higher interaction = closer entries, higher growth = longer delay
        val beats = (2 + growth * 6 - interaction * 3).toInt().coerceIn(1, 8)
        return (beats * interval).toLong()
    }

    private fun getVoiceTransposition(voice: Int): Int {
        return when (voice) {
            0 -> 0
            1 -> -7  // Down a 5th (in scale degrees ~4 down)
            2 -> -12 // Down an octave
            3 -> -19 // Down an octave and 5th
        }
    }

    private fun getTickInterval(density: Float): Int {
        return when {
            density > 0.7f -> RhythmUtils.EIGHTH
            density > 0.4f -> RhythmUtils.QUARTER
            else -> RhythmUtils.HALF
        }
    }

    private fun scaleDegreeToMidi(
        degree: Int,
        rootNote: NoteName,
        scale: Scale,
        range: IntRange
    ): Int {
        val degrees = scale.degrees
        if (degrees.isEmpty()) return range.first

        val octaveSize = degrees.size
        val octave = if (degree >= 0) degree / octaveSize else (degree - octaveSize + 1) / octaveSize
        val degreeInScale = ((degree % octaveSize) + octaveSize) % octaveSize
        val semitoneOffset = degrees[degreeInScale]
        val baseMidi = rootNote.midiValue + semitoneOffset + (octave * 12)

        var midiNote = baseMidi
        while (midiNote < range.first) midiNote += 12
        while (midiNote > range.last) midiNote -= 12

        return midiNote.coerceIn(range.first, range.last)
    }
}
