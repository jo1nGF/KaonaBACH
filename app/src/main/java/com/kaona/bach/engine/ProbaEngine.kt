package com.kaona.bach.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import java.util.Random

/**
 * ProbaEngine - Implements Markov chain based probabilistic composition.
 * Uses transition matrices of configurable order to generate melodies
 * with statistical properties derived from vocabulary sets.
 */
class ProbaEngine : CompositionEngine {

    /**
     * Vocabulary set defining a pitch collection and transition biases.
     */
    private data class Vocabulary(
        val name: String,
        val intervals: List<Int>, // Available intervals in semitones from root
        val biases: List<Float>   // Bias weights for each interval
    )

    /**
     * Markov chain state for a single voice.
     */
    private class MarkovChain(val order: Int) {
        val history: MutableList<Int> = mutableListOf()
        val transitionCounts: MutableMap<List<Int>, MutableMap<Int, Int>> = mutableMapOf()

        fun record(state: Int) {
            history.add(state)
            if (history.size > order) {
                val context = history.takeLast(order + 1).dropLast(1)
                val target = history.last()
                val map = transitionCounts.getOrPut(context) { mutableMapOf() }
                map[target] = (map[target] ?: 0) + 1
            }
        }

        fun predict(random: Random, tension: Float): Int? {
            if (history.size < order) return null
            val context = history.takeLast(order)
            val transitions = transitionCounts[context] ?: return null
            if (transitions.isEmpty()) return null

            val totalWeight = transitions.values.sum().toFloat()
            if (totalWeight <= 0f) return null

            // Apply tension: higher tension favors less probable transitions
            val entries = transitions.entries.toList()
            val probabilities = entries.map { (_, count) ->
                val p = count / totalWeight
                // Invert probabilities based on tension
                val adjusted = p * (1f - tension) + (1f - p) * tension
                adjusted.coerceAtLeast(0.01f)
            }

            val totalAdjusted = probabilities.sum()
            var roll = random.nextFloat() * totalAdjusted
            for (i in entries.indices) {
                roll -= probabilities[i]
                if (roll <= 0f) return entries[i].key
            }
            return entries.last().key
        }

        fun reset() {
            history.clear()
            transitionCounts.clear()
        }
    }

    // Vocabulary sets
    private val vocabularies = listOf(
        Vocabulary("pentatonic", listOf(0, 2, 4, 7, 9), listOf(1.5f, 1f, 1.2f, 1.3f, 1f)),
        Vocabulary("modal_dorian", listOf(0, 2, 3, 5, 7, 9, 10), listOf(1.5f, 1f, 1.2f, 1f, 1.3f, 1f, 0.8f)),
        Vocabulary("modal_mixolydian", listOf(0, 2, 4, 5, 7, 9, 10), listOf(1.5f, 1f, 1.2f, 1f, 1.3f, 1f, 0.8f)),
        Vocabulary("blues", listOf(0, 3, 5, 6, 7, 10), listOf(1.5f, 1.2f, 1f, 0.8f, 1.3f, 1f)),
        Vocabulary("chromatic", listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), listOf(1f, 0.5f, 0.8f, 0.7f, 0.9f, 0.8f, 0.4f, 1.1f, 0.6f, 0.9f, 0.7f, 0.5f)),
        Vocabulary("whole_tone", listOf(0, 2, 4, 6, 8, 10), listOf(1.2f, 1f, 1.1f, 0.9f, 1f, 0.8f)),
        Vocabulary("japanese", listOf(0, 1, 5, 7, 8), listOf(1.5f, 1f, 1.2f, 1.3f, 1f)),
        Vocabulary("harmonic_minor", listOf(0, 2, 3, 5, 7, 8, 11), listOf(1.5f, 1f, 1.2f, 1f, 1.3f, 0.8f, 1.1f))
    )

    // Voice state
    private val chains = Array(4) { MarkovChain(1) }
    private var lastGeneratedTick: Long = -1L
    private var currentVocabIndex: Int = 0
    private var phrasePositions: IntArray = IntArray(4) { 0 }
    private var phraseLengths: IntArray = IntArray(4) { 16 }
    private var initialized: Boolean = false
    private var lastNotes: IntArray = IntArray(4) { 60 }
    private var generationCount: Int = 0

    override fun generate(tick: Long, state: EngineState, random: Random): List<NoteEvent> {
        val params = state.params
        val interval = getTickInterval(params.density)

        if (tick % interval != 0L) return emptyList()
        if (tick == lastGeneratedTick) return emptyList()
        lastGeneratedTick = tick

        if (!initialized) {
            initialize(state, random)
            initialized = true
        }

        val events = mutableListOf<NoteEvent>()
        val vocab = getVocabulary(params.material)
        val chainOrder = getChainOrder(params.memory)
        val linked = params.interaction > 0.5f

        // Update chain order if changed
        for (v in 0 until 4) {
            if (chains[v].order != chainOrder) {
                val oldHistory = chains[v].history.toList()
                chains[v] = MarkovChain(chainOrder)
                // Reseed with old history
                for (note in oldHistory.takeLast(chainOrder * 4)) {
                    chains[v].record(note)
                }
            }
        }

        for (v in 0 until 4) {
            if (state.trackMutes.getOrElse(v) { false }) continue

            val chain = chains[v]
            val phraseLen = (8 + params.growth * 24).toInt().coerceIn(8, 32)
            phraseLengths[v] = phraseLen

            // Determine next note
            var nextNoteIndex: Int? = chain.predict(random, params.tension)

            if (nextNoteIndex == null) {
                // Fallback: generate from vocabulary with bias
                nextNoteIndex = selectFromVocabulary(vocab, random, params.tension)
            }

            // If linked, influence from other voices
            if (linked && v > 0) {
                val leaderNote = lastNotes[0]
                val vocabSize = vocab.intervals.size
                val leaderIndex = findClosestVocabIndex(leaderNote, vocab, state.rootNote)
                // Bias toward complementary intervals
                val offset = when (v) {
                    1 -> 2  // Third above leader
                    2 -> 4  // Fifth above leader
                    3 -> -3 // Third below leader
                    else -> 0
                }
                nextNoteIndex = ((leaderIndex + offset) % vocabSize + vocabSize) % vocabSize

                // Add some randomness based on interaction
                if (random.nextFloat() > params.interaction) {
                    nextNoteIndex = selectFromVocabulary(vocab, random, params.tension)
                }
            }

            // Convert vocabulary index to MIDI note
            val semitoneOffset = vocab.intervals[nextNoteIndex.coerceIn(0, vocab.intervals.size - 1)]
            val baseOctave = getVoiceBaseOctave(v)
            val midiNote = quantizeToScale(
                state.rootNote.midiValue + baseOctave * 12 + semitoneOffset,
                state.rootNote,
                state.scale
            )

            // Record in chain
            chain.record(nextNoteIndex)
            lastNotes[v] = midiNote

            // Velocity and dynamics
            val baseVelocity = (70 + (params.tension * 25).toInt()).coerceIn(40, 115)
            val velocity = RhythmUtils.humanizeVelocity(baseVelocity - v * 5, params.humanize)

            val duration = when {
                params.growth > 0.7f -> RhythmUtils.DOTTED_QUARTER
                params.growth > 0.4f -> RhythmUtils.QUARTER
                else -> RhythmUtils.EIGHTH
            }

            val startTick = RhythmUtils.humanizeTiming(tick, params.humanize)
            val modValue = (32 + (params.tension * 64)).toInt().coerceIn(0, 127)

            events.add(
                NoteEvent(
                    track = v,
                    midiNote = midiNote.coerceIn(0, 127),
                    velocity = velocity,
                    durationTicks = duration,
                    startTick = startTick,
                    modValue = modValue
                )
            )

            phrasePositions[v]++

            // Phrase boundary: adjust chain
            if (phrasePositions[v] >= phraseLen) {
                phrasePositions[v] = 0
                // Partially reset chain based on memory
                if (params.memory < 0.5f) {
                    val keepLen = (chain.history.size * params.memory).toInt()
                    val kept = chain.history.takeLast(keepLen)
                    chain.history.clear()
                    chain.history.addAll(kept)
                }
            }
        }

        generationCount++
        return events
    }

    override fun reset() {
        for (chain in chains) chain.reset()
        lastGeneratedTick = -1L
        currentVocabIndex = 0
        phrasePositions = IntArray(4) { 0 }
        phraseLengths = IntArray(4) { 16 }
        initialized = false
        lastNotes = IntArray(4) { 60 }
        generationCount = 0
    }

    // --- Initialization ---

    private fun initialize(state: EngineState, random: Random) {
        val params = state.params
        val vocab = getVocabulary(params.material)
        val chainOrder = getChainOrder(params.memory)

        for (v in 0 until 4) {
            chains[v] = MarkovChain(chainOrder)
            // Seed chains with initial training data
            seedChain(chains[v], vocab, random, params.tension)
        }
    }

    private fun seedChain(chain: MarkovChain, vocab: Vocabulary, random: Random, tension: Float) {
        // Generate training sequence from vocabulary biases
        val seedLength = 32 + chain.order * 8
        for (i in 0 until seedLength) {
            val idx = selectFromVocabulary(vocab, random, tension * 0.3f)
            chain.record(idx)
        }
    }

    // --- Vocabulary and note selection ---

    private fun getVocabulary(material: Float): Vocabulary {
        val index = (material * (vocabularies.size - 1)).toInt().coerceIn(0, vocabularies.size - 1)
        return vocabularies[index]
    }

    private fun selectFromVocabulary(vocab: Vocabulary, random: Random, tension: Float): Int {
        val weights = vocab.biases.mapIndexed { i, bias ->
            // Higher tension = more uniform distribution (more surprises)
            val adjusted = bias * (1f - tension * 0.6f) + tension * 0.4f
            adjusted.coerceAtLeast(0.01f)
        }

        val totalWeight = weights.sum()
        var roll = random.nextFloat() * totalWeight
        for (i in weights.indices) {
            roll -= weights[i]
            if (roll <= 0f) return i
        }
        return weights.size - 1
    }

    private fun findClosestVocabIndex(midiNote: Int, vocab: Vocabulary, rootNote: NoteName): Int {
        val noteInOctave = (midiNote - rootNote.midiValue + 120) % 12
        var closest = 0
        var minDist = Int.MAX_VALUE
        for (i in vocab.intervals.indices) {
            val dist = abs(vocab.intervals[i] - noteInOctave)
            if (dist < minDist) {
                minDist = dist
                closest = i
            }
        }
        return closest
    }

    private fun getChainOrder(memory: Float): Int {
        return when {
            memory > 0.7f -> 3
            memory > 0.4f -> 2
            else -> 1
        }
    }

    private fun getVoiceBaseOctave(voice: Int): Int {
        return when (voice) {
            0 -> 5  // Soprano ~C5
            1 -> 4  // Alto ~C4
            2 -> 3  // Tenor ~C3
            3 -> 2  // Bass ~C2
            else -> 4
        }
    }

    // --- Utility ---

    private fun quantizeToScale(midiNote: Int, rootNote: NoteName, scale: Scale): Int {
        val degrees = scale.degrees
        if (degrees.isEmpty()) return midiNote

        val noteInOctave = (midiNote - rootNote.midiValue + 120) % 12
        val octave = (midiNote - rootNote.midiValue) / 12

        // Find closest scale degree
        var closest = degrees[0]
        var minDist = Int.MAX_VALUE
        for (d in degrees) {
            val dist = abs(d - noteInOctave)
            if (dist < minDist) {
                minDist = dist
                closest = d
            }
        }

        return rootNote.midiValue + octave * 12 + closest
    }

    private fun getTickInterval(density: Float): Int {
        return when {
            density > 0.8f -> RhythmUtils.SIXTEENTH
            density > 0.6f -> RhythmUtils.EIGHTH
            density > 0.3f -> RhythmUtils.QUARTER
            else -> RhythmUtils.HALF
        }
    }
}
