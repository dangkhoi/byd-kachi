package com.byd.clusternav.navigation

/**
 * **Một nguồn sự thật** cho danh sách app dẫn đường mà ClusterNav đọc.
 *
 * Trước 2026-08-22 danh sách này bị chép ở **5 nơi** (2 Kotlin ở `:app`, 1 XML, 2 Kotlin ở `:core`) cộng
 * thêm hàng chục bản sao lẻ theo hãng. Lúc khảo sát thì 5 bản đang trùng nội dung — nhưng không có test nào
 * canh, và bản nguy hiểm nhất là **XML** (`res/xml/nav_accessibility_config.xml` → `android:packageNames`):
 * đó là cổng của `system_server`, thiếu một gói ở đó thì framework **không giao AccessibilityEvent** cho ta
 * ⇒ app chết câm, không crash, không log, không test đỏ. Đúng kiểu lỗi CLAUDE.md §8 cảnh báo.
 *
 * XML không import được Kotlin nên nó vẫn phải liệt kê tay; cặp Kotlin↔XML được khoá bằng
 * `NavPackageRosterSyncTest`.
 */
object NavApps {

    /** VietMap Live — nguồn biển báo tốc độ + cảnh báo (widget), và nav khi app hiển thị. */
    const val VIETMAP_LIVE = "vn.vietmap.live"

    /**
     * Tiền tố resource-id của họ Waze.
     *
     * KHÔNG phải applicationId: tiền tố trong `AccessibilityNodeInfo.getViewIdResourceName()` là tên package
     * ghi trong `resources.arsc`. Đo tĩnh bằng `aapt2 dump resources` trên
     * `apk-ref/WazeMod_V9_Stable_05082026_Android10_DUAL.apk` (2026-08-22): manifest = `com.chisadin.wazemod`
     * nhưng `Package name=com.waze id=7f`. Waze chính chủ thì cả hai đều là `com.waze`.
     * ⇒ **một tiền tố này phủ cả bản zin lẫn bản mod**.
     */
    const val WAZE_RES_PREFIX = "com.waze"

    /** Google Maps: bản zin + bản ReVanced (cùng bố cục notification). */
    val GMAPS = setOf("com.google.android.apps.maps", "app.revanced.android.apps.maps")

    /**
     * Họ Waze. `com.waze` = zin (bản beta cũng dùng id này); `com.chisadin.wazemod` = WazeMod DUAL.
     * Lựa chọn "Waze" trong menu là cả NHÓM này, không phải một gói cụ thể.
     */
    val WAZE = setOf("com.chisadin.wazemod", WAZE_RES_PREFIX)

    val VIETMAP = setOf(VIETMAP_LIVE)

    /**
     * App phơi dữ liệu dẫn đường **CHỈ qua `contentDescription`**, KHÔNG có `resource-id`.
     *
     * Đây là một **khả năng ĐO ĐƯỢC**, không phải một cái tên gói bị hardcode để rẽ nhánh (CLAUDE.md §7):
     * VietMap Live là app Flutter ⇒ probe bộ view-id kiểu OpenBYD trả **rỗng** trên mọi cửa sổ của nó (đo
     * 2026-08-22, `OPENBYD-VIEWID-PROBE`), trong khi content-desc thì đọc được đủ cự ly + đường + ETA
     * ([VietMapDescParser]). App nào sau này cũng đo ra như vậy thì thêm vào ĐÂY — một nơi duy nhất.
     *
     * ⚠ RỖNG từ 2026-08-28: toàn bộ đường ĐỌC dẫn đường VietMap/Waze (screen-capture + a11y content-desc +
     * view-id) đã bị GỠ (owner chốt: chậm/lag/thiếu data). VietMap chỉ còn nuôi **speed badge** qua widget
     * (gói `vietmapwidget`, KHÔNG qua a11y), Waze không còn kênh nào. Google Maps đi đường NOTIFICATION. Vì thế
     * không app nào còn đọc bằng content-desc ⇒ tập này rỗng. Giữ lại `val` (thay vì xoá) vì API công khai.
     */
    val DESC_ONLY: Set<String> = emptySet()

    /**
     * App mà ClusterNav lấy dữ liệu dẫn đường **qua KÊNH NOTIFICATION** (`NavNotificationListener`).
     *
     * Đây là roster **KÊNH**, KHÁC roster **ĐỌC-ĐƯỢC** [ALL]: [ALL] là cổng của `system_server` cho a11y
     * (`android:packageNames`), còn tập này quyết định gói nào được `NavNotificationListener.handle()` xử lý.
     * Một gói ra khỏi đây thì **không mất kênh nào khác** — nó vẫn đọc được bằng a11y/widget/screen-capture.
     *
     * ⚠ NHƯNG NÓ TỪNG MẤT **BỀ MẶT** CỤM (đã sửa 08-23 vòng 3 — [P1]; giữ lại đây làm cảnh báo cho lần thu
     * roster sau). `handle()` là nơi DUY NHẤT gọi `ClusterNavLaneWidget.onNavActive` — clusterDebug op 39,
     * thứ DỰNG lớp nav OEM giữa cụm (on-car 2026-08-12). Thu roster này về [GMAPS] ⇒ VietMap mất luôn lệnh
     * dựng bề mặt đúng lúc mũi tên của nó vừa chuyển hẳn sang kênh ảnh. Đã nối lại ở `NavOutputOwner` (owner
     * của đường ảnh tự assert op-39 mỗi tick có khung; khoá bằng `NavOutputOwnerTest` nhóm "be mat" +
     * `NavCastUiWiringContractTest`). **Bài học chung: đường notification mang theo cả DỮ LIỆU lẫn
     * SIDE-EFFECT bề mặt op-39; rút một gói ra khỏi đây là rút cả hai.** (Bản 08-23 vòng 2 đếm BA — cái thứ
     * ba là "nhịp tim `PREFER_*`"; nó đã hết tồn tại từ B3.48, xem ngay dưới.)
     *
     * ✅ CÒN "NHỊP TIM CỦA CHẾ ĐỘ `PREFER_*`" — CẢNH BÁO CŨ NAY **KHÔNG CÒN ĐÚNG** (đóng 2026-08-23, backlog
     * **B3.48**). Bản 08-23 vòng 2 của KDoc này ghi rằng gỡ VietMap khỏi roster còn làm mất một side-effect
     * thứ hai: nhịp tim nuôi `SourceArbiter.isGroupFresh` — vế quyết định của `allowedByMode` ở mọi chế độ
     * `PREFER_*`. Cơ chế mô tả hồi đó ĐÚNG: sổ `lastSeenByPkg` chỉ còn đúng một writer là kênh ẢNH, mà kênh
     * ảnh chỉ đập nhịp khi phân loại được mũi tên (phủ sóng thật của `WazeArrowRegistry.VIETMAP_INK` là
     * **39/87 maneuver** sau khi B3.47 vòng 3 thay cổng cỡ tuyệt đối bằng tiêu chí tỉ số — trước đó chỉ
     * 27/87, và 60/87 khung còn trả về một icon POI bản đồ thay vì mũi tên), nên khoảng lặng >
     * `SourceArbiter.STALE_MS` (6 s) là chuyện bình thường ⇒ ở `PREFER_VIETMAP`, GMaps lọt cổng và HAI owner
     * cùng ghi `INSTRUMENT_GUIDE_INFO_SIMPLE_SET` (cự ly app này cạnh mũi tên app kia).
     *
     * CÁCH ĐÓNG — **không** nối lại nhịp tim, mà **gỡ hẳn thứ cần nhịp tim**. Owner chốt 2026-08-23 (nguyên
     * văn: *"nếu chọn auto thì lấy app nào có tín hiệu dẫn đường đầu tiên … còn chọn đích danh app thì luôn
     * lấy app đích danh, nếu app đó ko dẫn thì ko hiện gì, không cần auto switch làm gì cả"*). Từ đó
     * `SourceArbiter.allowedByMode` ở `PREFER_X` CHỈ còn `pkg in X`; cửa thoát `|| !isGroupFresh(X)` cùng sổ
     * `lastSeenByPkg`, hàm `noteSeen` và `isGroupFresh` đã bị **XOÁ** khỏi `SourceArbiter`. Cổng `PREFER_*`
     * không đọc mốc thời gian nào nữa ⇒ roster KÊNH này **không còn ảnh hưởng gì** tới chế độ ưu tiên.
     * ĐÁNH ĐỔI đã chấp nhận (ý owner, KHÔNG phải suy giảm cần bù): `PREFER_X` mà X không dẫn ⇒ **cụm im
     * lặng**. Khoá bằng `SourceArbiterAllowsTest` (nhóm B3.48) + `SourceArbiterTest` ở `:app`.
     *
     * ⇒ Hệ quả cho lần thu roster sau: bỏ một gói khỏi đây chỉ còn phải cân nhắc DỮ LIỆU + bề mặt op-39.
     * Và vẫn đừng "vá" bằng cách nhét lại VietMap vào roster này: làm thế là bật lại đúng cái khoá
     * DATA→IMAGE mô tả ngay bên dưới (B3.42).
     *
     * ── VÌ SAO PHẢI THU HẸP (cơ chế, đo được, không phải sở thích) ────────────────────────────────────
     * `NavNotificationListener.handle()` gọi `SourceArbiter.shouldFeed(pkg, …)` với kênh mặc định
     * [NavChannel.DATA] ⇒ nó **đóng mốc `lastDataByPkg[pkg]`** ⇒ trong 6 s sau đó `SourceArbiter.isDataFresh(pkg)`
     * = true ⇒ mọi publish kênh [NavChannel.IMAGE] của CHÍNH gói đó bị `shouldFeed` trả false
     * (`SourceArbiter.kt` — "ảnh là FALLBACK"). Với một app mà notification KHÔNG mang mũi tên, hệ quả là:
     * notification tự khoá mất kênh ảnh của chính nó, mũi tên biến mất, `hudIcon` rơi về hằng 11 = đi thẳng.
     * Đây đúng là cơ chế đã làm chết `CaptureArrowFallback` (backlog **B3.42**, đã revert 08-23).
     *
     * ── AI Ở TRONG / AI Ở NGOÀI (theo bằng chứng, không theo tên gói — §7) ────────────────────────────
     * · **GMaps (+ ReVanced)** — ✅ TRONG. Notification mang **đủ**: large-icon mũi tên + cự ly + tên đường,
     *   chạy được cả khi app ở NỀN (ma trận kênh đo 08-20,
     *   `docs/diagnostics/multi-app-nav-source-channels-2026-08-20.md` §1 hàng 1). Đường này đã proven ngoài
     *   hiện trường ⇒ CLAUDE.md §6: KHÔNG được đụng.
     * · **VietMap Live** — ❌ NGOÀI. Notification của nó có đường + cự ly (đo 08-20, §1 hàng 2) nhưng
     *   **KHÔNG có mũi tên**; mũi tên chỉ tồn tại dưới dạng pixel ⇒ phải đi kênh ảnh. Giữ nó ở kênh
     *   notification là tự tay bật cái khoá DATA→IMAGE mô tả ở trên. **OWNER CHỐT 08-23 (B3.42): VietMap đi
     *   HẲN đường screen-capture** (mũi tên + cự ly cùng một kênh, cùng một package).
     * · **Họ Waze** — ❌ NGOÀI. Đo 08-22 (backlog B3.32): notification lúc ĐANG DẪN chỉ có `tickerText=Waze`
     *   — không cự ly, không mã hướng rẽ; ma trận 08-20 ghi ❌ cho cả "mũi tên qua notification" LẪN
     *   "đường + cự ly (nền)". Tức kênh này **chưa từng cho Waze một byte dữ liệu nào**, nên bỏ ra = mất 0.
     *   Ngược lại, để lại là một cái bẫy im lặng: bất kỳ bản Waze/WazeMod nào sau này post một notification
     *   `category=navigation` (hoặc có token cự ly) sẽ đóng mốc DATA và **giết kênh ảnh của Waze** — đúng
     *   con bệnh vừa chữa cho VietMap, mà không có test nào đỏ. Waze đã có đường riêng đã đo:
     *   screen-capture + `WazeArrowRegistry.WAZE_INK`.
     *
     * ⚠ Muốn thêm một gói vào đây thì phải có **phép đo** rằng notification của nó mang mũi tên (hoặc app đó
     * không có kênh ảnh nào để mất) — xem CLAUDE.md §14. Không suy từ "app nào cũng có notification".
     */
    val NOTIFICATION: Set<String> = GMAPS

    /**
     * Toàn bộ app dẫn đường được đọc qua a11y. Phải khớp `android:packageNames` trong XML cấu hình a11y
     * (`NavPackageRosterSyncTest` khoá cặp này).
     *
     * ⚠ CHỈ CÒN GMAPS từ 2026-08-28: đường ĐỌC dẫn đường VietMap/Waze (screen-capture + a11y) đã bị GỠ. A11y
     * giờ chỉ phục vụ (a) booster cự-ly ground-truth của Google Maps và (b) `onKeyEvent` cho nút vật lý →
     * trợ lý (key event KHÔNG bị `packageNames` lọc). VietMap speed badge đi qua widget, không qua a11y.
     */
    val ALL: Set<String> = GMAPS
}
