package com.byd.clusternav.launcher

/**
 * ═══ 3.1.7 · STALE-AUDIT — lượt ghi KHÔNG đi qua ô (phím · giọng nói) để lại ĐÚNG dấu vết của một cú chạm (thuần `:core`) ═══════
 *
 * Owner 15/10 sau FAN-STEP: *"ngoài cái gió thì còn cái nào bị 'sót' nữa không?"*. Kiểm toán: `docs/diagnostics/stale-display-audit-
 * 2026-10-15.md`. Họ lỗi: phím/giọng nói ghi bảng dùng chung nhưng KHÔNG vẽ — bề mặt nào chỉ vẽ lại khi trạng thái xe ĐỔI (hoặc so
 * với bảng thay vì với hình) thì đứng hình cũ. Cú chạm ô làm BA việc; tới 3.1.6 đường phím/giọng nói (`VoiceControlDispatch.finish`)
 * chỉ làm việc thứ nhất:
 *
 *  1. ghi bảng theo kiểu nút (TOGGLE 1/0 · SELECT/COVER chỉ số · STEP mức) — y nguyên bản cũ;
 *  2. đích giữ [LampHold] cho nút ĐỌC ĐƯỢC hai mức (TOGGLE/COVER có `readKey`) — như cú chạm ô cốp (`ControlTileFactory` COVER), chấm
 *     đèn/cốp trên xe 3D và bảng Đèn: thiếu nó thì *"đóng cốp"* bằng phím xong ô/3D vẫn hiện MỞ ~5,8 s (cốp chỉ đọc 0 khi chốt khoá —
 *     [ĐO logcat xe 14/10], `DummyCarReceiver.TRUNK_CLOSE_MS`), trong khi cùng lệnh bằng ngón tay hiện ĐÓNG ngay;
 *  3. VẼ — ở đây là báo màn ([ControlLastSent.announce]) để màn chính vẽ lại mọi ô/bảng ngay, cùng đường của cầu `:wake`.
 *
 * Chỉ gọi khi lệnh ĂN (`ok`), như bản cũ — không lạc quan cho lệnh hỏng.
 *
 * ## Đồng hồ của đích giữ
 * [LampHold] được người vẽ hỏi bằng `SystemClock.uptimeMillis()`. Ở đây dùng `System.nanoTime() / 1e6` (chạy được trong bài JVM):
 * CÙNG gốc — [ĐO nguồn android-10.0.0_r47] `libutils/SystemClock.cpp:35-40` `uptimeMillis()` = `systemTime(SYSTEM_TIME_MONOTONIC)`;
 * `libcore/ojluni/src/main/native/System.c:218-221` `System_nanoTime` = `clock_gettime(CLOCK_MONOTONIC)`; javadoc
 * `SystemClock.java:95-101` *"[uptimeMillis] is the basis for … System.nanoTime()"*.
 */
object ControlWriteEcho {

    /** Đồng hồ đơn điệu (ms) cùng gốc `SystemClock.uptimeMillis()` — KDoc lớp. */
    fun uptimeMs(): Long = System.nanoTime() / 1_000_000L

    /** Lượt ghi ngoài ô của [def] có giữ đích [LampHold] không: nút hai mức ĐỌC ĐƯỢC (TOGGLE/COVER có `readKey`). */
    fun holds(def: ControlDef): Boolean =
        (def.kind == ControlKind.TOGGLE || def.kind == ControlKind.COVER) && def.readKey.isNotBlank()

    /**
     * Lệnh [arg] cho [def] vừa ĂN từ đường không vẽ ô: ghi bảng ([sent]; STEP qua [setStep] — mức −/+ sống ở bảng của `:app`), giữ
     * đích cho nút đọc được ([hold], mốc [now]), rồi báo màn vẽ lại.
     */
    fun applied(
        def: ControlDef,
        arg: Int,
        setStep: (String, Int) -> Unit,
        sent: ControlLastSent = ControlLastSent.shared,
        hold: LampHold = LampHold.shared,
        now: Long = uptimeMs(),
    ) {
        when (def.kind) {
            ControlKind.TOGGLE -> sent.record(def.id, if (arg > 0) 1 else 0)
            ControlKind.STEP -> setStep(def.id, arg)
            ControlKind.SELECT, ControlKind.COVER -> sent.record(def.id, arg)
            ControlKind.BUTTON -> Unit
        }
        if (holds(def)) hold.put(def.id, arg > 0, now)
        sent.announce()
    }
}
