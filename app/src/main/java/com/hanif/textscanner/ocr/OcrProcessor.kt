package com.hanif.textscanner.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream

object OcrProcessor {

    private const val TAG = "OcrProcessor"
    private const val TESS_DATA_DIR = "tessdata"
    // Bengali + English combined — best for Bangla documents
    private const val LANG = "ben+eng"

    /**
     * Initialize tessdata directory — copies ben.traineddata & eng.traineddata
     * from assets to internal storage on first run.
     */
    fun init(context: Context) {
        val tessDir = getTessDir(context)
        val dataDir = File(tessDir, TESS_DATA_DIR)
        dataDir.mkdirs()

        listOf("ben.traineddata", "eng.traineddata").forEach { fileName ->
            val dest = File(dataDir, fileName)
            if (!dest.exists() || dest.length() < 1000) {
                try {
                    context.assets.open("tessdata/$fileName").use { input ->
                        FileOutputStream(dest).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Copied $fileName (${dest.length()} bytes)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy $fileName: ${e.message}")
                }
            }
        }
    }

    private fun getTessDir(context: Context): String {
        return context.filesDir.absolutePath
    }

    /**
     * Process image from URI — main entry point
     */
    suspend fun processImage(context: Context, uri: Uri): String {
        val bitmap = loadAndPrepareBitmap(context, uri)
            ?: throw IllegalStateException("Could not load image")
        return try {
            processBitmap(context, bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Process a Bitmap directly (used for PDF pages)
     */
    suspend fun processBitmap(context: Context, bitmap: Bitmap): String {
        val prepared = prepareBitmap(bitmap)
        val result1 = runTesseract(context, prepared)
        if (prepared != bitmap) prepared.recycle()

        if (result1.isNotBlank() && result1.length > 10) {
            return result1
        }

        // Try enhanced version if result was poor
        val enhanced = enhanceBitmap(bitmap)
        val result2 = runTesseract(context, enhanced)
        enhanced.recycle()

        return if (result2.length > result1.length) result2 else result1
    }

    /**
     * Core Tesseract OCR call
     */
    private fun runTesseract(context: Context, bitmap: Bitmap): String {
        val tessDir = getTessDir(context)
        val api = TessBaseAPI()

        return try {
            val initialized = api.init(tessDir, LANG)
            if (!initialized) {
                Log.e(TAG, "Tesseract init failed — tessdata missing?")
                return ""
            }

            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            api.setVariable("load_system_dawg", "false")
            api.setVariable("load_freq_dawg", "false")
            api.setVariable("tessedit_do_invert", "false")

            api.setImage(bitmap)
            val text = api.utF8Text ?: ""
            api.clear()
            cleanText(text)
        } catch (e: Exception) {
            Log.e(TAG, "Tesseract error: ${e.message}")
            ""
        } finally {
            try { api.recycle() } catch (_: Exception) {}
        }
    }

    private fun cleanText(raw: String): String {
        return raw
            .lines()
            .map { line ->
                line
                    .replace(Regex("[|}{\\[\\]@#\\^~`]"), "")
                    .replace(Regex("\\s{3,}"), "  ")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .trim()
    }

    private fun loadAndPrepareBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val opts1 = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts1)
            }

            val maxPx = 3000
            var sample = 1
            var w = opts1.outWidth
            var h = opts1.outHeight
            while (w > maxPx || h > maxPx) {
                sample *= 2; w /= 2; h /= 2
            }

            val opts2 = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts2)
            } ?: return null

            prepareBitmap(raw)
        } catch (e: Exception) {
            Log.e(TAG, "loadBitmap error: ${e.message}")
            null
        }
    }

    private fun prepareBitmap(bitmap: Bitmap): Bitmap {
        val minDim = 1500
        val shorter = minOf(bitmap.width, bitmap.height)
        return if (shorter < minDim) {
            val scale = minDim.toFloat() / shorter
            val nw = (bitmap.width * scale).toInt()
            val nh = (bitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, nw, nh, true)
        } else bitmap
    }

    private fun enhanceBitmap(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        val contrast = ColorMatrix(floatArrayOf(
            2.0f, 0f,   0f,   0f, -80f,
            0f,   2.0f, 0f,   0f, -80f,
            0f,   0f,   2.0f, 0f, -80f,
            0f,   0f,   0f,   1f,   0f
        ))
        cm.postConcat(contrast)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }
}
