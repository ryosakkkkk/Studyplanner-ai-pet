package pe.studyplanner.aipet.domain.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PetProtocolTest {

    private fun face(expression: PetExpression, text: String = "", maxBytes: Int = PetProtocol.MAX_FACE_BYTES): String =
        String(PetProtocol.encodeFace(expression, text, maxBytes), Charsets.UTF_8)

    @Test fun uuidsAreTheOnesInTheFirmwareContract() {
        assertEquals("45bcd4ae-6b58-4eb9-968a-3ed899731e9a", PetProtocol.SERVICE.toString())
        assertEquals("d693d988-f7c3-41b4-b13a-fc22bf26a0e6", PetProtocol.FACE.toString())
        assertEquals("b547f1e3-3abd-4393-b19a-6837b3f6c6b4", PetProtocol.CUE.toString())
        assertEquals("8b27bc7a-5906-4647-9730-2d61cbaec596", PetProtocol.EVENT.toString())
        assertEquals("43f84ca4-e9df-4166-9a06-f04aebeb4908", PetProtocol.STATUS.toString())
        assertEquals("00002902-0000-1000-8000-00805f9b34fb", PetProtocol.CCCD.toString())
        assertEquals("StudyPet", PetProtocol.DEVICE_NAME)
    }

    @Test fun everyExpressionUsesOneOfTheNineFirmwareWords() {
        val words = setOf("idle", "feliz", "pensar", "hablar", "sorprendido", "celebrar", "dormir", "alerta", "escuchar")
        assertEquals(words, PetExpression.entries.map { it.wire }.toSet())
    }

    @Test fun faceWithTextCarriesExpressionAndText() {
        assertEquals("E:feliz|T:¡Correcto!", face(PetExpression.FELIZ, "¡Correcto!"))
        assertEquals("E:pensar|T:Pensando", face(PetExpression.PENSAR, "Pensando"))
    }

    @Test fun faceWithoutTextOnlyHasTheExpression() {
        assertEquals("E:dormir", face(PetExpression.DORMIR))
        assertEquals("E:idle", face(PetExpression.IDLE, "   "))
    }

    @Test fun faceTextIsFlattenedSoTheSeparatorsCannotBreakTheMessage() {
        assertEquals("E:pensar|T:a b c d", face(PetExpression.PENSAR, "a|b\nc\td"))
        assertEquals("E:hablar|T:uno dos", face(PetExpression.HABLAR, "  uno    dos  "))
    }

    @Test fun faceTextIsCutAtThirtySixCharacters() {
        val text = "abcdefghij".repeat(5)
        val message = face(PetExpression.HABLAR, text)
        val body = message.substringAfter("|T:")
        assertEquals(PetProtocol.MAX_TEXT_CHARS, body.length)
        assertEquals(text.take(PetProtocol.MAX_TEXT_CHARS), body)
    }

    @Test fun faceFitsTheDefaultMtuWithoutSplittingCharacters() {
        val limit = PetProtocol.payloadLimit(PetProtocol.DEFAULT_MTU)
        assertEquals(20, limit)
        val bytes = PetProtocol.encodeFace(PetExpression.FELIZ, "¡Correcto!", limit)
        assertTrue("size ${bytes.size}", bytes.size <= limit)
        val decoded = String(bytes, Charsets.UTF_8)
        assertFalse(decoded.contains('�'))
        assertEquals("E:feliz|T:¡Correcto", decoded)
    }

    @Test fun faceNeverExceedsTheFirmwareLimitEvenWithFourByteCharacters() {
        val bytes = PetProtocol.encodeFace(PetExpression.SORPRENDIDO, "😀".repeat(36))
        assertTrue("size ${bytes.size}", bytes.size <= PetProtocol.MAX_FACE_BYTES)
        assertFalse(String(bytes, Charsets.UTF_8).contains('�'))
    }

    @Test fun payloadLimitFollowsTheNegotiatedMtu() {
        assertEquals(111, PetProtocol.payloadLimit(247))
        assertEquals(97, PetProtocol.payloadLimit(100))
        assertEquals(20, PetProtocol.payloadLimit(23))
        assertEquals(20, PetProtocol.payloadLimit(5))
    }

    @Test fun cueIsASingleByteWithTheSoundCode() {
        assertEquals(listOf<Byte>(1), PetProtocol.encodeCue(PetSound.CORRECTO).toList())
        assertEquals(listOf<Byte>(5), PetProtocol.encodeCue(PetSound.CELEBRACION).toList())
        assertEquals(listOf(1, 2, 3, 4, 5), PetSound.entries.map { it.code })
    }

    @Test fun soundOfOnlyKnowsCodesOneToFive() {
        assertEquals(PetSound.PISTA, PetProtocol.soundOf(3))
        assertNull(PetProtocol.soundOf(0))
        assertNull(PetProtocol.soundOf(6))
    }

    @Test fun optionButtonsMapLettersToIndexes() {
        "ABCDE".forEachIndexed { index, letter ->
            assertEquals(PetEvent.Option(index), PetProtocol.parseEvent("BTN:$letter"))
        }
    }

    @Test fun talkOkAndPongAreRecognised() {
        assertEquals(PetEvent.Talk, PetProtocol.parseEvent("BTN:TALK"))
        assertEquals(PetEvent.Ok, PetProtocol.parseEvent("BTN:OK"))
        assertEquals(PetEvent.Pong("17"), PetProtocol.parseEvent("PONG:17"))
    }

    @Test fun eventParsingToleratesPaddingAndRejectsUnknownText() {
        assertEquals(PetEvent.Option(1), PetProtocol.parseEvent("  BTN:B\u0000".toByteArray(Charsets.UTF_8)))
        assertNull(PetProtocol.parseEvent("BTN:F"))
        assertNull(PetProtocol.parseEvent("BTN:"))
        assertNull(PetProtocol.parseEvent("BTN:AB"))
        assertNull(PetProtocol.parseEvent("hola"))
        assertNull(PetProtocol.parseEvent(""))
    }

    @Test fun statusWithoutBatteryIsNotAnError() {
        val status = PetProtocol.parseStatus("FW:1.0.0|BAT:NA")
        assertNotNull(status)
        assertEquals("1.0.0", status!!.firmware)
        assertNull(status.batteryPercent)
        assertTrue(status.compatible)
    }

    @Test fun statusReadsBatteryAndChecksTheMajorVersion() {
        val ok = PetProtocol.parseStatus("FW:1.4.2|BAT:80".toByteArray(Charsets.UTF_8))!!
        assertEquals(80, ok.batteryPercent)
        assertTrue(ok.compatible)
        assertFalse(PetProtocol.parseStatus("FW:2.0.0|BAT:NA")!!.compatible)
    }

    @Test fun malformedStatusIsRejectedOrIgnored() {
        assertNull(PetProtocol.parseStatus("BAT:50"))
        assertNull(PetProtocol.parseStatus("FW:|BAT:1"))
        assertNull(PetProtocol.parseStatus(""))
        assertNull(PetProtocol.parseStatus("FW:1.0.0|BAT:150")!!.batteryPercent)
    }

    @Test fun buttonLabelsAreShortAndReadable() {
        assertEquals("C", PetProtocol.buttonLabel(PetEvent.Option(2)))
        assertEquals("TALK", PetProtocol.buttonLabel(PetEvent.Talk))
        assertEquals("OK", PetProtocol.buttonLabel(PetEvent.Ok))
        assertEquals("PONG", PetProtocol.buttonLabel(PetEvent.Pong("1")))
        assertEquals("?", PetProtocol.buttonLabel(PetEvent.Option(9)))
    }
}
