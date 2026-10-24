package com.kaona.bach.engine

import java.util.Random
import kotlin.math.ceil

/**
 * Ornamentos da página MUSIC: appoggiatura, mordente, turn (grupeto) e trinado.
 * Em GROOVE e GROOVE MIX o ajuste gera flams (nota fantasma logo antes do golpe).
 *
 *  - Só um gesto pode começar por tick; a prioridade gira entre as 4 vozes.
 *  - A escolha do gesto depende do andamento, da duração da nota e da posição métrica
 *    (em tempos rápidos ou notas curtas, só a appoggiatura é possível).
 *  - A direção segue a linha: uma linha que sobe se aproxima da nota por baixo.
 *  - As notas seguintes do gesto ficam numa fila e saem quando o tick chega ([takeDue]).
 */
class OrnamentProcessor(private val random: Random) {
    private val scheduled = ArrayList<NoteEvent>()
    private val busyUntil = LongArray(4)
    private val prevNote = IntArray(4) { -1 }
    private var turn = 0

    fun clear() {
        scheduled.clear()
        busyUntil.fill(0L)
        prevNote.fill(-1)
        turn = 0
    }

    /** Agenda [e] para sair [delay] ticks depois de [tick] (swing e microtiming usam isto). */
    fun defer(e: NoteEvent, tick: Long, delay: Int) {
        scheduled.add(e.copy(startTick = tick + delay))
    }

    /** Eventos agendados cujo momento chegou. */
    fun takeDue(tick: Long): List<NoteEvent> {
        if (scheduled.isEmpty()) return emptyList()
        val due = ArrayList<NoteEvent>()
        val it = scheduled.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.startTick <= tick) {
                due.add(e)
                it.remove()
            }
        }
        return due
    }

    fun process(events: List<NoteEvent>, tick: Long, state: EngineState): List<NoteEvent> {
        if (events.isEmpty()) return events

        val amount = state.ornaments
        if (amount <= 0f) {
            remember(events)
            return events
        }

        val drums = state.language == CompositionalLanguage.GROOVE ||
            state.language == CompositionalLanguage.GROOVE_MIX
        val msPerTick = 60000.0 / (state.bpm.coerceAtLeast(1) * RhythmUtils.QUARTER)
        // Cada nota de ornamento dura pelo menos ~40 ms; o flam usa ~25 ms
        val unit = maxOf(3, ceil(40.0 / msPerTick).toInt())
        val flam = maxOf(2, ceil(25.0 / msPerTick).toInt())

        // Prioridade rotativa entre as vozes
        var found: NoteEvent? = null
        for (k in 0 until 4) {
            val tr = (turn + k) % 4
            val c = events.firstOrNull { it.track == tr && eligible(it, tick, drums, unit) }
            if (c != null) {
                found = c
                break
            }
        }
        val target = found
        if (target == null) {
            remember(events)
            return events
        }
        turn = (target.track + 1) % 4

        // Probabilidade de ornamentar: até 60% das notas elegíveis no máximo
        if (random.nextFloat() >= amount * 0.6f) {
            remember(events)
            return events
        }

        val out = ArrayList<NoteEvent>(events.size + 1)
        for (e in events) {
            if (e === target) {
                if (drums) addFlam(e, tick, flam, out) else addMelodic(e, tick, state, unit, out)
            } else {
                out.add(e)
            }
        }
        remember(events)
        return out
    }

    private fun eligible(e: NoteEvent, tick: Long, drums: Boolean, unit: Int): Boolean {
        if (e.track !in 0..3) return false
        if (busyUntil[e.track] > tick) return false
        return if (drums) e.track != 0 else e.durationTicks >= 2 * unit
    }

    private fun remember(events: List<NoteEvent>) {
        for (e in events) {
            if (e.track in 0..3) prevNote[e.track] = e.midiNote
        }
    }

    private fun ev(e: NoteEvent, note: Int, velocity: Int, duration: Int, start: Long): NoteEvent =
        e.copy(
            midiNote = note.coerceIn(0, 127),
            velocity = velocity.coerceIn(1, 127),
            durationTicks = duration.coerceAtLeast(1),
            startTick = start
        )

    private fun addFlam(e: NoteEvent, tick: Long, flam: Int, out: MutableList<NoteEvent>) {
        out.add(ev(e, e.midiNote, (e.velocity * 0.55f).toInt(), flam, tick))
        scheduled.add(e.copy(startTick = tick + flam))
        busyUntil[e.track] = tick + flam * 2
    }

    private fun addMelodic(
        e: NoteEvent,
        tick: Long,
        state: EngineState,
        unit: Int,
        out: MutableList<NoteEvent>
    ) {
        val d = e.durationTicks
        val onBeat = tick % RhythmUtils.QUARTER == 0L
        val rising = prevNote[e.track] >= 0 && e.midiNote > prevNote[e.track]
        val up = neighbor(e.midiNote, 1, state)
        val down = neighbor(e.midiNote, -1, state)
        val soft = (e.velocity * 0.8f).toInt()
        val short = maxOf(1, unit - 1)

        // Pesos de cada gesto, conforme a duração disponível e a posição métrica
        val wApp = 4
        val wMord = if (d >= 3 * unit) (if (onBeat) 4 else 2) else 0
        val wTurn = if (d >= 4 * unit) (if (onBeat && d >= RhythmUtils.QUARTER) 3 else 1) else 0
        val wTrill = if (d >= 6 * unit && d >= RhythmUtils.QUARTER) (if (onBeat) 3 else 1) else 0

        var r = random.nextInt(wApp + wMord + wTurn + wTrill)
        val gesture = when {
            r < wApp -> 0
            else -> {
                r -= wApp
                if (r < wMord) 1 else {
                    r -= wMord
                    if (r < wTurn) 2 else 3
                }
            }
        }

        when (gesture) {
            0 -> { // appoggiatura: nota de adorno antes da nota principal
                out.add(ev(e, if (rising) down else up, soft, short, tick))
                scheduled.add(ev(e, e.midiNote, e.velocity, d - unit, tick + unit))
            }
            1 -> { // mordente: principal - vizinha de cima - principal
                out.add(ev(e, e.midiNote, e.velocity, short, tick))
                scheduled.add(ev(e, up, soft, short, tick + unit))
                scheduled.add(ev(e, e.midiNote, e.velocity, d - 2 * unit, tick + 2L * unit))
            }
            2 -> { // turn: cima - principal - baixo - principal
                out.add(ev(e, up, soft, short, tick))
                scheduled.add(ev(e, e.midiNote, e.velocity, short, tick + unit))
                scheduled.add(ev(e, down, soft, short, tick + 2L * unit))
                scheduled.add(ev(e, e.midiNote, e.velocity, d - 3 * unit, tick + 3L * unit))
            }
            else -> { // trinado: alterna principal e vizinha de cima, terminando na principal
                var m = minOf(12, d / unit)
                if (m % 2 == 0) m -= 1
                m = maxOf(m, 5)
                out.add(ev(e, e.midiNote, e.velocity, short, tick))
                for (i in 1 until m) {
                    val main = i % 2 == 0
                    val dur = if (i == m - 1) d - (m - 1) * unit else short
                    scheduled.add(
                        ev(e, if (main) e.midiNote else up, if (main) e.velocity else soft, dur, tick + i.toLong() * unit)
                    )
                }
            }
        }
        busyUntil[e.track] = tick + d
    }

    /** Próxima nota da escala acima ([dir] = 1) ou abaixo ([dir] = -1). */
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
