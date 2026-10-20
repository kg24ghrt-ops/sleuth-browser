package com.sleuth.browser.browser

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object MediaSaver {

    fun saveToGallery(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val name = if (displayName.endsWith(".png")) displayName else "$displayName.png"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Sleuth")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            uri
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, name)
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
            Uri.fromFile(file)
        }
    }

    fun decodeSampled(path: String, maxWidth: Int): android.graphics.Bitmap? {
        return try {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxWidth * 2) sample *= 2
            val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            android.graphics.BitmapFactory.decodeFile(path, options)
        } catch (error: Throwable) {
            null
        }
    }

    fun writeBitmap(file: File, bitmap: Bitmap): Boolean {        return try {
            file.parentFile?.let { if (!it.exists()) it.mkdirs() }
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            true
        } catch (error: Throwable) {
            false
        }
    }

    fun writeText(file: File, text: String): Boolean {
        return try {
            file.parentFile?.let { if (!it.exists()) it.mkdirs() }
            file.writeText(text)
            true
        } catch (error: Throwable) {
            false
        }
    }

    fun shareFile(context: Context, file: File, mimeType: String): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(intent, null)
    }
}
