package app.toque.ui

import android.graphics.Bitmap
import android.graphics.Color
import io.nayuki.qrcodegen.QrCode

/** QR code noir sur blanc, avec sa marge, à la taille [px] (carré). */
object Qr {
    fun bitmap(text: String, px: Int): Bitmap? = try {
        val qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM)
        val border = 2
        val cells = qr.size + 2 * border
        val scale = maxOf(1, px / cells)
        val side = cells * scale
        val pixels = IntArray(side * side)
        for (y in 0 until side) for (x in 0 until side) {
            val dark = qr.getModule(x / scale - border, y / scale - border)
            pixels[y * side + x] = if (dark) Color.BLACK else Color.WHITE
        }
        Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
    } catch (_: Exception) {
        null
    }
}
