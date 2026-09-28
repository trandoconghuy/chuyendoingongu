package com.zalodich

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.app.Activity
import android.text.InputType
import java.net.URL

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

        col.addView(tv("AI nhận biết ngữ cảnh", 19f))
        col.addView(tv("Nhập địa chỉ backend Gemini/OpenAI của bạn. Khi bật, tối đa 15 tin nhắn đang thấy sẽ được gửi tới backend để suy đoán và dịch theo cả đoạn chat. Để trống nếu chỉ muốn xử lý trên điện thoại.", 13f))
        val aiEndpoint = EditText(this).apply {
            hint = "https://ten-backend-cua-ban.example.com"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(AiContextEngine.endpoint(this@MainActivity))
            setSingleLine(true)
        }
        col.addView(aiEndpoint)
        val aiToken = EditText(this).apply {
            hint = "Mã truy cập backend"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(AiContextEngine.token(this@MainActivity))
            setSingleLine(true)
        }
        col.addView(aiToken)
        col.addView(Button(this).apply {
            text = "Lưu cấu hình AI"
            setOnClickListener {
                val value = aiEndpoint.text.toString().trim().trimEnd('/')
                val token = aiToken.text.toString().trim()
                val valid = value.isBlank() || runCatching {
                    val url = URL(value)
                    url.protocol == "https"
                }.getOrDefault(false)
                if (!valid || (value.isNotBlank() && token.isBlank())) {
                    Toast.makeText(this@MainActivity, "Địa chỉ AI phải là URL HTTPS hợp lệ", Toast.LENGTH_LONG).show()
                } else {
                    AiContextEngine.saveConfig(this@MainActivity, value, token)
                    Toast.makeText(
                        this@MainActivity,
                        if (value.isBlank()) "Đã tắt AI ngữ cảnh" else "Đã bật AI ngữ cảnh",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })

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
