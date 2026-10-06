package com.byd.clusternav.modules.clustercast.simplified

/**
 * Kiểu khung chiếu của cụm — "lỗ" trong suốt của theme cụm mà app chiếu lọt qua (`cluster-rect-seal-2026-10-05.md` §2).
 *  • [CURVED] — theme 12.3" (thấu kính cong, km/h gốc còn) — opcode 30 trên DiLink 3 [ĐO xe 05/10].
 *  • [RECT] — theme 10.25" FULL (chữ nhật trọn ngang, km/h gốc MẤT) — opcode 31 [ĐO xe 05/10 `31 → 16 → 35`].
 */
enum class CastStyle {
    CURVED, RECT;

    companion object {
        /**
         * B1b — đọc lựa chọn đã lưu (`cast_style`, chuỗi [name]). Vắng / lạ / sai kiểu ⇒ [CURVED] (D3 — đường đang chạy). Giá
         * trị đến từ tệp hồ sơ người khác gửi cũng chỉ ra được một trong hai kiểu — không có gì đi vào lệnh shell.
         */
        fun parse(raw: Any?): CastStyle = values().firstOrNull { it.name == raw } ?: CURVED
    }
}

/**
 * ═══ CLUSTER-THEME-SAFE (2.89) — CÔNG THỨC lệnh AutoContainer của đời xe, dạng THUẦN cho `:core` ═══════════════════════
 *
 * Trước 2.89 [ProjectionManager] ghi cứng `AutoContainer` + `30 → 16 → 35` / `18 → 0`, còn `ClusterProfile` (ở `:app`) có
 * sẵn `castSeq` · `teardownSeq` · `svcName` · `styleOps` mà đường chiếu SimpleCast KHÔNG đọc (trường chết — CLAUDE.md §7:
 * khác biệt đời xe phải nằm trong `ClusterProfile`). `:core` không thấy được `ClusterProfile` (nó đụng `Context`), nên
 * `:app` dịch hồ sơ sang lớp này (`ClusterProfile.projectionRecipe()`) và tiêm vào [ProjectionManager].
 *
 * ## B1a (2.89) — tách opcode theme khỏi chuỗi chiếu
 * [castSeq] / [teardownSeq] KHÔNG BAO GIỜ chứa opcode theme ([of] bóc ra); opcode theme nằm ở [styleOps] theo kiểu, và chỉ
 * [ClusterStylePlan] chọn có gửi một opcode trước [castSeq] hay không. Seal DL3 lần mở đầu sau nổ máy vẫn ĐÚNG từng byte
 * `30 →2s→ 16 →2s→ 35 →1s` (CLAUDE.md §6 — `ProjectionManagerThemeGateTest`).
 *
 * ## Bốn bất biến của lệnh
 *  1. **Luôn có đuôi `s16 ""`** — [ĐO xe 05/10] `service call AutoContainer 2 i32 1000 i32 N` thiếu tham số chuỗi trả
 *     `Parcel(fffffffc …)` = EX_NULL_POINTER và KHÔNG làm gì (`docs/diagnostics/oncar-2026-10-05-slot-cluster.md` §4).
 *     Một chỗ dựng duy nhất: [svcCall].
 *  2. **Opcode cấm không bao giờ đi ra** ([FORBIDDEN_OPS]) — chuỗi hồ sơ là dữ liệu share qua nhóm (KHÔNG tin cậy) nên lọc
 *     ở đây, tầng dựng lệnh.
 *  3. **Opcode theme ([themeOps]) chỉ đi qua cổng [ThemeGate]** — [ĐO xe 05/10, hai lần] gửi 30/31 khi màn ảo cụm còn lớp
 *     Android ⇒ SurfaceFlinger chết (`DEAD_OBJECT`, `HWComposer::getActiveConfig` từ `Layer::Handle` huỷ) ⇒
 *     `system_server` khởi động lại. Cổng ở [ClusterThemeGuard] / [ClusterThemePlan].
 *  4. **Tên service + opcode theme chỉ được viết chữ ở đây** (và catalog đo tay CarExec) — `ThemeOpcodeLiteralContractTest`.
 */
data class ProjectionRecipe(
    /** Tên service — [SVC_DILINK3] · [SVC_DILINK5]. Đã qua [SVC_OK]. */
    val svcName: String,
    /** Chuỗi mở chiếu, theo thứ tự gửi. Không rỗng, không chứa [FORBIDDEN_OPS], không chứa opcode theme. */
    val castSeq: List<Int>,
    /** Chuỗi trả đồng hồ. Không rỗng, không chứa [FORBIDDEN_OPS], không chứa opcode theme (D6: tắt chiếu không đổi theme). */
    val teardownSeq: List<Int>,
    /** Opcode ép kiểu cụm, theo kiểu. Chỉ chứa [KNOWN_THEME_OPS]. Rỗng = đời xe không đổi kiểu (DiLink 5). */
    val styleOps: Map<CastStyle, Int> = emptyMap(),
    /**
     * Kiểu GỐC của cụm (sau nổ máy, không lệnh nào) — sự thật phần cứng do `:app` dò (`persist.sys.car.type`). `null` = chưa
     * biết ⇒ [CastStyle.RECT] bị ẩn ([offers]). Seal 10.25" (`car.type=138`) = [CastStyle.RECT] [ĐO-gv 05/10].
     */
    val nativeStyle: CastStyle? = null,
    /**
     * Cho phép gửi theme khi màn ảo cụm CÒN nhưng trống (mức B). Mặc định `false`; 2.90 bật CHỈ cho đời xe đã đo
     * (`ClusterProfile.forCarType`: Seal `car.type=138` + `AutoContainer`) — [ĐO xe 06/10] màn ảo có 0 task + 0 cửa sổ ⇒ gửi
     * `31` không sập, màn ảo dựng lại với id MỚI (4 → 9) (`docs/diagnostics/oncar-2026-10-06-cluster-rect.md` F4). DashCast ghi
     * màn ảo tạo lại làm hỏng sổ display của ATM (`ClusterManager.kt:37`) ⇒ bên gọi dò lại id sau khi gửi. Không vào chuỗi export.
     */
    val themeOnVacantVd: Boolean = false,
) {
    /** Mọi opcode coi là đổi theme: [KNOWN_THEME_OPS] ∪ [styleOps]. */
    val themeOps: Set<Int> get() = KNOWN_THEME_OPS + styleOps.values

    /** Lệnh shell cho [op] trên đúng service của đời xe này (luôn kèm `s16 ""`). */
    fun command(op: Int): String = svcCall(svcName, op)

    fun isTheme(op: Int): Boolean = op in themeOps

    /**
     * Đời xe này có cho người lái chọn [style] không. [CastStyle.RECT] chỉ khi kiểu gốc ĐÃ BIẾT là chữ nhật và có opcode ép
     * (bảng B.2: Seal `car.type=138`); xe khác ẩn — opcode 31 trên cụm 8.8" đẩy cụm về "simple mode" [ĐO DashCast
     * INC-20260625], DL5 không có opcode kiểu.
     */
    fun offers(style: CastStyle): Boolean = when (style) {
        CastStyle.CURVED -> styleOps[CastStyle.CURVED] != null
        CastStyle.RECT -> nativeStyle == CastStyle.RECT && styleOps[CastStyle.RECT] != null
    }

    /** Kiểu mà [op] ép ra trên đời xe này; `null` = không phải opcode kiểu của hồ sơ. */
    fun styleOf(op: Int): CastStyle? = styleOps.entries.firstOrNull { it.value == op }?.key

    companion object {
        /**
         * Opcode đổi theme ĐÃ BIẾT: 30 · 31 [ĐO xe 05/10: 31 ⇒ theme 10.25", 30 trả về]; 29 [SUY — DashCast RE 29/07 gọi là
         * 8.8" (Atto 3/Dolphin) cùng họ với 30/31, `CarExecClusterProjectionCatalog` `style.probe-screen-size-29`].
         */
        val KNOWN_THEME_OPS: Set<Int> = setOf(29, 30, 31)

        /**
         * Opcode KHÔNG BAO GIỜ gửi từ hồ sơ:
         *  • 17 — [ĐO disasm `libBydDataSource.so`, 05/10] chỉ ghi bền `SetConfig_UINT8(53,3)` + khung CAN `0x40C03032`, không
         *    đổi bố cục (`docs/diagnostics/cluster-rect-seal-2026-10-05.md` §4).
         *  • 41 — [ĐO disasm @0x146ea8, nghiên cứu 05/10 `rect-design` F8] ghi `adasInterfaceDisplay` KÈM ghi bền
         *    `theme_index/navi_type` và gửi CAN — trạng thái ngoài hệ sống qua tắt máy, không đường trả lại (CLAUDE.md §5).
         *    2.91 · F5 — [ĐO disasm `libBydDataSource.so`, nghiên cứu 06/10 `docs/diagnostics/cluster-rect-adas-shrink-2026-10-06.md`]:
         *    41 là đường THỬ ĐÈN (lamp-test) — đặt CỨNG mục dữ liệu 466 (`adasInterfaceDisplay`) = 2, ghi BỀN cấu hình cụm 49 và 53,
         *    và gửi CAN `0x40C03032` = 3; opcode 42 KHÔNG hoàn tác cấu hình 49/53. ⇒ không phải cần gạt thu nhỏ khung ADAS, và cấm.
         */
        val FORBIDDEN_OPS: Set<Int> = setOf(17, 41)

        /** Tên service DiLink 2/3/4 (RE DashCast v1.5.4 `ClusterManager.SERVICE_NAME`). */
        const val SVC_DILINK3: String = "AutoContainer"

        /** Tên service DiLink 5 (RE DashCast `DiagActivity:2287` `isDiLink5 ? "auto_container"`). */
        const val SVC_DILINK5: String = "auto_container"

        private val SVC_OK = Regex("^[A-Za-z0-9_]{1,32}$")

        /** Tên service hợp lệ (chữ/số/_) — chuỗi hồ sơ đi thẳng vào lệnh shell. */
        fun svcOk(name: String): Boolean = SVC_OK.matches(name)

        /** Bộ dựng lệnh DUY NHẤT của `:core` (và `ClusterProfile.svcCall` ở `:app` gọi lại hàm này). */
        fun svcCall(svcName: String, op: Int): String = "service call $svcName 2 i32 1000 i32 $op s16 \"\""

        private val SEAL_CAST = listOf(16, 35)
        private val SEAL_TEARDOWN = listOf(18, 0)

        /**
         * Đúng chuỗi đã chạy trên Seal DL3 từ 2026-08-02 (`30` ở [styleOps] CURVED, `16 → 35`, `18 → 0`) — mặc định của
         * [ProjectionManager] (và các test cũ). Kiểu gốc chưa biết ⇒ RECT ẩn.
         */
        val SEAL_DL3: ProjectionRecipe = ProjectionRecipe(
            svcName = SVC_DILINK3,
            castSeq = SEAL_CAST,
            teardownSeq = SEAL_TEARDOWN,
            styleOps = mapOf(CastStyle.CURVED to 30, CastStyle.RECT to 31),
        )

        /**
         * Chuỗi cũ (7–9 trường, `castSeq` có opcode theme) → (opcode theme ĐẦU TIÊN, chuỗi còn lại không còn opcode theme
         * nào). Opcode cấm bị bỏ trước. Cách tách duy nhất cho cả `ClusterProfile.parse` lẫn [of].
         */
        fun peelTheme(seq: List<Int>, extraTheme: Set<Int> = emptySet()): Pair<Int?, List<Int>> {
            val theme = KNOWN_THEME_OPS + extraTheme
            val clean = seq.filter { it !in FORBIDDEN_OPS }
            return clean.firstOrNull { it in theme } to clean.filter { it !in theme }
        }

        /**
         * Dựng từ các trường của hồ sơ (đã được `ClusterProfile.parse` lọc dải 0..255). Làm sạch thêm ở tầng dựng lệnh:
         * service lạ ⇒ [SVC_DILINK3]; bỏ [FORBIDDEN_OPS]; [styleOps] chỉ giữ [KNOWN_THEME_OPS] (opcode hồ sơ khai là kiểu mà
         * lạ ⇒ không gửi như theme, và cũng bị bóc khỏi chuỗi chiếu — không bao giờ đi ra mà không qua cổng); bóc MỌI opcode
         * theme khỏi [castSeq]/[teardownSeq]; chuỗi rỗng sau khi lọc ⇒ chuỗi Seal (rỗng = projection không bao giờ mở được).
         * Chuỗi chiếu còn opcode theme đã biết mà [styleOps] chưa có CURVED ⇒ opcode đó là CURVED (chuỗi cũ: thứ thật sự được
         * gửi lúc mở chiếu — giữ hành vi).
         */
        fun of(
            svcName: String,
            castSeq: List<Int>,
            teardownSeq: List<Int>,
            styleOps: Map<CastStyle, Int>,
            nativeStyle: CastStyle?,
            themeOnVacantVd: Boolean = false,
        ): ProjectionRecipe {
            val svc = svcName.takeIf(::svcOk) ?: SVC_DILINK3
            val declared = styleOps.values.toSet()
            val known = styleOps.filterValues { it in KNOWN_THEME_OPS && it !in FORBIDDEN_OPS }
            val (peeled, castLeft) = peelTheme(castSeq, declared)
            val styles = if (CastStyle.CURVED !in known && peeled != null && peeled in KNOWN_THEME_OPS) {
                known + (CastStyle.CURVED to peeled)
            } else {
                known
            }
            val cast = castLeft.ifEmpty { SEAL_CAST }
            val tear = peelTheme(teardownSeq, declared).second.ifEmpty { SEAL_TEARDOWN }
            return ProjectionRecipe(svc, cast, tear, styles, nativeStyle, themeOnVacantVd)
        }
    }
}

/**
 * Lời đáp của [ThemeGate.admit] cho MỘT opcode theme (review 2.89 Pass 1 · safety-1 — trước là `Boolean`).
 * Đây là lời của CỔNG; quyết định cuối (gửi · bỏ-đi-tiếp · dừng lượt mở) là của [ClusterStylePlan.decide].
 */
enum class ThemeVerdict {
    /** Gửi opcode. */
    SEND,

    /**
     * Bỏ opcode; màn ảo cụm CÓ TỪ TRƯỚC lượt mở (cụm đang trong một phiên chiếu) hoặc vừa gửi cùng theme trong tiến trình
     * này ⇒ chuỗi đi tiếp 16/35 ([ĐO 05/10] gửi lại 16/35 khi cụm có app: "không đổi gì").
     */
    SKIP_KNOWN,

    /**
     * Bỏ opcode và cổng KHÔNG bảo đảm được kiểu cụm (đọc display hỏng · chưa có màn ảo cụm mà phải bỏ, vd `TOO_SOON`).
     * [ClusterStylePlan.decide] chỉ cho đi tiếp 16/35 khi sổ theme (cùng tiến trình) chứng minh cụm đã ở đúng kiểu người lái
     * chọn; không thì DỪNG lượt mở (chiếu trong theme gốc Seal = mất số km/h [ĐO 05/10]).
     */
    ABORT,
}

/**
 * Cổng của MỘT opcode đổi theme — [ProjectionManager] hỏi [admit] ngay trước khi gửi, gọi [sending] (sổ `pending`) NGAY trước
 * lệnh và [sent] (sổ `ok`) sau khi shell nhận. Thực thi duy nhất trên đường chạy là [ClusterThemeGuard]. [ledger]/[now]
 * cho [ClusterStylePlan] suy kiểu cụm đang ở — chỉ để BỎ hoặc SUY, không bao giờ để cho phép (CLAUDE.md §5).
 */
interface ThemeGate {
    fun admit(op: Int): ThemeVerdict
    fun sent(op: Int)

    /**
     * Ghi sổ `pending` TRƯỚC khi gửi [op] (tiến trình chết giữa chừng ⇒ lần sau thấy `pending` ⇒ kiểu UNKNOWN). Review 2.89 Pass 2
     * · cluster-r1-5: `false` = KHÔNG ghi được dấu ⇒ bên gọi KHÔNG được gửi [op] (CLAUDE.md §5 — dấu trước, đổi sau; ghi hỏng thì
     * sổ còn mục cũ ⇒ khoảng 15 s và kiểu suy ra từ sổ đều sai).
     */
    fun sending(op: Int): Boolean = true

    /** Mục sổ theme bền gần nhất; `null` = trống / hỏng. */
    fun ledger(): ThemeLedger.Entry? = null

    /** Đồng hồ của sổ. Mặc định = không biết gì ⇒ mọi suy luận "cùng tiến trình" đều sai ⇒ hướng an toàn. */
    fun now(): ThemeLedger.Now = ThemeLedger.Now.UNKNOWN
}
