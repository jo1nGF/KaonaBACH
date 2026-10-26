package com.kaona.bach.engine

import java.util.Random

/**
 * TreeEngine: sistemas de Lindenmayer (L-systems) transformados em linhas melódicas.
 * Parte de um axioma e substitui todos os símbolos ao mesmo tempo, em cada passo, segundo as regras
 * da "espécie" escolhida em MATERIAL. A estrutura resultante é lida como um percurso musical:
 *   F = nota (um passo adiante)   + e - = vira para cima ou para baixo
 *   [ = guarda a posição (ramo)   ] = volta à posição guardada     | = inverte a direção
 * As oito espécies são determinísticas: com os mesmos ajustes, a mesma árvore e a mesma linha.
 *
 *  - MEMORY:      acima de 60%, o percurso desacelera um passo.
 *  - GROWTH:      profundidade de reescrita, de 2 a 6 passos (cada mudança reconstrói a árvore);
 *                 acima de 78%, todas as notas ficam em semicolcheias.
 *  - MORPH:       acima de 50%, os intervalos são invertidos em uma voz sim, outra não.
 *  - DENSITY:     proporção de eventos tocados, de 40 a 98%; acima de 60%, o percurso avança uma
 *                 nota a cada passo em vez de uma a cada dois passos.
 *  - TENSION:     acima de 55%, um semitom em cerca de 16% das notas; aumenta a velocity.
 *  - INTERACTION: distância de leitura entre as vozes (no máximo, as quatro leem o mesmo ponto);
 *                 também define a abertura dos registros.
 */
class TreeEngine : CompositionEngine {

    private class Species(
        val name: String,
        val axiom: String,
        val rules: Map<Char, String>,
        val registerBias: Int = 0,
        val startDirection: Int = 1
    )

    private class Turtle(var degree: Int, var direction: Int) {
        fun copyFrom(other: Turtle) {
            degree = other.degree
            direction = other.direction
        }
    }

    private val species = listOf(
        // CANOPY: regra muito ramificada, copa densa com muitos retornos de ramos
        Species("CANOPY", "F", mapOf('F' to "F[+F]F[-F]F")),
        // TENDRIL: o crescimento alterna extensões e pequenos ramos ascendentes
        Species("TENDRIL", "F", mapOf('F' to "FF[+F]")),
        // ZIGZAG: a linha muda de direção com regularidade, figura angular
        Species("ZIGZAG", "F", mapOf('F' to "F+F-F")),
        // FERN: sistema assimétrico, ramos deslocados
        Species("FERN", "F", mapOf('F' to "F[+F]F[-F[+F]]")),
        // CORAL: bifurcações repetidas e muitas duplicações
        Species("CORAL", "F", mapOf('F' to "FF[+F][-F]")),
        // VINE: laço e ramificações laterais
        Species("VINE", "F", mapOf('F' to "F+F[-F]-F")),
        // FORK: percursos simétricos
        Species("FORK", "F", mapOf('F' to "F[+F][-F]")),
        // ROOTS: ramos descendentes, favorecendo o registro grave
        Species("ROOTS", "F", mapOf('F' to "F[-F]-F[-F]"), registerBias = -12, startDirection = -1)
    )

    private var currentString = ""
    private var builtKey = -1
    private var stepCounter = 0
    private var lastStepTick = -1L
    private var readPositions = IntArray(4)
    private val turtles = Array(4) { Turtle(0, 1) }
    private val stacks: Array<MutableList<Turtle>> = Array(4) { mutableListOf() }

    override fun generate(tick: Long, state: EngineState, random: Random): List<NoteEvent> {
        if (tick % RhythmUtils.SIXTEENTH != 0L) return emptyList()
        if (tick == lastStepTick) return emptyList()
        lastStepTick = tick

        val p = state.params
        val speciesIndex = MaterialCatalog.index(CompositionalLanguage.TREE, p.material).coerceIn(0, species.size - 1)
        val depth = 2 + (p.growth * 4f).toInt()
        val key = speciesIndex * 10 + depth
        if (key != builtKey) {
            rebuild(species[speciesIndex], depth)
            builtKey = key
        }
        if (currentString.isEmpty()) return emptyList()

        // MEMORY (> 60%) desacelera um passo; DENSITY (> 60%) avança a cada passo, senão a cada dois
        val interval = (if (p.density > 0.6f) 1 else 2) + (if (p.memory > 0.6f) 1 else 0)
        val advance = stepCounter % interval == 0
        stepCounter++
        if (!advance) return emptyList()

        val length = currentString.length
        // INTERACTION: distância de leitura entre as vozes
        val readGap = ((1f - p.interaction) * 24f).toInt()
        for (v in 1 until 4) readPositions[v] = (readPositions[0] + v * readGap) % length

        val sp = species[speciesIndex]
        val playProbability = 0.40f + 0.58f * p.density
        val spacing = 8 + ((1f - p.interaction) * 8f).toInt() // abertura dos registros
        val events = ArrayList<NoteEvent>(4)

        for (v in 0 until 4) {
            val turtle = turtles[v]
            val stack = stacks[v]
            var pos = readPositions[v]
            var processed = 0
            var produced = false

            while (!produced && processed < 6) {
                when (currentString[pos % length]) {
                    'F' -> {
                        turtle.degree += turtle.direction
                        produced = true
                    }
                    '+' -> {
                        turtle.degree += 1
                        turtle.direction = 1
                    }
                    '-' -> {
                        turtle.degree -= 1
                        turtle.direction = -1
                    }
                    '[' -> stack.add(Turtle(turtle.degree, turtle.direction))
                    ']' -> {
                        if (stack.isNotEmpty()) turtle.copyFrom(stack.removeAt(stack.size - 1))
                    }
                    '|' -> turtle.direction = -turtle.direction
                }
                pos++
                processed++
            }
            readPositions[v] = pos % length

            if (!produced) continue
            if (state.trackMutes.getOrElse(v) { false }) continue
            if (hash(stepCounter, v, 1) >= playProbability) continue

            val size = state.scale.intervals.size.coerceAtLeast(1)
            if (turtle.degree > size * 3) turtle.degree -= size * 2
            if (turtle.degree < -size * 2) turtle.degree += size * 2

            // MORPH: inverte os intervalos de uma voz sim, outra não
            var degree = turtle.degree
            if (p.morph > 0.5f && v % 2 == 1) degree = -degree

            val base = 72 - v * spacing + sp.registerBias
            var midi = degreeToMidi(degree, state, base)
            if (p.tension > 0.55f && hash(stepCounter, v, 7) < 0.16f) midi += 1

            val velocity = RhythmUtils.humanizeVelocity(
                (70 + (p.tension * 20f).toInt() - v * 5).coerceIn(35, 120), p.humanize, random
            )
            val duration = if (p.growth > 0.78f) RhythmUtils.SIXTEENTH else interval * RhythmUtils.SIXTEENTH

            events.add(
                NoteEvent(
                    track = v,
                    midiNote = midi.coerceIn(0, 127),
                    velocity = velocity,
                    durationTicks = duration,
                    startTick = tick,
                    modValue = (64 + (p.morph * 30f - 15f)).toInt().coerceIn(0, 127)
                )
            )
        }
        return events
    }

    override fun reset() {
        currentString = ""
        builtKey = -1
        stepCounter = 0
        lastStepTick = -1L
        readPositions = IntArray(4)
        for (v in 0 until 4) {
            turtles[v].degree = 0
            turtles[v].direction = 1
            stacks[v].clear()
        }
    }

    /** Aplica as regras [depth] vezes (com limite de tamanho) e reposiciona as vozes. */
    private fun rebuild(sp: Species, depth: Int) {
        var s = sp.axiom
        repeat(depth) {
            val next = StringBuilder()
            for (ch in s) {
                next.append(sp.rules[ch] ?: ch.toString())
                if (next.length > MAX_LENGTH) break
            }
            s = if (next.length > MAX_LENGTH) next.substring(0, MAX_LENGTH) else next.toString()
        }
        currentString = s

        readPositions = IntArray(4)
        for (v in 0 until 4) {
            turtles[v].degree = v * 2 // as vozes começam escalonadas
            turtles[v].direction = if (v % 2 == 0) sp.startDirection else -sp.startDirection
            stacks[v].clear()
        }
    }

    private fun degreeToMidi(degree: Int, state: EngineState, base: Int): Int {
        val degrees = state.scale.intervals
        val size = degrees.size
        val octave = Math.floorDiv(degree, size)
        val idx = Math.floorMod(degree, size)
        var midi = base + state.rootNote.midiBase + degrees[idx] + octave * 12
        while (midi < 12) midi += 12
        while (midi > 115) midi -= 12
        return midi
    }

    /** Aleatoriedade determinística: os mesmos ajustes produzem a mesma execução. */
    private fun hash(a: Int, b: Int, c: Int): Float {
        var h = a * 374761393 + b * 668265263 + c * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0x7FFFFFFF) / 2147483647f
    }

    companion object {
        private const val MAX_LENGTH = 600
    }
}
