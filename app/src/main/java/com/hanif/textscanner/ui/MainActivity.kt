package com.hanif.textscanner.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.hanif.textscanner.data.SessionManager
import com.hanif.textscanner.databinding.ActivityMainBinding
import com.hanif.textscanner.ocr.OcrProcessor
import com.hanif.textscanner.util.PdfUtil
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) { pendingAction?.invoke(); pendingAction = null }
        else Toast.makeText(this, "Permission required", Toast.LENGTH_SHORT).show()
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>("captured_image_uri")
            uri?.let { processImageUri(it) }
        }
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            if (uris.size == 1) processImageUri(uris[0]) else processMultipleImages(uris)
        }
    }

    private val pdfPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { processPdfUri(it) } }

    private var pendingAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        scope.launch(Dispatchers.IO) {
            OcrProcessor.init(applicationContext)
        }

        setupUI()
    }

    override fun onResume() {
        super.onResume()
        checkResumeSessions()
    }

    private fun checkResumeSessions() {
        val inProgress = SessionManager.getInProgressSessions(this)
        if (inProgress.isNotEmpty()) {
            binding.btnResume.visibility = View.VISIBLE
            binding.btnResume.text = "▶ Resume (${inProgress.size} incomplete)"
        } else {
            binding.btnResume.visibility = View.GONE
        }
    }

    private fun setupUI() {
        binding.btnCamera.setOnClickListener {
            checkPermissionsAndRun(needCamera = true) {
                cameraLauncher.launch(Intent(this, CameraActivity::class.java))
            }
        }
        binding.btnGallery.setOnClickListener {
            checkPermissionsAndRun(needCamera = false) { imagePickerLauncher.launch("image/*") }
        }
        binding.btnPdf.setOnClickListener {
            checkPermissionsAndRun(needCamera = false) { pdfPickerLauncher.launch("application/pdf") }
        }
        binding.btnBatch.setOnClickListener {
            checkPermissionsAndRun(needCamera = true) {
                startActivity(Intent(this, BatchScanActivity::class.java))
            }
        }
        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.btnResume.setOnClickListener {
            showResumeDialog()
        }
    }

    private fun showResumeDialog() {
        val sessions = SessionManager.getInProgressSessions(this)
        if (sessions.isEmpty()) { binding.btnResume.visibility = View.GONE; return }

        val names = sessions.map { s ->
            "${s.title} — ${s.completedPages}/${s.totalExpected.takeIf { it > 0 } ?: "?"} পৃষ্ঠা"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Resume করুন")
            .setItems(names) { _, which ->
                val session = sessions[which]
                startActivity(Intent(this, BatchScanActivity::class.java).apply {
                    putExtra(BatchScanActivity.EXTRA_RESUME_SESSION_ID, session.id)
                })
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun checkPermissionsAndRun(needCamera: Boolean, action: () -> Unit) {
        val perms = mutableListOf<String>()
        if (needCamera && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (perms.isEmpty()) action()
        else { pendingAction = action; permissionLauncher.launch(perms.toTypedArray()) }
    }

    private fun processImageUri(uri: Uri) {
        showLoading(true, "স্ক্যান হচ্ছে...")
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    OcrProcessor.processImage(applicationContext, uri)
                }
                showLoading(false)
                startActivity(Intent(this@MainActivity, ResultActivity::class.java).apply {
                    putExtra(ResultActivity.EXTRA_TEXT, text)
                    putExtra(ResultActivity.EXTRA_SOURCE_URI, uri.toString())
                })
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@MainActivity, "OCR failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun processMultipleImages(uris: List<Uri>) {
        showLoading(true, "স্ক্যান শুরু হচ্ছে...")
        scope.launch {
            try {
                val allText = StringBuilder()
                uris.forEachIndexed { index, uri ->
                    withContext(Dispatchers.Main) {
                        binding.tvLoadingStatus.text = "স্ক্যান হচ্ছে ${index+1}/${uris.size}..."
                    }
                    val text = withContext(Dispatchers.IO) {
                        OcrProcessor.processImage(applicationContext, uri)
                    }
                    if (text.isNotBlank()) allText.append("--- পৃষ্ঠা ${index+1} ---\n$text\n\n")
                }
                showLoading(false)
                startActivity(Intent(this@MainActivity, ResultActivity::class.java).apply {
                    putExtra(ResultActivity.EXTRA_TEXT, allText.toString())
                    putExtra(ResultActivity.EXTRA_IS_MULTI_PAGE, true)
                })
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@MainActivity, "OCR failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun processPdfUri(uri: Uri) {
        showLoading(true, "PDF পড়া হচ্ছে...")
        scope.launch {
            try {
                val pages = withContext(Dispatchers.IO) { PdfUtil.pdfToImages(applicationContext, uri) }
                if (pages.isEmpty()) {
                    showLoading(false)
                    Toast.makeText(this@MainActivity, "PDF পড়া যায়নি", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val allText = StringBuilder()
                pages.forEachIndexed { index, bitmap ->
                    withContext(Dispatchers.Main) {
                        binding.tvLoadingStatus.text = "পৃষ্ঠা ${index+1}/${pages.size} স্ক্যান হচ্ছে..."
                    }
                    val text = withContext(Dispatchers.IO) {
                        OcrProcessor.processBitmap(applicationContext, bitmap)
                    }
                    if (text.isNotBlank()) allText.append("=== পৃষ্ঠা ${index+1} ===\n$text\n\n")
                    bitmap.recycle()
                }
                showLoading(false)
                startActivity(Intent(this@MainActivity, ResultActivity::class.java).apply {
                    putExtra(ResultActivity.EXTRA_TEXT, allText.toString())
                    putExtra(ResultActivity.EXTRA_IS_MULTI_PAGE, pages.size > 1)
                })
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@MainActivity, "PDF failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showLoading(show: Boolean, message: String = "স্ক্যান হচ্ছে...") {
        binding.loadingLayout.visibility = if (show) View.VISIBLE else View.GONE
        binding.tvLoadingStatus.text = message
        binding.mainContent.visibility = if (show) View.GONE else View.VISIBLE
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
