package pe.studyplanner.aipet.domain.calendar

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Never applies java.time's automatic daylight-saving gap/overlap correction. */
object StrictLocalTime {
    fun zone(zoneId: String): ZoneId {
        require(zoneId in ZoneId.getAvailableZoneIds()) { "Selecciona una zona IANA válida." }
        return ZoneId.of(zoneId)
    }

    fun resolve(local: LocalDateTime, zoneId: String, preferredOffset: ZoneOffset? = null): Instant {
        val offsets = zone(zoneId).rules.getValidOffsets(local)
        if (offsets.isEmpty()) {
            throw DateTimeException("Esta hora no existe en $zoneId por el cambio de horario. Elige otra hora.")
        }
        if (preferredOffset != null) {
            if (preferredOffset !in offsets) throw DateTimeException("El desfase elegido no corresponde a esta fecha y zona.")
            return local.toInstant(preferredOffset)
        }
        if (offsets.size != 1) {
            throw DateTimeException("Esta hora ocurre dos veces en $zoneId. Debes elegir un desfase explícito.")
        }
        return local.toInstant(offsets.single())
    }
}
