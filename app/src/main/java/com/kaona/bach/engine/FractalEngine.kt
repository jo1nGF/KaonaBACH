package com.kaona.bach.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import java.util.Random

/**
 * FractalEngine - Implements fractal and chaos mathematics mapped to music.
 * Supports Fibonacci sequences, Thue-Morse sequences, Sierpinski triangle
 * distribution, and logistic map chaos generation.
 */
class FractalEngine : CompositionEngine {

    /**
     * Which fractal algorithm is active.
     */
    private enum class FractalAlgorithm {
        FIBONACCI,       // Fibonacci sequence mapped to pitches
        THUE_MORSE,      // Thue-Morse sequence for rhythm
        SIERPINSKI,      // Sierpinski triangle for voice distribution
        LOGISTIC_MAP,    // Logistic map chaos/bifurcation
        BASE4,           // Dígitos da posição em base quatro, em vários níveis
        ONE_OVER_F,      // Camadas binárias de amplitude decrescente (1/f aditivo)
        RULER,           // Função "régua": divisibilidade dos passos por potências de dois
        DRAGON           // Sequência de dobra de papel (curva do dragão)
    }

    // --- Fibonacci state ---
    private var fibA: Long = 0L
    private var fibB: Long = 1L
    private var fibSequence: MutableList<Int> = mutableListOf()

    // --- Thue-Morse state ---
    private var thueMorseSequence: BooleanArray = BooleanArray(256) { false }
    private var thueMorsePosition: Int = 0

    // --- Sierpinski state ---
    private var sierpinskiRow: Int = 0
    private var sierpinskiTriangle: MutableList<BooleanArray> = mutableListOf()

    // --- Logistic map state ---
    private var logisticX: Double = 0.5
    private var logisticR: Double = 3.7  // Chaos parameter
    private var logisticHistory: MutableList<Double> = mutableListOf()

    // --- Common state ---
    private var lastGeneratedTick: Long = -1L
    private var generationCount: Int = 0
    private var initialized: Boolean = false
    private var voicePositions: IntArray = IntArray(4) { 0 }

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

        val algorithm = getAlgorithm(params.material)

        // INTERACTION: de vozes independentes (posições distantes) até as quatro lendo a mesma posição
        val gap = ((1f - params.interaction) * 6f).toInt()
        for (v in 1 until 4) voicePositions[v] = voicePositions[0] + v * gap

        // MEMORY: escala de leitura; quanto mais alta, mais devagar a posição avança
        val readScale = 1 + (params.memory * 3f).toInt()
        val advance = generationCount % readScale == 0
        val savedPositions = voicePositions.copyOf()

        val events = when (algorithm) {
            FractalAlgorithm.FIBONACCI -> generateFibonacci(tick, state, random)
            FractalAlgorithm.THUE_MORSE -> generateThueMorse(tick, state, random)
            FractalAlgorithm.SIERPINSKI -> generateSierpinski(tick, state, random)
            FractalAlgorithm.LOGISTIC_MAP -> generateLogisticMap(tick, state, random)
            FractalAlgorithm.BASE4,
            FractalAlgorithm.ONE_OVER_F,
            FractalAlgorithm.RULER,
            FractalAlgorithm.DRAGON -> generateLevels(algorithm, tick, state, random)
        }
        if (!advance) voicePositions = savedPositions

        generationCount++
        return events
    }

    override fun reset() {
        fibA = 0L
        fibB = 1L
        fibSequence.clear()
        thueMorseSequence = BooleanArray(256) { false }
        thueMorsePosition = 0
        sierpinskiRow = 0
        sierpinskiTriangle.clear()
        logisticX = 0.5
        logisticR = 3.7
        logisticHistory.clear()
        lastGeneratedTick = -1L
        generationCount = 0
        initialized = false
        voicePositions = IntArray(4) { 0 }
    }

    // --- Initialization ---

    private fun initialize(state: EngineState, random: Random) {
        val params = state.params

        // Initialize Fibonacci
        fibA = 0L
        fibB = 1L
        fibSequence.clear()
        generateFibonacciSequence(64)

        // Initialize Thue-Morse
        generateThueMorseSequence(256)

        // Initialize Sierpinski
        generateSierpinskiTriangle(32)

        // Initialize Logistic map
        logisticR = 2.5 + params.tension * 1.5  // r from 2.5 to 4.0
        logisticX = 0.1 + random.nextDouble() * 0.8
        logisticHistory.clear()
        // Pre-generate some values to skip transients
        repeat(50) {
            logisticX = logisticR * logisticX * (1.0 - logisticX)
            logisticHistory.add(logisticX)
        }

        // Set voice positions
        for (v in 0 until 4) {
            voicePositions[v] = v * 4
        }
    }

    // --- Fibonacci generation ---

    private fun generateFibonacciSequence(count: Int) {
        fibA = 0L
        fibB = 1L
        fibSequence.clear()
        fibSequence.add(0)
        fibSequence.add(1)

        for (i in 2 until count) {
            val next = fibA + fibB
            fibA = fibB
            fibB = next
            // Map to scale degree range using modulo
            fibSequence.add((next % 21).toInt() - 10) // Range: -10 to 10
        }
    }

    private fun generateFibonacci(tick: Long, state: EngineState, random: Random): List<NoteEvent> {
        val events = mutableListOf<NoteEvent>()
        val params = state.params

        // Extend sequence if needed
        while (fibSequence.size < generationCount + 20) {
            val next = fibA + fibB
            fibA = fibB
            fibB = next
            fibSequence.add((next % 21).toInt() - 10)
        }

        for (v in 0 until 4) {
            if (state.trackMutes.getOrElse(v) { false }) continue

            val pos = voicePositions[v]
            if (pos >= fibSequence.size) continue

            val degree = fibSequence[pos]
            val scaledDegree = (degree * (1f + params.morph)).toInt()

            val baseOctave = 5 - v
            val midiNote = scaleDegreeToMidi(scaledDegree, state.rootNote, state.scale, baseOctave)

            // Use Fibonacci ratios for velocity
            val fibRatio = if (pos > 1 && fibSequence[pos - 1] != 0) {
                abs(fibSequence[pos].toFloat() / fibSequence[pos - 1].toFloat())
            } else {
                1f
            }
            val baseVelocity = (60 + (fibRatio * 20).toInt() + (params.tension * 20).toInt())
                .coerceIn(35, 120)
            val velocity = RhythmUtils.humanizeVelocity(baseVelocity - v * 5, params.humanize)

            // Duration based on Fibonacci-like pattern
            val duration = when (pos % 5) {
                0 -> RhythmUtils.HALF
                1 -> RhythmUtils.QUARTER
                2 -> RhythmUtils.EIGHTH
                3 -> RhythmUtils.QUARTER
                else -> RhythmUtils.DOTTED_QUARTER
            }

            events.add(
                NoteEvent(
                    track = v,
                    midiNote = midiNote.coerceIn(0, 127),
                    velocity = velocity,
                    durationTicks = duration,
                    startTick = RhythmUtils.humanizeTiming(tick, params.humanize),
                    modValue = (fibRatio * 64).toInt().coerceIn(0, 127)
                )
            )

            voicePositions[v]++
        }

        return events
    }

    // --- Thue-Morse generation ---

    private fun generateThueMorseSequence(length: Int) {
        thueMorseSequence = BooleanArray(length) { n ->
            // Thue-Morse: count 1-bits in binary representation of n
            var bits = 0
            var x = n
            while (x > 0) {
                bits += x and 1
                x = x shr 1
            }
            bits % 2 == 1
        }
    }

    private fun generateThueMorse(tick: Long, state: EngineState, random: Random): List<NoteEvent> {
        val events = mutableListOf<NoteEvent>()
        val params = state.params
        val seqLen = thueMorseSequence.size

        for (v in 0 until 4) {
            if (state.trackMutes.getOrElse(v) { false }) continue

            val pos = voicePositions[v] % seqLen
            val isBeat = thueMorseSequence[pos]

            if (isBeat) {
                // Use Thue-Morse to determine pitch contour
                // Look ahead to create melodic interest
                val lookAhead = 4
                var pitchAccum = 0
                for (la in 0 until lookAhead) {
                    val laPos = (pos + la) % seqLen
                    pitchAccum += if (thueMorseSequence[laPos]) 1 else -1
                }

                // Additional pitch from voice offset
                val degree = pitchAccum + v * 2
                val baseOctave = 5 - v
                val midiNote = scaleDegreeToMidi(degree, state.rootNote, state.scale, baseOctave)

                val velocity = RhythmUtils.humanizeVelocity(
                    (75 + (params.tension * 25).toInt() - v * 5).coerceIn(35, 115),
                    params.humanize
                )

                // Rhythm from Thue-Morse: alternating note lengths
                val nextBeat = thueMorseSequence[(pos + 1) % seqLen]
                val duration = if (nextBeat == isBeat) {
                    RhythmUtils.QUARTER // Consecutive same values = longer note
                } else {
                    RhythmUtils.EIGHTH  // Alternation = shorter note
                }

                events.add(
                    NoteEvent(
                        track = v,
                        midiNote = midiNote.coerceIn(0, 127),
                        velocity = velocity,
                        durationTicks = duration,
                        startTick = RhythmUtils.humanizeTiming(tick, params.humanize),
                        modValue = if (isBeat) 90 else 40
                    )
                )
            }

            voicePositions[v]++
        }

        return events
    }

    // --- Sierpinski generation ---

    private fun generateSierpinskiTriangle(rows: Int) {
        sierpinskiTriangle.clear()
        val width = rows * 2 + 1

        // First row: single cell in center
        val firstRow = BooleanArray(width) { false }
        firstRow[width / 2] = true
        sierpinskiTriangle.add(firstRow)

        // Generate using XOR rule (Rule 90 cellular automaton)
        for (r in 1 until rows) {
            val prevRow = sierpinskiTriangle[r - 1]
            val newRow = BooleanArray(width) { false }
            for (c in 1 until width - 1) {
                newRow[c] = prevRow[c - 1] xor prevRow[c + 1]
            }
            sierpinskiTriangle.add(newRow)
        }
    }

    private fun generateSierpinski(tick: Long, state: EngineState, random: Random): List<NoteEvent> {
        val events = mutableListOf<NoteEvent>()
        val params = state.params

        if (sierpinskiTriangle.isEmpty()) return events

        val row = sierpinskiRow % sierpinskiTriangle.size
        val currentRow = sierpinskiTriangle[row]

        // Distribute columns across voices
        val colsPerVoice = currentRow.size / 4

        for (v in 0 until 4) {
            if (state.trackMutes.getOrElse(v) { false }) continue

            val startCol = v * colsPerVoice
            val endCol = min(startCol + colsPerVoice, currentRow.size)

            // Count active cells in this voice's region
            var activeCells = 0
            var firstActiveCol = -1
            for (c in startCol until endCol) {
                if (currentRow[c]) {
                    activeCells++
                    if (firstActiveCol < 0) firstActiveCol = c
                }
            }

            if (activeCells == 0) continue

            // Map cell position to pitch
            val degree = if (firstActiveCol >= 0) {
                (firstActiveCol - currentRow.size / 2) % state.scale.degrees.size.coerceAtLeast(1)
            } else {
                0
            }

            val baseOctave = 5 - v
            val midiNote = scaleDegreeToMidi(degree, state.rootNote, state.scale, baseOctave)

            // Velocity proportional to active cell density
            val density = activeCells.toFloat() / colsPerVoice
            val baseVelocity = (50 + (density * 40).toInt() + (params.tension * 20).toInt())
                .coerceIn(35, 120)
            val velocity = RhythmUtils.humanizeVelocity(baseVelocity, params.humanize)

            val duration = when {
                activeCells > 3 -> RhythmUtils.HALF
                activeCells > 1 -> RhythmUtils.QUARTER
                else -> RhythmUtils.EIGHTH
            }

            events.add(
                NoteEvent(
                    track = v,
                    midiNote = midiNote.coerceIn(0, 127),
                    velocity = velocity,
                    durationTicks = duration,
                    startTick = RhythmUtils.humanizeTiming(tick, params.humanize),
                    modValue = (density * 127).toInt().coerceIn(0, 127)
                )
            )
        }

        // Advance row
        val advanceRate = (1 + (1f - params.growth) * 3).toInt().coerceIn(1, 4)
        if (generationCount % advanceRate == 0) {
            sierpinskiRow++
            if (sierpinskiRow >= sierpinskiTriangle.size) {
                sierpinskiRow = 0
                // Regenerate with different size for variation
                val newSize = (16 + (params.morph * 32).toInt()).coerceIn(16, 48)
                generateSierpinskiTriangle(newSize)
            }
        }

        return events
    }

    // --- Logistic map generation ---

    private fun generateLogisticMap(tick: Long, state: EngineState, random: Random): List<NoteEvent> {
        val events = mutableListOf<NoteEvent>()
        val params = state.params

        // Update r parameter from tension (maps to chaos regime)
        // r < 3.0: stable, r = 3.0-3.449: period doubling, r > 3.57: chaos
        logisticR = 2.5 + params.tension * 1.5

        for (v in 0 until 4) {
            if (state.trackMutes.getOrElse(v) { false }) continue

            // Iterate logistic map: x_{n+1} = r * x_n * (1 - x_n)
            logisticX = logisticR * logisticX * (1.0 - logisticX)

            // Clamp to prevent numerical issues
            logisticX = logisticX.coerceIn(0.001, 0.999)
            logisticHistory.add(logisticX)

            // Limit history size
            if (logisticHistory.size > 256) {
                logisticHistory.removeAt(0)
            }

            // Map x (0-1) to scale degree
            val scaleSize = state.scale.degrees.size.coerceAtLeast(1)
            // GROWTH: extensão das alturas, de 6 a 18 semitons
            val semitoneRange = 6f + params.growth * 12f
            val totalRange = (semitoneRange * scaleSize / 12f).toInt().coerceAtLeast(2)
            val degree = (logisticX * totalRange).toInt() - totalRange / 2

            val baseOctave = 5 - v
            val midiNote = scaleDegreeToMidi(degree, state.rootNote, state.scale, baseOctave)

            // Velocity from rate of change (derivative approximation)
            val prevX = if (logisticHistory.size >= 2) {
                logisticHistory[logisticHistory.size - 2]
            } else {
                logisticX
            }
            val deltaX = abs(logisticX - prevX)
            val baseVelocity = (50 + (deltaX * 150).toInt()).coerceIn(35, 120)
            val velocity = RhythmUtils.humanizeVelocity(baseVelocity - v * 5, params.humanize)

            // Duration: more chaotic = shorter notes
            val duration = when {
                params.tension > 0.8f -> RhythmUtils.SIXTEENTH
                params.tension > 0.6f -> RhythmUtils.EIGHTH
                params.tension > 0.3f -> RhythmUtils.QUARTER
                else -> RhythmUtils.HALF
            }

            // ModValue tracks the logistic map position
            val modValue = (logisticX * 127).toInt().coerceIn(0, 127)

            events.add(
                NoteEvent(
                    track = v,
                    midiNote = midiNote.coerceIn(0, 127),
                    velocity = velocity,
                    durationTicks = duration,
                    startTick = RhythmUtils.humanizeTiming(tick, params.humanize),
                    modValue = modValue
                )
            )
        }

        return events
    }

    // --- Algorithm selection ---

    /** Ordem do manual: THUE-MORSE, LOGISTIC, BASE-4, 1/F ADDITIVE, RULER, SIERPINSKI, FIBONACCI, DRAGON. */
    private fun getAlgorithm(material: Float): FractalAlgorithm {
        return when (MaterialCatalog.index(CompositionalLanguage.FRACTAL, material)) {
            0 -> FractalAlgorithm.THUE_MORSE
            1 -> FractalAlgorithm.LOGISTIC_MAP
            2 -> FractalAlgorithm.BASE4
            3 -> FractalAlgorithm.ONE_OVER_F
            4 -> FractalAlgorithm.RULER
            5 -> FractalAlgorithm.SIERPINSKI
            6 -> FractalAlgorithm.FIBONACCI
            else -> FractalAlgorithm.DRAGON
        }
    }

    /**
     * BASE-4, 1/F ADDITIVE, RULER e DRAGON: sequências lidas por vários níveis sobrepostos.
     *  - GROWTH: número de níveis (1 a 5);
     *  - MEMORY: escala de leitura (a posição avança um passo a cada 1 a 8 gerações);
     *  - INTERACTION: distância de leitura entre as vozes (no máximo, as quatro leem a mesma posição);
     *  - MORPH (BASE-4): acima de 44% complementa os dígitos; acima de 76% inverte um nível sim, outro não;
     *  - TENSION: acima de 56%, um semitom em cerca de 16% das notas;
     *  - DENSITY: probabilidade de cada voz emitir uma nota.
     */
    private fun generateLevels(
        algorithm: FractalAlgorithm,
        tick: Long,
        state: EngineState,
        random: Random
    ): List<NoteEvent> {
        val events = mutableListOf<NoteEvent>()
        val params = state.params

        val levels = 1 + (params.growth * 4f).toInt().coerceIn(0, 4)
        val readScale = 1 + (params.memory * 7f).toInt()
        val base = generationCount / readScale
        val gap = ((1f - params.interaction) * 6f).toInt()
        val playProbability = 0.2f + 0.8f * params.density

        for (v in 0 until 4) {
            if (state.trackMutes.getOrElse(v) { false }) continue
            if (random.nextFloat() > playProbability) continue

            val n = base + v * gap
            var degree = 0
            var accent = 0

            when (algorithm) {
                FractalAlgorithm.BASE4 -> {
                    for (level in 0 until levels) {
                        var digit = (n shr (2 * level)) and 3
                        if (params.morph > 0.44f) digit = 3 - digit
                        if (params.morph > 0.76f && level % 2 == 1) digit = 3 - digit
                        degree += (digit - 1) * (levels - level)
                        accent += digit
                    }
                }
                FractalAlgorithm.ONE_OVER_F -> {
                    for (level in 0 until levels) {
                        val bit = (n shr level) and 1
                        degree += (if (bit == 1) 1 else -1) * (levels - level)
                        accent += bit
                    }
                }
                FractalAlgorithm.RULER -> {
                    val ruler = Integer.numberOfTrailingZeros(n + 1)
                    val side = if (((n + 1) shr (ruler + 1)) and 1 == 1) 1 else -1
                    degree = side * ruler * 2
                    accent = ruler
                }
                else -> { // DRAGON: contorno acumulado da sequência de dobra de papel
                    val limit = n % 1024
                    var contour = 0
                    var lastTurn = 1
                    for (i in 1..limit) {
                        val low = i and -i
                        lastTurn = if (((i / low) shr 1) and 1 == 0) 1 else -1
                        contour += lastTurn
                    }
                    degree = contour.coerceIn(-levels * 2, levels * 2)
                    accent = if (lastTurn == 1) 2 else 0
                }
            }

            val baseOctave = 5 - v
            var midiNote = scaleDegreeToMidi(degree, state.rootNote, state.scale, baseOctave)
            if (params.tension > 0.56f && random.nextFloat() < 0.16f) {
                midiNote += if (random.nextBoolean()) 1 else -1
            }

            val baseVelocity = (62 + accent * 4 + (params.tension * 25).toInt() - v * 5).coerceIn(35, 115)
            val duration = when {
                accent >= 3 -> RhythmUtils.HALF
                accent == 2 -> RhythmUtils.QUARTER
                else -> RhythmUtils.EIGHTH
            }

            events.add(
                NoteEvent(
                    track = v,
                    midiNote = midiNote.coerceIn(0, 127),
                    velocity = RhythmUtils.humanizeVelocity(baseVelocity, params.humanize),
                    durationTicks = duration,
                    startTick = tick,
                    modValue = (40 + accent * 12).coerceIn(0, 127)
                )
            )
        }
        return events
    }

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

        return (rootNote.midiValue + (baseOctave + octave) * 12 + semitoneOffset).coerceIn(0, 127)
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
