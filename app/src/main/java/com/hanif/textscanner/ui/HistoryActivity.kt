package com.hanif.textscanner.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.hanif.textscanner.adapter.HistoryAdapter
import com.hanif.textscanner.data.HistoryManager
import com.hanif.textscanner.databinding.ActivityHistoryBinding

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Scan History"

        setupRecyclerView()
        loadHistory()

        binding.btnClearAll.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear History")
                .setMessage("Delete all scan history?")
                .setPositiveButton("Delete") { _, _ ->
                    HistoryManager.clearAll(this)
                    loadHistory()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter { history ->
            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra(ResultActivity.EXTRA_TEXT, history.text)
                putExtra(ResultActivity.EXTRA_SOURCE_URI, history.sourceUri)
            }
            startActivity(intent)
        }
        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = adapter
    }

    private fun loadHistory() {
        val list = HistoryManager.getAll(this)
        if (list.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recyclerHistory.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerHistory.visibility = View.VISIBLE
            adapter.submitList(list)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}
