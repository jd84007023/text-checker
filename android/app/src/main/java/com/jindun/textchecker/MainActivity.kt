package com.jindun.textchecker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var btnToggle: Button
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.statusText)
        val btnPermission = findViewById<Button>(R.id.btnPermission)
        btnToggle = findViewById(R.id.btnToggle)

        btnPermission.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "悬浮窗权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }

        btnToggle.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先点击上方按钮授权悬浮窗", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (FloatingService.isRunning) {
                stopService(Intent(this, FloatingService::class.java))
            } else {
                startService(Intent(this, FloatingService::class.java))
            }
            refreshUI()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
    }

    private fun refreshUI() {
        val granted = Settings.canDrawOverlays(this)
        status.text = when {
            !granted -> "状态：悬浮窗权限未授予"
            FloatingService.isRunning -> "状态：悬浮球运行中"
            else -> "状态：悬浮球已停止"
        }
        btnToggle.text = if (FloatingService.isRunning) "停止悬浮球" else "启动悬浮球"
    }
}
