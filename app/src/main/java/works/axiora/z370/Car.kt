package works.axiora.z370

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Live car state decoded from this unit's CAN box broadcasts (GT6 / choiceway firmware, 806G box, 2020 370Z).
 * Frames arrive as A5 2E <type> <len> <data…> <check>. Mapping confirmed with the probe on 1 Nov 2026:
 *   0x28 byte 0 : doors, bit 0x40 driver, 0x80 passenger, 0x08 boot
 *   0x29        : steering angle, signed 16-bit little-endian, tenths of a degree
 *   0x55        : climate, bytes 0/1 left/right set temp in half degrees, byte 2 fan
 *   0x6B byte 0 : fuel level %, bytes 4–5 range km (0x0FFF unknown)
 * Reverse comes as its own start/end broadcast; headlights only as the firmware's night switch (rw.out.dark).
 */
class CarState {
    val driverDoor = mutableStateOf(false)
    val passengerDoor = mutableStateOf(false)
    val boot = mutableStateOf(false)
    val btTitle = mutableStateOf<String?>(null)
    val btPlaying = mutableStateOf(false)
    val outsideTemp = mutableStateOf<String?>(null)
    val steeringDeg = mutableStateOf<Float?>(null)
    val cabinC = mutableStateOf<Float?>(null)   // no longer decoded: 0x6B byte 4 turned out to be range
    /** Fuel level, percent (0x6B byte 0). Rises slowly after ignition as the gauge settles, like the cluster. */
    val fuelPct = mutableStateOf<Int?>(null)
    /** Distance to empty, km (0x6B bytes 4–5, little-endian; 0x0FFF = not available yet). */
    val rangeKm = mutableStateOf<Int?>(null)
    val setTempL = mutableStateOf<Float?>(null)
    val setTempR = mutableStateOf<Float?>(null)
    /** Fan level, low nibble of 0x55 byte 2 (0x12 read as fan 2 on screen). Inferred from one session. */
    val fan = mutableStateOf<Int?>(null)
    /** btsuite HFP state: 3 connected idle, 4 outgoing, 5 incoming, 6 active call. */
    val callState = mutableStateOf(0)
    val callName = mutableStateOf<String?>(null)
    val callNumber = mutableStateOf<String?>(null)
    val reverse = mutableStateOf(false)
    val night = mutableStateOf(false)
    val live = mutableStateOf(false)

    fun onFrame(raw: ByteArray) {
        if (raw.size < 4) return
        val type = raw[2].toInt() and 0xFF
        val len = raw[3].toInt() and 0xFF
        if (raw.size < 4 + len) return
        fun b(i: Int) = raw[4 + i].toInt() and 0xFF
        live.value = true
        when (type) {
            0x28 -> if (len >= 1) {
                driverDoor.value = b(0) and 0x40 != 0
                passengerDoor.value = b(0) and 0x80 != 0
                boot.value = b(0) and 0x08 != 0
            }
            0x29 -> if (len >= 2) {
                val v = (b(1) shl 8 or b(0)).toShort().toInt()
                // The box reports right turns as positive; the app uses positive = left (checked in the car, 3 Nov).
                steeringDeg.value = -v / 10f
            }
            0x55 -> if (len >= 2) {
                setTempL.value = b(0) / 2f
                setTempR.value = b(1) / 2f
                if (len >= 3) fan.value = b(2) and 0x0F
            }
            // 0xFF (with byte 5 set) is the box's "no reading" marker: show nothing rather than 127.5°.
            // Drive of 3 Nov: bytes 4–5 counted 360 → 359 → 358 km while driving (range), and were 0x0FFF
            // before the trip computer had a figure. Byte 0 = 73 with that range fits fuel percent on a 72 L tank.
            0x6B -> if (len >= 6) {
                fuelPct.value = b(0).takeIf { it in 0..100 }
                val r = b(4) or (b(5) shl 8)
                rangeKm.value = r.takeIf { it in 1..1500 }
            }
        }
    }

    companion object {
        const val CAN_ALL = "com.choiceway.eventcenter.EventUtils.MCU_MSG_CAN_ALL_INFO"
        const val BACK_START = "com.choiceway.eventcenter.EventUtils.MCU_MSG_BACKCAR_START"
        const val BACK_END = "com.choiceway.eventcenter.EventUtils.MCU_MSG_BACKCAR_END"
        const val FRAME_KEY = "EventUtils.CAR_AIR_DATA"
        const val BT_AV = "com.szchoiceway.btsuite.HBCP_EVT_AV_STATUS"
        const val HSHF = "com.szchoiceway.btsuite.HBCP_EVT_HSHF_STATUS"
        const val CONTACT_NAME = "com.szchoiceway.btsuite.HBCP_EVT_CONTACT_NAME"
        const val CONTACT_NUM = "com.szchoiceway.btsuite.HBCP_EVT_CONTACT_NUM"
        const val OUT_TEMP = "com.choiceway.eventcenter.CanUtils.CAN_CAR_OUT_SIDE_TEMP_EVT"
        const val BT_INT = "com.szchoiceway.btsuite.DATA_INT"
        const val BT_STR = "com.szchoiceway.btsuite.DATA_STR"
    }
}

/** System property read in-process (no shell spawned); falls back to getprop if the API is hidden. */
private val sysPropGet: java.lang.reflect.Method? = try {
    Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
} catch (_: Throwable) { null }

fun readProp(key: String): String? {
    sysPropGet?.let { m -> try { return (m.invoke(null, key) as? String)?.ifEmpty { null } } catch (_: Throwable) {} }
    return readPropShell(key)
}

private fun readPropShell(key: String): String? = try {
    val p = Runtime.getRuntime().exec(arrayOf("getprop", key))
    val v = p.inputStream.bufferedReader().readText().trim()
    p.waitFor()
    v.ifEmpty { null }
} catch (_: Exception) {
    null
}

@Composable
fun rememberCarState(): CarState {
    val ctx = LocalContext.current
    val car = remember { CarState() }
    DisposableEffect(Unit) {
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                when (i?.action) {
                    CarState.CAN_ALL -> {
                        @Suppress("DEPRECATION")
                        (i.extras?.get(CarState.FRAME_KEY) as? ByteArray)?.let { car.onFrame(it) }
                    }
                    CarState.BACK_START -> car.reverse.value = true
                    CarState.BACK_END -> car.reverse.value = false
                    CarState.HSHF -> {
                        val st = i.getIntExtra(CarState.BT_INT, 0)
                        car.callState.value = st
                        if (st <= 3) { car.callName.value = null; car.callNumber.value = null }
                    }
                    CarState.CONTACT_NAME -> car.callName.value = i.getStringExtra(CarState.BT_STR)?.takeIf { it.isNotBlank() && !it.startsWith("Unknown", ignoreCase = true) }
                    CarState.CONTACT_NUM -> car.callNumber.value = i.getStringExtra(CarState.BT_STR)?.takeIf { it.isNotBlank() }
                    CarState.OUT_TEMP -> i.getStringExtra(CarState.OUT_TEMP + "_EXTRA_STR")?.let { if (it.isNotBlank()) car.outsideTemp.value = it }
                    CarState.BT_AV -> {
                        // btsuite: 4 = playing, 3 = paused (BTUtils HBCP_STATUS_AV_PLAYING / AV_PAUSE).
                        val st = i.getIntExtra(CarState.BT_INT, -1)
                        val name = i.getStringExtra(CarState.BT_STR)
                        if (!name.isNullOrBlank()) car.btTitle.value = name
                        car.btPlaying.value = st == 4
                    }
                }
            }
        }
        val f = IntentFilter().apply {
            addAction(CarState.CAN_ALL); addAction(CarState.BACK_START); addAction(CarState.BACK_END); addAction(CarState.BT_AV); addAction(CarState.OUT_TEMP); addAction(CarState.HSHF); addAction(CarState.CONTACT_NAME); addAction(CarState.CONTACT_NUM)
        }
        ContextCompat.registerReceiver(ctx, rx, f, ContextCompat.RECEIVER_EXPORTED)
        onDispose { try { ctx.unregisterReceiver(rx) } catch (_: Exception) {} }
    }
    // Headlights reach Android only as the firmware's night switch; poll it gently.
    LaunchedEffect(Unit) {
        while (true) {
            val dark = withContext(Dispatchers.IO) { readProp("rw.out.dark") }
            car.night.value = dark == "1"
            val back = withContext(Dispatchers.IO) { readProp("sys.backcar.state") }
            if (back != null) car.reverse.value = back == "1"
            delay(2000)
        }
    }
    return car
}
