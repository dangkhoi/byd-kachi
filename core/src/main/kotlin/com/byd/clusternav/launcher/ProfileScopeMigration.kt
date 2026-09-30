package com.byd.clusternav.launcher

/**
 * ═══ V-CLUSTER · VC-R3 — DI TRÚ MỘT LẦN khi khoá đổi phạm vi XE → HỒ SƠ: *rót xuống*, không *bốc lên* ═════════════
 *
 * Spec `docs/specs/kachi-profiles-are-everything.html` §11.4.5. Thuần Kotlin ⇒ kiểm off-car.
 *
 * ## Vì sao cần — và vì sao làm sai là mất cấu hình
 * Đổi phạm vi mà không di trú thì ảnh chụp CŨ của mọi hồ sơ không có khoá mới. Lượt áp khi đó KHÔNG chạm khoá vắng
 * khỏi ảnh ⇒ giá trị đang sống được giữ ⇒ **hồ sơ nào được chụp trước thì chiếm cấu hình**, các hồ sơ còn lại ăn theo
 * rồi lệch dần — đúng bệnh mà `migrateNavScheduleOnce` (2026-09-28) đã phải chữa cho lịch dẫn đường.
 *
 * ## Cách làm
 * Chép giá trị ĐANG SỐNG vào ảnh của **mọi** hồ sơ, **chỉ điền chỗ trống**:
 *  • khoá cố định vắng khỏi ảnh ⇒ chép giá trị sống, kể cả `null` tường minh (cùng hợp đồng ảnh chụp: khoá có mặt
 *    với `null` ⇒ lượt áp XOÁ nó; khoá vắng ⇒ không chạm — hai nghĩa khác nhau);
 *  • họ chưa có mốc ⇒ chép cả họ sống (đã qua bộ kiểm) rồi đặt mốc. Họ ĐÃ có mốc ⇒ của hồ sơ đó, không chạm.
 *
 * Sau lượt này ai cũng bắt đầu bằng đúng cấu hình cụm đang dùng, rồi mới tách ra khi họ chỉnh. Chạy hai lần thì
 * lần hai không đổi gì (idempotent) — nhưng mốc chạy-một-lần vẫn nằm ở chỗ gọi, cùng `Editor` với dữ liệu.
 */
object ProfileScopeMigration {

    /**
     * @param shots hồ sơ → ảnh đã giải mã của MỘT tệp (hồ sơ chưa có ảnh ⇒ map rỗng).
     * @param live tệp sống.
     * @param newKeys khoá cố định vừa đổi phạm vi sang hồ sơ, của tệp này.
     * @param families họ của tệp này.
     * @param deferred khoá hoãn → khoá chờ (giá trị rót là lựa chọn hiệu lực, xem [ClusterSnapshotPlan.snapshot]).
     * @return CHỈ những hồ sơ có ảnh đổi → ảnh mới (ghi lại là việc của chỗ gọi, trong một `Editor`).
     */
    fun rotDown(
        shots: Map<String, Map<String, Any?>>,
        live: Map<String, Any?>,
        newKeys: Collection<String>,
        families: Collection<SnapshotFamily>,
        deferred: Map<String, String> = emptyMap(),
    ): Map<String, Map<String, Any?>> {
        // DRY: giá trị rót = đúng thứ phép chụp sẽ ghi (kể cả bộ kiểm họ + khoá hoãn), không một phép thứ hai.
        val carry = ClusterSnapshotPlan.snapshot(newKeys.associateWith { live[it] }, live, families, deferred).values
        val out = LinkedHashMap<String, Map<String, Any?>>()
        shots.forEach { (profile, shot) ->
            val next = LinkedHashMap(shot)
            var touched = false
            newKeys.forEach { k -> if (k !in next) { next[k] = carry[k]; touched = true } }
            families.forEach { f ->
                if (next[f.marker] == true) return@forEach
                // Nửa họ không mốc (sửa tay / bản dở) KHÔNG phải lựa chọn của hồ sơ: thay bằng họ sống cho trọn một khối.
                next.keys.removeAll { f.owns(it) }
                carry.forEach { (k, v) -> if (f.owns(k)) next[k] = v }
                next[f.marker] = true
                touched = true
            }
            if (touched) out[profile] = next
        }
        return out
    }
}
