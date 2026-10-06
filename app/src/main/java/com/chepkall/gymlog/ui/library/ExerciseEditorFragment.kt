package com.chepkall.gymlog.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.chepkall.gymlog.R
import com.chepkall.gymlog.data.Exercise
import com.chepkall.gymlog.data.Repo
import com.chepkall.gymlog.databinding.FragmentExerciseEditorBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Экран создания/редактирования упражнения: название, целевая мышца (выпадающий
 * список), побочные мышцы (мультивыбор). Удаление/скрытие — с подтверждением.
 */
class ExerciseEditorFragment : Fragment() {

    companion object {
        const val TAG = "ExerciseEditor"

        fun newInstance(exerciseId: Long, resultKey: String? = null) = ExerciseEditorFragment().apply {
            arguments = Bundle().apply {
                putLong("exercise_id", exerciseId)
                if (resultKey != null) putString("result_key", resultKey)
            }
        }
    }

    private var _binding: FragmentExerciseEditorBinding? = null
    private val binding get() = requireNotNull(_binding)
    private var exercise: Exercise? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExerciseEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val exerciseId = requireArguments().getLong("exercise_id", 0L)
        val isNew = exerciseId <= 0L

        binding.eeTitle.text = if (isNew) "Новое упражнение" else "Изменить"
        binding.eeBack.setOnClickListener { parentFragmentManager.popBackStack() }

        binding.eePrimary.setOnClickListener { showPrimaryPicker() }
        binding.eeSecondary.setOnClickListener { showSecondaryPicker() }

        binding.eeSave.setOnClickListener { save(isNew) }

        if (isNew) {
            binding.eePrimary.setText(resources.getStringArray(R.array.muscle_groups).firstOrNull().orEmpty())
        } else {
            binding.eeDelete.visibility = View.VISIBLE
            lifecycleScope.launch {
                val e = Repo.get(requireContext()).dao.exerciseById(exerciseId) ?: run {
                    parentFragmentManager.popBackStack()
                    return@launch
                }
                exercise = e
                binding.eeName.setText(e.name)
                binding.eePrimary.setText(e.primaryMuscle)
                binding.eeSecondary.setText(e.secondaryMuscles)
                if (e.isCustom) {
                    binding.eeDelete.text = "Удалить упражнение"
                } else {
                    binding.eeDelete.text = "Скрыть из каталога"
                }
            }
            binding.eeDelete.setOnClickListener { exercise?.let { confirmRemove(it) } }
        }
    }

    /** Диалог выбора целевой группы (радио-список — от фильтров не зависит). */
    private fun showPrimaryPicker() {
        val muscles = resources.getStringArray(R.array.muscle_groups)
        val current = binding.eePrimary.text?.toString().orEmpty()
        val checkedIdx = muscles.indexOf(current).coerceAtLeast(0)
        var picked = checkedIdx
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Целевая группа мышц")
            .setSingleChoiceItems(muscles, checkedIdx) { _, which -> picked = which }
            .setPositiveButton("Готово") { _, _ -> binding.eePrimary.setText(muscles[picked]) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun save(isNew: Boolean) {
        val name = binding.eeName.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            binding.eeNameLayout.error = "Введи название"
            return
        }
        binding.eeNameLayout.error = null
        val primary = binding.eePrimary.text?.toString()?.trim().orEmpty()
            .ifBlank { resources.getStringArray(R.array.muscle_groups).first() }
        val secondary = binding.eeSecondary.text?.toString()?.trim().orEmpty()
        val resultKey = requireArguments().getString("result_key")
        lifecycleScope.launch {
            val repo = Repo.get(requireContext())
            val base = exercise
            val e = if (base != null)
                base.copy(name = name, primaryMuscle = primary, secondaryMuscles = secondary)
            else
                Exercise(name = name, primaryMuscle = primary, secondaryMuscles = secondary, isCustom = true)
            if (isNew || base == null) {
                val newId = repo.dao.insert(e)
                // Если открыли из пикера — отдать созданное упражнение вызвавшему экрану
                if (resultKey != null) {
                    parentFragmentManager.setFragmentResult(
                        resultKey,
                        Bundle().apply { putLong("id", newId) },
                    )
                }
            } else {
                repo.dao.update(e)
            }
            parentFragmentManager.popBackStack()
        }
    }

    private fun showSecondaryPicker() {
        val muscles = resources.getStringArray(R.array.muscle_groups)
        val current = binding.eeSecondary.text?.toString()
            ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        val checked = muscles.map { it in current }.toBooleanArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Побочные мышцы")
            .setMultiChoiceItems(muscles, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Готово") { _, _ ->
                binding.eeSecondary.setText(
                    muscles.filterIndexed { i, _ -> checked[i] }.joinToString(", "),
                )
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun confirmRemove(e: Exercise) {
        if (e.isCustom) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Удалить упражнение?")
                .setMessage("«${e.name}» уйдёт из каталога. Прошлые тренировки и записи подходов в истории останутся.")
                .setPositiveButton("Удалить") { _, _ ->
                    lifecycleScope.launch {
                        Repo.get(requireContext()).dao.delete(e)
                        parentFragmentManager.popBackStack()
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        } else {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Скрыть из каталога?")
                .setMessage("«${e.name}» перестанет показываться в списках и поиске.")
                .setPositiveButton("Скрыть") { _, _ ->
                    lifecycleScope.launch {
                        Repo.get(requireContext()).dao.archive(e.id)
                        parentFragmentManager.popBackStack()
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
