package com.chepkall.gymlog.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chepkall.gymlog.R
import com.chepkall.gymlog.data.Repo
import com.chepkall.gymlog.databinding.FragmentStatsBinding
import com.chepkall.gymlog.util.Dates
import kotlinx.coroutines.launch
import kotlin.math.abs

class StatsFragment : Fragment() {

    companion object { const val TAG = "StatsFragment" }

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val vm: StatsViewModel by viewModels()

    private var searchAdapter: ArrayAdapter<String>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Выбор упражнения из подсказок строки поиска
        binding.exSearch.setOnItemClickListener { _, _, pos, _ ->
            val name = binding.exSearch.adapter?.getItem(pos)?.toString() ?: return@setOnItemClickListener
            vm.selectByName(name)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.ui.collect { render(it) }
            }
        }
    }

    private fun render(ui: StatsUi) {
        // Строка поиска упражнения: подсказки при вводе, выбор — по тапу
        val names = ui.exercises.map { it.name }
        val selName = ui.exercises.firstOrNull { it.id == ui.selectedId }?.name
        if (searchAdapter == null || (searchAdapter?.count ?: 0) != names.size) {
            searchAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, names)
            binding.exSearch.setAdapter(searchAdapter)
        }
        if (selName != null && binding.exSearch.text.toString() != selName) {
            binding.exSearch.setText(selName, false)
        }
        if (selName == null && binding.exSearch.text.isNullOrEmpty()) {
            binding.exSearch.hint = "Найти упражнение"
        }

        binding.chart.setPoints(ui.points)

        binding.stat1rm.text = if (ui.current1Rm > 0) "${fmt(ui.current1Rm)} кг" else "—"
        val delta = ui.current1Rm - ui.prev1Rm
        binding.stat1rmDelta.text = when {
            ui.points.isEmpty() -> ""
            abs(ui.current1Rm - ui.prev1Rm) < 0.05 -> "без изменений"
            ui.current1Rm > ui.prev1Rm -> "▲ +${fmt(ui.current1Rm - ui.prev1Rm)} кг"
            else -> "▼ −${fmt(ui.prev1Rm - ui.current1Rm)} кг"
        }
        binding.stat1rmDelta.setTextColor(
            if (ui.current1Rm >= ui.prev1Rm) requireContext().getColor(R.color.md_theme_primary)
            else requireContext().getColor(R.color.md_theme_error),
        )

        binding.statBest.text = ui.bestSet?.let {
            "${fmt(it.weightKg)} кг × ${it.reps} · ${Dates.dayMonth(it.date)}"
        } ?: "—"

        binding.statVolume.text = "${fmt(ui.totalVolume)} кг"
        binding.statSets.text = "${ui.totalSets}"
        binding.statDays.text = "${ui.trainedDays}"

        // Последние подходы
        binding.recentList.removeAllViews()
        for (s in ui.recentSets) {
            val line = layoutInflater.inflate(R.layout.item_recent_set, binding.recentList, false) as TextView
            line.text = "${Dates.dayMonth(s.date)} — ${fmt(s.weightKg)} кг × ${s.reps}" +
                (if (s.isDropset) "  (дропсет)" else "")
            binding.recentList.addView(line)
        }
        binding.recentEmpty.visibility = if (ui.recentSets.isEmpty()) View.VISIBLE else View.GONE

        // Таблица процентов
        binding.pctList.removeAllViews()
        if (ui.current1Rm > 0) {
            for (pct in com.chepkall.gymlog.stats.OneRm.percentTable) {
                val w = com.chepkall.gymlog.stats.OneRm.weightForPercent(ui.current1Rm, pct)
                val row = layoutInflater.inflate(R.layout.item_pct_row, binding.pctList, false) as TextView
                row.text = "$pct%  →  ${fmt(w)} кг"
                binding.pctList.addView(row)
            }
        }
    }

    private fun fmt(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.ROOT, "%.1f", v)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}