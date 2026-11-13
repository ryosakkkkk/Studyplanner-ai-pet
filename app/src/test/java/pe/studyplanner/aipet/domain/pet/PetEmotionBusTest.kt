package pe.studyplanner.aipet.domain.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PetEmotionBusTest {
    @Test
    fun emotionLastsForItsDurationThenExpires() {
        PetEmotionBus.clear()
        try {
            assertNull(PetEmotionBus.active(0))
            PetEmotionBus.emit("celebrate", 3000, 1000)
            assertEquals("celebrate", PetEmotionBus.active(1000))
            assertEquals("celebrate", PetEmotionBus.active(3999))
            assertNull(PetEmotionBus.active(4000))
            assertNull(PetEmotionBus.active(60_000))
            assertEquals("celebrate", PetEmotionBus.current?.emotion)
            // la duración por defecto son 3 s y una señal nueva reemplaza a la anterior
            PetEmotionBus.emit("happy", nowMs = 5000)
            assertEquals("happy", PetEmotionBus.active(7999))
            assertNull(PetEmotionBus.active(8000))
            // texto vacío o duración 0 no activan nada
            PetEmotionBus.emit("  ", 1000, 9000)
            assertNull(PetEmotionBus.active(9000))
            PetEmotionBus.emit("alert", 0, 9000)
            assertNull(PetEmotionBus.active(9000))
            PetEmotionBus.emit("alert", -50, 9000)
            assertNull(PetEmotionBus.active(9000))
        } finally { PetEmotionBus.clear() }
    }

    @Test
    fun busOverridesTheViewMoodAndATapGreetingOverridesTheBus() {
        PetEmotionBus.clear()
        try {
            assertEquals("floating", PetEmotionBus.effective("floating", 100))
            PetEmotionBus.emit("celebrate", 3000, 100)
            assertEquals("celebrate", PetEmotionBus.effective("floating", 200))
            assertEquals("greet", PetEmotionBus.effective("floating", 200, greetUntilMs = 1700)) // toque: manda el saludo
            assertEquals("celebrate", PetEmotionBus.effective("floating", 1700, greetUntilMs = 1700))
            assertEquals("floating", PetEmotionBus.effective("floating", 3100)) // venció: vuelve a su mood
            PetEmotionBus.clear()
            assertEquals("greet", PetEmotionBus.effective("idle", 10, greetUntilMs = 11))
            assertEquals("idle", PetEmotionBus.effective("idle", 10, greetUntilMs = 0))
        } finally { PetEmotionBus.clear() }
    }

    @Test
    fun clearRemovesTheSignal() {
        PetEmotionBus.emit("alert", 5000, 0)
        assertEquals("alert", PetEmotionBus.active(1))
        PetEmotionBus.clear()
        assertNull(PetEmotionBus.active(1))
        assertNull(PetEmotionBus.current)
    }
}
