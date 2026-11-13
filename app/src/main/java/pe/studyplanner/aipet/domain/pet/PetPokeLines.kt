package pe.studyplanner.aipet.domain.pet

/** Lo que dice la mascota según la parte que se toca (la 3D avisa cuál fue). */
object PetPokeLines {
    private val lines = mapOf(
        "cabeza" to listOf("¡Jaja, cosquillas!", "¡Me despeinas!"),
        "mano" to listOf("¡Hola!", "¡Choca esos cinco!"),
        "panza" to listOf("¡Jaja, ahí no!", "¡Me haces reír!"),
        "pies" to listOf("¡Salto!", "¡Arriba!"),
        "cola" to listOf("¡Mi cola!", "¡Eso fue mi cola!"),
        "baile" to listOf("¡A bailar!", "¡Qué ritmo!"),
    )
    private val other = listOf("¡Aquí estoy!", "¿Estudiamos?", "¡Me gusta!")

    fun forPart(part: String, pick: (Int) -> Int = { kotlin.random.Random.nextInt(it) }): String {
        val list = lines[part] ?: other
        return list[pick(list.size).coerceIn(0, list.size - 1)]
    }
}
