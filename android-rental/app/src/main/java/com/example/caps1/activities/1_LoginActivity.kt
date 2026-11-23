package com.example.caps1.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.example.caps1.R
import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.POST
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Field
import androidx.appcompat.app.AppCompatActivity
import com.example.caps1.network.IpAddress

class LoginActivity : AppCompatActivity() {

    private lateinit var editTextUsername: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var buttonLogin: Button
    private val TAG = "LoginActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_1_login)

        window.statusBarColor = getColor(R.color.dgray)

        editTextUsername = findViewById(R.id.editTextUsername)
        editTextPassword = findViewById(R.id.editTextPassword)
        buttonLogin = findViewById(R.id.buttonLogin)

        buttonLogin.setOnClickListener {
            val username = editTextUsername.text.toString()
            val password = editTextPassword.text.toString()

            val retrofit = Retrofit.Builder()
                    .baseUrl(IpAddress.BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

            val service = retrofit.create(ApiService::class.java)
            val call = service.login(username, password)

            call.enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@LoginActivity, "로그인 성공", Toast.LENGTH_SHORT).show()

                        val intent = Intent(this@LoginActivity, HomeActivity::class.java)
                        startActivity(intent)
                        finish()

                    } else {
                        Toast.makeText(this@LoginActivity, "아이디 또는 비밀번호가 잘못되었습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    Log.e(TAG, "Network error")
                }
            })
        }
    }

    interface ApiService {
        @POST("login")
        @FormUrlEncoded
        fun login(
                @Field("username") username: String,
                @Field("password") password: String
        ): Call<LoginResponse>
    }

    data class LoginResponse(
            @SerializedName("message")
            val message: String
    )
}
