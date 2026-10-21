package com.kaona.bach.engine

import java.util.Random

class GrooveEngine : CompositionEngine {
    // 64 rhythm templates - each is a 16-step pattern (1=hit, 0=rest) for 4 voices
    private val templates = Array(64) { templateIdx ->
        val seed = Random(templateIdx.toLong() * 53)
        Array(4) { voice ->
            val density = when (voice) {
                0 -> 0.3f  // kick-like: sparse
                1 -> 0.25f // snare-like: sparse
                2 -> 0.6f  // hihat-like: dense
                3 -> 0.2f  // perc: sparse accents
                else -> 0.3f
            }
            BooleanArray(16) { step ->
                when (voice) {
                    0 -> step % 4 == 0 || (seed.nextFloat() < 0.15f) // Kick on beats
                    1 -> step % 8 == 4 || (seed.nextFloat() < 0.1f)  // Snare on 2&4
                    2 -> seed.nextFloat() < density                   // Hihat pattern
                    3 -> seed.nextFloat() < density                   // Perc accents
                    else -> false
                }
            }
        }
    }

    // Pitch patterns for groove mix
    private val pitchPatterns = Array(64) { idx ->
        val seed = Random(idx.toLong() * 71)
        IntArray(16) { seed.nextInt(12) }
    }

    private var currentStep = 0
    private var lastStepTick: Long = -1

    override fun generate(tick: Long, state: EngineState, random: Random): List<NoteEvent> {
        // Step through at eighth note resolution
        val stepInterval = RhythmUtils.EIGHTH.toLong()
        if (tick % stepInterval != 0L) return emptyList()

        val events = mutableListOf<NoteEvent>()
        val templateIdx = (state.params.material * 63).toInt().coerceIn(0, 63)
        val template = templates[templateIdx]
        val patternLength = when {
            state.params.growth < 0.25f -> 4
            state.params.growth < 0.5f -> 8
            state.params.growth < 0.75f -> 12
            else -> 16
        }

        // Apply morph (rotation)
        val morphShift = (state.params.morph * patternLength).toInt()
        val step = (currentStep + morphShift) % patternLength

        val activeVoices = (1 + state.params.density * 3).toInt().coerceIn(1, 4)

        for (track in 0 until activeVoices) {
            val pattern = template[track]
            val isHit = if (step < pattern.size) pattern[step] else false

            // Density probability gate
            if (isHit && random.nextFloat() < (0.3f + state.params.density * 0.7f)) {
                val baseNote = when (track) {
                    0 -> 36  // C2 (kick area)
                    1 -> 38  // D2 (snare area)
                    2 -> 42  // F#2 (hihat area)
                    3 -> 46  // Bb2 (perc area)
                    else -> 36
                }

                // Scale quantize based on root
                val note = state.scale.quantize(baseNote + (state.params.tension * 12).toInt(), state.rootNote)

                val accentProbability = if (step % 4 == 0) 0.8f else 0.3f
                val isAccent = random.nextFloat() < accentProbability
                val velocity = if (isAccent) {
                    (90 + (state.params.tension * 37).toInt()).coerceIn(1, 127)
                } else {
                    (60 + (state.params.tension * 20).toInt()).coerceIn(1, 127)
                }

                events.add(NoteEvent(
                    track = track,
                    midiNote = note,
                    velocity = velocity,
                    durationTicks = RhythmUtils.SIXTEENTH,
                    startTick = tick,
                    modValue = if (isAccent) 100 else 64
                ))
            }
        }

        currentStep = (currentStep + 1) % patternLength
        return events
    }

    /**
     * Groove Mix mode: maps groove rhythm onto pitched melodic content
     */
    fun generateMix(tick: Long, state: EngineState, random: Random): List<NoteEvent> {
        val stepInterval = RhythmUtils.EIGHTH.toLong()
        if (tick % stepInterval != 0L) return emptyList()

        val events = mutableListOf<NoteEvent>()
        val templateIdx = (state.params.material * 63).toInt().coerceIn(0, 63)
        val template = templates[templateIdx]
        val pitchPattern = pitchPatterns[templateIdx]
        val patternLength = when {
            state.params.growth < 0.25f -> 4
            state.params.growth < 0.5f -> 8
            state.params.growth < 0.75f -> 12
            else -> 16
        }

        val morphShift = (state.params.morph * patternLength).toInt()
        val step = (currentStep + morphShift) % patternLength
        val activeVoices = (1 + state.params.density * 3).toInt().coerceIn(1, 4)

        // Voice registers for melodic distribution
        val registers = intArrayOf(72, 60, 48, 36) // Soprano, Alto, Tenor, Bass

        for (track in 0 until activeVoices) {
            val pattern = template[track]
            val isHit = if (step < pattern.size) pattern[step] else false

            if (isHit && random.nextFloat() < (0.3f + state.params.density * 0.7f)) {
                // Map rhythm to pitched note
                val pitchOffset = if (step < pitchPattern.size) pitchPattern[step] else 0
                val rawNote = registers[track] + pitchOffset - 6
                val note = state.scale.quantize(rawNote, state.rootNote).coerceIn(24, 108)

                val velocity = (70 + (state.params.tension * 40).toInt() + random.nextInt(20)).coerceIn(1, 127)
                val duration = if (random.nextFloat() < state.params.growth) RhythmUtils.QUARTER else RhythmUtils.EIGHTH

                events.add(NoteEvent(
                    track = track,
                    midiNote = note,
                    velocity = velocity,
                    durationTicks = duration,
                    startTick = tick,
                    modValue = velocity
                ))
            }
        }

        currentStep = (currentStep + 1) % patternLength
        return events
    }

    override fun reset() {
        currentStep = 0
        lastStepTick = -1
    }
}
