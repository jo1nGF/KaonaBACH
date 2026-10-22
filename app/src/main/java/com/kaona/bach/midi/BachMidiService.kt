package com.kaona.bach.midi

import android.media.midi.MidiDeviceService
import android.media.midi.MidiDeviceStatus
import android.media.midi.MidiReceiver
import android.util.Log
import java.io.IOException

/**
 * Virtual MIDI Device Service that exposes B.A.C.H. as a MIDI source
 * to other Android apps. Provides 4 output ports, one per voice.
 */
class BachMidiService : MidiDeviceService() {

    private val inboundReceiver = InboundMidiReceiver()

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "B.A.C.H. MIDI Service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "B.A.C.H. MIDI Service destroyed")
    }

    override fun onGetInputPortReceivers(): Array<MidiReceiver> {
        return arrayOf(inboundReceiver)
    }

    override fun onDeviceStatusChanged(status: MidiDeviceStatus?) {
        super.onDeviceStatusChanged(status)
        status?.let {
            for (i in 0 until 4) {
                val isOpen = it.getOutputPortOpenCount(i) > 0
                Log.d(TAG, "Output port $i open: $isOpen")
            }
            isConnected = (0 until 4).any { i -> it.getOutputPortOpenCount(i) > 0 }
        }
    }

    private class InboundMidiReceiver : MidiReceiver() {
        override fun onSend(data: ByteArray?, offset: Int, count: Int, timestamp: Long) {
            // Handle incoming MIDI (e.g., clock sync from external source)
            data?.let {
                if (count > 0) {
                    val status = it[offset].toInt() and 0xFF
                    when (status) {
                        0xF8 -> onMidiClockReceived?.invoke()          // MIDI Clock
                        0xFA -> onMidiStartReceived?.invoke()          // Start
                        0xFB -> onMidiContinueReceived?.invoke()       // Continue
                        0xFC -> onMidiStopReceived?.invoke()           // Stop
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "BachMidiService"
        private var instance: BachMidiService? = null
        var isConnected: Boolean = false
            private set

        // Callbacks for MIDI clock sync
        var onMidiClockReceived: (() -> Unit)? = null
        var onMidiStartReceived: (() -> Unit)? = null
        var onMidiContinueReceived: (() -> Unit)? = null
        var onMidiStopReceived: (() -> Unit)? = null

        /**
         * Send a Note On message
         * @param channel MIDI channel 1-16
         * @param note MIDI note 0-127
         * @param velocity 0-127
         */
        fun sendNoteOn(channel: Int, note: Int, velocity: Int) {
            val status = (0x90 or ((channel - 1) and 0x0F)).toByte()
            val msg = byteArrayOf(status, (note and 0x7F).toByte(), (velocity and 0x7F).toByte())
            sendRawMessage(msg, 0, msg.size)
        }

        /**
         * Send a Note Off message
         */
        fun sendNoteOff(channel: Int, note: Int) {
            val status = (0x80 or ((channel - 1) and 0x0F)).toByte()
            val msg = byteArrayOf(status, (note and 0x7F).toByte(), 0x00)
            sendRawMessage(msg, 0, msg.size)
        }

        /**
         * Send a Control Change message (for MOD output mapping)
         */
        fun sendControlChange(channel: Int, controller: Int, value: Int) {
            val status = (0xB0 or ((channel - 1) and 0x0F)).toByte()
            val msg = byteArrayOf(status, (controller and 0x7F).toByte(), (value and 0x7F).toByte())
            sendRawMessage(msg, 0, msg.size)
        }

        /**
         * Send a Pitch Bend message
         */
        fun sendPitchBend(channel: Int, value: Int) {
            val status = (0xE0 or ((channel - 1) and 0x0F)).toByte()
            val lsb = (value and 0x7F).toByte()
            val msb = ((value shr 7) and 0x7F).toByte()
            val msg = byteArrayOf(status, lsb, msb)
            sendRawMessage(msg, 0, msg.size)
        }

        /**
         * Send MIDI Clock message
         */
        fun sendClock() {
            sendRawMessage(byteArrayOf(0xF8.toByte()), 0, 1)
        }

        /**
         * Send MIDI Start
         */
        fun sendStart() {
            sendRawMessage(byteArrayOf(0xFA.toByte()), 0, 1)
        }

        /**
         * Send MIDI Stop
         */
        fun sendStop() {
            sendRawMessage(byteArrayOf(0xFC.toByte()), 0, 1)
        }

        /**
         * Send all notes off on a channel
         */
        fun sendAllNotesOff(channel: Int) {
            sendControlChange(channel, 123, 0) // CC 123 = All Notes Off
        }

        /**
         * Send raw MIDI bytes to all connected receivers
         */
        fun sendRawMessage(msg: ByteArray, offset: Int, count: Int) {
            val service = instance ?: return
            val receivers = service.outputPortReceivers ?: return
            for (receiver in receivers) {
                try {
                    receiver.send(msg, offset, count, System.nanoTime())
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to send MIDI: ${e.message}")
                }
            }
        }
    }
}
