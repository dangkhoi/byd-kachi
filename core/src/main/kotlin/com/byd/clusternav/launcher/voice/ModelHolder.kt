package com.byd.clusternav.launcher.voice

import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.withLock
import kotlin.concurrent.write

/**
 * ═══ FIX286 · VK3 (S1) — GIỮ MỘT MÔ HÌNH CHO CẢ TIẾN TRÌNH, với khoá ĐO ĐƯỢC (thuần, `:core`) ═══════════════════════
 *
 * Thay `synchronized(this)` + `ReentrantReadWriteLock` của `VoiceEngine` (2.66–2.85). Lý do không phải thẩm mỹ:
 *
 * [ĐO mã 02/10 · phản biện R-VK B1] `VoiceEngine.release()` chờ CÙNG monitor mà luồng dựng (9–34 s) đang giữ, và
 * nhánh đứng xuống BG-20 gọi nó trên LUỒNG CHÍNH của `:wake`. Huỷ khi còn "Getting ready…" rồi bấm phím lần nữa ⇒
 * `onStartCommand` xếp hàng sau luồng chính đang bị chặn ⇒ không tấm chữ nào hiện tới khi lượt nạp 1 xong, rồi mô hình
 * vừa nạp bị nhả và lần bấm 2 nạp lại từ đầu. `synchronized` không hỏi được *"có ai đang giữ không"* và không thử-lấy
 * được ⇒ đổi sang [ReentrantLock]: [loading] đọc thẳng từ khoá (không cờ ghi tay — cờ có thể lệch khỏi sự thật), và
 * [tryRelease] KHÔNG BAO GIỜ chặn.
 *
 * ## Vì sao KHÔNG "chuyển release() sang luồng nền" (phản biện R-VK #6)
 * Quyết định *"phiên đã IDLE"* lấy TRƯỚC lúc chờ khoá; trong lúc chờ, lần bấm 2 dựng phiên B và B lấy được mô hình
 * trước ⇒ lượt nhả muộn rút mô hình dưới chân B ⇒ giải mã trả rỗng ⇒ người lái nói xong nhận *"không nghe thấy"*. Nên
 * [tryRelease] nhận `precheck` — chạy KHI ĐÃ GIỮ khoá dựng — để chỗ gọi kiểm lại pha/epoch phiên ngay tại đó; sai ⇒
 * [Release.SKIPPED], không nhả.
 *
 * ## Hai khoá, hai việc (giữ nguyên hợp đồng 2.66)
 *  • [build] (`ReentrantLock`) — dựng / đổi / nhả. [get] chặn trên khoá này: CHỈ gọi từ luồng NỀN (phiên nghe ·
 *    nạp sẵn · bộ nghe câu gọi). Lượt chờ thứ hai nhận ĐÚNG bản lượt thứ nhất vừa dựng — builder chạy MỘT lần.
 *  • [use] (`ReentrantReadWriteLock`) — giải mã giữ `read` ([withUse]), nhả giữ `write`. Không có nó, nhả trong lúc
 *    một lượt giải mã đang chạy là use-after-free native (SIGSEGV, không phải ngoại lệ). Bản đã nhả ⇒ [withUse] trả
 *    `null`, không chạm native.
 *
 * Thứ tự lấy khoá luôn là build → use(write); giải mã không bao giờ lấy build ⇒ không có vòng chờ.
 *
 * @param close nhả tài nguyên native của một bản (chạy dưới cả hai khoá). Ném ⇒ vẫn coi là đã nhả (bỏ tham chiếu).
 * @param nanoTime đồng hồ đo [lastBuildMs] — tiêm được cho test.
 */
class ModelHolder<K : Any, T : Any>(
    private val close: (T) -> Unit,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val build = ReentrantLock()
    private val use = ReentrantReadWriteLock()

    @Volatile private var model: T? = null
    @Volatile private var key: K? = null
    @Volatile private var builds = 0
    @Volatile private var lastBuildMs = -1L

    /** Kết quả [tryRelease]. */
    enum class Release {
        /** Đã nhả. */
        RELEASED,

        /** Không có gì để nhả. */
        EMPTY,

        /** Khoá dựng đang bị giữ (đang nạp) hoặc đang có lượt giải mã — không chờ, chỗ gọi hỏi lại sau. */
        BUSY,

        /** `precheck` nói không còn nên nhả (pha/epoch phiên đã đổi trong lúc quyết). */
        SKIPPED,
    }

    /** Bản đang nằm trong RAM (`null` = chưa nạp / đã nhả). Không khoá. */
    fun current(): T? = model

    /** Khoá của bản đang nằm trong RAM (`null` = không có). */
    fun currentKey(): K? = key

    /**
     * Đang dựng / đổi / nhả — đọc thẳng từ khoá dựng ([ReentrantLock.isLocked]), không từ cờ ghi tay. Đầu vào
     * `loading` của [VoiceWakeStandDown.decide].
     */
    fun loading(): Boolean = build.isLocked

    /** Số lượt dựng THÀNH CÔNG từ lúc tiến trình bật — nhật ký phiên `:wake` so trước/sau để biết phiên có nạp không. */
    fun builds(): Int = builds

    /** Thời gian lượt dựng thành công gần nhất (ms), `-1` = chưa có lượt nào. */
    fun lastBuildMs(): Long = lastBuildMs

    /**
     * Bản cho [k]: có sẵn ⇒ trả ngay (không khoá); chưa có / khác khoá ⇒ nhả bản cũ rồi dựng dưới khoá dựng.
     * **CHẶN** khi có lượt dựng khác đang chạy ⇒ chỉ gọi từ luồng NỀN. [builder] trả `null` (chưa tải gói / hỏng) ⇒
     * trả `null`, không nhớ gì — lượt sau dựng lại.
     */
    fun get(k: K, builder: () -> T?): T? {
        model?.let { if (key == k) return it }
        return build.withLock {
            model?.let { if (key == k) return@withLock it else releaseHeld() }
            val t0 = nanoTime()
            builder()?.also {
                model = it
                key = k
                lastBuildMs = (nanoTime() - t0) / NANOS_PER_MS
                builds++
            }
        }
    }

    /**
     * Nhả CÓ CHỜ (lượt dựng đang chạy, lượt giải mã đang chạy) — cho gỡ/đổi gói từ màn Cài đặt (luồng nền).
     * ⚠ KHÔNG gọi trên luồng chính của `:wake`: chờ ở đây có thể là cả một lượt nạp. Đường đứng xuống dùng [tryRelease].
     */
    fun release() {
        build.withLock { releaseHeld() }
    }

    /**
     * Nhả KHÔNG CHẶN: không lấy được khoá dựng ngay (đang nạp) hoặc khoá ghi ngay (đang giải mã) ⇒ [Release.BUSY].
     * [precheck] chạy SAU khi đã giữ khoá dựng — chỗ gọi kiểm lại điều kiện đã dùng để quyết nhả (VK3: pha IDLE +
     * epoch phiên chưa đổi); `false` ⇒ [Release.SKIPPED].
     */
    fun tryRelease(precheck: () -> Boolean = { true }): Release {
        if (!build.tryLock()) return Release.BUSY
        try {
            if (!precheck()) return Release.SKIPPED
            val cur = model ?: return Release.EMPTY
            val w = use.writeLock()
            if (!w.tryLock()) return Release.BUSY
            try {
                drop(cur)
            } finally {
                w.unlock()
            }
            return Release.RELEASED
        } finally {
            build.unlock()
        }
    }

    /**
     * Chạy [block] khi [m] **vẫn là** bản hiện hành, dưới khoá đọc; đã bị nhả/đổi ⇒ `null`, không chạy (không chạm
     * native của một bản đã giải phóng).
     */
    fun <R> withUse(m: T, block: () -> R): R? = use.read {
        if (model !== m) null else block()
    }

    /** Gọi khi ĐÃ giữ khoá dựng: chờ lượt giải mã đang chạy (khoá ghi) rồi nhả. */
    private fun releaseHeld() {
        val cur = model ?: return
        use.write { drop(cur) }
    }

    /** Gọi dưới cả hai khoá. Bỏ tham chiếu TRƯỚC khi gọi [close]: [close] ném thì bản này vẫn không còn được dùng. */
    private fun drop(cur: T) {
        model = null
        key = null
        close(cur)
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
    }
}
