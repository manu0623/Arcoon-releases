package com.nendo.argosy.ui.cocoon

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.nendo.argosy.data.platform.PlatformDefinitions

/** The silhouette family a platform's folder glyph is drawn from. */
enum class ConsoleShape { CLAMSHELL, HANDHELD_WIDE, HANDHELD_TALL, HYBRID, ARCADE, CONTROLLER }

fun consoleShapeFor(slug: String?): ConsoleShape {
    val canonical = slug?.let { PlatformDefinitions.getCanonicalSlug(it) }?.lowercase() ?: return ConsoleShape.CONTROLLER
    return when {
        canonical in setOf("nds", "3ds", "n3ds", "dsi", "new-nintendo-3ds") -> ConsoleShape.CLAMSHELL
        canonical in setOf("gb", "gbc", "pokemini", "pokemon-mini") -> ConsoleShape.HANDHELD_TALL
        canonical in setOf(
            "gba", "psp", "psvita", "vita", "gamegear", "lynx", "ngp", "ngpc", "wonderswan",
            "wonderswancolor", "wsc", "ws", "neo-geo-pocket", "neo-geo-pocket-color"
        ) -> ConsoleShape.HANDHELD_WIDE
        canonical.startsWith("switch") -> ConsoleShape.HYBRID
        canonical in setOf(
            "arcade", "mame", "fbneo", "cps1", "cps2", "cps3", "naomi", "atomiswave", "neogeoaes",
            "neogeomvs", "neogeo", "daphne", "model2", "model3"
        ) -> ConsoleShape.ARCADE
        else -> ConsoleShape.CONTROLLER
    }
}

/** White line-art of a console family, sized to fill its box. */
@Composable
fun ConsoleGlyph(shape: ConsoleShape, modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension * 0.095f, cap = StrokeCap.Round)
        when (shape) {
            ConsoleShape.CLAMSHELL -> clamshell(color, stroke)
            ConsoleShape.HANDHELD_WIDE -> handheldWide(color, stroke)
            ConsoleShape.HANDHELD_TALL -> handheldTall(color, stroke)
            ConsoleShape.HYBRID -> hybrid(color, stroke)
            ConsoleShape.ARCADE -> arcade(color, stroke)
            ConsoleShape.CONTROLLER -> controller(color, stroke)
        }
    }
}

private fun DrawScope.box(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float, stroke: Stroke? = null) {
    val s = size.minDimension
    if (stroke != null) {
        drawRoundRect(color, Offset(x * s, y * s), Size(w * s, h * s), CornerRadius(r * s), style = stroke)
    } else {
        drawRoundRect(color, Offset(x * s, y * s), Size(w * s, h * s), CornerRadius(r * s))
    }
}

private fun DrawScope.dot(color: Color, x: Float, y: Float, r: Float) {
    val s = size.minDimension
    drawCircle(color, r * s, Offset(x * s, y * s))
}

private fun DrawScope.plus(color: Color, cx: Float, cy: Float, arm: Float) {
    box(color, cx - arm, cy - arm * 0.32f, arm * 2, arm * 0.64f, arm * 0.2f)
    box(color, cx - arm * 0.32f, cy - arm, arm * 0.64f, arm * 2, arm * 0.2f)
}

private fun DrawScope.clamshell(color: Color, stroke: Stroke) {
    box(color, 0.16f, 0.04f, 0.68f, 0.44f, 0.08f, stroke)
    box(color, 0.29f, 0.12f, 0.42f, 0.28f, 0.03f)
    box(color, 0.16f, 0.52f, 0.68f, 0.44f, 0.08f, stroke)
    box(color, 0.36f, 0.6f, 0.28f, 0.22f, 0.03f)
    plus(color, 0.25f, 0.72f, 0.055f)
    dot(color, 0.75f, 0.68f, 0.03f)
    dot(color, 0.75f, 0.78f, 0.03f)
}

private fun DrawScope.handheldWide(color: Color, stroke: Stroke) {
    box(color, 0.02f, 0.26f, 0.96f, 0.48f, 0.2f, stroke)
    box(color, 0.3f, 0.34f, 0.4f, 0.32f, 0.04f)
    plus(color, 0.16f, 0.5f, 0.07f)
    dot(color, 0.8f, 0.45f, 0.04f)
    dot(color, 0.88f, 0.55f, 0.04f)
}

private fun DrawScope.handheldTall(color: Color, stroke: Stroke) {
    box(color, 0.2f, 0.02f, 0.6f, 0.96f, 0.08f, stroke)
    box(color, 0.3f, 0.12f, 0.4f, 0.34f, 0.03f)
    plus(color, 0.38f, 0.66f, 0.07f)
    dot(color, 0.62f, 0.63f, 0.035f)
    dot(color, 0.7f, 0.57f, 0.035f)
}

private fun DrawScope.hybrid(color: Color, stroke: Stroke) {
    box(color, 0.02f, 0.24f, 0.18f, 0.52f, 0.09f, stroke)
    box(color, 0.8f, 0.24f, 0.18f, 0.52f, 0.09f, stroke)
    box(color, 0.24f, 0.24f, 0.52f, 0.52f, 0.04f, stroke)
    dot(color, 0.11f, 0.38f, 0.035f)
    dot(color, 0.89f, 0.58f, 0.035f)
}

private fun DrawScope.arcade(color: Color, stroke: Stroke) {
    val s = size.minDimension
    box(color, 0.12f, 0.56f, 0.76f, 0.3f, 0.08f, stroke)
    drawLine(color, Offset(0.38f * s, 0.56f * s), Offset(0.38f * s, 0.3f * s), strokeWidth = stroke.width, cap = StrokeCap.Round)
    dot(color, 0.38f, 0.24f, 0.09f)
    dot(color, 0.62f, 0.7f, 0.045f)
    dot(color, 0.76f, 0.7f, 0.045f)
}

private fun DrawScope.controller(color: Color, stroke: Stroke) {
    box(color, 0.04f, 0.28f, 0.92f, 0.46f, 0.23f, stroke)
    plus(color, 0.28f, 0.51f, 0.075f)
    dot(color, 0.7f, 0.45f, 0.04f)
    dot(color, 0.8f, 0.55f, 0.04f)
}
