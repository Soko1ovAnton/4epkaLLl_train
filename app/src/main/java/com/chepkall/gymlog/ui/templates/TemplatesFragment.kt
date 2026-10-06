package com.chepkall.gymlog.ui.templates

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chepkall.gymlog.R
import com.chepkall.gymlog.data.WorkoutTemplate
import com.chepkall.gymlog.databinding.FragmentTemplatesBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Вкладка «Шаблоны»: список сохранённых тренировок. */
class TemplatesFragment : Fragment() {

    companion object { const val TAG = "TemplatesFragment" }

    private var _binding: FragmentTemplatesBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val vm: TemplatesViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTemplatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnNewTemplate.setOnClickListener { openEditor(0L) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.ui.collect { render(it) }
            }
        }
    }

    private fun render(list: List<TemplateUi>) {
        binding.tplList.removeAllViews()
        binding.tplEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE

        for (t in list) {
            val item = layoutInflater.inflate(R.layout.item_template_row, binding.tplList, false)
            item.findViewById<TextView>(R.id.t_name).text = t.name
            item.findViewById<TextView>(R.id.t_count).text = plural(t.exerciseCount)
            item.findViewById<View>(R.id.t_more).setOnClickListener { showActions(t) }
            item.setOnClickListener { openEditor(t.id) }
            item.setOnLongClickListener { openEditor(t.id); true }
            binding.tplList.addView(item)
        }
    }

    private fun plural(n: Int): String = when {
        n % 10 == 1 && n % 100 != 11 -> "$n упражнение"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "$n упражнения"
        else -> "$n упражнений"
    }

    private fun showActions(t: TemplateUi) {
        MaterialAlertDialogBuilder(requireContext())
            .setItems(arrayOf("Изменить", "Удалить шаблон")) { _, which ->
                when (which) {
                    0 -> openEditor(t.id)
                    1 -> MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Удалить шаблон?")
                        .setMessage("«${t.name}» будет удалён. Тренировки в календаре не пострадают.")
                        .setPositiveButton("Удалить") { _, _ -> vm.delete(WorkoutTemplate(id = t.id, name = t.name)) }
                        .setNegativeButton("Отмена", null)
                        .show()
                }
            }
            .show()
    }

    private fun openEditor(id: Long) {
        parentFragmentManager.commit {
            replace(R.id.container, TemplateEditorFragment.newInstance(id), TemplateEditorFragment.TAG + id)
            addToBackStack(TemplateEditorFragment.TAG + id)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
