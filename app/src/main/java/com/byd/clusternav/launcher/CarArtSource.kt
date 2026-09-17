package com.byd.clusternav.launcher

import android.graphics.Path
import android.graphics.RectF

/**
 * ═══ VISUAL-REFRESH P3 · §4.8 (2) — HỢP ĐỒNG NGUỒN ẢNH của hình xe ═════════════════════════════════════════════
 *
 * Tầng vẽ ([CarArtPainter] và ba bảng Canvas) hỏi **một giao diện**, không hỏi "vector hay ảnh". Hôm nay chỉ có
 * [VectorCarArt] (mức tả thực (1), đọc [CarFrames]); `RasterCarArt` (mức (2), ảnh render sẵn) **không viết** — giao
 * diện chỉ đỡ sẵn để cắm vào sau mà `CarPartStyle` (`:core`) và các bảng **không sửa một dòng** (AC1.6,
 * `CarArtSourceContractTest` chứng minh bằng một nguồn giả).
 *
 * Ràng buộc: [bounds] trả **hộp bao theo hệ 24×24** cho mọi bộ phận — y hệt [CarFrames] — nhờ vậy chỗ đặt nhãn `%`,
 * số áp suất, phép `Matrix.setRectToRect(…, CENTER)` không phụ thuộc nguồn ảnh.
 */
internal interface CarArtSource {
    /** Mảnh của một mặt, đúng thứ tự vẽ (lớp dưới trước), **không** gồm ký hiệu icon (`glyph`). */
    fun parts(face: CarFace): List<CarArtPart>

    /** Path dùng chung, CHỈ ĐỌC (chép sang `Path` riêng trước khi biến hình). `null` = không có. */
    fun path(face: CarFace, id: String): Path?

    /** Hộp bao hệ 24×24; `false` = không có mảnh. */
    fun bounds(face: CarFace, id: String, out: RectF): Boolean

    /** Hộp bao THÂN (phóng vào ô để bánh/vạt cửa thò ra ngoài). */
    fun frameBounds(face: CarFace, out: RectF)

    /** Hộp bao THÂN + bánh + mọi bộ phận mở được. */
    fun openFrameBounds(face: CarFace, out: RectF)

    /** Mảnh mà lớp highlight (§4.3 lớp 6) vẽ viền lên — hôm nay là thân. */
    fun highlightRef(face: CarFace): String
}

/** Một mảnh nhìn từ tầng vẽ: tên · vai màu (đã dịch sang `:core`) · tô even-odd · chỉ nét. */
internal class CarArtPart(val id: String, val role: CarPartRole, val evenOdd: Boolean, val strokeOnly: Boolean)

/** Nguồn VECTOR — mức tả thực (1): đọc [CarFrames]/[CarFramesGenerated]. */
internal object VectorCarArt : CarArtSource {

    private val partsByFace: Map<CarFace, List<CarArtPart>> by lazy {
        CarFace.values().associateWith { face ->
            CarFrames.pieces(face).filter { !it.glyph }.map { p ->
                // Vai lạ trong SVG ⇒ ném NGAY lúc nạp (bài canh ở `:app` cũng đỏ), không rơi vào một vai mặc định.
                val role = CarPartRole.of(p.role) ?: error("CarFramesGenerated: vai màu lạ '${p.role}' ở ${p.face}/${p.id}")
                CarArtPart(p.id, role, p.evenOdd, p.strokeOnly)
            }
        }
    }

    override fun parts(face: CarFace): List<CarArtPart> = partsByFace.getValue(face)
    override fun path(face: CarFace, id: String): Path? = CarFrames.path(face, id)
    override fun bounds(face: CarFace, id: String, out: RectF): Boolean = CarFrames.bounds(face, id, out)
    override fun frameBounds(face: CarFace, out: RectF) = CarFrames.frameBounds(face, out)
    override fun openFrameBounds(face: CarFace, out: RectF) = CarFrames.openFrameBounds(face, out)
    override fun highlightRef(face: CarFace): String =
        CarFramesGenerated.HIGHLIGHT_REF[face.name.lowercase()] ?: CarFrames.BODY
}
