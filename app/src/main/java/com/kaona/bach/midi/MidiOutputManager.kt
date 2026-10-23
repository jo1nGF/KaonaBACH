package com.kaona.bach.midi

import com.kaona.bach.engine.NoteEvent

/**
 * Saída MIDI: envia Note On/CC e fecha cada nota (Note Off) no tick certo.
 *
 * Os Note Off são conferidos pelo próprio clock (onTick), em vez de uma corrotina por nota.
 * Assim a duração das notas tem precisão de tick, acompanha mudanças de BPM e não gera
 * trabalho extra nem jitter no agendador de threads.
 */
class MidiOutputManager {

    private val lock = Any()
    private val pending = ArrayList<PendingOff>()
    private var currentTick = 0L

    companion object {
        /** Nota usada quando PITCH está desligado (C4). */
        const val FIXED_NOTE = 60
    }

    private class PendingOff(val channel: Int, val note: Int, val offTick: Long)

    /** Chamado pelo clock a cada tick: envia os Note Off que já venceram. */
    fun onTick(tick: Long) {
        synchronized(lock) {
            currentTick = tick
            var i = 0
            while (i < pending.size) {
                val p = pending[i]
                if (p.offTick <= tick) {
                    BachMidiService.sendNoteOff(p.channel, p.note)
                    pending.removeAt(i)
                } else {
                    i++
                }
            }
        }
    }

    /**
     * Envia um NoteEvent como MIDI.
     * @param channel canal MIDI (1-16)
     */
    fun sendNoteEvent(
        event: NoteEvent,
        channel: Int,
        gateEnabled: Boolean = true,
        modEnabled: Boolean = true,
        pitchEnabled: Boolean = true
    ) {
        // MOD: CC1 vai antes da nota, para a nota já nascer com a modulação aplicada
        if (modEnabled) {
            BachMidiService.sendControlChange(channel, 1, event.modValue)
        }

        // Sem GATE não há disparo de nota
        if (!gateEnabled) return

        // PITCH desligado: o gate dispara uma nota fixa (trigger puro)
        val noteNumber = if (pitchEnabled) event.midiNote else FIXED_NOTE

        synchronized(lock) {
            // Mesma nota ainda soando no mesmo canal: fecha antes de retocar
            var i = 0
            while (i < pending.size) {
                val p = pending[i]
                if (p.channel == channel && p.note == noteNumber) {
                    BachMidiService.sendNoteOff(p.channel, p.note)
                    pending.removeAt(i)
                } else {
                    i++
                }
            }

            BachMidiService.sendNoteOn(channel, noteNumber, event.velocity)
            pending.add(PendingOff(channel, noteNumber, currentTick + event.durationTicks.coerceAtLeast(1)))
        }
    }

    /** Fecha todas as notas pendentes e manda All Notes Off em todos os canais. */
    fun allNotesOff() {
        synchronized(lock) {
            for (p in pending) {
                BachMidiService.sendNoteOff(p.channel, p.note)
            }
            pending.clear()
            currentTick = 0L
        }
        for (ch in 1..16) {
            BachMidiService.sendAllNotesOff(ch)
        }
    }

    fun destroy() {
        allNotesOff()
    }
}
