package works.axiora.z370

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Outside weather from Open-Meteo (free, no key) for where the car is, refreshed every 20 minutes.
 * The last answer is kept, so the tile has something to show straight after a cold start.
 */
data class WeatherNow(val tempC: Float, val code: Int, val hiC: Float?, val loC: Float?, val isDay: Boolean)

object WeatherRepo {
    val now = mutableStateOf<WeatherNow?>(null)
    private const val KEY = "weather_json"

    fun restore(ctx: Context) {
        if (now.value != null) return
        ctx.getSharedPreferences("z370", Context.MODE_PRIVATE).getString(KEY, null)?.let { runCatching { now.value = parse(JSONObject(it)) } }
    }

    suspend fun fetch(ctx: Context, lat: Double, lon: Double): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(
                String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f", lat, lon) +
                    "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto",
            )
            val c = url.openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            val body = c.inputStream.bufferedReader().readText()
            now.value = parse(JSONObject(body))
            ctx.getSharedPreferences("z370", Context.MODE_PRIVATE).edit().putString(KEY, body).apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun parse(j: JSONObject): WeatherNow {
        val cur = j.getJSONObject("current")
        val daily = j.optJSONObject("daily")
        return WeatherNow(
            tempC = cur.getDouble("temperature_2m").toFloat(),
            code = cur.getInt("weather_code"),
            hiC = daily?.optJSONArray("temperature_2m_max")?.optDouble(0)?.takeIf { !it.isNaN() }?.toFloat(),
            loC = daily?.optJSONArray("temperature_2m_min")?.optDouble(0)?.takeIf { !it.isNaN() }?.toFloat(),
            isDay = cur.optInt("is_day", 1) == 1,
        )
    }

    /** WMO weather code to a short label. */
    fun label(code: Int) = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Rain"
        71, 73, 75, 77, 85, 86 -> "Snow"
        95, 96, 99 -> "Thunderstorm"
        else -> "—"
    }

    /** 24-unit line icon path for the code. */
    fun icon(code: Int, day: Boolean) = when (code) {
        0, 1 -> if (day) "M12 8a4 4 0 1 0 0.01 0z M12 2v2 M12 20v2 M4.9 4.9l1.4 1.4 M17.7 17.7l1.4 1.4 M2 12h2 M20 12h2 M4.9 19.1l1.4-1.4 M17.7 6.3l1.4-1.4"
                else "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"
        2 -> "M8 6a3.5 3.5 0 0 1 6.6-1 M4 4.5l1 1 M7 18h10a4 4 0 0 0 0-8 5.5 5.5 0 0 0-10.6 1.5A3.3 3.3 0 0 0 7 18z"
        45, 48 -> "M4 9h16 M3 13h18 M5 17h14"
        in 51..67, in 80..82 -> "M7 14h10a4 4 0 0 0 0-8 5.5 5.5 0 0 0-10.6 1.5A3.3 3.3 0 0 0 7 14z M8 17l-1 3 M12 17l-1 3 M16 17l-1 3"
        in 95..99 -> "M7 14h10a4 4 0 0 0 0-8 5.5 5.5 0 0 0-10.6 1.5A3.3 3.3 0 0 0 7 14z M12 15l-2 4h4l-2 4"
        else -> "M7 18h10a4 4 0 0 0 0-8 5.5 5.5 0 0 0-10.6 1.5A3.3 3.3 0 0 0 7 18z"
    }
}

/** Keeps the weather fresh from the car's position (Dubai until the first fix). */
@Composable
fun WeatherUpdater(fixState: State<Fix>) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        WeatherRepo.restore(ctx)
        while (true) {
            val f = fixState.value
            val ok = WeatherRepo.fetch(ctx, f.lat ?: 25.2048, f.lon ?: 55.2708)
            delay(if (ok) 20 * 60_000L else 2 * 60_000L)
        }
    }
}

@Composable
fun ColumnScope.WeatherTileBody(accent: Color) {
    val w = WeatherRepo.now.value
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        T(w?.let { "%.0f°".format(it.tempC) } ?: "--", 96, Z.Bright, Condensed, FontWeight.Light)
        if (w != null) LineIcon(WeatherRepo.icon(w.code, w.isDay), accent, 54.dp, 1.8f)
    }
    T(w?.let { WeatherRepo.label(it.code).uppercase() } ?: "WAITING FOR WEATHER", 20, Color(0xFFDDE1E6), spacing = 2f)
    if (w?.hiC != null && w.loC != null) {
        Row {
            T("H %.0f°".format(w.hiC), 20, Color(0xFFDDE1E6), spacing = 1f)
            Spacer(Modifier.width(18.dp))
            T("L %.0f°".format(w.loC), 20, Color(0xFFDDE1E6), spacing = 1f)
        }
    }
}
