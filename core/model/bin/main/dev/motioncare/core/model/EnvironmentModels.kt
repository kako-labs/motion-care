package dev.motioncare.core.model

/** Where an environment value came from. Values from different sources are never merged. */
enum class EnvironmentSource {
    /** Read from the car this app runs in. */
    VEHICLE,

    /** Open-Meteo air quality API, CAMS global forecast. A model forecast, not a station measurement. */
    OPEN_METEO_CAMS,
}

/** Whether a reading holds a value. Only [AVAILABLE] and [STALE] carry one. */
enum class Availability { AVAILABLE, LOADING, UNSUPPORTED, UNAVAILABLE, STALE }

/** Why a supported reading has no value right now. */
enum class MissingReason { NO_PERMISSION, NO_LOCATION, NETWORK, NO_DATA }

/** One environment value with its source and time. A missing value is never replaced by zero or another source. */
data class EnvironmentReading<out T : Any>(
    val value: T?,
    val availability: Availability,
    val source: EnvironmentSource,
    val observedAtEpochMs: Long? = null,
    val missingReason: MissingReason? = null,
) {
    init {
        val holdsValue = availability == Availability.AVAILABLE || availability == Availability.STALE
        require(holdsValue == (value != null)) { "$availability reading must ${if (holdsValue) "" else "not "}hold a value" }
        require(holdsValue == (observedAtEpochMs != null)) { "A value needs its observation time" }
        require(missingReason == null || availability == Availability.UNAVAILABLE) { "Only unavailable readings have a reason" }
    }
}

/** Korean particulate matter grade. See docs/environment.md for the table and how it is applied. */
enum class AirGrade { GOOD, MODERATE, BAD, VERY_BAD }

/**
 * Outdoor particulate matter in µg/m³ around a position rounded to 0.1°.
 * [grade] is the worse of the two pollutant grades.
 */
data class OutdoorAir(
    val pm10: Double?,
    val pm25: Double?,
    val grade: AirGrade?,
    val latitude: Double,
    val longitude: Double,
)

/** Environment values read together. Temperatures are °C. The car offers no cabin air quality value. */
data class EnvironmentSnapshot(
    val capturedAtEpochMs: Long,
    val outsideTemperature: EnvironmentReading<Double>,
    val cabinTemperature: EnvironmentReading<Double>,
    val cabinSetTemperature: EnvironmentReading<Double>,
    val recirculation: EnvironmentReading<Boolean>,
    val airCleaner: EnvironmentReading<Boolean>,
    val outdoorAir: EnvironmentReading<OutdoorAir>,
)
