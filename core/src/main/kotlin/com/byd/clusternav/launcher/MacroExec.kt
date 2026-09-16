package com.byd.clusternav.launcher

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * ═══ CHỖ CHẠY GÓI LỆNH — **daemon, có trần, không bao giờ từ chối** ══════════════════════════════════════════
 *
 * [SOÁT 2026-09-16 · P3] Trước bản này, mỗi cú chạm ô gói lệnh dựng thẳng một `Thread(…).start()` trong
 * `ControlTileFactory.macroTile`. Hai chỗ hỏng, cả hai đều im lặng:
 *
 *  1. **Thread KHÔNG phải daemon.** Một gói đang ngủ giữa hai bước (mặc định 400 ms/bước, gói *"Rời xe"* có
 *     năm bước) **giữ tiến trình sống** sau khi launcher đã dọn xong — JVM chỉ thoát khi luồng non-daemon cuối
 *     cùng kết thúc. Mọi chỗ chạy nền khác của dự án đã là daemon (`SlotLiveProbe`, `VoiceUtteranceLog`,
 *     `PhotoWidgetView`, `InputDaemonClient`…); đây là chỗ duy nhất còn sót.
 *  2. **Không có trần số luồng.** `ControlTileState.beginRun` chỉ chặn **cùng một** mã gói chạy chồng; hai ô gói
 *     KHÁC nhau (ô giữa màn + thanh nút, hoặc hai gói khác nhau) thì không gì chặn, và mỗi cú chạm là một luồng
 *     mới toanh.
 *
 * ## Vì sao HAI luồng, không phải một, và vì sao hàng đợi KHÔNG có trần
 *  • **Trần 2**: gói lệnh là một chuỗi ghi xuống HAL có **ngủ** xen giữa, tức nó tốn *thời gian* chứ gần như
 *    không tốn CPU. Một người không chạm được nhiều hơn hai gói khác nhau trong một nhịp thật; để 2 là đủ cho
 *    mọi ca có thật mà vẫn có một con số trần thay vì "bao nhiêu cũng được".
 *  • **Hàng đợi không trần** (và vì thế **không có `RejectedExecutionException`**): nếu một lượt bị từ chối thì
 *    `state.endRun(macro.id)` trong khối `finally` của nó **không bao giờ chạy** ⇒ cờ chống-bấm-kép kẹt `true`
 *    ⇒ **ô chết hẳn**, bấm mãi không lên. Đó đúng là kiểu hỏng mà KDoc `macroTile` đã phải chữa một lần rồi
 *    (nhả cờ ngoài `tile.post`). Xếp hàng thì chậm; từ chối thì hỏng vĩnh viễn.
 *  • `allowCoreThreadTimeOut(true)` ⇒ ngồi im 30 s là **không còn luồng nào**; xe đứng yên không nuôi luồng nào.
 *
 * ⚠ **Không đổi hành vi của gói**: thứ tự bước, khoảng ngủ, cách ghi trạng thái, cách báo lỗi đều nằm nguyên ở
 * `MacroRunner` / `macroTile`. Ở đây chỉ đổi **ai cầm luồng**.
 *
 * ## Vì sao ở `:core` chứ không cạnh `ControlTileFactory`
 * Tệp này **thuần JVM** (không `Context`, không `View`, không `android.*`) ⇒ `LayeringRulesTest` xếp nó vào
 * `:core`, và nhờ thế bài kiểm của nó chạy trong `:core:test` — đo được **thật** trần luồng và cờ daemon, thay
 * vì chỉ quét chuỗi trong mã nguồn. Nó **công khai** vì `internal` ở `:core` thì `:app` không thấy; thứ giữ nó
 * khỏi bị gọi bừa không phải từ khoá mà là vai của nó: **một** chỗ gọi, ở `ControlTileFactory.macroTile`.
 */
object MacroExec {

    /** Xem KDoc lớp về vì sao đúng hai luồng và vì sao hàng đợi không có trần. */
    private const val MAX_THREADS = 2

    /** Luồng rỗi sống thêm ngần này rồi tự tắt — không giữ luồng nào khi không ai bấm gói. */
    private const val IDLE_SECONDS = 30L

    private val pool: ThreadPoolExecutor = ThreadPoolExecutor(
        MAX_THREADS,
        MAX_THREADS,
        IDLE_SECONDS,
        TimeUnit.SECONDS,
        LinkedBlockingQueue<Runnable>(),
        ThreadFactory { r -> Thread(r, "kachi-macro").apply { isDaemon = true } },
    ).apply { allowCoreThreadTimeOut(true) }

    /**
     * Chạy [body] trên luồng nền dùng chung.
     *
     * Đổi tên luồng theo [macroId] ngay trước khi chạy: nhật ký sự cố (`KachiMacro`) và một lượt `dumpsys` trên
     * xe phải đọc ra **gói nào đang chạy**, y như cái tên `"macro-<id>"` mà bản dựng-luồng-riêng cho. Tên được
     * trả lại sau khi xong, vì luồng này còn phục vụ gói khác.
     */
    fun submit(macroId: String, body: () -> Unit) {
        pool.execute {
            val t = Thread.currentThread()
            val name = t.name
            runCatching { t.name = "macro-$macroId" }
            try {
                body()
            } finally {
                runCatching { t.name = name }
            }
        }
    }
}
