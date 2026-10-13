package com.byd.clusternav.launcher

/**
 * ═══ 3.01 R28 — widget xe 3D là "hero" của màn chính (thuần `:core`: luật, không vẽ) ════════════════════════════════
 *
 * Owner 13/10 (*"làm hết ra apk mới"*): trên chính hình xe 3D — hai icon sưởi/mát NGAY Ở GHẾ (lái, phụ), áp suất + nhiệt lốp
 * DƯỚI TỪNG BÁNH, hàng chân [nhiệt −/+] · [3 nút kính] · [gió −/+] · [lấy gió trong/ngoài]. Mọi điều khiển đã chạy trên xe ⇒
 * đây CHỈ là UX: ghi qua ô nút SẴN CÓ ([ControlRegistry] `seath`/`seatc`/`seath_r`/`seatc_r`/`temp`/`fan`/`recirc` →
 * `ControlTileFactory`), đọc qua cùng datum với chip thanh trên / widget Lốp. Không lệnh HAL mới, không nhịp poll mới.
 *
 * Khung thế giới của xe 3D ([Car3dOrient]): Y lên · Z = đầu xe · X = Y×Z = sườn TRÁI (đo: `door-lf` x +0,85, `turn-left` x
 * +0,73). Ghế LÁI = sườn trái (tay lái thuận) — tay lái nghịch là [CHƯA BIẾT] (`CustomVehicleConfig.isRightDriver`, diagnostics
 * §1) ⇒ mặc định tay lái thuận như toàn bộ widget.
 */
object Car3dHero {

    /** Một ghế: mã nút sưởi/mát (SELECT Tắt/Mức 1/Mức 2) + datum đọc mức tương ứng (cùng cặp [TopStripConfig.SEAT_PAIRS]). */
    enum class Seat(val heat: String, val vent: String, val heatDatum: String, val ventDatum: String, val side: Int) {
        DRIVER("seath", "seatc", "seat_heat_state", "seat_vent_state", LEFT),
        PASSENGER("seath_r", "seatc_r", "seat_heat_state_r", "seat_vent_state_r", RIGHT),
    }

    /**
     * Số đọc ghế → CHỈ SỐ lựa chọn của ô SELECT (0 tắt · 1 · 2), để ô nút ẩn ([ControlTileFactory] SELECT) tự xoay vòng từ ĐÚNG
     * mức xe đang ở (chạm: tắt → 1 → 2 → tắt). Nguồn = [TelemetryReadout.of]`.level` — cùng thang [ControlLevels] với chip thanh
     * trên (OFF=1·1=2·2=3 cho ghế mát [ĐO xe 09-17]). Chưa đọc / mức ngoài số lựa chọn của nút (sưởi [SUY] 4 mức) ⇒ KHÔNG có khoá
     * ⇒ ô giữ mức lệnh cuối của chính nó (không bịa).
     */
    fun seatControls(car: CarStatus): Map<String, Int> = buildMap {
        for (s in Seat.values()) for ((id, datum) in listOf(s.heat to s.heatDatum, s.vent to s.ventDatum)) {
            val options = ControlRegistry.byId(id)?.args?.size ?: continue
            TelemetryReadout.of(datum, car)?.level?.takeIf { it in 0 until options }?.let { put(id, it) }
        }
    }

    /** Bước kế khi chạm icon ghế: cùng phép của ô SELECT ([ControlTileLogic.nextSelectIndex]) — 0 → 1 → 2 → 0. */
    fun nextSeat(level: Int?, options: Int): Int = ControlTileLogic.nextSelectIndex(level ?: 0, options)

    /**
     * Điểm đặt icon ghế (thế giới): tựa lưng ghế trước nhìn QUA kính cửa trước — lùi vào trong [SEAT_INWARD] bề ngang, ngay dưới
     * tâm kính ([SEAT_DROP]), lùi về phía trụ B [SEAT_BACK] (tựa lưng ở nửa sau cửa trước). [ĐO máy ảo 13/10] bản đầu (giữa tấm cửa
     * và kính) chiếu ra ngay gương chiếu hậu ⇒ trông như nút trên gương, không phải trên ghế. Suy từ neo cửa/kính SẴN CÓ của [Car3dCatalog] — không neo tên ghế (lưới AVM
     * không có bộ phận ghế riêng: `body_interior` một khối [ĐO tên bộ phận Seal]).
     */
    fun seatPoint(door: V3, glass: V3): V3 =
        V3(door.x * SEAT_INWARD, glass.y - SEAT_DROP, (door.z + glass.z) / 2 - SEAT_BACK)

    /**
     * Ghế có hiện không: camera ở PHÍA ghế đó, hoặc đủ cao để nhìn xuống qua kính nóc (pháp tuyến = sườn + [SEAT_UP] × lên).
     * ¾ trước-trái (yaw 40°, pitch 16°) ⇒ ghế lái hiện, ghế phụ (khuất sau thân) ẩn; nhìn thẳng trước/trên ⇒ cả hai.
     */
    fun seatVisible(cam: Car3dCamera, p: V3, side: Int): Boolean = cam.facing(p, V3(side.toDouble(), SEAT_UP, 0.0))

    /**
     * Bốn điểm chạm đất ở MẶT NGOÀI từng bánh (thế giới) từ hộp bao HỢP các bộ phận `wheel*` [lo]..[hi]: bánh trước ở đầu +Z,
     * tâm trục lùi một bán kính (nửa chiều cao hộp) khỏi mép; mặt ngoài = mép X của hộp. Một hộp chung vì lưới AVM gộp bánh
     * sau vào MỘT bộ phận (`wheelr_texture` Seal [ĐO]) — đo hình học thay vì theo tên bánh.
     */
    fun wheels(lo: V3, hi: V3): Map<TyreCorner, V3> {
        val r = (hi.y - lo.y) / 2
        val front = hi.z - r; val rear = lo.z + r
        return mapOf(
            TyreCorner.FRONT_LEFT to V3(hi.x, lo.y, front), TyreCorner.FRONT_RIGHT to V3(lo.x, lo.y, front),
            TyreCorner.REAR_LEFT to V3(hi.x, lo.y, rear), TyreCorner.REAR_RIGHT to V3(lo.x, lo.y, rear),
        )
    }

    /**
     * Hai cụm icon ghế không được đè nhau (nhìn thẳng trước/trên thì hai ghế chiếu gần nhau — [ĐO máy ảo 13/10] yaw 0 pitch 59: hai cụm
     * 153 px cách nhau 117 px). [a], [b] = tâm x của hai cụm; cách nhau < [need] ⇒ tách đều quanh trung điểm, GIỮ thứ tự trái/phải.
     */
    fun spread(a: Float, b: Float, need: Float): Pair<Float, Float> {
        if (kotlin.math.abs(a - b) >= need) return a to b
        val mid = (a + b) / 2; val half = need / 2
        return if (a <= b) (mid - half) to (mid + half) else (mid + half) to (mid - half)
    }

    /** Sườn của bánh: trái = +X. */
    fun side(c: TyreCorner): Int = if (c == TyreCorner.FRONT_LEFT || c == TyreCorner.REAR_LEFT) LEFT else RIGHT

    /** Nhãn lốp hiện khi mặt ngoài bánh quay về camera (bánh phía khuất ẩn) — pháp tuyến sườn thuần. */
    fun tyreVisible(cam: Car3dCamera, p: V3, c: TyreCorner): Boolean = cam.facing(p, V3(side(c).toDouble(), 0.0, 0.0))

    /**
     * Hàng chân có chỗ cho hai bộ chỉnh nhiệt/gió không (owner: *"fallback: ẩn bộ chỉnh trước khi đè nhau"*): tổng bề rộng ĐO được
     * của các cụm ([groups] — bộ chỉnh, cụm 3 nút kính, nút lấy gió) + khe nhóm [groupGap] giữa chúng ≤ [width]. Đo bề rộng thật
     * (chữ "22°"/"AUTO", phông, mật độ) thay cho ước lượng — [ĐO máy ảo 13/10] ước lượng 222 px cho bộ chỉnh, thật 302 px.
     */
    fun fits(width: Int, groups: List<Int>, groupGap: Int): Boolean =
        groups.sum() + (groups.size - 1).coerceAtLeast(0) * groupGap <= width

    const val LEFT = 1
    const val RIGHT = -1
    /** Ghế nằm ở ~0,5 nửa bề ngang tính từ tâm (Seal: cửa x 0,85 ⇒ ghế x ≈ 0,43). */
    const val SEAT_INWARD = 0.5
    /** Lùi tựa lưng sau tâm cửa trước (m, thế giới). */
    const val SEAT_BACK = 0.22
    /** Tựa lưng thấp hơn tâm kính chừng này (m). */
    const val SEAT_DROP = 0.08
    const val SEAT_UP = 1.2
}
