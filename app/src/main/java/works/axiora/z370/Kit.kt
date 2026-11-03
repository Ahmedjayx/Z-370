package works.axiora.z370

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared building blocks for the pages: glass cards, section titles, option cards, chips. */

val Soft = Color(0xFFDDE1E6)
val Muted = Color(0xFFA4A9B0)

@Composable
fun Section(title: String, sub: String? = null) {
    Column(Modifier.padding(top = 18.dp, bottom = 4.dp, start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        T(title, 18, Soft, spacing = 4f, weight = FontWeight.Medium)
        if (sub != null) T(sub, 22, Muted)
    }
}

/** A glass panel with an optional label. */
@Composable
fun Card(modifier: Modifier = Modifier, label: String? = null, pad: Int = 32, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.glass(RoundedCornerShape(44.dp)).padding(pad.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (label != null) T(label, 18, Soft, spacing = 4f)
        content()
    }
}

/**
 * A choice among siblings: a glass card that lights up in the accent when chosen.
 * `preview` draws a small picture of the option above its name.
 */
@Composable
fun RowScope.OptionCard(
    title: String,
    selected: Boolean,
    accent: Color,
    sub: String? = null,
    height: Int = 150,
    preview: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(36.dp)
    Column(
        Modifier.weight(1f).height(height.dp).glass(shape, if (selected) 1.7f else 1f)
            .then(if (selected) Modifier.border(3.dp, accent, shape) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = if (preview != null) Arrangement.SpaceBetween else Arrangement.Center,
    ) {
        if (preview != null) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { preview() }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(18.dp).clip(CircleShape)
                    .border(2.dp, if (selected) accent else Color.White.copy(alpha = 0.45f), CircleShape)
                    .padding(4.dp).clip(CircleShape).background(if (selected) accent else Color.Transparent),
            )
            Column {
                T(title, 26, Z.Bright, Condensed, FontWeight.Medium)
                if (sub != null) T(sub, 17, Muted)
            }
        }
    }
}

/** One row of option cards for a setting. */
@Composable
fun <V> Options(
    choices: List<Triple<V, String, String?>>,
    current: V,
    accent: Color,
    height: Int = 130,
    preview: (@Composable (V) -> Unit)? = null,
    onPick: (V) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        choices.forEach { (v, title, sub) ->
            OptionCard(title, v == current, accent, sub, height, preview?.let { p -> { p(v) } }) { onPick(v) }
        }
    }
}

/** A glass pill button. */
@Composable
fun GlassPill(text: String, accent: Color? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(84.dp).glass(RoundedCornerShape(42.dp), if (accent != null) 1.6f else 1f)
            .then(if (accent != null) Modifier.border(2.dp, accent, RoundedCornerShape(42.dp)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 34.dp),
        contentAlignment = Alignment.Center,
    ) { T(text, 20, Z.Bright, spacing = 2f, weight = FontWeight.Medium) }
}

/** Big label/value pair used across Drive and Garage. */
@Composable
fun Stat(label: String, value: String, unit: String? = null, size: Int = 64, color: Color = Z.Bright) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        T(label, 16, Muted, spacing = 3f)
        Row(verticalAlignment = Alignment.Bottom) {
            T(value, size, color, Condensed, FontWeight.Light)
            if (unit != null) T(" $unit", 18, Muted, spacing = 2f, modifier = Modifier.padding(bottom = (size / 6).dp))
        }
    }
}

/** A glass text field (names, odometer, notes). */
@Composable
fun GlassField(value: String, onChange: (String) -> Unit, placeholder: String, number: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier.height(84.dp).clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.08f))
            .border(1.5.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(28.dp)).padding(horizontal = 28.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) T(placeholder, 28, Color.White.copy(alpha = 0.35f))
        BasicTextField(
            value = value,
            onValueChange = { onChange(if (number) it.filter(Char::isDigit).take(7) else it.take(40)) },
            singleLine = true,
            textStyle = TextStyle(color = Z.Bright, fontSize = 28.sp, fontFamily = Barlow),
            cursorBrush = SolidColor(Z.Bright),
            keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
