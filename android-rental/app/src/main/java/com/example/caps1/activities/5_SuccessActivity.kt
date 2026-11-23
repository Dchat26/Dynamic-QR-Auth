package com.example.caps1.activities

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.caps1.R
import com.example.caps1.network.RetrofitClient
import com.example.caps1.network.ReturnRequest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.random.Random
import kotlin.math.max
import kotlin.math.min

class SuccessActivity : AppCompatActivity() {

    private lateinit var speedTextView: TextView
    private lateinit var timerTextView: TextView
    private lateinit var distanceTextView: TextView
    private lateinit var returnButton: Button

    private var seconds = 0
    private var minutes = 0
    private var distance = 0.0
    private var currentSpeed = 0

    private lateinit var code: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_4_success)

        window.statusBarColor = ContextCompat.getColor(this, R.color.dgray)

        speedTextView = findViewById(R.id.speedTextView)
        timerTextView = findViewById(R.id.timerTextView)
        distanceTextView = findViewById(R.id.distanceTextView)
        returnButton = findViewById(R.id.returnButton)

        code = intent.getStringExtra("CODE") ?: ""

        startTimer()

        startSpeedChange()

        returnButton.setOnClickListener {
            sendReturnRequest(code)
        }
    }

    private fun sendReturnRequest(code: String) {
        val returnRequest = ReturnRequest(code)

        RetrofitClient.apiService.returnItem(returnRequest).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    navigateToHome()
                } else {
                    Toast.makeText(this@SuccessActivity, "반납 실패. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(this@SuccessActivity, "반납 실패. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            }
        })
    }


    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        val options = ActivityOptions.makeCustomAnimation(
            this@SuccessActivity,
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )
        startActivity(intent, options.toBundle())
        finish()
    }

    private fun startTimer() {
        val timer = object : CountDownTimer(Long.MAX_VALUE, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                seconds++
                if (seconds == 60) {
                    seconds = 0
                    minutes++
                }
                val timeText = String.format("이용시간 %02d:%02d", minutes, seconds)
                timerTextView.text = timeText

                val distanceIncrement = currentSpeed * (1 / 1500.0)
                distance += distanceIncrement
                val distanceText = String.format("이동거리: %.2f km", distance)
                distanceTextView.text = distanceText
            }

            override fun onFinish() {
            }
        }
        timer.start()
    }

    private fun startSpeedChange() {
        var targetSpeed = 20
        val speedChangeInterval = 1000L

        val speedTimer = object : CountDownTimer(Long.MAX_VALUE, speedChangeInterval) {
            override fun onTick(millisUntilFinished: Long) {
                val speedChange = Random.nextInt(-3, 4)
                targetSpeed = min(25, max(0, targetSpeed + speedChange))

                if (currentSpeed < targetSpeed) {
                    currentSpeed = min(currentSpeed + 1, targetSpeed)
                } else if (currentSpeed > targetSpeed) {
                    currentSpeed = max(currentSpeed - 1, targetSpeed)
                }

                speedTextView.text = String.format("%02d", currentSpeed)
            }

            override fun onFinish() {
            }
        }
        speedTimer.start()
    }
}
