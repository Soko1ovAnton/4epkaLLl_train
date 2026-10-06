package com.chepkall.gymlog.data

import android.content.Context

class Repo private constructor(context: Context) {
    val dao: GymDao = GymDatabase.get(context).gymDao()
    val cal: CalendarQueries = GymDatabase.get(context).calendarQueries()

    companion object {
        @Volatile private var instance: Repo? = null

        fun get(context: Context): Repo =
            instance ?: synchronized(this) {
                instance ?: Repo(context.applicationContext).also { instance = it }
            }
    }
}