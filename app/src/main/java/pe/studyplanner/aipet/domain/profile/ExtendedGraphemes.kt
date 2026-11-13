package pe.studyplanner.aipet.domain.profile

/**
 * Grafemas extendidos por defecto, UAX #29 revisión 47 / Unicode 17.0.0.
 * Las propiedades están fijadas en GraphemeData17: ni regex ni ICU del dispositivo
 * deciden el límite del nombre. No garantiza que la fuente pueda dibujar cada emoji.
 * Las versiones posteriores de Unicode requieren actualizar datos y corpus juntos.
 * https://www.unicode.org/reports/tr29/tr29-47.html#Grapheme_Cluster_Boundary_Rules
 */
internal object ExtendedGraphemes {
    fun count(value: String): Int {
        var count = 0
        forEachClusterStart(value) { count++ }
        return count
    }

    /** Offsets UTF-16, incluidos inicio/fin, para comprobar las fronteras del corpus. */
    internal fun boundaries(value: String): List<Int> = buildList {
        forEachClusterStart(value) { add(it) }
        add(value.length)
    }

    private inline fun forEachClusterStart(value: String, onStart: (Int) -> Unit) {
        var index = 0
        var previous = GraphemeData17.OTHER
        var regionalRun = 0
        var pictographicExtendRun = false
        var previousZwjAfterPictographic = false
        // 0: sin consonante; 1: consonante + Extend*; 2: incluye al menos un Linker.
        var indicState = 0
        while (index < value.length) {
            val codePoint = Character.codePointAt(value, index)
            val current = GraphemeData17.graphemeBreak(codePoint)
            val indic = GraphemeData17.indicConjunctBreak(codePoint)
            val pictographic = GraphemeData17.isExtendedPictographic(codePoint)
            val boundary = when {
                index == 0 -> true // GB1
                previous == GraphemeData17.CR && current == GraphemeData17.LF -> false // GB3
                isControl(previous) || isControl(current) -> true // GB4, GB5
                previous == GraphemeData17.L && (
                    current == GraphemeData17.L || current == GraphemeData17.V ||
                        current == GraphemeData17.LV || current == GraphemeData17.LVT
                    ) -> false // GB6
                (previous == GraphemeData17.LV || previous == GraphemeData17.V) &&
                    (current == GraphemeData17.V || current == GraphemeData17.T) -> false // GB7
                (previous == GraphemeData17.LVT || previous == GraphemeData17.T) &&
                    current == GraphemeData17.T -> false // GB8
                current == GraphemeData17.EXTEND || current == GraphemeData17.ZWJ -> false // GB9
                current == GraphemeData17.SPACINGMARK -> false // GB9a
                previous == GraphemeData17.PREPEND -> false // GB9b
                indic == GraphemeData17.INCB_CONSONANT && indicState == 2 -> false // GB9c
                pictographic && previousZwjAfterPictographic -> false // GB11
                previous == GraphemeData17.REGIONAL_INDICATOR &&
                    current == GraphemeData17.REGIONAL_INDICATOR && regionalRun % 2 == 1 -> false // GB12/13
                else -> true // GB999
            }
            if (boundary) onStart(index)

            previousZwjAfterPictographic = current == GraphemeData17.ZWJ && pictographicExtendRun
            pictographicExtendRun = pictographic || (current == GraphemeData17.EXTEND && pictographicExtendRun)
            indicState = when (indic) {
                GraphemeData17.INCB_CONSONANT -> 1
                GraphemeData17.INCB_LINKER -> if (indicState > 0) 2 else 0
                GraphemeData17.INCB_EXTEND -> indicState
                else -> 0
            }
            regionalRun = if (current == GraphemeData17.REGIONAL_INDICATOR) regionalRun + 1 else 0
            previous = current
            index += Character.charCount(codePoint)
        }
        // GB2 es el fin de la cadena; count no necesita materializar esa frontera.
    }

    private fun isControl(property: Int): Boolean =
        property == GraphemeData17.CR || property == GraphemeData17.LF || property == GraphemeData17.CONTROL
}
