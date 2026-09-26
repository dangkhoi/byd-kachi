/*
 * ═══ CLOSE-4 · WAKE-MALLOPT — trả bộ nhớ native ĐÃ FREE về cho hệ, bằng đường DUY NHẤT mà bionic có ═══════
 *
 * Doc: `docs/diagnostics/offcar-2026-09-26/wake-mallopt-ndk.md` · audit gốc `docs/diagnostics/ram-audit-2026-09-25.md`
 * §4. Owner duyệt 2026-09-26 (7a) — `docs/PROJECT-BACKLOG.md` dòng CLOSE-4.
 *
 * ## Vì sao phải có một tệp C, không làm được bằng Kotlin
 * [ĐO AOSP android-10.0.0_r47] Không có API Java/Kotlin nào purge được malloc heap. `System.gc()` chỉ chạm
 * Dalvik heap; `onTrimMemory` chỉ là một callback — nền tảng tự gọi `mallopt(M_PURGE)` CHỈ khi sysprop
 * `debug.am.run_mallopt_trim_level` được đặt (`frameworks/base/core/java/android/app/ActivityThread.java:6016-6019`,
 * mặc định MAX ⇒ tắt) hoặc từ idler của một Activity (`:2083,2091`) — mà `:wake` **không có Activity**.
 * ⇒ đường duy nhất là gọi `mallopt` từ native. Hai hằng, cả hai là API công khai của bionic:
 *
 *  • `M_DECAY_TIME` = -100 — [ĐO `bionic/libc/include/malloc.h:164` (r47), `:165` (android-12.0.0_r34)].
 *    Giá trị 0 = *"Release the unused pages immediately"*; có từ **API 27**. Thi hành:
 *    `bionic/libc/bionic/jemalloc_wrapper.cpp:67-104` — đặt `arenas.{dirty,muzzy}_decay_ms` cho arena SẼ sinh
 *    ra, rồi lặp đặt `arena.<i>.{dirty,muzzy}_decay_ms` cho arena ĐÃ có. Cần vì Zygote đặt `M_DECAY_TIME 1`
 *    (= 1000 ms) cho MỌI app (`frameworks/base/core/jni/com_android_internal_os_Zygote.cpp:523`), và với decay
 *    > 0 thì purge chỉ chạy theo **tick sự kiện malloc** (1000 sự kiện/tick,
 *    `external/jemalloc_new/include/jemalloc/internal/arena_types.h:17`), KHÔNG có luồng nền
 *    (`opt_background_thread` = false) ⇒ một tiến trình im lặng sau khi nạp mô hình không bao giờ tới tick.
 *
 *  • `M_PURGE` = -101 — [ĐO `bionic/libc/include/malloc.h:171` (r47), `:172` (A12)]: *"immediately purge any
 *    memory not in use ... The value is ignored"*; có từ **API 28** (minSdk của Kachi = 29 ⇒ không cần cổng
 *    phiên bản). Thi hành `jemalloc_wrapper.cpp:105-124`: `thread.tcache.flush` **của luồng đang gọi** rồi
 *    `arena.<narenas>.purge` (chỉ số `narenas` = *"tất cả arena"*).
 *    ⚠ Hệ quả thiết kế, đã đọc từ source chứ không đoán: **tcache chỉ xả cho luồng GỌI** (*"there is no easy way
 *    to clear the caches of other threads"*, `:106-108`) ⇒ nên gọi trim **trên đúng luồng đã cấp phát** (luồng
 *    nạp mô hình / luồng `KachiSpeak`), không phải từ một luồng bất kỳ. Phần arena (phần lớn của 100+ MB) thì
 *    purge là toàn cục nên vẫn ăn.
 *
 * KHÔNG dùng `M_PURGE_ALL` (-104): [ĐO NDK r30 `sysroot/usr/include/malloc.h:229-241`] chỉ có từ **API 34** —
 * xe DiLink 3 là API 29, DL5 là API 31/32 ⇒ `je_mallopt` rơi xuống `return 0` (tuỳ chọn lạ) và không làm gì.
 *
 * ## Ràng buộc của tệp này
 *  • **C thuần, không STL** ⇒ APK không phải chở `libc++_shared.so` (~1 MB/ABI). Không thêm dependency nào.
 *  • Không giữ state, không luồng, không đăng ký JNI động: hai hàm tĩnh, mỗi hàm một lời gọi `mallopt`.
 *  • Không bao giờ ném: `mallopt` trả 1 = ok / 0 = lỗi (`malloc.h:181` r47) và ta trả đúng số đó lên Kotlin.
 */

#include <jni.h>
#include <malloc.h>

/*
 * `mallopt(M_PURGE, 0)` — trả về 1 khi bionic nhận lệnh, 0 khi không (allocator khác / tuỳ chọn lạ).
 * Tên hàm theo luật mangling JNI tĩnh: package `com.byd.clusternav.launcher.perf`, lớp `KachiMem`.
 */
JNIEXPORT jint JNICALL
Java_com_byd_clusternav_launcher_perf_KachiMem_nativePurge(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    return (jint) mallopt(M_PURGE, 0);
}

/*
 * `mallopt(M_DECAY_TIME, 0)` — *"Release the unused pages immediately"*: mọi lần free extent lớn sau lời gọi này
 * tự madvise ngay, không chờ tick. Gọi MỘT lần lúc tiến trình `:wake` sinh ra.
 */
JNIEXPORT jint JNICALL
Java_com_byd_clusternav_launcher_perf_KachiMem_nativeDecayNow(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    return (jint) mallopt(M_DECAY_TIME, 0);
}
