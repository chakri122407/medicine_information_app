package com.example.medicine_informationapp.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.medicine_informationapp.databinding.ItemRecognizedTextBinding

class RecognizedTextAdapter(
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<RecognizedTextAdapter.ViewHolder>() {

    private val lines = mutableListOf<String>()

    fun submitList(newLines: List<String>) {
        lines.clear()
        lines.addAll(newLines)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecognizedTextBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(lines[position])
    }

    override fun getItemCount(): Int = lines.size

    inner class ViewHolder(private val binding: ItemRecognizedTextBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(textLine: String) {
            binding.tvLineText.text = textLine
            binding.root.setOnClickListener {
                onItemClick(textLine)
            }
        }
    }
}
