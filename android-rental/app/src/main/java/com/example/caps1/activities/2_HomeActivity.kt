package com.example.caps1.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.caps1.network.Item
import com.example.caps1.network.QrCodeRequest
import com.example.caps1.network.QrCodeResponse
import com.example.caps1.R
import com.example.caps1.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class HomeActivity : AppCompatActivity() {

    private lateinit var itemContainer: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val pollingInterval = 2000L
    private val QR_SCANNER_REQUEST_CODE = 1001
    private var currentItemCode: String? = null
    private var currentItemId: String? = null
    private var lastItems: List<Item> = emptyList()
    private var isPollingPaused = false
    private val TAG = "HomeActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_2_home)

        window.statusBarColor = getColor(R.color.dgray)

        itemContainer = findViewById(R.id.itemContainer)

        startPolling()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    private fun startPolling() {
        handler.post(object : Runnable {
            override fun run() {
                if (!isPollingPaused) {
                    fetchItems()
                }
                handler.postDelayed(this, pollingInterval)
            }
        })
    }

    private fun fetchItems() {
        val call = RetrofitClient.apiService.getItems()
        call.enqueue(object : Callback<List<Item>> {
            override fun onResponse(call: Call<List<Item>>, response: Response<List<Item>>) {
                if (response.isSuccessful) {
                    val items = response.body()
                    items?.let {
                        if (it != lastItems) {
                            updateItems(it)
                            lastItems = it
                        }
                    }
                } else {
                    Log.d(TAG, "Can't get items")
                }
            }
            override fun onFailure(call: Call<List<Item>>, t: Throwable) {
                Log.e(TAG, "Failed to get items")
            }
        })
    }

    private fun updateItems(items: List<Item>) {
        itemContainer.removeAllViews()
        for (item in items) {
            addItemView(item)
        }
    }

    private fun addItemView(item: Item) {
        val itemView = layoutInflater.inflate(R.layout.utility_item_view, itemContainer, false)

        val textView = itemView.findViewById<TextView>(R.id.textView)
        textView.text = "번호: ${item.code} 배터리: ${item.battery}%"
        itemView.tag = item.id

        val button = itemView.findViewById<Button>(R.id.buttonScan)

        when {
            item.status == "on" -> {
                button.text = "가능"
                button.setOnClickListener {
                    currentItemCode = item.code
                    currentItemId = item.id
                    requestQrCode(item.code)
                }
            }
            item.status == "off" && item.battery > 25 -> {
                button.text = "대여중"
                button.setOnClickListener {
                    Toast.makeText(this, "해당 기기는 현재 대여중입니다.", Toast.LENGTH_LONG).show()
                }
            }
            else -> {
                button.text = "불가"
                button.setOnClickListener {
                    Toast.makeText(this, "해당 기기는 대여가 불가능합니다.", Toast.LENGTH_LONG).show()
                }
            }
        }

        itemContainer.addView(itemView)
    }

    private fun requestQrCode(code: String) {
        isPollingPaused = true
        val request = QrCodeRequest(code)
        val call = RetrofitClient.apiService.generateQrCode(request)
        call.enqueue(object : Callback<QrCodeResponse> {
            override fun onResponse(call: Call<QrCodeResponse>, response: Response<QrCodeResponse>) {
                isPollingPaused = false
                if (response.isSuccessful) {
                    val intent = Intent(this@HomeActivity, QRScannerActivity::class.java)
                    intent.putExtra("EXPECTED_CODE", code)
                    intent.putExtra("ITEM_ID", code)
                    startActivityForResult(intent, QR_SCANNER_REQUEST_CODE)
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                } else {
                    Log.e(TAG, "Can't generate QRCode")
                }
            }

            override fun onFailure(call: Call<QrCodeResponse>, t: Throwable) {
                isPollingPaused = false
                Log.e(TAG, "Request QRCode failed")
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == QR_SCANNER_REQUEST_CODE && resultCode == RESULT_OK) {
            val itemId = currentItemId ?: return

            val intent = Intent(this, SuccessActivity::class.java)
            intent.putExtra("ITEM_ID", itemId)
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
    }
}
