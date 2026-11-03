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

/** Renders each page at the unit's 1080 × 1920 so the layout can be looked at without the car. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w540dp-h960dp-xhdpi")
class PageShots {
    @get:Rule val rule = createComposeRule()

    private fun shot(page: String, setup: () -> Unit = {}) {
        Cfg.load(ApplicationProvider.getApplicationContext())
        setup()
        Cfg.page.value = page
        val car = CarState()
        car.live.value = true
        car.steeringDeg.value = 12f
        car.setTempL.value = 22f; car.fan.value = 2
        rule.setContent {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalDensity provides Density(constraints.maxWidth / 1080f, 1f)) {
                    AppShell(car, Color(0xFFFF5A1F), false, emptyMap(), {}, {}, {}, {})
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/$page.png")
    }

    @Test fun home() = shot("home")
    @Test fun settings() = shot("settings")
    @Test fun drive() = shot("drive")
    @Test fun driveNumbers() = shot("drive") { Cfg.driveFace.value = "numbers" }.also { }
    @Test fun garage() = shot("garage")
    @Test fun media() = shot("media")
}
