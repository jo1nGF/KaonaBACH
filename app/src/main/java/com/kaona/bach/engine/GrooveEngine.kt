package com.kaona.bach.engine

import java.util.Random

/**
 * GrooveEngine: bateria generativa de quatro papéis (LOW, BACKBEAT, TIME e PERC) com 64 modelos rítmicos.
 *
 * Cada MATERIAL é uma estrutura que diz quando cada papel intervém. Os modelos ficam numa grade fina
 * (semicolcheias de 24 ticks, tercinas de 32 ticks ou colcheias de 48 ticks), que equivale ao
 * resolução de 24 PPQN do módulo, e seguem o compasso escolhido em MUSIC:
 *  - modelo mais curto que o compasso: recomeça a cada compasso (e se repete dentro dele);
 *  - modelo mais longo que o compasso (ou cíclico): continua de um compasso ao seguinte, como uma
 *    clave de 16 posições em 2/4, que ocupa dois compassos.
 *
 *  - MEMORY:      por quantos compassos as variações se mantêm (1 a 8);
 *  - GROWTH:      entrada das camadas ao longo da frase de 4 compassos e viradas no último compasso;
 *  - MORPH:       gira, desloca ou inverte algumas células (LOW é a âncora e nunca gira);
 *  - DENSITY:     do esqueleto (só os golpes fortes) às notas fantasma e às subdivisões;
 *  - TENSION:     antecipa os golpes dos tempos (síncopes) e aumenta a dinâmica;
 *  - INTERACTION: da sobreposição ao encaixe e à chamada e resposta;
 *  - HUMANIZE:    velocity e microtiming por papel (tratados no estágio de humanização).
 * Tudo é determinístico: os mesmos ajustes produzem a mesma execução.
 */
class GrooveEngine : CompositionEngine {

    private class Model(
        val name: String,
        val stepTicks: Int,
        val layers: Array<String>, // LOW, BACKBEAT, TIME, PERC
        val timeNote: Int,
        val backNote: Int,
        val ternary: Boolean
    ) {
        val cycle: Int get() = layers[0].length
    }

    private val models: List<Model> = ROWS.map { row ->
        val f = row.split("|")
        Model(
            name = f[0],
            stepTicks = f[1].toInt(),
            layers = arrayOf(f[2], f[3], f[4], f[5]),
            timeNote = f[6].toInt(),
            backNote = f[7].toInt(),
            ternary = f[0] in TERNARY
        )
    }

    private val tomDegrees = intArrayOf(0, 4, 2, 5, 1, 3)

    /** Modelos escritos em grade de tercinas: o SWING não os deforma uma segunda vez. */
    fun isTernary(material: Float): Boolean =
        models[MaterialCatalog.index(CompositionalLanguage.GROOVE, material).coerceIn(0, models.size - 1)].ternary

    override fun generate(tick: Long, state: EngineState, random: Random): List<NoteEvent> = render(tick, state)

    /** GROOVE MIX usa exatamente a mesma grade; as alturas são trocadas pelas do parceiro. */
    fun generateMix(tick: Long, state: EngineState, random: Random): List<NoteEvent> = render(tick, state)

    override fun reset() {
        // Sem estado interno: tudo é função do tick e dos ajustes.
    }

    private fun render(tick: Long, state: EngineState): List<NoteEvent> {
        val p = state.params
        val model = models[MaterialCatalog.index(CompositionalLanguage.GROOVE, p.material).coerceIn(0, models.size - 1)]
        val meter = state.music.meter
        val barTicks = meter.barTicks

        val rel = (tick % barTicks).toInt()
        if (rel % model.stepTicks != 0) return emptyList()

        val barIndex = (tick / barTicks).toInt()
        val stepInBar = rel / model.stepTicks
        val barSteps = (barTicks + model.stepTicks - 1) / model.stepTicks
        val cycle = model.cycle
        val pos = if (cycle <= barSteps) {
            stepInBar % cycle // recomeça a cada compasso
        } else {
            (((barIndex.toLong() * barSteps) + stepInBar) % cycle).toInt() // continua entre compassos
        }

        val stepsPerBeat = maxOf(1, meter.beatTicks / model.stepTicks)
        val persist = 1 + (p.memory * 7f).toInt()
        val group = barIndex / persist
        val phraseBar = barIndex % 4

        val chars = CharArray(4)
        for (layer in 0 until 4) {
            chars[layer] = layerChar(model, layer, pos, stepsPerBeat, state, group, barIndex)
        }

        // INTERACTION: sobreposição, encaixe (interlocking) ou chamada e resposta
        val interaction = p.interaction
        if (interaction >= 0.67f) {
            if (chars[3] != '.' && (chars[0] != '.' || chars[1] != '.')) chars[3] = '.'
            if (pos >= cycle / 2 && chars[2] == 'x') chars[2] = 'o'
        } else if (interaction >= 0.34f) {
            if (chars[0] != '.') {
                if (chars[2] == 'x') chars[2] = 'o'
                chars[3] = '.'
            }
            if (chars[1] != '.') chars[3] = '.'
        }

        // GROWTH: as camadas entram ao longo da frase de 4 compassos (LOW, TIME, BACKBEAT, PERC)
        val entryOrder = intArrayOf(0, 2, 1, 3)
        for (layer in 0 until 4) {
            val entryBar = (entryOrder[layer] * p.growth + 0.5f).toInt()
            if (phraseBar < entryBar) chars[layer] = '.'
        }

        // GROWTH: virada no último compasso da frase, na última batida
        if (p.growth > 0.4f && phraseBar == 3 && stepInBar >= barSteps - stepsPerBeat) {
            for (layer in intArrayOf(1, 3)) {
                if (chars[layer] == '.' && hash(group, layer, pos, 3) < p.growth * 0.6f) chars[layer] = 'x'
            }
        }

        val events = ArrayList<NoteEvent>(4)
        for (layer in 0 until 4) {
            val c = chars[layer]
            if (c == '.') continue
            if (state.trackMutes.getOrElse(layer) { false }) continue

            val baseVelocity = when (c) {
                'X' -> 104 + (p.tension * 14f).toInt()
                'x' -> 82
                else -> 46
            }
            val roleOffset = when (layer) {
                0 -> 6
                1 -> 2
                2 -> -8
                else -> -2
            }
            val note = when (layer) {
                0 -> 36
                1 -> model.backNote
                2 -> model.timeNote
                else -> tunedPercNote(state, pos)
            }
            val duration = maxOf(12, model.stepTicks * 3 / 4)

            events.add(
                NoteEvent(
                    track = layer,
                    midiNote = note.coerceIn(0, 127),
                    velocity = (baseVelocity + roleOffset).coerceIn(1, 127),
                    durationTicks = duration,
                    startTick = tick,
                    modValue = (40 + layer * 20).coerceIn(0, 127)
                )
            )
        }
        return events
    }

    /** Símbolo do padrão de uma camada na posição, depois de MORPH, TENSION e DENSITY. */
    private fun layerChar(
        model: Model,
        layer: Int,
        pos: Int,
        stepsPerBeat: Int,
        state: EngineState,
        group: Int,
        barIndex: Int
    ): Char {
        val s = model.layers[layer]
        val n = s.length
        val p = state.params
        var idx = pos

        // MORPH: gira ou desloca algumas células; LOW é a âncora e nunca gira
        val rotation = (p.morph * n).toInt()
        when (layer) {
            3 -> if (p.morph >= 0.2f) idx = Math.floorMod(idx - rotation, n)
            2 -> if (p.morph >= 0.45f) idx = Math.floorMod(idx - rotation / 2, n)
            1 -> if (p.morph >= 0.7f) idx = Math.floorMod(idx - 1, n)
        }
        if (model.name == "ROTATING" && layer >= 2) idx = Math.floorMod(idx - barIndex * 2, n)

        var c = s[idx]
        if (layer == 3 && p.morph >= 0.9f) c = if (c == '.') 'o' else '.'

        // TENSION: antecipa golpes dos tempos (síncope) e busca posições intermediárias
        val syncProbability = p.tension * 0.45f
        if ((c == 'X' || c == 'x') && idx % stepsPerBeat == 0) {
            if (hash(group, layer, idx, 1) < syncProbability) c = '.'
        } else if (c == '.') {
            val next = (idx + 1) % n
            val nc = s[next]
            if ((nc == 'X' || nc == 'x') && next % stepsPerBeat == 0 &&
                hash(group, layer, next, 1) < syncProbability
            ) {
                c = 'x'
            }
        }

        // DENSITY: esqueleto -> notas normais -> fantasmas -> subdivisões
        val density = p.density
        if (c == 'x' && density < 0.25f) c = '.'
        if (c == 'o' && density < 0.5f) c = '.'
        if (c == '.' && density >= 0.75f && layer >= 2 &&
            hash(group, layer, idx, 2) < (density - 0.75f) * 2f
        ) {
            c = 'o'
        }
        return c
    }

    /** PERC segue ROOT e SCALE: percussão afinada (toms). */
    private fun tunedPercNote(state: EngineState, pos: Int): Int {
        val degrees = state.scale.intervals
        val d = tomDegrees[pos % tomDegrees.size] % degrees.size
        return 48 + state.rootNote.midiBase + degrees[d] + (if (pos % 2 == 0) 0 else 12)
    }

    /** Aleatoriedade determinística (sem estado): mesmos ajustes, mesma execução. */
    private fun hash(a: Int, b: Int, c: Int, d: Int): Float {
        var h = a * 374761393 + b * 668265263 + c * 1274126177 + d * 2147483629
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0x7FFFFFFF) / 2147483647f
    }

    companion object {
        /** NOME|ticks por passo|LOW|BACKBEAT|TIME|PERC|nota do TIME|nota do BACKBEAT */
        private val ROWS = listOf(
        "MODEL 01|24|X.........x.....|....X.......X...|x.x.x.x.x.x.x.x.|...............o|42|38",
        "SWING RIDE|32|o..o..o..o..|...x.....x..|x..x.ox..x.o|..o.....o...|51|44",
        "BOSSA NOVA|24|X.....x.X.....x.|X..x..x...x..x..|x.x.x.x.x.x.x.x.|..o...o...o...o.|42|37",
        "AKSAK 5|24|X...x.....|....X.x...|x.x.x.x.x.|..o...o.o.|42|38",
        "MODEL 05|24|X...X...X...X...|....X.......X...|..x...x...x...x.|o.o.o.o.o.o.o.o.|42|38",
        "BEBOP|32|o...........|...x.....x..|x..x.xx..x.x|o....o...o..|51|44",
        "SAMBA|24|x...X...x...X...|x..x.xx.x..x.xx.|XoxoXoxoXoxoXoxo|X.X.X..X.X.X..X.|42|37",
        "AKSAK 7|24|X.......x.....|....X...x.x...|x.x.x.x.x.x.x.|..o...o...o...|42|38",
        "MODEL 09|24|X.......X.......|........X.......|x.x.x.x.x.x.x.x.|..............o.|42|38",
        "JAZZ WALTZ|32|o........|...x..x..|x..x.ox..|..o...o..|51|44",
        "SAMBA REGGAE|24|X..x....X..x....|........X.......|x.x.x.x.x.x.x.x.|o.oxo.oxo.oxo.ox|42|37",
        "AKSAK 9|24|X...x...x...x.....|....x...x...X.....|x.x.x.x.x.x.x.x.x.|..o...o...o...o.o.|42|38",
        "MODEL 13|24|X..x..x.X..x....|....X.......X...|xxxxxxxxxxxxxxxx|....x.......x...|42|38",
        "JAZZ FUNK|24|X..x..x...x.x...|....X..o.o..X..o|xoxoxoxoxoxoxoxo|..o...o.o...o...|51|38",
        "BAIAO|24|X..X..X.X..X..X.|....x.......x...|x.x.x.x.x.x.x.x.|o.x.o.x.o.x.o.x.|42|37",
        "BALKAN 11|24|X.......x.....x.......|....X.........X.......|x.o.x.o.x...o.x.o.x.o.|..o...o...o.o...o...o.|42|38",
        "MODEL 17|24|X......x..x.....|........X.......|xoxoxoxoxoxoxoxo|...............x|42|38",
        "LATIN JAZZ|24|X.....X.x.....x.|X..x..x...x..x..|x.x.x.x.x.x.x.x.|..o..o...o..o...|51|37",
        "MARACATU|24|X..x..x.X..x..x.|....x..X....x..X|x.x.x.x.x.x.x.x.|.o.o.o.o.o.o.o.o|42|38",
        "KARSILAMA|24|X...x...x...x.....|........X.....x...|x.x.x.x.x.x.x.x.x.|..o...o...o....o..|42|38",
        "MODEL 21|24|X.........x.....|....X..x....x..o|x.x.x.x.x.x.x.x.|...o.....o......|42|38",
        "BRUSHES|32|o.....o.....|...o.....o..|x.xx.xx.xx.x|....o.....o.|42|38",
        "FREVO|24|X...X...X...X...|..x...x...x...x.|xxxxxxxxxxxxxxxx|X..X..X..X..X..X|42|37",
        "MALFUF|48|X.......|...X..x.|xxxxxxxx|.o..o..o|42|37",
        "DNB HALF|24|X.........x.....|........X......o|x.x.x.x.x.x.x.x.|...o...o.....o..|42|38",
        "ODD JAZZ 5|32|o..o..o..o..o..|...x.....x.....|x..x.ox..x.ox.o|..o.....o..o...|51|44",
        "IJEXA|24|X.........x.....|....x.......x...|x.x.x.x.x.x.x.x.|X.X.X..X.X.X..X.|42|37",
        "MAQSUM|48|X...X...|.x.x..x.|xxxxxxxx|..o..o.o|42|37",
        "MODEL 29|24|X..x......x.....|....X.......X...|x.x.x.x.x.x.x.x.|......o.......o.|42|38",
        "ODD JAZZ 7|24|x.....o...o...|......x.....x.|x.x.x.x.x.x.x.|...o...o...o..|51|44",
        "PARTIDO ALTO|24|X..x..x...x..x..|....x.......x...|x.x.x.x.x.x.x.x.|X..X..X...X..X..|42|37",
        "DABKE|24|X.....x.X.......|...x..x....x..x.|x.x.x.x.x.x.x.x.|....o.......o...|42|38",
        "ROCK BASIC|24|X.......x.x.....|....X.......X...|x.x.x.x.x.x.x.x.|...............o|42|38",
        "SON 3-2|24|X.......X.......|X..X..X...X.X...|x.x.x.x.x.x.x.x.|..o..o...o....o.|56|37",
        "AFROBEAT|24|X.....x...x.....|....X.......X...|xxxxxxxxxxxxxxxx|..x..x.x..x..x..|42|38",
        "EUCLID 3-8|48|X..X..X.|x...x...|xxxxxxxx|o.o..o..|42|38",
        "ROCK SHUFFLE|32|X.....X..x..|...X.....X..|x.xx.xx.xx.x|.....o.....o|42|38",
        "SON 2-3|24|X.......X.......|..X.X...X..X..X.|x.x.x.x.x.x.x.x.|o.....o...o..o..|56|37",
        "HIGHLIFE|24|X.....x.x.....x.|....x.......x...|..x...x...x...x.|x..x..x..x..x...|42|38",
        "EUCLID 5-8|48|X...X...|..x...x.|xxxxxxxx|X.X.XX.X|42|38",
        "FUNK|24|X..x..x...x.x...|....X..o....X..o|xoxoxoxoxoxoxoxo|..o......o....o.|42|38",
        "RUMBA 3-2|24|X.....x.X.....x.|X..X...X..X.X...|x.x.x.x.x.x.x.x.|..o..o...o....o.|56|37",
        "BEMBE 6-8|32|X.....X.....|...x.....x..|X.X.XX.X.X.X|.o......o.o.|56|38",
        "EUCLID 5-12|32|X.....X.....|...x.....x..|oooooooooooo|X..X.X..X.X.|42|38",
        "FUNK SYNC|24|X.x..x.x..x..x..|....X....o..X..o|xoxoxoxoxoxoxoxo|...x.......x....|42|38",
        "RUMBA 2-3|24|X.....x.X.....x.|..X.X...X..X...X|x.x.x.x.x.x.x.x.|o....o...o...o..|56|37",
        "BELL 12|32|X.....x.....|...x.....x..|X.XX.X.XX.X.|....o......o|56|38",
        "EUCLID 7-12|32|X.....X.....|...x.....x..|oooooooooooo|X.X.X.XX.X.X|42|38",
        "SOUL|24|X.....X...x.....|....X.......X...|xoxoxoxoxoxoxoxo|.......o.......o|42|38",
        "MAMBO|24|X.....x.X.....x.|...x...x...x...x|x.x.x.x.x.x.x.x.|X...X...X...X...|56|37",
        "KUKU|24|X..x....X.......|....x.......x...|x.x.x.x.x.x.x.x.|X..X..X.X..X..X.|56|37",
        "POLY 3-2|32|X...X...X...|X.....X.....|oooooooooooo|x...........|42|38",
        "MODEL 53|24|X...X...X...X...|...X..X....X..X.|..x...x...x...x.|.o.o.o.o.o.o.o.o|42|38",
        "SONGO|24|X.....X.X.....X.|..o.x.....o.x...|x.x.x.x.x.x.x.x.|X..X..X...X.X...|42|37",
        "GNAWA|32|X.....X.....|...x.....x..|XX.XX.XX.XX.|..o.....o...|56|38",
        "POLY 4-3|32|X..X..X..X..|X...X...X...|oooooooooooo|x...........|42|38",
        "HIP HOP|24|X.........X.....|....X.......X...|x.x.x.x.x.x.x.x.|.......o.......o|42|38",
        "REGGAE ONE|24|........X.......|........X.......|..x...x...x...x.|o...o.......o...|42|37",
        "GUMBE|24|X.....x.x.....x.|...xx.......x...|x.x.x.x.x.x.x.x.|X..X..X.X..X..X.|42|38",
        "ROTATING|24|X.......X.......|....X.......X...|x.x.x.x.x.x.x.x.|X..X..X..X..X...|42|38",
        "NEW|24|X..x..x...x.....|..o.X..o.o..X.o.|x.x.x.x.x.x.x.x.|.....x.......x..|42|38",
        "CALYPSO|24|X..x..x.X..x..x.|....X.......X...|x.x.x.x.x.x.x.x.|.o.o.o.o.o.o.o.o|42|38",
        "KWAITO|24|X.....x.X.x.....|....X.......X...|x.o.x.o.x.o.x.o.|...x.......x....|42|38",
        "INTERLOCK|32|X.....X.....|...X.....X..|.x..x..x..x.|..x..x..x..x|42|38"
        )

        private val TERNARY = setOf(
            "BEBOP","BELL 12","BEMBE 6-8","BRUSHES","EUCLID 5-12","EUCLID 7-12","GNAWA","INTERLOCK","JAZZ WALTZ","ODD JAZZ 5","POLY 3-2","POLY 4-3","ROCK SHUFFLE","SWING RIDE"
        )
    }
}
