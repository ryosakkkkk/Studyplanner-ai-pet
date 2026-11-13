package pe.studyplanner.aipet.domain.pet

import pe.studyplanner.aipet.domain.profile.AvatarStyle

/**
 * Personalización de cada mascota: colores por prenda, piezas que se pueden quitar (la mochila) y accesorios 3D
 * hechos en Blender (lentes, corona, moño). Los mismos identificadores usa el visor 3D (`ZONES`, `PARTS` y
 * `<mascota>_extras.glb` en `pet-viewer.html`).
 */
object PetOutfits {
    data class Zone(val id: String, val label: String)
    data class Part(val id: String, val label: String)
    data class Accessory(val id: String, val label: String)

    /** Color de la paleta. [hex] null deja el color original del modelo. */
    data class Swatch(val name: String, val hex: String?)

    /** Colores vivos, al estilo de los colores del sistema de Apple, más tonos claros y oscuros de cada uno. */
    val PALETTE: List<Swatch> = listOf(
        Swatch("Original", null),
        Swatch("Rojo", "#FF3B30"), Swatch("Naranja", "#FF9500"), Swatch("Amarillo", "#FFCC00"),
        Swatch("Verde", "#34C759"), Swatch("Menta", "#00C7BE"), Swatch("Cian", "#32ADE6"),
        Swatch("Azul", "#007AFF"), Swatch("Índigo", "#5856D6"), Swatch("Morado", "#AF52DE"),
        Swatch("Rosa", "#FF2D55"), Swatch("Café", "#A2845E"), Swatch("Grafito", "#48484A"),
        Swatch("Coral", "#FF7A6B"), Swatch("Durazno", "#FFB347"), Swatch("Limón", "#E5E040"),
        Swatch("Lima", "#9BE15D"), Swatch("Turquesa", "#2ED3C6"), Swatch("Cielo", "#7CC8FF"),
        Swatch("Lavanda", "#B9A3FF"), Swatch("Chicle", "#FF8FC7"), Swatch("Vino", "#8E1F3B"),
        Swatch("Bosque", "#1E7A46"), Swatch("Marino", "#1F3A93"), Swatch("Blanco", "#F2F2F7"),
    )

    fun zones(style: AvatarStyle): List<Zone> = when (style) {
        AvatarStyle.FOX -> listOf(Zone("ropa", "Chaqueta"), Zone("audifonos", "Audífonos"), Zone("mochila", "Mochila"), Zone("botas", "Botas"))
        AvatarStyle.CAT -> listOf(Zone("ropa", "Polera"), Zone("mochila", "Mochila"), Zone("zapatillas", "Zapatillas"))
        AvatarStyle.PANDA -> listOf(Zone("ropa", "Bufanda"), Zone("mochila", "Mochila"))
        AvatarStyle.OWL -> listOf(Zone("ropa", "Chaleco"), Zone("mochila", "Bolso"), Zone("birrete", "Birrete"))
        AvatarStyle.PENGUIN -> listOf(Zone("gorra", "Gorra"), Zone("ropa", "Corbatín"), Zone("mochila", "Mochila"))
        AvatarStyle.RABBIT -> listOf(Zone("ropa", "Polera"), Zone("mochila", "Mochila"), Zone("zapatillas", "Zapatillas"))
    }

    /** Piezas del modelo que se pueden quitar y volver a poner (la mochila del gato está unida al cuerpo: no se quita). */
    fun parts(style: AvatarStyle): List<Part> = when (style) {
        AvatarStyle.FOX, AvatarStyle.PANDA, AvatarStyle.PENGUIN, AvatarStyle.RABBIT -> listOf(Part("mochila", "Mochila"))
        else -> emptyList()
    }

    /** Accesorios 3D que le quedan a cada mascota (el búho ya lleva lentes y birrete; el pingüino, gorra). */
    fun accessories(style: AvatarStyle): List<Accessory> = when (style) {
        AvatarStyle.FOX, AvatarStyle.CAT, AvatarStyle.PANDA, AvatarStyle.RABBIT -> listOf(Accessory("lentes", "Lentes"), Accessory("corona", "Corona"), Accessory("mono", "Moño"))
        AvatarStyle.PENGUIN -> listOf(Accessory("lentes", "Lentes"), Accessory("mono", "Moño"))
        AvatarStyle.OWL -> listOf(Accessory("mono", "Moño"))
    }

    /** Clave de preferencia de una zona, p. ej. «zorro.ropa». */
    fun key(style: AvatarStyle, zone: String): String? = PetClips.modelName(style)?.let { "$it.$zone" }

    /** Clave de una pieza escondida, p. ej. «zorro.oculta.mochila». */
    fun hiddenKey(style: AvatarStyle, part: String): String? = PetClips.modelName(style)?.let { "$it.oculta.$part" }

    /** Clave de un accesorio puesto, p. ej. «zorro.acc.lentes» (valor: color o «on» para su color original). */
    fun accessoryKey(style: AvatarStyle, id: String): String? = PetClips.modelName(style)?.let { "$it.acc.$id" }

    const val WORN = "on"

    /** Solo acepta colores de la paleta; cualquier otro valor guardado vuelve al original. */
    fun sanitize(hex: String?): String? = PALETTE.firstOrNull { it.hex != null && it.hex.equals(hex, ignoreCase = true) }?.hex

    fun swatchName(hex: String?): String = PALETTE.firstOrNull { it.hex != null && it.hex.equals(hex, ignoreCase = true) }?.name ?: "Original"

    /** Colores de una mascota a partir de todas las preferencias guardadas: zona → color (solo las cambiadas). */
    fun colorsFor(style: AvatarStyle, saved: Map<String, String>): Map<String, String> =
        zones(style).mapNotNull { z -> key(style, z.id)?.let { k -> sanitize(saved[k])?.let { z.id to it } } }.toMap()

    /** Piezas escondidas de una mascota. */
    fun hiddenFor(style: AvatarStyle, saved: Map<String, String>): Set<String> =
        parts(style).filter { p -> hiddenKey(style, p.id)?.let { saved[it] == "1" } == true }.map { it.id }.toSet()

    /** Accesorios puestos: id → color (null = su color original). */
    fun accessoriesFor(style: AvatarStyle, saved: Map<String, String>): Map<String, String?> =
        accessories(style).mapNotNull { a ->
            val v = accessoryKey(style, a.id)?.let { saved[it] } ?: return@mapNotNull null
            a.id to sanitize(v)
        }.toMap()

    /** Objeto JSON para `PetViewer.setColors`: zonas (null = original), piezas escondidas y accesorios puestos. */
    fun toJson(style: AvatarStyle, colors: Map<String, String>, hidden: Set<String> = emptySet(),
        worn: Map<String, String?> = emptyMap()): String {
        val zonePart = zones(style).joinToString(",") { z -> "\"${z.id}\":" + (sanitize(colors[z.id])?.let { "\"$it\"" } ?: "null") }
        val hidePart = hidden.filter { h -> parts(style).any { it.id == h } }.sorted().joinToString(",", "[", "]") { "\"$it\"" }
        val accPart = worn.filterKeys { k -> accessories(style).any { it.id == k } }.toSortedMap()
            .entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":" + (sanitize(v)?.let { "\"$it\"" } ?: "null") }
        return "{" + listOf(zonePart, "\"_hide\":$hidePart", "\"_acc\":$accPart").filter { it.isNotEmpty() }.joinToString(",") + "}"
    }

    /** JSON completo de una mascota desde las preferencias guardadas. */
    fun jsonFor(style: AvatarStyle, saved: Map<String, String>): String =
        toJson(style, colorsFor(style, saved), hiddenFor(style, saved), accessoriesFor(style, saved))
}
