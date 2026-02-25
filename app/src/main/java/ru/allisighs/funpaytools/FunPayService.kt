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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.app.AlarmManager
import android.content.ContentResolver
import android.os.SystemClock
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.*

class FunPayService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: FunPayRepository
    private var wakeLock: PowerManager.WakeLock? = null
    private val processedMessagesCache = mutableMapOf<String, String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repository = FunPayRepository(this)
        WatchdogWorker.start(this)
        try {
            startService(Intent(this, WatchdogDaemon::class.java))
        } catch (e: Exception) {}

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "FunPayTools::ServiceWakeLock"
            )
            wakeLock?.acquire(24 * 60 * 60 * 1000L)
            LogManager.addLog("🔋 WakeLock активирован")
        } catch (e: Exception) {
            LogManager.addLog("❌ Ошибка WakeLock: ${e.message}")
        }

        createNotificationChannel()
        startForeground(1, createNotification("FunPay Tools работает"))
        LogManager.addLog("✅ SERVICE: Запущен")
        startWorkLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startWorkLoop() {
        serviceScope.launch {
            LogManager.addLog("🛠️ SERVICE: Цикл started")
            var lastCleanup = 0L
            var lastWidgetUpdate = 0L
            while (isActive) {
                if (!isNetworkAvailable(this@FunPayService)) {
                    LogManager.addLogDebug("📶 Нет интернета. Сплю...")
                    updateNotification("Ожидание сети...")
                    delay(25000)
                    continue
                }
                if (repository.hasAuth()) {
                    try {
                        val chats = repository.getChats()
                        val busySettings = ChatFolderManager.getBusyMode(this@FunPayService)

                        if (busySettings.enabled) {
                            repository.checkBusyModeReplies(chats, busySettings)
                            if (busySettings.keepRaise) repository.raiseAllLots()
                            if (busySettings.keepAutoResponse) repository.checkAutoResponse(chats)
                            if (busySettings.keepGreeting) repository.checkGreetings(chats)
                        } else {
                            resolveNewUnreadChats(chats)
                            repository.checkAutoResponse(chats)
                            repository.checkGreetings(chats)
                            repository.raiseAllLots()
                            repository.checkOrderConfirmations(chats)
                            repository.checkAutoRefund(chats)
                            repository.checkReviewReplies(chats)
                            repository.runDumperCycle()
                        }

                        if (repository.getSetting("push_notifications")) {
                            checkPushNotifications(chats)
                        }

                        if (System.currentTimeMillis() - lastCleanup > 24 * 60 * 60 * 1000L) {
                            repository.cleanupOldProcessedEvents()
                            lastCleanup = System.currentTimeMillis()
                        }


                        if (System.currentTimeMillis() - lastWidgetUpdate > 5 * 60 * 1000L) {
                            try {
                                val profile = repository.getSelfProfile()
                                if (profile != null) {
                                    WidgetManager.saveProfileCache(this@FunPayService, profile)
                                }
                                WidgetManager.updateAllWidgets(this@FunPayService)
                            } catch (e: Exception) {
                                LogManager.addLogDebug("⚠️ Widget update error: ${e.message}")
                            }
                            lastWidgetUpdate = System.currentTimeMillis()
                        }

                        val unread = chats.count { it.isUnread }
                        val status = if (unread > 0) "Непрочитанных: $unread" else "Работает"
                        updateNotification(status)

                    } catch (e: Exception) {
                        LogManager.addLog("❌ SERVICE CRASH: ${e.message}")
                        e.printStackTrace()
                    }
                }
                val calendar = java.util.Calendar.getInstance()
                val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                val isNight = hour in 2..6

                val delayTime = if (isNight) {
                    15000L
                } else {
                    6513L
                }

                delay(delayTime)
            }
        }
    }

    private suspend fun resolveNewUnreadChats(chats: List<ChatItem>) {
        val (_, userId) = repository.getCsrfAndId() ?: return

        val newlyUnread = chats.filter { chat ->
            chat.isUnread &&
                    !FunPayRepository.lastOutgoingMessages.containsKey(chat.id) &&
                    !repository.knownUnreadChats.contains(chat.id)
        }

        for (chat in newlyUnread) {
            
            
            
            repository.knownUnreadChats.add(chat.id)
        }


        val unreadIds = chats.filter { it.isUnread }.map { it.id }.toSet()
        repository.knownUnreadChats.retainAll(unreadIds)
    }

    private fun checkPushNotifications(chats: List<ChatItem>) {
        for (chat in chats) {
            if (chat.isUnread) {
                val lastMsg = chat.lastMessage


                val lastSelf = FunPayRepository.lastOutgoingMessages[chat.id]

                if (lastSelf != null) {

                    if (lastSelf == "__image__") {
                        FunPayRepository.lastOutgoingMessages.remove(chat.id)
                        continue
                    }

                    val cleanIncoming = lastMsg.replace("...", "").trim().lowercase()
                    val cleanSelf = lastSelf.trim().lowercase()
                    if (cleanSelf.contains(cleanIncoming) ||
                        cleanIncoming.contains(cleanSelf) ||
                        cleanSelf.startsWith(cleanIncoming) ||
                        cleanIncoming.startsWith(cleanSelf)) {
                        continue
                    }
                }

                val cachedMsg = processedMessagesCache[chat.id]

                if (cachedMsg != lastMsg) {
                    sendChatNotification(chat)
                    processedMessagesCache[chat.id] = lastMsg
                }
            } else {
                processedMessagesCache.remove(chat.id)
            }
        }
    }

    private fun sendChatNotification(chat: ChatItem) {
        val notificationId = chat.id.hashCode()
        val channelId = "fp_push_channel_v4"

        val replyLabel = "Ответить"
        val remoteInput = RemoteInput.Builder("key_text_reply")
            .setLabel(replyLabel)
            .build()

        val replyIntent = Intent(this, ReplyReceiver::class.java).apply {
            putExtra("chat_id", chat.id)
            putExtra("notification_id", notificationId)
        }

        val replyPendingIntent = PendingIntent.getBroadcast(
            this,
            notificationId,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val action = Notification.Action.Builder(
            android.R.drawable.ic_menu_send,
            replyLabel,
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("chat_id", chat.id)
            putExtra("chat_username", chat.username)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = builder
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(chat.username)
            .setContentText(chat.lastMessage)
            .setStyle(Notification.BigTextStyle().bigText(chat.lastMessage))
            .setContentIntent(pendingIntent)
            .addAction(action)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val serviceChannel = NotificationChannel(
                "fp_service",
                "FunPay Tools Core",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(serviceChannel)

            val soundUri = Uri.parse(
                ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/raw/funpay_push"
            )
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val pushChannel = NotificationChannel(
                "fp_push_channel_v4",
                "Сообщения",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(soundUri, audioAttributes)
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
            }

            manager.deleteNotificationChannel("fp_push_channel_v4")
            manager.createNotificationChannel(pushChannel)
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

        val notification = builder
            .setContentTitle("FunPay Tools")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR

        return notification
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, createNotification(text))
    }


    override fun onDestroy() {
        serviceScope.cancel()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) { }

        LogManager.addLog("🛑 SERVICE: Убит системой, запускаю дефибриллятор")
        if (repository.getSetting("auto_start_on_boot")) {

            val restartIntent = Intent(applicationContext, PhoenixReceiver::class.java).apply {
                action = "ru.allisighs.funpaytools.RESTART_SERVICE"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this, 2, restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                android.os.SystemClock.elapsedRealtime() + 3000,
                pendingIntent
            )
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        LogManager.addLog("⚠️ SERVICE: Смахнули из недавних. Воскрешаюсь!")

        if (repository.getSetting("auto_start_on_boot")) {
            val restartIntent = Intent(applicationContext, PhoenixReceiver::class.java).apply {
                action = "ru.allisighs.funpaytools.RESTART_SERVICE"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this, 1, restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                android.os.SystemClock.elapsedRealtime() + 3000,
                pendingIntent
            )
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return activeNetwork.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }

}