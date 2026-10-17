package com.byd.clusternav.launcher

/**
 * 3.01 R25 — luật tệp đệm của widget xe 3D (thuần `:core`; `:app` [Car3dModelSource] chỉ thi hành).
 *
 * Soát Pass 2 (13/10):
 *  • **Rác của lần dựng chết giữa chừng** — firmware BYD giết Kachi mỗi lần tắt máy (memory `kachi-car-facts-0929`): bản đệm
 *    dở `car3d-<khoá>.glb.part` (~30 MB) và bản chép lưới nén `car3d-src.tmp` (~35 MB) sống mãi khi khoá đổi. Trước khi dựng
 *    mới, xoá MỌI tệp thuộc widget ([owned]) — chỉ tên `car3d-*` với đuôi đã biết, không đụng tệp khác trong `cacheDir`.
 *  • **Đĩa đầy** — GLB ghi vào tệp ánh xạ THƯA (`setLength` không cấp khối): hết chỗ lúc ghi trang ⇒ SIGBUS = HOME sập, không
 *    `try/catch` nào bắt được. Kiểm chỗ trống TRƯỚC khi ánh xạ ([roomFor]); thiếu ⇒ không dựng, widget lùi bảng 2D.
 */
object Car3dCache {

    const val PREFIX = "car3d-"
    private const val MEMO_PREFIX = PREFIX + "cabin-"
    private const val MEMO_SUFFIX = ".bin"
    private val SUFFIXES = listOf(".glb", ".glb.part", ".tmp")

    /** Tệp [name] trong `cacheDir` là của widget 3D (bản đệm · bản dở · bản chép tạm · bản nhớ phân loại khoang). */
    fun owned(name: String): Boolean = name.startsWith(PREFIX) && (SUFFIXES.any { name.endsWith(it) } || isMemo(name))

    /**
     * 3.1.10 `CAR3D-CABIN-BUILD-COST` — bản nhớ kết quả [Car3dCabin] (lượt đắt nhất của lần dựng: 24,6/33,6 s trên xe 15/10 [ĐO
     * log]) theo khoá suy từ đầu vào ([Car3dCabin.key]). Sống QUA lần đổi khoá đệm GLB: chỉ GLB đổi (vật liệu, đèn) ⇒ không chạy lại.
     */
    fun memo(key: String): String = MEMO_PREFIX + key + MEMO_SUFFIX
    fun isMemo(name: String): Boolean = name.startsWith(MEMO_PREFIX) && name.endsWith(MEMO_SUFFIX)

    /** Dọn TRƯỚC khi dựng GLB mới: mọi tệp của widget TRỪ bản nhớ khoang (nó tự kiểm khớp khoá; cũ thì [staleMemo] dọn). */
    fun stale(name: String): Boolean = owned(name) && !isMemo(name)

    /** Bản nhớ khoang KHÁC khoá [key] vừa dùng ⇒ bỏ (một bản duy nhất, ~0,1 MB). */
    fun staleMemo(name: String, key: String): Boolean = isMemo(name) && name != memo(key)

    /**
     * Đủ chỗ ghi [bytes] khi đĩa còn [usable] byte: chừa [MARGIN_BYTES] cho hệ thống (Android coi `/data` dưới vài chục MB
     * là "sắp đầy" — để launcher ăn phần đó là đẩy cả xe vào lỗi ghi). `bytes ≤ 0` ⇒ không dựng.
     */
    fun roomFor(bytes: Long, usable: Long): Boolean = bytes > 0 && usable - bytes >= MARGIN_BYTES

    /** Phần chừa lại trên `/data` sau khi ghi tệp đệm (64 MB). */
    const val MARGIN_BYTES = 64L shl 20

    // ── 3.1.14 `PERF-SL6-3113` R5: lượt dựng NGUỘI (lưới → GLB, ~33 s CPU trên Seal) có trần qua các lần nổ máy ─────────────────────
    // SL6 17/10 (owner chuyển): bật xe 3D + Hey Kachi, đơ lúc nổ máy VÀ sau đó, qua nhiều lần reset. [ĐO mã 3.1.13] khoá đệm ổn định
    // qua các lần nổ máy, NHƯNG: kiểm hợp lý trượt ⇒ không ghi gì ⇒ MỖI lần khởi động phân tích lại lưới; gltfio từ chối GLB vừa dựng ⇒
    // bỏ đệm ⇒ dựng lại tới 4 lượt/tiến trình, lặp lại MỖI lần nổ máy; bị giết giữa chừng (BYD giết Kachi mỗi lần tắt máy) ⇒ dựng lại từ
    // đầu lần sau. Không có gì nhớ "đời xe + phiên bản này dựng hỏng". Cả ba lượt đều chạy ngay lúc nổ máy, tranh CPU với lúc khởi động.

    /** Tệp đánh dấu số lượt dựng nguội KHÔNG thành của khoá đệm [glbName] (sống ngoài `cacheDir`; khoá đổi theo phiên bản ⇒ cập nhật app thử lại). */
    fun failMark(glbName: String): String = PREFIX + "fail-" + glbName.removeSuffix(".glb") + ".txt"

    /**
     * Được dựng nguội không: chưa dựng lượt nào trong tiến trình này ([triedThisProcess] — tối đa MỘT lượt/lần khởi động) và khoá này
     * chưa hỏng quá [MAX_COLD_FAILS] lần (lượt bị giết giữa chừng cũng tính — dấu ghi TRƯỚC khi dựng, chỉ xoá khi gltfio đã nhận).
     */
    fun mayColdBuild(failed: Int, triedThisProcess: Boolean): Boolean = !triedThisProcess && failed < MAX_COLD_FAILS

    /** Còn phải chờ bao lâu trước lượt dựng nguội: không trong [COLD_AFTER_MS] đầu sau khi tiến trình khởi động. */
    fun coldDelayMs(sinceStartMs: Long): Long = (COLD_AFTER_MS - sinceStartMs).coerceAtLeast(0L)

    /** Đọc số trong tệp đánh dấu (hỏng/thiếu ⇒ 0). */
    fun parseFails(text: String?): Int = text?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0

    /** Ba lần nổ máy dựng hỏng (hoặc bị giết giữa chừng) cho cùng khoá ⇒ thôi, tới khi đổi khoá (cập nhật app / gói AVM). */
    const val MAX_COLD_FAILS = 3

    /** Lúc khởi động: HOME dựng, kênh shell, Hey Kachi nạp mô hình (~45 s Seal) — lượt dựng nguội chờ qua 30 s đầu. */
    const val COLD_AFTER_MS = 30_000L
}
