package com.kaona.bach.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import java.util.Random

/**
 * TintinnabuliEngine - Implements Arvo Pärt's Tintinnabuli technique.
 * Voice 1 (M-voice) moves stepwise while Voices 2-4 (T-voices)
 * are drawn from the notes of the current triad.
 */
class TintinnabuliEngine : CompositionEngine {

    /**
     * T-voice position rule: determines which triad note the T-voice selects.
     * "Superior" means the nearest triad note above/equal; "Inferior" means below/equal.
     * "1st" = nearest, "2nd" = next nearest.
     */
    private enum class TPosition {
        FIRST_SUPERIOR,
        FIRST_INFERIOR,
        SECOND_SUPERIOR,
        SECOND_INFERIOR
    }

    /**
     * M-voice melodic pattern types.
     */
    private enum class MelodicPattern {
        ASCENDING_STEPWISE,
        DESCENDING_STEPWISE,
        ASCENDING_THEN_DESCENDING,
        PENDULUM,           // Alternating up/down expanding
        SPIRAL,             // Expanding spiral from center
        CONVERGING          // Converging toward center
    }

    // Internal state
    private var currentMDegree: Int = 0          // Current M-voice scale degree
    private var mDirection: Int = 1              // M-voice direction
    private var mStepCount: Int = 0              // Steps taken in current direction
    private var mPatternPhase: Int = 0           // Phase within pattern
    private var mExpandDistance: Int = 1          // For expanding patterns
    private var lastGeneratedTick: Long = -1L
    private var generationCount: Int = 0
    private var phrasePosition: Int = 0
    private var phraseLength: Int = 16
    private var initialized: Boolean = false
    private var mCenterDegree: Int = 0           // Center point for patterns

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
        val pattern = getMelodicPattern(params.material)
        phraseLength = (8 + params.growth * 24).toInt().coerceIn(8, 32)

        // --- Voice 0: M-voice (stepwise melodic motion) ---
        if (!state.trackMutes.getOrElse(0) { false }) {
            val mMidiNote = scaleDegreeToMidi(currentMDegree, state.rootNote, state.scale, 48, 84)
            val mVelocity = RhythmUtils.humanizeVelocity(
                (75 + (params.tension * 15).toInt()).coerceIn(40, 110),
                params.humanize
            )
            val mDuration = when {
                params.growth > 0.7f -> RhythmUtils.HALF
                params.growth > 0.4f -> RhythmUtils.QUARTER
                else -> RhythmUtils.EIGHTH
            }

            events.add(
                NoteEvent(
                    track = 0,
                    midiNote = mMidiNote.coerceIn(0, 127),
                    velocity = mVelocity,
                    durationTicks = mDuration,
                    startTick = RhythmUtils.humanizeTiming(tick, params.humanize),
                    modValue = 64
                )
            )
        }

        // --- Voices 1-3: T-voices (triad tones) ---
        val triadNotes = getCurrentTriadNotes(state)
        val tPositions = getTVoicePositions(params.interaction, params.morph)

        for (v in 1..3) {
            if (state.trackMutes.getOrElse(v) { false }) continue

            val tPosition = tPositions[v - 1]
            val mMidiForRef = scaleDegreeToMidi(currentMDegree, state.rootNote, state.scale, 36, 84)
            var tMidiNote = findTVoiceNote(mMidiForRef, triadNotes, tPosition)

            // Apply tension: occasional deviation from pure triad
            if (params.tension > 0.3f && random.nextFloat() < params.tension * 0.4f) {
                val deviation = if (random.nextBoolean()) 1 else -1
                tMidiNote += deviation
            }

            // Adjust to appropriate voice range
            val rangeBase = when (v) {
                1 -> 48  // Alto-ish range
                2 -> 36  // Tenor range
                3 -> 24  // Bass range
                else -> 48
            }
            val rangeTop = rangeBase + 24
            while (tMidiNote < rangeBase) tMidiNote += 12
            while (tMidiNote > rangeTop) tMidiNote -= 12

            val tVelocity = RhythmUtils.humanizeVelocity(
                (65 + (params.tension * 10).toInt() - v * 3).coerceIn(35, 100),
                params.humanize
            )

            // T-voice duration: often sustained longer than M-voice
            val tDuration = when {
                params.growth > 0.6f -> RhythmUtils.WHOLE
                params.growth > 0.3f -> RhythmUtils.HALF
                else -> RhythmUtils.QUARTER
            }

            events.add(
                NoteEvent(
                    track = v,
                    midiNote = tMidiNote.coerceIn(0, 127),
                    velocity = tVelocity,
                    durationTicks = tDuration,
                    startTick = RhythmUtils.humanizeTiming(tick, params.humanize),
                    modValue = (50 + (params.morph * 28)).toInt().coerceIn(0, 127)
                )
            )
        }

        // Advance M-voice
        advanceMVoice(pattern, state, random)

        phrasePosition++
        if (phrasePosition >= phraseLength) {
            phrasePosition = 0
            // Potentially reset pattern phase
            mPatternPhase = 0
            mExpandDistance = 1
        }

        generationCount++
        return events
    }

    override fun reset() {
        currentMDegree = 0
        mDirection = 1
        mStepCount = 0
        mPatternPhase = 0
        mExpandDistance = 1
        lastGeneratedTick = -1L
        generationCount = 0
        phrasePosition = 0
        phraseLength = 16
        initialized = false
        mCenterDegree = 0
    }

    // --- Initialization ---

    private fun initialize(state: EngineState, random: Random) {
        currentMDegree = 0  // Start on tonic
        mCenterDegree = 0
        mDirection = 1
    }

    // --- M-voice movement ---

    private fun getMelodicPattern(material: Float): MelodicPattern {
        return when {
            material < 0.167f -> MelodicPattern.ASCENDING_STEPWISE
            material < 0.333f -> MelodicPattern.DESCENDING_STEPWISE
            material < 0.5f -> MelodicPattern.ASCENDING_THEN_DESCENDING
            material < 0.667f -> MelodicPattern.PENDULUM
            material < 0.833f -> MelodicPattern.SPIRAL
            else -> MelodicPattern.CONVERGING
        }
    }

    private fun advanceMVoice(pattern: MelodicPattern, state: EngineState, random: Random) {
        val scaleSize = state.scale.degrees.size.coerceAtLeast(1)
        val maxRange = scaleSize * 2

        when (pattern) {
            MelodicPattern.ASCENDING_STEPWISE -> {
                currentMDegree++
                if (currentMDegree > maxRange) {
                    currentMDegree = 0
                }
            }

            MelodicPattern.DESCENDING_STEPWISE -> {
                currentMDegree--
                if (currentMDegree < -maxRange) {
                    currentMDegree = 0
                }
            }

            MelodicPattern.ASCENDING_THEN_DESCENDING -> {
                currentMDegree += mDirection
                mStepCount++
                val halfPhrase = phraseLength / 2
                if (mStepCount >= halfPhrase) {
                    mDirection *= -1
                    mStepCount = 0
                }
            }

            MelodicPattern.PENDULUM -> {
                // Up 1, down 2, up 3, down 4, etc.
                currentMDegree += mDirection * mExpandDistance
                mPatternPhase++
                if (mPatternPhase % 2 == 0) {
                    mExpandDistance++
                }
                mDirection *= -1
                // Limit range
                currentMDegree = currentMDegree.coerceIn(-maxRange, maxRange)
            }

            MelodicPattern.SPIRAL -> {
                // Expanding spiral: +1, -2, +3, -4, ...
                currentMDegree = mCenterDegree + mDirection * mExpandDistance
                mDirection *= -1
                mPatternPhase++
                if (mPatternPhase % 2 == 0) {
                    mExpandDistance++
                }
                if (mExpandDistance > maxRange) {
                    mExpandDistance = 1
                    mCenterDegree = currentMDegree
                }
            }

            MelodicPattern.CONVERGING -> {
                // Start wide, converge to center
                if (mExpandDistance > 0) {
                    currentMDegree = mCenterDegree + mDirection * mExpandDistance
                    mDirection *= -1
                    mPatternPhase++
                    if (mPatternPhase % 2 == 0) {
                        mExpandDistance--
                    }
                } else {
                    // Reset
                    mExpandDistance = scaleSize
                    mCenterDegree = currentMDegree
                }
            }
        }
    }

    // --- T-voice computation ---

    private fun getCurrentTriadNotes(state: EngineState): List<Int> {
        // Get triad notes from current chord in the progression, or build from root
        val rootMidi = state.rootNote.midiValue
        val degrees = state.scale.degrees

        // Build triad: root, third, fifth from scale
        val triadDegrees = mutableListOf<Int>()
        if (degrees.size >= 5) {
            triadDegrees.add(degrees[0])  // Root
            triadDegrees.add(degrees[2])  // Third
            triadDegrees.add(degrees[4])  // Fifth
        } else if (degrees.size >= 3) {
            triadDegrees.add(degrees[0])
            triadDegrees.add(degrees[1])
            triadDegrees.add(degrees[2])
        } else {
            triadDegrees.add(0)
            triadDegrees.add(4)
            triadDegrees.add(7)
        }

        // Generate all triad notes across MIDI range
        val triadNotes = mutableListOf<Int>()
        for (octave in 0..9) {
            for (d in triadDegrees) {
                val note = rootMidi + octave * 12 + d
                if (note in 0..127) {
                    triadNotes.add(note)
                }
            }
        }
        return triadNotes.sorted()
    }

    private fun findTVoiceNote(mNote: Int, triadNotes: List<Int>, position: TPosition): Int {
        if (triadNotes.isEmpty()) return mNote

        return when (position) {
            TPosition.FIRST_SUPERIOR -> {
                // Nearest triad note >= mNote
                triadNotes.firstOrNull { it >= mNote } ?: triadNotes.last()
            }

            TPosition.FIRST_INFERIOR -> {
                // Nearest triad note <= mNote
                triadNotes.lastOrNull { it <= mNote } ?: triadNotes.first()
            }

            TPosition.SECOND_SUPERIOR -> {
                // Second nearest triad note above mNote
                val above = triadNotes.filter { it >= mNote }
                if (above.size >= 2) above[1] else above.lastOrNull() ?: triadNotes.last()
            }

            TPosition.SECOND_INFERIOR -> {
                // Second nearest triad note below mNote
                val below = triadNotes.filter { it <= mNote }
                if (below.size >= 2) below[below.size - 2] else below.firstOrNull() ?: triadNotes.first()
            }
        }
    }

    private fun getTVoicePositions(interaction: Float, morph: Float): List<TPosition> {
        // Interaction determines coupling pattern
        // Morph selects specific position combinations
        val positions = TPosition.values()
        val baseIndex = (morph * (positions.size - 1)).toInt().coerceIn(0, positions.size - 1)

        return when {
            interaction < 0.25f -> {
                // All T-voices use same position rule
                List(3) { positions[baseIndex] }
            }
            interaction < 0.5f -> {
                // Alternating patterns
                listOf(
                    positions[baseIndex],
                    positions[(baseIndex + 2) % positions.size],
                    positions[baseIndex]
                )
            }
            interaction < 0.75f -> {
                // Each voice different
                listOf(
                    positions[baseIndex],
                    positions[(baseIndex + 1) % positions.size],
                    positions[(baseIndex + 2) % positions.size]
                )
            }
            else -> {
                // Complementary: superior/inferior pairing
                listOf(
                    TPosition.FIRST_SUPERIOR,
                    TPosition.FIRST_INFERIOR,
                    TPosition.SECOND_SUPERIOR
                )
            }
        }
    }

    // --- Utility ---

    private fun scaleDegreeToMidi(
        degree: Int,
        rootNote: NoteName,
        scale: Scale,
        rangeLow: Int,
        rangeHigh: Int
    ): Int {
        val degrees = scale.degrees
        if (degrees.isEmpty()) return (rangeLow + rangeHigh) / 2

        val octaveSize = degrees.size
        val octave = if (degree >= 0) degree / octaveSize else (degree - octaveSize + 1) / octaveSize
        val degreeInScale = ((degree % octaveSize) + octaveSize) % octaveSize
        val semitoneOffset = degrees[degreeInScale]
        val baseMidi = rootNote.midiValue + semitoneOffset + (octave * 12)

        var midiNote = baseMidi
        while (midiNote < rangeLow) midiNote += 12
        while (midiNote > rangeHigh) midiNote -= 12

        return midiNote.coerceIn(rangeLow, rangeHigh)
    }

    private fun getTickInterval(density: Float): Int {
        return when {
            density > 0.7f -> RhythmUtils.EIGHTH
            density > 0.4f -> RhythmUtils.QUARTER
            else -> RhythmUtils.HALF
        }
    }
}
