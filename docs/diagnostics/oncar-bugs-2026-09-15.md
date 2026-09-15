# Hai lỗi trên xe 2026-09-15 — YouTube co vào giữa · Voice không chạy

- **Ngày:** 2026-09-15 · **Chủ:** dangkhoi · **Xe:** DiLink3 (DL3), Android 10 (API 29), Kachi uid 10135 (KHÔNG platform-sign), bản 1.55 đang cài.
- **Cách lấy:** adb vào xe qua cầu TCP loopback (`No route to host` trực tiếp → bridge `127.0.0.1:5556 → <ip-xe>:5555`). Dump `dumpsys display/window`, logcat, và kéo APK YouTube/GMaps của xe về máy.
- **Mức bằng chứng** (CLAUDE.md §2): **[ĐO]** = đọc dump/log thật; **[SUY]** = suy luận khớp; **[CHƯA BIẾT]** = cần đo thêm.

---

## Lỗi 1 — YouTube (và app "kiểu điện thoại") play bị co vào giữa, không lấp đầy ô — ĐÃ VÁ

### Triệu chứng
Owner: *"youtube khi play bị scale vào giữa, không full"*. Ảnh cụm thật: ô chứa YouTube hiện video nhỏ ở giữa-trên, danh sách phát chiếm cột dọc, hai bên đen.

### Gốc rễ [ĐO]
Ô app dùng **màn ảo** (`VdAppHost` tạo qua `createVirtualDisplay`) làm màn phụ để render app không caption (kiểu Dudu). Dump `dumpsys display` xe:

```
kachi-slot-1 (displayId 28): base 1401 x 748 (ngang), FLAG_PRIVATE, FLAG_NEVER_BLANK, FLAG_OWN_CONTENT_ONLY
  mOverrideDisplayInfo: rotation 3 (270°) → app 748 x 1401 (DỌC)
```
Cửa sổ YouTube trên màn đó: `mBounds=Rect(0,0-748,1401)`, `mRotation=ROTATION_270`, config `port`.

⇒ Màn ảo ô được tạo với cờ `8 | 256` (OWN_CONTENT_ONLY | DESTROY_ON_REMOVAL) — là màn **PRIVATE thường**, KHÔNG phải PRESENTATION. Trên Android 10, màn phụ loại này **XOAY theo hướng app yêu cầu**. YouTube ở khung ~sw598dp (1401×748@200dpi) tự nhận là "điện thoại" và đòi PORTRAIT ⇒ WM xoay màn ảo 270° thành dọc 748×1401. Video 16:9 co vào bề ngang 748px rồi nằm giữa, hai bên đen. **Đây là ROUTE/CỜ sai, KHÔNG phải mật độ (density)** — xem chứng minh dưới.

### Chứng minh cơ chế [ĐO emulator 2026-09-15]
Tạo màn overlay đúng khung xe (`settings put global overlay_display_devices "1401x748/200"` → displayId 50, có `FLAG_PRESENTATION`), rồi `am start --display 50` YouTube:
- **Kết quả: YouTube LANDSCAPE, lấp đầy 1401×748, `mRotation=ROTATION_0`, config `land`** — ở CÙNG sw598dp mà màn ảo ô của xe bị dọc. Khác biệt DUY NHẤT là cờ PRESENTATION. ⇒ density KHÔNG phải nguyên nhân; **cờ PRESENTATION** mới quyết định xoay hay không.

### Bản vá
`VdAppHost.createVirtualDisplay(... , 2 or 8 or 256)` — thêm **`FLAG_PRESENTATION` (2)**. Màn PRESENTATION không xoay theo app ⇒ ô giữ hướng ngang, app tự bày landscape lấp đầy. Cờ này có từ API 19, chạy off-platform-sign trên Android 10 (khác `wm set-fix-to-user-rotation -d` chỉ chắc từ API 30).

**Tương tác đã xử lý:** màn ô nay lọt vào `DISPLAY_CATEGORY_PRESENTATION`. `SpeedBadgeOverlay.resolveClusterDisplay` (bộ dò màn cụm cho badge tốc độ) nay **loại** các màn tên `kachi-slot-*` để badge không bám nhầm vào một ô. Màn cụm thật (fission/xdja) không mang tên đó.

### Xác nhận E2E [ĐO emulator, bản đã vá]
Đặt YouTube vào ô 0 (QUAD), mở Kachi: `dumpsys display` cho `kachi-slot-0` (displayId 51) nay có **`FLAG_PRESENTATION`**, giữ `rotation 0`, app `929 x 748`→landscape; ảnh chụp: YouTube render **ngang lấp đầy ô**. Trước vá (xe): dọc 748×1401, video giữa.

**Còn lại (xe):** xác nhận cuối trên chính xe DL3 phiên sau — cài 1.56, mở YouTube vào ô, xem video play có full ngang không.

---

## Lỗi 2 — Voice command không chạy — CHƯA VÁ ĐƯỢC (chờ 1 lượt thu tiếng trên xe), đã thêm CHẨN ĐOÁN

### Đã đo được [ĐO]
- Quyền `RECORD_AUDIO`: **granted=true**.
- Mic **mở bình thường**: `KachiVoiceMic: micro mở bằng nguồn 6 (VOICE_RECOGNITION, 16kHz)`, `VoiceOverlay` vẽ ra. Cổng chặn của phiên nghe (`!hasPermission` → `no_mic`, `!isReady` → `no_model`) **đều qua** ⇒ mô hình sherpa ĐÃ tải trên xe.
- Nền phần cứng mic là **4 kênh** (`platform_set_codec_backend_cfg: ... channels 4`) trong khi app mở mono → framework phải trộn 4→1.
- `.so` sherpa đóng đúng cho **arm64** (ABI xe). `VoiceEngine.build` có log lỗi nếu nạp hỏng — **không có** lỗi đó ⇒ engine dựng được (đã cache trước lượt đo).
- **KHÔNG** có dòng kết quả nhận dạng nào — nhưng lượt đo là kích qua deep-link từ máy, **không ai nói vào mic xe được từ xa**, nên "rỗng" là đúng kỳ vọng của lượt đo này, KHÔNG phải bằng chứng lỗi.

### Kết luận mức chắc
- Engine sherpa **lành** (đóng gói đúng, nạp được, off-car eval 87% — `vn-stt-sherpa-emulator-eval-2026-09-14.md`). ⇒ Lỗi nằm ở **âm thanh mic THẬT trên xe** khi owner nói: nghi (a) mic-array 4 kênh trộn ra mono kém/câm, (b) ồn, (c) mức tín hiệu. **[CHƯA BIẾT]** — không tái hiện được từ xa.

### Đã thêm (để một lượt nói trên xe là chốt được)
1. `VoiceCapture`: log **đỉnh biên độ + RMS** mỗi lượt nghe (`mức micro: đỉnh X/32767 · rms Y · N mẫu`). Đỉnh ~0 = mic câm; đỉnh kịch 32767 liên tục = clip/méo (nghi 4-kênh ghép sai); đỉnh vừa mà ra rỗng = chất lượng/định dạng.
2. `VoiceRecognizer`: log **chữ THÔ sherpa trả về** (kể cả rỗng) — tách "engine ra gì" khỏi "NLU hiểu gì".

### Việc cần trên xe (phiên sau)
Owner nói một câu vào mic xe → đọc 2 dòng log trên. Nếu đỉnh ~0 hoặc clip: sửa đường mic (thử `AudioSource.MIC`, hoặc mở đúng số kênh rồi trộn tay, hoặc chọn 1 kênh sạch bằng `setChannelIndexMask`). Nếu đỉnh vừa mà chữ rỗng: soi định dạng/feature. **KHÔNG ship phán đoán khi chưa có dữ liệu này (CLAUDE.md §14).**

---

## Vật tư kéo về (off-car, scratchpad — KHÔNG commit)
`youtube.apk` (182 MB), `gmaps.apk` (80 MB) — APK YouTube/GMaps của chính xe, để cài vào emulator test. ⚠ [ĐO] không cài đè được lên bản hệ thống của emulator (lệch chữ ký) ⇒ dùng bản YouTube sẵn của emulator để tái hiện cơ chế; APK xe để dành cho AVD sạch nếu cần đúng phiên bản.
