package pe.studyplanner.aipet.domain.knowledge

import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln
import pe.studyplanner.aipet.domain.json.MiniJson
import pe.studyplanner.aipet.domain.tutor.Topic

/**
 * Una ficha de lo que la mascota sabe sin Internet.
 *
 * @property kind `def` (explicación de un concepto o tema) o `preg` (una pregunta propia ya resuelta).
 * @property question enunciado de la pregunta resuelta (solo en `preg`).
 * @property topicId tema de práctica relacionado, si lo hay.
 */
data class KnowledgeEntry(
    val id: String,
    val kind: String,
    val course: String,
    val title: String,
    val text: String,
    val question: String = "",
    val keywords: List<String> = emptyList(),
    val topicId: String = "",
)

/**
 * Resultado de una búsqueda. [coverage] es la parte de las palabras de la pregunta que aparecen en la ficha y
 * [titleHits] cuántas aparecen en su título o en sus palabras clave.
 */
data class KnowledgeHit(val entry: KnowledgeEntry, val score: Double, val coverage: Double, val titleHits: Int, val queryTerms: Int) {
    /** Bastante seguro como para responder sin Internet. */
    val strong: Boolean
        get() = when {
            queryTerms <= 0 -> false
            queryTerms == 1 -> titleHits >= 1
            else -> coverage >= 0.66 && titleHits >= 1
        }
}

/**
 * Lo que la mascota sabe sin Internet: busca la ficha que mejor responde una pregunta escrita o dictada, con BM25 sobre
 * título (pesa 3), palabras clave (2), enunciado y explicación (1). Sin tildes, sin palabras vacías y con plurales
 * simplificados, así «células» encuentra «célula». Kotlin puro, sin Android: se prueba en la JVM.
 */
class StudyKnowledge(val entries: List<KnowledgeEntry>, private val courseNames: Map<String, String> = emptyMap()) {

    private class Doc(val weights: Map<String, Double>, val length: Double, val titleTerms: Set<String>)

    private val docs: List<Doc> = entries.map { e ->
        val w = HashMap<String, Double>()
        fun add(text: String, weight: Double) { for (t in terms(text)) w[t] = (w[t] ?: 0.0) + weight }
        add(e.title, 3.0)
        e.keywords.forEach { add(it, 2.0) }
        add(e.question, 1.0)
        add(e.text, 1.0)
        Doc(w, w.values.sum(), (terms(e.title) + e.keywords.flatMap { terms(it) }).toSet())
    }
    private val avgLength = docs.map { it.length }.average().takeIf { it > 0 } ?: 1.0
    private val postings: Map<String, List<Int>> = HashMap<String, MutableList<Int>>().also { map ->
        docs.forEachIndexed { i, d -> for (t in d.weights.keys) map.getOrPut(t) { mutableListOf() } += i }
    }

    private fun idf(term: String): Double {
        val df = postings[term]?.size ?: return 0.0
        return ln(1.0 + (docs.size - df + 0.5) / (df + 0.5))
    }

    /** Las [limit] fichas que mejor responden [question], de mejor a peor. */
    fun search(question: String, limit: Int = 3): List<KnowledgeHit> {
        val query = terms(question).distinct()
        if (query.isEmpty() || docs.isEmpty()) return emptyList()
        val scores = HashMap<Int, Double>()
        for (t in query) {
            val idf = idf(t)
            for (i in postings[t].orEmpty()) {
                val d = docs[i]
                val tf = d.weights.getValue(t)
                scores[i] = (scores[i] ?: 0.0) + idf * tf * (K1 + 1) / (tf + K1 * (1 - B + B * d.length / avgLength))
            }
        }
        return scores.entries.map { (i, s) ->
            val d = docs[i]
            val matched = query.count { it in d.weights }
            val inTitle = query.count { it in d.titleTerms }
            // Las explicaciones de conceptos ganan a las preguntas sueltas cuando empatan.
            val boost = if (entries[i].kind == KIND_DEF) 1.15 else 1.0
            KnowledgeHit(entries[i], s * boost, matched.toDouble() / query.size, inTitle, query.size)
        }.sortedWith(compareByDescending<KnowledgeHit> { it.score }.thenBy { it.entry.id }).take(limit)
    }

    /** La mejor ficha, o `null` si nada se parece lo suficiente. */
    fun best(question: String): KnowledgeHit? = search(question, 1).firstOrNull()?.takeIf { it.coverage >= 0.5 }

    /** Respuesta lista para la burbuja y la voz (texto simple, unas pocas frases). */
    fun reply(hit: KnowledgeHit): String {
        val e = hit.entry
        val course = courseNames[e.course]?.let { " ($it)" }.orEmpty()
        return if (e.kind == KIND_PREG) {
            val answer = e.text.removePrefix("Respuesta: ")
            clip("Una pregunta parecida: «${e.question}». Respuesta: $answer", MAX_REPLY)
        } else {
            clip("${e.title}$course. ${e.text}", MAX_REPLY)
        }
    }

    companion object {
        const val KIND_DEF = "def"
        const val KIND_PREG = "preg"
        private const val K1 = 1.2
        private const val B = 0.75
        const val MAX_REPLY = 520

        /** Lee `assets/conocimiento/base.json`. Una ficha dañada se omite; un archivo dañado da lista vacía. */
        fun parse(json: String): Pair<List<KnowledgeEntry>, Map<String, String>> {
            val root = runCatching { MiniJson.parse(json) }.getOrNull() as? Map<*, *> ?: return emptyList<KnowledgeEntry>() to emptyMap()
            val courses = (root["cursos"] as? Map<*, *>).orEmpty().mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? String)?.let { key to it } } }.toMap()
            val list = (root["entradas"] as? List<*>).orEmpty().mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                val id = m["id"] as? String ?: return@mapNotNull null
                val title = (m["t"] as? String).orEmpty()
                val text = (m["x"] as? String).orEmpty()
                if (title.isBlank() || text.isBlank()) return@mapNotNull null
                KnowledgeEntry(
                    id = id,
                    kind = if (m["tipo"] == KIND_PREG) KIND_PREG else KIND_DEF,
                    course = (m["c"] as? String).orEmpty(),
                    title = title,
                    text = text,
                    question = (m["q"] as? String).orEmpty(),
                    keywords = (m["k"] as? List<*>).orEmpty().filterIsInstance<String>(),
                    topicId = (m["tema"] as? String).orEmpty(),
                )
            }
            return list to courses
        }

        /** Fichas de los temas de la app que no están en la base (por ejemplo, los de matemática con generador). */
        fun fromTopics(topics: List<Topic>, known: Set<String>): List<KnowledgeEntry> = topics.filter { it.id !in known }.map { t ->
            KnowledgeEntry(
                id = "app-${t.id}",
                kind = KIND_DEF,
                course = t.area.label,
                title = t.title,
                text = (listOf(t.objective) + t.explanation).joinToString(" "),
                keywords = emptyList(),
                topicId = t.id,
            )
        }

        /** Palabras con contenido de un texto: sin tildes, sin palabras vacías y en singular simple. */
        fun terms(text: String): List<String> = normalize(text).split(' ').filter { it.length > 1 && it !in STOP }.map(::stem)

        fun normalize(text: String): String =
            Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
                .replace(Regex("[^a-z0-9]+"), " ").trim()

        /** Plural a singular, lo justo para que pregunta y ficha coincidan: «raíces» → «raiz», «funciones» → «funcion». */
        fun stem(word: String): String = when {
            word.length > 5 && word.endsWith("ces") -> word.dropLast(3) + "z"
            word.length > 4 && word.endsWith("es") && word[word.length - 3] !in "aeiou" -> word.dropLast(2)
            word.length > 3 && word.endsWith("s") && word[word.length - 2] in "aeiou" -> word.dropLast(1)
            else -> word
        }

        private fun clip(text: String, max: Int): String {
            val clean = text.replace(Regex("\\s+"), " ").trim()
            if (clean.length <= max) return clean
            val cut = clean.take(max)
            val end = maxOf(cut.lastIndexOf(". "), cut.lastIndexOf("? "), cut.lastIndexOf("; "))
            return if (end > max / 2) cut.take(end + 1) else cut.substringBeforeLast(' ') + "…"
        }

        /** Palabras vacías y de relleno de una pregunta («¿qué es…?», «explícame…»): no dicen de qué tema se habla. */
        private val STOP = setOf(
            "a", "al", "algo", "algun", "alguna", "como", "con", "cual", "cuales", "cuando", "cuanto", "cuanta", "cuantos", "cuantas",
            "de", "del", "donde", "el", "ella", "ellas", "ellos", "en", "entre", "era", "es", "esa", "ese", "eso", "esta", "estan",
            "este", "esto", "estos", "estas", "fue", "ha", "han", "hay", "la", "las", "le", "les", "lo", "los", "me", "mi", "mis",
            "mas", "muy", "no", "nos", "o", "para", "pero", "por", "porque", "que", "quien", "quienes", "se", "ser", "si", "sin",
            "sobre", "son", "su", "sus", "te", "tu", "tus", "un", "una", "uno", "unos", "unas", "y", "ya", "yo", "cada", "tambien",
            "explica", "explicame", "explicar", "explicas", "dime", "decir", "define", "defineme", "definir", "definicion", "significa",
            "significado", "concepto", "quiero", "saber", "sabes", "puedes", "podrias", "ayuda", "ayudame", "tema", "favor", "porfa",
            "hola", "oye", "mira", "entiendo", "entender", "dame", "cuentame", "habla", "hablame", "acerca", "respecto", "seria",
            "pregunta", "responde", "respuesta", "kiro",
        )
    }
}
