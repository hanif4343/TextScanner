package com.hanif.textscanner.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.hanif.textscanner.R
import com.hanif.textscanner.data.SessionManager
import com.hanif.textscanner.databinding.ActivityResultBinding
import com.hanif.textscanner.util.McqParser
import com.hanif.textscanner.util.TextExporter
import java.text.SimpleDateFormat
import java.util.*

class ResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEXT       = "extra_text"
        const val EXTRA_SOURCE_URI = "extra_source_uri"
        const val EXTRA_IS_MULTI_PAGE = "extra_is_multi_page"
        const val EXTRA_SESSION_ID = "extra_session_id"
    }

    private lateinit var binding: ActivityResultBinding
    private var originalText = ""
    private var isEditing = false
    private var sessionId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        originalText   = intent.getStringExtra(EXTRA_TEXT) ?: ""
        sessionId      = intent.getLongExtra(EXTRA_SESSION_ID, -1L)

        if (originalText.isBlank()) {
            binding.tvEmpty.visibility    = View.VISIBLE
            binding.scrollContent.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility    = View.GONE
            binding.scrollContent.visibility = View.VISIBLE
            binding.etResult.setText(originalText)
            binding.etResult.isEnabled = false
            updateStats(originalText)
        }

        setupButtons()
        setupSearch()
        detectMcq()
    }

    private fun updateStats(text: String) {
        val lines = text.lines().filter { it.isNotBlank() }.size
        val words = text.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
        val chars = text.length
        binding.tvStats.text = "Lines: $lines | Words: $words | Chars: $chars"
    }

    private fun detectMcq() {
        val mcq = McqParser.parse(originalText)
        if (mcq.isNotEmpty()) {
            binding.chipMcqDetected.visibility = View.VISIBLE
            binding.chipMcqDetected.text = "MCQ: ${mcq.size} টি প্রশ্ন"
            binding.chipMcqDetected.setOnClickListener { showMcqDialog(mcq) }
        }
    }

    private fun showMcqDialog(mcq: List<McqParser.McqQuestion>) {
        val sb = StringBuilder()
        mcq.forEachIndexed { i, q ->
            sb.append("Q${i+1}. ${q.question}\n")
            q.options.forEach { sb.append("   $it\n") }
            sb.append("\n")
        }
        AlertDialog.Builder(this)
            .setTitle("MCQ (${mcq.size} টি)")
            .setMessage(sb.toString())
            .setPositiveButton("সব Copy করুন") { _,_ -> copyToClipboard(sb.toString()) }
            .setNegativeButton("বন্ধ", null)
            .show()
    }

    private fun setupButtons() {
        binding.btnCopy.setOnClickListener {
            copyToClipboard(binding.etResult.text.toString())
            Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
        }

        binding.btnEdit.setOnClickListener {
            isEditing = !isEditing
            binding.etResult.isEnabled = isEditing
            if (isEditing) {
                binding.etResult.requestFocus()
                binding.btnEdit.text = "Save"
            } else {
                binding.btnEdit.text = "Edit"
                originalText = binding.etResult.text.toString()
                updateStats(originalText)
                // Update session if exists
                if (sessionId != -1L) {
                    val session = SessionManager.getSession(this, sessionId)
                    session?.let {
                        // update first page text for single-page sessions
                    }
                }
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnShare.setOnClickListener { showShareOptions() }
        binding.btnExport.setOnClickListener { showExportOptions() }

        // Quick Copy button — new feature
        binding.btnQuickCopy.setOnClickListener { showQuickCopyMenu() }
    }

    /** Quick Copy — copy just questions, just answers, or MCQ format */
    private fun showQuickCopyMenu() {
        val text = binding.etResult.text.toString()
        val mcq  = McqParser.parse(text)
        val lines = text.lines()

        val options = mutableListOf(
            "📋 সব Text Copy",
            "❓ শুধু প্রশ্ন Copy",
            "💡 শুধু উত্তর Copy",
        )
        if (mcq.isNotEmpty()) options.add("✅ MCQ Format Copy (${mcq.size}টি)")
        options.add("📊 Formatted Copy (with header)")

        AlertDialog.Builder(this)
            .setTitle("কী copy করবেন?")
            .setItems(options.toTypedArray()) { _, which ->
                val copied = when (which) {
                    0 -> text

                    1 -> {
                        // Extract question lines — lines with question marks or numbered
                        lines.filter { line ->
                            line.contains("?") || line.contains("কী?") ||
                            line.contains("কি?") || line.matches(Regex("^[০-৯\\d]+[।.)].*"))
                        }.joinToString("\n")
                    }

                    2 -> {
                        // Extract answer lines — lines starting with উত্তর:
                        val answerLines = mutableListOf<String>()
                        var capturing = false
                        lines.forEach { line ->
                            if (line.startsWith("উত্তর") || line.startsWith("Answer")) {
                                capturing = true
                                answerLines.add(line)
                            } else if (capturing && line.matches(Regex("^[০-৯\\d]+.*"))) {
                                capturing = false
                            } else if (capturing && line.isNotBlank()) {
                                answerLines.add(line)
                            }
                        }
                        if (answerLines.isEmpty()) {
                            Toast.makeText(this,"কোনো উত্তর পাওয়া যায়নি",Toast.LENGTH_SHORT).show()
                            return@setItems
                        }
                        answerLines.joinToString("\n")
                    }

                    3 -> if (mcq.isNotEmpty()) McqParser.formatMcq(mcq) else text

                    4 -> "Text Scanner Export\n" +
                         "তারিখ: ${SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(Date())}\n" +
                         "═══════════════════\n\n$text"

                    else -> text
                }
                copyToClipboard(copied)
                Toast.makeText(this, "Copied! (${copied.length} chars)", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun setupSearch() {
        binding.btnSearch.setOnClickListener {
            if (binding.searchBar.visibility == View.VISIBLE) {
                binding.searchBar.visibility = View.GONE
                binding.etResult.setText(originalText)
                binding.tvSearchCount.visibility = View.GONE
            } else {
                binding.searchBar.visibility = View.VISIBLE
                binding.etSearch.requestFocus()
            }
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                highlightSearch(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnSearchClear.setOnClickListener {
            binding.etSearch.text?.clear()
            binding.etResult.setText(originalText)
            binding.tvSearchCount.visibility = View.GONE
        }
    }

    private fun highlightSearch(query: String) {
        if (query.isBlank()) { binding.etResult.setText(originalText); return }
        val text = originalText
        val spannable = SpannableString(text)
        var count = 0
        var idx = text.indexOf(query, ignoreCase = true)
        while (idx >= 0) {
            spannable.setSpan(
                BackgroundColorSpan(ContextCompat.getColor(this, R.color.highlight_yellow)),
                idx, idx + query.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            count++
            idx = text.indexOf(query, idx + 1, ignoreCase = true)
        }
        binding.etResult.setText(spannable)
        binding.tvSearchCount.text = "$count found"
        binding.tvSearchCount.visibility = View.VISIBLE
    }

    private fun showShareOptions() {
        AlertDialog.Builder(this)
            .setTitle("Share")
            .setItems(arrayOf("Text হিসেবে Share", "File হিসেবে Share")) { _, which ->
                if (which == 0) {
                    startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, binding.etResult.text.toString())
                        }, "Share via"
                    ))
                } else {
                    val uri = TextExporter.exportToFile(this, binding.etResult.text.toString())
                    uri?.let {
                        startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, it)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, "Share file"
                        ))
                    }
                }
            }.show()
    }

    private fun showExportOptions() {
        val text = binding.etResult.text.toString()
        AlertDialog.Builder(this)
            .setTitle("Export")
            .setItems(arrayOf("TXT File সেভ করুন", "Formatted TXT")) { _, which ->
                val content = if (which == 1) {
                    "Text Scanner\nতারিখ: ${SimpleDateFormat("dd/MM/yyyy HH:mm",
                        Locale.getDefault()).format(Date())}\n══════════════\n\n$text"
                } else text
                val uri = TextExporter.exportToFile(this, content)
                if (uri != null) Toast.makeText(this,"Downloads-এ সেভ হয়েছে!",Toast.LENGTH_LONG).show()
                else Toast.makeText(this,"Export ব্যর্থ",Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Scanned Text", text))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.result_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        R.id.action_select_all -> { binding.etResult.selectAll(); true }
        R.id.action_clear -> {
            AlertDialog.Builder(this).setTitle("Text মুছবেন?")
                .setPositiveButton("হ্যাঁ") { _,_ ->
                    binding.etResult.setText(""); originalText = "" }
                .setNegativeButton("না", null).show(); true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
