# Zalo Dịch (MVP)

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
