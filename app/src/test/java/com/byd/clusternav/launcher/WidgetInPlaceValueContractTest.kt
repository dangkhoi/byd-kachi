package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ Ô ĐỌC ĐỔ SỐ **TẠI CHỖ**, KHÔNG TTHÁO/GẮN VIEW ═══════════════════════════════════════════════════════════
 *
 * Khoá bản vá 2026-09-21. Owner (nguyên văn): *"widget curated như Áp suất lốp refresh lấy số mới bị GIẬT vì dựng
 * lại CẢ widget mỗi nhịp car-status"*.
 *
 * ## Bệnh
 * [WidgetViews.refreshRead] làm mới ô ĐỌC bằng `removeViewAt` + `addView`. Trên xe trạng thái đổi **1 nhịp/giây** ⇒
 * mỗi giây một ô curated bị tháo khỏi cây view và gắn lại: khung mới đo–đặt–vẽ từ đầu và ô vẽ Canvas nạp lại ảnh xe
 * ([CarImageLayer]) ⇒ **giật**. Ô HÀNH ĐỘNG và ô NHÓM đã đổ chữ tại chỗ từ trước; lượt này trải cơ chế ấy sang ô ĐỌC.
 *
 * ## ⚠ Vì sao khoá bằng QUÉT NGUỒN
 * Cùng lý do đã ghi ở [WidgetRefreshInPlaceContractTest]: ca này **không quan sát được off-car** (không xe ⇒ trạng
 * thái luôn rỗng ⇒ *"trạng thái đổi"* luôn `false`) và dự án **không dùng Robolectric** nên không dựng được `View`
 * trong test JVM (mọi `View`/`Context` là stub `android.jar` — gọi là ném). Vậy khoá phần **nối dây** + **bất biến
 * của hàm đổ**, tức đúng hai thứ off-car chứng minh được.
 *
 * Bốn thứ được khoá, mỗi thứ chặn một đường hồi quy khác nhau:
 *  1. [refreshRead] **thử đổ tại chỗ TRƯỚC** khi tháo view (đảo thứ tự = bệnh cũ y nguyên);
 *  2. **MỌI** widget dựng tay đăng ký đường đổ — danh sách sinh từ [WidgetRegistry] nên thêm widget mới mà quên
 *     đăng ký là ĐỎ, không phải là một ô giật mà không ai biết;
 *  3. hàm đổ **KHÔNG dựng View mới** (nếu không thì nó chỉ đổi chỗ cú giật, không bỏ nó);
 *  4. sổ đăng ký giữ hàm đổ **trên chính view** (`setTag`), KHÔNG bằng một map keyed-by-`View` — xem KDoc
 *     [WidgetRefreshers] về vì sao `WeakHashMap` ở đây **không** chống rò được.
 */
class WidgetInPlaceValueContractTest {

    private val widgets by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/WidgetViews.kt") }
    private val shared by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/WidgetTelemetry.kt") }
    private val media by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/MediaWidgetView.kt") }
    private val registry by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/WidgetRefreshers.kt") }

    /** Tệp tầng vẽ ô giữa màn, tra theo tên hàm (bộ vẽ nằm ở tệp nào cũng được, miễn nó đăng ký đường đổ). */
    private val byFile by lazy { listOf(widgets, shared, media) }

    /** Tệp theo tên OBJECT, để nhánh `"w_media" -> MediaWidgetView.build(...)` soi đúng tệp chứ không soi dispatcher. */
    private val byObject by lazy {
        mapOf("WidgetViews" to widgets, "WidgetTelemetry" to shared, "MediaWidgetView" to media)
    }

    /**
     * Thân của **mọi** hàm có tên khớp [name] (khác [SourceRoots.body] — nó chỉ trả lần đầu). Chỉ dùng cho hàm có
     * thân KHỐI; hàm một-biểu-thức đi qua [bodyOf].
     */
    private fun bodies(src: String, name: Regex): List<String> {
        val out = mutableListOf<String>()
        name.findAll(src).forEach { m ->
            val open = src.indexOf('{', m.range.last)
            if (open < 0) return@forEach
            var depth = 0
            var i = open
            while (i < src.length) {
                if (src[i] == '{') depth++
                if (src[i] == '}' && --depth == 0) break
                i++
            }
            out += src.substring(open, (i + 1).coerceAtMost(src.length))
        }
        return out
    }

    /**
     * Thân hàm [name] ở [src] — qua [SourceRoots.body] để đọc được **cả** hàm một-biểu-thức
     * (`private fun tyreMini(...) = miniCard(...) { … }`). `null` = tệp này không khai nó.
     */
    private fun bodyOf(src: String, name: String): String? =
        if (!Regex("""\bfun $name\s*\(""").containsMatchIn(src)) null
        else runCatching { SourceRoots.body(src, "fun $name(") }.getOrNull()

    /** Thân hàm [name]; [obj] = tên object đứng trước dấu chấm ở chỗ gọi (nếu có). */
    private fun anyBody(name: String, obj: String? = null): String? =
        if (obj != null) byObject[obj]?.let { bodyOf(it, name) }
        else byFile.firstNotNullOfOrNull { bodyOf(it, name) }

    // ══ 1 · Thứ tự trong refreshRead ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `refreshRead do tai cho TRUOC, chi lui ve dung lai khi o chua dang ky`() {
        val fn = SourceRoots.body(widgets, "fun refreshRead(")
        val inPlace = fn.indexOf("WidgetRefreshers.refresh(")
        val remove = fn.indexOf("removeViewAt")
        assertTrue(inPlace >= 0, "phải thử đổ giá trị tại chỗ qua sổ đăng ký")
        assertTrue(
            inPlace in 1 until remove,
            "đổ tại chỗ phải được thử TRƯỚC khi tháo view — đảo thứ tự là giữ nguyên cú giật owner báo",
        )
        // Đường LÙI phải còn: ô chưa đăng ký hàm đổ thì vẫn hiện số đúng (giật), KHÔNG được câm.
        assertTrue(fn.contains("addView(") && fn.contains("indexOfChild"), "đường lùi dựng-lại phải còn nguyên")
        // Nút HÀNH ĐỘNG vẫn đi đường của nó (đọc lại số, không dựng lại) — lượt này KHÔNG đụng ô WRITE.
        assertTrue(fn.contains("WidgetRefreshers.refreshAction("), "ô HÀNH ĐỘNG giữ đường đọc-lại riêng")
        val actionAt = fn.indexOf("WidgetRefreshers.refreshAction(")
        assertTrue(actionAt in 1 until inPlace, "nhánh WRITE phải xét trước — nó tuyệt đối không được dựng lại")
    }

    // ══ 2 · MỌI widget dựng tay có đường đổ ════════════════════════════════════════════════════════════════

    /**
     * Danh sách sinh từ [WidgetRegistry] (không chép tay): thêm một widget dựng tay mà quên đăng ký đường đổ thì bài
     * này đỏ kèm tên widget. `w_photos` được miễn vì nó **tự lo nội dung**
     * ([WorkspaceRenderPlanner.selfDriven]) — [refreshRead] không bao giờ chạm nó.
     */
    @Test
    fun `moi widget dung tay dang ky duong do gia tri`() {
        val build = SourceRoots.body(widgets, "fun build(")
        val missing = mutableListOf<String>()
        var checked = 0
        WidgetRegistry.ALL.map { it.id }
            .filterNot { WorkspaceRenderPlanner.selfDriven(it) }
            .forEach { id ->
                val m = Regex(""""$id"\s*->\s*(?:([A-Za-z]\w*)\.)?([a-zA-Z]\w*)\s*\(""").find(build)
                assertTrue(m != null, "không đọc được bộ vẽ của $id trong build() — đổi dạng nhánh `when`?")
                val (obj, callee) = m!!.groupValues[1].ifEmpty { null } to m.groupValues[2]
                val body = anyBody(callee, obj)
                assertTrue(body != null, "không tìm thấy thân bộ vẽ `$callee` của $id ở tầng vẽ ô giữa màn")
                checked++
                if (!body!!.contains("WidgetRefreshers.live(")) missing += "$id ($callee)"
            }
        assertEquals(
            emptyList<String>(), missing,
            "widget dựng tay KHÔNG đăng ký đường đổ giá trị ⇒ mỗi nhịp trạng thái xe nó bị tháo/gắn lại = GIẬT: " +
                missing,
        )
        // Chốt CHÍNH BÀI TEST: phép đọc nguồn hụt (đổi tên hàm, đổi dạng `when`) thì nó kiểm 0 widget và xanh vô
        // nghĩa. 8 = 9 widget dựng tay trừ `w_photos`.
        assertEquals(8, checked, "chỉ soi được $checked bộ vẽ — phép đọc nguồn đã hụt, không phải mã đúng")
    }

    /** Ô NÉN cũng vậy: mọi nhánh `w_*` của `mini()` phải đi qua một bộ dựng CÓ đăng ký đường đổ. */
    @Test
    fun `moi o nen cua widget dung tay cung co duong do`() {
        val fn = SourceRoots.body(widgets, "private fun mini(")
        val bad = mutableListOf<String>()
        WidgetRegistry.ALL.map { it.id }
            .filterNot { WorkspaceRenderPlanner.selfDriven(it) }
            .forEach { id ->
                val line = fn.lines().firstOrNull { Regex(""""$id"\s*->""").containsMatchIn(it) }
                assertTrue(line != null, "không tìm thấy nhánh ô nén của $id")
                // Hai đường hợp lệ: `miniCard(...)` (tự đăng ký) hoặc một bộ vẽ riêng đã đăng ký (`tyreMini`).
                val viaCard = line!!.contains("miniCard(")
                val viaOwn = Regex("""->\s*([a-z]\w*)\s*\(""").find(line)?.groupValues?.get(1)
                    ?.let { name ->
                        val own = anyBody(name)
                        own != null && (own.contains("WidgetRefreshers.live(") || own.contains("miniCard("))
                    }
                if (!viaCard && viaOwn != true) bad += id
            }
        assertEquals(emptyList<String>(), bad, "ô nén không có đường đổ tại chỗ ⇒ vẫn giật: $bad")
        assertTrue(shared.contains("WidgetRefreshers.live("), "miniCard phải tự đăng ký đường đổ")
    }

    /** Đường telemetry chung (ô to + ô nén) cũng đổ tại chỗ — nó là họ ô ĐÔNG NHẤT trên màn. */
    @Test
    fun `duong telemetry chung do tai cho cho ca o to lan o nen`() {
        listOf("telemetry", "telemetryMini").forEach { name ->
            val body = anyBody(name)
            assertTrue(body != null, "không tìm thấy `$name`")
            assertTrue(body!!.contains("WidgetRefreshers.live("), "`$name` phải đăng ký đường đổ giá trị")
        }
    }

    // ══ 3 · Hàm đổ KHÔNG dựng View mới ═════════════════════════════════════════════════════════════════════

    /**
     * Bất biến đắt nhất của lượt này.
     *
     * Một hàm đổ mà dựng `TextView`/`LinearLayout`/ô vẽ mới rồi `addView` thì nó chỉ **dời** cú giật vào trong, chứ
     * không bỏ nó: vẫn là một lượt đo–đặt–vẽ mỗi giây, mà nay còn khó thấy hơn vì không còn `removeViewAt` nào để
     * grep. Thứ gì cố định theo mã khả năng (nhãn · hình · dấu *"chưa kiểm"*) phải dựng ở hàm dựng.
     *
     * ⚠ `\b` trước `View\(` không phải trang trí: thiếu nó thì `addView(` cũng khớp `View(` và thông báo lỗi chỉ
     * đúng một nửa.
     */
    @Test
    fun `ham do KHONG dung View moi`() {
        val forbidden = Regex(
            """\b(?:View|TextView|ImageView|LinearLayout|FrameLayout|RingView|TyreBoardView|CarMiniView""" +
                """|PhotoWidgetView|MiniCard|BoardCell|RingCard|ringCard|tv|col|addView)\s*\(""",
        )
        val offenders = mutableListOf<String>()
        var fills = 0
        listOf("WidgetViews.kt" to widgets, "WidgetTelemetry.kt" to shared, "MediaWidgetView.kt" to media)
            .forEach { (name, src) ->
                // Mỗi bộ vẽ đặt tên hàm đổ RIÊNG (`fillTyreBoard`/`fillEnergy`/…) — không phải để đẹp: bài canh
                // `CarDataDemandRendererContractTest` tra hàm phụ **theo tên** và lấy lần khai ĐẦU TIÊN trong tệp,
                // nên bảy hàm cùng tên `fill` làm nó quy nhu cầu của ô lốp cho mọi widget. [ĐO] đúng lỗi đó.
                bodies(src, Regex("""\bfun fill\w*\s*\(""")).forEach { body ->
                    fills++
                    forbidden.findAll(body).forEach { offenders += "$name: hàm đổ dựng `${it.value}`" }
                }
            }
        assertEquals(emptyList<String>(), offenders, "hàm đổ dựng View mới ⇒ chỉ dời cú giật, không bỏ nó: $offenders")
        // Sàn tự-kiểm: 7 hàm đổ ở WidgetViews (clock · energy · pm25 · speed · tyreBoard · carState · board) + 2 ở
        // WidgetTelemetry (telemetry · telemetryMini) + 1 ở MediaWidgetView.
        assertTrue(fills >= 10, "chỉ đọc được $fills hàm đổ — phép quét đã hụt, không phải mã sạch")
    }

    // ══ 4 · Sổ đăng ký không giữ view sống ═════════════════════════════════════════════════════════════════

    @Test
    fun `so dang ky giu ham do TREN VIEW, khong bang map keyed by View`() {
        // ⚠⚠ [SOÁT 2026-09-21 · P1] Bài này đã ĐẢO CHIỀU. Bản đầu đòi `WeakHashMap<View, …>` với lý do *"giữ mạnh
        // là rò Context"* — lý do đúng, kết luận SAI: mọi hàm đổ đều **bắt chính view** nó đổ vào, và trong
        // `WeakHashMap` **giá trị được giữ MẠNH**, nên một giá trị trỏ về khoá của nó làm khoá không bao giờ
        // weakly-reachable ⇒ không mục nào bị dọn (cảnh báo có sẵn trong tài liệu `java.util.WeakHashMap`). Tức bài
        // canh cũ **ghim đúng cái rò** mà nó tưởng mình đang chặn — đây là ca "bài canh cũng sai" của dự án.
        //
        // Hình dạng ĐÚNG: view giữ hàm đổ (`setTag`), nên cả vòng chết cùng ô. Khoá luật theo NGUYÊN NHÂN (không có
        // sổ nào keyed by View) chứ không theo hiện tượng — `HashMap`/`WeakHashMap`/`IdentityHashMap` đều đổ cùng
        // một bệnh nếu giá trị bắt view.
        assertFalse(
            Regex("""(Weak|Identity)?HashMap<\s*View""").containsMatchIn(registry),
            "sổ keyed by View là rò View+Context: hàm đổ bắt chính view của nó nên khoá không bao giờ bị dọn",
        )
        listOf("R.id.kachi_widget_fill", "R.id.kachi_widget_action_fill").forEach { key ->
            assertTrue(registry.contains(key), "hàm đổ phải giữ trên chính view qua tag `$key`")
        }
        // Đọc/ghi tag chỉ được ở ĐÚNG tệp này — hai khoá lọt ra tệp khác là câu hỏi "ai đang đổ ô này" phải đi tìm.
        listOf(widgets, shared, media).forEach { src ->
            assertFalse(
                src.contains("kachi_widget_fill") || src.contains("kachi_widget_action_fill"),
                "hai khoá tag là của riêng WidgetRefreshers — mọi chỗ khác đi qua live()/refresh()",
            )
        }
        // Và KHÔNG còn bản sao thứ hai của sổ ô hành động ở tầng vẽ (nó từng sống trong WidgetViews).
        assertFalse(
            Regex("""(Weak|Identity)?HashMap<\s*View""").containsMatchIn(widgets),
            "sổ đăng ký phải khai ĐÚNG MỘT chỗ (WidgetRefreshers) — hai bảng là hai chỗ để lệch nhau",
        )
    }

    /** Trần 500 dòng (CLAUDE.md §4.1) cho bốn tệp lượt này chạm tới. */
    @Test
    fun `bon tep cua luot nay khong vuot tran 500 dong`() {
        listOf("WidgetViews.kt", "WidgetTelemetry.kt", "WidgetRefreshers.kt", "MediaWidgetView.kt").forEach { name ->
            val n = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/$name").lines().size
            assertTrue(n <= 500, "$name dài $n dòng — trần là 500")
        }
    }
}
