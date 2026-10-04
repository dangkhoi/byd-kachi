package com.byd.clusternav.launcher

/**
 * Một chip trên thanh trạng thái sau khi đã quyết định xong nội dung — **thuần dữ liệu**, không biết View.
 *
 * @property text chữ hiện ra (đã qua lớp đơn vị).
 * @property icon tên icon `ic-*`, hoặc `null` nếu chip chỉ có chữ.
 * @property tone SẮC THÁI, không phải mã màu — bảng màu nằm ở `:app` (`KachiTheme`). Nếu `:core` giữ mã hex thì dự án
 *   có **hai bảng màu** và chúng sẽ lệch nhau: [ĐO] bản nháp đầu của tệp này viết `#37d67a` trong khi `KachiTheme.GREEN`
 *   là `#34d399` — đúng cái bẫy hai-bản-sao mà dự án đang dọn.
 * @property desc câu đọc cho trình đọc màn hình — chip rất ngắn nên chữ hiện ra thường không đủ nghĩa.
 * @property runs 2.88 — đoạn chữ mang SẮC THÁI RIÊNG (chip lốp: mỗi con số một màu). Rỗng (mặc định) ⇒ cả chuỗi một
 *   màu [tone] như mọi chip trước 2.88 — các chip cũ không đổi một byte nào.
 */
data class ChipView(
    val text: String,
    val icon: String?,
    val tone: ChipTone,
    val desc: String,
    val runs: List<ChipRun> = emptyList(),
)

/**
 * Một đoạn `[start, end)` của [ChipView.text] mang sắc thái [tone] riêng. Vị trí tính theo `Char` của chuỗi Kotlin —
 * đúng đơn vị của `Spannable` ở `:app`, nên tầng vẽ đặt span thẳng, không tính lại.
 */
data class ChipRun(val start: Int, val end: Int, val tone: ChipTone)

/**
 * Sắc thái chip. `:app` dịch sang mã màu — xem [ChipView.tone].
 *
 * [ACTIVE]/[INACTIVE] = datum **BẬT/TẮT** đang bật / đang tắt (owner 2026-09-21: *"trạng thái bật/tắt phải thể hiện
 * bằng icon active/inactive (màu), KHÔNG bằng chữ Tắt/Bật"*). Chip loại này **bỏ hẳn phần chữ giá trị** — trạng thái
 * nằm ở MÀU của icon + nhãn, xem [TopStripChips.datumChip].
 *
 * ⚠ [NEUTRAL] vẫn là sắc thái của datum bật/tắt **chưa đọc được** (off-car / không có trên trim): lúc đó chip hiện
 * `"—"` như mọi datum khác. Tô nó thành [INACTIVE] là **bịa trạng thái** — nói *"đang tắt"* trong khi sự thật là
 * *"không biết"*, đúng điều dự án cấm (xem [TelemetryView.PLACEHOLDER]).
 *
 * [WARN]/[ALERT] (2.88) = xe báo VÀNG / ĐỎ cho một bánh lốp ([TyreSeverity]). Chip lốp tô bánh bình thường bằng
 * [ENERGY] — *"xanh lá như màu range lái"* (owner 04/10) — và bánh chưa phán được bằng [NEUTRAL].
 */
enum class ChipTone { NEUTRAL, ENERGY, ACTIVE, INACTIVE, WARN, ALERT }

/**
 * MỘT KHỐI của màn chọn chip — xem [TopStripConfig.picks].
 *
 * Cố ý **không mang tiêu đề dạng chuỗi**: tiêu đề của khối lĩnh vực là [Domain.displayLabel] (đã có ở `:core`),
 * còn tiêu đề khối *"đang bật"* phải kèm con số `N/${TopStripConfig.CAP}` nên nó là một chuỗi **tài nguyên** của
 * `:app` (VI/EN). Dựng sẵn một chuỗi ở đây là ép `:core` giữ bản sao thứ hai của cùng một câu.
 *
 * @property on khối *"đang bật"* (đúng một khối, luôn đứng đầu).
 * @property domain lĩnh vực của khối; `null` ⇒ khối *"đang bật"* ([on]) hoặc khối mục chưa xếp lĩnh vực.
 * @property picks các ô của khối — mỗi mã chỉ xuất hiện ở **một** khối (xem cảnh báo `tiles[id]` ở [TopStripConfig.picks]).
 * @property open mặc định mở hay gấp. Tầng vẽ giữ trạng thái của phiên, giá trị ban đầu quyết ở `:core`.
 */
data class ChipSection(
    val on: Boolean,
    val domain: Domain?,
    val picks: List<CapabilityPick>,
    val open: Boolean,
)

/**
 * CẤU HÌNH THANH TRÊN — danh sách khả năng hiện thành chip, thứ tự = thứ tự hiển thị.
 *
 * Đây là **vùng thứ ba** của RW0 ("đặt được ở thanh trên / ô giữa màn / thanh tác vụ"). Trước 2026-09-11 thanh trên là
 * **3 chip viết cứng** trong bộ vẽ, nên nó là vùng duy nhất người dùng không sửa được.
 *
 * ⚠⚠ **CHỈ NHẬN MỤC ĐỌC — quyết định có chủ ý, không phải bỏ sót.** Hai lý do:
 *  1. **An toàn**: chip cao ~24dp, dưới xa mức tối thiểu 48dp cho một đích chạm. Một cú chạm lệch trên thanh trên mà
 *     bắn lệnh xe (vd "Mở khoá cửa") là hậu quả không hoàn lại được. Nút có hai vùng khác rộng rãi hơn để đặt.
 *  2. **Ngữ nghĩa**: thanh trên là dòng trạng thái nhìn-là-biết, không phải bảng điều khiển.
 * Nếu owner muốn chip bấm được thì đó là một quyết định riêng, cần đích chạm to hơn — KHÔNG lặng lẽ nới ở đây.
 */
data class TopStripConfig(
    val ids: List<String> = DEFAULT_IDS,
    /**
     * ═══ V3 · R14 — có vẽ **NHÃN** trên chip không (mặc định có) ═════════════════════════════════════════
     *
     * Owner 2026-09-16: *"chỉ ẩn icon và chỉ số thôi, text nhiều chật chỗ, cho cái toggle hiện text label"*. Tắt
     * ⇒ chip chỉ còn **icon + giá trị (+đơn vị)** (`2.4 bar` thay `Lốp TT · 2.4 bar`).
     *
     * ## Vì sao là một trường của cấu hình, không phải một cờ vẽ ở `:app`
     * Chữ trên chip do [TopStripChips] dựng ở `:core` (đó là lý do phép đổi đơn vị mới kiểm được off-car). Một cờ
     * ở tầng vẽ sẽ phải **cắt chuỗi đã dựng** — tức đoán lại dấu `·` nằm ở đâu, trên một chuỗi mà chính `:core`
     * vừa ghép. Đặt ở đây thì mỗi dạng chip tự biết bỏ phần nào, và có bài test.
     *
     * **Theo HỒ SƠ** như [ids] (S4 *"hồ sơ là tất cả"*), lưu ở khoá riêng `top_strip_labels` chứ không nhét vào
     * chuỗi mã: chuỗi ấy đã nằm trên đĩa của xe đang chạy, và [decode] của mọi bản cũ đọc nó như một danh sách
     * mã — thêm một ô lạ vào là bản cũ lọc nó đi (may) hoặc dựng một chip rỗng (rủi).
     */
    val showLabels: Boolean = true,
) {

    init {
        // ⚠ Chốt ở CHÍNH LỚP, không chỉ ở [setEnabled]. Quét bảo mật trước commit nêu đúng: nếu cổng chỉ nằm ở
        // `setEnabled` thì lớp đang dựa vào "mọi chỗ gọi trong tương lai đều nhớ đi qua nó" — tức một luật con người
        // phải nhớ, đúng loại giả định dự án đã trả giá (bẫy hai-bản-sao). Ở đây hậu quả nếu quên là một mã HÀNH ĐỘNG
        // lọt lên thanh trên, tức một đích chạm 24dp bắn lệnh xe không hoàn lại được.
        require(ids.all { isChippable(it) }) {
            "thanh trên chỉ nhận mục ĐỌC — mã không hợp: " + ids.filterNot { isChippable(it) }
        }
        require(ids.size <= CAP) { "thanh trên chứa tối đa $CAP chip, nhận ${ids.size}" }
    }

    /**
     * Bật/tắt một khả năng. Từ chối mã lạ (không nhét rác vào cấu hình bền) **và** từ chối mã HÀNH ĐỘNG (xem KDoc
     * lớp) — cùng tinh thần với [DockConfig.setEnabled], chỉ khác ở chỗ vùng này hẹp hơn có lý do.
     */
    fun setEnabled(id: String, on: Boolean): TopStripConfig {
        if (!on) return copy(ids = ids - id)
        if (id in ids) return this
        if (ids.size >= CAP) return this                       // đầy thì bỏ qua, không đẩy mục khác ra
        if (!isChippable(id)) return this
        return copy(ids = ids + id)
    }

    fun has(id: String): Boolean = id in ids

    /**
     * #15 (owner 2026-09-21 "chỉnh vị trí từng thông tin"): dời một chip SANG TRÁI (sớm hơn trong thứ tự hiện) —
     * thứ tự trong [ids] CHÍNH là thứ tự hiện trên thanh. Mã không có / đã ở đầu ⇒ trả nguyên (no-op an toàn).
     */
    fun moveEarlier(id: String): TopStripConfig {
        val i = ids.indexOf(id)
        if (i <= 0) return this
        val m = ids.toMutableList(); m[i] = m[i - 1]; m[i - 1] = id
        return copy(ids = m)
    }

    /** Dời một chip SANG PHẢI (muộn hơn). Mã không có / đã ở cuối ⇒ no-op. */
    fun moveLater(id: String): TopStripConfig {
        val i = ids.indexOf(id)
        if (i < 0 || i >= ids.size - 1) return this
        val m = ids.toMutableList(); m[i] = m[i + 1]; m[i + 1] = id
        return copy(ids = m)
    }

    companion object {
        /**
         * TRẦN CHIP — **8** từ S4 · R11 (owner 2026-09-14: *"hiện chỉ cho chọn 3 trong khi có thể chọn nhiều hơn"*).
         *
         * ## Vì sao 4 → 8, và vì sao con số này không còn là "chỗ trống chia cho bề rộng một chip"
         * Trần cũ (4) được đặt khi thanh trên còn mang **5 nút bố cục**; R7 gỡ hàng nút đó ⇒ thanh trên trống thêm
         * một quãng rộng. Nhưng điều đổi hẳn cách chọn con số là bản vá đi kèm ở tầng vẽ
         * (`KachiTopStrip.fitChips`): chip nay **tự co + cắt "…"** theo bề rộng còn lại, nên vượt trần không còn
         * nghĩa là *tràn đè lên nút bên phải* — nó chỉ nghĩa là *mỗi chip đọc được ít chữ hơn*.
         *
         * [SUY] theo số ở 1920px (chưa [ĐO] ảnh — việc của S4 · T4): thanh trên trừ lề + đồng hồ/ngày + ba vật bên
         * phải (chip hồ sơ · Ứng dụng · Cài đặt) còn ≈ 1240dp cho hàng chip ⇒ 8 chip ≈ **155dp/chip**, đủ cho
         * `"Lốp TT · 2.4 bar"` (≈ 160dp, cắt đuôi một chút) và thừa cho `"82% · 418 km"`. 12 chip thì còn ≈ 103dp
         * ⇒ phần lớn chip chỉ còn nhãn cụt, tức thanh trên **có chữ mà không đọc được** — tệ hơn là không bày.
         * Vì thế trần vẫn tồn tại, chỉ đổi số; nó là trần **đọc được**, không còn là trần *vừa khung*.
         */
        const val CAP = 16

        /** Chip TỔNG HỢP dựng sẵn — xem [TopStripChips]. */
        const val PM25 = "chip_pm25"
        const val TEMP = "chip_outside_temp"
        const val ENERGY = "chip_energy"

        /**
         * UX5 — chip GỘP **ghế sưởi / ghế mát** (owner 2026-09-26: *"gộp icon ghế + sưởi/mát + mức"*).
         *
         * Gộp được vì hai chế độ **loại trừ nhau** trên thực tế (một ghế không vừa sưởi vừa thổi mát), nên hai chip
         * cạnh nhau thì luôn có một cái chỉ để nói *"đang tắt"* — tốn đúng chỗ mà thanh trên không có. Hai datum LẺ
         * **ở lại nguyên** trong bộ chọn (câu hỏi bằng giọng đọc chúng, và ai muốn hai chip vẫn đặt được).
         */
        const val SEAT = "chip_seat"

        /**
         * UX5b — chip GỘP ghế **PHỤ** (owner 2026-09-27: *"ghế sao không có ghế lái hay ghế phụ? 2 ghế nó khác nhau mà"*).
         *
         * Cùng bộ dựng với [SEAT], khác đúng **ba dữ liệu** (mã sưởi · mã mát · nhãn) — xem [TopStripChips.seatChip].
         * Hai chip vì hai ghế là hai câu hỏi khác nhau (*"ghế TÔI có đang sưởi không"* ≠ *"ghế bên kia"*); gộp cả bốn
         * datum vào một chip thì con số hiện ra không nói được nó của ai.
         */
        const val SEAT_R = "chip_seat_r"

        /**
         * 2.88 — chip **ÁP SUẤT LỐP** (owner 04/10: *"vẽ icon bánh xe: 2.4 2.4 2.6 2.6 kiểu vậy, cho 4 bánh, cái nào
         * cảnh báo thì vàng, đỏ, bình thường thì xanh lá như màu range lái"*). Bốn số TT TP · ST SP, mỗi số một màu
         * theo lời phán của chính xe ([TyreBoard] / [TyreJudge]) — xem [TopStripChips] ca này. TUỲ CHỌN: có ở
         * [BUILT_IN], KHÔNG ở [DEFAULT_IDS] (không lặng lẽ mọc thêm chip cho người đang dùng — KDoc [DEFAULT_IDS]).
         */
        const val TYRES = "chip_tyres"

        /**
         * MỖI chip ghế GỘP đọc HAI datum, và cặp ấy khai **đúng một chỗ** — đây.
         *
         * Vì sao là một bảng chứ không hai nhánh `when` chép tay: bộ dựng chip, bảng nhu cầu đọc
         * ([CarDataDemand.CHIPS]) và bài canh đọc-ngược đều cần cùng cặp mã; ba bản sao của một cặp là đúng bẫy
         * hai-bản-sao mà dự án đang dọn. Thứ tự trong `Pair` là **(sưởi, mát)** — không phải bảng chữ cái — vì quy
         * tắc *"ưu tiên SƯỞI"* đọc theo thứ tự ấy.
         */
        val SEAT_PAIRS: Map<String, Pair<String, String>> = mapOf(
            SEAT to ("seat_heat_state" to "seat_vent_state"),
            SEAT_R to ("seat_heat_state_r" to "seat_vent_state_r"),
        )

        /**
         * ⚠ **THỨ TỰ KHAI QUAN TRỌNG**: [BUILT_IN] phải nằm TRƯỚC [DEFAULT], vì `init` của lớp gọi [isChippable] mà
         * hàm đó đọc [BUILT_IN] — [ĐO] khai sau thì việc dựng [DEFAULT] lúc nạp lớp đọc `BUILT_IN` còn null và cả
         * gói test nổ `ExceptionInInitializerError` (27 bài đỏ). Ba hằng `PM25`/`TEMP`/`ENERGY` là `const` nên an toàn.
         */
        val BUILT_IN: Set<String> = setOf(PM25, TEMP, ENERGY, SEAT, SEAT_R, TYRES)

        /**
         * MẶC ĐỊNH — *"ai không sửa gì thì thấy cái gì"*, **khác** câu hỏi của [BUILT_IN] (*"đặt được cái gì"*).
         *
         * ⚠ UX5 (2026-09-26) — **TÁCH khỏi [BUILT_IN]**. Trước đó đây là `BUILT_IN.toList()`, nên thêm một chip dựng
         * sẵn là **lặng lẽ** mọc thêm một chip trên thanh trên của mọi người đang dùng máy. Hai danh sách vì thế phải
         * là hai danh sách.
         *
         * ⚠⚠ UX5b (owner 2026-09-27) — **HAI chip ghế GỘP nay VÀO mặc định**, và đó là *quyết định của owner*, không
         * phải một mặc định lặng lẽ: owner ngồi trước máy ảo nhìn thanh trên và nói *"sao còn ghế mát và ghế sưởi
         * riêng, với ghế sao không có ghế lái hay ghế phụ? 2 ghế nó khác nhau mà"*. Cơ chế §UX5 vẫn còn nguyên (hai
         * danh sách rời) — chỉ nội dung danh sách này đổi, một lần, có người xin.
         *
         * Thứ tự: giữ nguyên **PM25 · TEMP · ENERGY** rồi **nối** hai chip ghế vào cuối (ghế lái trước ghế phụ). Nối
         * vào cuối vì đó là đường mới (CLAUDE.md §6 — đường mới xuống cuối, không đảo thứ tự đang chạy tốt), và vì
         * `KachiTopStrip.fitChips` cắt từ **phải** sang nên chip vào sau là chip nhường chỗ trước.
         *
         * [ĐO số học, 1920×720 @1.0×] một chip = đệm `XS`×2 + icon `ICON_XS` + khe `S` + chữ, cộng `marginStart = S`
         * ⇒ **32dp + chữ + 8dp**. Ở 13.5sp (`KachiType.BODY`) một ký tự trung bình ≈ 7dp. Năm chip mặc định ở ca xấu
         * nhất (mọi chip đang có giá trị): `"PM2.5 · Tốt"` 11 kt ≈ 117dp · `"24 °C ngoài"` 11 kt ≈ 117dp (không
         * icon ⇒ −24) = 93dp · `"82 % · 418 km"` 13 kt ≈ 131dp · `"Ghế lái · 2"` 11 kt ≈ 117dp · `"Ghế phụ · 2"`
         * ≈ 117dp ⇒ tổng ≈ **575dp**, trong khi hàng chip có ≈ 1240dp ([SUY] của R11, chưa [ĐO] ảnh) ⇒ thừa hơn
         * **hai lần**. Vậy năm chip vừa, và không cần dựa vào phép cắt `…`. (Phép đo bằng MẮT vẫn phải làm — ghi
         * ở doc UX5b §7 mục kiểm.)
         */
        val DEFAULT_IDS: List<String> = listOf(PM25, TEMP, ENERGY, SEAT, SEAT_R)

        /** Mặc định **CŨ** (trước UX5b) — chỉ dùng cho phép di trú [migrate]; xem KDoc ở đó. */
        private val LEGACY_DEFAULT_IDS: List<String> = listOf(PM25, TEMP, ENERGY)

        val DEFAULT = TopStripConfig(DEFAULT_IDS)

        /**
         * Đặt được lên thanh trên: 3 chip dựng sẵn, hoặc một datum ĐỌC. Gói lệnh/nút thì KHÔNG (xem KDoc lớp).
         *
         * ⚠⚠ **NHÓM khả năng (G1) cũng KHÔNG**, dù [CapabilityCatalog.kindOf] trả [CapabilityKind.READ] cho nó. Hai
         * lý do, và cả hai là lý do của CHÍNH chỗ này chứ không phải của loại khả năng:
         *  1. **Không vẽ được**: chip cao ~24dp một dòng chữ; nhóm là bảng 4 bánh / dải 9 đèn / thẻ 10 con số. Nhồi
         *     vào chip thì ra một ô hiện được đúng cái nhãn — mất hết thứ khiến nhóm có ích.
         *  2. **An toàn**: 3/12 nhóm mang nút (kính · cửa & khoang · đèn). Cho nhóm lên đây là mở lại đúng cái cửa mà
         *     KDoc lớp này đóng: một đích chạm 24dp bắn lệnh xe không hoàn lại được.
         * Chặn ở ĐÂY thay vì bắt nhóm khai [CapabilityKind.WRITE] cho "khỏi lọt": làm thế sẽ khiến ô giữa màn dựng
         * nhóm thành một cái nút đơn và mất hết thành viên (xem KDoc [CapabilityCatalog.kindOf]). Hạn chế là của
         * thanh trên, nên nó phải nằm trong thanh trên.
         */
        // ⚠ S4 · R12 — [CapabilityKind.LAUNCHER] cũng bị từ chối ở đây, và **do cấu tạo**: điều kiện là
        // "== READ", không phải "!= WRITE". Viết theo chiều phủ định thì mỗi loại khả năng mới lại lọt lên thanh
        // trên cho tới khi có ai nhớ ra phải chặn — chiều khẳng định thì loại mới mặc định KHÔNG lọt.
        // Bài canh: `TopStripTest.hanh dong cua launcher KHONG len duoc thanh tren`.
        fun isChippable(id: String): Boolean =
            id in BUILT_IN ||
                (CapabilityCatalog.kindOf(id) == CapabilityKind.READ && CapabilityGroups.byId(id) == null)

        /**
         * ═══ 2.76 (R9) · ẨN KHỎI BỘ CHỌN **CHIP** — không ẩn khỏi các bộ chọn khác, không xoá datum ═══════════════
         *
         * Khác [CapabilityCatalog.HIDDEN_FROM_PICKER] (ẩn ở MỌI màn chọn) đúng một điều: lý do ở đây là lý do **của
         * thanh trên**. `ac_wind_auto` trả lời câu *"gió đang tự động không"*, mà chip Gió (`ac_wind`) đã trả lời
         * đúng câu ấy bằng `"auto n"` (UX4/UX8) — hai chip cạnh nhau nói một điều là thứ owner hỏi từ 26/09
         * (*"gộp ac_mode_auto/ac_wind_auto?"*). Ở ô lớn / nhóm / giọng nói / test-bridge datum vẫn có ích nguyên
         * (nó là `readKey` của nút `ac_auto`, xem `ControlRegistry`), nên KHÔNG đụng registry và không ẩn toàn cục.
         *
         * Cơ chế y hệt bảng ẩn toàn cục: [choices] lọc, [isChippable] **không** lọc ⇒ [decode] GIỮ mã ai đã đặt
         * (khoá lưu bền không được mất) và khối *"đang bật"* của [picks] vẫn bày nó để còn GỠ được. Mỗi mục kèm
         * lý do ≥ 40 ký tự — bài canh đọc.
         */
        val CHIP_HIDDEN: Map<String, String> = mapOf(
            "ac_wind_auto" to
                "chip Gió (`ac_wind`) đã nói \"auto n\" ngay trên thanh ⇒ chip này là bề mặt thứ hai cho cùng câu " +
                    "\"gió tự động không\"; owner hỏi gộp 26/09, chốt 27/09: ẩn khỏi bộ chọn CHIP, giữ datum cho " +
                    "ô lớn · nhóm · giọng nói · test-bridge · readKey của nút ac_auto",
        )

        /** Mọi thứ đặt được lên thanh trên, cho màn chọn bày ra. */
        fun choices(): List<CapabilityPick> = buildList {
            // U5 · T2: ba chip TỔNG HỢP không có dòng registry nào để treo `labelEn` vào ⇒ dựng [CapabilityPick] với
            // nhãn Việt + `labelEn` ngay tại chỗ, đúng cùng cơ chế như mọi mục khác (xem KDoc [Strings]).
            // ⚠⚠ **KHÔNG đặt lại tên này thành "Bụi mịn PM2.5"** — đó là tên của datum `pm25_value` từ U6, và hai
            // dòng ấy đứng CẠNH NHAU trong CÙNG khối "Khí hậu & không khí" của màn chọn ([picks] xếp theo [Domain],
            // ô chỉ có nhãn — không vẽ dòng phụ). [ĐO] soát U6: đổi `pm25_value` từ "PM2.5" sang "Bụi mịn PM2.5" đã
            // làm màn chọn có **hai ô chữ y hệt** (cả VI lẫn EN) trỏ vào hai việc khác nhau — chip này đổi mức 1–6
            // thành CHỮ ("PM2.5 · Tốt"), datum kia là TRỊ SỐ µg/m³. Tên ở đây phải nói đúng thứ nó hiện: một lời
            // nhận xét về không khí trong xe. Bài canh: `TopStripTest.hai o tren cung mot man chon…`.
            add(CapabilityPick(PM25, "Không khí trong xe", "ic-leaf", EvidenceTier.PROVEN, CapabilityKind.READ, Domain.CLIMATE,
                labelEn = "Cabin air quality"))
            add(CapabilityPick(TEMP, "Nhiệt độ ngoài", "ic-fan", EvidenceTier.PROVEN, CapabilityKind.READ, Domain.CLIMATE,
                labelEn = "Outside temperature"))
            add(CapabilityPick(ENERGY, "Pin và tầm chạy", "ic-bolt", EvidenceTier.PROVEN, CapabilityKind.READ, Domain.ENERGY,
                labelEn = "Battery and range"))
            // UX5 · chip gộp ghế. Hình ở đây là ghế TRỐNG (chưa biết đang chế độ nào lúc bày bộ chọn); trên thanh
            // thì chip tự đổi sang glyph ghép sưởi/mát theo chế độ đang chạy — xem [TopStripChips.chip] ca [SEAT].
            // ⚠ UX5b — nhãn nói **GHẾ NÀO**, không nói *"sưởi / mát"*: hai chế độ đã nằm ở HÌNH trên thanh, còn thứ
            // owner không đọc được là ghế nào (*"ghế sao không có ghế lái hay ghế phụ"*). Giữ thêm chữ *"(sưởi/mát)"*
            // ở màn chọn — ở đó ô chỉ có nhãn, không có dòng phụ, nên một mình chữ *"Ghế lái"* không nói nó bày cái gì.
            add(CapabilityPick(SEAT, "Ghế lái (sưởi/mát)", "ic-seat-left", EvidenceTier.PROVEN, CapabilityKind.READ,
                Domain.CLIMATE, labelEn = "Driver seat (heat/vent)"))
            add(CapabilityPick(SEAT_R, "Ghế phụ (sưởi/mát)", "ic-seat", EvidenceTier.PROVEN, CapabilityKind.READ,
                Domain.CLIMATE, labelEn = "Passenger seat (heat/vent)"))
            // 2.88 · chip áp suất lốp — hình CẢ XE + 4 bánh (cùng hình nhóm Lốp `g_tyres`: một khái niệm, một hình).
            add(CapabilityPick(TYRES, "Áp suất lốp", CapabilityGroups.TYRES.icon, EvidenceTier.PROVEN, CapabilityKind.READ,
                Domain.TYRES, labelEn = "Tyre pressure"))
            // Lọc bằng CHÍNH [isChippable] thay vì viết lại điều kiện `kind == READ`: bản cũ lặp lại luật, nên khi
            // luật ở [isChippable] chặt thêm (G1 loại NHÓM) thì màn chọn vẫn bày ra thứ mà [setEnabled] sẽ từ chối —
            // người dùng bấm mà không có gì xảy ra. Một luật, một chỗ.
            // 2.76: lọc thêm [CHIP_HIDDEN] — ẩn CHỈ ở bộ chọn chip, xem KDoc ở đó.
            addAll(CapabilityCatalog.all().filter { !it.curated && isChippable(it.id) && it.id !in CHIP_HIDDEN })
        }

        /**
         * MÀN CHỌN CHIP theo **KHỐI**: khối *"đang bật"* trước, rồi mỗi [Domain] một khối (S4 · R11 c).
         *
         * ## Vì sao bày theo khối chứ không phải một danh sách phẳng
         * Tới U6 màn chọn chỉ bày **3 chip dựng sẵn + chip đang bật** (≤ 7 ô); muốn đặt một datum bất kỳ thì phải
         * biết có một nút *"Thêm chip khác…"* mở một hộp thoại **phẳng 120+ dòng**. Owner 2026-09-14: *"hiện chỉ
         * cho chọn 3 trong khi có thể chọn nhiều hơn"* — tức thứ hụt không phải cái trần, mà là **thứ nhìn thấy
         * được**. Một danh sách phẳng 120 dòng thì bày ra cũng như không; xếp theo lĩnh vực thì mỗi khối là một
         * câu hỏi người dùng thật sự có (*"xe còn bao nhiêu pin"*, *"lốp thế nào"*), và R4 (mỗi nhóm ≤ 2 màn cuộn)
         * còn giữ được nhờ tầng vẽ gấp/mở từng khối.
         *
         * ## Ba tính chất mà chỗ gọi được dựa vào (có test khoá từng cái)
         *  1. **Mỗi mã đúng MỘT ô.** Cả hai màn chọn giữ bảng tra `tiles[id] → view` để tô ô đang bật; một mã hai ô
         *     thì `tiles[id]` bị ghi đè và ô trước nói sai cấu hình (đúng lỗi RW0, và là lý do
         *     [CapabilityPicker.singlesOf] tồn tại). Vì thế mục đang bật bị **trừ khỏi** khối lĩnh vực của nó.
         *  2. **Chip mang mã đã ẩn vẫn có ô để GỠ.** U6 thêm [CapabilityCatalog.HIDDEN_FROM_PICKER] (lọc ở `all()`)
         *     còn [decode] **giữ** mã ẩn vì nó lọc bằng [isChippable] chứ không bằng danh sách — đúng thiết kế
         *     (khoá lưu bền của người dùng không được mất). Nếu khối *"đang bật"* cũng dựng từ [choices] thì chip
         *     ấy hiện trên thanh mà **không còn ô nào để bấm gỡ**: một trạng thái không có đường ra. Nên khối này
         *     tra thẳng [CapabilityCatalog.pick] cho mã mà [choices] bỏ sót — và chỉ ở đây, [choices] vẫn phải im
         *     lặng về mã đã ẩn (bày lại chính là đường mời đặt thêm).
         *  3. **[ChipSection.open] là một LUẬT, không phải trạng thái.** Mặc định mở đúng những lĩnh vực đang có
         *     chip trên thanh — nơi người dùng nhiều khả năng muốn thêm cái kế bên. Tầng vẽ giữ trạng thái gấp/mở
         *     của phiên, nhưng giá trị **ban đầu** quyết ở đây để kiểm được off-car.
         */
        fun picks(cfg: TopStripConfig): List<ChipSection> {
            val all = choices()
            val byId = all.associateBy { it.id }
            // Theo ĐÚNG thứ tự chip trên thanh, không theo thứ tự bộ đăng ký: khối này là ảnh của thanh trên.
            val on = cfg.ids.mapNotNull { byId[it] ?: CapabilityCatalog.pick(it) }
            val onIds = on.mapTo(mutableSetOf()) { it.id }
            // [ĐO] ảnh máy ảo 2026-09-14 (T4): mặc định mở lĩnh vực đang có chip ⇒ với 3 chip sẵn là Năng lượng (28 ô)
            // + Khí hậu (11 ô) mở cùng lúc, trang dài quá trần R4 (≤2 màn cuộn). Nay MỌI lĩnh vực gấp sẵn; khối
            // "đang bật" luôn mở nên câu "thanh trên đang có gì" vẫn trả lời ngay; muốn thêm thì mở đúng lĩnh vực.
            return buildList {
                add(ChipSection(on = true, domain = null, picks = on, open = true))
                Domain.values().forEach { d ->
                    val rest = all.filter { it.domain == d && it.id !in onIds }
                    if (rest.isNotEmpty()) add(ChipSection(false, d, rest, open = false))
                }
                // Mục ĐỌC chưa xếp lĩnh vực: hôm nay là danh sách RỖNG (mọi datum đều khai [Domain], widget dựng tay
                // thì `curated` nên [choices] đã loại). Vẫn dựng khối cuối thay vì bỏ im lặng — thêm một mục đọc
                // không-lĩnh-vực về sau thì nó phải hiện ra ở đâu đó, chứ không biến mất khỏi màn chọn.
                val loose = all.filter { it.domain == null && it.id !in onIds }
                if (loose.isNotEmpty()) add(ChipSection(false, null, loose, open = true))
            }
        }

        /**
         * `"a,b,c"` → cấu hình. Chuỗi rỗng/lỗi ⇒ mặc định (không để thanh trên trắng vì một dòng prefs hỏng).
         *
         * [showLabels] là **khoá riêng** (`top_strip_labels`), không nằm trong chuỗi này — xem KDoc
         * [TopStripConfig.showLabels]. Nó đi vào qua tham số để nơi lưu bền chỉ đọc một lần rồi dựng một vật.
         *
         * ═══ [applyMigration] · [P1 · SOÁT Opus 2026-09-27] vì sao phép di trú phải có MỘT CÁI MỐC ═══════════════
         *
         * [migrate] là hàm thuần, và luật 2 của nó nhận ra *"mặc định CŨ"* bằng **đúng chuỗi** `chip_pm25,temp,
         * chip_energy`. Nhưng đó cũng đúng là chuỗi mà một người **vừa gỡ cả hai chip ghế** khỏi mặc định MỚI để lại
         * trên đĩa. Hai ý định, một chuỗi ⇒ **không một hàm thuần nào phân biệt được**, và vì `decode` chạy ở MỖI
         * lượt đọc thì luật 2 nổ lại mỗi lần: gỡ bao nhiêu lần chip cũng mọc lại đúng bấy nhiêu lần. KDoc [migrate]
         * đang hứa ngược lại (*"gỡ đi thì lần sau không mọc lại"*) — lời hứa ấy chỉ đúng khi gỡ **một** trong hai
         * (còn lại một mã ghế ⇒ luật 3), và `TopStripMigrationTest` không phủ ca gỡ cả hai.
         *
         * ⚠ Ghi **danh sách đã di trú** trở lại đĩa KHÔNG chữa được: sau lượt ghi, đĩa có 5 mã, người ta gỡ hai chip
         * ⇒ đĩa lại đúng 3 mã của mặc định cũ ⇒ vòng lặp y như trước. Thứ phân biệt được không nằm trong DỮ LIỆU mà
         * nằm ở **thời gian**: *"lượt di trú đã chạy cho hồ sơ này chưa"*. Nên chỗ lưu bền giữ một mốc riêng
         * (`WorkspacePrefs.K_STRIP_MIGRATED`) và truyền `applyMigration = false` từ lượt thứ hai trở đi — đúng khuôn
         * CLAUDE.md §5 (*"ghi marker vào prefs"*) và cùng khuôn `K_MIGRATED_SCENES` của P7.
         *
         * `false` ⇒ chỉ LỌC (mã đã xoá · trùng · quá [CAP]), không gộp, không nâng mặc định.
         */
        fun decode(s: String?, showLabels: Boolean = true, applyMigration: Boolean = true): TopStripConfig {
            val ids = s?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: return DEFAULT.copy(showLabels = showLabels)
            if (ids.isEmpty()) return DEFAULT.copy(showLabels = showLabels)
            // Lọc mã không còn đặt được (bản sau xoá một datum / người dùng sửa tay) — im lặng bỏ MỤC, không bỏ cả dòng.
            val kept = ids.filter { isChippable(it) }.distinct().take(CAP)
            if (kept.isEmpty()) return DEFAULT.copy(showLabels = showLabels)
            return TopStripConfig(if (applyMigration) migrate(kept).take(CAP) else kept, showLabels)
        }

        /**
         * ═══ UX5b · DI TRÚ danh sách chip ĐÃ LƯU (mọi hồ sơ) — thuần, gọi từ [decode] ═══════════════════════════
         *
         * Vì sao phải có: ai đã đặt **hai** chip ghế lẻ (`seat_heat_state` + `seat_vent_state`) từ trước UX5b thì
         * trên thanh có hai hình ghế đứng cạnh nhau, mà một trong hai gần như luôn chỉ để nói *"đang tắt"* — đúng
         * cái owner nhìn thấy trên máy ảo (*"sao còn ghế mát và ghế sưởi riêng"*). Chip gộp đã tồn tại từ UX5 nhưng
         * **không ai tự đổi** cho người đang dùng máy; đó là việc của phép di trú này.
         *
         * Ba luật, theo đúng thứ tự — và luật thứ ba là luật quan trọng nhất:
         *  1. **Có chip ghế LẺ (ghế lái)** ⇒ thay bằng **MỘT** [SEAT] tại **vị trí của cái đầu tiên** (thứ tự các
         *     chip khác không đổi). Đã có [SEAT] sẵn ở đâu đó ⇒ không nhân đôi.
         *  2. **Không có mã ghế nào** *và* danh sách **bằng đúng mặc định CŨ** ⇒ nâng lên [DEFAULT_IDS]. Chỉ dám làm
         *     khi khớp **tuyệt đối** vì đó là dấu duy nhất phân biệt *"chưa từng sửa gì"* với *"đã sửa và đang muốn
         *     đúng ba chip này"* — hai trạng thái ấy không phân biệt được trên đĩa (chuỗi lưu y hệt nhau), nên đây là
         *     một đánh đổi **có chủ ý**: ai cố tình giữ đúng ba chip cũ sẽ thấy hai chip ghế mọc thêm một lần, và gỡ
         *     đi thì lần sau không mọc lại nữa (danh sách lúc đó đã khác mặc định cũ ⇒ rơi vào luật 3).
         *  3. **Còn lại ⇒ KHÔNG ĐỤNG.** Danh sách chip là thứ người ta tự đặt; một phép "nâng cấp" tự ý xếp lại nó là
         *     đúng loại thay-đổi-không-ai-xin mà KDoc [DEFAULT_IDS] vừa nói tới.
         *
         * Tính chất: **luỹ đẳng** (chạy lại không đổi gì nữa — sau luật 1 danh sách đã có [SEAT] nên luật 2 không
         * bao giờ với tới), **giữ thứ tự**, **không trùng lặp**. Có bài kiểm cho cả ba.
         */
        fun migrate(ids: List<String>): List<String> {
            val singles = SEAT_PAIRS.getValue(SEAT).toList().toSet()   // hai chip LẺ của ghế lái
            if (ids.any { it in singles }) {
                val out = mutableListOf<String>()
                ids.forEach { id ->
                    val replacement = if (id in singles) SEAT else id
                    if (replacement !in out) out += replacement        // gộp hai lẻ (và một SEAT có sẵn) thành MỘT
                }
                return out
            }
            val seatIds = SEAT_PAIRS.keys + SEAT_PAIRS.values.flatMap { listOf(it.first, it.second) }
            if (ids.none { it in seatIds } && ids == LEGACY_DEFAULT_IDS) return DEFAULT_IDS
            return ids
        }

        fun encode(c: TopStripConfig): String = c.ids.joinToString(",")
    }
}
