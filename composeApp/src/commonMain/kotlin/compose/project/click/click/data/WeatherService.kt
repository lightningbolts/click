package compose.project.click.click.data

import compose.project.click.click.data.models.WeatherSnapshot
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface WeatherService {
    suspend fun fetchWeather(lat: Double, lon: Double): WeatherSnapshot?
}

class OpenMeteoWeatherService(
    private val client: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
) : WeatherService {

    override suspend fun fetchWeather(lat: Double, lon: Double): WeatherSnapshot? {
        if (!lat.isFinite() || !lon.isFinite() || (lat == 0.0 && lon == 0.0)) {
            return null
        }

        return try {
            val response = client.get(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,wind_speed_10m,wind_direction_10m,pressure_msl"
            ).body<OpenMeteoResponse>()

            val current = response.current ?: return null
            WeatherSnapshot(
                condition = current.weatherCode.toConditionLabel(),
                temperatureCelsius = current.temperature.toFloat(),
                iconCode = current.weatherCode.toIconCode(),
                windSpeedKph = current.windSpeed?.toFloat(),
                windDirectionDegrees = current.windDirection?.toInt(),
                pressureMslHpa = current.pressureMsl
            )
        } catch (error: Exception) {
            println("OpenMeteoWeatherService: Failed to fetch weather: ${error.message}")
            null
        }
    }
}

@Serializable
private data class OpenMeteoResponse(
    val current: OpenMeteoCurrentWeather? = null
)

@Serializable
private data class OpenMeteoCurrentWeather(
    @SerialName("temperature_2m")
    val temperature: Double,
    @SerialName("weather_code")
    val weatherCode: Int,
    @SerialName("wind_speed_10m")
    val windSpeed: Double? = null,
    @SerialName("wind_direction_10m")
    val windDirection: Double? = null,
    @SerialName("pressure_msl")
    val pressureMsl: Double? = null
)

private fun Int.toConditionLabel(): String = when (this) {
    0 -> "Sunny"
    1, 2, 3 -> "Cloudy"
    45, 48 -> "Foggy"
    51, 53, 55, 56, 57 -> "Drizzly"
    61, 63, 65, 66, 67, 80, 81, 82 -> "Rainy"
    71, 73, 75, 77, 85, 86 -> "Snowy"
    95, 96, 99 -> "Stormy"
    else -> "Clear"
}

private fun Int.toIconCode(): String = when (this) {
    0 -> "clear"
    1, 2, 3 -> "cloudy"
    45, 48 -> "fog"
    51, 53, 55, 56, 57 -> "drizzle"
    61, 63, 65, 66, 67, 80, 81, 82 -> "rain"
    71, 73, 75, 77, 85, 86 -> "snow"
    95, 96, 99 -> "thunder"
    else -> "clear"
}