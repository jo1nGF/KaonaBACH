package com.kaona.bach.midi

import com.kaona.bach.engine.NoteEvent
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages MIDI output, handling note on/off timing and channel routing.
 */
class MidiOutputManager {

    private val activeNotes = ConcurrentHashMap<String, NoteOffJob>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    data class NoteOffJob(
        val channel: Int,
        val note: Int,
        val job: Job
    )

    /**
     * Send a NoteEvent as MIDI, with automatic note-off scheduling.
     * @param event The note event to send
     * @param channel MIDI channel (1-16)
     * @param bpm Current BPM for duration calculation
     */
    fun sendNoteEvent(event: NoteEvent, channel: Int, bpm: Int) {
        val key = "${channel}_${event.midiNote}"

        // Kill any existing note on same channel/note
        activeNotes[key]?.let {
            BachMidiService.sendNoteOff(it.channel, it.note)
            it.job.cancel()
        }

        // Send note on
        BachMidiService.sendNoteOn(channel, event.midiNote, event.velocity)

        // Send mod value as CC1 (modulation wheel)
        BachMidiService.sendControlChange(channel, 1, event.modValue)

        // Schedule note off
        val durationMs = ticksToMs(event.durationTicks, bpm)
        val job = scope.launch {
            delay(durationMs)
            BachMidiService.sendNoteOff(channel, event.midiNote)
            activeNotes.remove(key)
        }

        activeNotes[key] = NoteOffJob(channel, event.midiNote, job)
    }

    /**
     * Convert tick duration to milliseconds based on BPM
     * Using 96 PPQN (Pulses Per Quarter Note)
     */
    private fun ticksToMs(ticks: Int, bpm: Int): Long {
        val msPerQuarter = 60000.0 / bpm
        val msPerTick = msPerQuarter / 96.0
        return (ticks * msPerTick).toLong()
    }

    /**
     * Send all notes off on all channels and cancel pending note-offs
     */
    fun allNotesOff() {
        activeNotes.values.forEach { noteOff ->
            BachMidiService.sendNoteOff(noteOff.channel, noteOff.note)
            noteOff.job.cancel()
        }
        activeNotes.clear()
        for (ch in 1..16) {
            BachMidiService.sendAllNotesOff(ch)
        }
    }

    fun destroy() {
        allNotesOff()
        scope.cancel()
    }
}
