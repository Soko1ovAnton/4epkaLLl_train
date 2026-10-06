package com.chepkall.gymlog.ui.workout

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.chepkall.gymlog.R
import com.chepkall.gymlog.data.ExerciseSet
import com.chepkall.gymlog.databinding.DialogAddSetBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Диалог добавления/редактирования подхода: вес, повторы, отказ, дропсет. */
class AddSetDialog : DialogFragment() {

    companion object {
        const val TAG = "AddSetDialog"

        fun newInstance(
            exerciseId: Long,
            exerciseName: String? = null,
            existing: ExerciseSet? = null,
            planRepsMin: Int = 0,
            planRepsMax: Int = 0,
        ) = AddSetDialog().apply {
            arguments = Bundle().apply {
                putLong("ex_id", exerciseId)
                putString("ex_name", exerciseName)
                putInt("plan_min", planRepsMin)
                putInt("plan_max", planRepsMax)
                if (existing != null) {
                    putLong("set_id", existing.id)
                    putDouble("w", existing.weightKg)
                    putInt("r", existing.reps)
                    putBoolean("fail", existing.isFailure)
                    putString("drop", existing.dropset)
                }
            }
        }
    }

    fun interface Listener {
        fun onConfirmed(weight: Double, reps: Int, dropsetJson: String?, rpe: Double?, failure: Boolean)
    }

    private var listener: Listener? = null
    private var deleteListener: ((Long) -> Unit)? = null
    private lateinit var ui: DialogAddSetBinding
    private val dropStages = mutableListOf<Pair<EditText, EditText>>()

    fun setAddSetListener(l: Listener) { listener = l }

    fun setOnDeleteListener(l: (Long) -> Unit) { deleteListener = l }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        ui = DialogAddSetBinding.inflate(layoutInflater)

        val editMode = arguments?.containsKey("set_id") == true
        if (editMode) {
            ui.inWeight.setText(fmt(arguments!!.getDouble("w")))
            ui.inReps.setText(arguments!!.getInt("r").toString())
            ui.chkFailure.isChecked = arguments!!.getBoolean("fail")
            val drop = arguments?.getString("drop")
            if (!drop.isNullOrBlank()) {
                ui.swDropset.isChecked = true
                ExerciseSet.parseDropset(drop).forEach { addDropStage(it.weight, it.reps) }
            }
            // Удаление заполненного подхода
            val setId = arguments?.getLong("set_id") ?: 0L
            ui.btnDeleteSet.visibility = View.VISIBLE
            ui.btnDeleteSet.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Удалить подход?")
                    .setMessage("Запись об этом подходе будет удалена.")
                    .setPositiveButton("Удалить") { _, _ ->
                        deleteListener?.invoke(setId)
                        dismiss()
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
        } else {
            // План из шаблона: повторы подставляем сразу — останется ввести только вес
            val planMin = arguments?.getInt("plan_min", 0) ?: 0
            val planMax = arguments?.getInt("plan_max", 0) ?: 0
            if (planMin > 0) {
                ui.inReps.setText(planMin.toString())
                ui.repsLayout.hint = "Повторы (план ${com.chepkall.gymlog.util.Plans.repsLabel(planMin, planMax)})"
            }
            prefillLastTime(arguments?.getLong("ex_id") ?: 0L)
        }

        ui.swDropset.setOnCheckedChangeListener { _, checked ->
            ui.dropsetContainer.visibility = if (checked) View.VISIBLE else View.GONE
            ui.btnAddDropStage.visibility = if (checked) View.VISIBLE else View.GONE
            if (checked && dropStages.isEmpty()) addDropStage()
        }
        ui.btnAddDropStage.setOnClickListener { addDropStage() }

        val exName = arguments?.getString("ex_name")
        val baseTitle = if (editMode) "Изменить подход" else "Новый подход"
        val title = if (exName.isNullOrBlank()) baseTitle else "$exName · ${baseTitle.lowercase()}"

        return MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(ui.root)
            .setPositiveButton("Сохранить", null) // перехватим ниже
            .setNegativeButton("Отмена", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(Dialog.BUTTON_POSITIVE).setOnClickListener { submit() }
                }
            }
    }

    private fun prefillLastTime(exerciseId: Long) {
        val session = parentFragment as? WorkoutSessionFragment ?: return
        val wid = session.vm.workoutId.value
        if (wid == 0L || exerciseId == 0L) return
        lifecycleScope.launch {
            val last = session.vm.lastTimeFor(exerciseId, wid) ?: return@launch
            val lastSets = last.second
            if (lastSets.isEmpty()) return@launch
            val maxSet = lastSets.maxByOrNull { it.weightKg } ?: return@launch
            // Подсказки с прошлым результатом — на TextInputLayout, чтобы hint
            // реально отображался в полях (hint у EditText перехватывается layout'ом).
            // Ввод остаётся пустым — не надо стирать автоподстановку каждый раз.
            val planMin = arguments?.getInt("plan_min", 0) ?: 0
            val planMax = arguments?.getInt("plan_max", 0) ?: 0
            val planPart = if (planMin > 0) "план ${com.chepkall.gymlog.util.Plans.repsLabel(planMin, planMax)} · " else ""
            ui.weightLayout.hint = "Вес, кг (в прошлый раз ${fmt(maxSet.weightKg)})"
            ui.repsLayout.hint = "Повторы ($planPart в прошлый раз ${maxSet.reps})"
            // Кнопка подтягивания прошлого раза по тапу
            ui.btnLastTime.text = "⤓ Прошлый раз: ${fmt(maxSet.weightKg)}×${maxSet.reps}"
            ui.btnLastTime.visibility = View.VISIBLE
            ui.btnLastTime.setOnClickListener {
                ui.inWeight.setText(fmt(maxSet.weightKg))
                ui.inReps.setText(maxSet.reps.toString())
                ui.inWeight.error = null
                ui.inReps.error = null
            }
        }
    }

    private fun addDropStage(weight: Double? = null, reps: Int? = null) {
        val v = layoutInflater.inflate(R.layout.item_drop_stage, ui.dropsetContainer, false)
        val wEt = v.findViewById<EditText>(R.id.drop_weight)
        val rEt = v.findViewById<EditText>(R.id.drop_reps)
        weight?.let { wEt.setText(fmt(it)) }
        reps?.let { rEt.setText(it.toString()) }
        v.findViewById<View>(R.id.drop_remove).setOnClickListener {
            ui.dropsetContainer.removeView(v)
            dropStages.remove(wEt to rEt)
        }
        ui.dropsetContainer.addView(v)
        dropStages.add(wEt to rEt)
    }

    private fun submit() {
        val w = ui.inWeight.text.toString().replace(",", ".").toDoubleOrNull()
        val r = ui.inReps.text.toString().toIntOrNull()
        if (w == null || w <= 0 || r == null || r <= 0) {
            ui.inWeight.error = if (w == null || w <= 0) "Укажи вес" else null
            ui.inReps.error = if (r == null || r <= 0) "Укажи повторы" else null
            return
        }
        val dropJson: String? = if (ui.swDropset.isChecked) {
            buildJson(dropStages)
        } else null
        listener?.onConfirmed(w, r, dropJson, null, ui.chkFailure.isChecked)
        dismiss()
    }

    private fun buildJson(stages: List<Pair<EditText, EditText>>): String? {
        val arr = com.google.gson.JsonArray()
        var any = false
        for ((wEt, rEt) in stages) {
            val w = wEt.text.toString().replace(",", ".").toDoubleOrNull() ?: continue
            val r = rEt.text.toString().toIntOrNull() ?: continue
            if (w <= 0 || r <= 0) continue
            val o = com.google.gson.JsonObject()
            o.addProperty("weight", w)
            o.addProperty("reps", r)
            arr.add(o)
            any = true
        }
        return if (any) arr.toString() else null
    }

    private fun fmt(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.ROOT, "%.1f", v)
}