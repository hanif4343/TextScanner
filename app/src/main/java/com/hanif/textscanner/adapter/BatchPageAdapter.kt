package com.hanif.textscanner.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hanif.textscanner.R
import com.hanif.textscanner.ui.BatchPage
import com.hanif.textscanner.ui.PageStatus

class BatchPageAdapter(
    private val onRetry: (BatchPage) -> Unit,
    private val onDelete: (BatchPage) -> Unit
) : RecyclerView.Adapter<BatchPageAdapter.VH>() {

    private val pages = mutableListOf<BatchPage>()

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvNum:     TextView    = view.findViewById(R.id.tvPageNum)
        val tvStatus:  TextView    = view.findViewById(R.id.tvPageStatus)
        val tvPreview: TextView    = view.findViewById(R.id.tvPagePreview)
        val progress:  ProgressBar = view.findViewById(R.id.pageProgress)
        val btnRetry:  ImageButton = view.findViewById(R.id.btnRetryPage)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeletePage)
        val ivStatus:  ImageView   = view.findViewById(R.id.ivPageStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_batch_page, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val page = pages[position]
        holder.tvNum.text = "পৃষ্ঠা ${page.pageNumber}"

        when (page.status) {
            PageStatus.PENDING -> {
                holder.tvStatus.text = "অপেক্ষায়..."
                holder.tvStatus.setTextColor(0xFF9CA3AF.toInt())
                holder.progress.visibility = View.GONE
                holder.ivStatus.setImageResource(R.drawable.ic_pending)
                holder.btnRetry.visibility = View.GONE
            }
            PageStatus.SCANNING -> {
                holder.tvStatus.text = "স্ক্যান হচ্ছে..."
                holder.tvStatus.setTextColor(0xFF60A5FA.toInt())
                holder.progress.visibility = View.VISIBLE
                holder.ivStatus.setImageResource(R.drawable.ic_scan)
                holder.btnRetry.visibility = View.GONE
            }
            PageStatus.DONE -> {
                val words = page.text.trim().split("\\s+".toRegex()).size
                holder.tvStatus.text = "✓ $words words"
                holder.tvStatus.setTextColor(0xFF34D399.toInt())
                holder.progress.visibility = View.GONE
                holder.ivStatus.setImageResource(R.drawable.ic_scan)
                holder.btnRetry.visibility = View.GONE
            }
            PageStatus.FAILED -> {
                holder.tvStatus.text = "❌ ব্যর্থ"
                holder.tvStatus.setTextColor(0xFFEF4444.toInt())
                holder.progress.visibility = View.GONE
                holder.ivStatus.setImageResource(R.drawable.ic_close)
                holder.btnRetry.visibility = View.VISIBLE
            }
        }

        val preview = page.text.take(80).replace("\n", " ").trim()
        holder.tvPreview.text = if (preview.isNotEmpty()) preview else "—"
        holder.tvPreview.visibility = if (page.text.isNotEmpty()) View.VISIBLE else View.GONE

        holder.btnRetry.setOnClickListener  { onRetry(page) }
        holder.btnDelete.setOnClickListener { onDelete(page) }
    }

    override fun getItemCount() = pages.size

    fun addPages(newPages: List<BatchPage>) {
        val start = pages.size
        pages.addAll(newPages)
        notifyItemRangeInserted(start, newPages.size)
    }

    fun updatePageStatus(pageNumber: Int, status: PageStatus) {
        val idx = pages.indexOfFirst { it.pageNumber == pageNumber }
        if (idx >= 0) { pages[idx].status = status; notifyItemChanged(idx) }
    }

    fun updatePageResult(pageNumber: Int, text: String, status: PageStatus) {
        val idx = pages.indexOfFirst { it.pageNumber == pageNumber }
        if (idx >= 0) { pages[idx].status = status; pages[idx].text = text; notifyItemChanged(idx) }
    }

    fun removePage(pageNumber: Int) {
        val idx = pages.indexOfFirst { it.pageNumber == pageNumber }
        if (idx >= 0) { pages.removeAt(idx); notifyItemRemoved(idx) }
    }

    fun clearAll() { val sz = pages.size; pages.clear(); notifyItemRangeRemoved(0, sz) }

    fun getPendingPages()  = pages.filter { it.status == PageStatus.PENDING }
    fun getDonePages()     = pages.filter { it.status == PageStatus.DONE }
    fun getFailedPages()   = pages.filter { it.status == PageStatus.FAILED }
}
