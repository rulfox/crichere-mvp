package com.crichere.app.parity

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * The iOS gallery shows its sample photo wherever a board has a photo, logo or payment screenshot (as the
 * picked local image). Android boards load those from a URL, so they point here: the same green-to-gold
 * gradient, written once to the app's cache and loaded by Coil like any other image.
 */
object SamplePhoto {
    val url: String by lazy {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "parity-sample.png")
        if (!file.exists()) {
            val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawRect(0f, 0f, 1200f, 900f, Paint().apply {
                shader = LinearGradient(0f, 0f, 1200f, 900f, 0xFF1B5E20.toInt(), 0xFFF2B544.toInt(), Shader.TileMode.CLAMP)
            })
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        "file://${file.absolutePath}"
    }
}
