package works.axiora.z370

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/**
 * Every user choice, as Compose state backed by SharedPreferences: change one anywhere and the
 * whole launcher follows at once, and it survives restarts. Load once from MainActivity.
 */
object Cfg {
    private var sp: SharedPreferences? = null

    // Look
    val bg = mutableStateOf("ink")              // ink | black | navy | warm
    val glass = mutableFloatStateOf(1f)         // 0.6 subtle · 1 normal · 1.5 strong
    val glow = mutableFloatStateOf(1f)          // ambient glow: 0 off · 0.6 low · 1 full
    val nightTheme = mutableStateOf("red")      // off | red | dim  (with the headlights on)

    // Home
    val leftTile = mutableStateOf("weather")    // weather | cabin | steering | doors

    // Units
    val mph = mutableStateOf(false)
    val fahrenheit = mutableStateOf(false)

    // Car
    val carName = mutableStateOf("370Z")
    val driveFace = mutableStateOf("sweep")     // sweep | numbers | minimal
    val redlineKmh = mutableIntStateOf(140)     // speed warning on the Drive page (0 = off)

    // Garage
    val odometerKm = mutableIntStateOf(0)
    val oilAtKm = mutableIntStateOf(0)
    val oilAtMs = mutableStateOf(0L)
    val oilEveryKm = mutableIntStateOf(10000)
    val oilEveryMonths = mutableIntStateOf(6)
    val serviceLog = mutableStateOf<List<ServiceEntry>>(emptyList())
    val checks = mutableStateOf<Set<String>>(emptySet())

    // Shell
    val page = mutableStateOf("home")

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("z370_cfg", Context.MODE_PRIVATE); sp = p
        bg.value = p.getString("bg", "ink")!!
        glass.floatValue = p.getFloat("glass", 1f)
        glow.floatValue = p.getFloat("glow", 1f)
        nightTheme.value = p.getString("nightTheme", "red")!!
        leftTile.value = p.getString("leftTile", "weather")!!
        mph.value = p.getBoolean("mph", false)
        fahrenheit.value = p.getBoolean("fahrenheit", false)
        carName.value = p.getString("carName", "370Z")!!
        driveFace.value = p.getString("driveFace", "sweep")!!
        redlineKmh.intValue = p.getInt("redlineKmh", 140)
        odometerKm.intValue = p.getInt("odometerKm", 0)
        oilAtKm.intValue = p.getInt("oilAtKm", 0)
        oilAtMs.value = p.getLong("oilAtMs", 0L)
        oilEveryKm.intValue = p.getInt("oilEveryKm", 10000)
        oilEveryMonths.intValue = p.getInt("oilEveryMonths", 6)
        serviceLog.value = runCatching {
            val a = JSONArray(p.getString("serviceLog", "[]"))
            (0 until a.length()).map { ServiceEntry.from(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        checks.value = p.getStringSet("checks", emptySet())!!.toSet()
        page.value = p.getString("page", "home")!!
    }

    fun set(key: String, v: Any) {
        val e = sp?.edit() ?: return
        when (v) {
            is String -> e.putString(key, v); is Boolean -> e.putBoolean(key, v)
            is Int -> e.putInt(key, v); is Long -> e.putLong(key, v); is Float -> e.putFloat(key, v)
        }
        e.apply()
    }

    /** Update a state and persist it in one go. */
    fun <T : Any> put(state: MutableState<T>, key: String, v: T) { state.value = v; set(key, v) }

    fun addService(entry: ServiceEntry) {
        serviceLog.value = (listOf(entry) + serviceLog.value).take(200)
        saveLog()
        if (entry.km > odometerKm.intValue) { odometerKm.intValue = entry.km; set("odometerKm", entry.km) }
        if (entry.kind == "Oil & filter") {
            oilAtKm.intValue = entry.km; set("oilAtKm", entry.km)
            oilAtMs.value = entry.atMs; set("oilAtMs", entry.atMs)
        }
    }

    fun removeService(entry: ServiceEntry) { serviceLog.value = serviceLog.value - entry; saveLog() }

    private fun saveLog() = set("serviceLog", JSONArray(serviceLog.value.map { it.json() }).toString())

    fun toggleCheck(id: String) {
        checks.value = if (id in checks.value) checks.value - id else checks.value + id
        sp?.edit()?.putStringSet("checks", checks.value)?.apply()
    }

    fun resetChecks() { checks.value = emptySet(); sp?.edit()?.putStringSet("checks", emptySet())?.apply() }

    // ---- display helpers
    fun speed(kmh: Int?): String = kmh?.let { if (mph.value) (it / 1.609f).toInt().toString() else it.toString() } ?: "--"
    val speedUnit get() = if (mph.value) "MPH" else "KM/H"
    fun dist(km: Float): String = if (mph.value) "%.1f MI".format(km / 1.609f) else "%.1f KM".format(km)
    fun temp(c: Float?): String = c?.let { if (fahrenheit.value) "%.0f°".format(it * 9 / 5 + 32) else "%.0f°".format(it) } ?: "--"

    /** Base colour behind the glass, per the chosen background. */
    val ground: Color get() = when (bg.value) {
        "black" -> Color(0xFF000000)
        "navy" -> Color(0xFF060A12)
        "warm" -> Color(0xFF0B0806)
        else -> Color(0xFF050506)
    }
}

data class ServiceEntry(val atMs: Long, val km: Int, val kind: String, val note: String) {
    fun json() = JSONObject().put("at", atMs).put("km", km).put("kind", kind).put("note", note)
    companion object {
        fun from(o: JSONObject) = ServiceEntry(o.optLong("at"), o.optInt("km"), o.optString("kind"), o.optString("note"))
    }
}
