package pe.studyplanner.aipet.platform.pet3d

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.studyplanner.aipet.domain.pet.PetOutfits
import pe.studyplanner.aipet.domain.pet.PetStickers
import pe.studyplanner.aipet.domain.profile.AvatarStyle

/**
 * Colores elegidos para cada mascota (zona → color) y la mascota actual, guardados en el teléfono. La mascota actual
 * también la usan los avisos, que no tienen acceso al perfil, para mostrar su imagen.
 */
object PetStyleStore {
    private const val FILE = "pet_style_v1"
    private const val CURRENT = "current_pet"
    private var prefs: SharedPreferences? = null
    private val state = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Todas las preferencias de color («zorro.ropa» → «#FF3B30»). */
    val colors: StateFlow<Map<String, String>> get() = state.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        state.value = p.all.filterKeys { it != CURRENT }.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
    }

    fun setColor(context: Context, style: AvatarStyle, zone: String, hex: String?) {
        init(context)
        val key = PetOutfits.key(style, zone) ?: return
        val clean = PetOutfits.sanitize(hex)
        prefs?.edit()?.apply { if (clean == null) remove(key) else putString(key, clean) }?.apply()
        state.value = state.value.toMutableMap().apply { if (clean == null) remove(key) else put(key, clean) }
    }

    /** Esconde o vuelve a poner una pieza (p. ej. la mochila). */
    fun setHidden(context: Context, style: AvatarStyle, part: String, hidden: Boolean) {
        val key = PetOutfits.hiddenKey(style, part) ?: return
        put(context, key, if (hidden) "1" else null)
    }

    /** Pone o quita un accesorio; [hex] null lo deja con su color original. */
    fun setAccessory(context: Context, style: AvatarStyle, id: String, worn: Boolean, hex: String? = null) {
        val key = PetOutfits.accessoryKey(style, id) ?: return
        put(context, key, if (!worn) null else PetOutfits.sanitize(hex) ?: PetOutfits.WORN)
    }

    private fun put(context: Context, key: String, value: String?) {
        init(context)
        prefs?.edit()?.apply { if (value == null) remove(key) else putString(key, value) }?.apply()
        state.value = state.value.toMutableMap().apply { if (value == null) remove(key) else put(key, value) }
    }

    /** Vuelve todo al original: colores, piezas y accesorios. */
    fun resetColors(context: Context, style: AvatarStyle) {
        PetOutfits.zones(style).forEach { setColor(context, style, it.id, null) }
        PetOutfits.parts(style).forEach { setHidden(context, style, it.id, false) }
        PetOutfits.accessories(style).forEach { setAccessory(context, style, it.id, false) }
    }

    fun setCurrentPet(context: Context, style: AvatarStyle) {
        init(context)
        if (prefs?.getString(CURRENT, null) != style.name) prefs?.edit()?.putString(CURRENT, style.name)?.apply()
    }

    fun currentPet(context: Context): AvatarStyle {
        init(context)
        return runCatching { AvatarStyle.valueOf(prefs?.getString(CURRENT, null) ?: "FOX") }.getOrDefault(AvatarStyle.FOX)
    }
}

/** Imágenes de las láminas (expresiones y poses), leídas de assets y guardadas en memoria. */
object PetStickerImages {
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(context: Context, style: AvatarStyle, key: String): Bitmap? {
        val path = PetStickers.path(style, key)
        cache.get(path)?.let { return it }
        return runCatching { context.assets.open(path).use(BitmapFactory::decodeStream) }.getOrNull()?.also { cache.put(path, it) }
    }
}
