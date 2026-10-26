package com.kaona.bach.engine

import java.util.Random

/**
 * TintinnabuliEngine: uma voz M (melodia, trilha 1) anda por graus conjuntos e as T-voices
 * (trilhas 2 a 4) tocam só notas do acorde (tríade), sempre acima ou abaixo da nota da voz M,
 * sem formar uníssono (sistema de Arvo Pärt, segundo as indicações de Paul Hillier: lado + posição).
 *
 * MATERIAL escolhe o caminho da voz M, sempre em graus conjuntos da escala:
 *   ARC UP/DOWN, MIRROR UP/DOWN, WAVE UP/DOWN e ALINA UP/DOWN (o MIRROR em tempo dobrado).
 *
 *  - MEMORY:      lentidão do processo; cada nota M dura de 1 a 4 tempos.
 *  - GROWTH:      amplitude do arco (alcance coberto pela voz M).
 *  - INTERACTION: até a metade do curso, a trilha 2 fica acima e a 3 abaixo; depois as duas
 *                 alternam de lado. Também define a densidade de ataques das T-voices.
 *  - TENSION:     afasta as T-voices dentro da tríade (1ª, 2ª e 3ª posição); nenhuma nota estranha.
 *  - DENSITY:     rarefaz a textura até o silêncio (número de T-voices e probabilidade das notas).
 *  - MORPH:       acima de 50% inverte o processo e leva a trilha 3 para a posição superior.
 * Com a SEQUENCE ativa, as T-voices usam as notas do acorde e a tônica local da voz M acompanha a
 * raiz do acorde.
 */
class TintinnabuliEngine : CompositionEngine {

    private var pathPos = 0
    private var sideFlip = false
    private var lastGeneratedTick = -1L
    private var generationCount = 0

    override fun generate(tick: Long, state: EngineState, random: Random): List<NoteEvent> {
        if (tick % RhythmUtils.QUARTER != 0L) return emptyList()
        if (tick == lastGeneratedTick) return emptyList()
        lastGeneratedTick = tick

        val p = state.params
        val events = ArrayList<NoteEvent>(4)

        // MEMORY: cada nota M vive de 1 a 4 tempos
        val hold = 1 + (p.memory * 3f).toInt()
        val newNote = generationCount % hold == 0
        if (newNote && generationCount > 0) {
            pathPos++
            sideFlip = !sideFlip
        }

        val kind = MaterialCatalog.index(CompositionalLanguage.TINTINNABULI, p.material)
        val amplitude = 2 + (p.growth * 5f).toInt()
        val cycle = cycleLength(kind, amplitude)
        val position = if (p.morph > 0.5f) cycle - 1 - (pathPos % cycle) else pathPos
        val center = chordCenterDegree(state, tick)

        val mDegree = center + pathOffset(kind, position, amplitude)
        val mMidi = scaleDegreeToMidi(mDegree, state.rootNote, state.scale, 48, 84)

        val noteProbability = 0.25f + 0.75f * p.density
        val duration = RhythmUtils.QUARTER * hold

        // --- Voz M (trilha 1): o caminho segue evoluindo mesmo quando a nota não toca ---
        if (newNote && !state.trackMutes.getOrElse(0) { false } && random.nextFloat() < noteProbability) {
            events.add(
                NoteEvent(
                    track = 0,
                    midiNote = mMidi.coerceIn(0, 127),
                    velocity = RhythmUtils.humanizeVelocity(
                        (75 + (p.tension * 15).toInt()).coerceIn(40, 110), p.humanize, random
                    ),
                    durationTicks = duration,
                    startTick = tick,
                    modValue = 64
                )
            )
        }

        // --- T-voices (trilhas 2 a 4): notas da tríade acima ou abaixo da nota M ---
        val triad = getCurrentTriadNotes(state, tick)
        val tCount = when {
            p.density < 0.34f -> 1
            p.density < 0.67f -> 2
            else -> 3
        }
        val baseRank = when {
            p.tension < 0.34f -> 0
            p.tension < 0.67f -> 1
            else -> 2
        }

        for (v in 1..tCount) {
            if (state.trackMutes.getOrElse(v) { false }) continue

            // Ataques: a cada nota nova; nas notas mantidas, com a densidade dada por INTERACTION
            val attack = newNote || random.nextFloat() < p.interaction * 0.5f
            if (!attack) continue
            if (random.nextFloat() >= 0.5f + 0.5f * p.density) continue

            val aboveBase = v % 2 == 1
            var above = if (p.interaction > 0.5f && sideFlip) !aboveBase else aboveBase
            if (v == 2 && p.morph > 0.5f) above = true

            val rank = (baseRank + if (v == 3) 1 else 0).coerceAtMost(2)
            val tMidi = findTNote(mMidi, triad, above, rank)

            events.add(
                NoteEvent(
                    track = v,
                    midiNote = tMidi.coerceIn(0, 127),
                    velocity = RhythmUtils.humanizeVelocity(
                        (65 + (p.tension * 10).toInt() - v * 3).coerceIn(35, 100), p.humanize, random
                    ),
                    durationTicks = duration,
                    startTick = tick,
                    modValue = (50 + (p.morph * 28)).toInt().coerceIn(0, 127)
                )
            )
        }

        generationCount++
        return events
    }

    override fun reset() {
        pathPos = 0
        sideFlip = false
        lastGeneratedTick = -1L
        generationCount = 0
    }

    // --- Caminhos da voz M (tudo em graus conjuntos da escala) ---

    private fun cycleLength(kind: Int, a: Int): Int = when (kind) {
        0, 1 -> 2 * a
        2, 3, 4, 5 -> 4 * a
        else -> 8 * a
    }

    private fun mirror(pos: Int, a: Int): Int {
        val t = pos % (4 * a)
        return if (t <= a) t else if (t <= 3 * a) 2 * a - t else t - 4 * a
    }

    /**
     * 0 ARC UP, 1 ARC DOWN, 2 MIRROR UP, 3 MIRROR DOWN, 4 WAVE UP, 5 WAVE DOWN,
     * 6 ALINA UP, 7 ALINA DOWN. Os pares (UP/DOWN) são espelhos um do outro.
     */
    private fun pathOffset(kind: Int, pos: Int, a: Int): Int {
        val sign = if (kind % 2 == 0) 1 else -1
        val value = when (kind) {
            0, 1 -> {
                val t = pos % (2 * a)
                if (t <= a) t else 2 * a - t
            }
            2, 3 -> mirror(pos, a)
            4, 5 -> {
                val t = pos % (4 * a)
                if (t <= 2 * a) -a + t else a - (t - 2 * a)
            }
            else -> mirror(pos / 2, a) // ALINA: cada grau dura duas notas do processo
        }
        return sign * value
    }

    /** Com a SEQUENCE ativa, a tônica local da voz M segue a raiz do acorde (dentro da escala). */
    private fun chordCenterDegree(state: EngineState, tick: Long): Int {
        val seq = state.sequence
        if (!seq.isActive) return 0
        val pos = HarmonyEngine.locate(seq, tick - state.sequenceOrigin, state.music.meter) ?: return 0
        val chordRoot = seq.steps[pos.index].root
        val degrees = state.scale.degrees
        var idx = 0
        var best = Int.MAX_VALUE
        for (i in degrees.indices) {
            val d = Math.abs(degrees[i] - chordRoot)
            if (d < best) {
                best = d
                idx = i
            }
        }
        return idx
    }

    // --- Tríade e T-voices ---

    /**
     * As T-voices usam as notas do acorde atual da SEQUENCE; sem progressão ativa, usam a tríade
     * central (graus 1, 3 e 5 da escala).
     */
    private fun getCurrentTriadNotes(state: EngineState, tick: Long): List<Int> {
        val seq = state.sequence
        if (seq.isActive) {
            val pos = HarmonyEngine.locate(seq, tick - state.sequenceOrigin, state.music.meter)
            if (pos != null) {
                val pcs = HarmonyEngine.pitchClasses(seq.steps[pos.index], state.rootNote, state.scale)
                val chordNotes = ArrayList<Int>()
                for (octave in 0..9) {
                    for (pc in pcs) {
                        val n = octave * 12 + pc
                        if (n in 0..127) chordNotes.add(n)
                    }
                }
                return chordNotes.sorted()
            }
        }
        return getScaleTriadNotes(state)
    }

    private fun getScaleTriadNotes(state: EngineState): List<Int> {
        val rootMidi = state.rootNote.midiValue
        val degrees = state.scale.degrees

        val triadDegrees = mutableListOf<Int>()
        if (degrees.size >= 5) {
            triadDegrees.add(degrees[0])
            triadDegrees.add(degrees[2])
            triadDegrees.add(degrees[4])
        } else if (degrees.size >= 3) {
            triadDegrees.add(degrees[0])
            triadDegrees.add(degrees[1])
            triadDegrees.add(degrees[2])
        } else {
            triadDegrees.add(0)
            triadDegrees.add(4)
            triadDegrees.add(7)
        }

        val triadNotes = mutableListOf<Int>()
        for (octave in 0..9) {
            for (d in triadDegrees) {
                val note = rootMidi + octave * 12 + d
                if (note in 0..127) triadNotes.add(note)
            }
        }
        return triadNotes.sorted()
    }

    /** Nota da tríade acima ou abaixo da nota M (nunca em uníssono), na posição [rank] (0 = mais próxima). */
    private fun findTNote(mNote: Int, triad: List<Int>, above: Boolean, rank: Int): Int {
        if (triad.isEmpty()) return mNote
        return if (above) {
            val candidates = triad.filter { it > mNote }
            if (candidates.isEmpty()) triad.last() else candidates[rank.coerceAtMost(candidates.size - 1)]
        } else {
            val candidates = triad.filter { it < mNote }.reversed()
            if (candidates.isEmpty()) triad.first() else candidates[rank.coerceAtMost(candidates.size - 1)]
        }
    }

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
        val octave = Math.floorDiv(degree, octaveSize)
        val degreeInScale = Math.floorMod(degree, octaveSize)
        var midiNote = rootNote.midiValue + degrees[degreeInScale] + octave * 12
        while (midiNote < rangeLow) midiNote += 12
        while (midiNote > rangeHigh) midiNote -= 12
        return midiNote.coerceIn(rangeLow, rangeHigh)
    }
}
