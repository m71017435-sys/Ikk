package com.voicebubble.reader

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import java.util.Locale

class ReaderAccessibilityService : AccessibilityService(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val overlayBubbles = mutableListOf<View>()
    private val handler = Handler(Looper.getMainLooper())
    private var pendingRefresh: Runnable? = null

    private val MAX_BUBBLES = 25
    private val MIN_TEXT_LENGTH = 2

    override fun onServiceConnected() {
        super.onServiceConnected()
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("fa", "IR"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ttsReady) {
                // فارسی نصب نیست؛ روی زبان پیش‌فرض دستگاه می‌خواند
                tts?.language = Locale.getDefault()
                ttsReady = true
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        val targetPackage = prefs.getString(Prefs.TARGET_PACKAGE, null)

        if (targetPackage == null) {
            clearBubbles()
            return
        }

        val eventPackage = event.packageName?.toString()
        if (eventPackage != targetPackage) {
            clearBubbles()
            return
        }

        // Debounce so we don't rescan on every tiny content change (keeps it light)
        pendingRefresh?.let { handler.removeCallbacks(it) }
        val runnable = Runnable { refreshBubbles(targetPackage) }
        pendingRefresh = runnable
        handler.postDelayed(runnable, 350)
    }

    private fun refreshBubbles(targetPackage: String) {
        clearBubbles()
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != targetPackage) return

        val nodes = mutableListOf<Pair<String, Rect>>()
        collectTextNodes(root, nodes)

        for ((text, bounds) in nodes.take(MAX_BUBBLES)) {
            addBubble(text, bounds)
        }
    }

    private fun collectTextNodes(node: AccessibilityNodeInfo, out: MutableList<Pair<String, Rect>>) {
        if (out.size >= MAX_BUBBLES) return

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && text.length >= MIN_TEXT_LENGTH && node.isVisibleToUser) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() > 0 && bounds.height() > 0) {
                out.add(text to bounds)
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTextNodes(child, out)
            if (out.size >= MAX_BUBBLES) break
        }
    }

    private fun addBubble(text: String, bounds: Rect) {
        val bubble = TextView(this).apply {
            setText("\uD83C\uDFA4") // 🎤
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC2196F3"))
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
        }

        val size = 72 // px, small square bubble
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        // Place bubble just to the left of the text's start, aligned with its top
        params.x = (bounds.left - size).coerceAtLeast(0)
        params.y = bounds.top

        bubble.setOnClickListener {
            if (ttsReady) {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
            }
        }

        try {
            windowManager.addView(bubble, params)
            overlayBubbles.add(bubble)
        } catch (e: Exception) {
            // اگر افزودن overlay شکست بخورد، به‌سادگی از آن صرف‌نظر می‌کنیم
        }
    }

    private fun clearBubbles() {
        for (v in overlayBubbles) {
            try {
                windowManager.removeView(v)
            } catch (e: Exception) {
                // already removed
            }
        }
        overlayBubbles.clear()
    }

    override fun onInterrupt() {
        clearBubbles()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearBubbles()
        tts?.stop()
        tts?.shutdown()
    }
}
