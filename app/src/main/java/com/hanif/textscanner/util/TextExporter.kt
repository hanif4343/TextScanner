package com.hanif.textscanner.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object TextExporter {

    fun exportToFile(context: Context, text: String): Uri? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "TextScan_$timestamp.txt"

            // Try Downloads first
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = if (downloadsDir.exists() || downloadsDir.mkdirs()) {
                File(downloadsDir, fileName)
            } else {
                File(context.filesDir, fileName)
            }

            file.writeText(text, Charsets.UTF_8)

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }
}
