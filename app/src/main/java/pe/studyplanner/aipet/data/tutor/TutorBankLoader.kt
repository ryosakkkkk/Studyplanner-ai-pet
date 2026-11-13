package pe.studyplanner.aipet.data.tutor

import pe.studyplanner.aipet.domain.tutor.QuestionBankParser
import pe.studyplanner.aipet.domain.tutor.TutorCatalog

/**
 * Lee los bancos de preguntas empaquetados en `assets/tutor/` y arma el catálogo de la app. Es de Kotlin puro: quien lo
 * llama le da una función que lee un archivo de assets, así se prueba en la JVM con los archivos reales.
 *
 * Nunca lanza: si un archivo falta o no cumple el esquema, se omite SOLO ese archivo, se anota el motivo en
 * [Loaded.errors] y la app sigue con matemática y con los demás bancos.
 */
object TutorBankLoader {

    /** Archivos de preguntas empaquetados, en el orden del temario. */
    val ASSET_FILES = listOf("tutor/banco_preguntas_v1.json", "tutor/banco_preguntas_v1b.json", "tutor/banco_preguntas_v2.json")

    data class Loaded(val catalog: TutorCatalog, val files: List<String>, val errors: List<String>)

    fun load(files: List<String> = ASSET_FILES, readAsset: (String) -> String): Loaded {
        var catalog = TutorCatalog.MATH_ONLY
        val loaded = mutableListOf<String>()
        val errors = mutableListOf<String>()
        for (file in files) {
            try {
                catalog = catalog.plus(QuestionBankParser.parse(readAsset(file)))
                loaded += file
            } catch (e: Exception) {
                errors += "$file: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        return Loaded(catalog, loaded, errors)
    }
}
