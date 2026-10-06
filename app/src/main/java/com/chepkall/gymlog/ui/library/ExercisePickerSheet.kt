package com.chepkall.gymlog.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chepkall.gymlog.R
import com.chepkall.gymlog.data.Exercise
import com.chepkall.gymlog.data.Repo
import com.chepkall.gymlog.databinding.SheetExercisePickerBinding
import com.chepkall.gymlog.util.CompactMenuPopup
import com.chepkall.gymlog.util.doAfterTextChanged
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/** Выбор упражнения из каталога (с созданием нового на лету). */
class ExercisePickerSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "ExercisePickerSheet"
        fun newInstance(resultKey: String) = ExercisePickerSheet().apply {
            arguments = Bundle().apply { putString("pick_key", resultKey) }
        }
    }

    private var _binding: SheetExercisePickerBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var adapter: ExercisePickAdapter

    /** Категория-фильтр: null = «Все группы». */
    private var selectedMuscle: String? = null
    private var menuPopup: CompactMenuPopup? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetExercisePickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.ThemeOverlay_GymLog_BottomSheet)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = ExercisePickAdapter { e -> deliver(e) }
        binding.pickList.layoutManager = LinearLayoutManager(requireContext())
        binding.pickList.adapter = adapter

        // Плавающая кнопка фильтра: компактный список категорий (макс. 6 на экране)
        binding.pickFilterBtn.setOnClickListener {
            val categories = listOf("Все группы") + resources.getStringArray(R.array.muscle_groups).toList()
            val pos = categories.indexOf(selectedMuscle ?: "Все группы").coerceAtLeast(0)
            binding.pickFilterBtn.tag = pos
            menuPopup = CompactMenuPopup(binding.pickFilterBtn, categories, 6) { _, text ->
                selectedMuscle = if (text == "Все группы") null else text
                observeSearch(binding.pickSearch.text?.toString().orEmpty())
            }.also { it.show() }
        }

        binding.pickSearch.doAfterTextChanged { q ->
            observeSearch(q?.toString().orEmpty())
        }
        observeSearch("")

        binding.pickCreate.setOnClickListener {
            // Полноценное создание: открываем экран редактирования упражнения.
            // Вызывающий экран слушает <pick_key>_create и открывает редактор,
            // после сохранения упражнение прилетит обратно по <pick_key>.
            val key = arguments?.getString("pick_key") ?: return@setOnClickListener
            parentFragmentManager.setFragmentResult(key + "_create", Bundle())
            dismiss()
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    private fun observeSearch(q: String) {
        searchJob?.cancel()
        val muscle = selectedMuscle
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            Repo.get(requireContext()).dao.search(q.ifBlank { "" }).collect { list ->
                val filtered = if (muscle == null) list else list.filter {
                    it.primaryMuscle == muscle || it.secondaryMuscles.contains(muscle)
                }
                adapter.submit(filtered)
            }
        }
    }

    private fun deliver(e: Exercise) {
        val key = arguments?.getString("pick_key") ?: return
        parentFragmentManager.setFragmentResult(
            key,
            Bundle().apply { putLong("id", e.id) },
        )
        dismiss()
    }

    override fun onDestroyView() {
        menuPopup?.dismiss()
        menuPopup = null
        super.onDestroyView()
        _binding = null
    }
}
