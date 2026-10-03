package com.byd.clusternav.launcher.trip

import com.byd.clusternav.launcher.ShellAppLauncher
import com.byd.clusternav.launcher.behind.BehindHomeSequence

/**
 * ═══ L4 · D1 — KẾT QUẢ TỪNG BƯỚC của chuyến lên xe: một MÃ ngắn, bền, dịch được (thuần, `:core`) ═══════════════════
 *
 * Owner 03/10 (xe thật, 2.86): *"auto mở nhạc youtube không chạy? … Autostart app background không work"*. [ĐO máy ảo
 * `e2e/e6-no-app-slot`] chuyến mà MỌI bước đều không làm gì (`com.waze:bg-NO_STAGE | music:…:NO_STAGE`) vẫn ghi
 * `code=RAN` ⇒ Cài đặt hiện *"đã chạy lúc HH:mm"*; lý do chỉ nằm trong một chuỗi tự do bị cắt 160 ký tự + bỏ dấu `=`,
 * và chỉ màn Chẩn đoán (không có nút ở bản phát hành) đọc được. Lớp này thay chuỗi tự do bằng:
 *  - mỗi bước MỘT [TripStep] = gói · kiểu ([TripStepKind]) · mã ([TripStepCode]) — mã là tên enum ASCII, không cắt;
 *  - mã chuyến suy từ các bước ([tripCode]): mọi bước đạt ⇒ `RAN`; không bước nào làm được gì ⇒ `NOOP`; lẫn ⇒ `PARTIAL`.
 *
 * Mã hoá (khoá `kachi_trip_last`, trường `s=`): `pkg:K:CODE,pkg:K:CODE` — không `;`/`=` (dấu ngăn của sổ), gói qua
 * [ShellAppLauncher.PKG]. Đọc dễ dãi: mục lạ bị bỏ, không ném (đến từ đĩa, có thể do bản sau ghi).
 */
enum class TripStepKind(val tag: Char) {
    BACKGROUND('B'), NORMAL('N'), MUSIC('M');

    companion object {
        fun of(tag: Char): TripStepKind? = values().firstOrNull { it.tag == tag }
    }
}

/**
 * Mã một bước. [result]: [Result.OK] = đạt điều người dùng muốn (app chạy / đã mở / đang phát) · [Result.UNCONFIRMED] =
 * đã làm mà chưa thấy kết quả · [Result.NOOP] = không làm gì (kèm lý do). Mỗi mã có MỘT câu dịch ở `:app`
 * (`SettingsTripResult`) — thêm mã mới là phải thêm câu (bài canh `TripResultStringsContractTest`).
 */
enum class TripStepCode(val result: Result) {
    MOVED(Result.OK),               // chạy nền: đang sống sau màn nhà
    HOME_RESTORED(Result.OK),       // chạy nền: sống sau màn nhà, màn nhà bị che thoáng qua rồi được đưa lên lại (K12)
    ALREADY_RUNNING(Result.OK),     // đã có task + tiến trình (L4 · D4)
    KEPT_UNDER(Result.OK),          // sống, ẩn dưới app của ô / trên màn ảo dàn dựng (O1) — không che màn nhà
    IN_SLOT(Result.OK),             // app nằm trong một ô đang hiện — ô tự mở nó
    OPENED(Result.OK),              // mở bình thường (K10)
    PLAYING(Result.OK),             // nhạc: đọc lại thấy phiên của app ĐANG PHÁT
    SELF_PLAYING(Result.OK),        // nhạc: app chọn đã đang phát sẵn
    SENT(Result.UNCONFIRMED),       // nhạc: đã giao lệnh/link, chưa thấy phát trong hạn
    TIMEOUT(Result.UNCONFIRMED),    // chuỗi chạy ngầm chưa xong trong hạn chờ
    NO_STAGE(Result.NOOP),          // không có chỗ dàn dựng (không ô sống, màn ảo ẩn không tạo được)
    NO_CHANNEL(Result.NOOP),        // kênh shell chưa dùng được
    DISABLED(Result.NOOP),          // BEHIND-HOME tự tắt trong tiến trình (ROM bỏ qua khoá giữ chỗ — R0.5a)
    SYSTEM_APP(Result.NOOP),        // app hệ thống: không chạy nền (R0.6)
    NOT_INSTALLED(Result.NOOP),
    SELF(Result.NOOP),
    CAMERA_UNKNOWN(Result.NOOP),    // đời xe chưa biết màn camera ⇒ không mở lên trước
    CAMERA(Result.NOOP),            // màn camera đang hiện suốt hạn 60 s
    OTHER_FRONT(Result.NOOP),       // app khác đang ở trước ⇒ không giành màn hình
    NOT_STAGED(Result.NOOP),        // không phân giải được / mở không lên
    NO_SESSION(Result.NOOP),        // nhạc: đã mở nhưng app không có phiên nhạc để phát
    UNKNOWN_MEDIA(Result.NOOP),     // nhạc: Kachi không đọc được phiên (thiếu quyền truy cập thông báo)
    DEADLINE(Result.NOOP);          // hết hạn chuyến trước bước này

    enum class Result { OK, UNCONFIRMED, NOOP }

    companion object {
        fun of(name: String): TripStepCode? = values().firstOrNull { it.name == name }
    }
}

/** Một bước của chuyến: gói (với nhạc: gói app nhạc, chưa cài ⇒ mã kiểu như `youtube`), kiểu, mã. */
data class TripStep(val pkg: String, val kind: TripStepKind, val code: TripStepCode)

object TripOutcome {

    /** Trần số bước ghi bền: tối đa 6 app + 1 nhạc, dư một chỗ. */
    const val MAX_STEPS = 8

    fun encode(steps: List<TripStep>): String =
        steps.filter { it.pkg.matches(ShellAppLauncher.PKG) }.take(MAX_STEPS).joinToString(",") { "${it.pkg}:${it.kind.tag}:${it.code.name}" }

    fun decode(raw: String?): List<TripStep> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',').mapNotNull { item ->
            val p = item.trim().split(':')
            if (p.size != 3 || p[1].length != 1 || !p[0].matches(ShellAppLauncher.PKG)) return@mapNotNull null
            val kind = TripStepKind.of(p[1][0]) ?: return@mapNotNull null
            val code = TripStepCode.of(p[2]) ?: return@mapNotNull null
            TripStep(p[0], kind, code)
        }.take(MAX_STEPS)
    }

    /**
     * Mã chuyến từ các bước: không bước nào ⇒ `NOOP` (hết hạn trước bước đầu cũng là "không làm gì"); mọi bước [TripStepCode.Result.OK]
     * ⇒ `RAN`; không bước nào OK và không bước nào [TripStepCode.Result.UNCONFIRMED] ⇒ `NOOP`; còn lại ⇒ `PARTIAL`.
     */
    fun tripCode(steps: List<TripStep>): TripGate.Code {
        if (steps.isEmpty()) return TripGate.Code.NOOP
        val ok = steps.count { it.code.result == TripStepCode.Result.OK }
        val unsure = steps.count { it.code.result == TripStepCode.Result.UNCONFIRMED }
        return when {
            ok == steps.size -> TripGate.Code.RAN
            ok == 0 && unsure == 0 -> TripGate.Code.NOOP
            else -> TripGate.Code.PARTIAL
        }
    }

    /** Kết quả chuỗi chạy ngầm ⇒ mã bước. `null` = không có kết quả (bên thi hành không nhận việc) ⇒ [TripStepCode.NO_STAGE]. */
    fun ofBehind(r: BehindHomeSequence.Result?): TripStepCode = when (r) {
        null, BehindHomeSequence.Result.NO_STAGE -> TripStepCode.NO_STAGE
        BehindHomeSequence.Result.MOVED -> TripStepCode.MOVED
        BehindHomeSequence.Result.MOVED_HOME_RESTORED, BehindHomeSequence.Result.X_FRONT_HOME_RESTORED,
        BehindHomeSequence.Result.ANCHOR_IN_FRONT -> TripStepCode.HOME_RESTORED
        BehindHomeSequence.Result.KEPT_UNDER, BehindHomeSequence.Result.B_NOT_IN_SLOT -> TripStepCode.KEPT_UNDER
        BehindHomeSequence.Result.ALREADY_RUNNING -> TripStepCode.ALREADY_RUNNING
        BehindHomeSequence.Result.X_NOT_STAGED -> TripStepCode.NOT_STAGED
        BehindHomeSequence.Result.SYSTEM_APP -> TripStepCode.SYSTEM_APP
        BehindHomeSequence.Result.NO_CHANNEL -> TripStepCode.NO_CHANNEL
        BehindHomeSequence.Result.DISABLED -> TripStepCode.DISABLED
        BehindHomeSequence.Result.TIMEOUT -> TripStepCode.TIMEOUT
    }

    /** Loại trừ lúc lập kế hoạch ⇒ mã bước. */
    fun ofSkip(w: TripPlan.Why): TripStepCode = when (w) {
        TripPlan.Why.SELF -> TripStepCode.SELF
        TripPlan.Why.NOT_INSTALLED -> TripStepCode.NOT_INSTALLED
        TripPlan.Why.SYSTEM_APP -> TripStepCode.SYSTEM_APP
        TripPlan.Why.IN_SLOT -> TripStepCode.IN_SLOT
        TripPlan.Why.CAMERA_UNKNOWN -> TripStepCode.CAMERA_UNKNOWN
    }

    /** Cổng nhạc ⇒ mã bước (chỉ các cổng KHÔNG đi tiếp; `GO` không có mã). */
    fun ofGate(g: TripMusicPlan.Gate): TripStepCode? = when (g) {
        TripMusicPlan.Gate.OFF, TripMusicPlan.Gate.GO -> null
        TripMusicPlan.Gate.NOT_INSTALLED -> TripStepCode.NOT_INSTALLED
        TripMusicPlan.Gate.UNKNOWN_MEDIA -> TripStepCode.UNKNOWN_MEDIA
        TripMusicPlan.Gate.SELF_PLAYING -> TripStepCode.SELF_PLAYING
    }
}
