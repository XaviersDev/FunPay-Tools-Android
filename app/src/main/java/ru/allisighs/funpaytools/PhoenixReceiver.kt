package ru.allisighs.funpaytools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class PhoenixReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "ru.allisighs.funpaytools.RESTART_SERVICE" ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {

            val repository = FunPayRepository(context)
            if (repository.hasAuth() && repository.getSetting("auto_start_on_boot")) {
                val serviceIntent = Intent(context, FunPayService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}