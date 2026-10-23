package com.kaona.bach.engine

import kotlin.math.abs

/**
 * Qualidades de acorde da página SEQUENCE: DIATONIC (empilha terças dentro da escala)
 * mais 24 qualidades explícitas, que têm prioridade sobre a escala.
 * [intervals] são semitons a partir da fundamental do acorde.
 */
enum class ChordQuality(val label: String, val suffix: String, val intervals: IntArray?) {
    DIATONIC("DIATONIC", "", null),
    MAJ("MAJ", "", intArrayOf(0, 4, 7)),
    MIN("MIN", "m", intArrayOf(0, 3, 7)),
    DIM("DIM", "dim", intArrayOf(0, 3, 6)),
    AUG("AUG", "aug", intArrayOf(0, 4, 8)),
    SUS2("SUS2", "sus2", intArrayOf(0, 2, 7)),
    SUS4("SUS4", "sus4", intArrayOf(0, 5, 7)),
    POWER("5", "5", intArrayOf(0, 7)),
    SIXTH("6", "6", intArrayOf(0, 4, 7, 9)),
    MIN6("m6", "m6", intArrayOf(0, 3, 7, 9)),
    MAJ9("MAJ9", "maj9", intArrayOf(0, 4, 7, 11, 14)),
    MIN9("m9", "m9", intArrayOf(0, 3, 7, 10, 14)),
    DOM13("13", "13", intArrayOf(0, 4, 7, 10, 14, 17, 21)),
    ADD9("ADD9", "add9", intArrayOf(0, 4, 7, 14)),
    MADD9("mADD9", "madd9", intArrayOf(0, 3, 7, 14)),
    DOM7("7", "7", intArrayOf(0, 4, 7, 10)),
    MAJ7("MAJ7", "maj7", intArrayOf(0, 4, 7, 11)),
    MIN7("m7", "m7", intArrayOf(0, 3, 7, 10)),
    MINMAJ7("mMAJ7", "mMaj7", intArrayOf(0, 3, 7, 11)),
    HALFDIM("m7b5", "m7b5", intArrayOf(0, 3, 6, 10)),
    DIM7("DIM7", "dim7", intArrayOf(0, 3, 6, 9)),
    DOM9("9", "9", intArrayOf(0, 4, 7, 10, 14)),
    DOM11("11", "11", intArrayOf(0, 4, 7, 10, 14, 17)),
    MIN11("m11", "m11", intArrayOf(0, 3, 7, 10, 14, 17)),
    SUS47("7SUS4", "7sus4", intArrayOf(0, 5, 7, 10))
}

/**
 * Uma posição da progressão.
 * [root] é um grau em semitons a partir da nota raiz (0-11) ou [EMPTY_ROOT];
 * [length] indexa [LENGTH_BEATS]; [inversion] vai de 0 a 3, ou [VOICING] (automático).
 */
data class ChordStep(
    val root: Int = EMPTY_ROOT,
    val quality: ChordQuality = ChordQuality.DIATONIC,
    val length: Int = 2,
    val inversion: Int = 0
) {
    val isEmpty: Boolean get() = root < 0

    val lengthTicks: Int
        get() = LENGTH_BEATS[length.coerceIn(0, LENGTH_BEATS.size - 1)] * RhythmUtils.QUARTER

    companion object {
        const val EMPTY_ROOT = -1
        const val VOICING = 4

        /** Duração do acorde em tempos (compasso 4/4). */
        val LENGTH_BEATS = intArrayOf(1, 2, 4, 8, 12, 16, 32)
        val LENGTH_LABELS = arrayOf("1 BEAT", "2 BEATS", "1 BAR", "2 BARS", "3 BARS", "4 BARS", "8 BARS")

        /** Índice = root + 1 (posição 0 é EMPTY). */
        val ROOT_LABELS = arrayOf("-", "I", "bII", "II", "bIII", "III", "IV", "#IV", "V", "bVI", "VI", "bVII", "VII")
    }
}

/**
 * Progressão de até 64 posições. Fica ativa se a posição 1 tiver um acorde e [enabled] for true.
 * A leitura vai da posição 1 até a primeira posição vazia e então recomeça.
 */
data class HarmonicSequence(
    val steps: List<ChordStep> = List(SIZE) { ChordStep() },
    val enabled: Boolean = true
) {
    val isActive: Boolean get() = enabled && steps.isNotEmpty() && !steps[0].isEmpty

    fun withStep(index: Int, step: ChordStep): HarmonicSequence =
        copy(steps = steps.mapIndexed { i, s -> if (i == index) step else s })

    /** Texto compacto (para presets): "raiz.qualidade.duração.inversão;..." */
    fun encode(): String =
        steps.joinToString(";") { "${it.root}.${it.quality.ordinal}.${it.length}.${it.inversion}" }

    companion object {
        const val SIZE = 64

        fun decode(text: String, enabled: Boolean = true): HarmonicSequence {
            if (text.isBlank()) return HarmonicSequence(enabled = enabled)
            val parsed = text.split(";").take(SIZE).map { part ->
                try {
                    val f = part.split(".")
                    ChordStep(
                        root = f[0].toInt().coerceIn(-1, 11),
                        quality = ChordQuality.entries[f[1].toInt().coerceIn(0, ChordQuality.entries.size - 1)],
                        length = f[2].toInt().coerceIn(0, ChordStep.LENGTH_BEATS.size - 1),
                        inversion = f[3].toInt().coerceIn(0, ChordStep.VOICING)
                    )
                } catch (e: Exception) {
                    ChordStep()
                }
            }
            return HarmonicSequence(List(SIZE) { parsed.getOrElse(it) { ChordStep() } }, enabled)
        }
    }
}

data class ChordPosition(val index: Int, val startTick: Long)

/** O que a interface mostra na página HARMONY. */
data class ChordDisplay(val step: Int, val label: String)

object HarmonyEngine {
    private val NOTE_LABELS = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /** Acha o acorde que toca no [tick]; null se a progressão estiver vazia. */
    fun locate(seq: HarmonicSequence, tick: Long): ChordPosition? {
        var total = 0L
        var count = 0
        for (s in seq.steps) {
            if (s.isEmpty) break
            total += s.lengthTicks
            count++
        }
        if (total <= 0L) return null

        val t = tick % total
        var acc = 0L
        for (i in 0 until count) {
            val len = seq.steps[i].lengthTicks
            if (t < acc + len) return ChordPosition(i, tick - (t - acc))
            acc += len
        }
        return ChordPosition(0, tick - t)
    }

    /** Semitons de cada nota do acorde a partir da fundamental, na ordem do acorde. */
    fun intervals(step: ChordStep, scale: Scale): IntArray {
        step.quality.intervals?.let { return it }

        // DIATONIC: empilha terças dentro da escala, a partir do grau mais próximo da fundamental
        val deg = scale.intervals
        var idx = 0
        var best = Int.MAX_VALUE
        for (i in deg.indices) {
            val d = abs(deg[i] - step.root)
            if (d < best) {
                best = d
                idx = i
            }
        }
        fun at(k: Int): Int {
            val j = idx + k
            return deg[j % deg.size] + 12 * (j / deg.size) - deg[idx]
        }
        return intArrayOf(0, at(2), at(4))
    }

    /** Classes de altura (0-11) do acorde, com a fundamental primeiro. */
    fun pitchClasses(step: ChordStep, root: NoteName, scale: Scale): IntArray {
        val iv = intervals(step, scale)
        return IntArray(iv.size) { (root.midiBase + step.root + iv[it]).mod(12) }
    }

    fun label(step: ChordStep, root: NoteName, scale: Scale): String {
        val name = NOTE_LABELS[(root.midiBase + step.root).mod(12)]
        val suffix = if (step.quality == ChordQuality.DIATONIC) {
            val iv = intervals(step, scale)
            when {
                iv[1] == 3 && iv[2] == 7 -> "m"
                iv[1] == 3 && iv[2] == 6 -> "dim"
                iv[1] == 4 && iv[2] == 8 -> "aug"
                iv[1] == 4 && iv[2] == 7 -> ""
                else -> "*"
            }
        } else {
            step.quality.suffix
        }
        return name + suffix
    }
}

/**
 * Aplica a progressão às notas geradas pelas linguagens (a SEQUENCE só mexe nas alturas):
 *  - nota estrutural (cai num tempo ou dura pelo menos uma semínima): vai para uma nota do acorde,
 *    na oitava mais próxima da altura prevista;
 *  - demais notas (de passagem, ornamentos, subdivisões rápidas): vão para a nota mais próxima
 *    que pertença à escala ou ao acorde.
 * FUGUE e GROOVE ignoram a progressão.
 */
class HarmonyProcessor {
    private val lastPlayed = IntArray(4) { 60 }
    private var voicingKey = -1L
    private var voicingInv = 0

    fun reset() {
        lastPlayed.fill(60)
        voicingKey = -1L
        voicingInv = 0
    }

    /** Chamar quando a progressão muda, para refazer a escolha do VOICING. */
    fun invalidate() {
        voicingKey = -1L
    }

    fun apply(events: List<NoteEvent>, tick: Long, state: EngineState): List<NoteEvent> {
        val seq = state.sequence
        val bypass = state.language == CompositionalLanguage.FUGUE ||
            state.language == CompositionalLanguage.GROOVE

        if (events.isEmpty() || !seq.isActive || bypass) {
            remember(events)
            return events
        }

        val pos = HarmonyEngine.locate(seq, tick)
        if (pos == null) {
            remember(events)
            return events
        }

        val step = seq.steps[pos.index]
        val chord = HarmonyEngine.pitchClasses(step, state.rootNote, state.scale)

        // Notas permitidas nas passagens: escala + acorde
        val allowed = BooleanArray(12)
        for (iv in state.scale.intervals) allowed[(state.rootNote.midiBase + iv).mod(12)] = true
        for (pc in chord) allowed[pc] = true

        val inv = resolveInversion(step, pos, chord)
        val out = ArrayList<NoteEvent>(events.size)

        for (e in events) {
            val track = e.track.coerceIn(0, 3)
            val structural = tick % RhythmUtils.QUARTER == 0L || e.durationTicks >= RhythmUtils.QUARTER
            val note = if (structural) {
                closestPitch(chord[(inv + track) % chord.size], e.midiNote)
            } else {
                nearestAllowed(e.midiNote, allowed)
            }
            lastPlayed[track] = note
            out.add(if (note == e.midiNote) e else e.copy(midiNote = note))
        }
        return out
    }

    private fun remember(events: List<NoteEvent>) {
        for (e in events) lastPlayed[e.track.coerceIn(0, 3)] = e.midiNote
    }

    /** A inversão decide qual nota do acorde fica no baixo (trilha 1); as outras seguem em ordem. */
    private fun resolveInversion(step: ChordStep, pos: ChordPosition, chord: IntArray): Int {
        val n = chord.size
        if (step.inversion != ChordStep.VOICING) {
            return if (step.inversion >= n) 0 else step.inversion
        }

        // VOICING: a cada troca de acorde, escolhe a inversão de menor movimento total das 4 vozes
        if (pos.startTick == voicingKey) return voicingInv.coerceAtMost(n - 1)

        var bestInv = 0
        var bestCost = Int.MAX_VALUE
        for (cand in 0 until minOf(n, 4)) {
            var cost = 0
            for (t in 0..3) {
                cost += abs(closestPitch(chord[(cand + t) % n], lastPlayed[t]) - lastPlayed[t])
            }
            if (cost < bestCost) {
                bestCost = cost
                bestInv = cand
            }
        }
        voicingKey = pos.startTick
        voicingInv = bestInv
        return bestInv
    }

    private fun closestPitch(pc: Int, ref: Int): Int {
        val diff = (pc - ref).mod(12)
        val d = if (diff > 6) diff - 12 else diff
        var n = ref + d
        if (n > 127) n -= 12
        if (n < 0) n += 12
        return n
    }

    private fun nearestAllowed(note: Int, allowed: BooleanArray): Int {
        for (d in 0..6) {
            val down = note - d
            val up = note + d
            if (down in 0..127 && allowed[down.mod(12)]) return down
            if (up in 0..127 && allowed[up.mod(12)]) return up
        }
        return note
    }
}
