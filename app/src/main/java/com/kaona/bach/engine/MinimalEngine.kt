package com.kaona.bach.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import java.util.Random

/**
 * MinimalEngine - Implements minimalist music processes including
 * phase shifting (Steve Reich), additive/subtractive processes,
 * substitution, and hocketing.
 */
class MinimalEngine : CompositionEngine {

    /**
     * The type of minimalist process active.
     */
    private enum class MinimalProcess {
        PHASE_SHIFTING,      // Two identical patterns gradually shift out of phase
        ADDITION,            // Notes added one at a time to build up a pattern
        SUBTRACTION,         // Notes removed one at a time from a full pattern
        SUBSTITUTION,        // Rests gradually substituted for notes (or vice versa)
        HOCKET              // Notes split between voices in interlocking fashion
    }

    // Base patterns for different material settings
    private val basePatterns = listOf(
        // Pattern 0: Simple pentatonic riff
        intArrayOf(0, 2, 4, 7, 4, 2),
        // Pattern 1: Modal pattern
        intArrayOf(0, 1, 3, 5, 3, 1, 0, -1),
        // Pattern 2: Rhythmic cluster
        intArrayOf(0, 0, 4, 7, 0, 4, 7, 9),
        // Pattern 3: Wider intervals
        intArrayOf(0, 4, 7, 11, 7, 4, 0, -3),
        // Pattern 4: Repetitive pulse
        intArrayOf(0, 2, 0, 4, 0, 2, 0, 5),
        // Pattern 5: African-inspired
        intArrayOf(0, 3, 5, 0, 3, 7, 5, 3),
        // Pattern 6: Ascending spiral
        intArrayOf(0, 1, 2, 0, 1, 2, 3, 1, 2, 3, 4, 2),
        // Pattern 7: Long pattern
        intArrayOf(0, 2, 4, 5, 4, 2, 0, -1, 0, 2, 4, 7, 4, 2, 0, -2)
    )

    // Internal state
    private var currentPattern: IntArray = intArrayOf()
    private var voicePhases: FloatArray = FloatArray(4) { 0f }  // Phase offset per voice (in fractional steps)
    private var voicePositions: IntArray = IntArray(4) { 0 }
    private var voiceActiveNotes: Array<BooleanArray> = Array(4) { BooleanArray(16) { true } }
    private var processStep: Int = 0             // Current step within the process
    private var processTickCounter: Int = 0      // Ticks since last process step
    private var lastGeneratedTick: Long = -1L
    private var generationCount: Int = 0
    private var initialized: Boolean = false
    private var additionIndex: Int = 0           // For addition process
    private var subtractionIndex: Int = 0        // For subtraction process
    private var substitutionMap: BooleanArray = BooleanArray(16) { true }
    private var hocketAssignment: Array<IntArray> = Array(4) { intArrayOf() }

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

        val process = getProcess(params.morph)
        val events = mutableListOf<NoteEvent>()

        // Advance process at rate controlled by growth
        processTickCounter++
        val processRate = (4 + (1f - params.growth) * 28).toInt().coerceIn(4, 32)
        if (processTickCounter >= processRate) {
            processTickCounter = 0
            advanceProcess(process, state, random)
        }

        // Generate notes based on current process state
        when (process) {
            MinimalProcess.PHASE_SHIFTING -> {
                events.addAll(generatePhaseShifting(tick, state, random))
            }
            MinimalProcess.ADDITION -> {
                events.addAll(generateAddition(tick, state, random))
            }
            MinimalProcess.SUBTRACTION -> {
                events.addAll(generateSubtraction(tick, state, random))
            }
            MinimalProcess.SUBSTITUTION -> {
                events.addAll(generateSubstitution(tick, state, random))
            }
            MinimalProcess.HOCKET -> {
                events.addAll(generateHocket(tick, state, random))
            }
        }

        generationCount++
        return events
    }

    override fun reset() {
        currentPattern = intArrayOf()
        voicePhases = FloatArray(4) { 0f }
        voicePositions = IntArray(4) { 0 }
        voiceActiveNotes = Array(4) { BooleanArray(16) { true } }
        processStep = 0
        processTickCounter = 0
        lastGeneratedTick = -1L
        generationCount = 0
        initialized = false
        additionIndex = 0
        subtractionIndex = 0
        substitutionMap = BooleanArray(16) { true }
        hocketAssignment = Array(4) { intArrayOf() }
    }

    // --- Initialization ---

    private fun initialize(state: EngineState, random: Random) {
        val patternIndex = (state.params.material * (basePatterns.size - 1)).toInt()
            .coerceIn(0, basePatterns.size - 1)
        currentPattern = basePatterns[patternIndex].copyOf()

        // Initialize voice phases
        for (v in 0 until 4) {
            voicePhases[v] = 0f
            voicePositions[v] = 0
        }

        // Initialize active notes
        voiceActiveNotes = Array(4) { BooleanArray(currentPattern.size) { true } }

        // Initialize substitution map
        substitutionMap = BooleanArray(currentPattern.size) { true }

        // Initialize hocket assignments
        setupHocket(state)

        // Reset process counters
        additionIndex = 0
        subtractionIndex = currentPattern.size - 1
        processStep = 0
    }

    private fun setupHocket(state: EngineState) {
        // Distribute pattern positions among voices
        val patLen = currentPattern.size
        hocketAssignment = Array(4) { v ->
            IntArray(patLen) { pos ->
                if (pos % 4 == v) 1 else 0
            }
        }
    }

    // --- Process selection ---

    private fun getProcess(morph: Float): MinimalProcess {
        return when {
            morph < 0.2f -> MinimalProcess.PHASE_SHIFTING
            morph < 0.4f -> MinimalProcess.ADDITION
            morph < 0.6f -> MinimalProcess.SUBTRACTION
            morph < 0.8f -> MinimalProcess.SUBSTITUTION
            else -> MinimalProcess.HOCKET
        }
    }

    // --- Process advancement ---

    private fun advanceProcess(process: MinimalProcess, state: EngineState, random: Random) {
        processStep++

        when (process) {
            MinimalProcess.PHASE_SHIFTING -> {
                // Gradually increase phase offset for voices 1-3
                val shiftAmount = 0.05f + state.params.growth * 0.1f
                for (v in 1 until 4) {
                    voicePhases[v] += shiftAmount * v
                    if (voicePhases[v] >= currentPattern.size) {
                        voicePhases[v] -= currentPattern.size
                    }
                }
            }

            MinimalProcess.ADDITION -> {
                // Add one more note to the pattern
                if (additionIndex < currentPattern.size) {
                    for (v in 0 until 4) {
                        voiceActiveNotes[v][additionIndex] = true
                    }
                    additionIndex++
                } else {
                    // Pattern complete, restart
                    additionIndex = 0
                    for (v in 0 until 4) {
                        voiceActiveNotes[v].fill(false)
                    }
                }
            }

            MinimalProcess.SUBTRACTION -> {
                // Remove one note from the pattern
                if (subtractionIndex >= 0) {
                    for (v in 0 until 4) {
                        voiceActiveNotes[v][subtractionIndex] = false
                    }
                    subtractionIndex--
                } else {
                    // All removed, restart
                    subtractionIndex = currentPattern.size - 1
                    for (v in 0 until 4) {
                        voiceActiveNotes[v].fill(true)
                    }
                }
            }

            MinimalProcess.SUBSTITUTION -> {
                // Randomly toggle a position
                val pos = random.nextInt(currentPattern.size)
                substitutionMap[pos] = !substitutionMap[pos]
            }

            MinimalProcess.HOCKET -> {
                // Rotate hocket assignments
                val patLen = currentPattern.size
                for (v in 0 until 4) {
                    val shifted = IntArray(patLen) { pos ->
                        hocketAssignment[v][(pos + 1) % patLen]
                    }
                    hocketAssignment[v] = shifted
                }
            }
        }
    }

    // --- Generation functions ---

    private fun generatePhaseShifting(tick: Long, state: EngineState, random: Random): List<NoteEvent> {
        val events = mutableListOf<NoteEvent>()
        val params = state.params
        val patLen = currentPattern.size
        if (patLen == 0) return events

        for (v in 0 until 4) {
            if (state.trackMutes.getOrElse(v) { false }) continue

            val phaseOffset = voicePhases[v]
            val pos = ((voicePositions[v] + phaseOffset.toInt()) % patLen + patLen) % patLen
            val degree = currentPattern[pos]

            val baseOctave = 5 - v
            val midiNote = scaleDegreeToMidi(degree, state.rootNote, state.scale, baseOctave)

            val velocity = RhythmUtils.humanizeVelocity(
                (75 - v * 5 + (params.tension * 15).toInt()).coerceIn(40, 110),
                params.humanize
            )

            events.add(
                NoteEvent(
                    track = v,
                    midiNote = midiNote.coerceIn(0, 127),
                    velocity = velocity,
                    durationTicks = RhythmUtils.EIGHTH,
                    startTick = RhythmUtils.humanizeTiming(tick, params.humanize),
                    modValue = 64
                )
            )
        }

        // Advance positions (voice 0 always at base rate, others may differ)
        for (v in 0 until 4) {
            voicePositions[v] = (voicePositions[v] + 1) % patLen
        }

        return events
    }

    private fun generateAddition(tick: Long, state: EngineState, random: Random): List<NoteEvent> {
        val events = mutableListOf<NoteEvent>()
        val params = state.params
        val patLen = currentPattern.size
        if (patLen == 0) return events

        for (v in 0 until 4) {
            if (state.trackMutes.getOrElse(v) { false }) continue

            val pos = voicePositions[v] % patLen
            if (!voiceActiveNotes[v][pos]) {
                voicePositions[v] = (voicePositions[v] + 1) % patLen
                continue
            }

            val degree = currentPattern[pos]
            val baseOctave = 5 - v
            val midiNote = scaleDegreeToMidi(degree, state.rootNote, state.scale, baseOctave)

            val velocity = RhythmUtils.humanizeVelocity(
                (70 + (params.tension * 20).toInt()).coerceIn(40, 110),
                params.humanize
            )

            events.add(
                NoteEvent(
                    track = v,
                    midiNote = midiNote.coerceIn(0, 127),
                    velocity = velocity,
                    durationTicks = RhythmUtils.EIGHTH,
                    startTick = RhythmUtils.humanizeTiming(tick, params.humanize),
                    modValue = 64
                )
            )

            voicePositions[v] = (voicePositions[v] + 1) % patLen
        }

        return events
    }

    private fun generateSubtraction(tick: Long, state: EngineState, random: Random): List<NoteEvent> {
        // Same logic as addition but with subtractive process
        return generateAddition(tick, state, random)
    }

    private fun generateSubstitution(tick: Long, state: EngineState, random: Random): List<NoteEvent> {
        val events = mutableListOf<NoteEvent>()
        val params = state.params
        val patLen = currentPattern.size
        if (patLen == 0) return events

        for (v in 0 until 4) {
            if (state.trackMutes.getOrElse(v) { false }) continue

            val pos = voicePositions[v] % patLen
            if (!substitutionMap[pos]) {
                voicePositions[v] = (voicePositions[v] + 1) % patLen
                continue
            }

            val degree = currentPattern[pos]
            val baseOctave = 5 - v
            val midiNote = scaleDegreeToMidi(degree, state.rootNote, state.scale, baseOctave)

            val velocity = RhythmUtils.humanizeVelocity(
                (72 + (params.tension * 18).toInt()).coerceIn(40, 110),
                params.humanize
            )

            events.add(
                NoteEvent(
                    track = v,
                    midiNote = midiNote.coerceIn(0, 127),
                    velocity = velocity,
                    durationTicks = RhythmUtils.EIGHTH,
                    startTick = RhythmUtils.humanizeTiming(tick, params.humanize),
                    modValue = 64
                )
            )

            voicePositions[v] = (voicePositions[v] + 1) % patLen
        }

        return events
    }

    private fun generateHocket(tick: Long, state: EngineState, random: Random): List<NoteEvent> {
        val events = mutableListOf<NoteEvent>()
        val params = state.params
        val patLen = currentPattern.size
        if (patLen == 0) return events

        // In hocket, only one voice plays at each position
        val globalPos = voicePositions[0] % patLen

        for (v in 0 until 4) {
            if (state.trackMutes.getOrElse(v) { false }) continue

            // Check if this voice should play at this position
            if (hocketAssignment[v][globalPos] == 0) continue

            val degree = currentPattern[globalPos]
            val baseOctave = 5 - v
            val midiNote = scaleDegreeToMidi(degree, state.rootNote, state.scale, baseOctave)

            val velocity = RhythmUtils.humanizeVelocity(
                (80 + (params.tension * 15).toInt()).coerceIn(40, 115),
                params.humanize
            )

            // Hocket notes are often short and accented
            val duration = if (params.memory > 0.5f) RhythmUtils.QUARTER else RhythmUtils.EIGHTH

            events.add(
                NoteEvent(
                    track = v,
                    midiNote = midiNote.coerceIn(0, 127),
                    velocity = velocity,
                    durationTicks = duration,
                    startTick = RhythmUtils.humanizeTiming(tick, params.humanize),
                    modValue = (64 + (params.interaction * 30 - 15)).toInt().coerceIn(0, 127)
                )
            )
        }

        // Advance global position
        for (v in 0 until 4) {
            voicePositions[v] = (voicePositions[v] + 1) % patLen
        }

        return events
    }

    // --- Utility ---

    private fun scaleDegreeToMidi(
        degree: Int,
        rootNote: NoteName,
        scale: Scale,
        baseOctave: Int
    ): Int {
        val degrees = scale.degrees
        if (degrees.isEmpty()) return rootNote.midiValue + baseOctave * 12

        val octaveSize = degrees.size
        val octave = if (degree >= 0) degree / octaveSize else (degree - octaveSize + 1) / octaveSize
        val degreeInScale = ((degree % octaveSize) + octaveSize) % octaveSize
        val semitoneOffset = degrees[degreeInScale]

        return rootNote.midiValue + (baseOctave + octave) * 12 + semitoneOffset
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
