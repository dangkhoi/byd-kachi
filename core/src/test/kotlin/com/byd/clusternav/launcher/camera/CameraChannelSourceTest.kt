package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.camera.CameraSignalPolicy.CamView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.75 — nguồn **MỘT KÊNH** camera (khung bị kéo ngang ×4), kiểm bằng số off-car ═══════════════════════════
 *
 * [ĐO xe 27/09 11:16] `addPreviewSurface(surface, halMode)` với `halMode ∈ 1..4` trả `rc = true` và buffer
 * `5120×960` chứa **trọn khung fisheye của MỘT camera**, căng ngang cho đầy — ảnh thật `1280×960`. Bản đồ kênh
 * Seal: `2` = gương trái · `3` = gương phải (owner xác nhận qua cột E4/E3). Đây là câu trả lời cho RE §7 Q3/D7.
 *
 * **Cái bẫy duy nhất, và là lý do bài này tồn tại**: mọi tầng hình học phải đo trên bề ngang **NỘI DUNG**
 * (`5120/4 = 1280`), không phải bề ngang **buffer**. Lấy nhầm `5120` ⇒ `aspect = 5,33` ⇒ đồng-θ thành ellipse dẹt,
 * `K` suy ra nhỏ đi 4 lần, cửa sổ sai tỉ lệ — ba thứ sai cùng lúc mà ảnh vẫn "ra hình", tức không ai nghi.
 */
class CameraChannelSourceTest {

    private val bufferW = 5120
    private val bufferH = 960

    /** Bề ngang nội dung = buffer / số dải — và hệ số ấy là [CameraPanoCrop.STRIPS], không phải hằng `4` rời. */
    @Test fun `be ngang noi dung bang buffer chia so dai`() {
        assertEquals(1280, CameraPanoCrop.contentWidth(bufferW, channel = true))
        assertEquals(bufferW, CameraPanoCrop.contentWidth(bufferW, channel = false), "PANO ⇒ giữ nguyên")
        assertEquals(bufferW / CameraPanoCrop.STRIPS, CameraPanoCrop.contentWidth(bufferW, true))
        // Chưa đo được cỡ luồng ⇒ trả nguyên (0), không sinh ra một số bịa.
        assertEquals(0, CameraPanoCrop.contentWidth(0, channel = true))
    }

    /** Mã nguồn: mặc định = đường đang chạy; mã lạ ⇒ mặc định, không ném. */
    @Test fun `ma nguon la mot tap dong, mac dinh la khung ghep`() {
        assertEquals(CameraSignalPolicy.SOURCE_PANO, CameraSignalPolicy.defaultSource())
        assertTrue(CameraSignalPolicy.isSource(CameraSignalPolicy.SOURCE_CHANNEL))
        assertFalse(CameraSignalPolicy.isSource("channel"), "mã là ASCII HOA, giống mọi mã khác")
        assertFalse(CameraSignalPolicy.usesChannel("lạ"), "mã lạ ⇒ rơi về mặc định = khung ghép")
        assertTrue(CameraSignalPolicy.usesChannel(CameraSignalPolicy.SOURCE_CHANNEL))
        assertEquals(listOf("PANO", "CHANNEL"), CameraSignalPolicy.SOURCES)
    }

    /** Kênh THẬT: PANO giữ pref cũ · CHANNEL lấy kênh của view · owner đè được bằng pref. */
    @Test fun `kenh that theo view, owner de duoc bang pref`() {
        val auto = CameraSignalPolicy.HAL_MODE_AUTO
        // PANO ⇒ đúng pref cũ, không đụng gì (đường 2.73).
        assertEquals(auto, CameraSignalPolicy.channelFor(CameraSignalPolicy.SOURCE_PANO, auto, profileChannel = 2))
        assertEquals(0, CameraSignalPolicy.channelFor(CameraSignalPolicy.SOURCE_PANO, 0, profileChannel = 2))
        // CHANNEL ⇒ kênh của HỒ SƠ XE (per-side; 2.76: không còn ghim trên CamView).
        assertEquals(2, CameraSignalPolicy.channelFor(CameraSignalPolicy.SOURCE_CHANNEL, auto, profileChannel = 2))
        assertEquals(3, CameraSignalPolicy.channelFor(CameraSignalPolicy.SOURCE_CHANNEL, auto, profileChannel = 3))
        // …trừ khi owner đè bằng `camera_hal_mode` = một kênh thật.
        assertEquals(4, CameraSignalPolicy.channelFor(CameraSignalPolicy.SOURCE_CHANNEL, 4, profileChannel = 2))
        // Kênh 0 = VIEW_DEFAULT (khung ghép) ⇒ KHÔNG phải một kênh đơn ⇒ vẫn lấy kênh của view.
        assertEquals(2, CameraSignalPolicy.channelFor(CameraSignalPolicy.SOURCE_CHANNEL, 0, profileChannel = 2))
        // Hồ sơ chưa đo kênh ⇒ AUTO, không bịa một kênh nào — và phiên KHÔNG được coi là "một kênh".
        assertEquals(auto, CameraSignalPolicy.channelFor(CameraSignalPolicy.SOURCE_CHANNEL, auto, profileChannel = 0))
        assertFalse(CameraSignalPolicy.channelActive(CameraSignalPolicy.SOURCE_CHANNEL, auto),
            "CHANNEL mà kênh hợp ra AUTO ⇒ HAL trả khung ghép ⇒ tầng vẽ KHÔNG được chia bề ngang cho 4")
        assertTrue(CameraSignalPolicy.channelActive(CameraSignalPolicy.SOURCE_CHANNEL, 2))
        assertFalse(CameraSignalPolicy.channelActive(CameraSignalPolicy.SOURCE_PANO, 2), "PANO thì kênh nào cũng không là 'một kênh'")
    }

    /**
     * 2.76 (R2, [P3] review Pass 2): bản đồ kênh Seal **không còn** trên `CamView` — số đo của một xe không được ghim
     * vào enum dùng chung. Nó nằm ở hồ sơ xe (`ClusterProfile.SEAL_DL3_CAMERA`, bài `ClusterProfileCameraDefaultsTest`);
     * `:core` chỉ biết cách HỎI hồ sơ, và hồ sơ trung tính trả "chưa đo".
     */
    @Test fun `kenh khong con ghim tren CamView, ho so trung tinh chua do`() {
        val d = CameraProfileDefaults.NEUTRAL
        assertEquals(CameraProfileDefaults.CHANNEL_UNKNOWN, d.channel(left = true))
        assertEquals(CameraProfileDefaults.CHANNEL_UNKNOWN, d.channel(left = false))
        assertFalse(d.hasChannelMap, "chưa đo ⇒ Cài đặt ẨN hàng Nguồn, không bày một chip nói dối")
        val seal = CameraProfileDefaults(channelLeft = 2, channelRight = 3)
        assertTrue(seal.hasChannelMap)
        assertEquals(2, CameraSignalPolicy.channelFor(CameraSignalPolicy.SOURCE_CHANNEL, CameraSignalPolicy.HAL_MODE_AUTO, seal.channel(left = true)))
        assertEquals(3, CameraSignalPolicy.channelFor(CameraSignalPolicy.SOURCE_CHANNEL, CameraSignalPolicy.HAL_MODE_AUTO, seal.channel(left = false)))
        // Ghi chú `camera_frame`: kênh đơn ⇒ anamorphic ×STRIPS; khung ghép ⇒ rỗng.
        assertEquals("anamorphic x4", CameraSignalPolicy.frameNote(true))
        assertEquals("", CameraSignalPolicy.frameNote(false))
    }

    /** MỘT KÊNH ⇒ **không cắt dải nào**: crop là nguyên buffer (hình chữ nhật). */
    @Test fun `mot kenh thi khong cat dai nao`() {
        for (span in CameraSignalPolicy.SPANS) for (strip in CameraPanoCrop.STRIPS_ALL) {
            assertNull(
                CameraPanoCrop.cropFor(
                    view = CamView.MIRROR_LEFT, left = true, strip = strip, span = span,
                    shape = CameraSignalPolicy.SHAPE_RECT,
                    circlePct = CameraSignalPolicy.CIRCLE_PCT_DEFAULT, channel = true,
                ),
                "kênh đơn: dải/bề rộng không còn nghĩa ($span/$strip)",
            )
        }
    }

    /** Hình TRÒN ở kênh đơn vẫn cắt VUÔNG — nhưng vuông theo pixel **nội dung**, không theo pixel buffer. */
    @Test fun `hinh tron o kenh don cat vuong theo pixel NOI DUNG`() {
        val crop = CameraPanoCrop.cropFor(
            view = CamView.MIRROR_LEFT, left = true, strip = 1, span = CameraSignalPolicy.SPAN_STRIP,
            shape = CameraSignalPolicy.SHAPE_ROUND, circlePct = 100, channel = true,
        )
        assertNotNull(crop)
        // Cạnh = 960 px nội dung ⇒ 960/1280 = 0,75 bề ngang chuẩn hoá, giữa buffer.
        assertEquals(0.125f, crop!![0], 1e-5f)
        assertEquals(0.875f, crop[2], 1e-5f)
        assertEquals(0f, crop[1], 1e-5f)
        assertEquals(1f, crop[3], 1e-5f)
        // Và ô ấy VUÔNG theo pixel nội dung: (0,875−0,125)·1280 = 960 = bề cao.
        val cw = CameraPanoCrop.contentWidth(bufferW, true)
        assertEquals(bufferH.toDouble(), ((crop[2] - crop[0]) * cw).toDouble(), 0.5)
    }

    /** Quang tâm ở kênh đơn = **tâm buffer**, không phải tâm dải — và đây là ca KHÔNG có giả định [SUY] nào. */
    @Test fun `quang tam o kenh don la tam khung`() {
        val c = CameraGlUniforms.sourceCentre(CamView.MIRROR_LEFT, strip = 1, channel = true)
        assertEquals(0.5f, c[0], 1e-6f)
        assertEquals(0.5f, c[1], 1e-6f)
        // PANO thì vẫn là tâm DẢI (0,375) — hai chế độ không được lẫn nhau.
        assertEquals(0.375f, CameraGlUniforms.sourceCentre(CamView.MIRROR_LEFT, strip = 1)[0], 1e-6f)
    }

    /**
     * ⚠ **Bài đắt nhất của R9**: bộ uniform ở kênh đơn phải cho **cùng** `aspect`/`K`/`F`/tâm với trọn dải `STRIP`.
     *
     * Vì sao chúng phải bằng nhau: cả hai đều mô tả **một khung camera `1280×960`** — một cái cắt ra từ ảnh ghép,
     * một cái do HAL đưa thẳng. Nếu bộ số lệch thì bộ `F 55 % · K 100 % · S 130 %` owner đã duyệt trên xe sẽ
     * **không còn đúng** khi đổi chip *Nguồn*, và owner sẽ đi chỉnh lại từ đầu mà không hiểu vì sao.
     */
    @Test fun `bo uniform kenh don trung bo uniform tron dai`() {
        val strip = CameraGlUniforms.of(
            crop = CameraPanoCrop.stripCrop(1),
            srcCentreX = CameraPanoCrop.stripCentre(1).toFloat(), srcCentreY = 0.5f,
            streamW = bufferW, streamH = bufferH, rotationDeg = -90,
        )
        val channel = CameraGlUniforms.of(
            crop = null, srcCentreX = 0.5f, srcCentreY = 0.5f,
            streamW = CameraPanoCrop.contentWidth(bufferW, true), streamH = bufferH, rotationDeg = -90,
        )
        assertEquals(strip.aspect, channel.aspect, 1e-6f, "cùng một khung 1280×960 ⇒ cùng aspect 4:3")
        assertEquals(1280f / 960f, channel.aspect, 1e-6f)
        assertEquals(strip.dewarp.k, channel.dewarp.k, 1e-6f, "K suy ra phải trùng ⇒ núm % của owner vẫn đúng")
        assertEquals(strip.dewarp.focal, channel.dewarp.focal, 1e-6f)
        assertEquals(strip.centerX, channel.centerX, 1e-6f)
        assertEquals(strip.centerY, channel.centerY, 1e-6f)
        // Khác nhau đúng MỘT thứ: vùng texture — kênh đơn lấy trọn buffer.
        assertEquals(0f, channel.srcRect[0], 1e-6f)
        assertEquals(1f, channel.srcRect[2], 1e-6f)
        assertEquals(0.25f, strip.srcRect[2], 1e-6f)
    }

    /** Lấy nhầm bề ngang BUFFER là hỏng cả ba thứ cùng lúc — bài ghim đúng cái sai ấy để nó không im lặng. */
    @Test fun `lay nham be ngang buffer thi aspect va K deu sai`() {
        val sai = CameraGlUniforms.of(
            crop = null, srcCentreX = 0.5f, srcCentreY = 0.5f,
            streamW = bufferW, streamH = bufferH, rotationDeg = 0,
        )
        assertEquals(bufferW.toFloat() / bufferH, sai.aspect, 1e-4f, "5,33 — đồng-θ thành ellipse dẹt")
        val dung = CameraGlUniforms.of(
            crop = null, srcCentreX = 0.5f, srcCentreY = 0.5f,
            streamW = CameraPanoCrop.contentWidth(bufferW, true), streamH = bufferH, rotationDeg = 0,
        )
        assertEquals(CameraPanoCrop.STRIPS.toDouble(), (dung.dewarp.k / sai.dewarp.k).toDouble(), 1e-3,
            "K lệch đúng STRIPS lần — đủ lớn để ảnh sai hẳn, đủ 'ra hình' để không ai nghi")
    }

    /**
     * Trần nhịp vẽ (CAM-B4): số ms giữa hai lượt vẽ, và vì sao lấy **3/4** chu kỳ chứ không trọn chu kỳ.
     *
     * [ĐO xe 27/09] HAL đẩy ~34 fps (chu kỳ ~29,4 ms) dù Kachi xin `setCameraFps(15)`. Ngưỡng trọn chu kỳ
     * (66 ms) rơi vào **giữa** hai khung ⇒ lượt vẽ trượt sang khung thứ ba ⇒ chỉ còn ~11 fps. Ngưỡng 3/4 (50 ms)
     * rơi trước khung thứ hai ⇒ nhịp thật ~17 fps, **sát dưới** trần.
     */
    @Test fun `tran nhip ve lay ba phan tu chu ky`() {
        assertEquals(50L, CameraSignalPolicy.renderMinGapMs(15))
        assertEquals(15, CameraSignalPolicy.RENDER_FPS_CAP)
        assertEquals(0L, CameraSignalPolicy.renderMinGapMs(0), "trần <= 0 ⇒ không chặn gì (đường 2.74)")
        assertEquals(0L, CameraSignalPolicy.renderMinGapMs(-5))
        assertTrue(CameraSignalPolicy.renderMinGapMs(15) < 1000L / 15, "phải NHỎ hơn trọn chu kỳ")
        // Mô phỏng: khung tới đều 29,4 ms (34 fps) ⇒ nhịp vẽ thật phải nằm trong [trần/1,5, trần·1,2].
        val gap = CameraSignalPolicy.renderMinGapMs()
        var last = 0L
        var painted = 0
        var t = 0L
        while (t <= 10_000L) {
            if (last == 0L || t - last >= gap) { last = t; painted++ }
            t += 29L
        }
        val fps = painted / 10.0
        assertTrue(fps in 10.0..CameraSignalPolicy.RENDER_FPS_CAP + 3.0, "nhịp thật $fps fps phải sát trần")
    }

    /**
     * Và **16 tổ hợp** ở chế độ kênh đơn: xoay ±90/180 vẫn đúng bằng ảnh `rot 0` đã xoay.
     *
     * Cùng ma trận với `CameraGlUniformsTest`, nhưng nguồn là kênh đơn — nơi `uSrcRect` trọn buffer và `aspect`
     * đến từ cỡ nội dung. Đây là chỗ một lỗi *"quên đổi sang cỡ nội dung ở một trong ba tầng"* lộ ra.
     */
    @Test fun `kenh don, 16 to hop xoay van la anh rot 0 da xoay`() {
        var checked = 0
        for (shape in CameraSignalPolicy.SHAPES) for (pct in listOf(60, 100)) {
            val crop = CameraPanoCrop.cropFor(
                view = CamView.MIRROR_LEFT, left = true, strip = 1, span = CameraSignalPolicy.SPAN_STRIP,
                shape = shape, circlePct = pct, channel = true,
            )
            for (rot in listOf(0, -90, 90, 180)) {
                val u = CameraGlUniforms.of(
                    crop = crop, srcCentreX = 0.5f, srcCentreY = 0.5f,
                    streamW = CameraPanoCrop.contentWidth(bufferW, true), streamH = bufferH, rotationDeg = rot,
                )
                val moc = CameraGlUniforms.of(
                    crop = crop, srcCentreX = 0.5f, srcCentreY = 0.5f,
                    streamW = CameraPanoCrop.contentWidth(bufferW, true), streamH = bufferH, rotationDeg = 0,
                )
                assertEquals(moc.aspect, u.aspect, 1e-6f, "$shape/$pct/$rot°: xoay không được đổi uAspect")
                listOf(0.02f to 0.02f, 0.98f to 0.02f, 0.02f to 0.98f, 0.98f to 0.98f, 0.5f to 0.5f)
                    .forEach { (uo, vo) ->
                        val got = CameraDewarp.sample(uo, vo, rot, u.dewarp, u.aspect, u.srcRect)
                        val (ru, rv) = CameraDewarp.rotateDstToLocal(uo, vo, rot)
                        val want = CameraDewarp.sample(ru, rv, 0, moc.dewarp, moc.aspect, moc.srcRect)
                        val tag = "$shape/$pct/$rot° ($uo,$vo)"
                        assertEquals(want == null, got == null, "$tag: một bên đen một bên không")
                        if (want != null) {
                            assertEquals(want.first.toDouble(), got!!.first.toDouble(), 1e-4, "$tag trục x")
                            assertEquals(want.second.toDouble(), got.second.toDouble(), 1e-4, "$tag trục y")
                        }
                        checked++
                    }
            }
        }
        assertEquals(CameraSignalPolicy.SHAPES.size * 2 * 4 * 5, checked, "phải đi hết mọi hình (kể cả CLUSTER 2.76) × 2 cỡ tròn × 4 góc × 5 điểm")
    }
}
