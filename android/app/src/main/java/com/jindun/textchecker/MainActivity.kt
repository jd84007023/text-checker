package com.jindun.textchecker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

/**
 * 主界面：悬浮窗授权 + 服务开关 + 云端检查服务商配置
 * 服务商配置存 SharedPreferences，供 FloatingService 的云检查读取
 */
class MainActivity : Activity() {

    private lateinit var btnToggle: Button
    private lateinit var status: TextView
    private lateinit var presetSpinner: Spinner
    private lateinit var keyInput: EditText
    private lateinit var urlInput: EditText
    private lateinit var modelInput: EditText

    // 预设：名称 to (BaseURL, 模型名)
    private val presets = listOf(
        "智谱 glm-4.7-flash（永久免费）" to Pair(
            "https://open.bigmodel.cn/api/paas/v4/chat/completions", "glm-4.7-flash"),
        "硅基流动 Qwen2.5-7B（免费档）" to Pair(
            "https://api.siliconflow.cn/v1/chat/completions", "Qwen/Qwen2.5-7B-Instruct"),
        "自定义（OpenAI 兼容）" to Pair("", "")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        val title = TextView(this).apply {
            text = "文字检查器"
            textSize = 24f
            setPadding(0, 0, 0, dp(4))
        }
        status = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF6B7280.toInt())
            setPadding(0, 0, 0, dp(12))
        }

        val btnPermission = Button(this).apply { text = "第一步：授权悬浮窗权限" }
        btnToggle = Button(this).apply { text = "第二步：启动悬浮球" }

        root.addView(title)
        root.addView(status)
        root.addView(btnPermission)
        root.addView(btnToggle)

        /* ===== 云端检查配置区 ===== */
        root.addView(TextView(this).apply {
            text = "云端 AI 检查配置（免费模型）"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(20), 0, dp(6))
        })
        root.addView(TextView(this).apply {
            text = "默认使用智谱 glm-4.7-flash（永久免费，注册 bigmodel.cn 获取 API Key）。" +
                    "仅点「云端检查」时文本会发送到所选服务商。"
            textSize = 12f
            setTextColor(0xFF6B7280.toInt())
            setPadding(0, 0, 0, dp(8))
        })

        presetSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item, presets.map { it.first })
        }
        root.addView(presetSpinner)

        keyInput = EditText(this).apply {
            hint = "API Key"
            setSingleLine(true)
        }
        root.addView(keyInput)

        urlInput = EditText(this).apply { hint = "接口地址（自定义时填写）"; setSingleLine(true) }
        root.addView(urlInput)
        modelInput = EditText(this).apply { hint = "模型名（自定义时填写）"; setSingleLine(true) }
        root.addView(modelInput)

        val btnSave = Button(this).apply { text = "保存配置" }
        root.addView(btnSave)

        root.addView(TextView(this).apply {
            text = "使用方法：\n" +
                    "1. 在微信里写好话，全选复制\n" +
                    "2. 点悬浮球 → 自动检查（错字/标点，离线）\n" +
                    "3. 重要消息点「云端检查」→ AI 查音近错字和语病\n" +
                    "4. 采纳建议或复制修正文本，回微信粘贴"
            textSize = 13f
            setPadding(0, dp(16), 0, 0)
        })

        setContentView(root)

        loadPrefs()
        btnPermission.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "悬浮窗权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
        }
        btnToggle.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先授权悬浮窗权限", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (FloatingService.isRunning) {
                stopService(Intent(this, FloatingService::class.java))
            } else {
                startService(Intent(this, FloatingService::class.java))
            }
            refreshUI()
        }
        btnSave.setOnClickListener { savePrefs() }
        presetSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                val isCustom = pos == presets.size - 1
                urlInput.isEnabled = isCustom
                modelInput.isEnabled = isCustom
                if (!isCustom) {
                    urlInput.setText(presets[pos].second.first)
                    modelInput.setText(presets[pos].second.second)
                }
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
    }

    private fun loadPrefs() {
        val sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedUrl = sp.getString(K_URL, presets[0].second.first) ?: ""
        val savedKey = sp.getString(K_KEY, "") ?: ""
        keyInput.setText(savedKey)
        urlInput.setText(savedUrl)
        modelInput.setText(sp.getString(K_MODEL, presets[0].second.second) ?: "")
    }

    private fun savePrefs() {
        val pos = presetSpinner.selectedItemPosition
        val url = urlInput.text.toString().trim()
        val model = modelInput.text.toString().trim()
        val key = keyInput.text.toString().trim()
        if (key.isEmpty() || url.isEmpty() || model.isEmpty()) {
            Toast.makeText(this, "Key / 地址 / 模型名均不能为空", Toast.LENGTH_LONG).show()
            return
        }
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(K_URL, url).putString(K_MODEL, model).putString(K_KEY, key)
            .putInt(K_PRESET, pos).apply()
        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val PREFS = "checker_cfg"
        const val K_URL = "base_url"
        const val K_MODEL = "model"
        const val K_KEY = "api_key"
        const val K_PRESET = "preset"
    }
}
