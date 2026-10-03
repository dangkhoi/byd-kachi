package com.byd.clusternav.launcher

import java.util.concurrent.ConcurrentHashMap

/**
 * ═══ 2.87 · R-FL2 — TRÍ NHỚ *"LỆNH CUỐI KACHI ĐÃ GỬI"* của mỗi nút (KEYCTL-FLIP-ALL) ═══════════════════════════
 *
 * Owner 03/10: *"phím đóng mở cốp không chung được à, phải 2 nút à? Cái nút picker trên widget thì nhấn cái đóng,
 * nhấn cái mở đc mà?"* → *"Mấy cái khác cũng thế, cái nào đảo đc phải làm đảo hết nhé, chứ hao phím lắm"*. Spec
 * `docs/specs/kachi-287-look-and-keys.html` R-FL2 · §4.3.
 *
 * MỘT bảng `mã nút → CHỈ SỐ` cho cả tiến trình. Chỉ số mang đúng nghĩa `arg` của [actByKind]: TOGGLE 1 bật / 0 tắt ·
 * COVER mức (0 đóng · 1 mở · 2 nửa…) · SELECT chỉ số lựa chọn. STEP (mức −/+) và BUTTON không dùng bảng này — Đảo /
 * Kế tiếp không áp cho chúng.
 *
 * ## Ai ghi — [ĐO đọc mã 2026-10-03]
 *  - **Ô trên màn**: `ControlTileState` (`:app`) đọc/ghi bật-tắt + lựa chọn QUA bảng này (không còn map riêng). Ô ghi
 *    LẠC QUAN rồi hoàn nguyên khi xe thật từ chối (`ControlTileWrite`, so-rồi-hoàn-nguyên) — nên khi lượt ghi đã lắng
 *    xuống, bảng mang đúng lệnh cuối xe NHẬN. Phím xếp CÙNG làn nền với cú chạm ô (`ControlTileWrite.LANE`) ⇒ phím
 *    luôn đọc bảng SAU khi cú chạm trước đó đã lắng.
 *  - **Giọng nói + phím** (tiến trình chính): `VoiceControlDispatch.finish(ok)` — CHỈ khi `ok`, không lạc quan.
 *  - **Gói lệnh**: chỗ `setOn` sẵn có sau mỗi bước ĂN.
 *  - **Đọc lại xe** (ô có `readKey` theo nhịp poll, lượt đọc lại của giọng nói): ghi sự thật đọc được — tốt hơn lệnh
 *    cuối, và [KeyCtlPlan] chỉ hỏi bảng khi KHÔNG đọc được xe.
 *
 * ## Mặc định khi tiến trình bật — [startIndex]
 * **0** (tắt/đóng): BYD giết Kachi mỗi lần tắt máy, nên "vừa có tiến trình" ≈ "vừa nổ máy, cốp đóng, kính đóng".
 * Ngoại lệ DUY NHẤT: TOGGLE khai [ControlDef.onByDefault] (hôm nay chỉ `pm25`) bắt đầu ở **1** — đó là trạng thái ô đã
 * vẽ từ trước bản này; để 0 thì ô sáng "bật" mà phím Đảo lại BẬT, tức ô và phím lệch nhau ngay lần bấm đầu.
 *
 * ## Giới hạn — nói rõ, owner chấp nhận (spec §4.3)
 *  - Nút đổi bằng đường KHÁC (chìa, công tắc cửa, app BYD) ⇒ bảng không biết ⇒ lần Đảo đầu có thể trùng trạng thái
 *    (không tác dụng), bấm lại là được. Nút có `readKey` đọc được thì không dính (quyết bằng xe).
 *  - **Cấp TIẾN TRÌNH — đã có CẦU một chiều `:wake` → chính** (2.87 · SOÁT vòng 1 · P2, quyết định điều phối). [ĐO đọc
 *    mã] BA lối vào giọng nói chạy phiên ở tiến trình `:wake` và ghi xe qua `AppContainer.carControl` CỦA `:wake` (bảng
 *    [shared] của `:wake` — tiến trình chính KHÔNG thấy nếu không có cầu):
 *     1. **phím vô-lăng gán "Kachi nghe"** — LUÔN đi `:wake` (`AssistantLauncher.launchKachiVoice` →
 *        `VoiceWakeService.listenNow(…, Entry.KEY)`, chạy cả khi Hey Kachi TẮT);
 *     2. **nút mic trên màn nhà** khi mô hình nằm ở `:wake` (Hey Kachi BẬT ∨ phím vô-lăng gán Kachi nghe —
 *        `VoiceEntry.tryWake`, CLOSE-3);
 *     3. **"Hey Kachi"** (câu gọi — `VoiceWakeService` → `VoiceWakeSessionFactory.buildSession`).
 *    Cầu: `:wake` nối [forwardTo] (mọi lượt [record] ở `:wake` — lệnh ghi THÀNH CÔNG, bước gói lệnh ĂN, số đọc lại từ
 *    xe; `:wake` không có ô nên không có ghi lạc quan nào) sang một broadcast TRONG GÓI; tiến trình chính nhận và
 *    [absorb] vào [shared] của nó. Còn hở: tiến trình chính CHẾT lúc `:wake` ghi ⇒ tin rơi (receiver động), tiến trình
 *    chính dựng lại ở [startIndex]; chiều ngược (ô/phím → `:wake`) không có — giọng nói không có Đảo nên không đọc
 *    bảng để quyết hướng.
 *  - Sống trong RAM: tiến trình chết là về [startIndex] — đúng ý đồ (xem trên), không ghi đĩa.
 *
 * Thuần (`:core`) · an toàn đa luồng (map đồng thời: ô ghi từ luồng chính, giọng nói/phím/gói lệnh từ luồng nền).
 */
class ControlLastSent {

    private val sent = ConcurrentHashMap<String, Int>()

    /** Nơi chuyển tiếp mỗi lượt [record] — chỉ `:wake` nối ([forwardTo]); tiến trình chính để `null`. */
    @Volatile private var forward: ((String, Int) -> Unit)? = null

    /** Chỉ số hiện nhớ của nút [id]; chưa có lệnh nào ⇒ [startIndex]. */
    fun index(id: String): Int = sent[id] ?: startIndex(id)

    /** Ghi chỉ số của lệnh vừa gửi (hoặc trạng thái vừa đọc được từ xe) — rồi chuyển tiếp nếu tiến trình này có cầu. */
    fun record(id: String, index: Int) {
        sent[id] = index
        forward?.invoke(id, index)
    }

    /**
     * Ghi một dòng do TIẾN TRÌNH KHÁC chuyển sang (cầu `:wake` → chính). KHÔNG chuyển tiếp lần nữa (không thể thành vòng,
     * kể cả khi ai đó lỡ nối [forwardTo] ở cả hai đầu). Dòng không hợp lệ ([relayable]) ⇒ bỏ, trả `false`.
     */
    fun absorb(id: String?, index: Int): Boolean {
        if (id == null || !relayable(id, index)) return false
        sent[id] = index
        return true
    }

    /** Nối (hoặc gỡ bằng `null`) nơi chuyển tiếp mỗi lượt [record]. Gọi lại nhiều lần an toàn — lần sau thay lần trước. */
    fun forwardTo(sink: ((String, Int) -> Unit)?) {
        forward = sink
    }

    companion object {
        /** Bảng dùng chung của tiến trình — ô, phím, giọng nói (tiến trình chính) cùng đọc/ghi. */
        val shared = ControlLastSent()

        /** Trạng thái giả định khi tiến trình vừa bật — luật ở KDoc lớp. */
        fun startIndex(id: String): Int =
            if (ControlRegistry.byId(id)?.let { it.kind == ControlKind.TOGGLE && it.onByDefault } == true) 1 else 0

        /**
         * Dòng qua cầu có hợp lệ không: nút CÒN trong registry, đúng ba kiểu mà bảng mang (TOGGLE · COVER · SELECT —
         * STEP/BUTTON không dùng bảng), chỉ số không âm (không chặn trên: bên đọc đã tự an toàn — Đảo dùng `> 0`, Kế tiếp
         * `coerceIn`; âm = extra thiếu ở đầu nhận).
         */
        fun relayable(id: String, index: Int): Boolean = index >= 0 &&
            ControlRegistry.byId(id)?.kind.let { it == ControlKind.TOGGLE || it == ControlKind.COVER || it == ControlKind.SELECT }
    }
}
