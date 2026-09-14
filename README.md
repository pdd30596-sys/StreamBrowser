# Stream Browser V1

Trình duyệt Android chuyên xem video online, phát hiện luồng video và chuyển sang player riêng/MX Player, đồng thời có tải video bằng yt-dlp + FFmpeg.

## Chức năng V1

- WebView đầy đủ JavaScript, DOM storage, phát media không cần chạm lại.
- Chặn host quảng cáo/tracker ở tầng request (WebView + Service Worker khi WebView hỗ trợ).
- Chặn popup / cửa sổ mới.
- Phát hiện video qua 2 đường:
  - request mạng: `.m3u8`, `.mpd`, `.mp4`, `.webm`, một số endpoint video;
  - DOM: quét `video`, `source` và Performance Resource entries.
- Danh sách video phát hiện với 3 nút: **Xem**, **MX Player**, **Tải**.
- Player tích hợp bằng AndroidX Media3/ExoPlayer, hỗ trợ HLS/DASH/MP4/WebM.
- Picture-in-Picture (PiP).
- Nhớ vị trí xem gần nhất theo URL.
- Mở luồng qua MX Player Free/Pro nếu máy đã cài; nếu không thì mở chooser Android.
- Tải bằng `youtubedl-android` + FFmpeg; lưu vào `Download/StreamBrowser` qua MediaStore.
- Nhận link từ Android Share và ACTION_VIEW.
- Chế độ desktop User-Agent.
- Lịch sử file đã tải.

## Công nghệ

- Kotlin 2.4.20
- Android Gradle Plugin 9.4.0
- compileSdk/targetSdk 36, minSdk 29
- Jetpack Compose
- AndroidX WebKit 1.17.0
- AndroidX Media3 1.11.0
- youtubedl-android 0.18.1 + FFmpeg
- JDK 17

## Build bằng Android Studio

1. Giải nén project.
2. Mở thư mục `VideoBrowser_V1` bằng Android Studio.
3. Chờ Gradle Sync và Android SDK 36 tải xong.
4. Chọn **Build > Build APK(s)**.
5. APK debug ở:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Hoặc Windows:

```bat
gradlew.bat assembleDebug
```

## Build bằng GitHub không cần Android Studio

Project đã có `.github/workflows/build-apk.yml`.

1. Tạo repository GitHub mới.
2. Upload toàn bộ nội dung project vào nhánh `main`.
3. Mở tab **Actions > Build Android APK > Run workflow**.
4. Khi job hoàn tất, tải artifact `StreamBrowser-V1-debug`.

## Cách dùng

1. Mở một website có video.
2. Bấm phát video trên trang một lần.
3. Khi app phát hiện luồng, nút `🎬` sẽ hiện số lượng video.
4. Vào tab **Video**.
5. Chọn:
   - **Xem**: player riêng của app;
   - **MX Player**: gửi link cho MX Player;
   - **Tải**: yt-dlp tải về máy.

Nếu chưa thấy luồng, bấm **Quét video** trên thanh Browser sau khi video đã bắt đầu phát.

## Giới hạn quan trọng

- Không vượt DRM/Widevine.
- Không thể đảm bảo chặn 100% quảng cáo. Quảng cáo được ghép trực tiếp vào cùng stream (SSAI) không thể tách chỉ bằng adblock URL.
- Một số stream dùng `blob:`/MSE chỉ có thể phát trong WebView nếu không tìm được URL manifest thật.
- Link có chữ ký có thể hết hạn nhanh.
- Một số server yêu cầu cookie/header phiên đăng nhập. Player tích hợp cố truyền Cookie/Referer/User-Agent; MX Player ngoài app có thể không nhận đủ header nên có link phát trong app được nhưng MX không phát.
- Danh sách host chặn quảng cáo V1 là danh sách bảo thủ để giảm nguy cơ làm hỏng website. Có thể nâng cấp sau thành EasyList/AdGuard parser.

## Hướng V1.1/V2 đề xuất

- Foreground Download Service: tải tiếp khi khóa màn hình/chuyển app.
- Trình quản lý nhiều tab.
- Import EasyList/AdGuard filter list và cosmetic filtering.
- Tự đánh điểm luồng để bỏ qua quảng cáo/video ngắn và ưu tiên stream chính.
- Chọn track/độ phân giải/subtitle trong player.
- Fullscreen xoay ngang, gesture âm lượng/độ sáng/tua giống MX Player.
- Bookmark, lịch sử duyệt, private mode.
