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

import com.google.gson.annotations.SerializedName

data class Account(
    @SerializedName("id") val id: String = java.util.UUID.randomUUID().toString(),
    @SerializedName("golden_key") val goldenKey: String,
    @SerializedName("phpsessid") val phpSessionId: String = "",
    @SerializedName("username") val username: String = "",
    @SerializedName("user_id") val userId: String = "",
    @SerializedName("avatar_url") val avatarUrl: String = "",
    @SerializedName("is_active") val isActive: Boolean = false,
    @SerializedName("added_at") val addedAt: Long = System.currentTimeMillis(),

    @SerializedName("run_in_background") val runInBackground: Boolean = false,

    @SerializedName("proxy_type") val proxyType: String? = "",
    @SerializedName("proxy_host") val proxyHost: String? = "",
    @SerializedName("proxy_port") val proxyPort: Int? = 0,
    @SerializedName("proxy_username") val proxyUsername: String? = "",
    @SerializedName("proxy_password") val proxyPassword: String? = ""
)

data class AccountsData(
    @SerializedName("accounts") val accounts: List<Account> = emptyList(),
    @SerializedName("active_account_id") val activeAccountId: String? = null
)

data class AutoTicketSettings(
    @SerializedName("enabled") val enabled: Boolean = false,
    @SerializedName("order_age_hours") val orderAgeHours: Int = 24,
    @SerializedName("max_orders_per_ticket") val maxOrdersPerTicket: Int = 10,
    @SerializedName("daily_limit") val dailyLimit: Int = 3,
    @SerializedName("auto_interval_hours") val autoIntervalHours: Int = 12,
    @SerializedName("sent_order_ids") val sentOrderIds: List<String> = emptyList(),
    @SerializedName("tickets_today") val ticketsToday: Int = 0,
    @SerializedName("today_date") val todayDate: String = "",
    @SerializedName("last_run_at") val lastRunAt: Long = 0L
)

data class AutoTicketResult(
    val ticketsCreated: Int,
    val ordersProcessed: Int,
    val errorMessage: String?
)

data class OrderReminderSettings(
    @SerializedName("enabled") val enabled: Boolean = false,
    @SerializedName("delay_hours") val delayHours: Int = 4,
    @SerializedName("message") val message: String =
        "⚠️ \$username, Вы забыли подтвердить заказ.\n\n⚡ Заказ выполнен. Пожалуйста, зайдите в раздел «Покупки», выберите его в списке и нажмите кнопку «Подтвердить выполнение заказа».\n\n⭐ Спасибо."
)

data class PendingOrderReminder(
    @SerializedName("order_id") val orderId: String,
    @SerializedName("chat_id") val chatId: String,
    @SerializedName("buyer_name") val buyerName: String,
    @SerializedName("placed_at") val placedAt: Long,
    @SerializedName("remind_at") val remindAt: Long
)