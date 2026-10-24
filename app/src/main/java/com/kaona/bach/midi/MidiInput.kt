package com.kaona.bach.midi

import com.kaona.bach.engine.CompositionalLanguage

/** Comandos que podem ser atribuídos a um CC na página MIDI IN. */
enum class MidiCommand(val label: String) {
    MATERIAL("MATERIAL"),
    MEMORY("MEMORY"),
    GROWTH("GROWTH"),
    MORPH("MORPH"),
    DENSITY("DENSITY"),
    TENSION("TENSION"),
    INTERACTION("INTERACTION"),
    HUMANIZE("HUMANIZE"),
    ALGORITHM("ALGORITHM"),
    RESET("RESET"),
    BREAK("BREAK"),
    PLAY_STOP("PLAY / STOP");

    /** Nome do parâmetro do engine (só os 8 controles). */
    val paramName: String? get() = if (ordinal < 8) label.lowercase() else null
}

/** [channel]: 0 = OMNI, 1 a 16. [cc]: -1 = OFF, 0 a 127. */
data class MidiAssign(val channel: Int = 1, val cc: Int = -1)

data class MidiInSettings(
    val clockEnabled: Boolean = false,
    val resetOnStop: Boolean = false,
    /** Canal que escuta notas para definir o ROOT: -1 = OFF, 0 = OMNI, 1 a 16. */
    val rootChannel: Int = 0,
    /** Um item por [MidiCommand], na ordem do enum. Padrão: canal 1, CC 16 a 27. */
    val assignments: List<MidiAssign> = MidiCommand.entries.map { MidiAssign(1, 16 + it.ordinal) }
)

/** Último evento recebido e contadores (MSG = mensagens de canal, CLK = pulsos de clock). */
data class MidiMonitor(
    val channel: Int = 0,
    val cc: Int = -1,
    val value: Int = 0,
    val messages: Int = 0,
    val clocks: Int = 0
)

/** CC do comando ALGORITHM: 12 faixas de valores, uma por linguagem. */
fun languageFromCc(value: Int): CompositionalLanguage = when {
    value <= 10 -> CompositionalLanguage.FUGUE
    value <= 21 -> CompositionalLanguage.CANON
    value <= 31 -> CompositionalLanguage.PROBA
    value <= 42 -> CompositionalLanguage.TINTINNABULI
    value <= 53 -> CompositionalLanguage.MINIMAL
    value <= 63 -> CompositionalLanguage.CELLULAR
    value <= 74 -> CompositionalLanguage.TREE
    value <= 85 -> CompositionalLanguage.FRACTAL
    value <= 95 -> CompositionalLanguage.MOTIF
    value <= 106 -> CompositionalLanguage.HYBRID
    value <= 117 -> CompositionalLanguage.GROOVE
    else -> CompositionalLanguage.GROOVE_MIX
}
