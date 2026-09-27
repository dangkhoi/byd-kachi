package com.byd.clusternav.launcher.camera

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ R4 · HÌNH "THEO CỤM" — bài canh DÂY NỐI của `:app` (spec `kachi-276-closing.html`, làn L2) ═════════════════
 *
 * Hình học đã test bằng số ở `:core` ([CameraClusterBandTest]). Ở đây canh những mắt xích mà gỡ đi thì **build vẫn
 * xanh và không bài `:core` nào đỏ** (CLAUDE.md §8): mã `CLUSTER` đi từ pref → controller (`shape = shape,` — bài
 * `CameraSpanShapeWiringContractTest`) → overlay, và overlay phải (a) quy về hình THẬT theo display thật, (b) đo
 * `displayMetrics` trên display đang treo, (c) đặt cửa sổ bằng phép của `:core`, (d) dựng lại bằng CÙNG phép ấy khi
 * HAL trả cỡ ảnh.
 */
class CameraClusterBandWiringContractTest {

    private fun app(relative: String): String = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$relative")

    private val overlay by lazy { app("launcher/camera/CameraOverlayView.kt") }
    private val mask by lazy { app("launcher/camera/CameraOverlayMask.kt") }
    private val panoCrop by lazy { SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/launcher/camera/CameraPanoCrop.kt") }
    private val script by lazy { SourceRoots.text("../scripts/emulator/camera-cluster-e2e.sh") }

    /**
     * (a) Hình thật = [CameraClusterBand.effectiveShape] theo **display thật** (`dctx != null`), không theo pref
     * `camera_on_cluster` — CLAUDE.md §5: cờ chỉ để hiển thị, quyết bằng sự thật. Và dòng `val round = …` của 2.73
     * đứng SAU phép quy ấy, nên "theo cụm" trên màn chính đi đúng nhánh chữ nhật.
     */
    @Test fun `overlay quy ve hinh that theo display that`() {
        val body = SourceRoots.body(overlay, "fun show(")
        assertTrue("val dctx = if (onCluster) clusterCtx(ctx) else null" in body, "ngữ cảnh display cụm dựng MỘT lần")
        assertTrue("val cluster = dctx != null" in body, "cụm = có display cụm THẬT, không phải pref")
        assertTrue("val shape = CameraClusterBand.effectiveShape(shape, cluster)" in body, "quy về hình thật ở `:core`")
        val quy = body.indexOf("CameraClusterBand.effectiveShape(shape, cluster)")
        val round = body.indexOf("val round = shape == CameraSignalPolicy.SHAPE_ROUND")
        assertTrue(quy in 0 until round, "phép quy phải đứng TRƯỚC mọi phép so hình")
        assertTrue("val w = wmOf(dctx ?: ctx) ?: return" in body, "WindowManager lấy từ đúng ngữ cảnh display")
        assertTrue("band: ClusterBandSpec = ClusterBandSpec.SEAL_DL3" in overlay, "số dải đến từ hồ sơ; mặc định Seal")
        assertTrue("\"CLUSTER\"" !in overlay, "mã lưu bền không được chép trần — dùng hằng `:core`")
    }

    /** (b)+(c) Đường dải cụm: metrics của display ĐANG treo, dải + chỗ đặt do `:core` tính, toạ độ tuyệt đối. */
    @Test fun `duong dai cum do tren display dang treo va dat bang core`() {
        val geo = SourceRoots.body(overlay, "private fun geometry(")
        assertTrue("if (CameraClusterBand.isCluster(st.shape))" in geo, "rẽ nhánh theo hình THẬT đã quy")
        assertTrue("val dm = st.ctx.resources.displayMetrics" in geo,
            "displayMetrics phải của display đang treo — [ĐO] 27/09: vùng=495 = 0,5 × 990 của màn CHÍNH")
        assertTrue("CameraClusterBand.band(dm.widthPixels, dm.heightPixels, st.band)" in geo, "dải co theo display thật")
        assertTrue("CameraClusterBand.place(" in geo, "chỗ đặt do `:core` tính (có test bằng số)")
        assertTrue("CameraClusterBand.leftEdge(bandRect, st.band, dm.widthPixels)" in geo && "leftEdge = edge," in geo,
            "2.77: mép ngoài CONG cũng co theo display thật rồi đi vào `place` — không có bảng thứ hai ở `:app`")
        assertTrue("CameraClusterBand.rightEdge(bandRect, st.band, dm.widthPixels)" in geo && "rightEdge = edgeRight," in geo,
            "2.78: mép PHẢI đi cùng đường ấy — thiếu dòng này thì bên phải lại là tường thẳng mà không bài nào đỏ")
        assertTrue("CameraClusterBand.outerInkAt(p, p.y)" in geo,
            "dòng log `cong=` phải đọc mép có mực của ĐÚNG bên cửa sổ đứng — [ĐO xe 27/09 tối] `cong=1462/1462/1462` " +
            "là maskLeftAt trên một cửa sổ bên PHẢI, tức ba số vô nghĩa")
        assertTrue("atLeft = st.corner == CameraSignalPolicy.CORNER_TOP_LEFT" in geo, "đầu trái/phải theo pref góc từng bên")
        assertTrue("rotationDeg = if (st.rotationEffective) st.rotationDeg else 0" in geo, "tỉ lệ chỉ lấy xoay khi xoay THẬT")
        assertTrue("return Geo(CameraOverlayFrame.Frame(p.w, p.h, p.streamKnown), bandLayoutParams(p), p.radiusPx, note, p)" in geo,
            "Placement đi tiếp tới `show` — nếu rơi ở đây thì mặt nạ cong thành code chết (CLAUDE.md §8)")
        // Đường 2.73 còn nguyên và đứng sau nhánh cụm (CLAUDE.md §6: đường mới xuống cuối… ở đây là rẽ trước, rơi về cũ).
        assertTrue("val box = box(st.ctx, st)" in geo && "return Geo(f, layoutParams(box, f, st.corner), null" in geo)
        val lp = SourceRoots.body(overlay, "private fun bandLayoutParams(")
        assertTrue("gravity = Gravity.TOP or Gravity.START" in lp && "x = p.x" in lp && "y = p.y" in lp,
            "toạ độ TUYỆT ĐỐI trên display cụm, không lề, không căn giữa vùng")
        assertTrue("TYPE_APPLICATION_OVERLAY" in lp && "FLAG_NOT_TOUCHABLE" in lp, "cùng loại cửa sổ + cờ của 2.73")
        // Bo góc: bán kính hồ sơ ở đường cụm, bán kính khung launcher ở đường cũ — CÙNG một ViewOutlineProvider.
        assertTrue("val radius = (g.radiusPx ?: KachiSpace.dp(ctx, KachiSpace.RADIUS_XL)).toFloat()" in overlay)
        assertEquals(1, Regex("""object : ViewOutlineProvider\(\)""").findAll(overlay).count())
    }

    /**
     * (e) 2.76 L7 (nợ chéo L2): số dải cụm đến từ **HỒ SƠ XE**, không phải hằng `:core`. `ClusterProfile.band` (mặc
     * định Seal DL3, KHÔNG vào `export()`/`parse()` — chuỗi chia sẻ chỉ mang thứ đo tay được), controller đi qua cửa
     * duy nhất `CameraDefaults.band` (cùng cửa với mặc định camera) và đưa vào `overlay.show(band = …)`.
     */
    @Test fun `so dai cum den tu ho so xe qua CameraDefaults, khong phai hang core`() {
        val profile = app("modules/clustercast/ClusterProfile.kt")
        val defaults = app("launcher/camera/CameraDefaults.kt")
        val controller = app("launcher/camera/CameraSignalController.kt")
        // 2.77 — mặc định phải là bản KHÔNG đường cong: bảng `leftEdge` CẮT điểm ảnh theo kính của MỘT đời cụm, nên
        // đời chưa đo phải rơi về tường thẳng 2.76 thay vì bị xén theo kính xe khác (CLAUDE.md §7).
        assertTrue("val band: ClusterBandSpec = ClusterBandSpec.SEAL_DL3_NO_CURVE," in profile,
            "mặc định hồ sơ = dải KHÔNG đường cong; chỉ đời ĐÃ ĐO mới mang bảng leftEdge")
        assertTrue("band = ClusterBandSpec.SEAL_DL3," in profile, "Seal DL3 khai tường minh bộ CÓ đường cong")
        assertTrue("if (id == SEAL_DL3.id) ClusterBandSpec.SEAL_DL3 else ClusterBandSpec.SEAL_DL3_NO_CURVE" in profile,
            "bandFor chọn theo id seed, cùng khuôn với cameraFor")
        assertFalse("band" in SourceRoots.body(profile, "fun export("), "band KHÔNG vào chuỗi export")
        // `parse` KHÔNG đọc dải từ chuỗi (nó chỉ tra theo `id` seed) — khẳng định vẫn là khẳng định cũ, chỉ đổi cách
        // đo: không được có trường nào của chuỗi `f[…]` chảy vào dải, và dải phải đến từ `bandFor(id)`.
        val parseBody = SourceRoots.body(profile, "fun parse(")
        assertTrue("band = bandFor(id)," in parseBody, "dải theo id seed, không theo chuỗi owner dán")
        assertFalse(Regex("""band\s*=\s*[^b\n]*f\[""").containsMatchIn(parseBody), "không trường nào của chuỗi vào dải")
        assertTrue("fun band(ctx: Context): ClusterBandSpec = ClusterProfile.resolveCached(ctx).band" in defaults, "một cửa, có đệm")
        val session = SourceRoots.body(controller, "private fun openSession(")
        assertTrue("band = CameraDefaults.band(appCtx)," in session, "controller đưa dải của HỒ SƠ vào overlay.show")
        assertFalse("ClusterBandSpec.SEAL_DL3" in controller, "controller không được chép hằng `:core`")
        assertFalse("ClusterProfile.resolve" in controller, "không tự resolve hồ sơ ở controller — đi qua CameraDefaults")
    }

    /**
     * (f) [P1 · SOÁT Opus 2026-09-27] Hình **CHỮ NHẬT/TRÒN** trên CỤM cũng phải kẹp vào dải.
     *
     * `camera_shape` mặc định là chữ nhật ([CameraSignalPolicy.defaultShape]) và owner đang bật *Hiện lên cụm*
     * ([ĐO logcat 27/09 10:50:42] `hình=RECT … cluster=true`) ⇒ đường vùng-vuông 2.73 là đường THẬT của xe; lề 6 %
     * chiều cao che 93/360 px dưới thanh trên. Phép kẹp ở `:core` ([CameraClusterBand.boxIn], có test bằng số).
     */
    @Test fun `vung vuong 2 73 tren cum di qua phep kep cua core`() {
        val box = SourceRoots.body(overlay, "private fun box(")
        assertTrue("if (st.onCluster) {" in box, "trên cụm rẽ nhánh riêng — % chiều cao không biết gì về dải")
        assertTrue("CameraClusterBand.boxIn(CameraClusterBand.band(dm.widthPixels, dm.heightPixels, st.band), side, atLeft)" in box,
            "vùng cho phép lấy từ dải của HỒ SƠ, không phải hằng %")
        assertTrue("if (atLeft) area.x0 else (dm.widthPixels - area.x1).coerceAtLeast(0)" in box,
            "`x` của LayoutParams là độ lệch kể từ góc `gravity` ⇒ góc PHẢI lấy khoảng cách tới mép phải display")
        assertFalse("CLUSTER_TOP_RATIO" in overlay,
            "lề 6 % của cụm đã hết chủ; KDoc của nó còn nói sai *\"cụm không có thanh trên\"* (F6 đo tới y ≈ 136)")
        assertTrue("MAIN_TOP_RATIO" in box, "đường màn CHÍNH không đổi một byte")
    }

    /**
     * ═══ 2.77 · MẶT NẠ CONG được DỰNG và được DÙNG ═══════════════════════════════════════════════════════════════
     *
     * Owner 27/09 chiều: *"này nhìn OK, nhưng shape nó không theo cạnh trái cong của cụm"*. Ba mắt xích mà gỡ đi
     * thì build vẫn xanh và không bài `:core` nào đỏ (CLAUDE.md §8): (1) cửa sổ phải là [CameraGlassFrame] chứ
     * không phải `FrameLayout` trần, (2) mặt nạ phải dựng từ chính `Placement` mà `geometry` trả, (3) phép cắt
     * phải là `clipPath` — outline KHÔNG cắt được đường bất kỳ ([ĐO] `Outline.canClip`, KDoc `CameraOverlayMask`).
     */
    @Test fun `mat na cong duoc dung tu Placement va cat bang clipPath`() {
        val body = SourceRoots.body(overlay, "fun show(")
        assertTrue("CameraGlassFrame(ctx, glassMask(g.place, radius), glassFade(g.place))" in body,
            "cửa sổ phải là khung có mặt nạ + dải mờ, cả hai lấy từ `Placement` của `geometry` (không dựng lại hình học)")
        assertTrue("android.widget.FrameLayout(ctx).apply" !in overlay, "không còn khung trần nào bỏ qua mặt nạ")
        val g = SourceRoots.body(mask, "internal fun glassMask(")
        assertTrue("p.leftEdge.size >= 2" in g && "p.rightEdge.size >= 2" in g && "return null" in g,
            "2.78: cắt được CẢ HAI mép; bảng rỗng/1 mẫu ⇒ không mặt nạ ⇒ cửa sổ 2.76")
        assertTrue("addRoundRect(0f, 0f, p.w.toFloat(), p.h.toFloat(), radius, radius" in g && "Path.Op.INTERSECT" in g,
            "GIAO với chữ nhật bo góc ⇒ bán kính vẫn là của hồ sơ, không có bản sao thứ hai của hình cửa sổ")
        assertTrue("band.y0 + band.h * i / (n - 1)" in SourceRoots.body(mask, "private fun halfPlane("),
            "lấy mẫu ĐÚNG các hàng mà `:core` định nghĩa")
        val d = SourceRoots.body(mask, "override fun draw(")
        assertTrue("canvas.clipPath(m)" in d && "canvas.restoreToCount(save)" in d, "cắt bằng clipPath, có save/restore")
        assertFalse("setConvexPath" in mask, "outline không cắt được đường bất kỳ — đừng thử lại")
    }

    /**
     * ═══ 2.78 · Dải MỜ ở mép TRONG được dựng và được DÙNG ════════════════════════════════════════════════════════
     *
     * [ĐO owner 27/09 tối]: *"phần cạnh bên phải thêm tý blur ra ngoài cho nó smooth, ko là 1 vạch thẳng nhìn nó như
     * sẹo, ngược lại cho bên phải cũng thế"*. Ba mắt xích gỡ đi thì build vẫn xanh: (1) bề rộng dải mờ đến từ `:core`
     * (có test bằng số), (2) mép mờ là mép TRONG (`atRight = p.atLeft`), (3) phép mờ là `DST_IN` trên một lớp riêng —
     * thiếu `saveLayer` thì gradient ăn vào nền cụm chứ không vào alpha của cửa sổ.
     */
    @Test fun `dai mo mep trong dung tu core va to bang DST_IN`() {
        val f = SourceRoots.body(mask, "internal fun glassFade(")
        assertTrue("CameraClusterBand.fadePx(p)" in f, "bề rộng do `:core` quyết (co theo display, kẹp ≤ w/4)")
        assertTrue("atRight = p.atLeft" in f, "cửa sổ đứng đầu TRÁI ⇒ mờ ở mép PHẢI (mép quay vào giữa cụm)")
        val d = SourceRoots.body(mask, "override fun draw(")
        assertTrue("canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)" in d,
            "phải có LỚP riêng, nếu không `DST_IN` ăn vào nền cụm phía sau")
        assertTrue("PorterDuff.Mode.DST_IN" in mask && "LinearGradient(" in mask, "mờ bằng alpha, không đổi màu")
        assertTrue("canvas.drawRect(x0, 0f, x0 + px, height.toFloat(), fadePaint)" in d, "dải mờ phủ trọn chiều cao")
    }

    /** (d) HAL trả cỡ ⇒ dựng lại bằng CÙNG [geometry]: hai công thức là cửa sổ nhảy khỏi dải đúng lúc có ảnh. */
    @Test fun `onStreamMeasured dung lai bang cung geometry`() {
        val body = SourceRoots.body(overlay, "fun onStreamMeasured(")
        assertTrue("val g = geometry(st)" in body && "wm?.updateViewLayout(c, g.lp)" in body)
        assertTrue("videoLp(st, g.f)" in body)
        assertEquals(2, Regex("""geometry\(st\)""").findAll(overlay).count(), "đúng hai chỗ gọi: show + onStreamMeasured")
        assertTrue("box(appCtx" !in overlay, "không còn chỗ nào đo vùng trên appCtx bừa — mọi chỗ đi qua st.ctx")
    }

    /** Crop của "theo cụm" quy về chữ nhật NGAY trong `cropFor` (tường minh), không dựa vào `isShape` rơi mặc định. */
    @Test fun `cropFor quy theo cum ve chu nhat tuong minh`() {
        val body = SourceRoots.body(panoCrop, "fun cropFor(")
        assertTrue("val effective = CameraClusterBand.effectiveShape(shape, onCluster = false)" in body)
        assertTrue("if (CameraSignalPolicy.isShape(effective)) effective else CameraSignalPolicy.defaultShape()" in body)
    }

    /**
     * Vòng kiểm máy ảo tồn tại và làm đúng bốn việc: dựng overlay display 1920×720, đặt hình `CLUSTER` + chiếu cụm,
     * ép xi-nhan cả HAI bên, và **chấm** cửa sổ nằm trong dải bằng số (không chỉ chụp ảnh).
     */
    @Test fun `vong kiem may ao dung overlay display va cham bang so`() {
        assertTrue("overlay_display_devices 1920x720/320" in script, "overlay display đúng cỡ cụm Seal")
        assertTrue("pset camera_shape CLUSTER" in script && "pset camera_on_cluster 1" in script)
        assertTrue("one_side left  left" in script && "one_side right right" in script, "phải ép cả hai bên")
        assertTrue("camera --es name \$side" in script, "ép xi-nhan qua cầu kiểm thử `camera`")
        assertTrue("dumpsys window windows" in script, "cỡ/chỗ cửa sổ đọc từ WindowManager (số thật), không đoán từ ảnh")
        assertTrue("overlay_display_devices none" in script, "dọn overlay display khi xong, kể cả khi chết giữa chừng")
        assertTrue("ALLOW_NON_EMULATOR" in script, "từ chối chạy trên xe thật (ghi prefs + ảnh tổng hợp)")
        // [P2 · SOÁT Opus 2026-09-27] Bốn số dải là **bản sao thứ hai** của phép đo, chỉ được nối bằng một lời nhắc
        // trong chú thích. Doc làn §9 đã hẹn có thể nới `bottom` 560 → 566 sau buổi xe: lúc ấy bản sao ở đây đứng lại
        // và dòng chấm điểm của vòng máy ảo (`y1 <= B`) chấm theo dải CŨ. Ghim bằng cách dựng chuỗi TỪ hồ sơ.
        val seal = ClusterBandSpec.SEAL_DL3
        assertTrue("BAND_L=${seal.left}; BAND_T=${seal.top}; BAND_R=${seal.right}; BAND_B=${seal.bottom}" in script,
            "số dải trong script phải khớp ClusterBandSpec.SEAL_DL3 — đổi hồ sơ thì đổi cả script")
        assertTrue("overlay_display_devices ${seal.refW}x${seal.refH}/320" in script,
            "cỡ overlay display phải là cỡ THAM CHIẾU của hồ sơ, nếu không `band()` co giãn và số chấm điểm lệch")
        assertTrue("BAND_EL=\"${seal.leftEdge.joinToString(",")}\"" in script,
            "bảng mép cong trong script phải khớp ClusterBandSpec.SEAL_DL3.leftEdge — chỉnh số ở xe thì script đỏ ngay")
        assertTrue("BAND_ER=\"${seal.rightEdge.joinToString(",")}\"" in script,
            "bảng mép PHẢI (2.78) cũng vậy — nếu không, vòng máy ảo chấm bên phải theo tường thẳng 1780 và luôn ĐẠT")
    }
}
