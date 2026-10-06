package com.byd.clusternav.launcher.camera

import android.opengl.GLES20
import android.util.Log

/**
 * ═══ VỊ TRÍ + GÁN uniform/attribute của program nắn — tách khỏi [CameraGlRenderer] (2.92, trần 500 dòng) ════════
 *
 * Spec `docs/specs/kachi-292-camera-full-view.html` T0. Tách thuần: cùng các lời gọi `glGet*Location` / `glUniform*`
 * của 2.74–2.91 cộng **hai** uniform mới của 2.92 (`uFit`, `uKappa`). Phải gọi trên luồng đang giữ `EGLContext`
 * (luồng vẽ của [CameraGlRenderer]) — một lời gọi GL từ luồng khác không ném gì, nó chỉ không làm gì.
 *
 * [bind] nằm trong **đường khung hình** (15 fps): không cấp phát, không log, không định dạng chuỗi — bài canh
 * `CameraGlWiringContractTest.duong khung hinh GL khong cap phat` đọc cả thân hàm này.
 */
internal class CameraGlBindings {

    var aPosition = -1
        private set
    var aTexCoord = -1
        private set
    private var uTex = -1
    private var uTexMatrix = -1
    private var uSrcRect = -1
    private var uRotation = -1
    private var uAmount = -1
    private var uFocal = -1
    private var uK = -1
    private var uScale = -1
    private var uAspect = -1
    private var uPan = -1
    private var uCenter = -1
    private var uFit = -1
    private var uKappa = -1

    /**
     * Vị trí uniform/attribute, lấy **theo TÊN** ngay sau khi liên kết program.
     *
     * Lượt kiểm cuối đọc danh sách từ `:core` ([CameraDewarpShader.UNIFORMS]) chứ không từ mấy dòng ngay trên: thêm
     * một `uniform` vào GLSL mà quên gán ở đây thì nó nhận `0` và khung ra sai **im lặng** — đúng loại lỗi mà
     * `declaredUniforms()` sinh ra để chặn ở tầng test, và dòng dưới đây chặn nốt ở tầng chạy.
     */
    fun locate(program: Int) {
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        uTex = GLES20.glGetUniformLocation(program, "uTex")
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uSrcRect = GLES20.glGetUniformLocation(program, "uSrcRect")
        uRotation = GLES20.glGetUniformLocation(program, "uRotation")
        uAmount = GLES20.glGetUniformLocation(program, "uAmount")
        uFocal = GLES20.glGetUniformLocation(program, "uFocal")
        uK = GLES20.glGetUniformLocation(program, "uK")
        uScale = GLES20.glGetUniformLocation(program, "uScale")
        uAspect = GLES20.glGetUniformLocation(program, "uAspect")
        uPan = GLES20.glGetUniformLocation(program, "uPan")
        uCenter = GLES20.glGetUniformLocation(program, "uCenter")
        uFit = GLES20.glGetUniformLocation(program, "uFit")
        uKappa = GLES20.glGetUniformLocation(program, "uKappa")
        val missing = CameraDewarpShader.UNIFORMS.filter { GLES20.glGetUniformLocation(program, it) < 0 }
        if (missing.isNotEmpty()) Log.w(PanoramaHal.TAG, "GL uniform KHÔNG tìm thấy: $missing (khung sẽ sai)")
    }

    /**
     * Gán trọn bộ uniform của [u] (texture unit 0 cho sampler). Đường khung hình — xem KDoc lớp.
     *
     * `transpose = false`: [ĐO] AOSP `SurfaceTexture.java:308-309` — *"The matrix is stored in column-major order so
     * that it may be passed directly to OpenGL ES via … glUniformMatrix4fv"*. Chuyển vị ở đây là xoay/lật khung theo
     * một cách trông "gần đúng" trên ma trận đơn vị và sai hẳn trên ma trận thật của xe.
     */
    fun bind(u: CameraGlUniforms, texMatrix: FloatArray) {
        GLES20.glUniform1i(uTex, 0)
        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
        val r = u.srcRect
        GLES20.glUniform4f(uSrcRect, r[0], r[1], r[2], r[3])
        GLES20.glUniform1f(uRotation, u.rotationDeg)
        val d = u.dewarp
        GLES20.glUniform1f(uAmount, d.amount)
        GLES20.glUniform1f(uFocal, d.focal)
        GLES20.glUniform1f(uK, d.k)
        GLES20.glUniform1f(uScale, d.scale)
        GLES20.glUniform1f(uAspect, u.aspect)
        GLES20.glUniform2f(uCenter, d.centerX, d.centerY)
        GLES20.glUniform2f(uPan, d.panX, d.panY)
        // 2.92 — `uFit = (0,0)` ⇒ shader bỏ hẳn bước vừa khung; `uKappa = 1` ⇒ phối cảnh thẳng từng bit cũ.
        GLES20.glUniform2f(uFit, u.fit[0], u.fit[1])
        GLES20.glUniform1f(uKappa, d.kappa)
    }
}
