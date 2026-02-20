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
    @SerializedName("ratingMin") val ratingMin: Int = 5,
    @SerializedName("ignoreZeroRating") val ignoreZeroRating: Boolean = false,
    @SerializedName("positionMax") val positionMax: Int = 50,
    @SerializedName("fastPriceCheck") val fastPriceCheck: Boolean = false,
    @SerializedName("updateInterval") val updateInterval: Int = 60 // в сек
)

data class DumperSettings(
    @SerializedName("enabled") val enabled: Boolean = false,
    @SerializedName("lots") val lots: List<DumperLotConfig> = emptyList()
)