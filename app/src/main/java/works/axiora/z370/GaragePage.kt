package works.axiora.z370

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Factory figures, 2020 370Z Sport Touring (Nissan US press specifications). */
private val Specs = listOf(
    "Engine" to "VQ37VHR 3.7 L V6, VVEL",
    "Power" to "332 hp (248 kW) @ 7,000 rpm",
    "Torque" to "270 lb-ft (366 Nm) @ 5,200 rpm",
    "Rev limit" to "7,500 rpm",
    "Compression" to "11.0 : 1",
    "Gearbox" to "7-speed automatic, paddles",
    "Kerb weight" to "1,560 kg (7AT)",
    "Fuel" to "Premium unleaded, 72 L tank",
    "Engine oil" to "4.9 L with filter",
    "Length / width" to "4,255 / 1,844 mm",
    "Height / wheelbase" to "1,316 / 2,550 mm",
    "Brakes (Sport)" to "320 mm vented discs F/R",
)

private val Tyres = listOf(
    "Front" to "245/40 R19 on 19 × 9 RAYS forged",
    "Rear" to "275/35 R19 on 19 × 10 RAYS forged",
    "Factory tyre" to "Bridgestone Potenza S007",
)

private val ServiceKinds = listOf("Oil & filter", "Tyres", "Brakes", "Inspection", "Other")

private val Checklist = listOf(
    "tyres" to "Tyre pressures checked",
    "oil" to "Oil level on the dipstick",
    "coolant" to "Coolant at the line",
    "lights" to "All lights working",
    "wipers" to "Washer fluid topped up",
    "dash" to "No warning lights on start",
)

@Composable
fun GaragePage(accent: Color, car: CarState) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Hero
        Column(Modifier.padding(start = 8.dp)) {
            T("GARAGE", 18, Soft, spacing = 4f)
            T(Cfg.carName.value.ifBlank { "370Z" }, 96, Z.Bright, Condensed, FontWeight.Light)
            T("2020 NISSAN 370Z SPORT TOURING", 20, Muted, spacing = 3f)
        }

        ServiceDue(accent)
        LogCard(accent)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Card(Modifier.weight(1f), "PRE-DRIVE CHECK") {
                Checklist.forEach { (id, label) ->
                    val on = id in Cfg.checks.value
                    Row(
                        Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(24.dp)).clickable { Cfg.toggleCheck(id) }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (on) accent else Color.Transparent)
                                .border(2.dp, if (on) accent else Color.White.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) { if (on) LineIcon("M5 12l5 5 9-10", Z.Bright, 28.dp, 2.6f) }
                        T(label, 24, if (on) Muted else Z.Bright)
                    }
                }
                val done = Checklist.count { it.first in Cfg.checks.value }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("$done of ${Checklist.size} done", 20, Muted, modifier = Modifier.weight(1f))
                    GlassPill("RESET") { Cfg.resetChecks() }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Card(Modifier.fillMaxWidth(), "TYRES") {
                    Tyres.forEach { (k, v) -> SpecRow(k, v) }
                }
                Card(Modifier.fillMaxWidth(), "ODOMETER") {
                    var text by remember { mutableStateOf(Cfg.odometerKm.intValue.takeIf { it > 0 }?.toString() ?: "") }
                    T("Type what the cluster shows; reminders count from it.", 18, Muted)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        GlassField(text, { text = it }, "km", number = true, modifier = Modifier.weight(1f))
                        GlassPill("SAVE", accent) {
                            text.toIntOrNull()?.let { Cfg.odometerKm.intValue = it; Cfg.set("odometerKm", it) }
                        }
                    }
                }
                Card(Modifier.fillMaxWidth(), "FUEL") {
                    val raw = car.fuelRaw.value
                    T(car.fuelPct.value?.let { "Tank $it%" + (car.rangeKm.value?.let { r -> " · range $r km" } ?: "") } ?: "Waiting for car data", 28, Z.Bright, Condensed, FontWeight.Medium)
                    T("Just filled up? Tap once so 100% matches your full tank.", 18, Muted)
                    GlassPill("TANK IS FULL NOW", accent) {
                        if (raw != null && raw > 20) {
                            Cfg.fuelFullRaw.intValue = raw; Cfg.set("fuelFullRaw", raw)
                            car.fuelPct.value = 100
                        }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth(), "SPECIFICATIONS") {
            Specs.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                    pair.forEach { (k, v) -> Box(Modifier.weight(1f)) { SpecRow(k, v) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            T("US-market factory figures; GCC cars may differ slightly.", 17, Muted)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SpecRow(k: String, v: String) {
    Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        T(k.uppercase(), 15, Muted, spacing = 3f)
        T(v, 28, Z.Bright, Condensed, FontWeight.Medium)
    }
}

/** Oil-change countdown by distance and by time, whichever comes first. */
@Composable
private fun ServiceDue(accent: Color) {
    val odo = Cfg.odometerKm.intValue
    val atKm = Cfg.oilAtKm.intValue
    val atMs = Cfg.oilAtMs.value
    val everyKm = Cfg.oilEveryKm.intValue
    val everyMonths = Cfg.oilEveryMonths.intValue
    val known = atMs > 0L
    val kmLeft = if (known && odo > 0) everyKm - (odo - atKm) else null
    val daysLeft = if (known) (everyMonths * 30.4 - (System.currentTimeMillis() - atMs) / 86_400_000.0).toInt() else null
    val frac = when {
        !known -> 0f
        else -> maxOf(
            kmLeft?.let { 1f - it.toFloat() / everyKm } ?: 0f,
            daysLeft?.let { 1f - it / (everyMonths * 30.4f) } ?: 0f,
        ).coerceIn(0f, 1f)
    }
    val due = known && ((kmLeft != null && kmLeft <= 500) || (daysLeft != null && daysLeft <= 14))
    Card(Modifier.fillMaxWidth(), "NEXT OIL CHANGE") {
        if (!known) {
            T("Log your last oil change below to start the countdown.", 28, Z.Bright, Condensed, FontWeight.Medium)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(60.dp)) {
                Stat("DISTANCE LEFT", kmLeft?.let { if (it < 0) "OVER ${-it}" else "%,d".format(it) } ?: "—", if (kmLeft != null) "KM" else null, 80, if (due) Color(0xFFFF3B2F) else Z.Bright)
                Stat("TIME LEFT", daysLeft?.let { if (it < 0) "OVER ${-it}" else it.toString() } ?: "—", "DAYS", 80, if (due) Color(0xFFFF3B2F) else Z.Bright)
            }
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.14f))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(if (due) Color(0xFFFF3B2F) else accent))
        }
        T("INTERVAL", 15, Muted, spacing = 3f)
        Options(
            listOf(Triple(5000, "5,000 km", "Hard use"), Triple(7500, "7,500 km", null), Triple(10000, "10,000 km", "Nissan")),
            everyKm, accent, height = 110,
        ) { Cfg.oilEveryKm.intValue = it; Cfg.set("oilEveryKm", it) }
        Options(
            listOf(Triple(3, "3 months", null), Triple(6, "6 months", "UAE heat"), Triple(12, "12 months", null)),
            everyMonths, accent, height = 110,
        ) { Cfg.oilEveryMonths.intValue = it; Cfg.set("oilEveryMonths", it) }
    }
}

@Composable
private fun LogCard(accent: Color) {
    var kind by remember { mutableStateOf(ServiceKinds[0]) }
    var km by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth(), "SERVICE LOG") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ServiceKinds.forEach { k ->
                val short = when (k) { "Oil & filter" -> "OIL"; "Inspection" -> "CHECK"; else -> k.uppercase() }
                GlassPill(short, if (k == kind) accent else null, Modifier.weight(1f)) { kind = k }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassField(km, { km = it }, "Odometer km", number = true, modifier = Modifier.width(300.dp))
            GlassField(note, { note = it }, "Note (shop, parts…)", modifier = Modifier.weight(1f))
            GlassPill("ADD", accent) {
                val k = km.toIntOrNull() ?: Cfg.odometerKm.intValue
                Cfg.addService(ServiceEntry(System.currentTimeMillis(), k, kind, note.trim()))
                km = ""; note = ""
            }
        }
        val fmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH) }
        val log = Cfg.serviceLog.value
        if (log.isEmpty()) T("Nothing logged yet.", 22, Muted)
        log.take(8).forEach { e ->
            Row(
                Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.05f)).padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(accent))
                T(e.kind, 26, Z.Bright, Condensed, FontWeight.Medium, modifier = Modifier.width(210.dp))
                T(fmt.format(Date(e.atMs)), 20, Soft, modifier = Modifier.width(190.dp))
                T(if (e.km > 0) "%,d km".format(e.km) else "", 20, Soft, modifier = Modifier.width(150.dp))
                T(e.note, 20, Muted, modifier = Modifier.weight(1f))
                Box(Modifier.size(56.dp).clip(CircleShape).clickable { Cfg.removeService(e) }, contentAlignment = Alignment.Center) {
                    LineIcon("M6 6l12 12 M18 6L6 18", Muted, 24.dp, 2f)
                }
            }
        }
    }
}
