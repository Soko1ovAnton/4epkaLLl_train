package com.chepkall.gymlog.ui.templates

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chepkall.gymlog.R
import com.chepkall.gymlog.databinding.FragmentTemplateEditorBinding
import com.chepkall.gymlog.ui.library.ExerciseEditorFragment
import com.chepkall.gymlog.ui.library.ExercisePickerSheet
import com.chepkall.gymlog.util.Plans
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Редактор шаблона: название + упражнения (подходы и повторы точные или диапазоном). */
class TemplateEditorFragment : Fragment() {

    companion object {
        const val TAG = "TemplateEditor"
        private const val KEY_PICK = "pick_for_template"

        fun newInstance(templateId: Long) = TemplateEditorFragment().apply {
            arguments = Bundle().apply { putLong("template_id", templateId) }
        }
    }

    private var _binding: FragmentTemplateEditorBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val vm: TemplateEditorViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTemplateEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val templateId = requireArguments().getLong("template_id", 0L)
        vm.bind(templateId)
        binding.edTitle.text = if (templateId > 0L) "Шаблон" else "Новый шаблон"

        // Выбор упражнения из каталога → в шаблон (свой ключ, чтобы не конфликтовать с календарём)
        setFragmentResultListener(KEY_PICK) { _, bundle ->
            val exId = bundle.getLong("id", -1L)
            if (exId > 0) vm.addExercise(exId)
        }
        // «Создать новое» из пикера → полный экран редактирования; после сохранения
        // упражнение вернётся по KEY_PICK и сразу добавится в шаблон
        setFragmentResultListener(KEY_PICK + "_create") { _, _ ->
            parentFragmentManager.commit {
                replace(R.id.container, ExerciseEditorFragment.newInstance(0L, KEY_PICK), ExerciseEditorFragment.TAG + "new")
                addToBackStack(ExerciseEditorFragment.TAG + "new")
            }
        }

        binding.edBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.edAddExercise.setOnClickListener {
            ExercisePickerSheet.newInstance(KEY_PICK).show(parentFragmentManager, ExercisePickerSheet.TAG)
        }
        binding.edName.doAfterTextChanged { vm.rename(it?.toString().orEmpty()) }
        binding.edSave.setOnClickListener {
            // По умолчанию — обновить этот же шаблон (переименование/правки на месте)
            vm.save(updateExisting = true) { parentFragmentManager.popBackStack() }
        }
        if (templateId > 0L) {
            binding.edSaveAs.visibility = View.VISIBLE
            binding.edSaveAs.setOnClickListener {
                // Явное «сохранить как»: старый шаблон не трогаем, создаётся копия
                vm.save(updateExisting = false) { parentFragmentManager.popBackStack() }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.name.collect { n ->
                        if (binding.edName.text.toString() != n) binding.edName.setText(n)
                        if (n.isNotBlank() && templateId > 0L) binding.edTitle.text = n
                    }
                }
                launch {
                    vm.rows.collect { render(it) }
                }
            }
        }
    }

    private fun render(rows: List<TemplateRow>) {
        binding.edList.removeAllViews()
        binding.edEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE

        rows.forEachIndexed { idx, r ->
            val item = layoutInflater.inflate(R.layout.item_template_exercise, binding.edList, false)
            item.findViewById<TextView>(R.id.te_name).text = r.nameSnapshot
            item.findViewById<TextView>(R.id.te_params).text = Plans.planLabel(r.planSets, r.planRepsMin, r.planRepsMax)

            item.findViewById<View>(R.id.te_up).visibility =
                if (idx == 0) View.INVISIBLE else View.VISIBLE
            item.findViewById<View>(R.id.te_down).visibility =
                if (idx == rows.lastIndex) View.INVISIBLE else View.VISIBLE

            item.findViewById<View>(R.id.te_up).setOnClickListener { vm.move(idx, -1) }
            item.findViewById<View>(R.id.te_down).setOnClickListener { vm.move(idx, 1) }
            item.findViewById<View>(R.id.te_more).setOnClickListener { showRowActions(idx) }
            item.setOnClickListener { showParamsDialog(idx, r) }
            binding.edList.addView(item)
        }
    }

    private fun showRowActions(position: Int) {
        val r = vm.rows.value.getOrNull(position) ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setItems(arrayOf("Подходы и повторы", "Убрать упражнение")) { _, which ->
                when (which) {
                    0 -> showParamsDialog(position, r)
                    1 -> vm.remove(position)
                }
            }
            .show()
    }

    /** Диалог «подходы × повторы»: точно или диапазоном. */
    private fun showParamsDialog(position: Int, r: TemplateRow) {
        val dlgView = layoutInflater.inflate(R.layout.dialog_template_params, null)
        val inSets = dlgView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.p_sets)
        val inMin = dlgView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.p_reps_min)
        val inMax = dlgView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.p_reps_max)

        inSets.setText(r.planSets.toString())
        if (r.planRepsMin > 0) {
            inMin.setText(r.planRepsMin.toString())
            inMax.setText(if (r.planRepsMax > r.planRepsMin) r.planRepsMax.toString() else "")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(r.nameSnapshot)
            .setView(dlgView)
            .setPositiveButton("Готово") { _, _ ->
                val sets = inSets.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
                val min = inMin.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: 0
                var max = inMax.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: 0
                if (max < min) max = min
                vm.updateParams(position, sets, min, max)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}