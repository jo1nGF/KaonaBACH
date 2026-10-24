package com.kaona.bach.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import java.util.Random

/**
 * FugueEngine - Implements classical fugue structure with subject, answer,
 * counter-subject, and episodic development across 4 voices.
 */
class FugueEngine : CompositionEngine {

    // Voice register ranges (MIDI note numbers)
    private data class VoiceRange(val low: Int, val high: Int)

    private val voiceRanges = listOf(
        VoiceRange(60, 84), // Soprano
        VoiceRange(48, 72), // Alto
        VoiceRange(36, 60), // Tenor
        VoiceRange(24, 48)  // Bass
    )

    // Internal state
    private var subject: List<Int> = emptyList()          // Subject as scale degrees
    private var answer: List<Int> = emptyList()            // Answer (tonal, at dominant)
    private var counterSubject: List<Int> = emptyList()    // Contrary motion to subject
    private var currentSection: FugueSection = FugueSection.EXPOSITION
    private var sectionStartTick: Long = 0L
    private var voiceEntryIndex: Int = 0
    private var voicePositions: IntArray = IntArray(4) { 0 } // Current position in assigned material
    private var voiceMaterial: Array<List<Int>> = Array(4) { emptyList() }
    private var voiceActive: BooleanArray = BooleanArray(4) { false }
    private var voiceEntryTicks: LongArray = LongArray(4) { -1L }
    private var lastGeneratedTick: Long = -1L
    private var episodeCount: Int = 0
    private var subjectVariation: Int = 0
    private var generationCount: Int = 0

    private enum class FugueSection {
        EXPOSITION,
        EPISODE,
        DEVELOPMENT,
        STRETTO,
        CODA
    }

    override fun generate(tick: Long, state: EngineState, random: Random): List<NoteEvent> {
        val params = state.params
        val interval = getTickInterval(params.density)

        if (tick % interval != 0L) return emptyList()
        if (tick == lastGeneratedTick) return emptyList()
        lastGeneratedTick = tick

        // Generate subject on first call or when material changes significantly
        if (subject.isEmpty()) {
            generateSubject(state, random)
            generateAnswer(state)
            generateCounterSubject(state)
            initializeExposition(tick, state, random)
        }

        // Check for section transitions
        updateSection(tick, state, random)

        // Apply memory-based variation
        if (params.memory < 0.3f && generationCount % 32 == 0 && generationCount > 0) {
            varySubject(state, random)
        }

        generationCount++

        val events = mutableListOf<NoteEvent>()
        val activeVoiceCount = getActiveVoiceCount(params.density)
        val scale = state.scale
        val rootNote = state.rootNote

        for (voice in 0 until 4) {
            if (!voiceActive[voice]) continue
            if (voice >= activeVoiceCount) continue
            if (state.trackMutes.getOrElse(voice) { false }) continue
            if (voiceEntryTicks[voice] < 0 || tick < voiceEntryTicks[voice]) continue

            val material = voiceMaterial[voice]
            if (material.isEmpty()) continue

            val pos = voicePositions[voice] % material.size
            val scaleDegree = material[pos]
            val range = voiceRanges[voice]

            // Convert scale degree to MIDI note within voice range
            val midiNote = scaleDegreeToMidi(scaleDegree, rootNote, scale, range)

            // Apply tension to velocity and intervals
            val baseVelocity = when (currentSection) {
                FugueSection.EXPOSITION -> 80
                FugueSection.EPISODE -> 65
                FugueSection.DEVELOPMENT -> 90
                FugueSection.STRETTO -> 95
                FugueSection.CODA -> 75
            }
            val velocity = RhythmUtils.humanizeVelocity(
                (baseVelocity + (params.tension * 20).toInt()).coerceIn(40, 120),
                params.humanize
            )

            // Duration based on section and growth
            val baseDuration = when {
                params.growth > 0.7f -> RhythmUtils.HALF
                params.growth > 0.4f -> RhythmUtils.QUARTER
                else -> RhythmUtils.EIGHTH
            }
            val duration = if (currentSection == FugueSection.STRETTO) {
                (baseDuration * 0.75).toInt().coerceAtLeast(RhythmUtils.SIXTEENTH)
            } else {
                baseDuration
            }

            val startTick = RhythmUtils.humanizeTiming(tick, params.humanize)
            val modValue = (64 + (params.morph * 40 - 20)).toInt().coerceIn(0, 127)

            events.add(
                NoteEvent(
                    track = voice,
                    midiNote = midiNote.coerceIn(0, 127),
                    velocity = velocity,
                    durationTicks = duration,
                    startTick = startTick,
                    modValue = modValue
                )
            )

            voicePositions[voice] = pos + 1
        }

        return events
    }

    override fun reset() {
        subject = emptyList()
        answer = emptyList()
        counterSubject = emptyList()
        currentSection = FugueSection.EXPOSITION
        sectionStartTick = 0L
        voiceEntryIndex = 0
        voicePositions = IntArray(4) { 0 }
        voiceMaterial = Array(4) { emptyList() }
        voiceActive = BooleanArray(4) { false }
        voiceEntryTicks = LongArray(4) { -1L }
        lastGeneratedTick = -1L
        episodeCount = 0
        subjectVariation = 0
        generationCount = 0
    }

    // --- Subject generation ---

    // Famílias de sujeito (graus da escala): contornos originais pensados para uma resposta tonal
    private val subjectFamilies: List<IntArray> = listOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 4, 3, 2, 1, 0, 0),        // ARCH: sobe a um pico central e volta
        intArrayOf(0, 4, 0, 7, 4, 0, 4, 7, 4, 0, -3, 0),       // FIFTHS: dominante, oitava e retornos da quinta
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 6, 5, 4, 3, 2, 1, 0, 0), // SCALAR: sobe e desce por graus conjuntos
        intArrayOf(0, 2, 1, 3, 2, 4, 3, 5, 4, 2, 1, 0),        // SYNCOPATED: zigue-zague que desloca os acentos
        intArrayOf(0, 1, 0, -1, 0, 2, 1, 0, -1, 0, 1, 0),      // TURN: grupeto em torno da tônica
        intArrayOf(0, 2, 0, 3, 0, 4, 0, 2, 0, 1, 0, 0),        // PEDAL: a tônica volta como pivô
        intArrayOf(0, 1, -1, 2, -2, 3, -3, 4, -4, 3, -3, 0),   // WEDGE: intervalos abrem acima e abaixo
        intArrayOf(0, 0, 2, 2, 4, 4, 2, 2, 3, 1, 0, 0)         // CHORALE: contorno de acordes
    )

    private fun generateSubject(state: EngineState, random: Random) {
        val params = state.params
        val family = MaterialCatalog.index(CompositionalLanguage.FUGUE, params.material)
            .coerceIn(0, subjectFamilies.size - 1)
        val template = subjectFamilies[family]
        val scaleSize = state.scale.degrees.size

        // TENSION ainda desloca alguns graus do contorno
        val degrees = template.map { d ->
            val varied = if (random.nextFloat() < params.tension * 0.25f) {
                d + (if (random.nextBoolean()) 1 else -1)
            } else {
                d
            }
            varied.coerceIn(-scaleSize, scaleSize * 2 - 1)
        }

        subject = degrees.toMutableList()
    }

    private fun generateAnswer(state: EngineState) {
        // Tonal answer: transpose subject up a 5th (4 scale degrees up)
        answer = subject.map { it + 4 }
    }

    private fun generateCounterSubject(state: EngineState) {
        // Contrary motion: invert the intervals of the subject
        if (subject.isEmpty()) return
        val intervals = subject.zipWithNext { a, b -> b - a }
        val cs = mutableListOf(subject.last()) // Start where subject ends approximately
        for (interval in intervals) {
            cs.add(cs.last() - interval) // Contrary motion: negate intervals
        }
        counterSubject = cs
    }

    private fun varySubject(state: EngineState, random: Random) {
        subjectVariation++
        val morph = state.params.morph

        subject = when {
            morph < 0.25f -> subject // No transformation
            morph < 0.5f -> invertSubject(subject)
            morph < 0.75f -> retrogradeSubject(subject)
            else -> augmentSubject(subject)
        }

        generateAnswer(state)
        generateCounterSubject(state)
    }

    private fun invertSubject(s: List<Int>): List<Int> {
        if (s.isEmpty()) return s
        val pivot = s[0]
        return s.map { pivot - (it - pivot) }
    }

    private fun retrogradeSubject(s: List<Int>): List<Int> = s.reversed()

    private fun augmentSubject(s: List<Int>): List<Int> {
        // Augmentation: double each note (repeat)
        return s.flatMap { listOf(it, it) }
    }

    // --- Section management ---

    private fun initializeExposition(tick: Long, state: EngineState, random: Random) {
        currentSection = FugueSection.EXPOSITION
        sectionStartTick = tick

        val params = state.params
        val entryDelay = (subject.size * getTickInterval(params.density)).toLong()
        val interactionDelay = (entryDelay * (0.5f + params.interaction * 0.5f)).toLong()

        // Stagger voice entries: subject, answer, countersubject+subject, countersubject+answer
        voiceActive[0] = true
        voiceEntryTicks[0] = tick
        voiceMaterial[0] = subject

        voiceActive[1] = true
        voiceEntryTicks[1] = tick + interactionDelay
        voiceMaterial[1] = answer

        voiceActive[2] = true
        voiceEntryTicks[2] = tick + interactionDelay * 2
        voiceMaterial[2] = subject

        voiceActive[3] = true
        voiceEntryTicks[3] = tick + interactionDelay * 3
        voiceMaterial[3] = answer

        voicePositions = IntArray(4) { 0 }
    }

    private fun updateSection(tick: Long, state: EngineState, random: Random) {
        val params = state.params
        val sectionLength = (subject.size * getTickInterval(params.density) *
                (2 + params.growth * 4).toInt()).toLong()
        val elapsed = tick - sectionStartTick

        if (elapsed < sectionLength) return

        // Transition to next section
        when (currentSection) {
            FugueSection.EXPOSITION -> {
                currentSection = FugueSection.EPISODE
                setupEpisode(tick, state, random)
            }
            FugueSection.EPISODE -> {
                episodeCount++
                if (episodeCount >= 2) {
                    currentSection = FugueSection.DEVELOPMENT
                    setupDevelopment(tick, state, random)
                } else {
                    // Another exposition with varied subject
                    currentSection = FugueSection.EXPOSITION
                    varySubject(state, random)
                    initializeExposition(tick, state, random)
                }
            }
            FugueSection.DEVELOPMENT -> {
                currentSection = FugueSection.STRETTO
                setupStretto(tick, state, random)
            }
            FugueSection.STRETTO -> {
                currentSection = FugueSection.CODA
                setupCoda(tick, state, random)
            }
            FugueSection.CODA -> {
                // Restart the cycle
                currentSection = FugueSection.EXPOSITION
                episodeCount = 0
                subjectVariation = 0
                generateSubject(state, random)
                generateAnswer(state)
                generateCounterSubject(state)
                initializeExposition(tick, state, random)
            }
        }
        sectionStartTick = tick
    }

    private fun setupEpisode(tick: Long, state: EngineState, random: Random) {
        // Free counterpoint: generate sequences derived from subject fragments
        val fragmentLen = max(3, subject.size / 2)
        val fragment = subject.take(fragmentLen)

        for (v in 0 until 4) {
            voicePositions[v] = 0
            val transposition = (v - 1) * 3
            voiceMaterial[v] = generateEpisodicSequence(fragment, transposition, state, random)
            voiceEntryTicks[v] = tick + (v * RhythmUtils.QUARTER).toLong()
        }
    }

    private fun generateEpisodicSequence(
        fragment: List<Int>,
        transposition: Int,
        state: EngineState,
        random: Random
    ): List<Int> {
        val length = (8 + state.params.growth * 16).toInt()
        val seq = mutableListOf<Int>()
        var seqIndex = 0

        for (i in 0 until length) {
            val base = fragment[seqIndex % fragment.size]
            val variation = if (random.nextFloat() < state.params.tension) {
                random.nextInt(7) - 3
            } else {
                0
            }
            seq.add(base + transposition + variation)
            seqIndex++

            // Sequence: repeat fragment at different transpositions
            if (seqIndex >= fragment.size) {
                seqIndex = 0
            }
        }
        return seq
    }

    private fun setupDevelopment(tick: Long, state: EngineState, random: Random) {
        val morph = state.params.morph
        for (v in 0 until 4) {
            voicePositions[v] = 0
            voiceEntryTicks[v] = tick

            voiceMaterial[v] = when {
                morph < 0.33f -> invertSubject(if (v % 2 == 0) subject else answer).map { it + v * 2 }
                morph < 0.66f -> retrogradeSubject(if (v % 2 == 0) subject else answer).map { it + v * 2 }
                else -> {
                    // Combine transformations
                    val base = if (v % 2 == 0) subject else answer
                    invertSubject(retrogradeSubject(base)).map { it + v * 2 }
                }
            }
        }
    }

    private fun setupStretto(tick: Long, state: EngineState, random: Random) {
        // Stretto: voices enter closer together
        val closeness = (1 + (1.0f - state.params.interaction) * 3).toInt()
        val entryGap = max(1, subject.size / closeness).toLong() * getTickInterval(state.params.density).toLong()

        for (v in 0 until 4) {
            voicePositions[v] = 0
            voiceEntryTicks[v] = tick + v * entryGap
            voiceMaterial[v] = subject.map { it + (v - 1) * 4 }
        }
    }

    private fun setupCoda(tick: Long, state: EngineState, random: Random) {
        // Coda: pedal point on tonic, subject fragments
        for (v in 0 until 4) {
            voicePositions[v] = 0
            voiceEntryTicks[v] = tick

            voiceMaterial[v] = if (v == 3) {
                // Bass holds pedal point
                List(16) { 0 }
            } else {
                subject.take(max(4, subject.size / 2)).map { it + v * 3 }
            }
        }
    }

    // --- Utility functions ---

    private fun getTickInterval(density: Float): Int {
        return when {
            density > 0.7f -> RhythmUtils.EIGHTH
            density > 0.4f -> RhythmUtils.QUARTER
            else -> RhythmUtils.HALF
        }
    }

    private fun getActiveVoiceCount(density: Float): Int {
        return when {
            density > 0.75f -> 4
            density > 0.5f -> 3
            density > 0.25f -> 2
            else -> 1
        }
    }

    private fun scaleDegreeToMidi(
        degree: Int,
        rootNote: NoteName,
        scale: Scale,
        range: VoiceRange
    ): Int {
        val degrees = scale.degrees
        if (degrees.isEmpty()) return range.low

        val octaveSize = degrees.size
        val octave = if (degree >= 0) degree / octaveSize else (degree - octaveSize + 1) / octaveSize
        val degreeInScale = ((degree % octaveSize) + octaveSize) % octaveSize
        val semitoneOffset = degrees[degreeInScale]
        val baseMidi = rootNote.midiValue + semitoneOffset + (octave * 12)

        // Clamp to voice range, adjust octave if needed
        var midiNote = baseMidi
        while (midiNote < range.low) midiNote += 12
        while (midiNote > range.high) midiNote -= 12

        return midiNote.coerceIn(range.low, range.high)
    }
}
