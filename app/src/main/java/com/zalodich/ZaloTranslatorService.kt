package com.zalodich

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class ZaloTranslatorService : AccessibilityService() {

    private val zalo = "com.zing.zalo"
    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private var panel: View? = null
    private var lastForeign = "en"
    private val targets = listOf("auto", "en", "ja", "ko", "zh")
    private var targetIdx = 0

    override fun onServiceConnected() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        removeAll()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun imePackages(): Set<String> =
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .enabledInputMethodList.map { it.packageName }.toSet()

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui" || pkg in imePackages()) return
        if (pkg == zalo) {
            if (bubble == null) showBubble()
        } else {
            removeAll()
        }
    }

    // ---------- Nút nổi ----------
    private fun showBubble() {
        val size = dp(48)
        val tv = TextView(this).apply {
            text = "譯"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF0068FF.toInt())
            }
        }
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - dp(8)
            y = dp(220)
        }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        tv.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; startX = lp.x; startY = lp.y; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - downX).toInt()
                    val dy = (ev.rawY - downY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    if (moved) {
                        lp.x = startX + dx; lp.y = startY + dy
                        wm.updateViewLayout(v, lp)
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) togglePanel()
            }
            true
        }
        wm.addView(tv, lp)
        bubble = tv
    }

    // ---------- Bảng dịch ----------
    private fun labelTarget() =
        if (targetIdx == 0) "Đích: Tự động ($lastForeign)" else "Đích: ${targets[targetIdx]}"

    private fun togglePanel() {
        if (panel != null) { closePanel(); return }
        val texts = collectMessages()

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), 0xFFCCCCCC.toInt())
            }
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        header.addView(TextView(this).apply {
            text = "Zalo Dịch"; textSize = 16f; setTextColor(Color.BLACK)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = "✕"; textSize = 18f; setTextColor(Color.BLACK); setPadding(dp(12), 0, dp(4), 0)
            setOnClickListener { closePanel() }
        })
        box.addView(header)

        val status = TextView(this).apply { text = "Đang quét tin nhắn…"; textSize = 13f; setTextColor(Color.DKGRAY) }
        box.addView(status)

        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(
            ScrollView(this).apply { addView(listBox) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(220))
        )

        // Quét và dịch tin ngoại ngữ -> Việt
        val langs = arrayOfNulls<String>(texts.size)
        var pending = texts.size
        var shown = 0
        if (texts.isEmpty()) status.text = "Không thấy tin nhắn nào trên màn hình."
        texts.forEachIndexed { i, t ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                setPadding(0, dp(6), 0, dp(6))
            }
            val orig = TextView(this).apply { text = t; textSize = 12f; setTextColor(0xFF777777.toInt()) }
            val tr = TextView(this).apply { text = "…"; textSize = 15f; setTextColor(Color.BLACK) }
            row.addView(orig); row.addView(tr); listBox.addView(row)

            TranslatorEngine.detect(t) { code ->
                pending--
                if (code != "vi" && code != "und") {
                    langs[i] = code
                    row.visibility = View.VISIBLE
                    shown++
                    langs.lastOrNull { it != null }?.let { lastForeign = it }
                    TranslatorEngine.translate(t, code, "vi") { res ->
                        tr.text = res ?: "(chưa có gói dịch — mở app Zalo Dịch, bấm \"Tải gói dịch\")"
                    }
                }
                if (pending == 0) {
                    status.text = if (shown == 0) "Không thấy tin ngoại ngữ." else "Tin ngoại ngữ: $shown"
                }
            }
        }

        // Khung trả lời
        val et = EditText(this).apply {
            hint = "Gõ tiếng Việt để dịch…"; textSize = 15f; minLines = 2; setTextColor(Color.BLACK)
        }
        val preview = TextView(this).apply { textSize = 13f; setTextColor(0xFF0068FF.toInt()) }
        val langBtn = Button(this)
        langBtn.text = labelTarget()
        langBtn.setOnClickListener {
            targetIdx = (targetIdx + 1) % targets.size
            langBtn.text = labelTarget()
        }
        val go = Button(this)
        go.text = "Dịch & điền vào Zalo"
        go.setOnClickListener { doReply(et.text.toString().trim(), preview) }

        box.addView(et)
        box.addView(langBtn)
        box.addView(go)
        box.addView(preview)

        val lp = WindowManager.LayoutParams(
            resources.displayMetrics.widthPixels - dp(24),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(8)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        wm.addView(box, lp)
        panel = box
    }

    private fun doReply(vi: String, preview: TextView) {
        if (vi.isEmpty()) return
        val target = if (targetIdx == 0) lastForeign else targets[targetIdx]
        preview.text = "Đang dịch…"
        TranslatorEngine.translate(vi, "vi", target) { res ->
            if (res == null) {
                preview.text = "Chưa có gói dịch vi→$target. Mở app Zalo Dịch và bấm \"Tải gói dịch\"."
            } else {
                preview.text = res
                // Dịch ngược để bạn kiểm tra
                TranslatorEngine.translate(res, target, "vi") { back ->
                    preview.text = "$res\n↩ ${back ?: ""}"
                }
                if (fillZalo(res)) {
                    Toast.makeText(this, "Đã điền vào ô chat — bấm Gửi trong Zalo", Toast.LENGTH_SHORT).show()
                } else {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("dich", res))
                    Toast.makeText(this, "Không tìm thấy ô chat. Đã copy — hãy dán vào Zalo", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun closePanel() {
        panel?.let { try { wm.removeView(it) } catch (_: IllegalArgumentException) {} }
        panel = null
    }

    private fun removeAll() {
        if (!::wm.isInitialized) return
        closePanel()
        bubble?.let { try { wm.removeView(it) } catch (_: IllegalArgumentException) {} }
        bubble = null
    }

    // ---------- Đọc / điền màn hình Zalo ----------
    private fun zaloRoot(): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { if (it.packageName == zalo) return it }
        for (w in windows) {
            val r = w.root
            if (r != null && r.packageName == zalo) return r
        }
        return null
    }

    private fun collectMessages(): List<String> {
        val root = zaloRoot() ?: return emptyList()
        val out = ArrayList<Pair<Int, String>>()
        walk(root, out)
        val timeRe = Regex("""^[\d:/\s.,\-]+$""")
        return out.sortedBy { it.first }
            .map { it.second }
            .filter { it.length >= 2 && !timeRe.matches(it) }
            .distinct()
            .takeLast(15)
    }

    private fun walk(n: AccessibilityNodeInfo, out: MutableList<Pair<Int, String>>) {
        if (n.isVisibleToUser && !n.isEditable) {
            val t = n.text?.toString()?.trim()
            if (!t.isNullOrEmpty()) {
                val r = Rect()
                n.getBoundsInScreen(r)
                out.add(r.top to t)
            }
        }
        for (i in 0 until n.childCount) {
            n.getChild(i)?.let { walk(it, out) }
        }
    }

    private fun findEditable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isEditable) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            findEditable(c)?.let { return it }
        }
        return null
    }

    private fun fillZalo(text: String): Boolean {
        val root = zaloRoot() ?: return false
        val edit = findEditable(root) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }
}
