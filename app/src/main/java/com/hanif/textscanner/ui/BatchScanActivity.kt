package com.hanif.textscanner.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.hanif.textscanner.adapter.BatchPageAdapter
import com.hanif.textscanner.data.*
import com.hanif.textscanner.databinding.ActivityBatchScanBinding
import com.hanif.textscanner.ocr.OcrProcessor
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class BatchScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBatchScanBinding
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var adapter: BatchPageAdapter

    private var session: ScanSession? = null
    private var isScanning = false

    // Image picker — multiple
    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) addImagesToQueue(uris)
    }

    // Camera
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>("captured_image_uri")
            uri?.let { addImagesToQueue(listOf(it)) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBatchScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Check if resuming existing session
        val resumeId = intent.getLongExtra(EXTRA_RESUME_SESSION_ID, -1L)
        if (resumeId != -1L) {
            session = SessionManager.getSession(this, resumeId)
            supportActionBar?.title = "Resume: ${session?.title ?: "Scan"}"
        } else {
            supportActionBar?.title = "Batch Scan"
        }

        setupRecyclerView()
        setupButtons()
        setupFolderSelector()

        // Load existing pages if resuming
        session?.let { loadExistingPages(it) }
        updateUI()
    }

    private fun setupRecyclerView() {
        adapter = BatchPageAdapter(
            onRetry = { page -> retryPage(page) },
            onDelete = { page -> deletePage(page) }
        )
        binding.rvPages.layoutManager = LinearLayoutManager(this)
        binding.rvPages.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnAddGallery.setOnClickListener {
            imagePicker.launch("image/*")
        }
        binding.btnAddCamera.setOnClickListener {
            cameraLauncher.launch(Intent(this, CameraActivity::class.java))
        }
        binding.btnStartScan.setOnClickListener {
            if (!isScanning) startBatchScan()
        }
        binding.btnViewResult.setOnClickListener {
            openResult()
        }
        binding.btnClearAll.setOnClickListener {
            if (!isScanning) confirmClear()
        }
    }

    private fun setupFolderSelector() {
        val folders = SessionManager.getFolders(this)
        val names = folders.map { "${it.icon} ${it.name}" }.toTypedArray()
        var selectedIdx = 0

        binding.btnFolder.text = "${folders[0].icon} ${folders[0].name}"
        binding.btnFolder.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Folder নির্বাচন করুন")
                .setSingleChoiceItems(names, selectedIdx) { _, which ->
                    selectedIdx = which
                    binding.btnFolder.text = names[which]
                }
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun addImagesToQueue(uris: List<Uri>) {
        val currentCount = adapter.itemCount
        val newPages = uris.mapIndexed { i, uri ->
            BatchPage(
                pageNumber = currentCount + i + 1,
                imageUri = uri.toString(),
                status = PageStatus.PENDING
            )
        }
        adapter.addPages(newPages)
        updateUI()
    }

    private fun loadExistingPages(s: ScanSession) {
        val existing = s.pages.map { page ->
            BatchPage(
                pageNumber = page.pageNumber,
                imageUri = page.imageUri,
                status = if (page.failed) PageStatus.FAILED
                         else if (page.text.isNotBlank()) PageStatus.DONE
                         else PageStatus.PENDING,
                text = page.text
            )
        }
        adapter.addPages(existing)
    }

    private fun startBatchScan() {
        val pages = adapter.getPendingPages()
        if (pages.isEmpty()) {
            Toast.makeText(this, "কোনো pending page নেই", Toast.LENGTH_SHORT).show()
            return
        }

        // Create/get session
        if (session == null) {
            val title = "Scan_${SimpleDateFormat("MMdd_HHmm", Locale.getDefault()).format(Date())}"
            session = SessionManager.createSession(
                context = this,
                title = title,
                totalExpected = adapter.itemCount
            )
        }

        isScanning = true
        binding.btnStartScan.isEnabled = false
        binding.btnStartScan.text = "স্ক্যান হচ্ছে..."
        binding.progressBar.visibility = View.VISIBLE

        val sessionId = session!!.id

        scope.launch {
            var doneCount = 0
            pages.forEach { batchPage ->
                withContext(Dispatchers.Main) {
                    adapter.updatePageStatus(batchPage.pageNumber, PageStatus.SCANNING)
                    binding.tvProgress.text = "স্ক্যান হচ্ছে ${batchPage.pageNumber}/${adapter.itemCount}..."
                }

                var text = ""
                var failed = false
                var retries = 0

                // Retry up to 2 times
                while (text.isBlank() && retries < 2) {
                    try {
                        text = withContext(Dispatchers.IO) {
                            OcrProcessor.processImage(
                                applicationContext,
                                Uri.parse(batchPage.imageUri)
                            )
                        }
                    } catch (e: Exception) {
                        retries++
                        if (retries < 2) delay(1000)
                    }
                    retries++
                }

                failed = text.isBlank()

                // Save page immediately — partial save
                val scanPage = ScanPage(
                    pageNumber = batchPage.pageNumber,
                    text = text,
                    imageUri = batchPage.imageUri,
                    failed = failed
                )
                withContext(Dispatchers.IO) {
                    SessionManager.savePage(applicationContext, sessionId, scanPage)
                }

                withContext(Dispatchers.Main) {
                    adapter.updatePageResult(
                        pageNumber = batchPage.pageNumber,
                        text = text,
                        status = if (failed) PageStatus.FAILED else PageStatus.DONE
                    )
                    if (!failed) doneCount++
                }
            }

            // Mark complete if all done
            val failedCount = adapter.getFailedPages().size
            if (failedCount == 0) {
                SessionManager.completeSession(applicationContext, sessionId)
            }

            withContext(Dispatchers.Main) {
                isScanning = false
                binding.progressBar.visibility = View.GONE
                binding.btnStartScan.isEnabled = true
                binding.btnStartScan.text = if (failedCount > 0)
                    "Retry Failed ($failedCount)" else "স্ক্যান সম্পন্ন ✓"
                binding.tvProgress.text = "$doneCount/${adapter.itemCount} সফল" +
                    if (failedCount > 0) ", $failedCount ব্যর্থ" else ""
                binding.btnViewResult.visibility = View.VISIBLE
                updateUI()
            }
        }
    }

    private fun retryPage(page: BatchPage) {
        scope.launch {
            adapter.updatePageStatus(page.pageNumber, PageStatus.SCANNING)
            try {
                val text = withContext(Dispatchers.IO) {
                    OcrProcessor.processImage(applicationContext, Uri.parse(page.imageUri))
                }
                val failed = text.isBlank()
                session?.id?.let { id ->
                    SessionManager.savePage(applicationContext, id,
                        ScanPage(page.pageNumber, text, page.imageUri, failed = failed))
                }
                adapter.updatePageResult(page.pageNumber, text,
                    if (failed) PageStatus.FAILED else PageStatus.DONE)
            } catch (e: Exception) {
                adapter.updatePageStatus(page.pageNumber, PageStatus.FAILED)
            }
            updateUI()
        }
    }

    private fun deletePage(page: BatchPage) {
        adapter.removePage(page.pageNumber)
        updateUI()
    }

    private fun openResult() {
        val sessionId = session?.id ?: return
        val s = SessionManager.getSession(this, sessionId) ?: return
        if (s.fullText.isBlank()) {
            Toast.makeText(this, "এখনো কোনো text নেই", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, ResultActivity::class.java).apply {
            putExtra(ResultActivity.EXTRA_TEXT, s.fullText)
            putExtra(ResultActivity.EXTRA_SESSION_ID, sessionId)
            putExtra(ResultActivity.EXTRA_IS_MULTI_PAGE, s.pages.size > 1)
        })
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("সব মুছবেন?")
            .setMessage("Queue-এর সব page মুছে যাবে।")
            .setPositiveButton("হ্যাঁ") { _, _ ->
                adapter.clearAll()
                session = null
                updateUI()
            }
            .setNegativeButton("না", null)
            .show()
    }

    private fun updateUI() {
        val count = adapter.itemCount
        val pending = adapter.getPendingPages().size
        val done = adapter.getDonePages().size
        val failed = adapter.getFailedPages().size

        binding.tvQueueInfo.text = when {
            count == 0 -> "ছবি যোগ করুন"
            else -> "মোট: $count | সম্পন্ন: $done | বাকি: $pending | ব্যর্থ: $failed"
        }

        binding.btnStartScan.visibility = if (count > 0) View.VISIBLE else View.GONE
        binding.btnClearAll.visibility  = if (count > 0 && !isScanning) View.VISIBLE else View.GONE
        binding.btnViewResult.visibility = if (done > 0) View.VISIBLE else View.GONE

        if (!isScanning && failed > 0) {
            binding.btnStartScan.text = "Retry ব্যর্থ ($failed টি)"
        } else if (!isScanning && pending > 0) {
            binding.btnStartScan.text = "স্ক্যান শুরু করুন ($pending টি)"
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }

    companion object {
        const val EXTRA_RESUME_SESSION_ID = "resume_session_id"
    }
}

// ── Data classes for batch queue ─────────────────────────────────────────────

data class BatchPage(
    val pageNumber: Int,
    val imageUri: String,
    var status: PageStatus = PageStatus.PENDING,
    var text: String = ""
)

enum class PageStatus { PENDING, SCANNING, DONE, FAILED }
