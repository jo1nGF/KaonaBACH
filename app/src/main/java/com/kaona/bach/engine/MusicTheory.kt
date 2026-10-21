package com.kaona.bach.engine

/**
 * Core music theory constants and utilities for the B.A.C.H. generative composer.
 * Provides scales, intervals, chord types, and voice-leading rules.
 */

enum class NoteName(val midiBase: Int) {
    C(0), Cs(1), D(2), Ds(3), E(4), F(5),
    Fs(6), G(7), Gs(8), A(9), As(10), B(11);

    fun toMidi(octave: Int): Int = midiBase + (octave + 1) * 12

    companion object {
        fun fromMidi(midi: Int): NoteName = entries[midi % 12]
        fun octaveFromMidi(midi: Int): Int = (midi / 12) - 1
    }
}

enum class Scale(val intervals: IntArray, val displayName: String) {
    MAJOR(intArrayOf(0, 2, 4, 5, 7, 9, 11), "Major"),
    MINOR(intArrayOf(0, 2, 3, 5, 7, 8, 10), "Minor"),
    HARMONIC_MINOR(intArrayOf(0, 2, 3, 5, 7, 8, 11), "Harm. Minor"),
    MELODIC_MINOR(intArrayOf(0, 2, 3, 5, 7, 9, 11), "Mel. Minor"),
    DORIAN(intArrayOf(0, 2, 3, 5, 7, 9, 10), "Dorian"),
    PHRYGIAN(intArrayOf(0, 1, 3, 5, 7, 8, 10), "Phrygian"),
    LYDIAN(intArrayOf(0, 2, 4, 6, 7, 9, 11), "Lydian"),
    MIXOLYDIAN(intArrayOf(0, 2, 4, 5, 7, 9, 10), "Mixolydian"),
    AEOLIAN(intArrayOf(0, 2, 3, 5, 7, 8, 10), "Aeolian"),
    LOCRIAN(intArrayOf(0, 1, 3, 5, 6, 8, 10), "Locrian"),
    PENTATONIC_MAJOR(intArrayOf(0, 2, 4, 7, 9), "Pent. Major"),
    PENTATONIC_MINOR(intArrayOf(0, 3, 5, 7, 10), "Pent. Minor"),
    BLUES(intArrayOf(0, 3, 5, 6, 7, 10), "Blues"),
    WHOLE_TONE(intArrayOf(0, 2, 4, 6, 8, 10), "Whole Tone"),
    CHROMATIC(intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), "Chromatic"),
    OCTATONIC_HW(intArrayOf(0, 1, 3, 4, 6, 7, 9, 10), "Octatonic HW"),
    OCTATONIC_WH(intArrayOf(0, 2, 3, 5, 6, 8, 9, 11), "Octatonic WH");

    fun getNotesInKey(root: NoteName, octaveStart: Int = 2, octaveEnd: Int = 7): List<Int> {
        val notes = mutableListOf<Int>()
        for (oct in octaveStart..octaveEnd) {
            for (interval in intervals) {
                val midi = root.midiBase + oct * 12 + interval
                if (midi in 0..127) notes.add(midi)
            }
        }
        return notes
    }

    fun quantize(midiNote: Int, root: NoteName): Int {
        val noteInOctave = (midiNote - root.midiBase).mod(12)
        val octave = (midiNote - root.midiBase) / 12
        var closest = intervals[0]
        var minDist = Int.MAX_VALUE
        for (interval in intervals) {
            val dist = kotlin.math.abs(noteInOctave - interval)
            if (dist < minDist) {
                minDist = dist
                closest = interval
            }
        }
        return (root.midiBase + octave * 12 + closest).coerceIn(0, 127)
    }
}

enum class ChordType(val intervals: IntArray, val displayName: String) {
    MAJOR(intArrayOf(0, 4, 7), "Maj"),
    MINOR(intArrayOf(0, 3, 7), "Min"),
    DIMINISHED(intArrayOf(0, 3, 6), "Dim"),
    AUGMENTED(intArrayOf(0, 4, 8), "Aug"),
    MAJOR7(intArrayOf(0, 4, 7, 11), "Maj7"),
    MINOR7(intArrayOf(0, 3, 7, 10), "Min7"),
    DOMINANT7(intArrayOf(0, 4, 7, 10), "Dom7"),
    SUS2(intArrayOf(0, 2, 7), "Sus2"),
    SUS4(intArrayOf(0, 5, 7), "Sus4");

    fun getMidiNotes(root: Int): IntArray = intervals.map { (root + it).coerceIn(0, 127) }.toIntArray()
}

data class HarmonicProgression(
    val name: String = "Default",
    val chords: List<Pair<NoteName, ChordType>> = listOf(
        NoteName.C to ChordType.MAJOR,
        NoteName.F to ChordType.MAJOR,
        NoteName.G to ChordType.MAJOR,
        NoteName.C to ChordType.MAJOR
    ),
    val barsPerChord: Int = 4
) {
    fun getChordAtBar(bar: Int): Pair<NoteName, ChordType> {
        val index = (bar / barsPerChord) % chords.size
        return chords[index]
    }
}

/**
 * Voice leading utility functions for counterpoint and harmony.
 */
object VoiceLeading {
    /**
     * Check if two intervals form parallel fifths or octaves (forbidden in strict counterpoint)
     */
    fun isParallelFifthOrOctave(
        prevNote1: Int, currNote1: Int,
        prevNote2: Int, currNote2: Int
    ): Boolean {
        val prevInterval = kotlin.math.abs(prevNote1 - prevNote2) % 12
        val currInterval = kotlin.math.abs(currNote1 - currNote2) % 12
        val isParallel = (currNote1 - prevNote1) * (currNote2 - prevNote2) > 0
        return isParallel && (prevInterval == 7 && currInterval == 7 || prevInterval == 0 && currInterval == 0)
    }

    /**
     * Find the nearest chord tone to a given note
     */
    fun nearestChordTone(note: Int, chordRoot: NoteName, chordType: ChordType): Int {
        val chordNotes = mutableListOf<Int>()
        for (oct in 0..10) {
            for (interval in chordType.intervals) {
                val n = chordRoot.midiBase + oct * 12 + interval
                if (n in 0..127) chordNotes.add(n)
            }
        }
        return chordNotes.minByOrNull { kotlin.math.abs(it - note) } ?: note
    }

    /**
     * Resolve a note by step motion (smooth voice leading)
     */
    fun resolveByStep(note: Int, target: Int, scale: Scale, root: NoteName): Int {
        val scaleNotes = scale.getNotesInKey(root)
        return if (note < target) {
            scaleNotes.firstOrNull { it > note } ?: note
        } else {
            scaleNotes.lastOrNull { it < note } ?: note
        }
    }

    /**
     * Get contrary motion note for a reference movement
     */
    fun contraryMotion(referenceMove: Int, note: Int, scale: Scale, root: NoteName): Int {
        val direction = if (referenceMove > 0) -1 else 1
        val scaleNotes = scale.getNotesInKey(root)
        val idx = scaleNotes.indexOf(scaleNotes.minByOrNull { kotlin.math.abs(it - note) })
        val newIdx = (idx + direction).coerceIn(0, scaleNotes.size - 1)
        return scaleNotes[newIdx]
    }
}

/**
 * Rhythmic pattern utilities
 */
object RhythmUtils {
    /** Standard note durations in ticks (based on 96 PPQN) */
    const val WHOLE = 384
    const val HALF = 192
    const val QUARTER = 96
    const val EIGHTH = 48
    const val SIXTEENTH = 24
    const val TRIPLET_QUARTER = 64
    const val TRIPLET_EIGHTH = 32
    const val DOTTED_QUARTER = 144
    const val DOTTED_EIGHTH = 72

    val STANDARD_DURATIONS = intArrayOf(WHOLE, HALF, DOTTED_QUARTER, QUARTER, DOTTED_EIGHTH, EIGHTH, SIXTEENTH)

    fun humanizeTiming(tickPosition: Int, amount: Float, random: java.util.Random): Int {
        val maxShift = (SIXTEENTH * amount).toInt()
        return tickPosition + random.nextInt(maxShift * 2 + 1) - maxShift
    }

    fun humanizeVelocity(velocity: Int, amount: Float, random: java.util.Random): Int {
        val maxShift = (30 * amount).toInt()
        return (velocity + random.nextInt(maxShift * 2 + 1) - maxShift).coerceIn(1, 127)
    }
}
