package com.nendo.argosy.ui.cocoon.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import java.io.File

/**
 * Arcoon's own demo theme, drawn in code (no borrowed artwork), so the theme system can be tried
 * before any real Cocoon theme is imported. It uses the same files a Cocoon theme ZIP would hold.
 */
internal object CocoonSampleTheme {
    const val ID = "arcoon-aurora"
    private const val VERSION = "1"

    fun ensureInstalled(root: File) {
        val dir = File(root, ID)
        val json = File(dir, "theme.json")
        if (json.isFile && json.readText().contains("\"version\": \"$VERSION\"")) return
        dir.deleteRecursively()
        File(dir, "wallpapers").mkdirs()
        json.writeText(
            """
            {
              "name": "Aurora (Sample)",
              "author": "Arcoon",
              "version": "$VERSION",
              "description": "A demo theme: violet and teal glow on both screens.",
              "theme_mode": "OLED",
              "wallpaper_main": "bottom",
              "wallpaper_external": "top",
              "color_scheme": {
                "background_gradient_start": "#FF06040F",
                "background_gradient_end": "#FF02080C",
                "card_gradient_start": "#FF171329",
                "card_gradient_end": "#FF1E1A36",
                "text_primary": "#FFF4F1FF",
                "text_secondary": "#FF9D96BF",
                "tile_background": "#FF141026",
                "accent_gradient_start": "#FF8B7CFF",
                "accent_gradient_end": "#FF3FE0C5",
                "selection_gradient_start": "#FFB4A8FF"
              }
            }
            """.trimIndent()
        )
        val top = draw(1920, 1080)
        save(top, File(dir, "wallpapers/top.png"))
        save(draw(1240, 1080), File(dir, "wallpapers/bottom.png"))
        save(Bitmap.createScaledBitmap(top, 640, 360, true), File(dir, "preview.png"))
    }

    private fun draw(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), 0xFF06040F.toInt(), 0xFF02080C.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        fun glow(cx: Float, cy: Float, r: Float, color: Int) {
            paint.shader = RadialGradient(cx, cy, r, color, 0x00000000, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, paint)
        }
        glow(width * 0.18f, height * 0.25f, height * 0.75f, 0x668B7CFF)
        glow(width * 0.82f, height * 0.78f, height * 0.70f, 0x553FE0C5)
        glow(width * 0.55f, height * 0.10f, height * 0.40f, 0x33FF7AD9)
        // a few soft ribbons across the middle
        paint.shader = null
        paint.style = Paint.Style.STROKE
        for (i in 0 until 4) {
            paint.strokeWidth = height * (0.010f + i * 0.004f)
            paint.color = if (i % 2 == 0) 0x228B7CFF else 0x1E3FE0C5
            val path = android.graphics.Path()
            val y = height * (0.42f + i * 0.07f)
            path.moveTo(-50f, y)
            path.cubicTo(width * 0.3f, y - height * 0.22f, width * 0.65f, y + height * 0.24f, width + 50f, y - height * 0.05f)
            canvas.drawPath(path, paint)
        }
        return bitmap
    }

    private fun save(bitmap: Bitmap, file: File) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
