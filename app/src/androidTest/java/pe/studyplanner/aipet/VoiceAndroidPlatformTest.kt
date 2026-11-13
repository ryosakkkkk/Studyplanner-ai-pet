package pe.studyplanner.aipet

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import pe.studyplanner.aipet.domain.voice.RecognitionEvents
import pe.studyplanner.aipet.domain.voice.SpeechEvents
import pe.studyplanner.aipet.platform.voice.AndroidVoicePlatform
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Real Android service discovery and missing-engine guards; it never requests audio capture. */
class VoiceAndroidPlatformTest {
    @Test fun installedSpanishEngineReportsRealStartAndDoneForSyntheticText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val terminal = CountDownLatch(1)
        val failure = AtomicReference<String?>(null)
        val starts = AtomicInteger(0)
        val completions = AtomicInteger(0)
        val startedAt = AtomicLong(0)
        val completedAt = AtomicLong(0)
        lateinit var platform: AndroidVoicePlatform
        instrumentation.runOnMainSync {
            platform = AndroidVoicePlatform(context)
            assertTrue("A native TTS engine must be installed for this emulator acceptance test.",
                platform.capabilities().ttsEngineAvailable)
        }
        try {
            instrumentation.runOnMainSync {
                platform.speak("Prueba técnica de lectura de Study Planner.", object : SpeechEvents {
                    override fun onStart() {
                        starts.incrementAndGet()
                        startedAt.set(System.nanoTime())
                        Log.i("F6_VOICE_NATIVE", "TTS onStart at=${Instant.now()}; source=native_engine; text=synthetic")
                    }
                    override fun onDone() {
                        completions.incrementAndGet()
                        completedAt.set(System.nanoTime())
                        Log.i("F6_VOICE_NATIVE", "TTS onDone at=${Instant.now()}; source=native_engine; humanAudition=false")
                        terminal.countDown()
                    }
                    override fun onError(message: String) {
                        failure.set(message)
                        Log.e("F6_VOICE_NATIVE", "TTS onError at=${Instant.now()}; message=$message")
                        terminal.countDown()
                    }
                })
            }
            assertTrue("The native Spanish TTS engine did not finish or report an error within 20 seconds.",
                terminal.await(20, TimeUnit.SECONDS))
            assertNull("Native Spanish TTS failed: ${failure.get()}", failure.get())
            assertEquals(1, starts.get())
            assertEquals(1, completions.get())
            assertTrue(startedAt.get() > 0)
            assertTrue(completedAt.get() >= startedAt.get())
            // Engine callbacks establish synthesis lifecycle, not human hearing or intelligibility.
        } finally {
            instrumentation.runOnMainSync { platform.release() }
        }
    }

    @Test fun capabilitiesMatchInstalledServicesAndMissingEnginesFailWithoutListening() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertEquals("The test runner grants microphone permission temporarily, outside instrumentation.",
            PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        instrumentation.runOnMainSync {
            val platform = AndroidVoicePlatform(context)
            try {
                val reported = platform.capabilities()
                val expectedSpeech = SpeechRecognizer.isRecognitionAvailable(context)
                val expectedLocal = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                val expectedTts = context.packageManager.queryIntentServices(
                    Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0).isNotEmpty()
                assertEquals(expectedSpeech, reported.speechAvailable)
                assertEquals(expectedLocal, reported.onDeviceAvailable)
                assertEquals(expectedTts, reported.ttsEngineAvailable)
                var checkedGuards = 0
                if (!reported.speechAvailable) {
                    val events = CapturedRecognitionEvents()
                    platform.startRecognition(onDevice = false, listener = events)
                    assertEquals(1, events.errors.size)
                    assertTrue(events.errors.single().contains("No hay un servicio de reconocimiento instalado"))
                    events.assertNoRecognition()
                    checkedGuards++
                }
                if (!reported.onDeviceAvailable) {
                    val events = CapturedRecognitionEvents()
                    platform.startRecognition(onDevice = true, listener = events)
                    assertEquals(1, events.errors.size)
                    assertTrue(events.errors.single().contains("en el dispositivo no está disponible"))
                    events.assertNoRecognition()
                    checkedGuards++
                }
                Log.i("F6_VOICE_NATIVE", "API=${Build.VERSION.SDK_INT}; speech=${reported.speechAvailable}; " +
                    "onDevice=${reported.onDeviceAvailable}; ttsEngine=${reported.ttsEngineAvailable}; " +
                    "missingEngineGuardsChecked=$checkedGuards; captureRequested=false")
            } finally {
                platform.release()
            }
        }
    }

    private class CapturedRecognitionEvents : RecognitionEvents {
        val errors = mutableListOf<String>()
        private var ready = 0
        private var ended = 0
        private var partials = 0
        private var results = 0
        override fun onReady() { ready++ }
        override fun onEndOfSpeech() { ended++ }
        override fun onPartial(text: String) { partials++ }
        override fun onResult(text: String) { results++ }
        override fun onError(message: String) { errors += message }
        fun assertNoRecognition() {
            assertEquals(0, ready)
            assertEquals(0, ended)
            assertEquals(0, partials)
            assertEquals(0, results)
        }
    }
}
