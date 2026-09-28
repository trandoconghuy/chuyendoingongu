# Zalo Dịch (MVP)

## Tải APK

[**Tải trực tiếp ZaloDich-v0.2.0.apk**](https://github.com/trandoconghuy/chuyendoingongu/releases/latest/download/ZaloDich-v0.2.0.apk)

App Android dịch tin nhắn Zalo (Anh/Nhật/Hàn/Trung → Việt) và trả lời bằng tiếng Việt (tự dịch ngược, điền vào ô chat, bạn tự bấm Gửi).

## Build APK bằng GitHub (không cần Android Studio)
1. Tạo repo GitHub mới, đẩy toàn bộ thư mục này lên nhánh `main`
   (nhớ có thư mục ẩn `.github/workflows/build-apk.yml`; nếu thiếu, tạo bằng Add file > Create new file với đúng đường dẫn đó).
2. Vào tab **Actions** > workflow **Build APK** > chờ xanh (~5-8 phút) > tải artifact **ZaloDich-apk**, giải nén ra `app-debug.apk`.
3. Chép APK vào điện thoại và cài (cho phép cài từ nguồn không xác định).

## Cài đặt trên điện thoại
1. Mở app **Zalo Dịch** > bấm "Bật dịch vụ" > bật **Zalo Dịch** trong Trợ năng.
   Android 13+: nếu bị chặn, vào Cài đặt > Ứng dụng > Zalo Dịch > ⋮ > *Cho phép cài đặt bị hạn chế*.
2. Bấm "Tải gói dịch offline" (cần mạng, làm 1 lần).
3. Mở Zalo > vào chat > chạm nút xanh "譯".

## Nhận diện ngôn ngữ bằng AI và ngữ cảnh

App luôn chạy ML Kit, nhận diện bảng chữ Nhật/Hàn/Trung và suy luận từ câu lân cận ngay trên điện thoại. Để nhận diện tốt hơn với chat rất ngắn, tiếng Việt không dấu và câu trộn ngôn ngữ, triển khai server Gemini/OpenAI trong `backend/`, sau đó nhập URL HTTPS của server vào mục **AI nhận biết ngữ cảnh** trong app.

Khi popup đang mở, app theo dõi sự kiện cuộn/nội dung của Zalo và tự dịch lại vùng chat đang nhìn thấy. Bản dịch trên thiết bị xuất hiện trước; nếu đã bật AI, kết quả sẽ được tinh chỉnh theo tên người gửi, chiều tin nhắn và mạch hội thoại, kèm ghi chú ngữ cảnh khi cần.

Khóa Gemini/OpenAI chỉ được đặt trong biến môi trường `GEMINI_API_KEY`/`OPENAI_API_KEY` của backend, không được ghi vào app Android.
