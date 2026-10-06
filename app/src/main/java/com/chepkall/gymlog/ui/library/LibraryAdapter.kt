package com.chepkall.gymlog.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chepkall.gymlog.data.Exercise
import com.chepkall.gymlog.databinding.ItemLibraryExerciseBinding

class LibraryAdapter(
    private val onClick: (Exercise) -> Unit,
    private val onLongClick: (Exercise) -> Unit,
    private val onMore: (Exercise) -> Unit,
) : RecyclerView.Adapter<LibraryAdapter.VH>() {

    private val items = mutableListOf<Exercise>()

    fun submit(list: List<Exercise>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemLibraryExerciseBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemLibraryExerciseBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        holder.binding.liName.text = e.name
        holder.binding.liMuscles.text = buildString {
            append("Целевая: ${e.primaryMuscle}")
            if (e.secondaryMuscles.isNotBlank()) append("  ·  Побочные: ${e.secondaryMuscles}")
        }
        holder.binding.liTag.visibility = if (e.isCustom) android.view.View.VISIBLE else android.view.View.GONE
        holder.binding.root.setOnClickListener { onClick(e) }
        holder.binding.root.setOnLongClickListener { onLongClick(e); true }
        holder.binding.liMore.setOnClickListener { onMore(e) }
    }
}
