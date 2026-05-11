package com.hanif.textscanner.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OcrProcessor {

    /**
     * Process image from URI - main entry point
     * Uses Latin recognizer which supports Bengali script well via ML Kit
     */
    suspend fun processImage(context: Context, uri: Uri): String {
        val bitmap = loadAndPrepareBitmap(context, uri)
            ?: throw IllegalStateException("Could not load image")

        return try {
            // Try Latin recognizer first (covers Bengali well)
            val result = runOcr(bitmap)
            if (result.isNotBlank()) result
            else {
                // Try with enhanced bitmap
                val enhanced = enhanceBitmap(bitmap)
                runOcr(enhanced).also { enhanced.recycle() }
            }
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Process already-loaded bitmap (used for PDF pages)
     */
    suspend fun processBitmap(bitmap: Bitmap): String {
        val prepared = prepareBitmap(bitmap)
        return try {
            val result = runOcr(prepared)
            if (result.isNotBlank()) result
            else {
                val enhanced = enhanceBitmap(prepared)
                runOcr(enhanced).also { enhanced.recycle() }
            }
        } finally {
            if (prepared != bitmap) prepared.recycle()
        }
    }

    private suspend fun runOcr(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { result ->
                // Build text preserving line structure
                val sb = StringBuilder()
                for (block in result.textBlocks) {
                    for (line in block.lines) {
                        sb.append(line.text)
                        sb.append("\n")
                    }
                    sb.append("\n")
                }
                cont.resume(sb.toString().trim())
            }
            .addOnFailureListener { e ->
                cont.resumeWithException(e)
            }
    }

    private fun loadAndPrepareBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Calculate sample size to avoid OOM
            val maxSize = 2048
            var sampleSize = 1
            var w = options.outWidth
            var h = options.outHeight
            while (w > maxSize || h > maxSize) {
                sampleSize *= 2
                w /= 2
                h /= 2
            }

            val inputStream2 = context.contentResolver.openInputStream(uri) ?: return null
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = BitmapFactory.decodeStream(inputStream2, null, decodeOptions)
            inputStream2.close()

            raw?.let { prepareBitmap(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun prepareBitmap(bitmap: Bitmap): Bitmap {
        // Scale up small images for better OCR accuracy
        val minDim = 1200
        return if (bitmap.width < minDim || bitmap.height < minDim) {
            val scale = maxOf(minDim.toFloat() / bitmap.width, minDim.toFloat() / bitmap.height)
            val newW = (bitmap.width * scale).toInt()
            val newH = (bitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        } else bitmap
    }

    /**
     * Enhance bitmap for better OCR on low quality / handwritten text
     */
    private fun enhanceBitmap(bitmap: Bitmap): Bitmap {
        val enhanced = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(enhanced)
        val paint = Paint()

        // Increase contrast and convert to grayscale
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f) // grayscale
        val contrastMatrix = ColorMatrix(floatArrayOf(
            1.5f, 0f, 0f, 0f, -40f,
            0f, 1.5f, 0f, 0f, -40f,
            0f, 0f, 1.5f, 0f, -40f,
            0f, 0f, 0f, 1f, 0f
        ))
        colorMatrix.postConcat(contrastMatrix)
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return enhanced
    }
}
