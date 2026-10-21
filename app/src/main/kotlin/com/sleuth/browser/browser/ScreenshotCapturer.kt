package com.sleuth.browser.browser

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View

/**
 * Grabs what is actually on screen. PixelCopy is used when the platform
 * allows it, otherwise the view is rendered into a bitmap by hand.
 */
object ScreenshotCapturer {

    fun capture(activity: Activity?, view: View, callback: (Bitmap?) -> Unit) {
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) {
            callback(null)
            return
        }
        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (error: Throwable) {
            callback(null)
            return
        }

        if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val location = IntArray(2)
            view.getLocationInWindow(location)
            val rect = Rect(location[0], location[1], location[0] + width, location[1] + height)
            try {
                PixelCopy.request(
                    activity.window,
                    rect,
                    bitmap,
                    { result ->
                        if (result == PixelCopy.SUCCESS) callback(bitmap) else callback(null)
                    },
                    Handler(Looper.getMainLooper())
                )
                return
            } catch (error: Throwable) {
                // fall through to the software renderer
            }
        }

        captureByDrawing(view, bitmap, callback)
    }

    fun captureByDrawing(view: View, bitmap: Bitmap, callback: (Bitmap?) -> Unit) {
        val previous = view.layerType
        try {
            view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            callback(bitmap)
        } catch (error: Throwable) {
            callback(null)
        } finally {
            // the view must never be left drawing itself by hand
            try {
                view.setLayerType(previous, null)
            } catch (error: Throwable) {
                // nothing left to do
            }
        }
    }

    /** True when the picture is one flat colour, which means the page hid itself. */
    fun looksBlank(bitmap: Bitmap): Boolean {
        return try {
            val samples = ArrayList<Int>(32)
            val stepX = (bitmap.width / 6).coerceAtLeast(1)
            val stepY = (bitmap.height / 4).coerceAtLeast(1)
            var y = 0
            while (y < bitmap.height) {
                var x = 0
                while (x < bitmap.width) {
                    samples.add(bitmap.getPixel(x, y))
                    x += stepX
                }
                y += stepY
            }
            CaptureQuality.isUniform(samples)
        } catch (error: Throwable) {
            false
        }
    }
}
