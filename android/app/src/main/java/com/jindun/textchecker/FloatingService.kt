package com.jindun.textchecker

import android.annotation.SuppressLint
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

/**
 * 悬浮球服务（形态C）
 * 流程：复制微信文本 → 点悬浮球 → 弹出检查卡片（内嵌 WebView 加载本地 assets/checker.html）
 * → 检查/修正 → 点「复制修正文本」→ JS 桥写回剪贴板 → 回微信粘贴
 * 全程离线；卡片获得输入焦点后才能读剪贴板（Android 10+ 系统限制）
 */
class FloatingService : Service() {

    companion object {
        @JvmStatic var isRunning = false
    }

    private lateinit var wm: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val aiExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var cscEngine: CscEngine? = null
    private var bubble: View? = null
    private var panel: LinearLayout? = null
    private var webView: WebView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        isRunning = true
        showBubble()
    }

    override fun onDestroy() {
        isRunning = false
        aiExecutor.shutdown()
        cscEngine?.close()
        cscEngine = null
        hidePanel()
        bubble?.let { wm.removeView(it) }
        bubble = null
        super.onDestroy()
    }

    /* ================= 悬浮球 ================= */

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        if (bubble != null) return
        val d = resources.displayMetrics.density
        val size = (56 * d).toInt()

        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xE62563EB.toInt())
        }
        val tv = TextView(this).apply {
            text = "检"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = circle
        }

        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 220
        }

        // 拖动 + 区分点击（位移超过 25dp 视为拖动）
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        val slop = (25 * d).toInt()
        tv.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt(); val dy = (e.rawY - downY).toInt()
                    if (moved || dx * dx + dy * dy > slop * slop) {
                        moved = true
                        params.x = startX + dx; params.y = startY + dy
                        wm.updateViewLayout(v, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved) togglePanel(); true }
                else -> false
            }
        }

        bubble = tv
        wm.addView(tv, params)
    }

    /* ================= 检查卡片 ================= */

    private fun togglePanel() {
        if (panel == null) showPanel() else hidePanel()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showPanel() {
        if (panel != null) return
        val d = resources.displayMetrics.density
        val w = (340 * d).toInt()
        val h = (480 * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        // 标题栏
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
        }
        val title = TextView(this).apply {
            text = "文字检查器"
            textSize = 15f
            setTextColor(0xFF1F2937.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = Button(this).apply {
            text = "✕"
            textSize = 13f
            setOnClickListener { hidePanel() }
        }
        header.addView(title)
        header.addView(closeBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // WebView（本地 assets，无网络访问）
        val wv = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            addJavascriptInterface(Bridge(), "AndroidBridge")
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onJsAlert(
                    view: WebView?, url: String?, message: String?,
                    result: android.webkit.JsResult?
                ): Boolean {
                    Toast.makeText(this@FloatingService, message ?: "", Toast.LENGTH_SHORT).show()
                    result?.confirm()
                    return true
                }
            }
        }
        root.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(wv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val params = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            0, // 可获焦：Android 10+ 读剪贴板的前提
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        panel = root
        webView = wv
        wm.addView(root, params)
        wv.loadUrl("file:///android_asset/checker.html")

        // 等窗口获得焦点后读剪贴板（Android 10+ 限制）
        mainHandler.postDelayed({ readClipboardAndCheck() }, 300)
    }

    private fun readClipboardAndCheck() {
        if (panel == null) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        if (text.isBlank()) {
            Toast.makeText(this, "剪贴板为空，请先复制要检查的文字", Toast.LENGTH_SHORT).show()
            return
        }
        webView?.evaluateJavascript("window.receiveText(${JSONObject.quote(text)});", null)
    }

    private fun hidePanel() {
        webView?.let { wv ->
            wv.loadUrl("about:blank")
            (wv.parent as? LinearLayout)?.removeView(wv)
            wv.destroy()
        }
        webView = null
        panel?.let { wm.removeView(it) }
        panel = null
    }

    /* ================= JS 桥 ================= */

    inner class Bridge {
        @JavascriptInterface
        fun copy(text: String) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("checked", text))
            mainHandler.post {
                Toast.makeText(this@FloatingService, "已复制修正文本，请回微信粘贴", Toast.LENGTH_SHORT).show()
                hidePanel()
            }
        }

        /** 本地 AI 检查：JS 线程调用，转后台线程推理，结果经 receiveAi 回传 */
        @JavascriptInterface
        fun runAi(text: String) {
            aiExecutor.execute {
                try {
                    val engine = cscEngine ?: CscEngine(this@FloatingService).also {
                        cscEngine = it
                    }
                    val suggestions = engine.infer(text)
                    val arr = org.json.JSONArray()
                    suggestions.forEach { s ->
                        val o = org.json.JSONObject()
                        o.put("index", s.index)
                        o.put("original", s.original)
                        o.put("suggestion", s.suggestion)
                        arr.put(o)
                    }
                    val json = arr.toString()
                    mainHandler.post {
                        webView?.evaluateJavascript("window.receiveAi(${JSONObject.quote(json)});", null)
                    }
                } catch (e: Throwable) {
                    mainHandler.post {
                        Toast.makeText(this@FloatingService, "AI 检查失败: ${e.message}", Toast.LENGTH_LONG).show()
                        webView?.evaluateJavascript("window.receiveAi('[]');", null)
                    }
                }
            }
        }

        @JavascriptInterface
        fun aiDone() { /* 预留：AI 结果回传完成回调 */ }
    }
}
