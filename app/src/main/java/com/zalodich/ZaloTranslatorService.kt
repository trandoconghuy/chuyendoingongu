package com.zalodich

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
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
    private var lastZaloRoot: AccessibilityNodeInfo? = null
    private var keyboardPackages: Set<String> = emptySet()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var liveList: LinearLayout? = null
    private var liveStatus: TextView? = null
    private var lastChatFingerprint = ""
    private var scanGeneration = 0
    private val liveScan = Runnable { refreshLiveTranslations() }

    override fun onServiceConnected() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        keyboardPackages = (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .enabledInputMethodList.map { it.packageName }.toSet()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        removeAll()
        lastZaloRoot?.recycle()
        lastZaloRoot = null
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui" || pkg in keyboardPackages) return
        if (pkg == zalo) {
            event.source?.let { source ->
                var root = source
                while (true) {
                    val parent = root.parent ?: break
                    root = parent
                }
                lastZaloRoot?.recycle()
                lastZaloRoot = AccessibilityNodeInfo.obtain(root)
            }
            if (bubble == null) showBubble()
            val liveEvent = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
            if (panel != null && liveEvent) {
                scheduleLiveScan()
            }
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
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
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            background = rounded(0xF2F8FBFF.toInt(), 24, 0xCCFFFFFF.toInt())
            elevation = dp(18).toFloat()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "译"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(0xFF1769E0.toInt(), 11)
        }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(10) })
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@ZaloTranslatorService).apply {
                text = "Zalo Dịch"
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFF13233B.toInt())
            })
            addView(TextView(this@ZaloTranslatorService).apply {
                text = "dịch trực tiếp theo ngữ cảnh"
                textSize = 11f
                setTextColor(0xFF65758B.toInt())
            })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = "LIVE"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xFF087C5B.toInt())
            background = rounded(0xFFE2F7EF.toInt(), 12)
            setPadding(dp(9), dp(5), dp(9), dp(5))
        })
        header.addView(TextView(this).apply {
            text = "×"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(0xFF3D4A5C.toInt())
            setPadding(dp(12), 0, 0, 0)
            setOnClickListener { closePanel() }
        })
        box.addView(header)

        val status = TextView(this).apply {
            text = "Đang đọc vùng chat hiện tại…"
            textSize = 12f
            setTextColor(0xFF65758B.toInt())
            setPadding(0, dp(12), 0, dp(8))
        }
        box.addView(status)
        liveStatus = status

        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        liveList = listBox
        box.addView(
            ScrollView(this).apply {
                isFillViewport = true
                addView(listBox)
                background = rounded(0x5596A9C0, 18)
                setPadding(dp(8), dp(8), dp(8), dp(8))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                minOf(dp(300), resources.displayMetrics.heightPixels * 36 / 100)
            )
        )

        // Khung trả lời
        val et = EditText(this).apply {
            hint = "Soạn câu trả lời bằng tiếng Việt…"
            textSize = 14f
            minLines = 1
            maxLines = 3
            setTextColor(0xFF13233B.toInt())
            setHintTextColor(0xFF8492A6.toInt())
            background = rounded(0xFFFFFFFF.toInt(), 14, 0xFFD9E1EA.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        box.addView(et, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
        })
        val preview = TextView(this).apply {
            textSize = 12f; setTextColor(0xFF1769E0.toInt()); setPadding(dp(2), dp(5), dp(2), 0)
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        val langBtn = TextView(this).apply {
            text = labelTarget(); textSize = 12f; gravity = Gravity.CENTER
            setTextColor(0xFF42526A.toInt()); background = rounded(0xFFE8EDF3.toInt(), 14)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        langBtn.setOnClickListener {
            targetIdx = (targetIdx + 1) % targets.size
            langBtn.text = labelTarget()
        }
        val go = TextView(this).apply {
            text = "Dịch & điền vào Zalo"; textSize = 12f; gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            background = rounded(0xFF1769E0.toInt(), 14)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        go.setOnClickListener { doReply(et.text.toString().trim(), preview) }
        actions.addView(langBtn)
        actions.addView(go, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) })
        box.addView(actions)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = dp(18)
            }
        }
        wm.addView(box, lp)
        panel = box
        lastChatFingerprint = ""
        scheduleLiveScan(immediate = true)
    }

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            strokeColor?.let { setStroke(dp(1), it) }
        }

    private fun scheduleLiveScan(immediate: Boolean = false) {
        mainHandler.removeCallbacks(liveScan)
        mainHandler.postDelayed(liveScan, if (immediate) 40L else 360L)
    }

    private fun refreshLiveTranslations() {
        if (panel == null) return
        val messages = collectMessages()
        val fingerprint = messages.joinToString("¦") { "${it.sender}:${it.outgoing}:${it.text}" }
        if (fingerprint == lastChatFingerprint) return
        lastChatFingerprint = fingerprint
        val generation = ++scanGeneration
        val list = liveList ?: return
        val status = liveStatus ?: return
        list.removeAllViews()

        if (messages.isEmpty()) {
            status.text = "Kéo đến đoạn chat cần dịch — kết quả sẽ cập nhật tự động"
            showEmptyState(list, "Chưa đọc được tin nhắn trong vùng đang hiển thị")
            return
        }

        status.text = if (AiContextEngine.isConfigured(this)) {
            "AI đang đọc mạch hội thoại và người gửi…"
        } else {
            "Đang nhận diện trực tiếp trên thiết bị…"
        }
        showEmptyState(list, "Đang phân tích ${messages.size} mục trong vùng chat…")
        TranslatorEngine.detectWithContext(this, messages) { decisions, usedAi ->
            if (panel == null || generation != scanGeneration) return@detectWithContext
            list.removeAllViews()
            var shown = 0
            decisions.forEachIndexed { index, decision ->
                if (decision.code != "vi" && decision.code != "und") {
                    shown++
                    lastForeign = decision.code
                    addChatTranslation(list, messages[index], decision)
                }
            }
            val source = when {
                usedAi -> "AI ngữ cảnh"
                AiContextEngine.isConfigured(this) -> "bản dịch nhanh • AI đang tinh chỉnh"
                else -> "trên thiết bị"
            }
            status.text = if (shown == 0) {
                "Không có tin ngoại ngữ trong vùng này • $source"
            } else {
                "$shown bản dịch • $source • tự cập nhật khi cuộn"
            }
            if (shown == 0) showEmptyState(list, "Cuộn Zalo để dịch đoạn chat khác")
        }
    }

    private fun showEmptyState(parent: LinearLayout, message: String) {
        parent.addView(TextView(this).apply {
            text = message
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF718096.toInt())
            setPadding(dp(18), dp(28), dp(18), dp(28))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun addChatTranslation(parent: LinearLayout, line: ChatLine, decision: LanguageDecision) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (line.outgoing) Gravity.END else Gravity.START
            setPadding(0, dp(4), 0, dp(6))
        }
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(10))
            background = rounded(
                if (line.outgoing) 0xFFE2F0FF.toInt() else 0xFFFFFFFF.toInt(),
                15,
                if (line.outgoing) 0xFFC8DFFD.toInt() else 0xFFE1E7EE.toInt()
            )
            elevation = dp(2).toFloat()
        }
        bubble.addView(TextView(this).apply {
            text = if (line.outgoing) "Bạn" else line.sender
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (line.outgoing) 0xFF1769E0.toInt() else 0xFF52657D.toInt())
        })
        bubble.addView(TextView(this).apply {
            text = line.text
            textSize = 12f
            setTextColor(0xFF66768A.toInt())
            setPadding(0, dp(3), 0, dp(5))
        })
        val translated = TextView(this).apply {
            text = decision.translationVi ?: "Đang dịch…"
            textSize = 15f
            setTextColor(0xFF14243A.toInt())
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        bubble.addView(translated)
        decision.contextNote?.takeIf { it.isNotBlank() }?.let { note ->
            bubble.addView(TextView(this).apply {
                text = "Ngữ cảnh · $note"
                textSize = 11f
                setTextColor(0xFF7B5B22.toInt())
                setPadding(0, dp(6), 0, 0)
            })
        }
        if (line.outgoing) {
            row.addView(View(this), LinearLayout.LayoutParams(0, 1, 0.12f))
        }
        row.addView(bubble, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.88f))
        if (!line.outgoing) {
            row.addView(View(this), LinearLayout.LayoutParams(0, 1, 0.12f))
        }
        parent.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        if (decision.translationVi == null) {
            TranslatorEngine.translate(line.text, decision.code, "vi") { result ->
                translated.text = result ?: "Không tải được bản dịch — kiểm tra mạng"
            }
        }
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
        mainHandler.removeCallbacks(liveScan)
        scanGeneration++
        panel?.let { try { wm.removeView(it) } catch (_: IllegalArgumentException) {} }
        panel = null
        liveList = null
        liveStatus = null
        lastChatFingerprint = ""
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
        return lastZaloRoot?.takeIf { it.refresh() }
    }

    private data class ScreenText(val text: String, val bounds: Rect, val viewId: String)

    private fun collectMessages(): List<ChatLine> {
        val root = zaloRoot() ?: return emptyList()
        val out = ArrayList<ScreenText>()
        walk(root, out)
        val timeRe = Regex("""^[\d:/\s.,\-]+$""")
        val candidates = out
            .filter { it.text.length >= 2 && !timeRe.matches(it.text) }
            .filterNot { isZaloUiLabel(it.text) }
            .filterNot {
                val id = it.viewId.lowercase()
                id.contains("reaction") || id.contains("message_time") || id.contains("seen_status")
            }
            .sortedWith(compareBy<ScreenText> { it.bounds.top }.thenBy { it.bounds.left })
            .distinctBy { "${it.bounds.top / dp(2)}:${it.text}" }
            .takeLast(40)

        val result = ArrayList<ChatLine>()
        var currentSender: String? = null
        var index = 0
        while (index < candidates.size) {
            val item = candidates[index]
            val multiline = item.text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (multiline.size >= 2 && looksLikePersonName(multiline.first())) {
                val outgoing = isOutgoing(item.bounds)
                result.add(ChatLine(
                    sender = if (outgoing) "Bạn" else multiline.first(),
                    text = multiline.drop(1).joinToString("\n"),
                    outgoing = outgoing,
                    top = item.bounds.top
                ))
                currentSender = multiline.first()
                index++
                continue
            }

            val next = candidates.getOrNull(index + 1)
            if (looksLikeSenderLabel(item, next)) {
                currentSender = item.text
                index++
                continue
            }

            val outgoing = isOutgoing(item.bounds)
            result.add(ChatLine(
                sender = if (outgoing) "Bạn" else currentSender ?: "Người gửi",
                text = item.text,
                outgoing = outgoing,
                top = item.bounds.top
            ))
            index++
        }
        return result.distinctBy { "${it.top / dp(3)}:${it.sender}:${it.text}" }.takeLast(15)
    }

    private fun isOutgoing(bounds: Rect): Boolean {
        val width = resources.displayMetrics.widthPixels
        return bounds.centerX() > width * 0.62f && bounds.left > width * 0.28f
    }

    private fun looksLikeSenderLabel(item: ScreenText, next: ScreenText?): Boolean {
        if (isOutgoing(item.bounds) || !looksLikePersonName(item.text)) return false
        val id = item.viewId.lowercase()
        if (id.contains("sender") || id.contains("member_name") || id.endsWith("/name")) return true
        if (next == null || isOutgoing(next.bounds)) return false
        val verticalGap = next.bounds.top - item.bounds.bottom
        return verticalGap in -dp(4)..dp(28) && item.bounds.height() <= dp(28)
    }

    private fun looksLikePersonName(text: String): Boolean {
        if (text.length !in 2..60 || text.contains("http", ignoreCase = true)) return false
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size !in 1..7 || text.any { it in "?!:;,." }) return false
        return words.count { word -> word.firstOrNull()?.isUpperCase() == true } >= maxOf(1, words.size - 1)
    }

    private fun isZaloUiLabel(text: String): Boolean {
        val normalized = text.lowercase().trim()
        return normalized in setOf(
            "zalo", "tin nhắn", "danh bạ", "khám phá", "nhật ký", "gửi", "ảnh",
            "thêm", "tìm kiếm", "nhập tin nhắn", "tin nhắn mới", "gọi thoại",
            "gọi video", "tùy chọn", "quay lại", "bày tỏ cảm xúc", "trả lời",
            "video", "sticker", "ảnh động", "thu hồi", "chia sẻ"
        ) || normalized.startsWith("đã xem") || normalized.startsWith("đang hoạt động") ||
            normalized.contains("bày tỏ cảm xúc") ||
            normalized.matches(Regex("""^[❤❤️👍😂😆😮😢😡\s\d]+$"""))
    }

    private fun walk(n: AccessibilityNodeInfo, out: MutableList<ScreenText>) {
        if (n.isVisibleToUser && !n.isEditable) {
            sequenceOf(n.text, n.contentDescription)
                .mapNotNull { it?.toString()?.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .forEach { t ->
                    val r = Rect()
                    n.getBoundsInScreen(r)
                    out.add(ScreenText(t, Rect(r), n.viewIdResourceName.orEmpty()))
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
