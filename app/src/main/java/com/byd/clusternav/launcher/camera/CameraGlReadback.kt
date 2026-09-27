package com.byd.clusternav.launcher.camera

import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ═══ Chụp một khung của đường GL qua **FBO ngoài màn hình** — `camera_frame` (R8-B · tách tệp 2.75) ════════════
 *
 * Tách khỏi [CameraGlRenderer] vì tệp đó đã chạm trần 500 dòng của CLAUDE.md §4.1, và vì đây là **vai khác**:
 * renderer lo *"vẽ ra cửa sổ, mỗi khung, không cấp phát"*, còn tệp này lo *"một lượt chụp lẻ, được phép cấp phát"*.
 * Không một trạng thái nào ở đây — chỉ một hàm nhận phép vẽ từ chỗ gọi.
 *
 * ⚠ **Phải chạy trên luồng vẽ** (`kachi-camgl`): nó đụng ngữ cảnh EGL hiện hành. Chỗ gọi
 * ([CameraGlRenderer.grab]) đã `post` + `CountDownLatch` cho việc ấy.
 */
internal object CameraGlReadback {

    /**
     * Vẽ một lượt cỡ [w]×[h] vào một FBO tạm bằng [paint] rồi đọc pixel về **ARGB, hàng-TRÊN-trước**.
     *
     * `glReadPixels` trả hàng **dưới** trước (gốc toạ độ GL ở góc dưới-trái) ⇒ phải lật, nếu không PNG ra ngược mà
     * mắt người khó nhận ra trên một khung camera — [CameraGlProgram.argbFlipped] làm đúng việc đó.
     *
     * `null` = FBO không hoàn chỉnh (cỡ vượt trần texture / định dạng không được cấp) — đã ghi một dòng `logcat`
     * kèm mã trạng thái; chỗ gọi coi đó là *"lượt chụp hỏng"*, không phải lỗi chết người.
     */
    fun into(w: Int, h: Int, paint: (Int, Int) -> Unit): IntArray? {
        val fbo = IntArray(1)
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glGenFramebuffers(1, fbo, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex[0], 0,
        )
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        var out: IntArray? = null
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.w(PanoramaHal.TAG, "GL FBO ${w}x$h không hoàn chỉnh: 0x${Integer.toHexString(status)}")
        } else {
            paint(w, h)
            val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
            out = CameraGlProgram.argbFlipped(buf, w, h)
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glDeleteFramebuffers(1, fbo, 0)
        GLES20.glDeleteTextures(1, tex, 0)
        return out
    }
}
