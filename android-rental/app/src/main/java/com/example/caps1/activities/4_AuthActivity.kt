package com.example.caps1.activities

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.caps1.R
import com.example.caps1.network.AuthRequest
import com.example.caps1.network.AuthResponse
import com.example.caps1.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AuthActivity : AppCompatActivity() {

    private val TAG = "AuthActivity"
    private lateinit var code: String
    private lateinit var randomToken: String
    private lateinit var authCodeInput: EditText
    private lateinit var submitButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_3_auth)

        window.statusBarColor = ContextCompat.getColor(this, R.color.dgray)

        code = intent.getStringExtra("CODE") ?: ""
        randomToken = intent.getStringExtra("RANDOM_TOKEN") ?: ""

        authCodeInput = findViewById(R.id.authCodeInput)
        submitButton = findViewById(R.id.submitButton)

        submitButton.setOnClickListener {
            val authCode = authCodeInput.text.toString()
            if (authCode.isNotEmpty()) {
                sendAuthCodeToServer(authCode)
            } else {
                Toast.makeText(this, "인증 코드를 입력하세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendAuthCodeToServer(authCode: String) {
        val request = AuthRequest(code, authCode, randomToken)
        RetrofitClient.apiService.verifyAuthCode(request).enqueue(object : Callback<AuthResponse> {
            override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                if (response.isSuccessful) {
                    val authResponse = response.body()
                    if (authResponse?.success == true) {
                        navigateToSuccess()
                    } else {
                        Toast.makeText(this@AuthActivity, "QR코드를 다시 스캔해 주세요.", Toast.LENGTH_LONG).show()
                        navigateToQRCodeScanner()
                    }
                } else {
                    Toast.makeText(this@AuthActivity, "QR코드를 다시 스캔해 주세요.", Toast.LENGTH_LONG).show()
                    navigateToQRCodeScanner()
                }
            }

            override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                Toast.makeText(this@AuthActivity, "QR코드를 다시 스캔해 주세요.", Toast.LENGTH_SHORT).show()
                navigateToQRCodeScanner()
            }
        })
    }

    private fun navigateToSuccess() {
        val intent = Intent(this, SuccessActivity::class.java)
        intent.putExtra("CODE", code)
        val options = ActivityOptions.makeCustomAnimation(
            this,
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )
        startActivity(intent, options.toBundle())
        finish()
    }

    private fun navigateToQRCodeScanner() {
        val intent = Intent(this, QRScannerActivity::class.java)
        intent.putExtra("EXPECTED_CODE", code)
        val options = ActivityOptions.makeCustomAnimation(
            this,
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )
        startActivity(intent, options.toBundle())
        finish()
    }
}

//    override fun onBackPressed() {
//        super.onBackPressed()
//        stopQrAndGoHome()
//    }
//
//    private fun stopQrAndGoHome() {
//        val requestBody = mapOf("code" to code)
//        RetrofitClient.apiService.stopQr(requestBody).enqueue(object : Callback<Void> {
//            override fun onResponse(call: Call<Void>, response: Response<Void>) {
//                if (response.isSuccessful) {
//                    val intent = Intent(this@AuthActivity, HomeActivity::class.java)
//                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
//                    val options = ActivityOptions.makeCustomAnimation(
//                        this@AuthActivity,
//                        android.R.anim.fade_in,
//                        android.R.anim.fade_out
//                    )
//                    startActivity(intent, options.toBundle())
//                    finish()
//                } else {
//                    Log.e(TAG, "stopQr 실패")
//                }
//            }
//
//            override fun onFailure(call: Call<Void>, t: Throwable) {
//                Log.e(TAG, "stopQr 요청 실패: ${t.message}")
//            }
//        })
//    }
//}
