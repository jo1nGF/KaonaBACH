package com.kaona.bach.engine

import java.util.Random

/**
 * MotifEngine: uma cadeia de células melódicas e rítmicas. A célula inicial depende do gênero
 * escolhido em MATERIAL (32 repertórios).
 *
 * Cada célula dura de 8 a 32 semicolcheias e a cadeia reúne de 2 a 10 células. A primeira célula é
 * sempre a mesma; as seguintes trazem respostas, variações e digressões; a última prepara a cadência
 * e a volta ao início. Não há sorteio nota a nota: os mesmos ajustes reproduzem as mesmas frases.
 * A cadeia começa no início de um compasso e termina completando o compasso, então o desenho
 * recomeça alinhado ao compasso escolhido em MUSIC.
 *
 *  - GROWTH:      abre a cadeia aos poucos, de 2 a 10 células.
 *  - MEMORY:      mantém ou reorganiza as células internas (início e cadência ficam fixos).
 *  - MORPH:       deslocamento, aumentação, fragmento, inversão e retrógrado.
 *  - DENSITY:     revela aos poucos motivo, resposta, apoio e baixo.
 *  - TENSION:     afasta as digressões e reforça as notas de passagem.
 *  - INTERACTION: solo, acompanhamento, chamada e resposta e contraponto.
 */
class MotifEngine : CompositionEngine {

    /** [tokens]: "grau/duração" em semicolcheias; "R" é pausa. [semitones]: graus lidos em semitons. */
    private class Spec(
        val name: String,
        val tokens: String,
        val semitones: Boolean = false,
        val chordal: Boolean = false
    )

    private class MNote(val start: Int, val pitch: Int, val duration: Int, val accent: Boolean)

    private class Cell(val notes: List<MNote>, val length: Int)

    private class TNote(val pitch: Int, val duration: Int, val accent: Boolean)

    private val specs = listOf(
        Spec("CANTABILE", "0/4 1/2 2/2 3/4 2/2 1/2"),
        Spec("ARCH", "0/2 1/2 2/2 3/2 4/4 3/2 2/2 1/2 0/2"),
        Spec("STEPWISE", "0/2 1/2 2/2 1/2 0/2 -1/2 0/2 1/2"),
        Spec("FOLK", "0/4 2/2 1/2 0/4 -2/2 0/2"),
        Spec("ARPEGGIO", "0/2 2/2 4/2 7/2 4/2 2/2 0/2 -3/2"),
        Spec("CHORALE", "0/8 2/8 1/8 0/8", chordal = true),
        Spec("LAMENT", "4/4 3/4 2/4 1/4 0/8"),
        Spec("PEDAL", "0/2 2/2 0/2 3/2 0/2 4/2 0/2 1/2"),
        Spec("RIFF", "0/2 0/1 2/1 0/2 -1/2"),
        Spec("OSTINATO", "0/2 2/2 4/2 2/2"),
        Spec("SYNCOPATED", "R/2 0/2 R/2 2/2 R/2 3/2 R/2 1/2"),
        Spec("WALTZ", "0/4 2/2 4/2 2/4 3/4 2/2 1/2 0/4"),
        Spec("CALL RESPONSE", "0/2 2/2 4/2 2/2 R/8"),
        Spec("MODAL", "0/4 1/2 3/2 4/4 3/2 1/2"),
        Spec("DANCE", "0/2 0/2 2/2 0/2 3/2 0/2 2/2 0/2"),
        Spec("WIDE", "0/4 5/4 -2/4 7/4"),
        Spec("NOCTURNE", "R/4 0/4 R/4 2/4 R/2 1/6"),
        Spec("HYMN", "0/8 1/4 2/4 1/8 0/8"),
        Spec("BAROQUE", "4/2 3/2 3/2 2/2 2/2 1/2 1/2 0/2"),
        Spec("ROMANTIC", "0/4 2/4 4/4 6/4 5/4 4/4 2/4 0/4"),
        Spec("MINIMAL LINE", "0/2 2/2 1/2 R/2"),
        Spec("JAZZ LINE", "0/2 1/1 2/1 4/2 R/1 3/1 2/2 -1/1 0/1 1/2 R/2"),
        Spec("BLUES LINE", "0/2 3/2 5/2 6/1 7/1 10/4 7/2 R/2", semitones = true),
        Spec("LATIN LINE", "0/3 2/3 4/2 5/3 4/3 2/2"),
        Spec("BOSSA LINE", "R/1 0/3 2/2 4/3 R/1 3/3 2/3"),
        Spec("ROCK LINE", "0/2 0/2 3/2 0/2 5/2 0/2 3/2 0/2"),
        Spec("AFRO LINE", "0/3 2/3 0/2 3/3 2/3 0/2"),
        Spec("BALKAN LINE", "0/2 2/2 4/3 2/2 0/2 3/3"),
        Spec("CELTIC LINE", "0/2 1/2 2/2 4/2 2/2 1/2 0/4"),
        Spec("EASTERN", "0/2 1/1 0/1 3/2 4/2 3/1 1/1 0/4 R/2", semitones = true),
        Spec("IMPRESSIONIST", "0/4 2/4 6/4 8/2 4/2", semitones = true),
        Spec("CINEMATIC", "0/8 2/8 4/8 7/8")
    )

    // Notas por passo da grade (semicolcheia) para cada voz: 0 = baixo, 1 = apoio, 2 = resposta, 3 = motivo
    private var timelines: Array<HashMap<Int, List<TNote>>> = Array(4) { HashMap() }
    private var period = 0
    private var signature = ""
    private var cycleCount = 0
    private var originStep = -1L
    private var lastStep = -1L

    override fun generate(tick: Long, state: EngineState, random: Random): List<NoteEvent> {
        if (tick % STEP_TICKS != 0L) return emptyList()
        val globalStep = tick / STEP_TICKS
        if (globalStep == lastStep) return emptyList()
        lastStep = globalStep
        if (originStep < 0L) originStep = globalStep

        val rel = globalStep - originStep
        val atCycleStart = period > 0 && rel % period == 0L && rel > 0L
        if (atCycleStart) cycleCount++

        val spec = specs[MaterialCatalog.index(CompositionalLanguage.MOTIF, state.params.material).coerceIn(0, specs.size - 1)]
        val sig = signatureOf(spec, state)
        if (sig != signature || period == 0) {
            rebuild(spec, state)
            signature = sig
        }
        if (period == 0) return emptyList()

        val stepInChain = (rel % period).toInt()
        val events = ArrayList<NoteEvent>(4)
        val p = state.params

        for (voice in 0 until 4) {
            val track = voice
            if (state.trackMutes.getOrElse(track) { false }) continue
            val notes = timelines[voice][stepInChain] ?: continue

            for (n in notes) {
                val midi = toMidi(n.pitch, spec, voice, state)
                val voiceOffset = when (voice) {
                    3 -> 0
                    2 -> -10
                    1 -> -18
                    else -> -12
                }
                val velocity = ((if (n.accent) 92 else 74) + (p.tension * 14f).toInt() + voiceOffset).coerceIn(30, 120)

                events.add(
                    NoteEvent(
                        track = track,
                        midiNote = midi.coerceIn(0, 127),
                        velocity = velocity,
                        durationTicks = (n.duration * STEP_TICKS).toInt(),
                        startTick = tick,
                        modValue = (50 + (p.morph * 40f).toInt()).coerceIn(0, 127)
                    )
                )
            }
        }
        return events
    }

    override fun reset() {
        timelines = Array(4) { HashMap() }
        period = 0
        signature = ""
        cycleCount = 0
        originStep = -1L
        lastStep = -1L
    }

    // ---------------- Construção da cadeia ----------------

    private fun signatureOf(spec: Spec, state: EngineState): String {
        val p = state.params
        val cells = 2 + (p.growth * 8f).toInt()
        val morphStage = when {
            p.morph < 0.2f -> 0
            p.morph < 0.4f -> 1
            p.morph < 0.55f -> 2
            p.morph < 0.7f -> 3
            p.morph < 0.85f -> 4
            else -> 5
        }
        val voices = 1 + (p.density * 3.99f).toInt()
        val mode = when {
            p.interaction < 0.25f -> 0
            p.interaction < 0.5f -> 1
            p.interaction < 0.75f -> 2
            else -> 3
        }
        val tensionBucket = (p.tension * 5f).toInt()
        val reorg = if (p.memory >= 0.5f) 0 else (cycleCount * (1f - p.memory * 2f)).toInt()
        return "${spec.name}|$cells|$morphStage|$voices|$mode|$tensionBucket|$reorg|${state.music.meter.name}"
    }

    private fun parse(spec: Spec): Cell {
        val notes = ArrayList<MNote>()
        var t = 0
        for (token in spec.tokens.trim().split(" ")) {
            val parts = token.split("/")
            val duration = parts[1].toInt()
            if (parts[0] != "R") {
                notes.add(MNote(t, parts[0].toInt(), duration, accent = notes.isEmpty() || (t % 4 == 0 && duration >= 2)))
            }
            t += duration
        }
        return Cell(notes, t)
    }

    private fun transpose(cell: Cell, amount: Int): Cell =
        Cell(cell.notes.map { MNote(it.start, it.pitch + amount, it.duration, it.accent) }, cell.length)

    private fun invert(cell: Cell): Cell {
        val first = cell.notes.firstOrNull()?.pitch ?: 0
        return Cell(cell.notes.map { MNote(it.start, first - (it.pitch - first), it.duration, it.accent) }, cell.length)
    }

    private fun retrograde(cell: Cell): Cell =
        Cell(
            cell.notes.map { MNote(cell.length - it.start - it.duration, it.pitch, it.duration, it.accent) }
                .sortedBy { it.start },
            cell.length
        )

    private fun displace(cell: Cell, steps: Int): Cell =
        Cell(
            cell.notes.map { MNote((it.start + steps) % cell.length, it.pitch, minOf(it.duration, cell.length - ((it.start + steps) % cell.length)), it.accent) }
                .sortedBy { it.start },
            cell.length
        )

    private fun augment(cell: Cell): Cell =
        Cell(cell.notes.map { MNote(it.start * 2, it.pitch, it.duration * 2, it.accent) }, cell.length * 2)

    private fun fragment(cell: Cell): Cell =
        Cell(cell.notes.filter { it.start < cell.length / 2 }, cell.length)

    /** TENSION: notas de passagem entre saltos grandes (a nota anterior perde metade da duração). */
    private fun withPassingNotes(cell: Cell, unit: Int): Cell {
        val out = ArrayList<MNote>()
        for (i in cell.notes.indices) {
            val n = cell.notes[i]
            val next = cell.notes.getOrNull(i + 1)
            if (next != null && n.duration >= 2 && Math.abs(next.pitch - n.pitch) >= 3 * unit &&
                n.start + n.duration == next.start
            ) {
                val half = n.duration / 2
                out.add(MNote(n.start, n.pitch, n.duration - half, n.accent))
                out.add(MNote(n.start + n.duration - half, (n.pitch + next.pitch) / 2, half, false))
            } else {
                out.add(n)
            }
        }
        return Cell(out, cell.length)
    }

    private fun innerCell(base: Cell, k: Int, reorg: Int, state: EngineState, unit: Int): Cell {
        val p = state.params
        val op = (k - 1 + reorg) % 5
        var cell = when (op) {
            0 -> transpose(base, 2 * unit) // resposta
            1 -> Cell( // variação: alguns graus se deslocam
                base.notes.mapIndexed { i, n -> if (i % 3 == 1) MNote(n.start, n.pitch + unit, n.duration, n.accent) else n },
                base.length
            )
            2 -> transpose(base, (2 + (p.tension * 5f).toInt()) * unit) // digressão
            3 -> transpose(base, -unit) // sequência
            else -> invert(base) // contra-resposta
        }
        if (op == 2 && p.tension > 0.5f) cell = withPassingNotes(cell, unit)

        // MORPH: transformações progressivas
        cell = when {
            p.morph < 0.2f -> cell
            p.morph < 0.4f -> displace(cell, 2)
            p.morph < 0.55f -> augment(cell)
            p.morph < 0.7f -> fragment(cell)
            p.morph < 0.85f -> invert(cell)
            else -> retrograde(cell)
        }
        return cell
    }

    private fun cadence(base: Cell, unit: Int): Cell {
        val cut = base.length - 8
        val kept = if (cut > 0) base.notes.filter { it.start + it.duration <= cut } else emptyList()
        val tail = ArrayList<MNote>(kept)
        val start = if (cut > 0) cut else 0
        tail.add(MNote(start, unit, 4, false))
        tail.add(MNote(start + 4, 0, 4, true))
        return Cell(tail, maxOf(base.length, start + 8))
    }

    private fun rebuild(spec: Spec, state: EngineState) {
        val p = state.params
        val unit = if (spec.semitones) 2 else 1
        val base = parse(spec)
        val count = 2 + (p.growth * 8f).toInt()
        val reorg = if (p.memory >= 0.5f) 0 else (cycleCount * (1f - p.memory * 2f)).toInt()

        val chain = ArrayList<Cell>(count)
        chain.add(base)
        for (k in 1..(count - 2)) chain.add(innerCell(base, k, reorg, state, unit))
        chain.add(cadence(base, unit))

        // Linha do tempo da cadeia (em semicolcheias), completando o último compasso com pausas
        val barSteps = (state.music.meter.barTicks / STEP_TICKS).toInt().coerceAtLeast(1)
        var total = 0
        for (cell in chain) total += cell.length
        period = ((total + barSteps - 1) / barSteps) * barSteps

        val motif = HashMap<Int, MutableList<TNote>>()
        var offset = 0
        for (cell in chain) {
            for (n in cell.notes) {
                val at = offset + n.start
                if (at < period) motif.getOrPut(at) { ArrayList() }.add(TNote(n.pitch, n.duration, n.accent))
            }
            offset += cell.length
        }

        val voices = 1 + (p.density * 3.99f).toInt()
        val mode = when {
            p.interaction < 0.25f -> 0
            p.interaction < 0.5f -> 1
            p.interaction < 0.75f -> 2
            else -> 3
        }
        val beatSteps = (state.music.meter.beatTicks / STEP_TICKS).toInt().coerceAtLeast(1)

        val result = Array(4) { HashMap<Int, List<TNote>>() }
        result[3].putAll(motif)

        // Resposta (trilha 3)
        if (voices >= 2 && mode != 0) {
            val halfCell = maxOf(1, base.length / 2)
            for ((at, list) in motif) {
                for (n in list) {
                    val mapped: Pair<Int, TNote>? = when {
                        spec.chordal -> Pair(at, TNote(n.pitch - 2 * unit, n.duration, n.accent))
                        mode == 1 -> Pair(at, TNote(n.pitch - 2 * unit, n.duration, false)) // acompanhamento em terças
                        mode == 2 -> Pair((at + halfCell) % period, TNote(n.pitch + 2 * unit, n.duration, n.accent)) // eco atrasado
                        else -> Pair(at, TNote(-n.pitch, n.duration, n.accent)) // contraponto: movimento contrário
                    }
                    if (mapped != null) {
                        val l = (result[2][mapped.first] ?: emptyList()).toMutableList()
                        l.add(mapped.second)
                        result[2][mapped.first] = l
                    }
                }
            }
        }

        // Apoio (trilha 2): acordes longos a cada tempo; no bloco coral, segue o motivo
        if (voices >= 3 && mode != 0) {
            if (spec.chordal) {
                for ((at, list) in motif) {
                    result[1][at] = list.map { TNote(it.pitch - 4 * unit, it.duration, false) }
                }
            } else {
                val chordDegrees = intArrayOf(0, 2, 4, 2)
                var step = 0
                var i = 0
                while (step < period) {
                    result[1][step] = listOf(TNote(chordDegrees[i % chordDegrees.size] * unit, beatSteps, step % (beatSteps * 4) == 0))
                    step += beatSteps
                    i++
                }
            }
        }

        // Baixo (trilha 1): fundamental no começo do compasso e quinta no meio
        if (voices >= 4 && mode != 0) {
            if (spec.chordal) {
                for ((at, list) in motif) {
                    result[0][at] = list.map { TNote(it.pitch - 7 * unit, it.duration, false) }
                }
            } else {
                var step = 0
                while (step < period) {
                    result[0][step] = listOf(TNote(0, barSteps, true))
                    val mid = step + barSteps / 2
                    if (mid < period) result[0][mid] = listOf(TNote(4 * unit, barSteps / 2, false))
                    step += barSteps
                }
            }
        }

        timelines = Array(4) { v -> HashMap<Int, List<TNote>>(result[v]) }
    }

    // ---------------- Alturas ----------------

    private fun toMidi(pitch: Int, spec: Spec, voice: Int, state: EngineState): Int {
        val base = when (voice) {
            3 -> 72
            2 -> 60
            1 -> 48
            else -> 36
        }
        val rootPc = state.rootNote.midiBase
        return if (spec.semitones) {
            state.scale.quantize(base + rootPc + pitch, state.rootNote)
        } else {
            val degrees = state.scale.intervals
            val size = degrees.size
            val octave = Math.floorDiv(pitch, size)
            val idx = Math.floorMod(pitch, size)
            var midi = base + rootPc + degrees[idx] + octave * 12
            while (midi < 12) midi += 12
            while (midi > 120) midi -= 12
            midi
        }
    }

    companion object {
        private const val STEP_TICKS = 24L
    }
}
