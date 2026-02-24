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

import android.content.Context
import android.content.Intent
import android.os.Build

object ServiceStarter {
    fun startSafely(context: Context) {
        val serviceIntent = Intent(context, FunPayService::class.java)
        try {
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            
            LogManager.addLogDebug("⚠️ Прямой запуск заблокирован системой. Обходим через Worker...")
            
            WatchdogWorker.startOneTime(context)
        }
    }
}