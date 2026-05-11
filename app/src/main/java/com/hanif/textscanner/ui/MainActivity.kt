package com.hanif.textscanner.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.hanif.textscanner.databinding.ActivityMainBinding
import com.hanif.textscanner.ocr.OcrProcessor
import com.hanif.textscanner.ui.ResultActivity
import com.hanif.textscanner.ui.HistoryActivity
import com.hanif.textscanner.util.PdfUtil
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            pendingAction?.invoke()
            pendingAction = null
        } else {
            Toast.makeText(this, "Permission required to scan", Toast.LENGTH_SHORT).show()
        }
    }

    // Camera result
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>("captured_image_uri")
            uri?.let { processImageUri(it) }
        }
    }

    // Image picker
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            if (uris.size == 1) {
                processImageUri(uris[0])
            } else {
                processMultipleImages(uris)
            }
        }
    }

    // PDF picker
    private val pdfPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { processPdfUri(it) }
    }

    private var pendingAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        // Camera button
        binding.btnCamera.setOnClickListener {
            checkPermissionsAndRun(needCamera = true) {
                openCamera()
            }
        }

        // Gallery button
        binding.btnGallery.setOnClickListener {
            checkPermissionsAndRun(needCamera = false) {
                imagePickerLauncher.launch("image/*")
            }
        }

        // PDF button
        binding.btnPdf.setOnClickListener {
            checkPermissionsAndRun(needCamera = false) {
                pdfPickerLauncher.launch("application/pdf")
            }
        }

        // History button
        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    private fun checkPermissionsAndRun(needCamera: Boolean, action: () -> Unit) {
        val perms = mutableListOf<String>()
        if (needCamera && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (perms.isEmpty()) {
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    private fun openCamera() {
        val intent = Intent(this, CameraActivity::class.java)
        cameraLauncher.launch(intent)
    }

    private fun processImageUri(uri: Uri) {
        showLoading(true)
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    OcrProcessor.processImage(this@MainActivity, uri)
                }
                showLoading(false)
                openResult(text, uri.toString(), isMultiPage = false)
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@MainActivity, "OCR failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun processMultipleImages(uris: List<Uri>) {
        showLoading(true, "Processing ${uris.size} images...")
        scope.launch {
            try {
                val allText = StringBuilder()
                uris.forEachIndexed { index, uri ->
                    withContext(Dispatchers.Main) {
                        binding.tvLoadingStatus.text = "Processing image ${index + 1}/${uris.size}..."
                    }
                    val text = withContext(Dispatchers.IO) {
                        OcrProcessor.processImage(this@MainActivity, uri)
                    }
                    if (text.isNotBlank()) {
                        allText.append("--- Page ${index + 1} ---\n")
                        allText.append(text)
                        allText.append("\n\n")
                    }
                }
                showLoading(false)
                openResult(allText.toString(), uris[0].toString(), isMultiPage = true)
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@MainActivity, "OCR failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun processPdfUri(uri: Uri) {
        showLoading(true, "Reading PDF...")
        scope.launch {
            try {
                val pages = withContext(Dispatchers.IO) {
                    PdfUtil.pdfToImages(this@MainActivity, uri)
                }
                if (pages.isEmpty()) {
                    showLoading(false)
                    Toast.makeText(this@MainActivity, "Could not read PDF pages", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val allText = StringBuilder()
                pages.forEachIndexed { index, bitmap ->
                    withContext(Dispatchers.Main) {
                        binding.tvLoadingStatus.text = "Scanning page ${index + 1}/${pages.size}..."
                    }
                    val text = withContext(Dispatchers.IO) {
                        OcrProcessor.processBitmap(bitmap)
                    }
                    if (text.isNotBlank()) {
                        allText.append("=== পৃষ্ঠা ${index + 1} ===\n")
                        allText.append(text)
                        allText.append("\n\n")
                    }
                    bitmap.recycle()
                }
                showLoading(false)
                openResult(allText.toString(), uri.toString(), isMultiPage = pages.size > 1)
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@MainActivity, "PDF processing failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openResult(text: String, sourceUri: String, isMultiPage: Boolean) {
        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtra(ResultActivity.EXTRA_TEXT, text)
            putExtra(ResultActivity.EXTRA_SOURCE_URI, sourceUri)
            putExtra(ResultActivity.EXTRA_IS_MULTI_PAGE, isMultiPage)
        }
        startActivity(intent)
    }

    private fun showLoading(show: Boolean, message: String = "Scanning...") {
        binding.loadingLayout.visibility = if (show) View.VISIBLE else View.GONE
        binding.tvLoadingStatus.text = message
        binding.mainContent.visibility = if (show) View.GONE else View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
