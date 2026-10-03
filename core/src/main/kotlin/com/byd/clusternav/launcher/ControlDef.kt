package com.byd.clusternav.launcher

/**
 * ═══ HỢP ĐỒNG **MỘT DÒNG NÚT** — tách khỏi `ControlRegistry.kt` ngày 2026-09-16 (H1 · T2) ═══════════════════
 *
 * Tách vì trần 500 dòng (CLAUDE.md §4.1) và tách theo **vai**, không theo số dòng: tệp kia là **BẢNG DỮ LIỆU** (54
 * dòng nút + [DockConfig] + phép tra), còn ở đây là **hợp đồng** của một dòng ấy — mỗi trường kèm bằng chứng vì sao
 * nó tồn tại. Hai vai đọc vào hai lúc khác nhau: thêm một nút thì mở bảng; hỏi *"trường này nghĩa là gì"* thì mở đây.
 *
 * ⚠ Không đổi một dòng hành vi nào lúc tách: cùng package, cùng tên, cùng chữ ký, cùng thứ tự tham số.
 */
/**
 * Catalog 1 nút điều khiển. Hành động THẬT bơm qua [CarControlPort] (BydHal on-car; [NoCar] off-car no-op).
 * Nhãn + ngữ nghĩa map theo catalog §B (`docs/diagnostics/kachi-capability-catalog-2026-09-10.md`).
 *
 * MỞ RỘNG W1a (giữ tương thích ngược — 4 field đầu + `enabledByDefault/onByDefault/value/min/max/step` KHÔNG đổi,
 * field mới đặt SAU + có default nên mọi call-site + test cũ còn nguyên):
 * @property domain nhóm panel ([Domain]) — CHỈ gom nhóm, KHÔNG gate an toàn (owner bỏ gate 2026-09-10).
 * @property tier mức bằng chứng GHI (write) — UI badge/mờ theo đây.
 * @property bindingKey khoá map HAL cho Stage 2 (named-method `"Device.method"` hoặc feature-id decimal `"501219340"`).
 * @property args tham số phụ: với [ControlKind.SELECT] = danh sách nhãn lựa chọn; kiểu khác thường rỗng.
 */
data class ControlDef(
    val id: String,
    override val label: String,
    val icon: String,
    val kind: ControlKind,
    val enabledByDefault: Boolean = false,
    val onByDefault: Boolean = false,   // cho TOGGLE
    val value: Int = 0,                 // mặc định cho STEP
    val min: Int = 0,
    val max: Int = 0,
    val step: Int = 1,
    val domain: Domain = Domain.CLIMATE,
    val tier: EvidenceTier = EvidenceTier.OVERDRIVE,
    val bindingKey: String = "",
    /**
     * Ghi ĐÈ thiết bị BYDAuto cho đường **feature-id** — tên lớp đơn giản (vd `"BYDAutoSettingDevice"`,
     * `"BYDAutoPM2p5Device"`). `null` ⇒ chọn thiết bị theo [domain] như cũ ([HalBindingTable.featureDeviceFqn]).
     *
     * ## Vì sao là DỮ LIỆU ở đây chứ không phải `if (id == …)` trong bảng nối (CLAUDE.md §7)
     * [ĐO] RE `docs/diagnostics/byd-hal-permission-RE-2026-09-14.md` §4: feature-id của BYD **nằm rải trên các
     * thiết bị KHÔNG khớp Domain của UI** — đèn đọc/nhớ-ghế/giới-hạn-sạc thuộc **SETTING (1023)**, ion thuộc
     * **PM2P5 (1008)** — nên map thô Domain→thiết bị route sai, `checkDeviceFeatures` trả sentinel (KHÔNG phải
     * cổng chữ ký). Khác biệt id↔thiết bị là **dữ liệu tra được từ `BYDAutoDeviceFeaturesMap`**, nên nó phải sống
     * như một trường của control, không phải một nhánh rẽ theo tên trong code định tuyến. Còn ở mức **cần kiểm
     * trên xe** (playbook HAL-sweep) — cổng chữ ký của thiết bị đích với các miền đã đo (AC/SETTING/PM2P5) tin cậy
     * cao, nhưng lệnh có thật sự tới xe hay không thì sweep mai xác nhận.
     */
    val halDevice: String? = null,
    val args: List<String> = emptyList(),
    /** Nhãn tiếng Anh (U5 · T2) — tham số mặc định, xem KDoc [Strings] về vì sao nhãn là DỮ LIỆU ở `:core`. */
    override val labelEn: String? = null,
    /**
     * [args] bằng tiếng Anh — **cùng thứ tự, cùng số phần tử** với [args].
     *
     * Vì sao phải có: [args] không phải chú thích, nó là **chữ hiện trên nút** (`ControlTileLogic.selectLabel`, và ô
     * đóng/mở của bộ dựng ô ở `:app`). Bỏ qua nó thì màn tiếng Anh vẫn có nút ghi *"Đóng"* / *"Xanh dương"* — nhãn
     * chính đã dịch mà lựa chọn bên trong thì không, tức nửa vời theo cách người dùng thấy ngay.
     *
     * Rỗng ⇒ [displayArgs] lùi về [args]. **Lệch số phần tử** cũng lùi về [args] cho CẢ danh sách: một danh sách
     * trộn hai thứ tiếng còn tệ hơn một danh sách nhất quán tiếng Việt, và `LangCoverageTest` đếm khớp nên ca đó
     * đỏ off-car.
     */
    val argsEn: List<String> = emptyList(),
    /**
     * Nhãn NGẮN cho bề mặt HẸP — **cùng khuôn** [TelemetrySpec.short] (tham số mặc định, chỉ điền chỗ thật cần).
     *
     * ## [ĐO] bệnh nó chữa — ảnh máy ảo 2026-09-12
     * Hàng nút của ô nhóm chia bề ngang cho tối đa 6 ô, nên ở khung 4/12 màn mỗi ô còn **82px** (≈70px dùng được).
     * Nhãn đầy bị cắt ở CẢ hai thứ tiếng: `"Window front-ri…"` và `"Kính trước-tr…"` / `"Kính trước-p…"` — hai ô kính
     * trước vì thế đọc ra **gần như y hệt nhau**, đúng họ lỗi 18-nhãn-trùng mà `TelemetrySpec.short` đã sinh ra để
     * chữa cho ô ĐỌC. Ô BẤM thì tới nay chưa có bản ngắn nào.
     *
     * `null` ⇒ [shortLabel] lùi về [label]; phép lùi và thứ tự bậc giống hệt [TelemetrySpec] để hai bộ đăng ký không
     * có hai luật khác nhau cho cùng một việc.
     */
    val short: String? = null,
    /** Nhãn NGẮN tiếng Anh. `null` ⇒ [shortLabelIn] lùi về [labelEn] rồi tới [label]. */
    val shortEn: String? = null,
    /**
     * ═══ H1 · **MÃ DATUM** đọc trạng thái thật của nút — KHÔNG phải [bindingKey] ══════════════════════════
     *
     * Trỏ vào một mã của [TelemetryRegistry] (spec `docs/specs/kachi-live-state-ux.html` §4.3 · T2). Rỗng = *"chưa có
     * đường đọc"* ⇒ [HalBindingTable.readState] trả `null`, chỗ gọi giữ NGUYÊN hành vi 1.68 (lùi về mức trong RAM) —
     * **không đoán một con số**. Là mã datum chứ không phải chuỗi `"Device.getter"` vì getter của xe phải khai đúng
     * **MỘT chỗ** ([TelemetryRegistry]) — cho nút mang chuỗi riêng là mọc bảng getter thứ hai (CLAUDE.md §4.1).
     *
     * ## [ĐO] bệnh — tester 1.66 *"điều hoà chỉnh lung tung, quất một phát như lò heo quay"*
     * *"Tăng gió"* quy về tuyệt đối bằng `ControlTileState.value(def)` = **mặc định RAM** (gió 4 · nhiệt 22) chứ không
     * phải mức thật ⇒ [ĐO xe 2026-09-16] xe đang **gió 1** mà nói *"tăng gió"* thì lệnh bắn đi là **5**. Mà đường đọc
     * cũ lại đi qua chính [bindingKey] — một khoá **GHI** (`501219340` = AC_WIND_LEVEL_SET · `setAcTemperature`) ⇒ đọc
     * qua setter chỉ ra rác/null; lỗi thứ hai làm lỗi thứ nhất không thể tự chữa.
     *
     * ⚠ Sáu mã của lượt T2 (`seatc` `seath` `defrost` `defrost_rear` `ac_auto` `vol`) **đã nối** ngày 2026-09-16,
     * mỗi cái dựa trên một phép đo thật trên xe; ba mã `readl` `sunshade` `windows_all` vẫn CỐ Ý để rỗng vì chúng
     * còn ở mức [CHƯA BIẾT]/[SUY] trong spec §4.5 — lý do + bằng chứng ở KDoc `ControlReadKeyTest`.
     */
    val readKey: String = "",
    /**
     * GHI ĐÈ tham số int của getter khi nút cần **vùng khác** với datum (`seatID` ghế · `area` nhiệt/sấy kính); `null` ⇒
     * dùng tham số của chính datum ([HalBindingTable.readArg]) — một con số, một chỗ. [ĐO] ca duy nhất đang cần:
     * `temp → inside_temp`, mà `inside_temp` đọc `getTemprature(0)` trong khi **0 không phải area hợp lệ**
     * (`ac/BYDAutoAcDevice.java:689` trả thẳng `-2147482645` = [HalBindingTable.SENTINEL_INVALID] cho area ngoài 1..4)
     * ⇒ nút `temp` ghi đè **1** = `AC_TEMP_MAIN` (`:681`, ghế lái). [CHƯA BIẾT] trim này có provision area 1 không
     * (spec V-oncar-3); đọc nhầm vùng chỉ ra một con số khác, KHÔNG bắn lệnh nào.
     */
    val readArg: Int? = null,
    /**
     * Số đọc mang nghĩa **NGƯỢC** với ô bật/tắt (0 = đang BẬT) — **DỮ LIỆU của dòng này**, không phải
     * `if (id == "ac_auto")` trong bảng nối (CLAUDE.md §7). Ca đã biết: `ac_auto` đọc `getAcControlMode()` mà
     * `AC_CTRLMODE_AUTO = 0`/`_MANUAL = 1` (`ac/BYDAutoAcDevice.java:29-30`; [ĐO xe 2026-09-16] ra **0** = AUTO bật) —
     * chưa bật được vì [readKey] nút đó còn chờ datum (⚠ trên), và **đường GHI đứng yên** (feature `1324355606` không
     * có trong `BYDAutoFeatureIds` của xe này ⇒ *"cấm bắn lệnh khí hậu theo phỏng đoán"* còn nguyên).
     */
    val readInverted: Boolean = false,
    /**
     * ═══ UX4 · Mã nút **TOGGLE** nói trạng thái **TỰ ĐỘNG** của cùng thiết bị này ═══════════════════════════
     *
     * Rỗng = *"nút này không có mặt tự động"* ⇒ mọi thứ y như trước UX4. Khai một chữ (`fan → "ac_auto"`) là nấc
     * dưới cùng của thang đổi tên thành AUTO: `−` ở mức `min + 1` **bật auto** thay vì ghi `min`, và ô hiện chữ
     * AUTO. Luật + bằng chứng: [ClimateAuto].
     *
     * ## Vì sao là DỮ LIỆU của dòng, không phải `if (id == "fan")` trong bộ dựng ô (CLAUDE.md §7)
     * Y hệt tiền lệ [readInverted]/[halDevice]: cặp *"thang mức + công tắc tự động của cùng thiết bị"* là một
     * **quan hệ tra được** (`AC_WIND_LEVEL_SET` ↔ `AC_CTRL_MODE_SET` cùng `BYDAutoAcDevice`), và nó sẽ còn xuất
     * hiện ở nơi khác (gió hàng sau, quạt ghế). Khai bằng trường thì nút thứ hai chỉ tốn một chữ và **không dòng
     * mã nào** phải sửa; khai bằng nhánh rẽ theo tên thì nút thứ hai lặng lẽ không có tính năng.
     *
     * ⚠ [ĐO xe 2026-09-20] Mã trỏ tới phải là một nút có đường GHI đã chứng minh (`ac_auto` ⇒ `AC_CTRL_MODE_SET`,
     * rc=0 hai chiều) **và** một [readKey] để ô biết xe đang ở chế độ nào — không có đường đọc thì [ClimateAuto]
     * trả `null` và ô **không bao giờ** dám nói AUTO (đúng, nhưng tính năng câm).
     */
    val autoId: String = "",
    /**
     * ═══ FIX286 · SR2 — lệnh **NHẢ** gửi sau mỗi lệnh ghi hợp lệ (mặc định `null` = không nhả, y như mọi bản trước) ═══
     *
     * [ĐO nguồn OEM, đọc lại 02/10] app Cài đặt BYD điều khiển nóc theo đúng một nhịp: `removeMessages(0)` → ghi →
     * `sendEmptyMessageDelayed(0, 200)` → ghi **255** (`SunRoofFragment.java:834-836,858-860,1109`). Tức tín hiệu nóc
     * là một **yêu cầu được giữ trên bus** cho tới khi bên gửi nhả — bắt buộc hay không: [SUY] (OEM luôn làm),
     * chưa đo trên xe. Khai ở đây là DỮ LIỆU của dòng (CLAUDE.md §7, cùng khuôn [readInverted]/[halDevice]):
     * `HalBindingTable.write` thi hành cho MỌI bề mặt (ô · giọng nói · gói lệnh · cầu `ctl`), nút không khai thì
     * không đổi một lượt gọi nào.
     */
    val release: WriteRelease? = null,
    /**
     * ═══ FIX286 · SR4 — cổng *"xe này CÓ bộ phận này không"* theo **đo đạc**, không theo tên gói/đời xe ═══
     *
     * `null` (mặc định) ⇒ không cổng nào, y như trước. Khai ⇒ trước lệnh GHI, đọc getter cấu hình (nói **CÓ** thì cất
     * MỘT lần mỗi tiến trình; nói "vắng" thì lệnh sau đọc lại — `ControlWriteFlow.presenceAbsent`); giá trị nằm trong
     * [Presence.absentWhen] ⇒ tầng ghi **từ chối** (0 lượt gọi HAL) và nút được báo
     * *"không có trên xe này"* qua đúng đường `featureAbsentOnCar` → `wiredOnThisCar` đã có. Đọc hỏng / sentinel /
     * số lạ ⇒ **cho qua** (fail-open = hành vi hôm nay) — cổng chỉ được nói *"không có"* khi xe tự nói thế.
     */
    val presence: Presence? = null,
    /**
     * FIX286 · SR6 — getter **chẩn đoán** chụp thành MỘT dòng `ctl-probe` ở lượt ghi ĐẦU TIÊN của nút này trong mỗi
     * tiến trình (nhật ký bền `ctl-writes.log`). Rỗng (mặc định) ⇒ không chụp gì. Đây là chỗ cho những getter mà
     * một lượt thử hiện trường cần để **chốt nghĩa** (enum chưa rõ, cấu hình xe) — không phải đường đọc của ô.
     */
    val probe: List<HalProbe> = emptyList(),
) : Localized {
    fun clamp(v: Int): Int = if (kind == ControlKind.STEP) v.coerceIn(min, max) else v

    /** Nhãn ngắn tiếng Việt — luôn có giá trị (lùi về [label] khi chưa khai [short]). */
    val shortLabel: String get() = short ?: label

    /** Nhãn ngắn theo [Strings.current] — dùng ở hàng nút của ô nhóm. */
    val displayShortLabel: String get() = shortLabelIn(Strings.current)

    /** Bậc lùi: [shortEn] → [labelEn] → [short] → [label]. Xem KDoc [TelemetrySpec.shortLabelIn] về lý do. */
    fun shortLabelIn(lang: Lang): String =
        Strings.pick(shortLabel, shortEn?.takeIf { it.isNotBlank() } ?: labelEn, lang)

    /** [args] theo [Strings.current] — xem [argsEn] về luật lùi. */
    val displayArgs: List<String> get() = argsIn(Strings.current)

    /**
     * [args] theo một ngôn ngữ CỤ THỂ (phép đọc thuần, cho test).
     *
     * [argsEn] lệch số phần tử ⇒ CẢ danh sách tiếng Việt (luật cũ của EN, áp cho mọi tiếng không phải VI). ZH/TH/MS
     * dịch TỪNG phần tử theo cặp song song `(args[i], argsEn[i])` — từ đồng hình như `"Open"` khoá theo cặp nên lệnh
     * Mở ở đây không lẫn với trạng thái Đang mở ở `TelemetryReadout`.
     */
    fun argsIn(lang: Lang): List<String> = when {
        lang == Lang.VI || argsEn.size != args.size || argsEn.isEmpty() -> args
        lang == Lang.EN -> argsEn
        else -> args.indices.map { Strings.pick(args[it], argsEn[it], lang) }
    }
}

/**
 * FIX286 · SR4 — cổng có-mặt của một nút ([ControlDef.presence]).
 *
 * @property getter khoá getter dạng `bindingKey` named-method (`"BYDAutoBodyworkDevice.getMoonRoofConfig"`), 0 tham
 *   số — cùng công thức FQN của [HalBindingTable.routeOf]; khoá không phân giải ra named-method ⇒ cổng câm (fail-open).
 * @property absentWhen giá trị cấu hình nói *"xe này KHÔNG có bộ phận"*. Mọi giá trị khác, `null`, sentinel ⇒ có/không biết.
 */
data class Presence(val getter: String, val absentWhen: Set<Int>)

/**
 * FIX286 · SR6 — một getter chẩn đoán ([ControlDef.probe]): khoá named-method + tham số int (`null` = 0 tham số).
 * Nhãn trong dòng nhật ký = tên method (kèm `(arg)` nếu có), để grep thẳng theo tên hàm của stub BYD.
 */
data class HalProbe(val getter: String, val arg: Int? = null) {
    /** Nhãn trong dòng `ctl-probe`: `getWindowOpenPercent(5)` · `getMoonRoofConfig`. */
    val label: String get() = getter.substringAfter('.') + (arg?.let { "($it)" } ?: "")
}
