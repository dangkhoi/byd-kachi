package com.byd.clusternav.launcher

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Bộ thi hành ĐƯỜNG LÙI của chạm vào màn ảo ô ([VdAppHost.onTouchEvent]) — tách khỏi `VdAppHost.kt` (trần 500 dòng, L6
 * 2026-10-03), thân giữ nguyên byte. Một đối tượng cho cả tiến trình (như `companion` cũ: mọi ô dùng chung một luồng).
 */
internal object VdTouchExec {
    /**
     * ⚠ [SOÁT OCR] MỘT luồng dùng chung cho đường LÙI của chạm — trước 1.69 mỗi `ACTION_DOWN`/`ACTION_UP`
     * dựng **một `Thread` mới** (hai luồng mỗi cú chạm), mỗi luồng chạy một lệnh dadb CHẶN. Cuộn một danh
     * sách trong ô là hàng chục luồng sinh-và-chết trong vài giây, tất cả xếp hàng sau CÙNG một chủ
     * `ShellTransport` — thêm luồng không làm nhanh hơn, chỉ làm mọi bản chụp luồng trên xe khó đọc.
     *
     * Hàng đợi **có trần** + [ThreadPoolExecutor.DiscardPolicy]: khi kênh shell nghẽn, bỏ cú chạm MỚI là
     * đúng — giữ nó lại chỉ để thi hành muộn vài giây thì app trong ô nhận một cú chạm ở chỗ người dùng đã
     * rời mắt từ lâu. Điều tuyệt đối KHÔNG được làm là chặn luồng vẽ (nên không có `CallerRunsPolicy`).
     */
    val TOUCH_FALLBACK: ThreadPoolExecutor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(16),
        { r -> Thread(r, "kachi-slot-tap").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardPolicy(),
    )
}
