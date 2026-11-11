package com.nahid.ai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView

    private val permissions = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE
    ).apply {
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(30, 30, 30, 30)
        }

        val title = TextView(this).apply {
            text = "NAHID AI"
            textSize = 32f
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "তোমার AI Assistant প্রস্তুত"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 30)
        }

        val startButton = Button(this).apply {
            text = "START NAHID AI"
            setOnClickListener {
                requestPermissions(permissions, 100)
            }
        }

        val stopButton = Button(this).apply {
            text = "STOP NAHID AI"
            setOnClickListener {
                stopService(Intent(this@MainActivity, WakeWordService::class.java))
                statusText.text = "NAHID AI বন্ধ করা হয়েছে"
            }
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(startButton)
        layout.addView(stopButton)

        setContentView(layout)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100) {
            val allGranted = grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                val intent = Intent(this, WakeWordService::class.java)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }

                statusText.text = "NAHID AI চালু হয়েছে। বলো: Hey Nahid"
            } else {
                statusText.text = "কাজ করার জন্য প্রয়োজনীয় Permission দিতে হবে।"
            }
        }
    }
}
