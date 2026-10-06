package com.chepkall.gymlog.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.chepkall.gymlog.R
import com.chepkall.gymlog.data.Exercise
import com.chepkall.gymlog.data.Repo
import com.chepkall.gymlog.databinding.FragmentLibraryBinding
import com.chepkall.gymlog.util.CompactMenuPopup
import com.chepkall.gymlog.util.TextInputDialog
import com.chepkall.gymlog.util.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class LibraryFragment : Fragment() {

    companion object { const val TAG = "LibraryFragment" }

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var adapter: LibraryAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = LibraryAdapter(
            onClick = { e -> openEditor(e.id) },
            onLongClick = { e -> showActions(e) },
            onMore = { e -> showActions(e) },
        )
        binding.libList.layoutManager = LinearLayoutManager(requireContext())
        binding.libList.adapter = adapter

        // Кнопка фильтра: компактный список категорий (макс. 6 на экране)
        binding.libFilterBtn.setOnClickListener {
            val categories = listOf("Все группы") + resources.getStringArray(R.array.muscle_groups).toList()
            val pos = categories.indexOf(selectedMuscle ?: "Все группы").coerceAtLeast(0)
            binding.libFilterBtn.tag = pos
            CompactMenuPopup(binding.libFilterBtn, categories, 6) { _, text ->
                selectedMuscle = if (text == "Все группы") null else text
                observeList()
            }.show()
        }

        binding.libSearch.doAfterTextChanged { observeList() }

        binding.libAdd.setOnClickListener {
            openEditor(0L)
        }
    }

    /** Категория-фильтр: null = «Все группы». */
    private var selectedMuscle: String? = null
    private var listJob: kotlinx.coroutines.Job? = null

    private fun observeList() {
        val repo = Repo.get(requireContext())
        val q = binding.libSearch.text?.toString().orEmpty()
        val muscle = selectedMuscle
        listJob?.cancel()
        listJob = viewLifecycleOwner.lifecycleScope.launch {
            repo.dao.search(q).collect { list ->
                val filtered = if (muscle == null) list else list.filter {
                    it.primaryMuscle == muscle || it.secondaryMuscles.contains(muscle)
                }
                adapter.submit(filtered)
                binding.libCount.text = "${filtered.size} упражнений"
            }
        }
    }

    /** Экран создания (id = 0) или редактирования упражнения. */
    private fun openEditor(id: Long) {
        parentFragmentManager.commit {
            replace(R.id.container, ExerciseEditorFragment.newInstance(id), ExerciseEditorFragment.TAG + id)
            addToBackStack(ExerciseEditorFragment.TAG + id)
        }
    }

    private fun showActions(e: Exercise) {
        val items = mutableListOf("Изменить")
        items += if (e.isCustom) "Удалить полностью" else "Скрыть из каталога"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(e.name)
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    "Изменить" -> openEditor(e.id)
                    "Удалить полностью" -> confirmDelete(e)
                    else -> confirmArchive(e)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun confirmDelete(e: Exercise) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удалить упражнение?")
            .setMessage("«${e.name}» уйдёт из каталога. Прошлые тренировки и записи подходов в истории останутся.")
            .setPositiveButton("Удалить") { _, _ ->
                lifecycleScope.launch { Repo.get(requireContext()).dao.delete(e) }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun confirmArchive(e: Exercise) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Скрыть из каталога?")
            .setMessage("«${e.name}» перестанет показываться в списках и поиске.")
            .setPositiveButton("Скрыть") { _, _ ->
                lifecycleScope.launch { Repo.get(requireContext()).dao.archive(e.id) }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}