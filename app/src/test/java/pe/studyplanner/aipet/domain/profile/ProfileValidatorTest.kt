package pe.studyplanner.aipet.domain.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileValidatorTest {
    @Test
    fun `el perfil inicial es valido y el nombre del estudiante es opcional`() {
        assertTrue(ProfileValidator.validate(UserProfile()).isEmpty())
    }

    @Test
    fun `normaliza acentos combinantes y espacios exteriores sin borrar espacios internos`() {
        val normalized = ProfileValidator.normalize(
            UserProfile(studentName = "  Mari\u0301a Jose\u0301  ", petName = "  Luci\u0301a Sol  "),
        )
        assertEquals("María José", normalized.studentName)
        assertEquals("Lucía Sol", normalized.petName)
        assertTrue(ProfileValidator.validate(normalized).isEmpty())
    }

    @Test
    fun `rechaza nombres de mascota vacios o blancos`() {
        listOf("", " ", "\u2003").forEach { name ->
            assertTrue(ProfileValidator.validate(UserProfile(petName = name)).containsKey("petName"))
        }
    }

    @Test
    fun `rechaza nombres hechos solo de invisibles o marcas sin base`() {
        val invalidNames = listOf(
            "\u200B", // espacio de ancho cero
            "\u200D", // unión ZWJ aislada
            "\uFE0F", // selector de presentación emoji aislado
            "\u0301\u0327", // acentos combinantes sin letra
            "\u034F", // unión de grafemas
            "\u115F\u1160", // fillers Hangul clasificados como letras
            "\u3164", "\uFFA0", // otros fillers Hangul
            "\u2800", // celda Braille vacía, clasificada como símbolo
            "\u2060\u200F\uFEFF", // formato sin contenido visible
            "\uDB40\uDD00", // selector de variación suplementario U+E0100
            " \u200B \u0301 ", // espacios con marcas no crean una base
        )
        invalidNames.forEach { name ->
            val errors = ProfileValidator.validate(UserProfile(studentName = name, petName = name))
            assertTrue("Debe rechazar un nombre de estudiante sin base", errors.containsKey("studentName"))
            assertTrue("Debe rechazar un nombre de mascota sin base", errors.containsKey("petName"))
        }
    }

    @Test
    fun `conserva marcas uniones y selectores en nombres visibles de diversas escrituras`() {
        listOf(
            "Luci\u0301a", "A\u0301\u0327", "María José", "D’Arcy", "雪", "한별",
            "محمد", "किरण", "نور\u200Cعلی", "👩‍👩‍👧‍👦", "❤️", "👍🏽", "1️⃣",
            "\uD83E\uDEE8", // emoji reciente: no depende de clasificación Unicode del JDK
        ).forEach { name ->
            assertTrue(
                "El nombre visible debe ser válido: $name",
                ProfileValidator.validate(UserProfile(studentName = name, petName = name)).isEmpty(),
            )
        }
        val family = "👩‍👩‍👧‍👦"
        assertEquals(family, ProfileValidator.normalize(UserProfile(petName = family)).petName)
    }

    @Test
    fun `el nombre opcional blanco sigue pudiendo omitirse`() {
        val profile = UserProfile(studentName = " \u2003 ")
        assertTrue(ProfileValidator.validate(profile).isEmpty())
        assertEquals("", ProfileValidator.normalize(profile).studentName)
    }

    @Test
    fun `el limite de mascota es 24 grafemas y no unidades utf16`() {
        listOf("👩‍👩‍👧‍👦", "👩🏽‍🚀", "🙂‍↔️", "🇵🇪", "1️⃣", "e\u0301", "क्षि", "각").forEach { cluster ->
            assertFalse(cluster, ProfileValidator.validate(UserProfile(petName = cluster.repeat(24))).containsKey("petName"))
            assertTrue(cluster, ProfileValidator.validate(UserProfile(petName = cluster.repeat(25))).containsKey("petName"))
        }
        assertTrue(ProfileValidator.validate(UserProfile(petName = "a".repeat(25))).containsKey("petName"))
    }

    @Test
    fun `el nombre opcional del estudiante admite hasta 80 grafemas`() {
        listOf("ñ", "👩‍👩‍👧‍👦", "🇵🇪", "क्षि").forEach { cluster ->
            assertFalse(cluster, ProfileValidator.validate(UserProfile(studentName = cluster.repeat(80))).containsKey("studentName"))
            assertTrue(cluster, ProfileValidator.validate(UserProfile(studentName = cluster.repeat(81))).containsKey("studentName"))
        }
    }

    @Test
    fun `rechaza caracteres de control incluso en extremos antes de normalizar`() {
        listOf("Lu\u0000na", "Luna\n", "\tLuna", "Luna\u007F").forEach { value ->
            val errors = ProfileValidator.validate(UserProfile(studentName = value, petName = value))
            assertTrue(errors.containsKey("studentName"))
            assertTrue(errors.containsKey("petName"))
        }
    }

    @Test
    fun `acepta zonas IANA y rechaza offsets o identificadores inventados`() {
        assertFalse(ProfileValidator.validate(UserProfile(zoneId = "America/Lima")).containsKey("zoneId"))
        assertFalse(ProfileValidator.validate(UserProfile(zoneId = "Europe/Madrid")).containsKey("zoneId"))
        listOf("", "Lima", "America/Inventada", "+02:00").forEach { zone ->
            assertTrue(ProfileValidator.validate(UserProfile(zoneId = zone)).containsKey("zoneId"))
        }
    }

    @Test
    fun `normalizar conserva avatar tema visibilidad y estado de onboarding`() {
        val original = UserProfile(
            avatar = AvatarStyle.OWL,
            theme = ThemeMode.DARK,
            petVisible = false,
            onboardingCompleted = true,
        )
        assertEquals(original, ProfileValidator.normalize(original))
    }
}
