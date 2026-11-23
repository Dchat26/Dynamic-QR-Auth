package com.example.vdcreate.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class Item(val id: String, val code: String, val battery: Int, val status: String)

interface ApiService {
    @POST("/items")
    fun addItem(@Body item: Item): Call<Item>

    @DELETE("/items/{id}")
    fun deleteItem(@Path("id") id: String): Call<Void>

    @GET("/items")
    fun getItems(): Call<List<Item>>
}
