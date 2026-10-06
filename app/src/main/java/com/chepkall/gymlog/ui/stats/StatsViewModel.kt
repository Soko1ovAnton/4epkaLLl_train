package com.chepkall.gymlog.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chepkall.gymlog.data.Exercise
import com.chepkall.gymlog.data.ExerciseSet
import com.chepkall.gymlog.data.Repo
import com.chepkall.gymlog.stats.OneRm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Точка на графике прогресса. */
data class ProgressPoint(val epochDay: Long, val oneRm: Double, val topWeight: Double)

data class StatsUi(
    val exercises: List<Exercise> = emptyList(),
    val selectedId: Long = 0L,
    val points: List<ProgressPoint> = emptyList(),
    val current1Rm: Double = 0.0,
    val prev1Rm: Double = 0.0,
    val bestSet: ExerciseSet? = null,
    val totalVolume: Double = 0.0,
    val totalSets: Int = 0,
    val trainedDays: Int = 0,
    val recentSets: List<ExerciseSet> = emptyList(),
)

class StatsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repo.get(app)

    private val _ui = MutableStateFlow(StatsUi())
    val ui: StateFlow<StatsUi> = _ui

    init {
        viewModelScope.launch {
            repo.dao.observeAllSets().collect { recompute() }
        }
        viewModelScope.launch {
            repo.dao.observeAllExercises().collect { list ->
                _ui.value = _ui.value.copy(exercises = list)
                if (_ui.value.selectedId == 0L && list.isNotEmpty()) {
                    select(list.first().id)
                } else {
                    recompute()
                }
            }
        }
    }

    fun select(exerciseId: Long) {
        if (_ui.value.selectedId == exerciseId) return
        _ui.value = _ui.value.copy(selectedId = exerciseId)
        viewModelScope.launch { recompute() }
    }

    /** Выбор упражнения по названию (из подсказок строки поиска). */
    fun selectByName(name: String) {
        _ui.value.exercises.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { select(it.id) }
    }

    private suspend fun recompute() {
        val exId = _ui.value.selectedId
        val daoSets = if (exId == 0L) emptyList() else repo.dao.historyForExercise(exId)
        val days = daoSets.groupBy { it.date }.toSortedMap()

        val points = days.map { (day, sets) ->
            val best = sets.maxOfOrNull { OneRm.bestOfSet(it) } ?: 0.0
            ProgressPoint(day, round(best), sets.maxOfOrNull { it.weightKg } ?: 0.0)
        }
        val current = points.lastOrNull()?.oneRm ?: 0.0
        val prev = if (points.size >= 2) points[points.size - 2].oneRm else current
        val best = daoSets.maxByOrNull { OneRm.bestOfSet(it) }

        // Общая активность
        val allSets = repo.dao.allSets()
        val vol = allSets.sumOf { s: com.chepkall.gymlog.data.ExerciseSet -> s.volume() }
        val trainedDays = repo.dao.trainedDaysCount()

        _ui.value = _ui.value.copy(
            points = points,
            current1Rm = current,
            prev1Rm = prev,
            bestSet = best,
            totalVolume = vol,
            totalSets = allSets.size,
            trainedDays = trainedDays,
            recentSets = daoSets.takeLast(12).reversed(),
        )
    }

    private fun round(v: Double) = (v * 10).toInt() / 10.0
}