package com.example.caps1.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class Item(
        val id: String,
        val code: String,
        val battery: Int,
        val status: String
)

data class QrCodeRequest(val code: String)
data class QrCodeResponse(val message: String)
data class AuthRequest(val code: String, val authCode: String, val randomToken: String)
data class AuthResponse(val success: Boolean, val message: String)
data class ReturnRequest(val code: String)

interface ApiService {
    @GET("/items")
    fun getItems(): Call<List<Item>>

    @POST("/generate_qr")
    fun generateQrCode(@Body request: QrCodeRequest): Call<QrCodeResponse>

    @POST("/stop_qr")
    fun stopQr(@Body requestBody: Map<String, String>): Call<Void>

    @POST("/verify_auth_code")
    fun verifyAuthCode(@Body authRequest: AuthRequest): Call<AuthResponse>

    @POST("/return")
    fun returnItem(@Body returnRequest: ReturnRequest): Call<Void>
}
