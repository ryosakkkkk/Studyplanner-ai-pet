package pe.studyplanner.aipet.domain.pet

import pe.studyplanner.aipet.domain.profile.AvatarStyle

/**
 * Expresiones y poses de las láminas de diseño de cada mascota (`assets/stickers/<mascota>/<clave>.webp`). Se usan
 * como reacciones junto a la mascota 3D, en avisos y en la galería «Reacciones».
 */
object PetStickers {
    data class Sticker(val key: String, val label: String)
    data class Group(val title: String, val items: List<Sticker>)

    val GROUPS: List<Group> = listOf(
        Group("Emociones", listOf(
            Sticker("feliz", "Feliz"), Sticker("saludo", "Saludo"), Sticker("risa", "Risa"), Sticker("emocionado", "Emocionado"),
            Sticker("duda", "Duda"), Sticker("sorpresa", "Sorpresa"), Sticker("preocupado", "Preocupado"), Sticker("triste", "Triste"),
            Sticker("enojado", "Enojado"), Sticker("asombro", "Asombro"), Sticker("amor", "Cariño"), Sticker("dormido", "Dormido"),
        )),
        Group("Estudio", listOf(
            Sticker("hola", "Hola"), Sticker("idea", "Idea"), Sticker("leer", "Leer"), Sticker("laptop", "Computadora"),
            Sticker("libros", "Estudiar"), Sticker("escribir", "Escribir"), Sticker("calendario", "Agenda"), Sticker("lista", "Lista"),
            Sticker("presentar", "Presentar"), Sticker("caminar", "Caminar"), Sticker("correr", "Correr"),
        )),
        Group("Descanso", listOf(
            Sticker("meditar", "Calma"), Sticker("ejercicio", "Ejercicio"), Sticker("futbol", "Fútbol"), Sticker("basquet", "Básquet"),
            Sticker("videojuego", "Juegos"), Sticker("musica", "Música"), Sticker("lectura", "Lectura"), Sticker("dormir", "Dormir"),
            Sticker("bateria", "Cansado"),
        )),
        Group("Logros y avisos", listOf(
            Sticker("celebrar", "Celebrar"), Sticker("graduacion", "Graduación"), Sticker("trofeo", "Trofeo"), Sticker("estrella", "Estrella"),
            Sticker("meta", "Meta"), Sticker("progreso", "Progreso"), Sticker("tareas", "Tareas"), Sticker("campana", "Aviso"),
            Sticker("alarma", "Alarma"), Sticker("mapa", "Ruta"), Sticker("corazon", "Corazón"), Sticker("seguridad", "Privacidad"),
            Sticker("nube", "Respaldo"), Sticker("holograma", "Tecnología"), Sticker("ajustes", "Ajustes"),
        )),
    )

    val ALL: List<Sticker> = GROUPS.flatMap { it.items }

    /** Carpeta de cada mascota dentro de `assets/stickers`. */
    fun folder(style: AvatarStyle): String = when (style) {
        AvatarStyle.FOX -> "zorro"
        AvatarStyle.CAT -> "gato"
        AvatarStyle.PANDA -> "panda"
        AvatarStyle.OWL -> "buho"
        AvatarStyle.PENGUIN -> "pinguino"
        AvatarStyle.RABBIT -> "conejo"
    }

    fun path(style: AvatarStyle, key: String): String = "stickers/${folder(style)}/$key.webp"

    /**
     * Reacción que acompaña a un estado de la mascota, o null si no lleva (reposo, hablar, escuchar).
     * Las caras («feliz», «preocupado») van como globo junto a la 3D; las poses, como insignia.
     */
    fun forMood(mood: String): String? = when (mood.trim().lowercase()) {
        "happy", "correct", "tasks" -> "feliz"
        "celebrate" -> "celebrar"
        "trophy" -> "trofeo"
        "confused", "wrong", "sad" -> "preocupado"
        "worried", "surprised", "alert" -> "sorpresa"
        "idea" -> "idea"
        "think", "thinking" -> "duda"
        "wave", "greet", "hello" -> "saludo"
        "reminder" -> "campana"
        "notify" -> "alarma"
        "plan" -> "calendario"
        "rest" -> "lectura"
        "sleep", "sleeping" -> "dormido"
        "read" -> "leer"
        "write" -> "escribir"
        "studying" -> "laptop"
        "progress" -> "progreso"
        "present" -> "presentar"
        else -> null
    }

    /** Estado de la mascota 3D que acompaña a cada expresión o pose (galería «Reacciones»). */
    fun moodFor(key: String): String = when (key) {
        "feliz", "risa", "emocionado", "asombro", "amor", "corazon", "estrella" -> "happy"
        "saludo", "hola" -> "wave"
        "duda", "libros", "leer", "laptop", "escribir" -> "think"
        "sorpresa" -> "surprised"
        "preocupado", "triste" -> "sad"
        "enojado" -> "angry"
        "dormido", "dormir", "lectura", "bateria", "meditar" -> "rest"
        "idea", "presentar" -> "idea"
        "celebrar", "graduacion", "trofeo" -> "celebrate"
        "ejercicio", "futbol", "basquet", "correr" -> "run"
        "caminar", "mapa" -> "walk"
        "musica", "videojuego" -> "dance"
        "campana", "alarma" -> "reminder"
        else -> "yes"
    }

    /** Si la reacción es una pose de cuerpo entero (se muestra como imagen principal cuando no hay 3D). */
    fun isFullBody(key: String): Boolean = GROUPS.first().items.none { it.key == key }
}
