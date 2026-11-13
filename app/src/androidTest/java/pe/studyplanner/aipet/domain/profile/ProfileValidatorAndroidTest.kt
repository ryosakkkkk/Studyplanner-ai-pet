package pe.studyplanner.aipet.domain.profile

import android.os.Build
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Verifica normalización Android y segmentación fijada; no sustituye las demás API de la matriz. */
@RunWith(AndroidJUnit4::class)
class ProfileValidatorAndroidTest {
    @Before
    fun reportActualRuntime() {
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply {
                putString(
                    "stream",
                    "\nProfileValidatorAndroidTest: API ${Build.VERSION.SDK_INT}, Android ${Build.VERSION.RELEASE}, ${Build.MODEL}\n",
                )
            },
        )
    }

    @Test
    fun normalizesAccentedNamesWithAndroidRuntime() {
        val normalized = ProfileValidator.normalize(
            UserProfile(studentName = "  Mari\u0301a Jose\u0301  ", petName = "  Luci\u0301a  "),
        )
        assertEquals("María José", normalized.studentName)
        assertEquals("Lucía", normalized.petName)
        assertTrue("API ${Build.VERSION.SDK_INT}", ProfileValidator.validate(normalized).isEmpty())
    }

    @Test
    fun enforcesVisibleCharacterLimitForAccentsAndJoinedEmoji() {
        listOf("e\u0301", "👩‍👩‍👧‍👦", "👩🏽‍🚀", "🙂‍↔️", "🇵🇪", "1️⃣", "क्षि", "각").forEach { grapheme ->
            val accepted = ProfileValidator.validate(UserProfile(petName = grapheme.repeat(24)))
            val rejected = ProfileValidator.validate(UserProfile(petName = grapheme.repeat(25)))
            assertFalse("24 grafemas $grapheme; API ${Build.VERSION.SDK_INT}", accepted.containsKey("petName"))
            assertTrue("25 grafemas $grapheme; API ${Build.VERSION.SDK_INT}", rejected.containsKey("petName"))
            assertFalse("80 grafemas $grapheme", ProfileValidator.validate(UserProfile(studentName = grapheme.repeat(80))).containsKey("studentName"))
            assertTrue("81 grafemas $grapheme", ProfileValidator.validate(UserProfile(studentName = grapheme.repeat(81))).containsKey("studentName"))
        }
    }

    @Test
    fun conformsToEveryOfficialUnicode17BoundaryOnAndroid() {
        val stream = InstrumentationRegistry.getInstrumentation().context.assets
            .open("unicode/GraphemeBreakTest-17.0.0.txt")
        var cases = 0
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEachIndexed { lineIndex, line ->
                val specification = line.substringBefore('#').trim()
                if (specification.isNotEmpty()) {
                    val text = StringBuilder()
                    val expected = mutableListOf<Int>()
                    specification.split(Regex("\\s+")).forEach { token ->
                        when (token) {
                            "÷" -> expected.add(text.length)
                            "×" -> Unit
                            else -> text.appendCodePoint(token.toInt(16))
                        }
                    }
                    assertEquals("Unicode 17, línea ${lineIndex + 1}; API ${Build.VERSION.SDK_INT}", expected, ExtendedGraphemes.boundaries(text.toString()))
                    assertEquals("Conteo en línea ${lineIndex + 1}", expected.size - 1, ExtendedGraphemes.count(text.toString()))
                    cases++
                }
            }
        }
        assertEquals("Corpus Unicode 17 completo", 766, cases)
    }

    @Test
    fun rejectsNamesWithoutBaseButPreservesEmojiJoiners() {
        listOf("\u200B", "\u200D", "\uFE0F", "\u0301", "\u3164", "\u2800").forEach { name ->
            val errors = ProfileValidator.validate(UserProfile(studentName = name, petName = name))
            assertTrue("Nombre de mascota sin base; API ${Build.VERSION.SDK_INT}", errors.containsKey("petName"))
            assertTrue("Nombre de estudiante sin base; API ${Build.VERSION.SDK_INT}", errors.containsKey("studentName"))
        }
        listOf("👩‍👩‍👧‍👦", "❤️", "Luci\u0301a", "نور\u200Cعلی", "\uD83E\uDEE8").forEach { name ->
            assertTrue(
                "Nombre con base; API ${Build.VERSION.SDK_INT}",
                ProfileValidator.validate(UserProfile(petName = name)).isEmpty(),
            )
        }
    }
}
