package com.byd.clusternav.navigation

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * B-II — khoá việc TÁCH cổng nguồn thuần [SourceArbiter.allows] khỏi [SourceArbiter.shouldFeed].
 *
 * VÌ SAO tách (lý do có thật, không phải dọn dẹp cho đẹp): vòng enum cửa sổ phải hỏi cổng cho NHIỀU ứng viên
 * trong CÙNG một nhịp 800 ms rồi mới bầu ra một nguồn. Hỏi bằng [SourceArbiter.shouldFeed] thì mỗi câu hỏi đã
 * đồng thời đặt `activeSource` = ứng viên đó, kể cả ứng viên rốt cuộc bị [NavSourceDwell] loại — đúng cú nhảy
 * nguồn mà B-II chữa.
 *
 * [SourceArbiter] là `object` (state singleton dùng chung cả suite) → mỗi test dùng một mốc thời gian LỚN,
 * cô lập, cộng `clear()` ở [BeforeEach]/[AfterEach], để không phụ thuộc thứ tự chạy.
 *
 * ĐÍNH CHÍNH 2026-08-23 (B3.48): bản trước của KDoc này nói về sổ `lastSeenByPkg` "không bị `clear()` reset".
 * Sổ đó đã bị XOÁ khỏi [SourceArbiter] cùng với `noteSeen`/`isGroupFresh` — cổng `PREFER_*` nay chỉ là phép
 * thử thành viên nhóm, không đọc lịch sử nào.
 */
class SourceArbiterAllowsTest {

    private val GMAPS = "com.google.android.apps.maps"
    private val WAZE = "com.waze"
    private val MOD = "com.chisadin.wazemod"
    private val VIETMAP = NavApps.VIETMAP_LIVE

    @BeforeEach fun setup() = SourceArbiter.clear()
    @AfterEach fun tearDown() = SourceArbiter.clear()

    /**
     * KHOÁ: việc tách hàm KHÔNG đổi hành vi cổng nguồn. [SourceArbiter.allows] phải trả đúng cái mà
     * [SourceArbiter.shouldFeed] sẽ quyết định, ở CẢ 4 mode — kể cả sau khi khoá đã bị chiếm.
     *
     * ⚠ MỨC BẢO ĐẢM THẬT (ghi rõ 2026-08-23 để không ai đếm nhầm test này vào "khoá B3.48"): hôm nay
     * [SourceArbiter.shouldFeed] ở kênh [NavChannel.DATA] **uỷ thẳng** cho [SourceArbiter.allows], nên vòng
     * lặp dưới là một **CANH CỬA cho lần tái-inline trong tương lai**, không phải phép đo hành vi độc lập.
     * Chỗ hai hàm CÓ THỂ khác nhau là kênh [NavChannel.IMAGE] (`isDataFresh` chặn ảnh khi data cùng gói còn
     * tươi) — cố ý KHÔNG phủ ở đây vì ở đó chúng phải khác nhau; phủ bởi `SourceArbiterChannelTest`.
     */
    @Test
    fun `allows() dong y voi shouldFeed o CA 4 mode`() {
        val modes = listOf(
            NavSourceMode.AUTO,
            NavSourceMode.PREFER_GMAPS,
            NavSourceMode.PREFER_WAZE,
            NavSourceMode.PREFER_VIETMAP,
        )
        var base = 30_000_000L
        for (mode in modes) {
            for (holder in listOf(GMAPS, WAZE, VIETMAP)) {
                SourceArbiter.clear()
                base += 1_000_000L
                // Dựng một trạng thái thật: [holder] vừa nuôi cụm → chiếm khoá + đóng mốc kênh DATA.
                SourceArbiter.shouldFeed(holder, mode, base)
                for (pkg in listOf(GMAPS, WAZE, MOD, VIETMAP)) {
                    val t = base + 100
                    val predicted = SourceArbiter.allows(pkg, mode, t)   // hỏi TRƯỚC (thuần, không tác dụng phụ)
                    val actual = SourceArbiter.shouldFeed(pkg, mode, t)
                    assertEquals(predicted, actual, "mode=$mode holder=$holder pkg=$pkg")
                }
            }
        }
    }

    /**
     * KHOÁ: đúng lý do tách hàm — hỏi cổng cho một ứng viên rồi LOẠI nó không được cướp `activeSource`, và
     * cũng không được đóng mốc kênh DATA (nếu đóng thì nguồn ẢNH của chính app đó bị chặn oan sau đó).
     */
    @Test
    fun `allows() KHONG chiem khoa va KHONG dong moc data`() {
        val t = 40_000_000L
        repeat(5) { SourceArbiter.allows(WAZE, NavSourceMode.AUTO, t + it) }
        assertNull(SourceArbiter.activeSource, "hỏi cổng KHÔNG được chiếm khoá nguồn")
        assertFalse(SourceArbiter.isDataFresh(WAZE, t + 10), "hỏi cổng KHÔNG được đóng mốc kênh DATA")
        assertFalse(SourceArbiter.isFresh(t + 10), "không có nguồn nào đang giữ")

        // Và ứng viên KHÁC vẫn lên được bình thường — không bị ứng viên bị loại chặn.
        assertTrue(SourceArbiter.shouldFeed(MOD, NavSourceMode.AUTO, t + 20))
        assertEquals(MOD, SourceArbiter.activeSource)
    }

    /**
     * KHOÁ LUẬT MỚI (owner chốt 2026-08-23 — backlog **B3.48**): ở `PREFER_X`, cổng nguồn CHỈ hỏi
     * *"pkg có thuộc nhóm X không"*. Không có lịch sử nào, không có đồng hồ nào mở cửa cho một gói NGOÀI
     * nhóm — dù quan sát ([SourceArbiter.allows]) hay nuôi khung thật ([SourceArbiter.shouldFeed]).
     *
     * BÀI HỌC CŨ ĐÃ BỊ THAY (giữ lại, không xoá trắng lịch sử — CLAUDE.md §10). Test này trước đây tên
     * `so 'da thay' cua PREFER chi duoc dong boi shouldFeed, khong boi viec quan sat`, khoá hồi quy 08-22
     * vòng 1: hồi đó `PREFER_X` có cửa thoát `|| !isGroupFresh(X)` đọc sổ `lastSeenByPkg`, nên việc AI được
     * phép ghi sổ đó là chuyện sống còn (bản 08-22 mở `noteSeen` ra public cho vòng enum cửa sổ gọi mỗi
     * 800 ms ⇒ mốc đổi nghĩa từ "app CÒN DẪN" thành "app CÓ CỬA SỔ" ⇒ ở PREFER_WAZE, mở Waze rồi để đó
     * không dẫn cũng khoá chết mọi app khác).
     *
     * Quyết định owner 2026-08-23 (nguyên văn: *"chọn đích danh app thì luôn lấy app đích danh, nếu app đó
     * ko dẫn thì ko hiện gì, không cần auto switch làm gì cả"*) gỡ HẲN cửa thoát đó ⇒ `lastSeenByPkg`,
     * `noteSeen`, `isGroupFresh` đều đã bị xoá khỏi [SourceArbiter]. Hồi quy 08-22 nay BẤT KHẢ THI theo cấu
     * tạo (không còn sổ để ghi sai) — thứ phải khoá bây giờ là chiều ngược lại: nhóm ưu tiên im lặng KHÔNG
     * được nhường cửa cho ai.
     */
    @Test
    fun `PREFER chi hoi thanh vien nhom - nuoi khung hay quan sat deu khong mo cua cho goi ngoai nhom`() {
        val t = 50_000_000L
        // Chưa app nào nuôi khung. Luật CŨ: VietMap được lên (nhóm GMaps chưa tươi). Luật MỚI: KHÔNG.
        assertFalse(
            SourceArbiter.allows(VIETMAP, NavSourceMode.PREFER_GMAPS, t),
            "PREFER_GMAPS mà GMaps chưa dẫn ⇒ im lặng, KHÔNG nhường cho VietMap",
        )

        // Hỏi cổng cho GMaps bao nhiêu lần cũng không có tác dụng phụ nào (đúng lý do tách `allows`).
        repeat(5) { SourceArbiter.allows(GMAPS, NavSourceMode.PREFER_GMAPS, t + 10 + it) }
        assertNull(SourceArbiter.activeSource, "hỏi cổng KHÔNG được chiếm khoá")

        // GMaps nuôi được MỘT khung thật → vẫn đúng mình GMaps qua cổng.
        assertTrue(SourceArbiter.shouldFeed(GMAPS, NavSourceMode.PREFER_GMAPS, t + 30))
        assertFalse(SourceArbiter.allows(VIETMAP, NavSourceMode.PREFER_GMAPS, t + 40))
        assertTrue(SourceArbiter.allows(GMAPS, NavSourceMode.PREFER_GMAPS, t + 40))

        // Và GMaps IM quá STALE — ĐÂY là dòng mà cửa thoát cũ mở cho VietMap. Nay phải ĐÓNG.
        assertFalse(
            SourceArbiter.allows(VIETMAP, NavSourceMode.PREFER_GMAPS, t + 30 + SourceArbiter.STALE_MS + 1),
            "cửa thoát `|| !isGroupFresh(X)` đã gỡ (B3.48): nhóm ưu tiên im ⇒ KHÔNG ai thay chỗ",
        )
    }

    /**
     * KHOÁ: PREFER_WAZE cho phép CẢ HAI thành viên nhóm Waze (`com.waze` + `com.chisadin.wazemod`) — đây chính
     * là lý do arbiter KHÔNG khoá được cú nhảy giữa hai bản Waze, và vì sao B-II phải có lớp dwell riêng.
     * Nếu ai đó "sửa" arbiter để chỉ cho một gói, dwell sẽ mất lý do tồn tại → test này nói rõ hiện trạng.
     */
    @Test
    fun `PREFER_WAZE cho phep CA HAI ban Waze (ly do phai co dwell)`() {
        val t = 60_000_000L
        assertTrue(SourceArbiter.allows(WAZE, NavSourceMode.PREFER_WAZE, t))
        assertTrue(SourceArbiter.allows(MOD, NavSourceMode.PREFER_WAZE, t))
    }

    /**
     * KHOÁ (08-22 vòng 3): [SourceArbiter.allowedByMode] là cổng CHỈ-THEO-MODE — ở AUTO nó KHÔNG được đọc
     * khoá-giữ `activeSource`, còn ở PREFER_* nó phải trùng KHÍT [SourceArbiter.allows].
     *
     * VÌ SAO cần một hàm riêng: [NavSourceDwell.onTick] tham số `holderAllowed` chỉ mang MỘT nghĩa — "user
     * đổi PREFER_* ⇒ nhường NGAY" — và R3 dùng nó để bỏ qua CẢ dwell LẪN guard cam-kết-rẽ. Truyền
     * [SourceArbiter.allows] vào đó là trộn khoá-giữ AUTO vào một cổng lẽ ra thuần mode (test kế tiếp dựng
     * đúng ca hỏng).
     */
    @Test
    fun `allowedByMode() la cong THUAN MODE - AUTO khong doc khoa-giu, PREFER_ trung khit allows()`() {
        var base = 70_000_000L
        for (holder in listOf(GMAPS, WAZE, VIETMAP)) {
            // (a) AUTO: dựng trạng thái "holder đang giữ khoá" rồi hỏi cho app KHÁC.
            SourceArbiter.clear(); base += 1_000_000L
            SourceArbiter.shouldFeed(holder, NavSourceMode.AUTO, base)
            for (pkg in listOf(GMAPS, WAZE, MOD, VIETMAP)) {
                assertTrue(
                    SourceArbiter.allowedByMode(pkg, NavSourceMode.AUTO, base + 100),
                    "AUTO không diễn đạt ưu tiên nào ⇒ cổng mode luôn mở (holder=$holder pkg=$pkg)",
                )
            }
            // Và `allows` thì VẪN khoá — nếu vế này đỏ thì khoá-giữ AUTO đã bị gỡ mất, không phải bug ở đây.
            val other = if (holder == GMAPS) WAZE else GMAPS
            assertFalse(SourceArbiter.allows(other, NavSourceMode.AUTO, base + 100), "khoá-giữ AUTO còn nguyên")

            // (b) PREFER_*: hai hàm phải cho CÙNG kết quả ở mọi tổ hợp.
            // ⚠ Hôm nay `allows` uỷ THẲNG cho `allowedByMode` ở đúng ba mode này (SourceArbiter.kt), nên vế
            // này là CANH CỬA cho lần tái-inline, không phải phép đo độc lập — đừng đếm nó vào "khoá B3.48".
            for (mode in listOf(NavSourceMode.PREFER_GMAPS, NavSourceMode.PREFER_WAZE, NavSourceMode.PREFER_VIETMAP)) {
                SourceArbiter.clear(); base += 1_000_000L
                SourceArbiter.shouldFeed(holder, mode, base)
                for (pkg in listOf(GMAPS, WAZE, MOD, VIETMAP)) {
                    val t = base + 100
                    assertEquals(
                        SourceArbiter.allows(pkg, mode, t),
                        SourceArbiter.allowedByMode(pkg, mode, t),
                        "mode=$mode holder=$holder pkg=$pkg",
                    )
                }
            }
        }
    }

    /**
     * KHOÁ HỒI QUY (08-22 vòng 3) — ca hỏng CỤ THỂ mà việc tách hàm sinh ra để chặn: ở AUTO, R3 KHÔNG được
     * vượt mặt R5 (cam kết rẽ).
     *
     * Cơ chế (tất định từ source, không phải phỏng đoán): biểu thức AUTO của [SourceArbiter.allows] là
     * `h == null || h == pkg || stale`. Nên `allows(candidate)=true` ĐỒNG THỜI `allows(holder)=false` chỉ xảy
     * ra khi `h == candidate` còn tươi — tức app kia đang THẬT SỰ nuôi khung. Đó chính là trạng thái dựng ở
     * dưới. Nếu call site truyền `allows` làm `holderAllowed`, R3 bắn `adopt(Reason.MODE)` TỨC THÌ dù holder
     * còn 50 m tới điểm rẽ ⇒ đổi nguồn đúng giây vào cua. Với `allowedByMode`, dwell rơi xuống R5 và CHẶN.
     */
    @Test
    fun `AUTO - app khac dang nuoi khung KHONG duoc vuot mat guard cam-ket-re`() {
        val t0 = 90_000_000L
        var now = t0
        val dwell = NavSourceDwell(clock = { now }, tickPeriodMs = 800L)

        // Holder = GMaps (đã được bầu ở nhịp trước).
        assertEquals(GMAPS, dwell.onTick(candidate = GMAPS, holderAllowed = true, holderPresent = true).source)

        // VietMap nuôi được một khung thật ⇒ nó chiếm khoá AUTO. GMaps mất `allows`, VietMap có.
        SourceArbiter.clear()
        assertTrue(SourceArbiter.shouldFeed(VIETMAP, NavSourceMode.AUTO, t0))
        assertFalse(SourceArbiter.allows(GMAPS, NavSourceMode.AUTO, t0 + 10), "tiền đề ca hỏng: holder mất allows()")
        assertTrue(SourceArbiter.allows(VIETMAP, NavSourceMode.AUTO, t0 + 10), "tiền đề ca hỏng: ứng viên có allows()")
        // …nhưng cổng MODE thì mở cho cả hai — đó là điểm khác biệt duy nhất, và là điểm cứu mạng.
        assertTrue(SourceArbiter.allowedByMode(GMAPS, NavSourceMode.AUTO, t0 + 10))

        now += 800L
        val d = dwell.onTick(
            candidate = VIETMAP,
            holderAllowed = SourceArbiter.allowedByMode(GMAPS, NavSourceMode.AUTO, t0 + 10),
            holderPresent = true,
            holderTurnMeters = 50,          // đang trong khúc rẽ
        )
        assertEquals(GMAPS, d.source, "R5 phải giữ holder qua điểm rẽ")
        assertEquals(NavSourceDwell.Reason.COMMITTED, d.reason)
        assertEquals(VIETMAP, d.blocked)
        assertFalse(d.switched)
    }

    /**
     * KHOÁ **CA B3.48** — đúng ca hỏng hiện trường mà quyết định owner 2026-08-23 sinh ra để chặn.
     *
     * Ca thật (cơ chế mức "đã chứng minh" từ source, xem backlog B3.48): từ B3.44, VietMap bị gỡ khỏi
     * `NavApps.NOTIFICATION` ⇒ writer duy nhất còn lại của mốc VietMap là kênh ẢNH, mà kênh ảnh chỉ đập nhịp
     * khi `NavGlyphLocator` + `WazeArrowRegistry` phân loại được mũi tên (phủ sóng thật **39/87** maneuver
     * VietMap sau B3.47 vòng 3; trước đó 27/87 — khoá bằng `VietMapGlyphGateTest`). Mỗi khoảng lặng
     * > [SourceArbiter.STALE_MS] là cửa thoát cũ
     * `|| !isGroupFresh(VIETMAP)` mở cho GMaps ⇒ HAI owner cùng ghi `INSTRUMENT_GUIDE_INFO_SIMPLE_SET`
     * (`NavigationHudOwner.writeNavFrame` + `NavOutputOwner.pushNavigation`) ⇒ cự ly app này nằm cạnh mũi tên
     * app kia trên cụm-centre và HUD.
     *
     * Test dựng ca CỰC ĐOAN nhất: VietMap **im hoàn toàn**, chưa từng nuôi một khung nào. GMaps phải bị chặn
     * ở MỌI thời điểm — kể cả sau khi vượt xa [SourceArbiter.STALE_MS] — và [SourceArbiter.activeSource] phải
     * ở `null` (cụm IM LẶNG, không owner nào được ghi HAL). Đó là hành vi owner MUỐN, không phải suy giảm.
     */
    @Test
    fun `B3_48 - PREFER_VIETMAP ma VietMap IM HOAN TOAN thi GMaps KHONG BAO GIO qua cong`() {
        val t = 100_000_000L
        val offsets = listOf(
            0L,
            1L,
            SourceArbiter.STALE_MS,
            SourceArbiter.STALE_MS + 1,          // ĐÂY là mốc cửa thoát cũ mở cho GMaps
            10 * SourceArbiter.STALE_MS,
            3_600_000L,                          // một giờ im lặng
        )
        for (dt in offsets) {
            val now = t + dt
            assertFalse(
                SourceArbiter.allows(GMAPS, NavSourceMode.PREFER_VIETMAP, now),
                "allows(GMaps) phải FALSE ở dt=$dt — user chọn đích danh VietMap",
            )
            assertFalse(
                SourceArbiter.allowedByMode(GMAPS, NavSourceMode.PREFER_VIETMAP, now),
                "allowedByMode(GMaps) phải FALSE ở dt=$dt (cổng R3 của dwell cũng không được mở)",
            )
            assertFalse(
                SourceArbiter.shouldFeed(GMAPS, NavSourceMode.PREFER_VIETMAP, now),
                "shouldFeed(GMaps) phải FALSE ở dt=$dt — không được nuôi cụm bằng khung GMaps",
            )
        }
        assertNull(
            SourceArbiter.activeSource,
            "VietMap im ⇒ KHÔNG nguồn nào chiếm khoá ⇒ cụm im lặng (không hai owner cùng ghi HAL)",
        )
    }

    /**
     * KHOÁ: `PREFER_X` = ĐÚNG tập thành viên nhóm X, không hơn không kém — duyệt CẢ BA nhóm bằng chính
     * [NavApps] (thêm gói vào roster mà quên nhóm ⇒ test này đỏ), ở mọi trạng thái holder, cả trước và sau
     * [SourceArbiter.STALE_MS].
     */
    @Test
    fun `PREFER_X cho DUNG thanh vien nhom X - duyet ca 3 nhom, moi goi ngoai nhom bi chan`() {
        val foreign = setOf("com.android.settings", "vn.vietmap.app", "com.example.notanav")
        val universe = (NavApps.GMAPS + NavApps.WAZE + NavApps.VIETMAP) + foreign
        val cases = listOf(
            NavSourceMode.PREFER_GMAPS to NavApps.GMAPS,
            NavSourceMode.PREFER_WAZE to NavApps.WAZE,
            NavSourceMode.PREFER_VIETMAP to NavApps.VIETMAP,
        )
        var base = 110_000_000L
        for ((mode, group) in cases) {
            for (holder in listOf(null, GMAPS, WAZE, MOD, VIETMAP)) {
                SourceArbiter.clear(); base += 1_000_000L
                if (holder != null) SourceArbiter.shouldFeed(holder, mode, base)
                for (dt in listOf(100L, SourceArbiter.STALE_MS + 1, 5 * SourceArbiter.STALE_MS)) {
                    val now = base + dt
                    for (pkg in universe) {
                        val expected = pkg in group
                        assertEquals(
                            expected, SourceArbiter.allows(pkg, mode, now),
                            "allows mode=$mode holder=$holder pkg=$pkg dt=$dt",
                        )
                        assertEquals(
                            expected, SourceArbiter.allowedByMode(pkg, mode, now),
                            "allowedByMode mode=$mode holder=$holder pkg=$pkg dt=$dt",
                        )
                    }
                }
            }
        }
    }

    /**
     * KHOÁ BẤT BIẾN B3.48: trong CÙNG một chế độ `PREFER_*`, KHÔNG còn đường nào để hai package thuộc HAI
     * nhóm khác nhau cùng qua cổng. Test phát biểu ĐIỀU KIỆN, không phát biểu CÁCH SỬA ⇒ nó vẫn đỏ nếu ai đó
     * mở lại cửa thoát bằng một cơ chế khác (timeout / fallback / degrade).
     *
     * ⚠ ĐỪNG ĐỌC QUÁ (đính chính 2026-08-23 vòng sửa): đây **KHÔNG** phải phát biểu đầy đủ của cái hại. Cái
     * hại là "hai OWNER cùng ghi `INSTRUMENT_GUIDE_INFO_SIMPLE_SET`", mà hai owner chỉ cần hai **PACKAGE**
     * khác nhau — không cần khác NHÓM. [NavApps.GMAPS] có 2 gói và [NavApps.WAZE] có 2 gói (cấu hình có thật
     * của owner: cài cả Waze zin lẫn WazeMod), nên ở `PREFER_GMAPS`/`PREFER_WAZE` hai thành viên CÙNG NHÓM
     * vẫn cùng qua cổng — xem test `PREFER_WAZE cho phep CA HAI ban Waze` ngay trên, nó assert đúng chuyện
     * đó. Hiện trạng này CÓ TỪ TRƯỚC B3.48 (cổng `PREFER_*` chưa từng có khoá-giữ) nên không nằm trong phạm
     * vi B3.48; nó cần hạng mục backlog riêng. Test này chỉ khoá phần B3.48 THẬT SỰ đóng.
     */
    @Test
    fun `PREFER_ - hai package KHAC NHOM khong bao gio cung qua cong`() {
        val groups = listOf(NavApps.GMAPS, NavApps.WAZE, NavApps.VIETMAP)
        val universe = (NavApps.GMAPS + NavApps.WAZE + NavApps.VIETMAP) + setOf("com.android.settings", "vn.vietmap.app")
        var base = 130_000_000L
        for (mode in listOf(NavSourceMode.PREFER_GMAPS, NavSourceMode.PREFER_WAZE, NavSourceMode.PREFER_VIETMAP)) {
            for (holder in listOf(null, GMAPS, WAZE, MOD, VIETMAP)) {
                for (dt in listOf(100L, SourceArbiter.STALE_MS + 1, 20 * SourceArbiter.STALE_MS)) {
                    SourceArbiter.clear(); base += 1_000_000L
                    if (holder != null) SourceArbiter.shouldFeed(holder, mode, base)
                    val now = base + dt
                    val passing = universe.filter { SourceArbiter.allows(it, mode, now) }
                    for (a in passing) for (b in passing) {
                        assertTrue(
                            groups.any { a in it && b in it },
                            "mode=$mode holder=$holder dt=$dt: '$a' và '$b' cùng qua cổng mà KHÁC nhóm",
                        )
                    }
                }
            }
        }
    }

    /**
     * KHOÁ TIỀN ĐỀ AN TOÀN của bản vá "cắt nguồn thì phải kèm lệnh dừng"
     * (`NavNotificationListener.stopClusterOwnedBy`, B3.48 vòng sửa).
     *
     * Call site mới ở đường notification là `if (SourceArbiter.release(pkg)) stopClusterOwnedBy(pkg, …)` đặt
     * NGAY SAU một `shouldFeed` trả false. Nó chỉ được phép bắn ở `PREFER_*`; nếu nó bắn được ở **AUTO** thì
     * mỗi lần app-nền bị khoá-giữ chặn sẽ tắt cụm của app đang dẫn — hồi quy chết người.
     *
     * Bất biến chứng minh nó không thể bắn ở AUTO: biểu thức AUTO là `h == null || h == pkg || stale`, nên
     * `!allows(pkg)` ⇒ `h != pkg` ⇒ `release(pkg)` = false. Test dựng mọi tổ hợp holder × pkg × mốc thời
     * gian để nếu ai đổi biểu thức AUTO (thêm điều kiện làm holder tự mất quyền) thì đỏ ở đây, không đỏ
     * ngoài đường.
     */
    @Test
    fun `AUTO - cong tu choi thi goi bi tu choi KHONG BAO GIO la nguon dang giu`() {
        var base = 170_000_000L
        for (holder in listOf<String?>(null, GMAPS, WAZE, MOD, VIETMAP)) {
            for (dt in listOf(0L, 100L, SourceArbiter.STALE_MS, SourceArbiter.STALE_MS + 1, 50 * SourceArbiter.STALE_MS)) {
                SourceArbiter.clear(); base += 1_000_000L
                if (holder != null) SourceArbiter.shouldFeed(holder, NavSourceMode.AUTO, base)
                val now = base + dt
                for (pkg in (NavApps.GMAPS + NavApps.WAZE + NavApps.VIETMAP)) {
                    if (SourceArbiter.allows(pkg, NavSourceMode.AUTO, now)) continue
                    assertEquals(
                        false, pkg == SourceArbiter.activeSource,
                        "AUTO holder=$holder dt=$dt: '$pkg' vừa bị cổng từ chối MÀ vẫn là activeSource ⇒ " +
                            "call site `if (release(pkg)) stopClusterOwnedBy(...)` sẽ tắt cụm ở AUTO",
                    )
                }
            }
        }
    }

    /**
     * KHOÁ MẶT KIA của cùng bản vá: ở `PREFER_X`, gói đang GIỮ cụm mà user đổi sang nhóm KHÁC thì
     * [SourceArbiter.release] phải trả **true** đúng MỘT lần — đủ để caller phát lệnh dừng, và không lặp lại
     * ở những khung bị chặn tiếp theo (nếu lặp thì mỗi noti GMaps ~1 Hz lại bắn `NavRepository.stop` +
     * `ClusterNavLaneWidget.onNavIdle`, tức xoá debounce 30 s của op-39 theo nhịp giây — xem B3.45).
     */
    @Test
    fun `PREFER_ - nguon dang giu bi doi che do thi release() bao dung MOT lan`() {
        val t = 180_000_000L
        // GMaps đang nuôi cụm ở AUTO.
        assertTrue(SourceArbiter.shouldFeed(GMAPS, NavSourceMode.AUTO, t))
        assertEquals(GMAPS, SourceArbiter.activeSource)

        // User đổi sang PREFER_VIETMAP: khung GMaps kế tiếp bị cổng loại…
        assertFalse(SourceArbiter.shouldFeed(GMAPS, NavSourceMode.PREFER_VIETMAP, t + 100))
        // …và GMaps đúng là nguồn đang giữ ⇒ caller được lệnh dừng cụm.
        assertTrue(SourceArbiter.release(GMAPS), "phải báo TRUE đúng lần đầu (caller phát STOP)")
        assertNull(SourceArbiter.activeSource)

        // Những khung GMaps bị chặn sau đó KHÔNG được bắn stop lần nữa.
        repeat(5) { i ->
            assertFalse(SourceArbiter.shouldFeed(GMAPS, NavSourceMode.PREFER_VIETMAP, t + 200 + i))
            assertFalse(SourceArbiter.release(GMAPS), "chỉ được dừng cụm ĐÚNG MỘT lần")
        }
    }

    /**
     * KHOÁ: quyết định 2026-08-23 chỉ đụng `PREFER_*`. Nhánh AUTO (`else ->` của [SourceArbiter.allows])
     * KHÔNG được đổi một bit: app dẫn TRƯỚC giữ khoá; app sau chờ tới khi holder IM quá
     * [SourceArbiter.STALE_MS]; holder còn đập nhịp thì khoá không bao giờ hết hạn.
     */
    @Test
    fun `AUTO KHONG doi hanh vi - app dan TRUOC giu khoa, app sau cho holder im qua STALE`() {
        val t = 150_000_000L
        assertTrue(SourceArbiter.shouldFeed(GMAPS, NavSourceMode.AUTO, t))
        assertEquals(GMAPS, SourceArbiter.activeSource)

        // Còn tươi → app sau bị chặn.
        assertFalse(SourceArbiter.shouldFeed(VIETMAP, NavSourceMode.AUTO, t + 100))
        assertEquals(GMAPS, SourceArbiter.activeSource, "ứng viên bị chặn KHÔNG được cướp khoá")

        // Holder tự đập nhịp ngay sát hạn → khoá gia hạn, app sau vẫn chờ.
        assertTrue(SourceArbiter.shouldFeed(GMAPS, NavSourceMode.AUTO, t + SourceArbiter.STALE_MS))
        assertFalse(SourceArbiter.shouldFeed(VIETMAP, NavSourceMode.AUTO, t + SourceArbiter.STALE_MS + 100))

        // Holder im quá STALE kể từ nhịp cuối → nhường.
        val late = t + SourceArbiter.STALE_MS + SourceArbiter.STALE_MS + 1
        assertTrue(SourceArbiter.shouldFeed(VIETMAP, NavSourceMode.AUTO, late))
        assertEquals(VIETMAP, SourceArbiter.activeSource)
    }

    /**
     * KHOÁ B3.50 — LỆCH ĐỒNG HỒ ở AUTO. Holder chiếm khoá tại mốc lớn; NTP/GPS kéo đồng hồ nhảy LÙI ⇒
     * `now < activeSeen` ⇒ hiệu ÂM. Biểu thức cũ `now - activeSeen > STALE_MS` = false ⇒ khoá-giữ KHÔNG bao
     * giờ hết hạn = **khoá cứng vào một nguồn đã chết** cho tới khi đồng hồ đuổi kịp (có thể hàng phút/giờ).
     * Nay mốc ở tương lai ⇒ coi STALE ⇒ ứng viên khác lên được; và [SourceArbiter.isFresh] cũng phải báo
     * đúng là KHÔNG tươi.
     *
     * ⚠ KHÔNG nới forward-clock: test `AUTO KHONG doi hanh vi …` ngay trên vẫn khoá y nguyên hành vi khi
     * đồng hồ tiến (holder giữ khoá tới khi im quá STALE). Guard chỉ đổi đúng ca hiệu âm.
     */
    @Test
    fun `B3_50 - AUTO dong ho nhay lui KHONG khoa cung vao nguon da chet`() {
        val holderAt = 200_000_000L
        assertTrue(SourceArbiter.shouldFeed(GMAPS, NavSourceMode.AUTO, holderAt))   // GMaps giữ, activeSeen=holderAt
        assertEquals(GMAPS, SourceArbiter.activeSource)

        val now = holderAt - 10_000L   // đồng hồ nhảy lùi 10 s ⇒ now < activeSeen ⇒ hiệu âm
        assertTrue(
            SourceArbiter.allows(VIETMAP, NavSourceMode.AUTO, now),
            "mốc holder ở TƯƠNG LAI (đồng hồ lùi) ⇒ coi STALE ⇒ ứng viên khác được lên, không khoá cứng",
        )
        assertFalse(
            SourceArbiter.isFresh(now),
            "nguồn giữ với mốc tương lai KHÔNG còn là 'tươi' (UI không báo nhầm)",
        )
        // Và ứng viên nuôi được khung thật ⇒ tiếp quản khoá bình thường.
        assertTrue(SourceArbiter.shouldFeed(VIETMAP, NavSourceMode.AUTO, now))
        assertEquals(VIETMAP, SourceArbiter.activeSource)
    }
}
