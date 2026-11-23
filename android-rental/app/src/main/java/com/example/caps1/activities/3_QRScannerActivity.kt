package com.example.caps1.activities

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import com.example.caps1.R
import com.example.caps1.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class QRScannerActivity : AppCompatActivity() {
    private val TAG = "QRScannerActivity"
    private lateinit var expectedCode: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        expectedCode = intent.getStringExtra("EXPECTED_CODE") ?: ""
        startQRCodeScanner()
    }

    private fun startQRCodeScanner() {
        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.setPrompt("QR 코드를 스캔하세요.")
        integrator.setCameraId(0)
        integrator.setBeepEnabled(false)
        integrator.setBarcodeImageEnabled(true)
        integrator.captureActivity = CustomScannerActivity::class.java
        integrator.initiateScan()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result: IntentResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                finish()
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            } else {
                val qrCodeContent = result.contents
                val parts = qrCodeContent.split("-")

                if (parts.size == 2) {
                    val code = parts[0]
                    val randomToken = parts[1]

                    if (code.trim() == expectedCode.trim()) {
                        val intent = Intent(this, AuthActivity::class.java)
                        intent.putExtra("CODE", code)
                        intent.putExtra("RANDOM_TOKEN", randomToken)
                        startActivity(intent)
                        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                    } else {
                        Toast.makeText(this, "QR코드를 다시 스캔해 주세요.", Toast.LENGTH_LONG).show()
                        Handler(Looper.getMainLooper()).postDelayed({
                            startQRCodeScanner()
                        }, 2000)
                    }
                } else {
                    Log.e(TAG, "Invalid QR code format")
                    Toast.makeText(this, "QR코드를 다시 스캔해 주세요.", Toast.LENGTH_LONG).show()
                    Handler(Looper.getMainLooper()).postDelayed({
                        startQRCodeScanner()
                    }, 2000)
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        stopQrAndGoHome()
    }

    private fun stopQrAndGoHome() {
        val requestBody = mapOf("code" to expectedCode)
        RetrofitClient.apiService.stopQr(requestBody).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    val intent = Intent(this@QRScannerActivity, HomeActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    val options = ActivityOptions.makeCustomAnimation(
                        this@QRScannerActivity,
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                    )
                    startActivity(intent, options.toBundle())
                    finish()
                } else {
                    Log.e(TAG, "stopQr error")
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e(TAG, "stopQr error")
            }
        })
    }
}
