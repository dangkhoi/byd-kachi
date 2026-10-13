# Đo máy ảo A10: overscan top ÂM có kéo nội dung app lên không? (2026-10-13)

> Spec: `docs/specs/kachi-301-cast-steps-dialog-ia.html` §2.2, R5 (T6). Chỉ đo, KHÔNG đổi code.
> Bằng chứng thô: `docs/diagnostics/emu-overscan-neg-2026-10-13/` (mỗi ca 1 `.txt` dump + 1 `.png` 960×360 của màn phụ).
> Script tái lập: `emu-overscan-neg-2026-10-13/measure.sh` (áp overscan → chờ 2 s → DisplayFrames + `am stack list` + khối cửa sổ + `screencap -d 1`), `diff.py` (diff pixel + ước lượng độ dịch dọc).

## Kết luận

| Câu hỏi | Trả lời | Mức |
|---|---|---|
| Overscan top âm có kéo nội dung lên với **task toàn màn**? | **KHÔNG** với cửa sổ activity thường (Settings, Đồng hồ, Google Maps): `mFrame`, `parent`, insets và **pixel** y hệt 0 / −40 / −120. | [ĐO] |
| Với **task freeform** (`am task resize 0 0 1920 720`)? | **KHÔNG**, kể cả overscan DƯƠNG cũng không có tác dụng (frame theo bounds task, insets = 0). | [ĐO] |
| Cửa sổ nào thì ăn? | Chỉ cửa sổ **không** có `FLAG_LAYOUT_IN_SCREEN` (VD Chrome FirstRunActivity: `wrapxwrap`, `fl=DIM_BEHIND…`) — `parent` = `mUnrestricted` = `[0,-T]`, cửa sổ dịch lên (căn giữa ⇒ dịch T/2; pixel dịch 20/60 px). | [ĐO] |
| Cờ nào chặn? | `FLAG_LAYOUT_IN_SCREEN` (+`LAYOUT_INSET_DECOR`) — có ở **cả 3** cửa sổ activity thường đã đo, bất kể `vsysui` (`LAYOUT_HIDE_NAVIGATION` ở Settings, `LAYOUT_FULLSCREEN` ở Đồng hồ/Maps). Với các cửa sổ này `parent` lấy từ `mOverscan`/`mRestrictedOverscan`, mà overscan âm KHÔNG đổi hai frame đó (vẫn `[0,0][1920,720]`); còn inset overscan bị kẹp ≥ 0 nên app không nhận "inset âm". | [ĐO] dump DisplayFrames + insets; [SUY] cơ chế kẹp ≥ 0 |
| Vì sao overscan DƯƠNG vẫn chạy ngoài xe? | Dương đi đường **inset**: `Cur insets: overscan=[0,40]…content=[0,40]`, `mFrame` giữ nguyên, decor app tự đệm ⇒ pixel dịch xuống đúng 40 px (Maps, Đồng hồ). Đường này không có nửa âm. | [ĐO] |
| `am task resize` top/left âm? | Bị từ chối: `Error: bad top arg: -40` / `bad left arg: -20`, bounds không đổi. | [ĐO] |
| `am task resize` tràn phải/dưới (freeform)? | **Nhận**: `0 0 1920 800` ⇒ bounds `[0,0][1920,800]`, `mFrame=[0,0][1920,800]`; `0 0 2000 760` ⇒ `[0,0][2000,760]`. (Đường cho R7.) | [ĐO] |
| `wm size` = vật lý + overscan âm? | `wm size 1920x720 -d 1` bằng đúng kích vật lý ⇒ WM coi là không override (readback chỉ "Physical size", xml không có forcedWidth); overscan âm vẫn không đổi gì. | [ĐO] |
| Overscan âm có ghi bền? | Có: `display_settings.xml` ⇒ `<display name="overlay:1" overscanTop="-120" />`; sau `wm overscan reset -d 1` ⇒ `<display name="overlay:1" />`. | [ĐO] |

**Phán quyết R5 trên máy ảo: ĐỎ.** Overscan top âm không kéo được khung bản đồ lên cho app nav kiểu activity thường, ở cả toàn màn lẫn freeform. Trên máy ảo, "Dịch nội dung" qua overscan âm sẽ là nút bấm không ăn.

## Bảng đo

Màn phụ: `overlay_display_devices 1920x720/240` ⇒ displayId 1, `uniqueId overlay:1`. "Pixel" = `diff.py` so với ảnh overscan 0 của cùng ca (ảnh 960 px ngang, ×2 ra px thật).

| App · cửa sổ (cờ) | Chế độ | Overscan (l,t,r,b) | `mFrame` | `parent` | Insets overscan | Pixel | Mức |
|---|---|---|---|---|---|---|---|
| Settings · `fl=LAYOUT_IN_SCREEN LAYOUT_INSET_DECOR`, `vsysui=LAYOUT_STABLE LAYOUT_HIDE_NAVIGATION` | toàn màn | 0,0,0,0 | [0,0][1920,720] | [0,0][1920,720] | 0 | gốc | [ĐO] |
| 〃 | toàn màn | 0,−40,0,0 | [0,0][1920,720] | [0,0][1920,720] | 0 | giống hệt | [ĐO] |
| 〃 | toàn màn | 0,−120,0,0 | [0,0][1920,720] | [0,0][1920,720] | 0 | giống hệt | [ĐO] |
| 〃 + `wm size 1920x720 -d 1` | toàn màn | 0,−40 / 0,−120 | [0,0][1920,720] | [0,0][1920,720] | 0 | giống hệt | [ĐO] |
| Đồng hồ · `LAYOUT_IN_SCREEN LAYOUT_INSET_DECOR`, `vsysui=LAYOUT_STABLE LAYOUT_FULLSCREEN` | toàn màn | 0 / −40 / −120 | [0,0][1920,720] | [0,0][1920,720] | 0 | giống hệt | [ĐO] |
| 〃 | toàn màn | 0,**+40**,0,0 (đối chứng) | [0,0][1920,720] | [0,0][1920,720] | **[0,40]** | (inset nhận) | [ĐO] |
| Google Maps · `LAYOUT_IN_SCREEN LAYOUT_INSET_DECOR`, `vsysui=LAYOUT_FULLSCREEN` | toàn màn | 0 / −40 / −120 | [0,0][1920,720] | [0,0][1920,720] | 0 | giống hệt | [ĐO] |
| 〃 | toàn màn | 0,**+40**,0,0 (đối chứng) | [0,0][1920,720] | [0,0][1920,720] | **[0,40]** | dịch **xuống 40 px** | [ĐO] |
| Chrome FirstRunActivity · `wrapxwrap`, `fl=DIM_BEHIND SPLIT_TOUCH HW_ACCEL` (KHÔNG `LAYOUT_IN_SCREEN`) | toàn màn | 0,0,0,0 | [630,36][1290,684] | [0,0][1920,720] | 0 | gốc | [ĐO] |
| 〃 | toàn màn | 0,+40,0,0 | [630,56][1290,704] | [0,40][1920,720] | 0 | dịch xuống 20 px | [ĐO] |
| 〃 | toàn màn | 0,−40,0,0 | [630,16][1290,664] | **[0,−40][1920,720]** | 0 | **dịch lên 20 px** | [ĐO] |
| 〃 | toàn màn | 0,−120,0,0 | [630,−24][1290,624] | **[0,−120][1920,720]** | 0 | **dịch lên 60 px** | [ĐO] |
| Đồng hồ (freeform, bounds [0,0][1920,720]) | freeform | 0 / +40 / −40 / −120 | [0,0][1920,720] | [0,0][1920,720] | 0 | giống hệt | [ĐO] |
| Google Maps (freeform, bounds [0,0][1920,720]) | freeform | 0 / +40 / −40 / −120 | [0,0][1920,720] | [0,0][1920,720] | 0 | giống hệt | [ĐO] |

Trong MỌI ca overscan âm, `DisplayFrames` của display 1 vẫn đổi: `mUnrestricted/mContent/mStable/mRestricted=[0,-T][1920,720]`, `mDisplayInfoOverscan=[0,-T][0,0]`, còn `mOverscan` và `mRestrictedOverscan` giữ `[0,0][1920,720]` [ĐO]. ⇒ **DisplayFrames đổi KHÔNG chứng minh app bị kéo** — dùng nó để xác minh là dương tính giả.

## Lệnh chính xác

```
adb root                                                     # google_apis A10: được (uid=0)
settings put global overlay_display_devices 1920x720/240     # trước: null
settings put global enable_freeform_support 1                # trước: null
settings put global force_resizable_activities 1             # trước: null
reboot                                                       # freeform chỉ có hiệu lực sau reboot (trước đó --windowingMode 5 ra fullscreen, resize ném "resizeTask not allowed")
am start --display 1 --windowingMode 1 -n <component>        # toàn màn trên display 1
wm overscan 0,-40,0,0 -d 1 ; wm overscan 0,-120,0,0 -d 1 ; wm overscan 0,40,0,0 -d 1
dumpsys window displays   (khối DisplayFrames của display 1)
dumpsys window windows    (khối "Window #… <pkg>": fl, vsysui, Frames, mFrame, Cur insets)
screencap -d 1 -p /sdcard/c.png                              # A10 máy ảo chụp được màn overlay (1920×720)
# freeform: overlay display KHÔNG nhận --windowingMode 5 (ra fullscreen). Cách được:
am start --display 0 --windowingMode 5 -n <component> ; am display move-stack <stackId> 1 ; am task resize <taskId> 0 0 1920 720
am task resize 75 0 0 1920 800 | 0 0 2000 760 | 0 -40 1920 720 | -20 0 1920 720
wm size 1920x720 -d 1
```

Dọn (`cleanup.txt`): `wm overscan reset -d 1`, `wm size reset -d 1`, `settings delete global overlay_display_devices|enable_freeform_support|force_resizable_activities` (về `null` như trước), xác nhận display 1 đã gỡ, `display_settings.xml` không còn `overscanTop`; tắt máy ảo (`adb emu kill`; khởi chạy với `-no-snapshot-save`). Không chạm `com.byd.launcher`.

## Ghi chú / giới hạn của phép đo

- Màn phụ là **overlay display** (`FLAG_PRESENTATION`, không system decor), không phải VirtualDisplay AutoContainer của BYD. [ĐO] overlay display không cho khởi chạy thẳng freeform; phải `move-stack` từ display 0.
- Không tìm được cửa sổ **toàn kích (fill) không có `LAYOUT_IN_SCREEN`** trong các app có sẵn; việc cửa sổ như vậy sẽ bị kéo lên đúng T px chỉ là [SUY] từ ca Chrome (`parent` = `[0,-T]`).
- `md5` PNG của Đồng hồ freeform khác nhau nhưng `diff.py` cho `diffbbox=None` (chỉ khác metadata PNG).
- Lúc đo Maps hiện màn "Cập nhật Google Maps" (bản cũ trên image) — không ảnh hưởng layout cửa sổ.
- `rc=` trong `task-resize-and-size.txt` là rc của `head`, không phải của `am`; lấy kết quả từ chuỗi "Error:" và bounds đọc lại.

## [CHƯA BIẾT] với ROM BYD (DiLink3, A10)

- ROM BYD có sửa `DisplayPolicy.layoutWindowLw`/`DisplayFrames` không (vd cho cửa sổ app nhận `mUnrestricted` làm parent) — chưa đo. Chốt bằng: trên xe, `wm overscan 0,-40,0,0 -d <cụm>` rồi so `mFrame`/`parent` của cửa sổ VietMap trong `dumpsys window windows`.
- Cờ cửa sổ thật của **VietMap / Android Auto / CarPlay** trên cụm (có `LAYOUT_IN_SCREEN` không, hay là cửa sổ kiểu khác/SurfaceView riêng) — chưa đo; dump cũ không đủ.
- VD AutoContainer của BYD có ở chế độ freeform mặc định không, và overscan có áp lên VD đó như overlay display không — chưa đo (log xe chỉ có `wm overscan 0,0,0,0`).
- Hệ quả: máy ảo nói "không ăn" cho activity thường; trên xe vẫn phải đo 1 lệnh như trên trước khi kết luận chung cho DiLink3 (CLAUDE.md §14). Owner đã chốt không lên xe ⇒ nếu làm R6 thì chỉ được làm với tự-đo-lúc-chạy và mặc định ẩn.
