package com.adastra.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.adastra.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Graphite page, indigo ink for "done", one amber accent for the streak. */
object Astra {
    val Bg = Color(0xFF171A21)
    val Surface = Color(0xFF1F232C)
    val Line = Color(0xFF2E3440)
    val Empty = Color(0xFF242934)
    val Text = Color(0xFFE8EAED)
    val Muted = Color(0xFF8B93A1)
    val Ink = Color(0xFF6C79F0)
    val Signal = Color(0xFFD9A441)
    val Rest = Color(0xFF4F6B87)
}

/** Kalam: a handwriting face, so the app reads like the paper notebook. */
val Hand = FontFamily(
    Font(R.font.kalam_regular, FontWeight.Normal),
    Font(R.font.kalam_bold, FontWeight.Bold),
)
val Serif = Hand
val Numbers = TextStyle(fontFeatureSettings = "tnum")

private val handType = Typography().run {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = Hand),
        displayMedium = displayMedium.copy(fontFamily = Hand),
        displaySmall = displaySmall.copy(fontFamily = Hand),
        headlineLarge = headlineLarge.copy(fontFamily = Hand),
        headlineMedium = headlineMedium.copy(fontFamily = Hand),
        headlineSmall = headlineSmall.copy(fontFamily = Hand),
        titleLarge = titleLarge.copy(fontFamily = Hand),
        titleMedium = titleMedium.copy(fontFamily = Hand),
        titleSmall = titleSmall.copy(fontFamily = Hand),
        bodyLarge = bodyLarge.copy(fontFamily = Hand),
        bodyMedium = bodyMedium.copy(fontFamily = Hand),
        bodySmall = bodySmall.copy(fontFamily = Hand),
        labelLarge = labelLarge.copy(fontFamily = Hand),
        labelMedium = labelMedium.copy(fontFamily = Hand),
        labelSmall = labelSmall.copy(fontFamily = Hand),
    )
}

private val scheme = darkColorScheme(
    primary = Astra.Ink, onPrimary = Color.White,
    secondary = Astra.Signal, onSecondary = Astra.Bg,
    background = Astra.Bg, onBackground = Astra.Text,
    surface = Astra.Surface, onSurface = Astra.Text,
    surfaceVariant = Astra.Surface, onSurfaceVariant = Astra.Muted,
    surfaceContainer = Astra.Surface, surfaceContainerHigh = Astra.Surface,
    outline = Astra.Line,
)

@Composable
fun AstraTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = scheme, typography = handType, content = content)

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Astra.Ink, unfocusedBorderColor = Astra.Line, cursorColor = Astra.Ink,
)

@Composable
fun ScreenTitle(title: String, subtitle: String?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Astra.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = Astra.Muted, fontSize = 14.sp)
        }
        actions()
    }
}

/** A titled block separated by a hairline, not a card. */
@Composable
fun Section(
    title: String,
    trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        HorizontalDivider(color = Astra.Line)
        Row(Modifier.padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Astra.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (trailing != null) Text(trailing, color = Astra.Muted, fontSize = 14.sp, style = Numbers)
        }
        content()
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) =
    Text(text, color = Astra.Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = modifier)

/** The notebook's filled square. */
@Composable
fun CheckSquare(on: Boolean, size: Dp = 22.dp, fill: Color = Astra.Ink) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .then(if (on) Modifier.background(fill) else Modifier.border(1.5.dp, Astra.Line, shape)),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(Icons.Filled.Check, null, Modifier.size(size * 0.72f), tint = Color.White)
    }
}

/** A plain line of review text with a small ink square as its marker. */
@Composable
fun Point(text: String, marker: Color = Astra.Ink) {
    Row(Modifier.padding(vertical = 5.dp)) {
        Box(Modifier.padding(top = 7.dp).size(6.dp).background(marker))
        Spacer(Modifier.width(12.dp))
        Text(text, color = Astra.Text, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

/** Lays text out sideways (bottom-to-top), like the column headings in the notebook. */
fun Modifier.sideways(): Modifier = this.layout { measurable, constraints ->
    val p = measurable.measure(
        Constraints(maxWidth = if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity)
    )
    layout(p.height, p.width) {
        p.placeWithLayer(x = (p.height - p.width) / 2, y = (p.width - p.height) / 2) { rotationZ = -90f }
    }
}
