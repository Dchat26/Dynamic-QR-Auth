package com.example.qrdevice.activities

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import android.content.Intent
import android.widget.LinearLayout
import com.example.qrdevice.network.IpAddress
import com.example.qrdevice.R

class MainActivity : AppCompatActivity() {

    private lateinit var webSocket: WebSocket
    private val TAG = "MainActivity"
    private lateinit var deviceCodeEditText: EditText
    private lateinit var submitButton: Button
    private lateinit var deviceCodeTextView: TextView
    private var deviceCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_1_main)

        window.statusBarColor = getColor(R.color.dgray)

        deviceCodeEditText = findViewById(R.id.deviceCodeEditText)
        submitButton = findViewById(R.id.submitButton)

        deviceCodeTextView = TextView(this).apply {
            textSize = 24f
            setTextColor(getColor(R.color.white))
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }

        deviceCode = intent.getStringExtra("DEVICE_CODE")

        if (deviceCode != null) {
            initWebSocket()
        } else {
            submitButton.setOnClickListener {
                deviceCode = deviceCodeEditText.text.toString().trim()
                if (deviceCode.isNullOrEmpty()) {
                    Toast.makeText(this, "코드를 입력하세요", Toast.LENGTH_SHORT).show()
                } else {
                    initWebSocket()
                }
            }
        }
    }


    private fun setupDeviceCodeView(deviceCode: String?) {
        deviceCodeTextView.text = deviceCode
        deviceCodeTextView.textSize = 24f
        deviceCodeTextView.gravity = Gravity.CENTER
        deviceCodeTextView.setTextColor(getColor(R.color.white))

        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 50, 0, 0)
        }
        deviceCodeTextView.layoutParams = layoutParams

        val parentLayout = deviceCodeEditText.parent as ViewGroup
        parentLayout.removeView(deviceCodeEditText)
        parentLayout.addView(deviceCodeTextView)

        submitButton.visibility = View.GONE
    }

    private fun initWebSocket() {
        val client = OkHttpClient()
        val request = Request.Builder().url(IpAddress.WEBSOCKET_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                val json = JSONObject()
                json.put("type", "REGISTER_DEVICE")
                json.put("deviceCode", deviceCode)
                webSocket.send(json.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runOnUiThread {
                    val jsonObject = JSONObject(text)
                    when (jsonObject.getString("type")) {
                        "REGISTER_SUCCESS" -> {
                            setupDeviceCodeView(deviceCode)
                        }
                        "QR_CODE" -> {
                            val qrContent = jsonObject.getString("qrContent")
                            val authCode = jsonObject.getString("authCode")
                            val randomToken = jsonObject.getString("randomToken")

                            val intent = Intent(this@MainActivity, QRCodeDisplayActivity::class.java)
                            intent.putExtra("QR_CONTENT", qrContent)
                            intent.putExtra("AUTH_CODE", authCode)
                            intent.putExtra("RANDOM_TOKEN", randomToken)
                            intent.putExtra("DEVICE_CODE", deviceCode)
                            startActivity(intent)
                        }
                        "REGISTER_FAILED" -> {
                            Toast.makeText(this@MainActivity, "등록 실패. 다시 입력하세요.", Toast.LENGTH_SHORT).show()
                            submitButton.visibility = View.VISIBLE
                            deviceCodeEditText.visibility = View.VISIBLE
                            deviceCodeTextView.visibility = View.GONE
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

    override fun onDestroy() {
        super.onDestroy()
        webSocket.close(1000, null)
    }
}
