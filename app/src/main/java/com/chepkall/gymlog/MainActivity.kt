package com.chepkall.gymlog

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import com.chepkall.gymlog.databinding.ActivityMainBinding
import com.chepkall.gymlog.ui.calendar.CalendarFragment
import com.chepkall.gymlog.ui.stats.StatsFragment
import com.chepkall.gymlog.ui.library.LibraryFragment
import com.chepkall.gymlog.ui.templates.TemplatesFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Явный трекинг текущей вкладки: findFragmentById возвращает "первый попавшийся"
    // фрагмент, а не видимый, из-за чего быстрые тапы ломали переключение.
    private var currentTag: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Контент не должен заходить под статус-бар (шторку): отступ сверху
        // = высоте системных окон. Навбар обрабатывается сам (edge-to-edge M3).
        ViewCompat.setOnApplyWindowInsetsListener(binding.container) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_calendar -> { show(CalendarFragment.TAG) { CalendarFragment() }; true }
                R.id.nav_stats -> { show(StatsFragment.TAG) { StatsFragment() }; true }
                R.id.nav_library -> { show(LibraryFragment.TAG) { LibraryFragment() }; true }
                R.id.nav_templates -> { show(TemplatesFragment.TAG) { TemplatesFragment() }; true }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.nav_calendar
        } else {
            currentTag = savedInstanceState.getString(KEY_TAB)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TAB, currentTag)
    }

    private fun show(tag: String, create: () -> Fragment) {
        // Один и тот же тап повторно — ничего не делаем.
        if (currentTag == tag) return

        val fm = supportFragmentManager

        // Экран тренировки лежит на бэкстеке транзакцией replace, которая УДАЛЯЛА
        // фрагменты вкладок. Без сброса стека вкладка после такой транзакции
        // оказывается «не добавленной» и показывается чёрный экран.
        while (fm.popBackStackImmediate()) { /* закрываем сессию */ }

        // Одна СИНХРОННАЯ транзакция (commitNow): спрятать все, показать/добавить
        // нужную. Ранее было две async-транзакции (hide + show), они перемешивались
        // при быстрых переключениях и навбар залипал на одной вкладке. commitNow
        // выполняется немедленно, поэтому два быстрых тапа не могут интерливнуться.
        fm.commitNow {
            fm.fragments.forEach { if (it.isAdded && it.tag != tag) hide(it) }
            val existing = fm.findFragmentByTag(tag)
            if (existing != null && existing.isAdded) show(existing) else add(R.id.container, create(), tag)
        }
        currentTag = tag
    }

    private companion object {
        const val KEY_TAB = "current_tab"
    }
}