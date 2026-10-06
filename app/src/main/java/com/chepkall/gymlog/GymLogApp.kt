package com.chepkall.gymlog

import android.app.Application
import com.chepkall.gymlog.data.Repo
import com.chepkall.gymlog.seed.SeedData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GymLogApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val dao = Repo.get(this@GymLogApp).dao
            if (dao.count() == 0) {
                dao.insertAll(SeedData.exercises())
            }
        }
    }
}