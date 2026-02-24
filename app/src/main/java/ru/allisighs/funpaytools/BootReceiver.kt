/*
 *
 *  * Copyright (c) 2026 XaviersDev (AlliSighs). All rights reserved.
 *  *
 *  * This code is proprietary. Modification, distribution, or use
 *  * of this file without express written permission is strictly prohibited.
 *  * Unauthorized use will be prosecuted.
 *
 */

package ru.allisighs.funpaytools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            if (!userManager.isUserUnlocked) return
        }

        val action = intent.action ?: return

        
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_USER_PRESENT || 
            action == Intent.ACTION_POWER_CONNECTED || 
            action == Intent.ACTION_POWER_DISCONNECTED 
        ) {
            try {
                val repository = FunPayRepository(context)

                if (repository.hasAuth() && repository.getSetting("auto_start_on_boot")) {

                    
                    if (action.contains("BOOT")) {
                        LogManager.addLog("🔄 Автозапуск после перезагрузки")
                    } else {
                        LogManager.addLogDebug("⚡ Системный триггер ($action) разбудил сервис")
                    }

                    
                    try {
                        context.startService(Intent(context, WatchdogDaemon::class.java))
                    } catch (e: Exception) {}

                    
                    
                    ServiceStarter.startSafely(context)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}