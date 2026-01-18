package ru.allisighs.funpaytools

import android.app.NotificationManager
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence("key_text_reply")?.toString()
        val chatId = intent.getStringExtra("chat_id")
        val notificationId = intent.getIntExtra("notification_id", 0)

        if (replyText != null && chatId != null) {
            val repository = FunPayRepository(context)
            val manager = context.getSystemService(NotificationManager::class.java)

            CoroutineScope(Dispatchers.IO).launch {
                val success = repository.sendMessage(chatId, replyText)
                if (success) {
                    manager.cancel(notificationId)
                }
            }
        }
    }
}