/*
 * Copyright (c) 2026 XaviersDev (AlliSighs). All rights reserved.
 *
 * This code is proprietary and confidential.
 * Modification, distribution, or use of this source code
 * without express written permission from the author is strictly prohibited.
 *
 * Decompiling, reverse engineering, or creating derivative works
 * based on this software is a violation of copyright law.
 */

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