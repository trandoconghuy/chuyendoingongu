# Backend AI cho Zalo Dịch

Backend hỗ trợ Gemini và OpenAI, nhận tối đa 15 tin nhắn để nhận diện/dịch theo ngữ cảnh. Không đưa API key vào mã Android hoặc APK.

Yêu cầu Node.js 20+:

```powershell
$env:AI_PROXY_TOKEN="mot-chuoi-bi-mat-dai-va-kho-doan"
$env:GEMINI_API_KEY="..."
node server.mjs
```

Mặc định backend dùng alias `gemini-flash-latest`; Gemini API sẽ tự ánh xạ alias này sang model Flash hiện hành mà API key được phép truy cập. Hãy tạo API key từ Google AI Studio. Gemini API không có tham số bắt buộc request phải miễn phí; trạng thái Free Tier phụ thuộc project chứa API key và model mà alias đang trỏ tới.

`AI_PROVIDER` mặc định là `gemini`. Chỉ khi chủ động muốn dùng dịch vụ khác mới đặt `openai` hoặc `auto`. Chế độ `auto` ưu tiên Gemini rồi fallback OpenAI:

```powershell
$env:GEMINI_API_KEY="..."
$env:OPENAI_API_KEY="sk-..."
$env:AI_PROVIDER="auto"
```

Mặc định Gemini dùng `gemini-flash-latest`, server chạy cổng `8787`. Có thể khóa một model cụ thể bằng `GEMINI_MODEL` hoặc đổi cổng bằng `PORT`. Khi triển khai, đặt server sau HTTPS rồi nhập URL gốc cùng `AI_PROXY_TOKEN` vào app.

Kiểm tra server:

```text
GET /health
POST /detect-languages
```

Ứng dụng chỉ gửi chat khi người dùng đã lưu một URL backend. Nếu backend lỗi hoặc hết thời gian chờ, ứng dụng tự dùng kết quả nhận diện trên thiết bị.
