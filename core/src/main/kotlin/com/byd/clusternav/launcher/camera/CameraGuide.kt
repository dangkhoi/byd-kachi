package com.byd.clusternav.launcher.camera

/**
 * ═══ VẠCH CHUẨN KHOẢNG CÁCH trên overlay camera — phần THUẦN ═══════════════════════════════════════════════
 *
 * ## Bài toán (owner 2026-09-28, trên xe)
 * *"camera nó tạo cảm giác xe mình rất xa xe bên cạnh, trong khi cách tầm 30cm thôi, nên khó phán đoán"*.
 *
 * Ba đường đã thử và đóng lại, ghi ở đây để không ai đi lại:
 *  • **Xin ảnh ghép nhìn-từ-trên-xuống của hãng** — [ĐO RE `com.byd.avc` 2026-09-28] ảnh ấy do thư viện native
 *    của hãng vẽ TRONG tiến trình app hãng, từ bộ hiệu chỉnh mã hoá riêng từng xe; dịch vụ hệ thống có hàm trả
 *    ảnh nhưng **trả mảng rỗng cứng**. Không xin được.
 *  • **Cắt vệt hẹp để kéo gần** — [ĐO xe 2026-09-28] owner xem trực tiếp: *"nhìn kỳ lắm, trả lại đi"*. Hãng không
 *    chỉ zoom, họ nắn theo mặt đất rồi mới cắt; mình không có dữ liệu hiệu chỉnh để nắn như vậy.
 *  • **Tự ghép bốn dải** — cần thông số ống kính và vị trí đặt camera trên xe, cả hai đều [CHƯA BIẾT].
 *
 * ## Vì sao vạch chuẩn là đường còn lại, và nó đủ tốt
 * Mọi đường trên đều cố làm hình **trông** thật hơn, tức vẫn trả về **cảm giác**. Vạch chuẩn trả về **con số**:
 * người lái đỗ cạnh một vật mốc ở khoảng cách họ quan tâm, chọn nấc sao cho vạch nằm đúng vật đó, từ đó về sau
 * nhìn vạch là biết. Đây đúng cách camera lùi zin làm, và nó **không phụ thuộc** thư viện hay dữ liệu của hãng.
 *
 * ⚠ **Mức trung thực của vạch**: ảnh mắt cá bị méo, nên một đường thẳng trên màn KHÔNG tương ứng một khoảng cách
 * không đổi ngoài đời. Vạch này chỉ đúng **tại chỗ người lái canh nó**, và lệch dần khi ra xa hai đầu vạch. Đó là
 * giới hạn thật, phải nói ra trong UI chứ không giấu — cũng là giới hạn của vạch camera lùi zin.
 *
 * Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 */
object CameraGuide {

    /** Không hiện vạch — mặc định, vì phần lớn người dùng chưa canh bao giờ. */
    const val OFF = "OFF"

    /** Nấc thấp nhất (gần mép trên khung). */
    const val STEP_MIN = 1

    /** Nấc cao nhất (gần mép dưới khung). */
    const val STEP_MAX = 9

    /** Mọi giá trị hợp lệ — cũng là thứ tự chip trong Cài đặt. */
    val VALUES: List<String> = listOf(OFF) + (STEP_MIN..STEP_MAX).map { it.toString() }

    /** Giá trị đọc lên có dùng được không (prefs sửa tay được qua `prefs_set`). */
    fun isValue(v: String?): Boolean = v != null && VALUES.any { it.equals(v.trim(), ignoreCase = true) }

    /** Giá trị mặc định khi chưa ai canh. */
    fun defaultValue(): String = OFF

    /**
     * Nấc của giá trị, hay `null` = **không vẽ vạch nào**.
     *
     * Giá trị lạ trên đĩa (bản trước / gõ tay) ⇒ `null`: thà không vẽ còn hơn vẽ một vạch ở chỗ ngẫu nhiên, vì
     * người lái sẽ tin nó. Đây là chỗ *"sai thì im"* đúng hơn *"sai thì đoán"*.
     */
    fun stepOf(v: String?): Int? {
        val t = v?.trim().orEmpty()
        if (t.isEmpty() || t.equals(OFF, ignoreCase = true)) return null
        return t.toIntOrNull()?.takeIf { it in STEP_MIN..STEP_MAX }
    }

    /**
     * Vị trí vạch theo **chiều cao khung**, chuẩn hoá `0f..1f` (0 = mép trên). Nấc `n` ⇒ `n / 10`.
     *
     * Chia mười cố ý để chín nấc nằm gọn trong `0,1 … 0,9`: không nấc nào trùng mép khung, vì một vạch dính sát
     * mép thì người lái không phân biệt được nó với viền overlay.
     */
    fun positionOf(step: Int): Float = step.coerceIn(STEP_MIN, STEP_MAX) / 10f

    /** Vị trí vạch cho một giá trị, hay `null` nếu không vẽ. Gộp [stepOf] + [positionOf] cho chỗ gọi. */
    fun positionFor(v: String?): Float? = stepOf(v)?.let { positionOf(it) }
}
