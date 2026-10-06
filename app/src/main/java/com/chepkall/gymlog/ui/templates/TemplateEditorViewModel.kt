package com.chepkall.gymlog.ui.templates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chepkall.gymlog.data.Repo
import com.chepkall.gymlog.data.TemplateExercise
import com.chepkall.gymlog.data.WorkoutTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Строка редактора шаблона (в памяти до сохранения). */
data class TemplateRow(
    val exerciseId: Long,
    val nameSnapshot: String,
    val primaryMuscle: String,
    val secondaryMuscles: String,
    val planSets: Int,
    val planRepsMin: Int,
    val planRepsMax: Int,
)

class TemplateEditorViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = Repo.get(app).dao

    private val _templateId = MutableStateFlow(0L)
    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name
    private val _rows = MutableStateFlow<List<TemplateRow>>(emptyList())
    val rows: StateFlow<List<TemplateRow>> = _rows
    private val _boundId = MutableStateFlow(-1L)

    fun bind(templateId: Long) {
        if (_boundId.value == templateId) return
        _boundId.value = templateId
        // Важно: id фиксируем сразу, иначе «Сохранить» создаст копию вместо обновления
        _templateId.value = templateId
        viewModelScope.launch {
            if (templateId > 0L) {
                val t = dao.templateById(templateId) ?: return@launch
                _name.value = t.name
                _rows.value = dao.exercisesForTemplate(t.id).map {
                    TemplateRow(it.exerciseId, it.nameSnapshot, it.primaryMuscle, it.secondaryMuscles, it.planSets, it.planRepsMin, it.planRepsMax)
                }
            }
        }
    }

    fun rename(name: String) { _name.value = name }

    fun addExercise(exerciseId: Long) {
        viewModelScope.launch {
            val e = dao.exerciseById(exerciseId) ?: return@launch
            _rows.value = _rows.value + TemplateRow(
                exerciseId = e.id, nameSnapshot = e.name,
                primaryMuscle = e.primaryMuscle, secondaryMuscles = e.secondaryMuscles,
                planSets = 3, planRepsMin = 10, planRepsMax = 10,
            )
        }
    }

    fun remove(position: Int) {
        _rows.value = _rows.value.filterIndexed { i, _ -> i != position }
    }

    fun move(position: Int, delta: Int) {
        val list = _rows.value.toMutableList()
        val to = position + delta
        if (position !in list.indices || to !in list.indices) return
        val r = list.removeAt(position)
        list.add(to, r)
        _rows.value = list
    }

    fun updateParams(position: Int, sets: Int, repsMin: Int, repsMax: Int) {
        val list = _rows.value.toMutableList()
        if (position !in list.indices) return
        val r = list[position]
        list[position] = r.copy(planSets = sets, planRepsMin = repsMin, planRepsMax = repsMax)
        _rows.value = list
    }

    /** Сохранить: обновить текущий шаблон (updateExisting) или создать копию. */
    fun save(updateExisting: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            val id = if (updateExisting) _templateId.value else 0L
            val name = _name.value.trim().ifBlank { "Шаблон" }
            val rows = _rows.value
            val templateId: Long
            if (id > 0L) {
                val t = dao.templateById(id)
                if (t != null) {
                    dao.update(t.copy(name = name))
                    dao.deleteExercisesForTemplate(t.id)
                    templateId = t.id
                } else templateId = dao.insert(WorkoutTemplate(name = name))
            } else {
                templateId = dao.insert(WorkoutTemplate(name = name))
            }
            rows.forEachIndexed { idx, r ->
                dao.insertTemplateExercise(
                    TemplateExercise(
                        templateId = templateId,
                        exerciseId = r.exerciseId,
                        nameSnapshot = r.nameSnapshot,
                        primaryMuscle = r.primaryMuscle,
                        secondaryMuscles = r.secondaryMuscles,
                        orderPos = idx,
                        planSets = r.planSets,
                        planRepsMin = r.planRepsMin,
                        planRepsMax = r.planRepsMax,
                    ),
                )
            }
            _templateId.value = templateId
            onDone()
        }
    }
}