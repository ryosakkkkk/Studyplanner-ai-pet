package pe.studyplanner.aipet.domain.pet
import org.junit.Assert.assertEquals
import org.junit.Test
class PetAnimationControllerTest {
 @Test fun voiceStatesReflectActualEngineEvents() {
  assertEquals(PetState.LISTEN,PetAnimationController.fromVoice("LISTENING"))
  assertEquals(PetState.SPEAK,PetAnimationController.fromVoice("SPEAKING"))
  assertEquals(PetState.THINK,PetAnimationController.fromVoice("IDLE",true))
  assertEquals(PetState.ALERT,PetAnimationController.fromVoice("ERROR"))
  assertEquals(PetState.IDLE,PetAnimationController.fromVoice("REVIEW"))
 }
}
