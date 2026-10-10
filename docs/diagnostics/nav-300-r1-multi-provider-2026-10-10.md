# 3.0 R1+R2 — một câu dẫn đường, nhiều app (NAV-MULTI-PROVIDER-ALL3 · NAV-BY-PROVIDER-SPLIT)

> Spec [`specs/kachi-300-plan.html`](../specs/kachi-300-plan.html) R1 · R2. Nhãn: **[ĐO]** · **[SUY]** · **[ĐOÁN]** · **[CHƯA BIẾT]**.
> Riêng tư: chỉ dùng nơi công cộng (chợ Bến Thành, Bitexco); toạ độ trong log dưới đây đã che.

## 1. Bằng chứng xe (10/10, Seal fw 2606, Kachi 2.99)

- *"dẫn đường đến … bằng google maps và vietmap"* ⇒ chỉ Google Maps dẫn; VietMap nhận `am start -a MAIN -c LAUNCHER -n vn.vietmap.live/.MainActivity` (không điểm đến) [ĐO log 21:24:52].
- *"dẫn đường về nhà bằng vietmap"* (một app) ⇒ `VIEW vietmaplive://companion/navigation?lat=…&lng=…&poiName=…` ⇒ VietMap dẫn [ĐO].
- Waze `waze://?ll=<lat>,<lng>&navigate=yes` bắn bằng adb ⇒ Waze dẫn [ĐO, owner xác nhận].
- *"dẫn đường đến công ty một bằng vietmap và google"* ⇒ `Nav(công ty một bằng vietmap và google)` [ĐO log].

## 2. Gốc [ĐO off-car, `VoiceIntentParser.parse` cây 2.99]

| Câu | Ý định 2.99 |
|---|---|
| *"dẫn đường đến chợ Bến Thành bằng Google Maps, VietMap, và Waze"* | `[Nav(query="chợ Bến Thành bằng Google Maps VietMap và Waze", app=null)]` |
| *"… bằng google maps và vietmap"* | `[Nav(query="chợ bến thành bằng google maps và vietmap", app=null)]` (nhãn app đã cài thử 4 kiểu — cùng kết quả) |

- [SUY source] `VoiceTailClause.appAfterMarker` chỉ nhận MỘT tên app chạm cuối câu ⇒ đuôi là danh sách thì không khớp ⇒ cả đuôi thành TÊN điểm đến, `app=null` ⇒ app mặc định. Tách câu ghép ở *"và"* không cứu: vế *"vietmap"* trần không hiểu được ⇒ cổng *"mọi vế phải hiểu được"* bỏ phép tách.
- [SUY, khớp log] dòng `MAIN/LAUNCHER` của VietMap = nhánh `coords == null` của `VoiceTargetDispatch.runNav` (tra toạ độ hỏng vì chuỗi rác ⇒ mở app trơn) — đúng ca bài `VoiceNavFallbackTest` *"mặc định VietMap + geocode hỏng ⇒ mở VietMap trơn"*. [CHƯA BIẾT] app mặc định trên xe lúc đó (log thô không có trong repo).
- Tầng thi hành KHÔNG lỗi: mỗi `Nav(q, app)` đơn app đã giao đúng deep-link của app ấy (xe: VietMap một mình dẫn được).

## 3. Sửa

`VoiceNavProviders.expand` (`:core`, mới, 85 dòng), gọi ở `VoiceIntentParser.parse` TRƯỚC phép tách *"và"*: đuôi từ cụm đánh dấu (*bằng/trên/với/qua/dùng*) tới hết câu phủ trọn bởi ≥ 2 tên app dẫn đường (mỗi tên qua đúng các tầng của `appAfterMarker`: tên đã dạy · chính xác · rụng âm cuối · neo tiền tố), xen giữa chỉ có *và/hoặc/với/cùng/and/or/with* ⇒ phân tích câu MỘT app (tên đầu) rồi nhân ra mỗi app một ý định cùng điểm đến. Không đường thi hành mới — mỗi ý định đi `runNav`/`runNavSaved` có sẵn. `splitOnConnectors` dời sang `VoiceClauseSplit.onConnectors` (trần 500 dòng; parser 488).

Chi phí: 0 luồng/nhịp/listener/lệnh shell. Mỗi câu: quét cụm đánh dấu; chỉ khi gặp cụm đánh dấu mới tra bảng tên (≤ 4 độ dài × số vị trí đuôi).

## 4. Đo máy ảo (`emulator-5556` kachi_play, vehicleTest cây 3.0, cầu `say`) [ĐO]

Câu kiểm của backlog: `say "dẫn đường đến chợ Bến Thành bằng Google Maps, VietMap, và Waze"` ⇒ `intents = Nav ×3`, rồi `ActivityTaskManager START`:

```
21:54:40.955 START {act=VIEW dat=google.navigation:q=chợ Bến Thành flg=0x10008000 pkg=com.google.android.apps.maps}
21:54:40.968 START {act=VIEW dat=waze://?q=chợ Bến Thành&navigate=yes flg=0x10000000 pkg=com.waze}
21:54:41.705 START {act=VIEW dat=vietmaplive://companion/navigation?lat=<lat>&lng=<lng>&poiName=chợ Bến Thành pkg=vn.vietmap.live}
21:54:44.155 START {dat=waze://?q=chợ Bến Thành&navigate=yes cmp=com.waze/.MainActivity} from uid 10149   ← Waze tự chuyển tiếp
```

Biến thể chuỗi mô hình (*"… bằng google map việt map và quây"*): 3/3 app nhận deep-link điểm đến (20.737 GMaps · 20.754 Waze · 21.900 VietMap).

- Không app nào nhận `MAIN/LAUNCHER`. VietMap tra toạ độ mất ~0,75 s / ~1,15 s (luồng nền) rồi mới bắn.
- **App lên trước cùng** [ĐO]: lượt 1 = Waze (`mResumedActivity com.waze/.MainActivity`) vì Waze tự mở lại `MainActivity` 3,2 s sau ý định của Kachi; lượt 2 VietMap bắn sau cùng. ⇒ app nằm trên cùng do app nào xong chuỗi activity của nó sau cùng, Kachi không quyết định. Quyết định thiết kế: KHÔNG bắn lại ý định để "kéo" một app lên (bắn lại `google.navigation` kèm `CLEAR_TASK` là huỷ tuyến vừa dựng — [ĐO xe 29/09]). Thứ tự bắn = thứ tự nói. Cụm (cast) không đổi: app chiếu lên cụm vẫn do cài đặt chiếu chọn.

## 5. Còn cho xe

- [CHƯA BIẾT] Waze trên xe với `waze://?q=<chữ>&navigate=yes` có dẫn thẳng không (máy ảo: resolve đúng nơi, tính tuyến hỏng vì thiếu GPS). Xe đã chứng minh `ll=` dẫn được; nếu `q=` dừng ở màn kết quả ⇒ đổi Waze sang đường toạ độ (dữ liệu bảng `coord` đã có — một dòng).
- Nói câu kiểm trên xe (TẮT nhạc hoặc bản R4): cả 3 app phải dẫn tới Bến Thành; ghi app nào nằm trên cùng.
