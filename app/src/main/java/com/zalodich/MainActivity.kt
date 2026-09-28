package com.zalodich

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.app.Activity

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun tv(t: String, size: Float = 15f) = TextView(this).apply { text = t; textSize = size; setPadding(0, pad / 2, 0, pad / 2) }

        col.addView(tv("Zalo Dịch", 24f))
        col.addView(tv("Dịch tin nhắn Anh/Nhật/Hàn/Trung → Việt trong Zalo, và trả lời bằng tiếng Việt (tự dịch ngược)."))

        col.addView(Button(this).apply {
            text = "1. Bật dịch vụ Zalo Dịch (Trợ năng)"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        col.addView(tv("Nếu Android 13+ báo \"Cài đặt bị hạn chế\": Cài đặt > Ứng dụng > Zalo Dịch > menu ⋮ > Cho phép cài đặt bị hạn chế, rồi bật lại.", 13f))

        val status = tv("", 13f)
        col.addView(Button(this).apply {
            text = "2. Tải gói dịch offline (cần mạng)"
            setOnClickListener {
                status.text = ""
                TranslatorEngine.downloadAll { msg -> runOnUiThread { status.append(msg + "\n") } }
            }
        })
        col.addView(status)

        col.addView(tv("3. Mở Zalo, vào một cuộc chat, chạm nút xanh \"譯\" nổi bên phải. Kéo để di chuyển nút.", 14f))

        setContentView(ScrollView(this).apply { addView(col) })
    }
}
