package com.kaona.bach.engine

import java.util.Random
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * CanonEngine: uma voz líder toca uma melodia e as outras a imitam com atraso e intervalo.
 *
 * MATERIAL combina uma de 4 formas melódicas (DIATONIC, TRIADIC, SINUOUS, DISJUNCT) com um de 7
 * intervalos de imitação (8VE, 5TH, 4TH, M3, m3, 2ND, UNI): 28 combinações.
 * A voz líder fica na trilha 4 (a mais aguda) e as imitações descem pelas trilhas 3, 2 e 1.
 *
 *  - MEMORY:      vida da melodia líder; uma nova é construída a cada 1 a 16 compassos.
 *  - GROWTH:      tamanho da melodia (6 a 16 eventos). Acima de 60%, uma voz sim, outra não toca a
 *                 cópia em aumentação; acima de 72%, parte dos valores rítmicos é dobrada.
 *  - MORPH:       acima de 42%, uma voz sim, outra não passa a movimento contrário; acima de 72%,
 *                 as duas primeiras imitações leem a melodia em retrógrado.
 *  - DENSITY:     proporção dos eventos da voz líder realmente tocados (55 a 98%). Afeta a velocity.
 *  - TENSION:     até 16% das notas deslocadas por um semitom. Aumenta a velocity.
 *  - INTERACTION: distância entre as entradas, do cânone largo ao fechado. Acima de 80%, as notas
 *                 são encurtadas pela metade.
 */
class CanonEngine : CompositionEngine {

    // Registros por trilha: 0 = grave ... 3 = aguda (líder)
    private val voiceRanges = listOf(24..48, 36..60, 48..72, 60..84)

    // Intervalos de imitação em semitons: 8VE, 5TH, 4TH, M3, m3, 2ND, UNI
    private val intervalSemitones = intArrayOf(12, 7, 5, 4, 3, 2, 0)

    private var melody = IntArray(0)
    private var maskSeed = 0
    private var currentShape = -1
    private var nextRebuildTick = 0L
    private var step = 0L
    private var lastTick = -1L
    private var initialized = false

    override fun generate(tick: Long, state: EngineState, random: Random): List<NoteEvent> {
        if (tick % RhythmUtils.EIGHTH != 0L) return emptyList()
        if (tick == lastTick) return emptyList()
        lastTick = tick

        val p = state.params
        val matIdx = MaterialCatalog.index(CompositionalLanguage.CANON, p.material)
        val shape = matIdx / 7
        val interval = intervalSemitones[matIdx % 7]
        val desiredLength = (6 + (p.growth * 10).roundToInt()).coerceIn(6, 16)

        val cycleBoundary = melody.isNotEmpty() && step % melody.size == 0L
        if (!initialized || tick >= nextRebuildTick ||
            (cycleBoundary && (shape != currentShape || desiredLength != melody.size))
        ) {
            rebuild(tick, state, random, shape, desiredLength)
            initialized = true
        }

        val length = melody.size
        if (length == 0) return emptyList()

        val delaySteps = 1 + ((1f - p.interaction) * 7f).toInt()   // 1 a 8 passos entre as entradas
        val playProbability = 0.55f + 0.43f * p.density           // 55 a 98%
        val augment = p.growth > 0.6f
        val doubleSome = p.growth > 0.72f
        val halve = p.interaction > 0.8f
        val chromaticProb = p.tension * 0.16f

        val events = ArrayList<NoteEvent>(4)

        for (k in 0..3) {                       // k = 0 é a líder; 1 a 3 são as imitações
            val track = 3 - k
            if (state.trackMutes.getOrElse(track) { false }) continue

            val entry = k * delaySteps
            if (step < entry) continue
            val rel = (step - entry).toInt()

            val slow = augment && k > 0 && k % 2 == 0      // cópia em aumentação (metade da velocidade)
            if (slow && rel % 2 != 0) continue
            val readPos = (if (slow) rel / 2 else rel) % length

            val retrograde = p.morph > 0.72f && (k == 1 || k == 2)
            val pos = if (retrograde) length - 1 - readPos else readPos

            // Pausas da líder também são imitadas; DENSITY age em tempo real
            if (((pos * 37 + maskSeed) % 100) / 100f >= playProbability) continue

            var degree = melody[pos]
            if (p.morph > 0.42f && k % 2 == 1) {            // movimento contrário
                degree = melody[0] - (degree - melody[0])
            }

            val range = voiceRanges[track]
            var midi = degreeToMidi(degree, state) - k * interval
            midi = state.scale.quantize(midi, state.rootNote)
            if (random.nextFloat() < chromaticProb) {
                midi += if (random.nextBoolean()) 1 else -1
            }
            while (midi < range.first) midi += 12
            while (midi > range.last) midi -= 12

            val baseVelocity = (68 + (p.density * 15).toInt() + (p.tension * 20).toInt() - k * 4).coerceIn(35, 120)
            val velocity = RhythmUtils.humanizeVelocity(baseVelocity, p.humanize, random)

            var duration = RhythmUtils.EIGHTH * (if (slow) 2 else 1)
            if (doubleSome && pos % 3 == 2) duration *= 2
            if (halve) duration = maxOf(RhythmUtils.SIXTEENTH, duration / 2)

            events.add(
                NoteEvent(
                    track = track,
                    midiNote = midi.coerceIn(0, 127),
                    velocity = velocity,
                    durationTicks = duration,
                    startTick = tick,
                    modValue = (64 + (p.morph * 30 - 15)).toInt().coerceIn(0, 127)
                )
            )
        }

        step++
        return events
    }

    override fun reset() {
        melody = IntArray(0)
        maskSeed = 0
        currentShape = -1
        nextRebuildTick = 0L
        step = 0L
        lastTick = -1L
        initialized = false
    }

    /** Constrói a melodia líder (em graus da escala) de acordo com a forma escolhida. */
    private fun rebuild(tick: Long, state: EngineState, random: Random, shape: Int, length: Int) {
        val size = state.scale.intervals.size
        val d = IntArray(length)

        when (shape) {
            0 -> { // DIATONIC: movimento por graus conjuntos, calmo
                var c = 0
                var dir = 1
                for (i in 0 until length) {
                    d[i] = c
                    c += if (random.nextFloat() < 0.12f) 0 else dir
                    if (c >= size) dir = -1 else if (c <= -2) dir = 1 else if (random.nextFloat() < 0.18f) dir = -dir
                }
            }
            1 -> { // TRIADIC: arpejo consonante (graus 1, 3, 5 e oitava)
                val tri = intArrayOf(0, 2, 4, 7, 4, 2)
                val start = random.nextInt(tri.size)
                for (i in 0 until length) d[i] = tri[(i + start) % tri.size]
            }
            2 -> { // SINUOUS: linha ondulante com pequenos movimentos contrários
                val phase = random.nextFloat() * 6.28f
                for (i in 0 until length) d[i] = (2.5 * sin(i * 0.9 + phase)).roundToInt()
            }
            else -> { // DISJUNCT: contorno largo, feito de saltos maiores
                var c = 0
                for (i in 0 until length) {
                    d[i] = c
                    val leap = 3 + random.nextInt(3)
                    c += if (i % 2 == 0) leap else -leap + random.nextInt(2)
                    if (c > size + 3) c -= leap
                    if (c < -size) c += leap
                }
            }
        }

        melody = d
        maskSeed = random.nextInt(1000)
        currentShape = shape

        // MEMORY: a melodia vive de 1 a 16 compassos
        val lifeBars = 1 + (state.params.memory * 15).toInt()
        nextRebuildTick = tick + lifeBars.toLong() * state.music.meter.barTicks
    }

    /** Grau da escala -> nota MIDI sem dobrar para um registro (a dobra é feita por voz). */
    private fun degreeToMidi(degree: Int, state: EngineState): Int {
        val degrees = state.scale.intervals
        val size = degrees.size
        val octave = Math.floorDiv(degree, size)
        val idx = Math.floorMod(degree, size)
        return 60 + state.rootNote.midiBase + degrees[idx] + octave * 12
    }
}
