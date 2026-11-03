package works.axiora.z370

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Frames for the walkthrough video: every page and the main options, with sample live data. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w540dp-h960dp-xhdpi")
class WalkShots {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, page: String, accent: Color = Color(0xFFFF5A1F), kmh: Int? = 0, setup: (CarState) -> Unit = {}) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Cfg.load(ctx); Perf.load(ctx)
        Cfg.page.value = page
        PreviewFix.value = Fix(kmh, 135f, 25.08, 55.14)
        Trip.km.floatValue = 18.4f; Trip.minutes.intValue = 27; Trip.avgKmh.intValue = 41; Trip.maxKmh.intValue = 126
        Perf.zeroTo100.last.value = 5.84f; Perf.zeroTo100.best.value = 5.61f
        Perf.eightyTo120.last.value = 3.42f; Perf.eightyTo120.best.value = 3.30f
        Perf.quarter.last.value = 14.12f; Perf.quarter.best.value = 13.95f; Perf.trapKmh.value = 162
        val car = CarState()
        car.live.value = true; car.steeringDeg.value = -14f
        car.setTempL.value = 22f; car.setTempR.value = 22f; car.fan.value = 2
        car.fuelRaw.value = 58; car.fuelPct.value = 75; car.rangeKm.value = 412
        car.btTitle.value = "Unique Moment"; car.btPlaying.value = true
        setup(car)
        rule.setContent {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalDensity provides Density(constraints.maxWidth / 1080f, 1f)) {
                    AppShell(car, accent, false, emptyMap(), {}, {}, {}, {})
                }
            }
        }
        // Let background loads (car renders, cover art, weather) land, and the car's ease-in finish.
        repeat(40) { Thread.sleep(100); rule.mainClock.advanceTimeBy(100); rule.waitForIdle() }
        rule.onRoot().captureRoboImage("build/walk/$name.png")
    }

    @Test fun a_home() = shot("home", "home")
    @Test fun b_homeFuelDoor() = shot("home_door", "home") { Cfg.leftTile.value = "fuel"; it.driverDoor.value = true }
    @Test fun c_homeBoot() = shot("home_boot", "home") { Cfg.leftTile.value = "steering"; it.boot.value = true; it.passengerDoor.value = true }
    @Test fun d_homeNight() = shot("home_night", "home") { Cfg.nightTheme.value = "red"; it.night.value = true }
    @Test fun e_ice() = shot("home_ice", "home", Color(0xFF4FB3FF))
    @Test fun f_mint() = shot("home_mint", "home", Color(0xFF3DDC97)) { Cfg.bg.value = "navy" }
    @Test fun g_violet() = shot("home_violet", "home", Color(0xFF9B7BFF)) { Cfg.bg.value = "black" }
    @Test fun h_driveSweep() = shot("drive_sweep", "drive", kmh = 87) { Cfg.driveFace.value = "sweep" }
    @Test fun i_driveNumbers() = shot("drive_numbers", "drive", kmh = 146) { Cfg.driveFace.value = "numbers"; Cfg.redlineKmh.intValue = 140 }
    @Test fun j_driveMinimal() = shot("drive_minimal", "drive", kmh = 62) { Cfg.driveFace.value = "minimal"; it.night.value = true }
    @Test fun k_media() = shot("media", "media")
    @Test @Config(qualifiers = "w540dp-h1500dp-xhdpi") fun l_garage() = shot("garage_tall", "garage") {
        Cfg.odometerKm.intValue = 50765; Cfg.oilAtKm.intValue = 46900; Cfg.oilAtMs.value = System.currentTimeMillis() - 95L * 86_400_000L
        Cfg.serviceLog.value = listOf(
            ServiceEntry(System.currentTimeMillis() - 95L * 86_400_000L, 46900, "Oil & filter", "Motul 5W-40, OEM filter"),
            ServiceEntry(System.currentTimeMillis() - 200L * 86_400_000L, 41200, "Tyres", "Potenza S007, rear pair"),
        )
        Cfg.checks.value = setOf("tyres", "oil", "lights")
    }
    @Test @Config(qualifiers = "w540dp-h2300dp-xhdpi") fun m_settings() = shot("settings_tall", "settings")
}
