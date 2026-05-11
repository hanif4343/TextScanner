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
import com.hanif.textscanner.databinding.ActivityResultBinding
import com.hanif.textscanner.data.ScanHistory
import com.hanif.textscanner.data.HistoryManager
import com.hanif.textscanner.util.McqParser
import com.hanif.textscanner.util.TextExporter
import java.text.SimpleDateFormat
import java.util.*

class ResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_SOURCE_URI = "extra_source_uri"
        const val EXTRA_IS_MULTI_PAGE = "extra_is_multi_page"
    }

    private lateinit var binding: ActivityResultBinding
    private var originalText = ""
    private var isEditing = false
    private var searchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        originalText = intent.getStringExtra(EXTRA_TEXT) ?: ""
        val sourceUri = intent.getStringExtra(EXTRA_SOURCE_URI) ?: ""
        val isMultiPage = intent.getBooleanExtra(EXTRA_IS_MULTI_PAGE, false)

        if (originalText.isBlank()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.scrollContent.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.scrollContent.visibility = View.VISIBLE
            displayText(originalText)
            updateStats(originalText)
            saveToHistory(originalText, sourceUri)
        }

        setupButtons()
        setupSearch()
        detectMcq()
    }

    private fun displayText(text: String) {
        binding.etResult.setText(text)
        binding.etResult.isEnabled = false
    }

    private fun updateStats(text: String) {
        val lines = text.lines().filter { it.isNotBlank() }.size
        val words = text.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
        val chars = text.length
        binding.tvStats.text = "Lines: $lines | Words: $words | Chars: $chars"
    }

    private fun detectMcq() {
        val mcqData = McqParser.parse(originalText)
        if (mcqData.isNotEmpty()) {
            binding.chipMcqDetected.visibility = View.VISIBLE
            binding.chipMcqDetected.text = "MCQ: ${mcqData.size} questions found"
            binding.chipMcqDetected.setOnClickListener {
                showMcqDialog(mcqData)
            }
        }
    }

    private fun showMcqDialog(mcqData: List<McqParser.McqQuestion>) {
        val sb = StringBuilder()
        mcqData.forEachIndexed { i, q ->
            sb.append("Q${i + 1}. ${q.question}\n")
            q.options.forEach { sb.append("  $it\n") }
            sb.append("\n")
        }
        AlertDialog.Builder(this)
            .setTitle("MCQ Questions (${mcqData.size})")
            .setMessage(sb.toString())
            .setPositiveButton("Copy All") { _, _ ->
                copyToClipboard(sb.toString())
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun setupButtons() {
        // Copy button
        binding.btnCopy.setOnClickListener {
            val text = binding.etResult.text.toString()
            copyToClipboard(text)
            Toast.makeText(this, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        // Edit/Save toggle
        binding.btnEdit.setOnClickListener {
            isEditing = !isEditing
            binding.etResult.isEnabled = isEditing
            if (isEditing) {
                binding.etResult.requestFocus()
                binding.btnEdit.text = "Save"
                binding.btnEdit.setIconResource(R.drawable.ic_save)
                Toast.makeText(this, "Editing enabled", Toast.LENGTH_SHORT).show()
            } else {
                binding.btnEdit.text = "Edit"
                binding.btnEdit.setIconResource(R.drawable.ic_edit)
                originalText = binding.etResult.text.toString()
                updateStats(originalText)
                Toast.makeText(this, "Changes saved", Toast.LENGTH_SHORT).show()
            }
        }

        // Share button
        binding.btnShare.setOnClickListener {
            showShareOptions()
        }

        // Export button
        binding.btnExport.setOnClickListener {
            showExportOptions()
        }
    }

    private fun setupSearch() {
        binding.btnSearch.setOnClickListener {
            if (binding.searchBar.visibility == View.VISIBLE) {
                binding.searchBar.visibility = View.GONE
                clearHighlights()
            } else {
                binding.searchBar.visibility = View.VISIBLE
                binding.etSearch.requestFocus()
            }
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s.toString()
                highlightSearchResults(searchQuery)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnSearchClear.setOnClickListener {
            binding.etSearch.text?.clear()
            clearHighlights()
        }
    }

    private fun highlightSearchResults(query: String) {
        if (query.isBlank()) {
            clearHighlights()
            return
        }
        val text = binding.etResult.text.toString()
        val spannable = SpannableString(text)
        var count = 0
        var index = text.indexOf(query, ignoreCase = true)
        while (index >= 0) {
            spannable.setSpan(
                BackgroundColorSpan(ContextCompat.getColor(this, R.color.highlight_yellow)),
                index,
                index + query.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            count++
            index = text.indexOf(query, index + 1, ignoreCase = true)
        }
        binding.etResult.setText(spannable)
        binding.tvSearchCount.text = if (count > 0) "$count found" else "Not found"
        binding.tvSearchCount.visibility = View.VISIBLE
    }

    private fun clearHighlights() {
        binding.etResult.setText(originalText)
        binding.tvSearchCount.visibility = View.GONE
    }

    private fun showShareOptions() {
        val options = arrayOf("Share as Text", "Share as TXT File")
        AlertDialog.Builder(this)
            .setTitle("Share")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> shareAsText()
                    1 -> shareAsTxtFile()
                }
            }.show()
    }

    private fun showExportOptions() {
        val options = arrayOf("Save as TXT", "Save as formatted TXT")
        AlertDialog.Builder(this)
            .setTitle("Export")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportAsTxt(false)
                    1 -> exportAsTxt(true)
                }
            }.show()
    }

    private fun shareAsText() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, binding.etResult.text.toString())
        }
        startActivity(Intent.createChooser(intent, "Share text via"))
    }

    private fun shareAsTxtFile() {
        val uri = TextExporter.exportToFile(this, binding.etResult.text.toString())
        uri?.let {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, it)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share file via"))
        }
    }

    private fun exportAsTxt(formatted: Boolean) {
        val text = if (formatted) {
            "Text Scanner - Export\n" +
            "Date: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}\n" +
            "═══════════════════════════\n\n" +
            binding.etResult.text.toString()
        } else {
            binding.etResult.text.toString()
        }
        val uri = TextExporter.exportToFile(this, text)
        if (uri != null) {
            Toast.makeText(this, "Exported to Downloads!", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Scanned Text", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun saveToHistory(text: String, sourceUri: String) {
        val history = ScanHistory(
            id = System.currentTimeMillis(),
            text = text,
            sourceUri = sourceUri,
            timestamp = System.currentTimeMillis(),
            wordCount = text.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
        )
        HistoryManager.addHistory(this, history)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.result_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_select_all -> {
                binding.etResult.selectAll()
                true
            }
            R.id.action_clear -> {
                AlertDialog.Builder(this)
                    .setTitle("Clear Text")
                    .setMessage("Clear all scanned text?")
                    .setPositiveButton("Clear") { _, _ ->
                        binding.etResult.setText("")
                        originalText = ""
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
