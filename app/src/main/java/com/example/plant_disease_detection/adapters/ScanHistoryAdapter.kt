package com.example.plant_disease_detection.adapters

import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.plant_disease_detection.R
import com.example.plant_disease_detection.databinding.ItemScanHistoryBinding
import com.example.plant_disease_detection.utils.ScanHistoryEntry
import java.text.DateFormat
import java.util.Date

class ScanHistoryAdapter : RecyclerView.Adapter<ScanHistoryAdapter.VH>() {
    private var items: List<ScanHistoryEntry> = emptyList()

    fun submit(list: List<ScanHistoryEntry>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemScanHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class VH(private val binding: ItemScanHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: ScanHistoryEntry) {
            loadThumb(entry.imageUri)
            binding.diseaseText.text = entry.disease.replace('_', ' ')
            val dateStr = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(entry.timestampMillis))
            binding.metaText.text = buildString {
                entry.sourceLabel?.let {
                    append(it)
                    append(" · ")
                }
                append(dateStr)
            }
            if (entry.confidencePercent > 0f) {
                binding.confidenceText.visibility = View.VISIBLE
                binding.confidenceText.text =
                    "Confidence: %.1f%%".format(entry.confidencePercent)
            } else {
                binding.confidenceText.visibility = View.GONE
            }
        }

        private fun loadThumb(uriString: String) {
            try {
                when {
                    uriString.startsWith("/") && !uriString.startsWith("//") -> {
                        val bmp = BitmapFactory.decodeFile(uriString)
                        if (bmp != null) binding.thumb.setImageBitmap(bmp)
                        else binding.thumb.setImageResource(R.drawable.healthy_plant)
                    }
                    else -> {
                        binding.thumb.setImageURI(Uri.parse(uriString))
                    }
                }
            } catch (_: Exception) {
                binding.thumb.setImageResource(R.drawable.healthy_plant)
            }
        }
    }
}

