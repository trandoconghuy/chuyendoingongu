# Backend AI cho Zalo Dịch

Backend hỗ trợ Gemini và OpenAI, nhận tối đa 15 tin nhắn để nhận diện/dịch theo ngữ cảnh. Không đưa API key vào mã Android hoặc APK.

Yêu cầu Node.js 20+:

```powershell
$env:AI_PROXY_TOKEN="mot-chuoi-bi-mat-dai-va-kho-doan"
$env:GEMINI_API_KEY="..."
$env:AI_PROVIDER="gemini"
node server.mjs
```

`AI_PROVIDER` nhận `gemini`, `openai` hoặc `auto`. Chế độ `auto` ưu tiên Gemini rồi fallback OpenAI nếu đã cấu hình cả hai khóa:

```powershell
$env:GEMINI_API_KEY="..."
$env:OPENAI_API_KEY="sk-..."
$env:AI_PROVIDER="auto"
```

Mặc định Gemini dùng `gemini-3.8-flash`, OpenAI dùng `gpt-6-astra`, server chạy cổng `8787`. Có thể đổi bằng `GEMINI_MODEL`, `OPENAI_MODEL` và `PORT`. Khi triển khai, đặt server sau HTTPS rồi nhập URL gốc cùng `AI_PROXY_TOKEN` vào app.

Kiểm tra server:

```text
GET /health
POST /detect-languages
```

Ứng dụng chỉ gửi chat khi người dùng đã lưu một URL backend. Nếu backend lỗi hoặc hết thời gian chờ, ứng dụng tự dùng kết quả nhận diện trên thiết bị.
