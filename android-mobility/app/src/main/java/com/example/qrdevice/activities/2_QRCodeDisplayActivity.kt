package com.example.qrdevice.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.journeyapps.barcodescanner.BarcodeEncoder
import android.os.CountDownTimer
import com.example.qrdevice.network.IpAddress
import com.example.qrdevice.R
import org.json.JSONObject

class QRCodeDisplayActivity : AppCompatActivity() {

    private val TAG = "QRCodeDisplayActivity"
    private lateinit var webSocket: WebSocket
    private lateinit var timerTextView: TextView
    private var deviceCode: String? = null
    private lateinit var qrCodeImageView: ImageView
    private lateinit var authCodeTextView: TextView

    private var countdownTimer: CountDownTimer? = null
    private val inactivityHandler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable {
        navigateToMainActivity()
    }

    companion object {
        var latestInstance: QRCodeDisplayActivity? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_2_qrcodedisplay)

        window.statusBarColor = getColor(R.color.dgray)

        qrCodeImageView = findViewById(R.id.qrCodeImageView)
        authCodeTextView = findViewById(R.id.authCodeTextView)
        timerTextView = findViewById(R.id.timerTextView)

        val qrContent = intent.getStringExtra("QR_CONTENT")
        val authCode = intent.getStringExtra("AUTH_CODE")
        val randomToken = intent.getStringExtra("RANDOM_TOKEN")
        deviceCode = intent.getStringExtra("DEVICE_CODE")

        if (qrContent != null && authCode != null && randomToken != null) {
            updateQRCode(qrContent, authCode, randomToken)
        }

        initWebSocket()
        startTimer(30000)

        latestInstance?.finish()
        latestInstance = this
    }

    private fun initWebSocket() {
        val client = OkHttpClient()
        val request = Request.Builder().url(IpAddress.WEBSOCKET_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runOnUiThread {
                    val message = JSONObject(text)
                    when (message.getString("type")) {
                        "QR_CODE" -> {
                            val qrContent = message.getString("qrContent")
                            val authCode = message.getString("authCode")
                            val randomToken = message.getString("randomToken")
                            updateQRCode(qrContent, authCode, randomToken)
                            restartInactivityTimer()
                        }
                        "AUTH_SUCCESS" -> {
                            val code = message.getString("code")
                            if (code == deviceCode) {
                                navigateToMainActivity()
                            }
                        }
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
            }
        })
    }

    private fun updateQRCode(qrContent: String, authCode: String, randomToken: String) {
        generateQRCode("$qrContent-$randomToken", qrCodeImageView)
        authCodeTextView.text = authCode
    }

    private fun generateQRCode(content: String, imageView: ImageView) {
        val multiFormatWriter = MultiFormatWriter()
        try {
            val bitMatrix = multiFormatWriter.encode(content, BarcodeFormat.QR_CODE, 200, 200)
            val barcodeEncoder = BarcodeEncoder()
            val bitmap = barcodeEncoder.createBitmap(bitMatrix)
            imageView.setImageBitmap(bitmap)
        } catch (e: WriterException) {
            e.printStackTrace()
        }
    }

    private fun startTimer(durationInMillis: Long) {
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(durationInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                timerTextView.text = "${secondsRemaining}초"
            }

            override fun onFinish() {
                timerTextView.text = "0초"
                restartInactivityTimer()
            }
        }.start()
    }

    private fun restartInactivityTimer() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
        inactivityHandler.postDelayed(inactivityRunnable, 60000)
    }

    private fun navigateToMainActivity() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("DEVICE_CODE", deviceCode)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (latestInstance == this) {
            latestInstance = null
        }
        webSocket.close(1000, null)
        countdownTimer?.cancel()
        inactivityHandler.removeCallbacks(inactivityRunnable)
    }
}
