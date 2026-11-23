package com.example.vdcreate.activities

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.vdcreate.R
import com.example.vdcreate.network.Item
import com.example.vdcreate.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private var itemCount = 0
    private lateinit var itemContainer: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val pollingInterval = 2000L
    private var lastItems: List<Item> = emptyList()
    private var isPollingPaused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_1_main)

        window.statusBarColor = getColor(R.color.dgray)

        val addButton = findViewById<Button>(R.id.myButton)
        itemContainer = findViewById(R.id.itemContainer)

        addButton.setOnClickListener {
            if (itemCount < 10) {
                val randomCode = generateRandomCode()
                val randomBattery = (0..100).random()
                val randomStatus = if (randomBattery <= 25) "off" else "on"
                val id = UUID.randomUUID().toString()
                val newItem = Item(id, randomCode, randomBattery, randomStatus)

                RetrofitClient.apiService.addItem(newItem).enqueue(object : Callback<Item> {
                    override fun onResponse(call: Call<Item>, response: Response<Item>) {
                        if (response.isSuccessful) {
                            addNewItem(itemContainer, newItem)
                            itemCount++
                        } else {
                            Log.e(TAG, "Failed to add item")
                        }
                    }

                    override fun onFailure(call: Call<Item>, t: Throwable) {
                        Log.e(TAG, "Error adding item: ${t.message}")
                    }
                })
            } else {
                Toast.makeText(this, "더 이상 항목을 추가할 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        startPolling()
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
        RetrofitClient.apiService.getItems().enqueue(object : Callback<List<Item>> {
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
                    Log.e(TAG, "Failed to get items")
                }
            }

            override fun onFailure(call: Call<List<Item>>, t: Throwable) {
                Log.e(TAG, "Error fetching items: ${t.message}")
            }
        })
    }

    private fun updateItems(items: List<Item>) {
        itemContainer.removeAllViews()
        itemCount = 0
        for (item in items) {
            addNewItem(itemContainer, item)
            itemCount++
        }
    }

    private fun generateRandomCode(): String {
        val charPool: List<Char> = ('A'..'Z') + ('0'..'9')
        return (1..5).map { charPool.random() }.joinToString("")
    }

    private fun addNewItem(itemContainer: LinearLayout, item: Item) {
        val relativeLayout = RelativeLayout(this)
        relativeLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val textView = TextView(this)
        textView.text = "번호: ${item.code} 배터리: ${item.battery}% 상태: ${item.status}"
        textView.textSize = 15f
        textView.setPadding(16, 16, 16, 16)
        textView.setTextColor(ContextCompat.getColor(this, R.color.pastel))

        val deleteButton = Button(this)
        deleteButton.text = "X"

        val textParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        textParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
        textParams.addRule(RelativeLayout.CENTER_VERTICAL)
        textView.layoutParams = textParams

        val deleteParams = RelativeLayout.LayoutParams(
            dpToPx(40),
            dpToPx(40)
        )
        deleteParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
        deleteParams.addRule(RelativeLayout.CENTER_VERTICAL)
        deleteButton.layoutParams = deleteParams

        textParams.rightMargin = dpToPx(16)

        relativeLayout.addView(textView)
        relativeLayout.addView(deleteButton)

        deleteButton.setOnClickListener {
            RetrofitClient.apiService.deleteItem(item.id).enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    if (response.isSuccessful) {
                        itemContainer.removeView(relativeLayout)
                        itemCount--
                    } else {
                        Log.e(TAG, "Failed to delete item: ${item.code}")
                    }
                }

                override fun onFailure(call: Call<Void>, t: Throwable) {
                    Log.e(TAG, "Error deleting item: ${t.message}")
                }
            })
        }

        itemContainer.addView(relativeLayout)
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
