package com.kaona.bach.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import java.util.Random

/**
 * CellularEngine - Implements 1D cellular automata (Rules 0-255)
 * mapped to musical parameters. Each cell state maps to a note
 * in the current scale.
 */
class CellularEngine : CompositionEngine {

    // Automaton configuration
    private var ruleNumber: Int = 110           // Default: Rule 110 (Turing-complete)
    private var ruleBits: BooleanArray = BooleanArray(8) { false }
    private var gridWidth: Int = 32
    private var currentGeneration: BooleanArray = BooleanArray(32) { false }
    private var nextGeneration: BooleanArray = BooleanArray(32) { false }
    private var generationHistory: MutableList<BooleanArray> = mutableListOf()
    private var currentRow: Int = 0             // Which generation we're reading from
    private var currentCol: Int = 0             // Which cell we're reading
    private var lastGeneratedTick: Long = -1L
    private var generationCount: Int = 0
    private var initialized: Boolean = false
    private var cellReadPositions: IntArray = IntArray(4) { 0 }  // Per-voice read position

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

        // Determine active cell count from density
        val activeCells = getActiveCellCount(params.density)

        // Evolve automaton based on growth rate
        val evolveRate = (1 + (1f - params.growth) * 7).toInt().coerceIn(1, 8)
        if (generationCount % evolveRate == 0) {
            evolveGeneration(state)
        }

        // Read cells and generate notes for each voice
        for (v in 0 until 4) {
            if (state.trackMutes.getOrElse(v) { false }) continue

            val cellPos = cellReadPositions[v] % gridWidth
            val cellState = currentGeneration[cellPos]

            // Only generate note if cell is alive (or based on density thresholds)
            val shouldPlay = when {
                params.density > 0.8f -> true  // Always play at max density
                cellState -> true              // Cell alive = play
                params.tension > 0.7f && random.nextFloat() < 0.3f -> true // Tension adds chaos
                else -> false
            }

            if (shouldPlay) {
                val pitchDegree = cellToPitch(cellPos, v, state, params.morph)
                val midiNote = scaleDegreeToMidi(pitchDegree, state.rootNote, state.scale, v)

                // Velocity from neighborhood density
                val neighborAlive = countNeighbors(cellPos)
                val baseVelocity = (50 + neighborAlive * 10 + (params.tension * 20).toInt())
                    .coerceIn(35, 120)
                val velocity = RhythmUtils.humanizeVelocity(baseVelocity, params.humanize)

                val duration = when {
                    neighborAlive >= 2 -> RhythmUtils.QUARTER
                    neighborAlive == 1 -> RhythmUtils.EIGHTH
                    else -> RhythmUtils.SIXTEENTH
                }

                events.add(
                    NoteEvent(
                        track = v,
                        midiNote = midiNote.coerceIn(0, 127),
                        velocity = velocity,
                        durationTicks = duration,
                        startTick = RhythmUtils.humanizeTiming(tick, params.humanize),
                        modValue = (neighborAlive * 21).coerceIn(0, 127)
                    )
                )
            }

            // Advance read position for this voice
            cellReadPositions[v] = (cellReadPositions[v] + 1) % gridWidth
        }

        generationCount++
        return events
    }

    override fun reset() {
        currentGeneration = BooleanArray(gridWidth) { false }
        nextGeneration = BooleanArray(gridWidth) { false }
        generationHistory.clear()
        currentRow = 0
        currentCol = 0
        lastGeneratedTick = -1L
        generationCount = 0
        initialized = false
        cellReadPositions = IntArray(4) { 0 }
    }

    // --- Initialization ---

    private fun initialize(state: EngineState, random: Random) {
        val params = state.params

        // Set rule from material parameter (0-255)
        ruleNumber = (params.material * 255).toInt().coerceIn(0, 255)
        parseRule(ruleNumber)

        // Grid width based on density
        gridWidth = when {
            params.density > 0.7f -> 64
            params.density > 0.4f -> 32
            else -> 16
        }

        // Initialize generations
        currentGeneration = BooleanArray(gridWidth) { false }
        nextGeneration = BooleanArray(gridWidth) { false }

        // Seed the initial generation
        seedGeneration(params.material, random)

        // Set voice read positions spread across the grid
        for (v in 0 until 4) {
            cellReadPositions[v] = (v * gridWidth / 4)
        }

        // Pre-evolve a few generations to avoid boring start
        for (i in 0 until 3) {
            evolveGeneration(state)
        }
    }

    private fun parseRule(rule: Int) {
        ruleBits = BooleanArray(8) { bit ->
            (rule shr bit) and 1 == 1
        }
    }

    private fun seedGeneration(material: Float, random: Random) {
        // Different seeding strategies based on material
        when {
            material < 0.25f -> {
                // Single cell in center
                currentGeneration[gridWidth / 2] = true
            }
            material < 0.5f -> {
                // Random sparse seeding
                for (i in currentGeneration.indices) {
                    currentGeneration[i] = random.nextFloat() < 0.2f
                }
            }
            material < 0.75f -> {
                // Alternating pattern
                for (i in currentGeneration.indices) {
                    currentGeneration[i] = i % 3 == 0
                }
            }
            else -> {
                // Dense random
                for (i in currentGeneration.indices) {
                    currentGeneration[i] = random.nextFloat() < 0.5f
                }
            }
        }

        // Ensure at least one cell is alive
        if (currentGeneration.none { it }) {
            currentGeneration[gridWidth / 2] = true
        }
    }

    // --- Cellular automaton evolution ---

    private fun evolveGeneration(state: EngineState) {
        // Save current generation to history
        generationHistory.add(currentGeneration.copyOf())
        if (generationHistory.size > 64) {
            generationHistory.removeAt(0)
        }

        nextGeneration = BooleanArray(gridWidth) { false }

        for (i in 0 until gridWidth) {
            val left = getCell(i - 1)
            val center = getCell(i)
            val right = getCell(i + 1)

            // Compute neighborhood index (3-bit number)
            val neighborhood = (if (left) 4 else 0) or
                    (if (center) 2 else 0) or
                    (if (right) 1 else 0)

            nextGeneration[i] = ruleBits[neighborhood]
        }

        // Swap generations
        currentGeneration = nextGeneration.copyOf()
        currentRow++

        // Detect dead state and re-seed
        if (currentGeneration.none { it }) {
            currentGeneration[gridWidth / 2] = true
            // Inject some randomness
            val injectCount = (gridWidth * 0.1f).toInt().coerceAtLeast(1)
            val random = Random(currentRow.toLong())
            repeat(injectCount) {
                currentGeneration[random.nextInt(gridWidth)] = true
            }
        }
    }

    private fun getCell(index: Int): Boolean {
        // Wrapping boundary conditions
        val wrappedIndex = ((index % gridWidth) + gridWidth) % gridWidth
        return currentGeneration[wrappedIndex]
    }

    private fun countNeighbors(index: Int): Int {
        var count = 0
        for (offset in -2..2) {
            if (offset == 0) continue
            if (getCell(index + offset)) count++
        }
        return count
    }

    // --- Pitch mapping ---

    private fun cellToPitch(cellIndex: Int, voice: Int, state: EngineState, morph: Float): Int {
        val scaleSize = state.scale.degrees.size.coerceAtLeast(1)

        return when {
            morph < 0.25f -> {
                // Direct mapping: cell position -> scale degree
                cellIndex % scaleSize
            }
            morph < 0.5f -> {
                // Neighborhood density mapping
                val density = countNeighbors(cellIndex)
                density % scaleSize
            }
            morph < 0.75f -> {
                // History-based: combine current and previous generation
                val historyDepth = min(generationHistory.size, 3)
                var accum = cellIndex
                for (h in 0 until historyDepth) {
                    val histGen = generationHistory[generationHistory.size - 1 - h]
                    if (cellIndex < histGen.size && histGen[cellIndex]) {
                        accum += h + 1
                    }
                }
                accum % scaleSize
            }
            else -> {
                // XOR pattern: combine cell index with generation number
                (cellIndex xor (currentRow % gridWidth)) % scaleSize
            }
        }
    }

    // --- Utility ---

    private fun scaleDegreeToMidi(
        degree: Int,
        rootNote: NoteName,
        scale: Scale,
        voice: Int
    ): Int {
        val degrees = scale.degrees
        if (degrees.isEmpty()) return 60

        val octaveSize = degrees.size
        val octave = if (degree >= 0) degree / octaveSize else (degree - octaveSize + 1) / octaveSize
        val degreeInScale = ((degree % octaveSize) + octaveSize) % octaveSize
        val semitoneOffset = degrees[degreeInScale]

        val baseOctave = when (voice) {
            0 -> 5  // Soprano
            1 -> 4  // Alto
            2 -> 3  // Tenor
            3 -> 2  // Bass
            else -> 4
        }

        return (rootNote.midiValue + (baseOctave + octave) * 12 + semitoneOffset).coerceIn(0, 127)
    }

    private fun getActiveCellCount(density: Float): Int {
        return (gridWidth * density.coerceIn(0.1f, 1f)).toInt().coerceAtLeast(1)
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
