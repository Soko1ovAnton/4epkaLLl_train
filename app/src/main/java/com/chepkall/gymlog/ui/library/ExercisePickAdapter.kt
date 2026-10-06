package com.chepkall.gymlog.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chepkall.gymlog.data.Exercise
import com.chepkall.gymlog.databinding.ItemExercisePickBinding

class ExercisePickAdapter(
    private val onClick: (Exercise) -> Unit,
) : RecyclerView.Adapter<ExercisePickAdapter.VH>() {

    private val items = mutableListOf<Exercise>()

    fun submit(list: List<Exercise>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemExercisePickBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemExercisePickBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        holder.binding.epName.text = e.name
        holder.binding.epMuscles.text = if (e.secondaryMuscles.isBlank()) {
            e.primaryMuscle
        } else {
            "${e.primaryMuscle} · ${e.secondaryMuscles}"
        }
        holder.binding.root.setOnClickListener { onClick(e) }
    }
}