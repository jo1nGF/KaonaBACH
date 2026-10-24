package com.kaona.bach.engine

import java.util.Random

/** DURATION: VARIABLE deixa cada linguagem decidir; os demais impõem um valor único às 4 vozes. */
enum class DurationMode(val label: String, val ticks: Int) {
    VARIABLE("VARIABLE", 0),
    D32("1/32", 12),
    D16("1/16", 24),
    D8("1/8", 48),
    D4("1/4", 96),
    D2("1/2", 192),
    WHOLE("WHOLE", 384)
}

/** VELOCITY: nível geral de dinâmica das quatro vozes. */
enum class VelocityMode(val label: String) {
    FULL("FULL"),
    MODULATED("MODULATED"),
    HARD("HARD"),
    SOFT("SOFT"),
    COOL("COOL"),
    PROGRESS("PROGRESS")
}

/**
 * METER: fórmula de compasso. [barTicks] é o tamanho do compasso (96 ticks = semínima),
 * [beatTicks] o tempo (semínima pontuada nos compostos) e [stresses] as posições dos acentos
 * (agrupamentos usuais: 2+2+3 em 7/8, tercinas em 6/8, 9/8 e 12/8, 3+2 em 5/4).
 */
enum class Meter(
    val label: String,
    val barTicks: Int,
    val beatTicks: Int,
    val stresses: IntArray,
    val compound: Boolean
) {
    M24("2/4", 192, 96, intArrayOf(0, 96), false),
    M34("3/4", 288, 96, intArrayOf(0, 96, 192), false),
    M44("4/4", 384, 96, intArrayOf(0, 96, 192, 288), false),
    M54("5/4", 480, 96, intArrayOf(0, 288), false),
    M68("6/8", 288, 144, intArrayOf(0, 144), true),
    M78("7/8", 336, 48, intArrayOf(0, 96, 192), false),
    M98("9/8", 432, 144, intArrayOf(0, 144, 288), true),
    M128("12/8", 576, 144, intArrayOf(0, 144, 288, 432), true);

    /** 3 = início do compasso, 2 = início de grupo, 1 = tempo, 0 = colcheia, -1 = subdivisão. */
    fun accentLevel(posInBar: Int): Int {
        if (posInBar == 0) return 3
        if (stresses.contains(posInBar)) return 2
        if (posInBar % beatTicks == 0) return 1
        if (posInBar % 48 == 0) return 0
        return -1
    }
}

/**
 * Parâmetros da página MUSIC comuns a todas as linguagens.
 * [phraseBars] = 0 significa INFINITE. [rootOctave] vai de 1 a 6 (C1 a C6).
 */
data class MusicSettings(
    val duration: DurationMode = DurationMode.VARIABLE,
    val velocity: VelocityMode = VelocityMode.MODULATED,
    val meter: Meter = Meter.M44,
    val swing: Float = 0f,
    val phraseBars: Int = 0,
    val rootOctave: Int = 4
) {
    companion object {
        val PHRASE_OPTIONS = intArrayOf(1, 2, 4, 8, 16, 32, 0)
    }
}

/** Nomes dos Materials de cada linguagem, como o motor realmente os interpreta. */
object MaterialCatalog {
    private val FUGUE = listOf("ARCH", "FIFTHS", "SCALAR", "SYNCOPATED", "TURN", "PEDAL", "WEDGE", "CHORALE")

    private val CANON_INTERVALS = listOf("8VE", "5TH", "4TH", "M3", "m3", "2ND", "UNI")
    private val CANON = listOf("DIATONIC", "TRIADIC", "SINUOUS", "DISJUNCT").flatMap { shape ->
        CANON_INTERVALS.map { "$shape $it" }
    }

    private val PROBA = listOf(
        "PENTATONIC", "DORIAN", "MIXOLYDIAN", "BLUES", "CHROMATIC", "WHOLE TONE", "JAPANESE", "HARM MINOR"
    )
    private val TINTINNABULI = listOf(
        "ASC STEPWISE", "DESC STEPWISE", "ARC", "PENDULUM", "SPIRAL", "CONVERGING"
    )
    private val MINIMAL = (1..8).map { "CELL $it" }
    private val CELLULAR = (1..254).map { "R%03d".format(it) }
    private val TREE = listOf(
        "TREE", "KOCH", "DRAGON", "SIERPINSKI", "PLANT", "ALGAE", "BINARY", "PENROSE", "HILBERT", "LADDER"
    )
    private val FRACTAL = listOf("FIBONACCI", "THUE-MORSE", "SIERPINSKI", "LOGISTIC")
    private val MOTIF = (1..32).map { "REPERTOIRE %02d".format(it) }
    private val HYBRID = listOf(
        "FUGUE", "CANON", "PROBA", "TINTINA", "MINIMAL", "CELLULAR", "TREE", "FRACTAL", "MOTIF"
    )
    private val GROOVE = (1..64).map { "MODEL %02d".format(it) }

    fun names(language: CompositionalLanguage): List<String> = when (language) {
        CompositionalLanguage.FUGUE -> FUGUE
        CompositionalLanguage.CANON -> CANON
        CompositionalLanguage.PROBA -> PROBA
        CompositionalLanguage.TINTINNABULI -> TINTINNABULI
        CompositionalLanguage.MINIMAL -> MINIMAL
        CompositionalLanguage.CELLULAR -> CELLULAR
        CompositionalLanguage.TREE -> TREE
        CompositionalLanguage.FRACTAL -> FRACTAL
        CompositionalLanguage.MOTIF -> MOTIF
        CompositionalLanguage.HYBRID -> HYBRID
        CompositionalLanguage.GROOVE, CompositionalLanguage.GROOVE_MIX -> GROOVE
    }

    /**
     * Índice do Material para o valor 0-1 do knob. Tintinnabuli e Fractal escolhem por faixas
     * iguais (limiares); as demais linguagens espalham o knob entre o primeiro e o último.
     */
    fun index(language: CompositionalLanguage, material: Float): Int {
        val n = names(language).size
        val m = material.coerceIn(0f, 1f)
        return when (language) {
            CompositionalLanguage.TINTINNABULI, CompositionalLanguage.FRACTAL ->
                (m * n).toInt().coerceIn(0, n - 1)
            else -> (m * (n - 1)).toInt().coerceIn(0, n - 1)
        }
    }

    /** Centro do Material mais próximo: evita deixar o knob sobre a fronteira entre dois Materials. */
    fun center(language: CompositionalLanguage, material: Float): Float {
        val n = names(language).size
        val idx = index(language, material)
        return when (language) {
            CompositionalLanguage.TINTINNABULI, CompositionalLanguage.FRACTAL -> (idx + 0.5f) / n
            else -> if (n <= 1) 0.5f else ((idx + 0.5f) / (n - 1)).coerceAtMost(1f)
        }
    }

    fun name(language: CompositionalLanguage, material: Float): String {
        val list = names(language)
        return list[index(language, material).coerceIn(0, list.size - 1)]
    }
}

/** Estágios musicais aplicados a todas as linguagens. */
object MusicShaper {

    /** Deslocamento de oitavas do ROOT (C1 a C6); FUGUE exige no mínimo C2. */
    fun octaveShift(state: EngineState): Int {
        var octave = state.music.rootOctave.coerceIn(1, 6)
        var rootMidi = (octave + 1) * 12 + state.rootNote.midiBase
        if (state.language == CompositionalLanguage.FUGUE) {
            while (rootMidi < 36) {
                rootMidi += 12
                octave++
            }
        }
        return octave - 4
    }

    fun transpose(events: List<NoteEvent>, state: EngineState): List<NoteEvent> {
        val shift = octaveShift(state) * 12
        if (shift == 0 || events.isEmpty()) return events
        val drums = state.language == CompositionalLanguage.GROOVE
        return events.map { e ->
            if (drums && e.track < 3) {
                e // LOW, BACKBEAT e TIME mantêm as alturas fixas da bateria
            } else {
                var n = e.midiNote + shift
                while (n > 127) n -= 12
                while (n < 0) n += 12
                e.copy(midiNote = n)
            }
        }
    }

    /** DURATION fixa, articulada pela posição métrica. */
    fun applyDuration(events: List<NoteEvent>, tick: Long, state: EngineState): List<NoteEvent> {
        val mode = state.music.duration
        if (mode == DurationMode.VARIABLE || events.isEmpty()) return events

        val meter = state.music.meter
        val pos = (tick % meter.barTicks).toInt()
        var loss = when (meter.accentLevel(pos)) {
            3 -> 0f
            2 -> 0.03f
            1 -> 0.06f
            0 -> 0.12f
            else -> 0.18f
        }
        if (mode.ticks >= 192) loss = minOf(loss, 0.06f) // mínima ou mais: nunca encurta mais de 6%
        val duration = (mode.ticks * (1f - loss)).toInt().coerceAtLeast(1)
        return events.map { it.copy(durationTicks = duration) }
    }

    /** VELOCITY: modo geral + hierarquia métrica. */
    fun applyVelocity(events: List<NoteEvent>, tick: Long, state: EngineState): List<NoteEvent> {
        if (events.isEmpty()) return events
        val mode = state.music.velocity
        val meter = state.music.meter
        val pos = (tick % meter.barTicks).toInt()
        val accent = when (meter.accentLevel(pos)) {
            3 -> 10
            2 -> 6
            1 -> 3
            0 -> 0
            else -> -5
        }
        val bar = (tick / meter.barTicks).toInt()

        return events.map { e ->
            val v = e.velocity
            val shaped = when (mode) {
                VelocityMode.FULL -> 127
                VelocityMode.MODULATED -> v + accent
                VelocityMode.HARD -> (70 + v * 0.45f).toInt() + accent
                VelocityMode.SOFT -> (20 + v * 0.35f).toInt() + accent / 2
                VelocityMode.COOL -> (40 + v * 0.45f).toInt() + accent / 2
                VelocityMode.PROGRESS -> (v * (0.65f + 0.5f * ((bar % 8) / 7f))).toInt() + accent
            }
            e.copy(velocity = shaped.coerceIn(1, 127))
        }
    }

    /**
     * SWING sobre a colcheia: a contratempo atrasa e o tempo seguinte fica no relógio.
     * 100% chega a 2:1 (ternário); nos compostos só a subdivisão em semicolcheias respira, com
     * metade da amplitude. A curva é suavizada nas pontas.
     */
    fun swingDelay(tick: Long, state: EngineState): Int {
        val swing = state.music.swing
        if (swing <= 0f) return 0
        val s = swing * swing * (3f - 2f * swing)
        val meter = state.music.meter
        val pos = (tick % meter.barTicks).toInt()
        return if (!meter.compound) {
            when (pos % 96) {
                24, 72 -> (8f * s).toInt()
                48 -> (16f * s).toInt()
                else -> 0
            }
        } else {
            if (pos % 48 == 24) (4f * s).toInt() else 0
        }
    }
}

/**
 * HUMANIZE como gesto de improvisação: no máximo um gesto por compasso, escolhido entre
 * equilíbrio do compasso, nota de passagem, frase curta, nota não tocada e ruptura de um tempo.
 * No GROOVE também desloca levemente as vozes de apoio (microtiming por papel).
 */
class HumanizeProcessor(private val random: Random) {
    private var gesture = 0
    private var consumed = false
    private var ruptureStart = 0
    private var ruptureEnd = 0

    fun reset() {
        gesture = 0
        consumed = false
    }

    fun process(
        events: List<NoteEvent>,
        tick: Long,
        state: EngineState,
        defer: (NoteEvent, Int) -> Unit
    ): List<NoteEvent> {
        val humanize = state.params.humanize
        val meter = state.music.meter
        val pos = (tick % meter.barTicks).toInt()

        if (pos == 0) {
            consumed = false
            gesture = 0
            if (humanize > 0f && random.nextFloat() < humanize * 0.8f) {
                gesture = 1 + random.nextInt(5)
                if (gesture == 5) {
                    val beats = maxOf(1, meter.barTicks / meter.beatTicks)
                    ruptureStart = random.nextInt(beats) * meter.beatTicks
                    ruptureEnd = ruptureStart + meter.beatTicks
                }
            }
        }

        var list = events
        val drums = state.language == CompositionalLanguage.GROOVE ||
            state.language == CompositionalLanguage.GROOVE_MIX
        if (drums && humanize > 0f && list.isNotEmpty()) {
            val maxDelay = (humanize * 6f).toInt()
            if (maxDelay > 0) {
                val now = ArrayList<NoteEvent>(list.size)
                for (e in list) {
                    val d = if (e.track == 0) 0 else random.nextInt(maxDelay + 1)
                    if (d > 0) defer(e, d) else now.add(e)
                }
                list = now
            }
        }

        if (gesture == 0 || list.isEmpty()) return list

        return when (gesture) {
            1 -> { // equilíbrio do compasso: acentua o início e alivia o resto
                val delta = if (pos == 0) 12 else -6
                list.map { it.copy(velocity = (it.velocity + delta).coerceIn(1, 127)) }
            }
            2 -> { // nota de passagem: uma nota curta vai para o vizinho da escala
                if (consumed) return list
                val i = list.indexOfFirst { it.durationTicks < RhythmUtils.QUARTER }
                if (i < 0) return list
                consumed = true
                val e = list[i]
                val out = ArrayList(list)
                out[i] = e.copy(midiNote = neighbor(e.midiNote, if (random.nextBoolean()) 1 else -1, state))
                out
            }
            3 -> { // frase curta: uma nota longa se divide em duas
                if (consumed) return list
                val i = list.indexOfFirst { it.durationTicks >= RhythmUtils.EIGHTH }
                if (i < 0) return list
                consumed = true
                val e = list[i]
                val half = e.durationTicks / 2
                val out = ArrayList(list)
                out[i] = e.copy(durationTicks = half)
                defer(
                    e.copy(
                        midiNote = neighbor(e.midiNote, 1, state),
                        durationTicks = e.durationTicks - half
                    ),
                    half
                )
                out
            }
            4 -> { // nota não tocada
                if (consumed) return list
                consumed = true
                val out = ArrayList(list)
                out.removeAt(random.nextInt(out.size))
                out
            }
            else -> { // ruptura de um tempo
                if (pos in ruptureStart until ruptureEnd) emptyList() else list
            }
        }
    }

    private fun neighbor(note: Int, dir: Int, state: EngineState): Int {
        val root = state.rootNote.midiBase
        var n = note
        for (i in 0 until 6) {
            n += dir
            if (n < 0 || n > 127) return note
            if (state.scale.intervals.contains((n - root).mod(12))) return n
        }
        return (note + dir).coerceIn(0, 127)
    }
}
