package com.kaona.bach.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import java.util.Random

/**
 * TreeEngine - Implements L-system (Lindenmayer system) based music generation.
 * Uses axioms and production rules to generate strings, then interprets them
 * as musical instructions using turtle graphics metaphor.
 *
 * F = step forward (move up in scale)
 * f = step forward smaller (half step up)
 * + = turn right (interval up)
 * - = turn left (interval down)
 * [ = push state (save current position)
 * ] = pop state (return to saved position)
 * A,B = variables for rule expansion
 */
class TreeEngine : CompositionEngine {

    /**
     * L-system rule set with axiom and production rules.
     */
    private data class LSystemRuleSet(
        val name: String,
        val axiom: String,
        val rules: Map<Char, String>
    )

    /**
     * Turtle state for interpretation.
     */
    private data class TurtleState(
        var degree: Int,       // Current scale degree
        var direction: Int,    // +1 ascending, -1 descending
        var velocity: Int,     // Current velocity
        var octaveOffset: Int  // Octave transposition
    )

    // Preset L-system rule sets
    private val presetRules = listOf(
        // 0: Simple branching (tree-like)
        LSystemRuleSet(
            name = "Tree",
            axiom = "F",
            rules = mapOf(
                'F' to "F[+F]F[-F]F"
            )
        ),
        // 1: Koch curve variant
        LSystemRuleSet(
            name = "Koch",
            axiom = "F",
            rules = mapOf(
                'F' to "F+F-F-F+F"
            )
        ),
        // 2: Dragon curve
        LSystemRuleSet(
            name = "Dragon",
            axiom = "FA",
            rules = mapOf(
                'A' to "A+BF+",
                'B' to "-FA-B"
            )
        ),
        // 3: Sierpinski triangle
        LSystemRuleSet(
            name = "Sierpinski",
            axiom = "A",
            rules = mapOf(
                'A' to "B-A-B",
                'B' to "A+B+A"
            )
        ),
        // 4: Plant-like
        LSystemRuleSet(
            name = "Plant",
            axiom = "A",
            rules = mapOf(
                'A' to "F[+A][-A]FA",
                'F' to "FF"
            )
        ),
        // 5: Algae growth
        LSystemRuleSet(
            name = "Algae",
            axiom = "A",
            rules = mapOf(
                'A' to "AB",
                'B' to "A"
            )
        ),
        // 6: Binary tree
        LSystemRuleSet(
            name = "Binary",
            axiom = "A",
            rules = mapOf(
                'A' to "B[+A]-A",
                'B' to "BB"
            )
        ),
        // 7: Penrose tiling inspired
        LSystemRuleSet(
            name = "Penrose",
            axiom = "F++F++F++F++F",
            rules = mapOf(
                'F' to "F++F++F|F-F++F"
            )
        ),
        // 8: Hilbert curve
        LSystemRuleSet(
            name = "Hilbert",
            axiom = "A",
            rules = mapOf(
                'A' to "-BF+AFA+FB-",
                'B' to "+AF-BFB-FA+"
            )
        ),
        // 9: Musical ladder
        LSystemRuleSet(
            name = "Ladder",
            axiom = "F+F",
            rules = mapOf(
                'F' to "F+F-F"
            )
        )
    )

    // Internal state
    private var currentString: String = ""
    private var stringPosition: Int = 0
    private var turtleState: TurtleState = TurtleState(0, 1, 80, 0)
    private val stateStack: MutableList<TurtleState> = mutableListOf()
    private var lastGeneratedTick: Long = -1L
    private var generationCount: Int = 0
    private var currentIteration: Int = 0
    private var initialized: Boolean = false
    private var voiceReadPositions: IntArray = IntArray(4) { 0 }
    private var voiceTurtles: Array<TurtleState> = Array(4) { TurtleState(0, 1, 80, 0) }
    private val voiceStateStacks: Array<MutableList<TurtleState>> = Array(4) { mutableListOf() }

    override fun generate(tick: Long, state: EngineState, random: Random): List<NoteEvent> {
        val params = state.params
        val interval = getTickInterval(params.density)

        if (tick % interval != 0L) return emptyList()
        if (tick == lastGeneratedTick) return emptyList()
        lastGeneratedTick = tick

        if (!initialized) {
            initialize(state, random)
            initialized = true
        }

        if (currentString.isEmpty()) return emptyList()

        val events = mutableListOf<NoteEvent>()

        for (v in 0 until 4) {
            if (state.trackMutes.getOrElse(v) { false }) continue

            val readPos = voiceReadPositions[v]
            val turtle = voiceTurtles[v]
            val stack = voiceStateStacks[v]

            // Process characters until we find a note-producing one
            var charsProcessed = 0
            val maxCharsPerTick = 4 // Prevent infinite loops
            var noteProduced = false

            while (!noteProduced && charsProcessed < maxCharsPerTick) {
                val charIndex = (readPos + charsProcessed) % currentString.length
                val ch = currentString[charIndex]

                when (ch) {
                    'F' -> {
                        // Step forward: produce a note, move up in scale
                        turtle.degree += turtle.direction
                        noteProduced = true
                    }
                    'f' -> {
                        // Small step
                        turtle.degree += turtle.direction
                        noteProduced = true
                    }
                    'A', 'B' -> {
                        // Variables also produce notes
                        val varStep = if (ch == 'A') 1 else 2
                        turtle.degree += turtle.direction * varStep
                        noteProduced = true
                    }
                    '+' -> {
                        // Turn right: interval up
                        turtle.degree += 1
                        turtle.direction = 1
                    }
                    '-' -> {
                        // Turn left: interval down
                        turtle.degree -= 1
                        turtle.direction = -1
                    }
                    '[' -> {
                        // Push state
                        stack.add(turtle.copy())
                    }
                    ']' -> {
                        // Pop state
                        if (stack.isNotEmpty()) {
                            val restored = stack.removeAt(stack.size - 1)
                            turtle.degree = restored.degree
                            turtle.direction = restored.direction
                            turtle.velocity = restored.velocity
                            turtle.octaveOffset = restored.octaveOffset
                        }
                    }
                    '|' -> {
                        // Reverse direction
                        turtle.direction *= -1
                    }
                }
                charsProcessed++
            }

            voiceReadPositions[v] = (readPos + charsProcessed) % currentString.length

            if (noteProduced) {
                // Clamp degree to reasonable range
                val scaleSize = state.scale.degrees.size.coerceAtLeast(1)
                if (turtle.degree > scaleSize * 3) turtle.degree -= scaleSize * 2
                if (turtle.degree < -scaleSize * 2) turtle.degree += scaleSize * 2

                val baseOctave = when (v) {
                    0 -> 5
                    1 -> 4
                    2 -> 3
                    3 -> 2
                    else -> 4
                }
                val midiNote = scaleDegreeToMidi(
                    turtle.degree,
                    state.rootNote,
                    state.scale,
                    baseOctave + turtle.octaveOffset
                )

                val velocity = RhythmUtils.humanizeVelocity(
                    (turtle.velocity + (params.tension * 20).toInt() - v * 5).coerceIn(35, 120),
                    params.humanize
                )

                val duration = when {
                    params.growth > 0.7f -> RhythmUtils.QUARTER
                    params.growth > 0.4f -> RhythmUtils.EIGHTH
                    else -> RhythmUtils.SIXTEENTH
                }

                events.add(
                    NoteEvent(
                        track = v,
                        midiNote = midiNote.coerceIn(0, 127),
                        velocity = velocity,
                        durationTicks = duration,
                        startTick = RhythmUtils.humanizeTiming(tick, params.humanize),
                        modValue = (64 + (params.morph * 30 - 15)).toInt().coerceIn(0, 127)
                    )
                )
            }
        }

        // Periodically re-iterate the L-system for development
        generationCount++
        val reiterateRate = (32 + (1f - params.growth) * 96).toInt().coerceIn(16, 128)
        if (generationCount % reiterateRate == 0 && currentIteration < getMaxIterations(params.growth)) {
            iterateLSystem(state)
        }

        return events
    }

    override fun reset() {
        currentString = ""
        stringPosition = 0
        turtleState = TurtleState(0, 1, 80, 0)
        stateStack.clear()
        lastGeneratedTick = -1L
        generationCount = 0
        currentIteration = 0
        initialized = false
        voiceReadPositions = IntArray(4) { 0 }
        voiceTurtles = Array(4) { TurtleState(0, 1, 80, 0) }
        for (stack in voiceStateStacks) stack.clear()
    }

    // --- Initialization ---

    private fun initialize(state: EngineState, random: Random) {
        val params = state.params
        val ruleSetIndex = (params.material * (presetRules.size - 1)).toInt()
            .coerceIn(0, presetRules.size - 1)
        val ruleSet = presetRules[ruleSetIndex]

        currentString = ruleSet.axiom
        currentIteration = 0

        // Apply initial iterations
        val initialIterations = (1 + params.growth * 3).toInt().coerceIn(1, 4)
        repeat(initialIterations) {
            iterateLSystem(state)
        }

        // Initialize voice turtles at different starting positions
        for (v in 0 until 4) {
            voiceTurtles[v] = TurtleState(
                degree = v * 2,  // Stagger starting degrees
                direction = if (v % 2 == 0) 1 else -1,
                velocity = 80 - v * 5,
                octaveOffset = 0
            )
            voiceReadPositions[v] = (v * currentString.length / 4) % currentString.length.coerceAtLeast(1)
            voiceStateStacks[v].clear()
        }
    }

    // --- L-system iteration ---

    private fun iterateLSystem(state: EngineState) {
        val params = state.params
        val ruleSetIndex = (params.material * (presetRules.size - 1)).toInt()
            .coerceIn(0, presetRules.size - 1)
        val ruleSet = presetRules[ruleSetIndex]

        val sb = StringBuilder()
        for (ch in currentString) {
            val replacement = ruleSet.rules[ch]
            if (replacement != null) {
                sb.append(replacement)
            } else {
                sb.append(ch)
            }
        }

        // Limit string length to prevent memory issues
        val maxLength = 4096
        currentString = if (sb.length > maxLength) {
            sb.substring(0, maxLength)
        } else {
            sb.toString()
        }

        currentIteration++

        // Adjust read positions to stay in bounds
        for (v in 0 until 4) {
            if (currentString.isNotEmpty()) {
                voiceReadPositions[v] = voiceReadPositions[v] % currentString.length
            }
        }
    }

    private fun getMaxIterations(growth: Float): Int {
        return (2 + growth * 5).toInt().coerceIn(2, 7)
    }

    // --- Utility ---

    private fun scaleDegreeToMidi(
        degree: Int,
        rootNote: NoteName,
        scale: Scale,
        baseOctave: Int
    ): Int {
        val degrees = scale.degrees
        if (degrees.isEmpty()) return rootNote.midiValue + baseOctave * 12

        val octaveSize = degrees.size
        val octave = if (degree >= 0) degree / octaveSize else (degree - octaveSize + 1) / octaveSize
        val degreeInScale = ((degree % octaveSize) + octaveSize) % octaveSize
        val semitoneOffset = degrees[degreeInScale]

        return (rootNote.midiValue + (baseOctave + octave) * 12 + semitoneOffset).coerceIn(0, 127)
    }

    private fun getTickInterval(density: Float): Int {
        return when {
            density > 0.8f -> RhythmUtils.SIXTEENTH
            density > 0.6f -> RhythmUtils.EIGHTH
            density > 0.3f -> RhythmUtils.QUARTER
            else -> RhythmUtils.HALF
        }
    }
}
