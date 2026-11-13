package pe.studyplanner.aipet.domain.tutor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorSessionTest {

    // ---- fase LEARN ----

    @Test
    fun laSesionNaceEnLaExplicacion() {
        val st = newSession().start()
        assertEquals(Phase.LEARN, st.phase)
        assertEquals(TutorScreen.EXPLANATION, st.screen)
        assertEquals(PetCue.EXPLAIN, st.cue)
        assertNull(st.exercise)
        assertNull(st.summary)
        assertTrue(TutorAction.NEXT in st.actions)
        assertTrue(TutorAction.SKIP_LEARN in st.actions)
        assertFalse(TutorAction.BACK in st.actions)
        assertTrue(st.text.isNotBlank())
        assertTrue(st.speech.isNotBlank())
    }

    @Test
    fun startEsIdempotente() {
        val s = newSession()
        val first = s.start()
        assertEquals(first, s.start())
        s.next()
        assertEquals(s.state, s.start())
    }

    @Test
    fun elRecorridoDeAprendizajeTerminaEnElEjemploResuelto() {
        val s = newSession()
        var st = s.start()
        val total = st.progress.total
        assertEquals(1, st.progress.current)
        repeat(total - 2) {
            st = s.next()
            assertEquals(TutorScreen.EXPLANATION, st.screen)
        }
        st = s.next()
        assertEquals(TutorScreen.WORKED_EXAMPLE, st.screen)
        assertEquals(total, st.progress.current)
        assertTrue(st.text.contains(checkNotNull(TutorRegistry.requireTopic("ecuaciones-lineales").workedExample).statement))
        st = s.next()
        assertEquals(Phase.GUIDED_PRACTICE, st.phase)
        assertEquals(TutorScreen.QUESTION, st.screen)
    }

    @Test
    fun sePuedeVolverAtrasSoloEnLaExplicacion() {
        val s = newSession()
        val first = s.start()
        assertEquals(first, s.back())
        s.next()
        assertTrue(TutorAction.BACK in s.state.actions)
        assertEquals(first, s.back())
        s.skipLearn()
        val question = s.state
        assertEquals(question, s.back())
    }

    @Test
    fun saltarLaExplicacionVaDirectoAPracticar() {
        val s = newSession()
        val st = s.skipLearn()
        assertEquals(Phase.GUIDED_PRACTICE, st.phase)
        assertEquals(TutorScreen.QUESTION, st.screen)
        assertEquals(Progress(Phase.GUIDED_PRACTICE, 1, 3), st.progress)
    }

    @Test
    fun siNoSePermiteSaltarLaAccionSeIgnora() {
        val s = newSession(config = SessionConfig(seed = 3L, allowSkipLearn = false))
        val before = s.start()
        assertFalse(TutorAction.SKIP_LEARN in before.actions)
        assertEquals(before, s.skipLearn())
    }

    // ---- práctica guiada: pistas ----

    @Test
    fun noHayPistasAntesDelPrimerIntento() {
        val st = newSession().toGuided()
        val view = checkNotNull(st.exercise)
        assertEquals(PetCue.THINK, st.cue)
        assertTrue(view.hintsShown.isEmpty())
        assertEquals(Exercise.HINT_COUNT, view.hintsRemaining)
        assertTrue(view.eliminated.isEmpty())
        assertTrue(view.solutionSteps.isEmpty())
        assertNull(view.correctIndex)
        assertEquals(Exercise.OPTION_COUNT, view.options.size)
        assertTrue(TutorAction.ANSWER in st.actions)
        assertTrue(TutorAction.REQUEST_HINT in st.actions)
        // «Ver la solución» está siempre a la mano en la práctica.
        assertTrue(TutorAction.SHOW_SOLUTION in st.actions)
        assertFalse(TutorAction.DONT_KNOW in st.actions)
        assertFalse(st.text.contains("Pista 1"))
    }

    @Test
    fun laPistaSeRevelaSoloSiElEstudianteLaPide() {
        val s = newSession()
        s.toGuided()
        val e = exerciseOf(s.state)
        for (k in 1..3) {
            val st = s.requestHint()
            assertEquals(TutorScreen.HINT, st.screen)
            assertEquals(PetCue.EXPLAIN, st.cue)
            val view = checkNotNull(st.exercise)
            assertEquals(e.hints.take(k), view.hintsShown)
            assertEquals(Exercise.HINT_COUNT - k, view.hintsRemaining)
            assertTrue(st.text.contains(e.hints[k - 1]))
        }
        assertTrue(s.attempts.isEmpty())
        val st = s.state
        assertTrue(TutorAction.SHOW_SOLUTION in st.actions)
        assertFalse(TutorAction.REQUEST_HINT in st.actions)
        assertEquals(st, s.requestHint())
    }

    @Test
    fun unErrorMuestraEnSeguidaPorQueNoEsYLaSolucionCompleta() {
        val s = newSession()
        s.toGuided()
        val e = exerciseOf(s.state)
        val wrong = wrongOf(s.state)
        val st = s.answer(wrong)
        // Respuesta inmediata: retroalimentación de ESA alternativa más la solución paso a paso (ejemplo resuelto).
        assertEquals(TutorScreen.SOLUTION, st.screen)
        assertEquals(PetCue.ENCOURAGE, st.cue)
        val view = checkNotNull(st.exercise)
        assertTrue(st.text.contains(e.optionFeedback.getValue(wrong)))
        assertEquals(e.solutionSteps, view.solutionSteps)
        assertEquals(e.correctIndex, view.correctIndex)
        assertTrue(wrong in view.eliminated)
        assertTrue(view.hintsShown.isEmpty())
        assertTrue(TutorAction.RETRY in st.actions)
        assertTrue(TutorAction.NEXT in st.actions)
        assertFalse(TutorAction.ANSWER in st.actions)
        assertFalse(TutorAction.REQUEST_HINT in st.actions)
        assertEquals(1, s.attempts.size)
        assertFalse(s.attempts.single().correct)
        assertEquals(0, s.attempts.single().hintsShownBefore)
        // Con la solución a la vista ya no se puede responder ese ejercicio.
        assertEquals(st, s.answer(correctOf(st)))
    }

    @Test
    fun conPistasPedidasUnErrorTambienMuestraLaSolucionYNoCuentaComoResuelto() {
        val s = newSession()
        s.toGuided()
        val e = exerciseOf(s.state)
        repeat(2) { s.requestHint() }
        val st = s.answer(wrongOf(s.state))
        assertEquals(TutorScreen.SOLUTION, st.screen)
        assertEquals(e.solutionSteps, checkNotNull(st.exercise).solutionSteps)
        assertEquals(listOf(2), s.attempts.map { it.hintsShownBefore })

        // Salta el resto de los guiados sin resolverlos: el resumen no los cuenta como resueltos.
        s.next()
        repeat(2) { s.answer(wrongOf(s.state)); s.next() }
        val summary = checkNotNull(s.runUnaided(listOf(true, true)).summary)
        assertEquals(0, summary.guidedSolved)
        assertEquals(3, summary.guidedTotal)
    }

    @Test
    fun laSolucionSePuedePedirEnCualquierMomento() {
        val s = newSession()
        s.toGuided()
        val e = exerciseOf(s.state)
        val st = s.showSolution()
        assertEquals(TutorScreen.SOLUTION, st.screen)
        assertEquals(PetCue.EXPLAIN, st.cue)
        assertEquals(e.solutionSteps, checkNotNull(st.exercise).solutionSteps)
        assertTrue(s.attempts.isEmpty())
        // También después de una pista.
        val t = newSession()
        t.toGuided()
        t.requestHint()
        assertEquals(TutorScreen.SOLUTION, t.showSolution().screen)
    }

    @Test
    fun reintentarDaUnEjercicioParecidoDeLaMismaDificultad() {
        val s = newSession()
        s.toGuided()
        val first = exerciseOf(s.state)
        val before = s.state.progress
        s.answer(wrongOf(s.state))
        assertEquals(TutorScreen.SOLUTION, s.state.screen)
        val st = s.retry()
        assertEquals(TutorScreen.QUESTION, st.screen)
        assertEquals(PetCue.THINK, st.cue)
        val view = checkNotNull(st.exercise)
        assertNotEquals(first.id, view.id)
        assertNotEquals(first.statement, view.statement)
        assertEquals(first.difficulty, exerciseOf(st).difficulty)
        assertEquals(before, st.progress)
        assertTrue(view.hintsShown.isEmpty())
        assertTrue(view.eliminated.isEmpty())
        // Resolver el parecido sí cuenta como resuelto.
        assertEquals(TutorScreen.CORRECT, s.answer(correctOf(st)).screen)
    }

    @Test
    fun reintentarSoloSeOfreceTrasVerLaSolucion() {
        val s = newSession()
        s.toGuided()
        val question = s.state
        assertEquals(question, s.retry())
        assertFalse(TutorAction.RETRY in question.actions)
    }

    @Test
    fun acertarALaPrimeraMuestraCorrectoYAvanza() {
        val s = newSession()
        s.toGuided()
        val st = s.answer(correctOf(s.state))
        assertEquals(TutorScreen.CORRECT, st.screen)
        assertEquals(PetCue.CORRECT, st.cue)
        val view = checkNotNull(st.exercise)
        assertEquals(exerciseOf(st).correctIndex, view.correctIndex)
        assertTrue(TutorAction.NEXT in st.actions)
        assertFalse(TutorAction.ANSWER in st.actions)
        val second = s.next()
        assertEquals(TutorScreen.QUESTION, second.screen)
        assertEquals(Progress(Phase.GUIDED_PRACTICE, 2, 3), second.progress)
        assertNotEquals(view.id, checkNotNull(second.exercise).id)
    }

    @Test
    fun trasUnErrorUnoParecidoBienResueltoTambienAvanza() {
        val s = newSession()
        s.toGuided()
        s.answer(wrongOf(s.state))
        s.retry()
        val st = s.answer(correctOf(s.state))
        assertEquals(TutorScreen.CORRECT, st.screen)
        assertEquals(PetCue.CORRECT, st.cue)
        assertEquals(listOf(false, true), s.attempts.map { it.correct })
        assertEquals(Progress(Phase.GUIDED_PRACTICE, 2, 3), s.next().progress)
    }

    @Test
    fun unaAlternativaYaDescartadaSeIgnora() {
        val s = newSession()
        s.toGuided()
        val wrong = wrongOf(s.state)
        val after = s.answer(wrong)
        val count = s.attempts.size
        assertEquals(after, s.answer(wrong))
        assertEquals(count, s.attempts.size)
    }

    @Test
    fun lasAccionesInvalidasSeIgnoran() {
        val s = newSession()
        val learn = s.start()
        assertEquals(learn, s.answer(0))
        assertEquals(learn, s.requestHint())
        assertEquals(learn, s.showSolution())
        assertEquals(learn, s.retry())
        assertEquals(learn, s.dontKnow())
        val question = s.toGuided()
        assertEquals(question, s.answer(-1))
        assertEquals(question, s.answer(5))
        assertEquals(question, s.next())
        assertEquals(question, s.back())
        assertEquals(question, s.skipLearn())
        assertEquals(question, s.dontKnow())
        assertEquals(question, s.retry())
        assertTrue(s.attempts.isEmpty())
    }

    // ---- comprobación sin ayuda ----

    @Test
    fun alTerminarLosGuiadosVieneLaIntroduccionDeLaComprobacion() {
        val s = newSession()
        val st = s.finishGuidedCorrectly()
        assertEquals(Phase.UNAIDED_CHECK, st.phase)
        assertEquals(TutorScreen.UNAIDED_INTRO, st.screen)
        assertEquals(setOf(TutorAction.NEXT), st.actions)
        assertNull(st.exercise)
        assertEquals(3, s.attempts.size)
        assertTrue(s.attempts.all { it.mode == AttemptMode.GUIDED })
    }

    @Test
    fun laComprobacionNoOfreceAyuda() {
        val s = newSession()
        s.finishGuidedCorrectly()
        val st = s.next()
        assertEquals(TutorScreen.QUESTION, st.screen)
        assertEquals(PetCue.THINK, st.cue)
        assertEquals(Progress(Phase.UNAIDED_CHECK, 1, 2), st.progress)
        val view = checkNotNull(st.exercise)
        assertTrue(view.hintsShown.isEmpty())
        assertEquals(0, view.hintsRemaining)
        assertTrue(view.eliminated.isEmpty())
        assertTrue(view.solutionSteps.isEmpty())
        assertNull(view.correctIndex)
        assertTrue(TutorAction.ANSWER in st.actions)
        assertTrue(TutorAction.DONT_KNOW in st.actions)
        assertFalse(TutorAction.REQUEST_HINT in st.actions)
        assertFalse(TutorAction.SHOW_SOLUTION in st.actions)
        assertFalse(TutorAction.RETRY in st.actions)
        assertEquals(st, s.requestHint())
        assertEquals(st, s.showSolution())
        assertEquals(st, s.retry())
    }

    @Test
    fun laComprobacionNoDiceSiAcertasHastaElResumen() {
        val right = newSession()
        val wrong = newSession()
        right.finishGuidedCorrectly()
        wrong.finishGuidedCorrectly()
        right.next()
        wrong.next()
        assertEquals(right.state, wrong.state)

        val afterRight = right.answer(correctOf(right.state))
        val afterWrong = wrong.answer(wrongOf(wrong.state))
        // El estado completo es idéntico: ni texto, ni mascota, ni acciones delatan el resultado.
        assertEquals(afterRight, afterWrong)
        assertEquals(TutorScreen.QUESTION, afterRight.screen)
        assertEquals(PetCue.THINK, afterRight.cue)
        assertNull(checkNotNull(afterRight.exercise).correctIndex)
        assertNull(afterRight.summary)

        val endRight = right.answer(correctOf(right.state))
        val endWrong = wrong.answer(wrongOf(wrong.state))
        assertEquals(Phase.SUMMARY, endRight.phase)
        assertEquals(Phase.SUMMARY, endWrong.phase)
        assertNotEquals(endRight, endWrong)
    }

    @Test
    fun enLaComprobacionNingunEstadoMuestraRetroalimentacionNiSolucion() {
        val s = newSession()
        s.finishGuidedCorrectly()
        s.next()
        val states = mutableListOf(s.state)
        val firstWrong = wrongOf(s.state)
        val feedback = exerciseOf(s.state).optionFeedback.getValue(firstWrong)
        states += s.answer(firstWrong)
        for (st in states) {
            assertFalse(st.text.contains(feedback))
            assertTrue(checkNotNull(st.exercise).solutionSteps.isEmpty())
        }
    }

    @Test
    fun noSeSabeCuentaComoIncorrecta() {
        val s = newSession()
        s.finishGuidedCorrectly()
        val st = s.runUnaided(listOf(null, true))
        val summary = checkNotNull(st.summary)
        assertEquals(1, summary.unaidedCorrect)
        val miss = summary.unaidedReview.first()
        assertFalse(miss.correct)
        assertEquals(Attempt.NO_ANSWER, miss.selectedIndex)
        val logged = s.attempts.filter { it.mode == AttemptMode.UNAIDED }.first()
        assertEquals(Attempt.NO_ANSWER, logged.selectedIndex)
        assertFalse(logged.correct)
        assertEquals(0, logged.hintsShownBefore)
    }

    @Test
    fun elNoSeNoExisteEnLaPracticaGuiada() {
        val s = newSession()
        val question = s.toGuided()
        assertEquals(question, s.dontKnow())
    }

    // ---- resumen y repaso ----

    @Test
    fun dosDeDosSinAyudaCelebraYProponeRepasoEnTresDias() {
        val s = newSession()
        s.finishGuidedCorrectly()
        val st = s.runUnaided(listOf(true, true))
        assertEquals(Phase.SUMMARY, st.phase)
        assertEquals(TutorScreen.SUMMARY, st.screen)
        assertEquals(PetCue.CELEBRATE, st.cue)
        assertTrue(st.actions.isEmpty())
        assertNull(st.exercise)
        val summary = checkNotNull(st.summary)
        assertEquals(2, summary.unaidedCorrect)
        assertEquals(2, summary.unaidedTotal)
        assertEquals(3, summary.guidedSolved)
        assertEquals(MasteryStatus.DOMINIO_SIN_AYUDA, summary.mastery)
        val review = checkNotNull(summary.review)
        assertEquals(3, review.dayOffset)
        assertFalse(review.relearn)
    }

    @Test
    fun unaDeDosAlientaYProponeRepasoMañana() {
        val s = newSession()
        s.finishGuidedCorrectly()
        val st = s.runUnaided(listOf(true, false))
        assertEquals(PetCue.ENCOURAGE, st.cue)
        val summary = checkNotNull(st.summary)
        assertEquals(MasteryStatus.SOLO_CON_AYUDA, summary.mastery)
        val review = checkNotNull(summary.review)
        assertEquals(1, review.dayOffset)
        assertFalse(review.relearn)
        assertEquals(listOf(true, false), summary.unaidedReview.map { it.correct })
    }

    @Test
    fun ceroDeDosDescansaYProponeVolverAAprenderMañana() {
        val s = newSession()
        s.finishGuidedCorrectly()
        val st = s.runUnaided(listOf(false, null))
        assertEquals(PetCue.REST, st.cue)
        val review = checkNotNull(checkNotNull(st.summary).review)
        assertEquals(1, review.dayOffset)
        assertTrue(review.relearn)
    }

    @Test
    fun elResumenRepasaCadaEjercicioSinAyudaConSuSolucion() {
        val s = newSession()
        s.finishGuidedCorrectly()
        val summary = checkNotNull(s.runUnaided(listOf(false, true)).summary)
        val wrongItem = summary.unaidedReview[0]
        val e = checkNotNull(TutorRegistry.regenerate(wrongItem.exerciseId))
        assertEquals(e.statement, wrongItem.statement)
        assertEquals(e.correctIndex, wrongItem.correctIndex)
        assertEquals(e.optionFeedback.getValue(wrongItem.selectedIndex), wrongItem.feedback)
        assertEquals(e.solutionSteps, wrongItem.solutionSteps)
        assertEquals("Correcta.", summary.unaidedReview[1].feedback)
    }

    @Test
    fun elResumenNoExisteAntesDelFinal() {
        val s = newSession()
        s.finishGuidedCorrectly()
        s.next()
        assertNull(s.state.summary)
        s.answer(correctOf(s.state))
        assertNull(s.state.summary)
    }

    @Test
    fun elDominioTieneEnCuentaElHistorialDeOtrasSesiones() {
        fun old(topicId: String, ok: Boolean) =
            Attempt(1_000L, topicId, "$topicId-d2-s1", AttemptMode.UNAIDED, if (ok) 0 else 1, ok, 0, 5_000)

        // Una sola comprobación sin ayuda en la sesión, para que el historial decida la ventana de 2.
        fun masteryWith(history: List<Attempt>): MasteryStatus {
            val s = newSession(config = SessionConfig(seed = 4L, unaidedDifficulties = listOf(2), history = history))
            s.finishGuidedCorrectly()
            return checkNotNull(s.runUnaided(listOf(true)).summary).mastery
        }
        assertEquals(MasteryStatus.DOMINIO_SIN_AYUDA, masteryWith(listOf(old("ecuaciones-lineales", true))))
        assertEquals(MasteryStatus.SOLO_CON_AYUDA, masteryWith(listOf(old("ecuaciones-lineales", false))))
        assertEquals(MasteryStatus.EN_PRACTICA, masteryWith(listOf(old("pitagoras", true)))) // otro tema: se ignora
        assertEquals(MasteryStatus.EN_PRACTICA, masteryWith(emptyList()))
    }

    // ---- registro, reloj y determinismo ----

    @Test
    fun cadaRespuestaSeAnotaConSuModoPistasYTiempo() {
        val clock = FakeClock()
        val s = newSession(clock = clock)
        s.toGuided()
        clock.advance(4_000)
        s.answer(wrongOf(s.state))
        s.retry()
        clock.advance(6_000)
        s.requestHint()
        s.answer(correctOf(s.state))
        val (first, second) = s.attempts
        assertEquals(FakeClock.START + 4_000, first.timestampMillis)
        assertEquals(4_000L, first.elapsedMs)
        assertEquals(0, first.hintsShownBefore)
        assertFalse(first.correct)
        assertEquals(AttemptMode.GUIDED, first.mode)
        assertEquals(FakeClock.START + 10_000, second.timestampMillis)
        // El tiempo del parecido cuenta desde que se mostró (al reintentar).
        assertEquals(6_000L, second.elapsedMs)
        assertEquals(1, second.hintsShownBefore)
        assertTrue(second.correct)
        assertEquals("ecuaciones-lineales", first.topicId)
        assertNotNull(TutorRegistry.regenerate(first.exerciseId))
    }

    @Test
    fun elTiempoDeCadaEjercicioEmpiezaCuandoSeMuestra() {
        val clock = FakeClock()
        val s = newSession(clock = clock)
        s.finishGuidedCorrectly()
        s.next()
        clock.advance(7_500)
        s.answer(correctOf(s.state))
        clock.advance(2_000)
        s.answer(correctOf(s.state))
        val unaided = s.attempts.filter { it.mode == AttemptMode.UNAIDED }
        assertEquals(listOf(7_500L, 2_000L), unaided.map { it.elapsedMs })
    }

    @Test
    fun lasAccionesSeEjecutanSinRelojRealYElTiempoNuncaEsNegativo() {
        val clock = FakeClock()
        val s = newSession(clock = clock)
        s.toGuided()
        clock.now -= 10_000 // un reloj que retrocede no produce tiempos negativos
        s.answer(correctOf(s.state))
        assertEquals(0L, s.attempts.single().elapsedMs)
    }

    @Test
    fun laMismaSemillaYLasMismasAccionesDanLosMismosEstados() {
        fun script(seed: Long): List<TutorState> {
            val s = newSession(config = SessionConfig(seed = seed))
            val states = mutableListOf(s.start())
            states += s.next()
            states += s.skipLearn()
            states += s.answer(wrongOf(s.state))
            states += s.requestHint()
            states += s.answer(correctOf(s.state))
            states += s.next()
            return states
        }
        assertEquals(script(21L), script(21L))
        assertNotEquals(script(21L).last().exercise, script(22L).last().exercise)
    }

    @Test
    fun laSemillaDeLaSesionDefineLosEjercicios() {
        val a = newSession(config = SessionConfig(seed = 100L))
        val b = newSession(config = SessionConfig(seed = 100L))
        val c = newSession(config = SessionConfig(seed = 101L))
        assertEquals(a.toGuided().exercise, b.toGuided().exercise)
        assertNotEquals(checkNotNull(a.state.exercise).id, checkNotNull(c.toGuided().exercise).id)
    }

    @Test
    fun lasSemillasSonPositivasYNingunEnunciadoSeRepite() {
        for (t in TutorRegistry.topics) for (seed in listOf(1L, 2L, 99L, 123_456L)) {
            val s = newSession(t.id, SessionConfig(seed = seed))
            val statements = mutableListOf(checkNotNull(t.workedExample).statement)
            for (st in s.playPerfect()) {
                val view = st.exercise ?: continue
                if (view.statement !in statements) statements += view.statement
                val id = Regex("""-s(-?\d+)$""").find(view.id)
                assertTrue("${t.id} seed $seed: id ${view.id}", id != null && id.groupValues[1].toLong() > 0)
            }
            // ejemplo + 3 guiados + 2 sin ayuda, todos distintos
            assertEquals("${t.id} seed $seed: $statements", 6, statements.size)
        }
    }

    @Test
    fun todosLosTemasCompletanUnaSesionConTodoCorrecto() {
        for (t in TutorRegistry.topics) {
            val s = newSession(t.id)
            val states = s.playPerfect()
            val last = states.last()
            assertEquals(t.id, Phase.SUMMARY, last.phase)
            assertEquals(t.id, PetCue.CELEBRATE, last.cue)
            assertEquals(t.id, 5, s.attempts.size)
            assertEquals(t.id, 3, s.attempts.count { it.mode == AttemptMode.GUIDED })
            assertEquals(t.id, 2, s.attempts.count { it.mode == AttemptMode.UNAIDED })
            assertTrue(t.id, s.attempts.all { it.correct && it.topicId == t.id })
            assertEquals(t.id, MasteryStatus.DOMINIO_SIN_AYUDA, checkNotNull(last.summary).mastery)
        }
    }

    @Test
    fun laFaseAvanzaEnOrden() {
        val phases = newSession().playPerfect().map { it.phase }.distinct()
        assertEquals(listOf(Phase.LEARN, Phase.GUIDED_PRACTICE, Phase.UNAIDED_CHECK, Phase.SUMMARY), phases)
    }

    @Test
    fun laCantidadDeEjerciciosSeConfigura() {
        val config = SessionConfig(seed = 8L, guidedDifficulties = listOf(1, 3), unaidedDifficulties = listOf(2, 2, 3))
        val s = newSession(config = config)
        assertEquals(Progress(Phase.GUIDED_PRACTICE, 1, 2), s.toGuided().progress)
        val last = s.playPerfect().last()
        assertEquals(5, s.attempts.size)
        assertEquals(3, checkNotNull(last.summary).unaidedTotal)
        assertEquals(listOf(1, 3), s.attempts.filter { it.mode == AttemptMode.GUIDED }.map { exerciseDifficulty(it) })
        assertEquals(listOf(2, 2, 3), s.attempts.filter { it.mode == AttemptMode.UNAIDED }.map { exerciseDifficulty(it) })
    }

    private fun exerciseDifficulty(a: Attempt): Int = checkNotNull(TutorRegistry.regenerate(a.exerciseId)).difficulty

    @Test
    fun laConfiguracionInvalidaSeRechaza() {
        for (bad in listOf(
            { SessionConfig(guidedDifficulties = emptyList()) },
            { SessionConfig(unaidedDifficulties = emptyList()) },
            { SessionConfig(guidedDifficulties = listOf(4)) },
            { SessionConfig(unaidedDifficulties = listOf(0)) },
        )) {
            try {
                bad()
                org.junit.Assert.fail("debía rechazar la configuración")
            } catch (expected: IllegalArgumentException) {
                // correcto
            }
        }
    }

    // ---- mascota y voz ----

    @Test
    fun laSenalDeLaMascotaSeTraduceAlEstadoDeLaApp() {
        assertEquals("hablar", PetCue.EXPLAIN.petState)
        assertEquals("pensar", PetCue.THINK.petState)
        assertEquals("feliz", PetCue.CORRECT.petState)
        assertEquals("alerta", PetCue.ENCOURAGE.petState)
        assertEquals("sorprendido", PetCue.ENCOURAGE.altPetState)
        assertEquals("celebrar", PetCue.CELEBRATE.petState)
        assertEquals("descansar", PetCue.REST.petState)
        val valid = setOf(
            "idle", "saludar", "escuchar", "pensar", "hablar", "estudiar", "feliz", "celebrar", "sorprendido",
            "alerta", "descansar", "dormir", "flotar",
        )
        for (cue in PetCue.values()) {
            assertTrue(cue.name, cue.petState in valid)
            assertTrue(cue.name, cue.altPetState == null || cue.altPetState in valid)
        }
    }

    @Test
    fun elTextoParaVozNoTraeSimbolosMatematicos() {
        val symbols = Regex("""[\^²³√÷·×°≈=/%]""")
        for (t in TutorRegistry.topics) {
            val states = newSession(t.id).playPerfect() + wrongPath(t.id)
            for (st in states) {
                assertTrue("${t.id}: speech vacío en ${st.screen}", st.speech.isNotBlank())
                assertFalse("${t.id} ${st.screen}: '${st.speech}'", symbols.containsMatchIn(st.speech))
            }
        }
    }

    /** Estados de un recorrido con errores: pista pedida, error con solución, uno parecido y solución pedida. */
    private fun wrongPath(topicId: String): List<TutorState> {
        val s = newSession(topicId, SessionConfig(seed = 31L))
        val seen = mutableListOf<TutorState>()
        seen += s.toGuided()
        seen += s.requestHint()
        seen += s.answer(wrongOf(s.state))
        seen += s.retry()
        seen += s.showSolution()
        return seen
    }
}
