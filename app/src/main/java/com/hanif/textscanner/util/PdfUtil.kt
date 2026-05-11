package com.hanif.textscanner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfUtil {

    suspend fun pdfToImages(context: Context, uri: Uri): List<Bitmap> = withContext(Dispatchers.IO) {
        val bitmaps = mutableListOf<Bitmap>()
        var pfd: ParcelFileDescriptor? = null
        val tempFile = File(context.cacheDir, "pdf_${System.currentTimeMillis()}.pdf")

        try {
            // Copy to temp file (required for PdfRenderer)
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { input.copyTo(it) }
            }

            pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)

                // Render at 2.5x scale — Tesseract needs higher res for Bengali
                val scale = 2.5f
                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()

                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmaps.add(bmp)
            }

            renderer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { pfd?.close() } catch (_: Exception) {}
            try { tempFile.delete() } catch (_: Exception) {}
        }

        bitmaps
    }
}
