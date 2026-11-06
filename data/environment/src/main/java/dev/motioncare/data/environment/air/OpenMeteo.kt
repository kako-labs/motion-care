package dev.motioncare.data.environment.air

import dev.motioncare.data.environment.location.Position
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

private const val TIMEOUT_MS = 10_000

/** Particulate matter for one place. [observedAtEpochMs] is the forecast hour the provider reports. */
internal data class AirSample(val pm10: Double?, val pm25: Double?, val observedAtEpochMs: Long)

/** The provider answered, but not with usable values. Network failures stay [IOException]. */
internal class AirResponseException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal fun interface OutdoorAirSource {
    /** Throws [IOException] when the provider cannot be reached and [AirResponseException] for a bad answer. */
    suspend fun fetch(position: Position.Found): AirSample
}

// The global domain is set explicitly so the source does not change with the position. No key is needed
// for non-commercial use; see docs/environment.md for the terms.
internal fun openMeteoUrl(latitude: Double, longitude: Double): String =
    "https://air-quality-api.open-meteo.com/v1/air-quality?" +
        String.format(Locale.ROOT, "latitude=%.1f&longitude=%.1f", latitude, longitude) +
        "&current=pm10,pm2_5&domains=cams_global&timeformat=unixtime"

internal fun parseOpenMeteo(body: String): AirSample {
    val json = try {
        JSONObject(body)
    } catch (error: JSONException) {
        throw AirResponseException("Response is not JSON", error)
    }
    if (json.optBoolean("error")) throw AirResponseException("Provider error: ${json.optString("reason")}")
    val current = json.optJSONObject("current") ?: throw AirResponseException("Response has no current values")
    if (!current.has("time")) throw AirResponseException("Response has no forecast time")
    return AirSample(
        pm10 = current.numberOrNull("pm10"),
        pm25 = current.numberOrNull("pm2_5"),
        observedAtEpochMs = current.getLong("time") * 1_000,
    )
}

private fun JSONObject.numberOrNull(name: String): Double? =
    if (isNull(name)) null else optDouble(name).takeUnless { it.isNaN() }

internal class OpenMeteoAirSource(private val io: CoroutineDispatcher = Dispatchers.IO) : OutdoorAirSource {
    override suspend fun fetch(position: Position.Found): AirSample = withContext(io) {
        val connection = URL(openMeteoUrl(position.latitude, position.longitude)).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("Open-Meteo returned HTTP $code")
            parseOpenMeteo(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}
