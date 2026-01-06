package ru.allisighs.funpaytools

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.*

class FunPayService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var repository: FunPayRepository

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repository = FunPayRepository(this)
        createNotificationChannel()
        startForeground(1, createNotification("FunPay Tools работает"))
        LogManager.addLog("✅ SERVICE: Запущен")
        startWorkLoop()
    }

    private fun startWorkLoop() {
        serviceScope.launch {
            LogManager.addLog("🛠️ SERVICE: Цикл started")
            while (isActive) {
                if (repository.hasAuth()) {
                    try {
                        val chats = repository.getChats()

                        repository.checkAutoResponse(chats)
                        repository.checkGreetings(chats)
                        repository.raiseAllLots()

                        val unread = chats.count { it.isUnread }
                        val status = if (unread > 0) "Непрочитанных: $unread" else "Ожидание..."
                        updateNotification(status)

                    } catch (e: Exception) {
                        LogManager.addLog("❌ SERVICE CRASH: ${e.message}")
                        e.printStackTrace()
                    }
                }
                delay(15000)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("fp_service", "FunPay Tools Core", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "fp_service")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("FunPay Tools")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, createNotification(text))
    }

    override fun onDestroy() {
        serviceScope.cancel()
        LogManager.addLog("🛑 SERVICE: Остановлен")
        super.onDestroy()
    }
}