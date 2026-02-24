package ru.allisighs.funpaytools

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import android.os.Build

class ZombieJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        val repo = FunPayRepository(this)
        if (repo.hasAuth() && repo.getSetting("auto_start_on_boot")) {
            try {
                val serviceIntent = Intent(this, FunPayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }

                
                startService(Intent(this, WatchdogDaemon::class.java))
            } catch (e: Exception) {}
        }
        return false 
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true 
    }
}