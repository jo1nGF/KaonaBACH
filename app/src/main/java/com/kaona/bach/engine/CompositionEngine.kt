package com.kaona.bach.engine

import java.util.Random

/**
 * Base interface for all compositional language engines.
 */
interface CompositionEngine {
    fun generate(tick: Long, state: EngineState, random: Random): List<NoteEvent>
    fun reset()
}
