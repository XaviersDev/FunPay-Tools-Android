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

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface FunPayApi {
    @GET("/")
    suspend fun getMainPage(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String
    ): Response<ResponseBody>

    @GET
    suspend fun getChatHistory(
        @Url url: String,
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String,
        @Header("X-Requested-With") xRequestedWith: String
    ): Response<ResponseBody>

    @GET
    suspend fun getChatPage(
        @Url url: String,
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String
    ): Response<ResponseBody>

    @GET("users/{id}/")
    suspend fun getUserProfile(
        @Path("id") userId: String,
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("runner/")
    suspend fun runnerGet(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String,
        @Header("X-Requested-With") xReq: String = "XMLHttpRequest",
        @Field("objects") objects: String,
        @Field("request") request: String,
        @Field("csrf_token") csrfToken: String
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("runner/")
    suspend fun runnerSend(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String,
        @Header("X-Requested-With") xReq: String = "XMLHttpRequest",
        @Field("objects") objects: String = "[]",
        @Field("request") request: String,
        @Field("csrf_token") csrfToken: String
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("lots/raise")
    suspend fun raiseLotInitial(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String,
        @Header("X-Requested-With") xReq: String = "XMLHttpRequest",
        @Field("game_id") gameId: Int,
        @Field("node_id") nodeId: Int
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("lots/raise")
    suspend fun raiseLotCommit(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String,
        @Header("X-Requested-With") xReq: String = "XMLHttpRequest",
        @Field("game_id") gameId: Int,
        @Field("node_id") nodeId: Int,
        @Field("node_ids[]") nodeIds: List<Int>
    ): Response<ResponseBody>


    @GET("orders/new")
    suspend fun getConfirmationPage(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String
    ): Response<ResponseBody>

    @GET("orders/{id}/")
    suspend fun getOrder(
        @Path("id") orderId: String,
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("orders/refund")
    suspend fun refundOrder(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String,
        @Header("X-Requested-With") xReq: String = "XMLHttpRequest",
        @Field("csrf_token") csrfToken: String,
        @Field("id") orderId: String
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("orders/review")
    suspend fun replyToReview(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String,
        @Header("X-Requested-With") xReq: String = "XMLHttpRequest",
        @Field("csrf_token") csrfToken: String,
        @Field("orderId") orderId: String,
        @Field("text") text: String,
        @Field("rating") rating: Int = 5,
        @Field("authorId") authorId: String
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("orders/reviewDelete")
    suspend fun deleteReviewReply(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String,
        @Header("X-Requested-With") xReq: String = "XMLHttpRequest",
        @Field("csrf_token") csrfToken: String,
        @Field("orderId") orderId: String,
        @Field("authorId") authorId: String
    ): Response<ResponseBody>

    @Multipart
    @POST("file/addChatImage")
    suspend fun uploadChatImage(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String,
        @Header("X-Requested-With") xReq: String = "XMLHttpRequest",
        @Part file: MultipartBody.Part,
        @Part("file_id") fileId: RequestBody
    ): Response<ResponseBody>

    @POST
    suspend fun rewriteText(
        @Url url: String = "https://fptools.onrender.com/api/ai",
        @Header("Content-Type") contentType: String = "application/json",
        @Header("Authorization") auth: String,
        @Body body: RequestBody
    ): Response<ResponseBody>

    

    @GET("lots/offer")
    suspend fun getLotPage(
        @Query("id") offerId: String,
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("orders/new")
    suspend fun postOrderNew(
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String,
        @Header("X-Requested-With") xReq: String = "XMLHttpRequest",
        @Header("Accept") accept: String = "application/json, text/javascript, */*; q=0.01",
        @Header("Referer") referer: String,
        @FieldMap fields: Map<String, String>
    ): Response<ResponseBody>
}

object GlobalProxyAuthenticator : java.net.Authenticator() {
    val proxyCredentials = java.util.concurrent.ConcurrentHashMap<String, java.net.PasswordAuthentication>()
    override fun getPasswordAuthentication(): java.net.PasswordAuthentication? {
        val host = requestingHost ?: requestingSite?.hostName ?: return null
        return proxyCredentials["$host:$requestingPort"]
    }
}

object ApiFactory {
    private var authenticatorSet = false

    fun createClient(account: Account?): okhttp3.OkHttpClient {
        val builder = okhttp3.OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .hostnameVerifier { _, _ -> true }

        val pHost = account?.proxyHost ?: ""
        val pPort = account?.proxyPort ?: 0
        if (account != null && pHost.isNotBlank() && pPort > 0) {
            try {
                val proxyType = if (account.proxyType == "SOCKS") java.net.Proxy.Type.SOCKS else java.net.Proxy.Type.HTTP
                val proxyAddress = java.net.InetSocketAddress(pHost, pPort)
                builder.proxy(java.net.Proxy(proxyType, proxyAddress))

                val pUser = account.proxyUsername ?: ""
                val pPass = account.proxyPassword ?: ""
                if (pUser.isNotBlank()) {
                    if (proxyType == java.net.Proxy.Type.SOCKS) {
                        GlobalProxyAuthenticator.proxyCredentials["${pHost}:${pPort}"] =
                            java.net.PasswordAuthentication(pUser, pPass.toCharArray())
                        if (!authenticatorSet) {
                            java.net.Authenticator.setDefault(GlobalProxyAuthenticator)
                            authenticatorSet = true
                        }
                    } else {
                        builder.proxyAuthenticator { _, response ->
                            val credential = okhttp3.Credentials.basic(pUser, pPass)
                            response.request.newBuilder()
                                .header("Proxy-Authorization", credential)
                                .build()
                        }
                    }
                }
                android.util.Log.d("FPC_DBG", "Установлен прокси ${account.proxyType} для ${account.username}")
            } catch (e: Exception) {
                android.util.Log.e("FPC_LOG", "Ошибка прокси для ${account.username}: ${e.message}")
            }
        }
        return builder.build()
    }

    fun create(account: Account? = null): FunPayApi {
        return Retrofit.Builder()
            .baseUrl("https://funpay.com/")
            .client(createClient(account))
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FunPayApi::class.java)
    }
}