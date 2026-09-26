package com.byd.clusternav.launcher

/**
 * ═══ UX4 · NẤC DƯỚI CÙNG CỦA GIÓ TÊN LÀ **AUTO**, KHÔNG PHẢI **0** — luật THUẦN ═══════════════════════════════
 *
 * Spec `docs/specs/kachi-274-ux-voice-camera.html` §3 R4 · §4.2. Owner 2026-09-26: *"ô chỉnh gió: bấm − từ mức 1 ⇒
 * AUTO; bấm + từ AUTO ⇒ mức; datum gió trên thanh trên: đang AUTO ⇒ AUTO n"*.
 *
 * ## Bệnh — [ĐO xe 2026-09-20] một nút NÓI DỐI
 * `docs/diagnostics/oncar-1.84-session-2026-09-20.md:41`: *"⚠ KHÔNG phải `AC_WIND_MODE_SET` (=hướng gió) hay
 * `AC_WIND_LEVEL_SET=0` (**bị bỏ qua**)"*. Nút `fan` khai `min = 0` nên người lái bấm `−` tới 0 được, ô hiện chữ
 * `"0"` — mà **xe vẫn thổi**, vì lệnh mức 0 không có tác dụng. Đây không phải một tính năng trang trí: nó là một
 * nút hỏng, đúng họ lỗi mà `ControlVisuals` dựng ra để dẹp (*"hai nửa của cùng một ô nói hai điều"*).
 *
 * Thứ xe THẬT SỰ có ở đáy thang là **gió tự động**: `AC_CTRL_MODE_SET` (=0 ⇒ AUTO · =1 ⇒ tay), đường ghi đã
 * [ĐO xe 2026-09-20] rc=0 hai chiều, owner đối chiếu màn AC. Nên `−` ở mức 1 phải **bật auto**, không phải ghi 0.
 *
 * ## Vì sao một tệp THUẦN riêng, không nhét vào bộ dựng ô
 * Cùng lẽ [ControlVisuals]: `:core` nói *"cú bấm này nghĩa là gì"*, `:app` chỉ thi hành. Và luật này có **hai** chỗ
 * đọc (ô nút + chip thanh trên qua [TelemetryReadout]) nên phép quy đổi phải sống đúng MỘT bản — bằng không nó là
 * bản sao thứ ba của một phép đảo mà dự án đã đảo sai một lần (xem ⚠⚠ dưới).
 *
 * ## ⚠⚠ HAI CỬA cho *"đang AUTO không"* — và vì sao KHÔNG được gộp
 * Hai bề mặt đọc cùng một getter nhưng **giá trị tới tay đã khác nhau**:
 *  • **datum** ([CarStatus.Climate.acWindAutoRaw]) giữ **số THÔ của khung**: `AC_WINDLEVEL_MANUAL_SIGN_OFF = 0`
 *    ⇒ `0 = AUTO` (`jadx-tmap/.../ac/BYDAutoAcDevice.java:302` + javadoc BYD, xem [TelemetryRegistry] `ac_wind_auto`);
 *  • **nút** ([CarStatus.controls]) đi qua [applyInverted] vì `ac_auto` khai [ControlDef.readInverted] ⇒ đã quy về
 *    ước chung **`1 = đang bật`**.
 * Đưa số của nút vào cửa của datum là **đảo lần thứ hai** ⇒ ô hiện AUTO đúng lúc xe đang chỉnh tay, rc vẫn 0, im
 * lặng. [CarStatus] đã viết sẵn cảnh báo cho đúng cái bẫy này. Vì thế ở đây là [autoOnFromRaw] và
 * [autoOnFromControl], hai tên khác nhau, và `ClimateAutoTest` khoá đúng sự khác nhau ấy.
 *
 * ## ⚠ Mức bằng chứng — nói đúng cái đã đo
 *  • **[ĐO]** ghi mức 0 bị xe bỏ qua · đường ghi auto (`AC_CTRL_MODE_SET`) chạy thật · xe vẫn báo mức gió khi đang
 *    AUTO (`hal-reads.txt` `getAcControlMode()=0` cùng lượt với `getAcWindLevel()=1`).
 *  • **[SUY]** việc chữ AUTO **hiện ra được**: datum `ac_wind_auto` = `getAcWindLevelManualSign` tới nay **chưa có
 *    một lượt `hal get` nào** trong `docs/diagnostics/`. Vì thế [autoOnFromRaw] trả `null` khi chưa đọc, và
 *    [fanText] **không bao giờ** nói AUTO dựa trên `null`: ca xấu nhất là *im lặng như hôm nay*, không phải *nói sai*.
 *    Bất biến ấy có bài canh riêng — đừng "dọn" nó thành `?: false`.
 */
object ClimateAuto {

    /** Chữ hiện ra khi đang tự động. Không dịch: owner + màn AC gốc của xe đều dùng đúng bốn chữ này. */
    const val AUTO = "AUTO"

    /**
     * Số **THÔ của khung** → *"đang AUTO không"*. `AC_WINDLEVEL_MANUAL_SIGN_OFF = 0` ⇒ `0 = AUTO`.
     *
     * `null` đi thẳng qua thành `null` = *"chưa biết"*, **không** phải *"đang chỉnh tay"* — xem ⚠ ở KDoc lớp.
     */
    fun autoOnFromRaw(raw: Int?): Boolean? = raw?.let { it == 0 }

    /**
     * Số của **NÚT** ([CarStatus.controls], đã qua [applyInverted]) → *"đang AUTO không"*. Ước chung `1 = đang bật`.
     *
     * Tách khỏi [autoOnFromRaw] là **cố ý** — xem ⚠⚠ ở KDoc lớp (đảo hai lần ⇒ ô nói ngược).
     */
    fun autoOnFromControl(value: Int?): Boolean? = value?.let { it > 0 }

    /**
     * Chữ giá trị của datum mức gió cho bề mặt RỘNG (chip thanh trên · ô đọc): `"AUTO 1"` · `"AUTO"` · `"1"` · `null`.
     *
     * [level] là mức xe đang thổi (`getAcWindLevel`), [autoRaw] là số thô của chỉ báo auto. Đang AUTO mà vẫn đọc được
     * mức ⇒ `"AUTO 1"`: [ĐO xe 2026-09-16] hai getter độc lập, xe báo mức gió **ngay khi** đang AUTO, nên con số ấy
     * là thật, không phải bịa.
     */
    fun fanText(level: Int?, autoRaw: Int?): String? = when {
        autoOnFromRaw(autoRaw) != true -> level?.toString()
        level != null -> "$AUTO $level"
        else -> AUTO
    }

    /** Điều một cú bấm `−`/`+` trên ô stepper THẬT SỰ phải làm. Xem [stepIntent] về bảng quyết định. */
    sealed class StepIntent {
        /** Ghi mức tuyệt đối như trước UX4 (đường đã chạy hiện trường — CLAUDE.md §6, không đổi một dòng). */
        data class SetLevel(val level: Int) : StepIntent()

        /** Đáy thang: **bật auto**, tuyệt đối KHÔNG ghi mức 0 ([ĐO] lệnh đó bị xe bỏ qua). */
        object EnableAuto : StepIntent()

        /** Rời auto rồi đặt [level] (hai lệnh, đúng thứ tự — thi hành ở tầng ô). */
        data class LeaveAuto(val level: Int) : StepIntent()

        /** Đã ở đáy và xe không có nấc tắt qua mã này ⇒ không bắn gì. Giả vờ có là nói dối. */
        object NoOp : StepIntent()
    }

    /**
     * [StepIntent] ở dạng **thi hành được**: ba câu trả lời mà tầng ô cần, không nhánh nào.
     *
     * Vì sao có cả hai dạng: [StepIntent] là **bảng quyết định** (đọc ra là hiểu owner xin gì, và test khoá từng
     * ô của bảng), còn đây là **hình dạng để chạy**. Nhờ nó, ô nút có **đúng MỘT** chỗ gọi `toggle(` và **đúng MỘT**
     * chỗ gọi `step(` — bất biến mà `ControlTileOffMainWiringContractTest` canh (*"cú ghi đúng một cửa"*), và là
     * thứ giữ cho một lượt sửa sau không mọc ra đường ghi thứ hai đi vòng qua làn tuần tự.
     *
     * Không có nguy cơ hai-bản-sao: [stepPlan] **suy ra** từ [stepIntent], không tự quyết gì.
     *
     * @property auto `true`/`false` = ghi công tắc tự động; `null` = **không đụng tới nó**.
     * @property level mức cần ghi xuống xe; `null` = không ghi mức nào.
     * @property shown mức mà ô phải HIỆN sau cú bấm (AUTO giữ nguyên mức đang thổi — xe vẫn thổi, chỉ là tự chọn).
     * @property act `false` = cú bấm không làm gì cả ⇒ ô **không** được đổi chữ/màu, và không có gì để hoàn nguyên.
     */
    data class StepPlan(val auto: Boolean?, val level: Int?, val shown: Int, val act: Boolean)

    /** [stepIntent] ở dạng thi hành — xem [StepPlan]. */
    fun stepPlan(def: ControlDef, current: Int, delta: Int, autoOn: Boolean?): StepPlan =
        planOf(current, stepIntent(def, current, delta, autoOn))

    /** Phép suy [StepIntent] → [StepPlan] (thuần, tách ra để test đối chiếu được từng ô của bảng quyết định). */
    fun planOf(current: Int, intent: StepIntent): StepPlan = when (intent) {
        is StepIntent.SetLevel -> StepPlan(auto = null, level = intent.level, shown = intent.level, act = true)
        StepIntent.EnableAuto -> StepPlan(auto = true, level = null, shown = current, act = true)
        is StepIntent.LeaveAuto -> StepPlan(auto = false, level = intent.level, shown = intent.level, act = true)
        StepIntent.NoOp -> StepPlan(auto = null, level = null, shown = current, act = false)
    }

    /**
     * Cú bấm `delta` trên [def] khi mức thật là [current] và trạng thái auto là [autoOn] (`null` = chưa biết).
     *
     * | đang | bấm | ra | vì sao |
     * |---|---|---|---|
     * | tay, đích > `min` | `−`/`+` | [StepIntent.SetLevel] | đường cũ, đã chạy hiện trường |
     * | tay, đích = `min` | `−` | [StepIntent.EnableAuto] | [ĐO] ghi `min` (=0) bị xe bỏ qua ⇒ nấc ấy là AUTO |
     * | AUTO | `+` | [StepIntent.LeaveAuto] | tắt auto rồi đặt mức đang thổi + 1 (kẹp ≤ `max`) |
     * | AUTO | `−` | [StepIntent.NoOp] | đã ở đáy; mã này không có nấc TẮT |
     * | chưa biết | `−` về `min` | [StepIntent.EnableAuto] | ghi `min` chắc chắn no-op, thử auto còn có cơ hội đúng |
     *
     * **Generic bằng DỮ LIỆU** (CLAUDE.md §7): nút nào khai [ControlDef.autoId] thì tự có bảng này; nút không khai
     * (nhiệt độ, âm lượng) đi đúng nhánh `SetLevel` như trước — không một `if (def.id == "fan")` nào.
     */
    fun stepIntent(def: ControlDef, current: Int, delta: Int, autoOn: Boolean?): StepIntent {
        val target = def.clamp(current + delta)
        if (def.autoId.isBlank()) return StepIntent.SetLevel(target)
        if (autoOn == true) {
            // Rời auto: mức xe đang thổi + delta, và không bao giờ về `min` (mức đó không tồn tại thật).
            return if (delta <= 0) StepIntent.NoOp
            else StepIntent.LeaveAuto(def.clamp(target.coerceAtLeast(def.min + 1)))
        }
        return if (delta < 0 && target <= def.min) StepIntent.EnableAuto else StepIntent.SetLevel(target)
    }
}
