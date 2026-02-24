package ru.allisighs.funpaytools

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*

class WatchdogDaemon : Service() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            while (isActive) {
                
                val restartIntent = Intent(this@WatchdogDaemon, PhoenixReceiver::class.java).apply {
                    action = "ru.allisighs.funpaytools.RESTART_SERVICE"
                }
                sendBroadcast(restartIntent)

                
                delay(30000)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        val restartIntent = Intent("ru.allisighs.funpaytools.RESTART_DAEMON")
        restartIntent.setPackage(packageName)
        sendBroadcast(restartIntent)
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartIntent = Intent("ru.allisighs.funpaytools.RESTART_DAEMON")
        restartIntent.setPackage(packageName)
        sendBroadcast(restartIntent)
        super.onTaskRemoved(rootIntent)
    }
}