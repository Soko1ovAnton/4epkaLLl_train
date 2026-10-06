package com.chepkall.gymlog.ui.templates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chepkall.gymlog.data.Repo
import com.chepkall.gymlog.data.WorkoutTemplate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplateUi(
    val id: Long,
    val name: String,
    val exerciseCount: Int,
)

class TemplatesViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = Repo.get(app).dao

    val ui: StateFlow<List<TemplateUi>> =
        combine(dao.observeAllTemplates(), dao.observeCounts()) { list, counts ->
            val byId = counts.associate { it.templateId to it.cnt }
            list.map { TemplateUi(it.id, it.name, byId[it.id] ?: 0) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(t: WorkoutTemplate) {
        viewModelScope.launch {
            dao.delete(t)
            dao.deleteExercisesForTemplate(t.id)
        }
    }
}
