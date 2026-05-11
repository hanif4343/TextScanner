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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.bengali.BengaliTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OcrProcessor {

    private const val TAG = "OcrProcessor"

    // No init needed — ML Kit models are bundled in APK
    fun init(context: Context) { /* no-op */ }

    /**
     * Process image from URI
     * Tries Bengali first, then Latin, keeps whichever has more text
     */
    suspend fun processImage(context: Context, uri: Uri): String {
        val bitmap = loadBitmap(context, uri)
            ?: throw IllegalStateException("Could not load image")
        return try {
            processBitmap(context, bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Process bitmap — used for PDF pages
     */
    suspend fun processBitmap(context: Context, bitmap: Bitmap): String {
        val prepared = ensureMinSize(bitmap)

        // Run Bengali and Latin recognizers in parallel approach
        val bengaliResult = runRecognizer(prepared, isBengali = true)
        val latinResult   = runRecognizer(prepared, isBengali = false)

        if (prepared != bitmap) prepared.recycle()

        // Pick whichever gave more content
        val best = if (bengaliResult.length >= latinResult.length) bengaliResult else latinResult

        // If still poor, try with enhanced contrast image
        if (best.length < 20) {
            val enhanced = enhance(bitmap)
            val bengaliEnh = runRecognizer(enhanced, isBengali = true)
            val latinEnh   = runRecognizer(enhanced, isBengali = false)
            enhanced.recycle()
            val bestEnh = if (bengaliEnh.length >= latinEnh.length) bengaliEnh else latinEnh
            return if (bestEnh.length > best.length) bestEnh else best
        }

        return best
    }

    private suspend fun runRecognizer(bitmap: Bitmap, isBengali: Boolean): String =
        suspendCancellableCoroutine { cont ->
            val recognizer = if (isBengali) {
                TextRecognition.getClient(BengaliTextRecognizerOptions.Builder().build())
            } else {
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            }

            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val sb = StringBuilder()
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            sb.append(line.text).append("\n")
                        }
                        sb.append("\n")
                    }
                    cont.resume(sb.toString().trim())
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "ML Kit error (bengali=$isBengali): ${e.message}")
                    cont.resume("") // don't crash — return empty
                }
        }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            // Pass 1 — get dimensions
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            // Downsample to max 3000px to avoid OOM
            var sample = 1
            var w = opts.outWidth; var h = opts.outHeight
            while (w > 3000 || h > 3000) { sample *= 2; w /= 2; h /= 2 }

            // Pass 2 — decode
            val opts2 = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts2)
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadBitmap: ${e.message}")
            null
        }
    }

    /** Scale up small images — ML Kit needs at least ~720px for good accuracy */
    private fun ensureMinSize(bmp: Bitmap): Bitmap {
        val minPx = 1080
        val shorter = minOf(bmp.width, bmp.height)
        if (shorter >= minPx) return bmp
        val scale = minPx.toFloat() / shorter
        return Bitmap.createScaledBitmap(bmp,
            (bmp.width * scale).toInt(),
            (bmp.height * scale).toInt(), true)
    }

    /** High-contrast grayscale — helps on faded/low-quality scans */
    private fun enhance(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = ColorMatrix()
        cm.setSaturation(0f) // grayscale
        val contrast = ColorMatrix(floatArrayOf(
            1.8f, 0f,   0f,   0f, -60f,
            0f,   1.8f, 0f,   0f, -60f,
            0f,   0f,   1.8f, 0f, -60f,
            0f,   0f,   0f,   1f,   0f
        ))
        cm.postConcat(contrast)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }
}
