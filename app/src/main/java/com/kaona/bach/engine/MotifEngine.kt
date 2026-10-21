package com.kaona.bach.engine

import java.util.Random

class MotifEngine : CompositionEngine {
    // Collection of melodic cells - intervals relative to root
    private val cellCollections = Array(32) { collIdx ->
        val seed = Random(collIdx.toLong() * 42)
        Array((4 + seed.nextInt(5))) { cellIdx ->
            val cellLength = 3 + seed.nextInt(4)
            IntArray(cellLength) { seed.nextInt(12) - 6 }
        }
    }

    // Rhythm patterns for cells
    private val rhythmCollections = Array(32) { collIdx ->
        val seed = Random(collIdx.toLong() * 37)
        Array((4 + seed.nextInt(5))) { cellIdx ->
            val cellLength = 3 + seed.nextInt(4)
            IntArray(cellLength) {
                arrayOf(RhythmUtils.QUARTER, RhythmUtils.EIGHTH, RhythmUtils.SIXTEENTH,
                    RhythmUtils.DOTTED_EIGHTH, RhythmUtils.HALF)[seed.nextInt(5)]
            }
        }
    }

    private var currentCells = Array(4) { intArrayOf() }
    private var currentRhythms = Array(4) { intArrayOf() }
    private var cellPositions = IntArray(4)
    private var nextEventTick = LongArray(4)
    private var baseNotes = IntArray(4) { 60 }
    private var cellCounter = IntArray(4)

    override fun generate(tick: Long, state: EngineState, random: Random): List<NoteEvent> {
        val events = mutableListOf<NoteEvent>()
        val collIdx = (state.params.material * 31).toInt().coerceIn(0, 31)
        val cells = cellCollections[collIdx]
        val rhythms = rhythmCollections[collIdx]
        val activeVoices = (1 + state.params.density * 3).toInt().coerceIn(1, 4)

        for (track in 0 until activeVoices) {
            if (tick < nextEventTick[track]) continue

            // Need new cell?
            if (cellPositions[track] >= currentCells[track].size || currentCells[track].isEmpty()) {
                // Select new cell
                val renewRate = 1f - state.params.memory
                if (random.nextFloat() < renewRate || currentCells[track].isEmpty()) {
                    val cellIdx = if (state.params.interaction > 0.5f && track > 0) {
                        // Share cell with track 0 but transform
                        (cellCounter[0]) % cells.size
                    } else {
                        (cellCounter[track] + random.nextInt(cells.size)) % cells.size
                    }
                    cellCounter[track]++

                    // Apply morph transformations
                    var cell = cells[cellIdx].copyOf()
                    var rhythm = if (cellIdx < rhythms.size) rhythms[cellIdx].copyOf() else intArrayOf(RhythmUtils.QUARTER)

                    when {
                        state.params.morph < 0.25f -> { /* Original */ }
                        state.params.morph < 0.5f -> cell = cell.reversedArray() // Retrograde
                        state.params.morph < 0.75f -> cell = cell.map { -it }.toIntArray() // Inversion
                        else -> { // Transpose
                            val transposition = (state.params.tension * 7).toInt()
                            cell = cell.map { it + transposition }.toIntArray()
                        }
                    }

                    currentCells[track] = cell
                    currentRhythms[track] = rhythm
                }
                cellPositions[track] = 0

                // Set base note based on register
                val registers = intArrayOf(72, 60, 48, 36)
                baseNotes[track] = registers[track]
            }

            // Generate event from current cell position
            val pos = cellPositions[track]
            val intervals = currentCells[track]
            val rhythmPatterns = currentRhythms[track]

            if (pos < intervals.size) {
                val rawNote = baseNotes[track] + intervals[pos]
                val note = state.scale.quantize(rawNote, state.rootNote).coerceIn(24, 108)
                val duration = if (pos < rhythmPatterns.size) rhythmPatterns[pos] else RhythmUtils.QUARTER
                val velocity = (70 + (state.params.tension * 40).toInt() + random.nextInt(20)).coerceIn(1, 127)
                val growthFactor = (0.5f + state.params.growth * 1.5f)

                events.add(NoteEvent(
                    track = track,
                    midiNote = note,
                    velocity = velocity,
                    durationTicks = (duration * growthFactor).toInt(),
                    startTick = tick,
                    modValue = velocity
                ))

                nextEventTick[track] = tick + (duration * growthFactor).toLong()
                cellPositions[track]++
            }
        }
        return events
    }

    override fun reset() {
        currentCells = Array(4) { intArrayOf() }
        currentRhythms = Array(4) { intArrayOf() }
        cellPositions = IntArray(4)
        nextEventTick = LongArray(4)
        baseNotes = IntArray(4) { 60 }
        cellCounter = IntArray(4)
    }
}
