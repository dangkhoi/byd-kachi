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
}
