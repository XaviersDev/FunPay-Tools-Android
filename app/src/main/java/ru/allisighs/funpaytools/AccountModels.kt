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
    @SerializedName("id")
    val id: String = java.util.UUID.randomUUID().toString(),

    @SerializedName("golden_key")
    val goldenKey: String,

    @SerializedName("phpsessid")
    val phpSessionId: String = "",

    @SerializedName("username")
    val username: String = "",

    @SerializedName("user_id")
    val userId: String = "",

    @SerializedName("avatar_url")
    val avatarUrl: String = "",

    @SerializedName("is_active")
    val isActive: Boolean = false,

    @SerializedName("added_at")
    val addedAt: Long = System.currentTimeMillis()
)

data class AccountsData(
    @SerializedName("accounts")
    val accounts: List<Account> = emptyList(),

    @SerializedName("active_account_id")
    val activeAccountId: String? = null
)

data class DumperLotConfig(
    @SerializedName("id") val id: String = java.util.UUID.randomUUID().toString(),
    @SerializedName("enabled") val enabled: Boolean = false,
    @SerializedName("lotId") val lotId: String = "",
    @SerializedName("categoryId") val categoryId: String = "",
    @SerializedName("keywords") val keywords: String = "",
    @SerializedName("priceMin") val priceMin: Double = 1.0,
    @SerializedName("priceMax") val priceMax: Double = 99999.0,
    @SerializedName("priceStep") val priceStep: Double = 5.0,
    @SerializedName("priceDivider") val priceDivider: Double = 0.0,
    @SerializedName("ratingMin") val ratingMin: Int = 0,
    @SerializedName("ignoreZeroRating") val ignoreZeroRating: Boolean = false,
    @SerializedName("positionMax") val positionMax: Int = 50,
    @SerializedName("fastPriceCheck") val fastPriceCheck: Boolean = false,
    @SerializedName("autoRaise") val autoRaise: Boolean = true,
    @SerializedName("aggressiveMode") val aggressiveMode: Boolean = false,
    @SerializedName("updateInterval") val updateInterval: Int = 60,
    @SerializedName("matchAllMethods") val matchAllMethods: Boolean = true
)

data class DumperSettings(
    @SerializedName("enabled") val enabled: Boolean = false,
    @SerializedName("lots") val lots: List<DumperLotConfig> = emptyList()
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