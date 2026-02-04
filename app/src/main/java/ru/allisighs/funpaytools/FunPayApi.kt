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


    @GET("orders/{id}/")
    suspend fun getOrder(
        @Path("id") orderId: String,
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String
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
}

object RetrofitInstance {
    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .hostnameVerifier { _, _ -> true }
        .build()

    val api: FunPayApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://funpay.com/")
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FunPayApi::class.java)
    }
}