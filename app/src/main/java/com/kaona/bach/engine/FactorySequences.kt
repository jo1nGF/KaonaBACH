package com.kaona.bach.engine

import com.kaona.bach.engine.ChordQuality.*

/**
 * 32 formas de progressão de fábrica, cada uma em 3 velocidades (QUICK, NORMAL, LONG),
 * totalizando 96 sequências. Todas usam VOICING (inversão automática, de menor movimento).
 * As raízes são graus em semitons a partir da nota raiz, então seguem a tonalidade escolhida.
 */
object FactorySequences {
    val speedLabels = arrayOf("QUICK", "NORMAL", "LONG")

    // Índices de duração: 0 = 1 BEAT, 1 = 2 BEATS, 2 = 1 BAR, 3 = 2 BARS, 4 = 3 BARS, 5 = 4 BARS, 6 = 8 BARS
    private const val B2 = 1
    private const val BAR = 2
    private const val BARS2 = 3

    private fun c(root: Int, quality: ChordQuality, length: Int) =
        ChordStep(root, quality, length, ChordStep.VOICING)

    private val forms: List<Pair<String, List<ChordStep>>> = listOf(
        "CIRCLE FIFTHS" to listOf(
            c(0, MAJ, BAR), c(5, MAJ, BAR), c(11, DIM, BAR), c(4, MIN, BAR),
            c(9, MIN, BAR), c(2, MIN, BAR), c(7, DOM7, BAR), c(0, MAJ, BAR)
        ),
        "II V I MAJOR" to listOf(c(2, MIN7, BAR), c(7, DOM7, BAR), c(0, MAJ7, BARS2)),
        "II V I MINOR" to listOf(c(2, HALFDIM, BAR), c(7, DOM7, BAR), c(0, MIN7, BARS2)),
        "TURNAROUND" to listOf(c(0, MAJ7, BAR), c(9, MIN7, BAR), c(2, MIN7, BAR), c(7, DOM7, BAR)),
        "BACKDOOR" to listOf(c(5, MIN7, BAR), c(10, DOM7, BAR), c(0, MAJ7, BARS2)),
        "POP AXIS" to listOf(c(0, MAJ, BAR), c(7, MAJ, BAR), c(9, MIN, BAR), c(5, MAJ, BAR)),
        "POP MINOR" to listOf(c(9, MIN, BAR), c(5, MAJ, BAR), c(0, MAJ, BAR), c(7, MAJ, BAR)),
        "ROCK MIXOLYD" to listOf(c(0, MAJ, BAR), c(10, MAJ, BAR), c(5, MAJ, BAR), c(0, MAJ, BAR)),
        "ANDALUSIAN" to listOf(c(0, MIN, BAR), c(10, MAJ, BAR), c(8, MAJ, BAR), c(7, MAJ, BAR)),
        "BLUES 12" to listOf(
            c(0, DOM7, BAR), c(0, DOM7, BAR), c(0, DOM7, BAR), c(0, DOM7, BAR),
            c(5, DOM7, BAR), c(5, DOM7, BAR), c(0, DOM7, BAR), c(0, DOM7, BAR),
            c(7, DOM7, BAR), c(5, DOM7, BAR), c(0, DOM7, BAR), c(7, DOM7, BAR)
        ),
        "QUICK BLUES" to listOf(
            c(0, DOM7, BAR), c(5, DOM7, BAR), c(0, DOM7, BAR), c(0, DOM7, BAR),
            c(5, DOM7, BAR), c(5, DOM7, BAR), c(0, DOM7, BAR), c(0, DOM7, BAR),
            c(7, DOM7, BAR), c(5, DOM7, BAR), c(0, DOM7, BAR), c(7, DOM7, BAR)
        ),
        "MINOR BLUES" to listOf(
            c(0, MIN7, BAR), c(0, MIN7, BAR), c(0, MIN7, BAR), c(0, MIN7, BAR),
            c(5, MIN7, BAR), c(5, MIN7, BAR), c(0, MIN7, BAR), c(0, MIN7, BAR),
            c(8, DOM7, BAR), c(7, DOM7, BAR), c(0, MIN7, BAR), c(7, DOM7, BAR)
        ),
        "RHYTHM A" to listOf(
            c(0, SIXTH, B2), c(9, MIN7, B2), c(2, MIN7, B2), c(7, DOM7, B2),
            c(0, SIXTH, B2), c(9, MIN7, B2), c(2, MIN7, B2), c(7, DOM7, B2)
        ),
        "RHYTHM BRIDGE" to listOf(c(4, DOM7, BARS2), c(9, DOM7, BARS2), c(2, DOM7, BARS2), c(7, DOM7, BARS2)),
        "JAZZ AABA" to listOf(
            c(0, MAJ7, BAR), c(9, MIN7, BAR), c(2, MIN7, BAR), c(7, DOM7, BAR),
            c(4, MIN7, BAR), c(9, DOM7, BAR), c(2, MIN7, BAR), c(7, DOM7, BAR)
        ),
        "MINOR FIFTHS" to listOf(
            c(0, MIN, BAR), c(5, MIN, BAR), c(10, MAJ, BAR), c(3, MAJ, BAR),
            c(8, MAJ, BAR), c(2, HALFDIM, BAR), c(7, DOM7, BAR), c(0, MIN, BAR)
        ),
        "MAJOR THIRDS" to listOf(c(0, MAJ7, BARS2), c(4, MAJ7, BARS2), c(8, MAJ7, BARS2)),
        "COLTRANE CYCLE" to listOf(
            c(0, MAJ7, B2), c(3, DOM7, B2), c(8, MAJ7, B2), c(11, DOM7, B2), c(4, MAJ7, B2)
        ),
        "BOSSA MAJOR" to listOf(
            c(0, MAJ7, BARS2), c(2, DOM7, BARS2), c(2, MIN7, BAR), c(1, DOM7, BAR)
        ),
        "BOSSA MINOR" to listOf(c(0, MIN7, BARS2), c(5, MIN7, BAR), c(2, HALFDIM, BAR), c(7, DOM7, BARS2)),
        "MONTUNO" to listOf(c(0, MAJ, BAR), c(5, MAJ, BAR), c(7, MAJ, BAR), c(5, MAJ, BAR)),
        "LATIN 2 3" to listOf(c(0, MIN, BAR), c(10, MAJ, BAR), c(8, MAJ, BAR), c(7, DOM7, BAR)),
        "PACHELBEL" to listOf(
            c(0, MAJ, BAR), c(7, MAJ, BAR), c(9, MIN, BAR), c(4, MIN, BAR),
            c(5, MAJ, BAR), c(0, MAJ, BAR), c(5, MAJ, BAR), c(7, MAJ, BAR)
        ),
        "FOLIA" to listOf(
            c(0, MIN, BAR), c(7, MAJ, BAR), c(0, MIN, BAR), c(10, MAJ, BAR),
            c(3, MAJ, BAR), c(10, MAJ, BAR), c(0, MIN, BAR), c(7, MAJ, BAR)
        ),
        "ROMANESCA" to listOf(
            c(3, MAJ, BAR), c(10, MAJ, BAR), c(0, MIN, BAR), c(7, MAJ, BAR),
            c(3, MAJ, BAR), c(10, MAJ, BAR), c(0, MIN, BAR), c(7, MAJ, BAR)
        ),
        "PASSAMEZZO" to listOf(
            c(0, MIN, BAR), c(10, MAJ, BAR), c(0, MIN, BAR), c(7, MAJ, BAR),
            c(0, MIN, BAR), c(10, MAJ, BAR), c(7, MAJ, BAR), c(0, MIN, BAR)
        ),
        "LAMENT" to listOf(c(0, MIN, BAR), c(10, MAJ, BAR), c(8, MAJ, BAR), c(7, DOM7, BAR)),
        "DORIAN VAMP" to listOf(c(0, MIN7, BARS2), c(5, DOM7, BARS2)),
        "MIXOLYD VAMP" to listOf(c(0, MAJ, BARS2), c(10, MAJ, BARS2)),
        "PHRYGIAN VAMP" to listOf(c(0, MIN, BARS2), c(1, MAJ, BARS2)),
        "AMBIENT THIRDS" to listOf(c(0, MAJ9, BARS2), c(4, MIN7, BARS2), c(9, MIN9, BARS2), c(5, MAJ7, BARS2)),
        "CINEMATIC" to listOf(c(0, MIN, BARS2), c(8, MAJ, BARS2), c(3, MAJ, BARS2), c(10, MAJ, BARS2))
    )

    val names: List<String> = forms.map { it.first }

    /** Monta a sequência da forma [form] na velocidade [speed] (0 = QUICK, 1 = NORMAL, 2 = LONG). */
    fun build(form: Int, speed: Int): HarmonicSequence {
        val steps = forms[form.coerceIn(0, forms.size - 1)].second.map {
            it.copy(length = (it.length + speed - 1).coerceIn(0, ChordStep.LENGTH_BEATS.size - 1))
        }
        return HarmonicSequence(
            steps = List(HarmonicSequence.SIZE) { steps.getOrElse(it) { ChordStep() } },
            enabled = true
        )
    }
}
