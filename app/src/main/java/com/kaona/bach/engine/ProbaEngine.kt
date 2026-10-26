package com.kaona.bach.engine

import java.util.Random
import kotlin.math.abs

/**
 * ProbaEngine: o acaso dirigido. Cada voz faz uma caminhada melódica; os passos são sorteados a
 * partir de uma matriz de estilo escolhida pelo MATERIAL (conjunto de alturas e pesos ou uma
 * preferência de intervalo).
 *
 *  - MATERIAL:    TONAL, ATONAL, PENTATONIC, BLUES, STEPWISE, FOURTHS, OCTATONIC, PEDAL.
 *  - MEMORY:      inércia: probabilidade de continuar na direção do movimento anterior.
 *  - GROWTH:      deriva lenta do centro tonal (até 4 semitons para cada lado); acima de 72%, as
 *                 durações dobram a cada dois compassos.
 *  - MORPH:       acima de 50%, a caminhada pode inverter de repente (até 35% de chance).
 *  - DENSITY:     probabilidade de cada voz emitir uma nota a cada passo; também age na velocity.
 *  - TENSION:     amplitude dos saltos (1 a 4 vezes, no máximo 12 semitons); acima de 52%, a tabela
 *                 de durações passa do tocar calmo para o tenso.
 *  - INTERACTION: probabilidade de uma voz adotar a direção da vizinha.
 */
class ProbaEngine : CompositionEngine {

    private class Vocabulary(val intervals: IntArray, val weights: FloatArray)

    // Ordem dos Materials do manual
    private val vocabularies = listOf(
        // TONAL: favorece os graus estáveis da tonalidade
        Vocabulary(intArrayOf(0, 2, 4, 5, 7, 9, 11), floatArrayOf(1.6f, 0.8f, 1.2f, 0.9f, 1.4f, 0.9f, 0.6f)),
        // ATONAL: distribui as alturas livremente
        Vocabulary(IntArray(12) { it }, FloatArray(12) { 1f }),
        // PENTATONIC: cinco tons abertos
        Vocabulary(intArrayOf(0, 2, 4, 7, 9), floatArrayOf(1.5f, 1f, 1.2f, 1.3f, 1f)),
        // BLUES: graus e inflexões do blues
        Vocabulary(intArrayOf(0, 3, 5, 6, 7, 10), floatArrayOf(1.5f, 1.2f, 1f, 0.8f, 1.3f, 1f)),
        // STEPWISE: movimento por graus conjuntos (a preferência de intervalo está no passo)
        Vocabulary(intArrayOf(0, 2, 4, 5, 7, 9, 11), FloatArray(7) { 1f }),
        // FOURTHS: contornos em quartas
        Vocabulary(intArrayOf(0, 3, 5, 8, 10), floatArrayOf(1.5f, 1f, 1.3f, 1f, 1.1f)),
        // OCTATONIC: tons e semitons alternados
        Vocabulary(intArrayOf(0, 2, 3, 5, 6, 8, 9, 11), FloatArray(8) { 1f }),
        // PEDAL: uma nota-pivô volta com frequência
        Vocabulary(intArrayOf(0, 2, 4, 5, 7, 9, 11), floatArrayOf(2.5f, 0.7f, 1f, 0.8f, 1.3f, 0.7f, 0.6f))
    )

    // Registro de cada voz (nota base) e posição da caminhada, em semitons relativos a ele
    private val voiceBase = intArrayOf(60, 48, 36, 24)
    private val relative = IntArray(4) { 0 }
    private val direction = IntArray(4) { 1 }
    private var drift = 0
    private var lastGeneratedTick = -1L
    private var generationCount = 0

    override fun generate(tick: Long, state: EngineState, random: Random): List<NoteEvent> {
        if (tick % RhythmUtils.EIGHTH != 0L) return emptyList()
        if (tick == lastGeneratedTick) return emptyList()
        lastGeneratedTick = tick

        val p = state.params
        val materialIndex = MaterialCatalog.index(CompositionalLanguage.PROBA, p.material)
        val vocab = vocabularies[materialIndex.coerceIn(0, vocabularies.size - 1)]
        val stepwise = materialIndex == 4
        val fourths = materialIndex == 5
        val pedal = materialIndex == 7

        // GROWTH: deriva lenta do centro tonal, de -4 a +4 semitons
        val driftPeriod = (40 - (p.growth * 34f).toInt()).coerceAtLeast(4)
        if (generationCount > 0 && generationCount % driftPeriod == 0) {
            drift = (drift + random.nextInt(3) - 1).coerceIn(-4, 4)
        }

        val emitProbability = 0.2f + 0.8f * p.density
        val inertia = 0.35f + 0.6f * p.memory
        val reverseProbability = if (p.morph > 0.5f) (p.morph - 0.5f) * 0.7f else 0f
        val leapFactor = 1f + p.tension * 3f
        val maxStep = minOf(12, (3f * leapFactor).toInt())

        val tense = p.tension > 0.52f
        val durations = if (tense) {
            intArrayOf(RhythmUtils.SIXTEENTH, RhythmUtils.EIGHTH, RhythmUtils.EIGHTH, RhythmUtils.QUARTER)
        } else {
            intArrayOf(RhythmUtils.QUARTER, RhythmUtils.QUARTER, RhythmUtils.DOTTED_QUARTER, RhythmUtils.EIGHTH)
        }
        val oddMeasure = (tick / state.music.meter.barTicks) % 2L == 1L

        val events = ArrayList<NoteEvent>(4)

        for (v in 0 until 4) {
            if (state.trackMutes.getOrElse(v) { false }) continue
            if (random.nextFloat() >= emitProbability) continue

            // Direção: inércia, inversão brusca e contágio da voz vizinha
            var dir = direction[v]
            if (random.nextFloat() > inertia) dir = -dir
            if (random.nextFloat() < reverseProbability) dir = -dir
            if (v > 0 && random.nextFloat() < p.interaction * 0.8f) dir = direction[v - 1]

            // Tamanho do passo
            val magnitude = when {
                fourths && random.nextFloat() < 0.8f -> 5
                stepwise -> 1 + random.nextInt(minOf(maxStep, 2 + (p.tension * 2f).toInt()).coerceAtLeast(1))
                else -> {
                    val r = random.nextFloat()
                    (1 + r * r * (maxStep - 1)).toInt().coerceIn(1, maxStep)
                }
            }

            var target = relative[v] + dir * magnitude
            if (target > 14 || target < -14) {
                dir = -dir
                target = relative[v] + dir * magnitude
            }
            if (pedal && random.nextFloat() < 0.3f) target = 0 // volta ao pivô

            val snapped = snapToVocabulary(target, vocab, random)
            relative[v] = snapped.coerceIn(-14, 14)
            direction[v] = dir

            val midi = state.scale.quantize(
                voiceBase[v] + state.rootNote.midiBase + relative[v] + drift,
                state.rootNote
            )

            var duration = durations[(generationCount + v) % durations.size]
            if (p.growth > 0.72f && oddMeasure) duration *= 2

            val velocity = RhythmUtils.humanizeVelocity(
                (62 + (p.density * 20).toInt() + (p.tension * 10).toInt() - v * 4).coerceIn(35, 118),
                p.humanize,
                random
            )

            events.add(
                NoteEvent(
                    track = v,
                    midiNote = midi.coerceIn(0, 127),
                    velocity = velocity,
                    durationTicks = duration,
                    startTick = tick,
                    modValue = (32 + (p.tension * 64)).toInt().coerceIn(0, 127)
                )
            )
        }

        generationCount++
        return events
    }

    override fun reset() {
        for (v in 0 until 4) {
            relative[v] = 0
            direction[v] = 1
        }
        drift = 0
        lastGeneratedTick = -1L
        generationCount = 0
    }

    /** Sorteia, perto do alvo, uma altura do vocabulário; os pesos e a distância decidem. */
    private fun snapToVocabulary(target: Int, vocab: Vocabulary, random: Random): Int {
        val candidates = ArrayList<Int>()
        val weights = ArrayList<Float>()
        for (c in target - 3..target + 3) {
            val pc = Math.floorMod(c - drift, 12)
            val idx = vocab.intervals.indexOf(pc)
            if (idx >= 0) {
                candidates.add(c)
                weights.add(vocab.weights[idx] / (1f + abs(c - target) * 0.9f))
            }
        }
        if (candidates.isEmpty()) return target

        var roll = random.nextFloat() * weights.sum()
        for (i in candidates.indices) {
            roll -= weights[i]
            if (roll <= 0f) return candidates[i]
        }
        return candidates.last()
    }
}
