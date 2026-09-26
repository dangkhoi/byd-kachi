package com.byd.clusternav.launcher.perf

import android.os.Debug
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.File

/**
 * ═══ CLOSE-4 · WAKE-MALLOPT — mặt Kotlin AN TOÀN của `libkachimem.so` ════════════════════════════════════
 *
 * Doc `docs/diagnostics/offcar-2026-09-26/wake-mallopt-ndk.md`; cơ chế + trích dẫn AOSP ở `src/main/cpp/kachimem.c`.
 * Owner duyệt 2026-09-26 (7a) — backlog CLOSE-4.
 *
 * ## Bài toán
 * [ĐO máy ảo 2026-09-25, `ram-audit-2026-09-25.md` §4] `:wake` giữ **126 MB đã free mà không trả hệ**: rác
 * transient lúc nạp mô hình ASR (ModelProto 71 MB của encoder bị onnxruntime giải phóng ngay sau khi dựng phiên)
 * nằm lại dirty trong arena jemalloc, vì Zygote đặt decay 1000 ms và decay chỉ chạy theo **tick sự kiện malloc** —
 * mà một tiến trình im lặng sau khi nạp không bao giờ tới tick.
 * [ĐO xe 2026-09-26, `perf-oncar-2026-09-26/after-oncar-2.69.md`] cùng lúc đó trên xe: `:wake` Native Heap
 * PSS **157 MB**, size 177 MB, alloc 168 MB, free **9,2 MB** — tức trên xe (cabin có tiếng ⇒ có tick) phần "free
 * không trả" nhỏ, nhưng tổng vẫn 180 MB PSS. ⇒ hiệu quả thật của bản vá này **chưa đo trên xe** (🚗, xem doc).
 *
 * ## Ba luật của lớp này
 *  1. **Không bao giờ giết bản release.** Thiếu `.so` (build lỗi, ROM lạ, `pm install` mất lib) ⇒
 *     [UnsatisfiedLinkError] bị bắt MỘT lần lúc nạp lớp, [loaded] = false, và từ đó mọi hàm là no-op trả `false`.
 *     Không có nhánh nào gọi native khi `loaded == false`, nên không có `UnsatisfiedLinkError` lần hai.
 *  2. **Không bao giờ nằm trên đường nóng.** Chỉ gọi ở **mốc pha** (nạp xong mô hình · nhả mô hình · phiên nghe
 *     xong · đọc xong một câu). `mallopt(M_PURGE)` là một loạt `madvise` — gọi mỗi khúc audio 100 ms sẽ đổi 100 MB
 *     RAM lấy một đường trễ mới, đúng thứ CLAUDE.md §6 cấm (không đảo hỏng đường đang chạy tốt).
 *  3. **Chỉ đo, không quyết định.** Lớp này không đọc pref, không tự hẹn giờ, không biết gì về voice — người gọi
 *     quyết định khi nào là mốc pha. Nhờ vậy nó test được off-device bằng đúng một bài (ca thiếu `.so`).
 *
 * ## Vì sao dòng log có cả RSS, không chỉ `Debug.getNativeHeap*`
 * [ĐO AOSP r47] `Debug.getNativeHeapSize()` = `mallinfo().usmblks`, `getNativeHeapAllocatedSize()` = `uordblks`,
 * `getNativeHeapFreeSize()` = `fordblks` (`frameworks/base/core/jni/android_os_Debug.cpp:157-174`), mà Android
 * tính `usmblks = hblkhd` và `fordblks = hblkhd - uordblks` với `hblkhd` = tổng `arena->stats.mapped`
 * (`external/jemalloc_new/src/android_je_mallinfo.c:59,66,67`). `M_PURGE` madvise **pages** chứ không unmap extent
 * ⇒ `mapped` **không đổi** ⇒ *"Heap Size"/"Heap Free"* của `dumpsys meminfo` gần như không nhúc nhích, còn thứ
 * rơi là **RSS/PSS**. Nên dòng log in cả ba, và số để KẾT LUẬN là RSS (`/proc/self/statm`, cột 2 × page size) —
 * nếu chỉ tin hai số Debug thì bản vá đúng vẫn trông như vô tác dụng.
 */
object KachiMem {

    const val TAG = "KachiMem"

    private const val LIB = "kachimem"

    /** `mallopt` trả 1 = nhận lệnh, 0 = không ([ĐO] `bionic/libc/include/malloc.h:174-181`). */
    private const val MALLOPT_OK = 1

    /**
     * Thư viện có nạp được không — đọc MỘT lần lúc lớp được chạm đầu tiên.
     *
     * Bắt [UnsatisfiedLinkError] (thiếu `.so` / sai ABI) và [SecurityException] (ROM chặn `dlopen`) — hai lỗi
     * DUY NHẤT mà `loadLibrary` sinh ra ở đây; không bắt `Throwable` (CLAUDE.md §4.1: cấm nuốt lỗi lạ).
     * ⚠ Cố ý **không** log ở đây: hàm này chạy trong khởi tạo `object`, và trong unit test JVM `android.util.Log`
     * là stub NÉM — một dòng log "cho đẹp" ở đây sẽ làm mọi bài test chạm lớp này đỏ vì lý do không liên quan.
     * Ai cần biết trạng thái thì đọc [available] và tự log (xem `VoiceWakeService.onCreate`).
     */
    private val loaded: Boolean = run {
        try {
            System.loadLibrary(LIB)
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    /** `libkachimem.so` đã nạp được chưa (bản không có lib ⇒ mọi hàm dưới là no-op). */
    fun available(): Boolean = loaded

    /**
     * `mallopt(M_DECAY_TIME, 0)` — từ đây trở đi tiến trình **tự trả** page free ngay, không chờ tick decay.
     * Gọi MỘT lần, càng sớm càng tốt, ở tiến trình cần nó (`:wake`). @return `true` khi bionic nhận lệnh.
     */
    fun decayNow(): Boolean {
        if (!loaded) return false
        return nativeDecayNow() == MALLOPT_OK
    }

    /**
     * `mallopt(M_PURGE, 0)` — trả NGAY mọi page không dùng về kernel, kèm một dòng log DEBUG có số trước/sau.
     *
     * Gọi **trên đúng luồng đã cấp phát** khi có thể (tcache chỉ xả cho luồng gọi — xem `kachimem.c`).
     *
     * @param reason mốc pha nào gọi (vào log, để đọc `logcat -s KachiMem:D` biết đường nào ăn).
     * @return `true` khi bionic nhận lệnh purge; `false` khi thiếu `.so` hoặc `mallopt` từ chối.
     */
    fun trim(reason: String): Boolean {
        if (!loaded) return false
        val allocBefore = Debug.getNativeHeapAllocatedSize()
        val sizeBefore = Debug.getNativeHeapSize()
        val rssBefore = rssBytes()
        val t0 = SystemClock.uptimeMillis()
        val ok = nativePurge() == MALLOPT_OK
        val ms = SystemClock.uptimeMillis() - t0
        Log.d(
            TAG,
            "purge($reason) ok=$ok ${ms}ms · RSS ${mb(rssBefore)}→${mb(rssBytes())} MB · " +
                "alloc ${mb(allocBefore)}→${mb(Debug.getNativeHeapAllocatedSize())} MB · " +
                "size ${mb(sizeBefore)}→${mb(Debug.getNativeHeapSize())} MB",
        )
        return ok
    }

    /**
     * RSS của tiến trình theo `/proc/self/statm` cột 2 (số page resident) × page size; `-1` khi không đọc được.
     *
     * Đọc `/proc/self` nên không cần quyền gì và không có ranh giới tiến trình nào ở giữa. Page size lấy từ
     * `sysconf(_SC_PAGESIZE)` chứ không viết cứng 4096 — máy 16 KB page sẽ cho số sai gấp 4 lần.
     */
    private fun rssBytes(): Long = try {
        val pages = File("/proc/self/statm").readText().trim().split(' ')[1].toLong()
        pages * Os.sysconf(OsConstants._SC_PAGESIZE)
    } catch (e: java.io.IOException) {
        -1L
    } catch (e: RuntimeException) {
        // NumberFormatException / IndexOutOfBounds: định dạng statm lạ. Một dòng log hỏng không được làm hỏng purge.
        -1L
    }

    /** Byte → MB một chữ số thập phân; `-1` (không đọc được) giữ nguyên dấu để đọc log không nhầm là 0. */
    private fun mb(bytes: Long): String =
        if (bytes < 0) "?" else String.format(java.util.Locale.US, "%.1f", bytes / 1_048_576.0)

    private external fun nativePurge(): Int

    private external fun nativeDecayNow(): Int
}
