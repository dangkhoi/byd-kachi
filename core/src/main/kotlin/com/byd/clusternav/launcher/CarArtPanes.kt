package com.byd.clusternav.launcher

/**
 * ═══ 3.01 R22·R — MẶT NẠ KÍNH HÔNG đăng ký trên ảnh xe MẶC ĐỊNH (thuần `:core`) ════════════════════════════════════════
 *
 * Owner 13/10: *"mấy cái miếng xanh xanh đó xấu và thô"* (thanh xanh dán lệch ~15 px khỏi kính thật) rồi *"hình nó hơi
 * thô … làm visual thật hơn, hiện đại hơn"* (xe nét mảnh bị chê trơ khung). Chốt (phương án A của orchestrator): giữ ảnh
 * xe top-view chụp-thật đóng gói (`assets/car/default-car.png`, 678×1397, owner chọn ở WP3-v5) ở cỡ LỚN, và vẽ trạng thái
 * kính lên ĐÚNG hình kính hông của chính ảnh đó — mảnh kính "hạ" để lộ khoang tối, không tô màu phẳng.
 *
 * Toạ độ dưới đây ĐO MỘT LẦN trên ảnh gốc [ĐO 13/10, lưới phóng 3× quanh dải kính trái]: kính hông = dải tối hình thấu
 * kính giữa nếp gờ cửa (x ≈ 108–110 px) và mép trong thanh nóc bạc (x ≈ 131–137 px); trụ B ở y ≈ 685–728; kính trước
 * từ y ≈ 458, kính sau tới y ≈ 1062. Ảnh đối xứng quanh x = 338,5 [ĐO mép đục 4 hàng] ⇒ bên phải = soi gương.
 * Chuẩn hoá theo CẢ khung ảnh (kể cả lề trong suốt) — cùng hệ với khung letterbox `CarImageLayer.contentRect`.
 *
 * Chỉ đúng cho ảnh MẶC ĐỊNH ⇒ bảng kính luôn vẽ ảnh mặc định (ảnh riêng người dùng không có toạ độ kính); bảng lốp/cửa
 * vẫn để ảnh riêng thắng. Đổi ảnh mặc định = đo lại bảng này (test `CarArtPanesTest` khoá hình học cơ bản).
 *
 * ## MÓC cho ảnh render chất lượng cao sau này (owner 13/10: hình xe sẽ chốt riêng — render 3D dựng sẵn, KHÔNG vẽ tay)
 * Ảnh mới thả vào cùng chỗ (`CarImageStore.DEFAULT_ASSET`) + thay [ART_W]/[ART_H] + 2 đa giác trái dưới đây (đo một lần
 * trên ảnh gốc, px) — bảng kính (`CarLevelsLayer`) tự dùng: khung theo [ASPECT], chấm + nhãn theo [paneCenter]. Không mã vẽ
 * nào phải đổi. Ảnh không đối xứng ⇒ thêm 2 đa giác phải thay cho phép soi gương.
 * Nhiều ảnh (theo đời xe) ⇒ biến object này thành bảng `art → masks`, khoá theo tên asset.
 *
 * 3.01 R23: số đo ảnh mặc định chuyển sang [CarArtCatalog.DEFAULT]; mọi hàm nhận thêm [CarArt] (ảnh BYD chính hãng có hình
 * học riêng, khoá theo tên ảnh). Mặc định tham số = ảnh mặc định ⇒ chỗ gọi cũ giữ nguyên nghĩa.
 *
 * 3.01 DOT (owner 13/10 *"vẽ chấm tròn thôi, như bọn kinex, kimlauncher"*): lớp "kính hạ" (ranh `fillTo` + khoang tối)
 * đã GỠ — trạng thái nay là một chấm tại [paneCenter] ([CarDots]).
 */
object CarArtPanes {

    /** Cỡ ảnh mặc định (px) — hệ đo của các đa giác [CarArtCatalog.DEFAULT]. */
    const val ART_W = 678f
    const val ART_H = 1397f

    /** Rộng : cao của ảnh mặc định. */
    const val ASPECT = ART_W / ART_H

    /**
     * Mảnh kính hông của cửa [p] (chuẩn hoá 0..1 theo khung ảnh [art]), `null` nếu không phải cửa / ảnh chưa đo. Thứ tự
     * điểm theo chiều kim đồng hồ quanh mảnh (ảnh mặc định: bắt đầu ở đỉnh trước).
     */
    fun pane(p: CarPart, art: CarArt = CarArtCatalog.DEFAULT): List<CarAnchor>? = art.pane(p)

    /** Tâm hộp bao mảnh kính — tâm chấm trạng thái ([CarDots.center]). */
    fun paneCenter(p: CarPart, art: CarArt = CarArtCatalog.DEFAULT): CarAnchor? = pane(p, art)?.let { q ->
        CarAnchor((q.minOf { it.x } + q.maxOf { it.x }) / 2f, (q.minOf { it.y } + q.maxOf { it.y }) / 2f)
    }
}
