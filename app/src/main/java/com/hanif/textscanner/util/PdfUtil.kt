package com.hanif.textscanner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PdfUtil {

    /**
     * Convert PDF pages to Bitmaps for OCR processing.
     * Uses Android's built-in PdfRenderer (API 21+).
     * Renders at 300 DPI equivalent for best OCR accuracy.
     */
    suspend fun pdfToImages(context: Context, uri: Uri): List<Bitmap> = withContext(Dispatchers.IO) {
        val bitmaps = mutableListOf<Bitmap>()
        var parcelFileDescriptor: ParcelFileDescriptor? = null

        try {
            // Copy to temp file if needed (content:// URIs need this)
            val tempFile = java.io.File(context.cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            parcelFileDescriptor = ParcelFileDescriptor.open(
                tempFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            )

            val renderer = PdfRenderer(parcelFileDescriptor)
            val pageCount = renderer.pageCount

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)

                // Render at ~2x for better OCR quality
                val scale = 2.0f
                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                // White background
                bitmap.eraseColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                bitmaps.add(bitmap)
            }

            renderer.close()
            tempFile.delete()

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { parcelFileDescriptor?.close() } catch (_: Exception) {}
        }

        bitmaps
    }
}
