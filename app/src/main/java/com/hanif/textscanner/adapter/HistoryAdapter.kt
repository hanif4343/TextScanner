package com.hanif.textscanner.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hanif.textscanner.data.ScanHistory
import com.hanif.textscanner.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private val onClick: (ScanHistory) -> Unit
) : ListAdapter<ScanHistory, HistoryAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ScanHistory) {
            val preview = item.text.take(120).replace("\n", " ").trim()
            binding.tvPreview.text = if (preview.length >= 120) "$preview..." else preview

            val date = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                .format(Date(item.timestamp))
            binding.tvDate.text = date
            binding.tvWordCount.text = "${item.wordCount} words"

            binding.root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<ScanHistory>() {
        override fun areItemsTheSame(oldItem: ScanHistory, newItem: ScanHistory) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ScanHistory, newItem: ScanHistory) = oldItem == newItem
    }
}
