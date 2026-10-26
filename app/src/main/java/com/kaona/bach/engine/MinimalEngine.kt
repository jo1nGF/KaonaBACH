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

    // As 8 células iniciais (graus da escala), na ordem dos Materials do manual:
    // PHASE STEPS, ADD FIFTHS, HOCKET MODAL, SUBSTITUTION, PHASE CHORD, ADD WAVE, HOCKET MAJ7, SUBST WIDE
    private val basePatterns = listOf(
        intArrayOf(0, 1, 2, 1, 0, -1, 0, 2),     // PHASE STEPS: célula por graus conjuntos
        intArrayOf(0, 4, 1, 5, 2, 6, 3, 0),      // ADD FIFTHS: arpejo de quintas
        intArrayOf(0, 1, 3, 5, 3, 1, 0, -1),     // HOCKET MODAL: figura modal
        intArrayOf(0, 2, 4, 5, 7, 5, 4, 2),      // SUBSTITUTION: célula modal ampla
        intArrayOf(0, 2, 4, 6, 4, 2, 0, 2),      // PHASE CHORD: acorde com sétima
        intArrayOf(0, 4, 2, 6, 3, -1, 1, 5),     // ADD WAVE: curva de intervalos abertos
        intArrayOf(0, 6, 2, 4, 6, 2, 0, 4),      // HOCKET MAJ7: cor de sétima maior
        intArrayOf(0, 5, 2, 7, -2, 6, 1, 4)      // SUBST WIDE: intervalos largos
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
    private var currentMaterialIndex: Int = -1
    private var additionIndex: Int = 0           // For addition process
    private var subtractionIndex: Int = 0        // For subtraction process
    private var substitutionMap: BooleanArray = BooleanArray(16) { true }
    private var hocketAssignment: Array<IntArray> = Array(4) { intArrayOf() }

    override fun generate(tick: Long, state: EngineState, random: Random): List<NoteEvent> {
        val params = state.params
        // TENSION acima de 60%: o pulso dobra de velocidade (colcheias -> semicolcheias)
        val interval = if (params.tension > 0.6f) RhythmUtils.SIXTEENTH else RhythmUtils.EIGHTH

        if (tick % interval != 0L) return emptyList()
        if (tick == lastGeneratedTick) return emptyList()
        lastGeneratedTick = tick

        // Padrão por compasso: a célula recomeça no início de cada compasso
        if (tick % state.music.meter.barTicks == 0L) {
            for (v in 0 until 4) voicePositions[v] = 0
        }

        if (!initialized) {
            initialize(state, random)
            initialized = true
        }

        // MATERIAL escolhe a célula e, com ela, o procedimento; a troca de Material recomeça o processo
        val materialIndex = MaterialCatalog.index(CompositionalLanguage.MINIMAL, params.material)
        if (materialIndex != currentMaterialIndex) {
            initialize(state, random)
            currentMaterialIndex = materialIndex
        }
        val process = getProcess(params.material)
        val events = mutableListOf<NoteEvent>()

        // Advance process at rate controlled by growth
        processTickCounter++
        // MEMORY: um passo do processo a cada 1 a 13 compassos
        val stepsPerBar = (state.music.meter.barTicks / interval).coerceAtLeast(1)
        val processRate = (1 + (params.memory * 12f).toInt()) * stepsPerBar
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

        // DENSITY: proporção das notas da célula realmente tocadas (35 a 100%);
        // MEMORY > 72%: durações dobradas; TENSION > 58%: um semitom em cerca de 14% das notas
        val played = ArrayList<NoteEvent>(events.size)
        for (e in events) {
            if (random.nextFloat() >= 0.35f + 0.65f * params.density) continue
            var out = e
            if (params.memory > 0.72f) out = out.copy(durationTicks = out.durationTicks * 2)
            if (params.tension > 0.58f && random.nextFloat() < 0.14f) {
                out = out.copy(midiNote = (out.midiNote + 1).coerceAtMost(127))
            }
            played.add(out)
        }
        return played
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
        currentMaterialIndex = -1
        additionIndex = 0
        subtractionIndex = 0
        substitutionMap = BooleanArray(16) { true }
        hocketAssignment = Array(4) { intArrayOf() }
    }

    // --- Initialization ---

    private fun initialize(state: EngineState, random: Random) {
        val patternIndex = MaterialCatalog.index(CompositionalLanguage.MINIMAL, state.params.material)
            .coerceIn(0, basePatterns.size - 1)
        // GROWTH: tamanho da célula, de 4 a 8 posições
        val cellLength = (4 + (state.params.growth * 4f).toInt()).coerceIn(4, 8)
        currentPattern = basePatterns[patternIndex].take(cellLength).toIntArray()

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

    /** PHASE (fase), ADD (aditivo), HOCKET e SUBSTITUTION, na ordem dos 8 Materials. */
    private fun getProcess(material: Float): MinimalProcess {
        return when (MaterialCatalog.index(CompositionalLanguage.MINIMAL, material) % 4) {
            0 -> MinimalProcess.PHASE_SHIFTING
            1 -> MinimalProcess.ADDITION
            2 -> MinimalProcess.HOCKET
            else -> MinimalProcess.SUBSTITUTION
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
                        voicePhases[v] = voicePhases[v] - currentPattern.size.toFloat()
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
