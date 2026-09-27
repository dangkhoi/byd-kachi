package com.byd.clusternav.launcher.camera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Camera theo xi-nhan ([CameraSignalPolicy]) — luật thuần, off-car. */
class CameraSignalPolicyTest {

    @Test fun `xi nhan trai to camera trai + overlay ben trai`() {
        val t = CameraSignalPolicy.turnOf(left = true, right = false)
        assertEquals(CameraSignalPolicy.Turn.LEFT, t)
        assertEquals(CameraSignalPolicy.CamView.MIRROR_LEFT, CameraSignalPolicy.defaultView(t))
        assertEquals(CameraSignalPolicy.Side.LEFT, CameraSignalPolicy.defaultSide(t))
    }

    @Test fun `xi nhan phai to camera phai + overlay ben phai`() {
        val t = CameraSignalPolicy.turnOf(left = false, right = true)
        assertEquals(CameraSignalPolicy.Turn.RIGHT, t)
        assertEquals(CameraSignalPolicy.CamView.MIRROR_RIGHT, CameraSignalPolicy.defaultView(t))
        assertEquals(CameraSignalPolicy.Side.RIGHT, CameraSignalPolicy.defaultSide(t))
    }

    @Test fun `ca hai bat (den khan) khong mo camera`() {
        val t = CameraSignalPolicy.turnOf(left = true, right = true)
        assertEquals(CameraSignalPolicy.Turn.NONE, t)
        assertNull(CameraSignalPolicy.defaultView(t))
        assertNull(CameraSignalPolicy.defaultSide(t))
    }

    // ══ R7 · XOAY video — 2.71 GÓC THEO BÊN (owner trên xe 2026-09-26: "2 line setting độc lập cho camera trái và
    // phải, có thể 2 camera cần xoay khác nhau") ═══════════════════════════════════════════════════════════════

    private val P = CameraSignalPolicy

    /** Bảng đủ 4 mã × 2 bên: mã là góc TUYỆT ĐỐI, không còn phụ thuộc bên. Âm = ↺, dương = ↻ (chiều `Matrix.postRotate`). */
    @Test fun `bang xoay 4 ma x 2 ben`() {
        listOf(true, false).forEach { left ->
            assertEquals(0, P.rotationDegrees(P.ROTATE_NONE, left), "0 · left=$left")
            assertEquals(-90, P.rotationDegrees(P.ROTATE_LEFT, left), "L90 · left=$left")
            assertEquals(90, P.rotationDegrees(P.ROTATE_RIGHT, left), "R90 · left=$left")
            assertEquals(180, P.rotationDegrees(P.ROTATE_180, left), "180 · left=$left")
        }
    }

    /**
     * Mặc định theo bên = nguyên văn owner 2.67 (trái ↺ −90, phải ↻ +90). Mã lạ — kể cả mã CŨ `SIDE`/`SIDEINV` chưa
     * qua migrate — rơi về mặc định của BÊN, KHÔNG về 0 (0 là lựa chọn thật).
     */
    @Test fun `mac dinh theo ben, ma la roi ve mac dinh cua ben`() {
        assertEquals(P.ROTATE_LEFT, P.defaultRotation(left = true))
        assertEquals(P.ROTATE_RIGHT, P.defaultRotation(left = false))
        assertEquals(-90, P.rotationDegrees("TOPLEFT", left = true))
        assertEquals(90, P.rotationDegrees("", left = false))
        assertEquals(-90, P.rotationDegrees(P.ROTATE_BY_SIDE, left = true))
        assertEquals(90, P.rotationDegrees(P.ROTATE_BY_SIDE_INV, left = false))
    }

    /** `isRotation` nhận đúng 4 mã; hai mã cũ `SIDE`/`SIDEINV` KHÔNG còn là lựa chọn (chỉ migrate đọc). */
    @Test fun `isRotation nhan dung 4 ma, ma cu bi loai`() {
        assertEquals(listOf("0", "L90", "R90", "180"), P.ROTATIONS)
        assertTrue(P.ROTATIONS.all(P::isRotation))
        assertFalse(P.isRotation(P.ROTATE_BY_SIDE))
        assertFalse(P.isRotation(P.ROTATE_BY_SIDE_INV))
        assertFalse(P.isRotation("90"))
        assertFalse(P.isRotation("l90"))   // phân biệt hoa/thường: prefs_set chuẩn hoá trước khi ghi
        assertFalse(P.isRotation(""))
    }

    /**
     * Migrate khoá đơn cũ `camera_rotation` → góc từng bên, đủ 6 mã cũ × 2 bên. Bất biến: **số độ trước và sau
     * nâng cấp bằng nhau** cho mọi mã cũ hợp lệ — xe đang nhìn thấy gì thì sau khi lên 2.71 vẫn thấy đúng thế.
     */
    @Test fun `migrate 6 ma cu x 2 ben giu nguyen so do`() {
        // Bảng độ của mô hình CŨ (2.67–2.70), chép nguyên từ `rotationDegrees(mode, turn)` trước khi gỡ.
        val oldDeg: Map<String, Pair<Int, Int>> = mapOf(   // mã cũ → (trái, phải)
            "SIDE" to (-90 to 90), "SIDEINV" to (90 to -90), "0" to (0 to 0),
            "L90" to (-90 to -90), "R90" to (90 to 90), "180" to (180 to 180),
        )
        oldDeg.forEach { (old, lr) ->
            val l = P.migrateRotation(old, left = true)
            val r = P.migrateRotation(old, left = false)
            assertTrue(P.isRotation(l) && P.isRotation(r), "$old phải migrate ra mã hợp lệ ($l/$r)")
            assertEquals(lr.first, P.rotationDegrees(l, left = true), "$old · trái")
            assertEquals(lr.second, P.rotationDegrees(r, left = false), "$old · phải")
        }
        // Đúng mã, không chỉ đúng độ: SIDE → L90/R90 ; SIDEINV → R90/L90 (đây là lý do 2.69 trên xe ra rot=90 cả
        // hai bên nếu pref cũ là R90 — migrate giữ nguyên, không "sửa hộ").
        assertEquals(P.ROTATE_LEFT, P.migrateRotation(P.ROTATE_BY_SIDE, left = true))
        assertEquals(P.ROTATE_RIGHT, P.migrateRotation(P.ROTATE_BY_SIDE, left = false))
        assertEquals(P.ROTATE_RIGHT, P.migrateRotation(P.ROTATE_BY_SIDE_INV, left = true))
        assertEquals(P.ROTATE_LEFT, P.migrateRotation(P.ROTATE_BY_SIDE_INV, left = false))
        // Mã lạ trên đĩa (prefs sửa tay) → mặc định của bên, không ném.
        assertEquals(P.ROTATE_LEFT, P.migrateRotation("BOGUS", left = true))
        assertEquals(P.ROTATE_RIGHT, P.migrateRotation("", left = false))
    }

    // ══ CLOSE-14 · ĐƯỜNG KẾT XUẤT (`camera_render`) — mặc định KHÔNG được đổi ══════════════════════════════

    /**
     * Mặc định phải là `TextureView` = đường ĐANG CHẠY hiện trường (CLAUDE.md §6: đường mới xuống cuối, không đảo
     * mặc định để chữa cho một thứ còn [CHƯA BIẾT] — hai lượt `gfxinfo` cùng bản 2.70 cho 26,9 % vs 4,67 % giật).
     */
    @Test fun `ket xuat mac dinh la TextureView`() {
        assertEquals(P.RENDER_TEXTURE, P.defaultRender())
        assertEquals(P.RENDER_TEXTURE, P.RENDERS.first(), "chip đầu hàng = mặc định")
        // R8-B: đường `GL` thêm vào **CUỐI**. Ghim cả thứ tự, không chỉ tập: chip đầu hàng là mặc định, và một
        // lượt sắp lại thứ tự sẽ âm thầm đổi thứ owner thấy trước tiên trên màn.
        assertEquals(listOf("TV", "SV", "GL"), P.RENDERS, "mã lưu bền: đổi là mất lựa chọn đã ghi trên xe")
        assertEquals(P.RENDER_GL, P.RENDERS.last(), "đường MỚI luôn xuống cuối (CLAUDE.md §6)")
        assertTrue(P.defaultRender() != P.RENDER_GL, "2.74 KHÔNG được bật nắn mặc định — RE §7 Q13 còn CHƯA BIẾT")
    }

    /** Mã đọc lên từ prefs (sửa tay được qua `prefs_set`): chỉ ba mã là hợp lệ, mọi thứ khác rơi về mặc định. */
    @Test fun `isRender loai ma la, rotatesByMatrix theo duong`() {
        assertTrue(P.isRender(P.RENDER_TEXTURE) && P.isRender(P.RENDER_SURFACE) && P.isRender(P.RENDER_GL))
        listOf("", "tv", "sv", "gl", "TEXTUREVIEW", "L90", "TL").forEach {
            assertFalse(P.isRender(it), "mã lạ \"$it\" không được coi là hợp lệ")
        }
        // Xoay bằng ma trận CHỈ có ở TextureView; mã lạ ⇒ xử như mặc định (⇒ true), không ném.
        assertTrue(P.rotatesByMatrix(P.RENDER_TEXTURE))
        assertFalse(P.rotatesByMatrix(P.RENDER_SURFACE), "SurfaceView không có setTransform ⇒ phải nhờ HAL")
        assertTrue(P.rotatesByMatrix("BOGUS"), "mã lạ = mặc định = TextureView")
    }

    /**
     * ⚠ Bài quan trọng nhất của R8-B ở tầng luật: **`rotatesByMatrix` và `usesTextureView` KHÔNG còn trùng nhau**.
     *
     * Tới 2.73 một hàm trả lời cả hai câu (*"có `setTransform` không"* ≡ *"có phải `TextureView` không"*). Đường `GL`
     * phá điều đó: nó **là** `TextureView` (nên bo góc, hình TRÒN và `getBitmap` vẫn ăn) nhưng **không** xoay bằng ma
     * trận (shader xoay). Dùng lẫn hai phép hỏi ⇒ đường GL rơi vào nhánh `SurfaceView` của tầng vẽ và **không một
     * khung nào hiện ra** trong khi compile vẫn xanh — đúng CLAUDE.md §8.
     */
    @Test fun `GL la TextureView nhung KHONG xoay bang ma tran`() {
        assertTrue(P.usesTextureView(P.RENDER_GL), "GL dựng TextureView ⇒ bo góc + hình TRÒN + getBitmap vẫn ăn")
        assertFalse(P.rotatesByMatrix(P.RENDER_GL), "GL xoay trong shader ⇒ setTransform phải là ma trận đơn vị")
        assertTrue(P.rotatesInShader(P.RENDER_GL))

        assertTrue(P.usesTextureView(P.RENDER_TEXTURE))
        assertFalse(P.rotatesInShader(P.RENDER_TEXTURE), "đường 2.73 không có shader nào")
        assertFalse(P.usesTextureView(P.RENDER_SURFACE))
        assertFalse(P.rotatesInShader(P.RENDER_SURFACE))
        // Mã lạ ⇒ mặc định (TextureView), không ném và không rơi vào nhánh GL.
        assertTrue(P.usesTextureView("BOGUS"))
        assertFalse(P.rotatesInShader("BOGUS"))
    }

    /**
     * `rotationEffective` — cửa DUY NHẤT quyết cửa sổ lấy tỉ lệ ĐÃ xoay hay CHƯA xoay.
     *
     * Nói sai một nhánh ⇒ ảnh nằm trong một khung sai tỉ lệ và bị giãn, **không có lỗi nào được báo** (CLAUDE.md §2).
     * Bốn nhánh: góc 0 (không cần ai làm) · ma trận · shader · HAL nhận.
     */
    @Test fun `rotationEffective dung cho ca bon nhanh`() {
        // Góc 0 ⇒ luôn "đã xoay", kể cả đường không xoay được.
        assertTrue(P.rotationEffective(P.RENDER_SURFACE, rotationDeg = 0, halAccepted = false))
        // TextureView: ma trận luôn làm được.
        assertTrue(P.rotationEffective(P.RENDER_TEXTURE, -90, halAccepted = false))
        // GL: shader làm — KHÔNG phụ thuộc HAL.
        assertTrue(P.rotationEffective(P.RENDER_GL, -90, halAccepted = false))
        // SurfaceView: chỉ khi HAL NHẬN. Không nhận ⇒ cửa sổ phải lấy tỉ lệ chưa xoay.
        assertFalse(P.rotationEffective(P.RENDER_SURFACE, -90, halAccepted = false))
        assertTrue(P.rotationEffective(P.RENDER_SURFACE, -90, halAccepted = true))
    }

    // ══ CAM-ROT-2 · GỢI Ý cỡ ảnh nguồn — chỉ cho hai view GƯƠNG (chỗ có bằng chứng RE) ═════════════════════

    /**
     * Hai view GƯƠNG có gợi ý 5120×960 (ảnh 4-in-1, [ĐO RE kinex `Y0/C0094o.java:318,342,347`]) vì chính crop của
     * chúng đã giả định ảnh nguồn là 4-in-1; các view khác **không** có gợi ý nào ⇒ cửa sổ giữ ô vuông 2.72 tới khi
     * `AVMCamera.getPreviewWidth/Height` đo được. Không đoán tỉ lệ cho thứ chưa có bằng chứng (CLAUDE.md §2).
     */
    @Test fun `goi y co anh nguon chi co o hai view guong`() {
        val mirrors = listOf(CameraSignalPolicy.CamView.MIRROR_LEFT, CameraSignalPolicy.CamView.MIRROR_RIGHT)
        mirrors.forEach {
            assertEquals(5120, it.hintW, "${it.name} gợi ý bề rộng ảnh 4-in-1")
            assertEquals(960, it.hintH, "${it.name} gợi ý bề cao ảnh 4-in-1")
            assertEquals(1.875f, gioiHanTiLe(it), 0.01f, "${it.name}: dải gương sau xoay ±90 là NGANG 1,875:1")
        }
        CameraSignalPolicy.CamView.entries.filterNot { it in mirrors }.forEach {
            assertEquals(0, it.hintW, "${it.name} chưa có bằng chứng cỡ ⇒ phải để 0")
            assertEquals(0, it.hintH, "${it.name} chưa có bằng chứng cỡ ⇒ phải để 0")
        }
    }

    /** Tỉ lệ (rộng/cao) của cửa sổ đúng tỉ lệ cho view [v] khi xoay ±90, trong một vùng vuông. */
    private fun gioiHanTiLe(v: CameraSignalPolicy.CamView): Float {
        val f = CameraOverlayFrame.fit(v.hintW, v.hintH, v.crop, 90, 1000, 1000)
        return f.w.toFloat() / f.h
    }

    // ══ R8-A · BỀ RỘNG · HÌNH KHUNG · KÊNH HAL — mọi mặc định phải = hành vi 2.73 ══════════════════════════

    /**
     * Bốn mặc định mới, một bài: chúng là toàn bộ lời hứa *"xe sáng mai thấy đúng khung của 2.73 nếu không ai chạm
     * Cài đặt"* (CLAUDE.md §6). Hình học đã ghim ở [CameraPanoCropTest]; đây ghim **mã** + thứ tự chip.
     */
    @Test fun `mac dinh be rong, hinh khung, dai = hanh vi 2 73`() {
        assertEquals(P.SPAN_NARROW, P.defaultSpan())
        assertEquals(P.SPAN_NARROW, P.SPANS.first(), "chip đầu hàng = mặc định")
        assertEquals(listOf("NARROW", "STRIP"), P.SPANS, "mã lưu bền: đổi là mất lựa chọn đã ghi trên xe")
        assertEquals(P.SHAPE_RECT, P.defaultShape())
        assertEquals(listOf("RECT", "ROUND", "CLUSTER"), P.SHAPES, "2.76: ô CLUSTER (theo cụm, làn L2) đứng CUỐI — mã mới không leo lên trước")
        assertEquals(1, CameraPanoCrop.defaultStrip(left = true), "dải chứa vệt TRÁI của 2.73")
        assertEquals(2, CameraPanoCrop.defaultStrip(left = false), "dải chứa vệt PHẢI của 2.73")
        assertEquals(100, P.CIRCLE_PCT_DEFAULT)
    }

    /** Mã/chỉ số lạ (prefs sửa tay qua `prefs_set`) bị loại — chỗ đọc rơi về mặc định, không ném, không hình thứ ba. */
    @Test fun `loai ma la cho be rong, hinh khung, dai`() {
        listOf("", "narrow", "FULL", "TL", "L90").forEach { assertFalse(P.isSpan(it), "bề rộng lạ \"$it\"") }
        listOf("", "rect", "CIRCLE", "OVAL").forEach { assertFalse(P.isShape(it), "hình khung lạ \"$it\"") }
        assertTrue(P.SPANS.all { P.isSpan(it) } && P.SHAPES.all { P.isShape(it) })
        listOf(-1, 4, 99).forEach { assertFalse(CameraPanoCrop.isStrip(it), "dải $it không tồn tại") }
        assertTrue(CameraPanoCrop.STRIPS_ALL.all { CameraPanoCrop.isStrip(it) })
        assertEquals(listOf(0, 1, 2, 3), CameraPanoCrop.STRIPS_ALL)
    }

    /**
     * `vehicle.config.cam_sort` — phân tích **chuỗi thật của xe** (carlog 2026-09-14 `10-logcat-baseline.txt:1776`).
     *
     * Đây là chỗ khoá lại sự thật đã sửa trong KDoc: `rear` = id 0, `pano_h` = id 1 — tức id 1 mới là khung ghép
     * 4-in-1. Chuỗi rác không được ném (nó là dữ liệu của ROM, đời xe khác có thể khác dấu phân cách).
     */
    @Test fun `cam sort cua xe nay ra rear 0 va pano_h 1`() {
        val raw = "rear:0;pano_h:1;"
        assertEquals(mapOf("rear" to 0, "pano_h" to 1), P.camSortIds(raw))
        assertEquals(0, P.camSortId(raw, P.CAM_TAG_REAR))
        assertEquals(1, P.camSortId(raw, P.CAM_TAG_PANO), "pano_h = LUỒNG ghép 4 camera fisheye = cameraId của hai view GƯƠNG")
        assertEquals(1, CameraSignalPolicy.CamView.MIRROR_LEFT.cameraId, "enum phải khớp cam_sort")
        assertEquals(1, CameraSignalPolicy.CamView.MIRROR_RIGHT.cameraId)
        // Rác / rỗng / thiếu tag ⇒ map rỗng hoặc bỏ mảnh xấu, KHÔNG ném.
        assertEquals(emptyMap<String, Int>(), P.camSortIds(""))
        assertEquals(emptyMap<String, Int>(), P.camSortIds("khong-co-dau-hai-cham"))
        assertEquals(mapOf("rear" to 0), P.camSortIds("rear:0;pano_h:x;"))
        assertNull(P.camSortId("rear:0;", P.CAM_TAG_PANO), "trim không có pano ⇒ null, không phải 0")
    }

    /** outputState là giá trị THẬT của HAL (RE) — ghim để không đổi bừa. */
    @Test fun `output state khop hang HAL`() {
        assertEquals(1, CameraSignalPolicy.CamView.FRONT_LEFT.outputState)
        assertEquals(2, CameraSignalPolicy.CamView.FRONT_RIGHT.outputState)
        assertEquals(13, CameraSignalPolicy.CamView.LEFT_FRONT.outputState)
        assertEquals(14, CameraSignalPolicy.CamView.RIGHT_FRONT.outputState)
    }
}
