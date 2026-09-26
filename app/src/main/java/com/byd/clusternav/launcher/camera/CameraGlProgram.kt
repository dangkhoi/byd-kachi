package com.byd.clusternav.launcher.camera

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer

/**
 * ═══ BIÊN DỊCH shader · TEXTURE OES · ĐỔI pixel — ba việc cơ học của đường GL, tách khỏi luồng vẽ ══════════════
 *
 * R8-B (2.74). Không giữ trạng thái, không biết luồng: mọi hàm ở đây **phải** gọi trên luồng đang giữ `EGLContext`
 * ([CameraGlRenderer]) — đó cũng là lý do chúng không nằm luôn trong lớp ấy: nó đã 431 dòng, và ba việc này không
 * chia sẻ một biến nào với vòng vẽ.
 *
 * Nguồn GLSL lấy từ `:core` ([CameraDewarpShader]) — **không** có một dòng shader nào viết ở `:app`. Đó là điều kiện
 * để bài `CameraDewarpTest` ghim được GLSL và bản Kotlin của công thức vào nhau (KDoc [CameraDewarpShader] giải thích).
 */
internal object CameraGlProgram {

    /**
     * Biên dịch + liên kết cặp shader của [CameraDewarpShader]. `null` = hỏng (đã ghi nhật ký lý do).
     *
     * Ghi **nguyên văn `glGetShaderInfoLog`** khi hỏng, không chỉ "compile failed": trình biên dịch GLSL của mỗi
     * driver nói khác nhau, và câu duy nhất giúp sửa được là câu nó nói. Đây là đường **một lần một lượt overlay**
     * nên không có lý do gì tiết kiệm chữ ở đây.
     */
    fun build(): Int? {
        val (vertexSrc, fragmentSrc) = CameraDewarpShader.program()
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertexSrc) ?: return null
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSrc) ?: run {
            GLES20.glDeleteShader(vs); return null
        }
        val program = GLES20.glCreateProgram()
        if (program == 0) {
            Log.w(TAG, "GL glCreateProgram trả 0")
            GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
            return null
        }
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        // Xoá shader NGAY sau khi liên kết: program giữ tham chiếu riêng, nên hai đối tượng shader không còn việc gì.
        // Giữ chúng lại là hai đối tượng GPU sống suốt lượt overlay mà không ai dùng.
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        if (status[0] != GLES20.GL_TRUE) {
            Log.w(TAG, "GL liên kết program hỏng: ${GLES20.glGetProgramInfoLog(program)}")
            GLES20.glDeleteProgram(program)
            return null
        }
        return program
    }

    /**
     * Một texture `GL_TEXTURE_EXTERNAL_OES` sẵn tham số cho `SurfaceTexture`.
     *
     * Bốn tham số không phải trang trí:
     *  • `GL_LINEAR` hai chiều — khung HAL và ô ra gần như không bao giờ cùng cỡ; `GL_NEAREST` cho ra răng cưa trên
     *    mọi vạch kẻ đường, tức đúng thứ owner sẽ đọc thành *"nắn xong vẫn xấu"*.
     *  • `GL_CLAMP_TO_EDGE` hai chiều — **bắt buộc** cho texture OES (`GL_REPEAT` không hợp lệ với target này theo
     *    `GL_OES_EGL_image_external`). Và nó KHÔNG phải đường xử lý điểm ngoài ô: shader tự trả **đen đặc** trước khi
     *    lấy mẫu (KDoc [CameraDewarpShader] mục *"Điểm ngoài ô ⇒ ĐEN ĐẶC"*), chính vì clamp sẽ kéo vành pixel biên
     *    thành vệt trông như hình thật.
     */
    fun oesTexture(): Int {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        val t = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
        GLES20.glBindTexture(t, tex[0])
        GLES20.glTexParameteri(t, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(t, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(t, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(t, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return tex[0]
    }

    /**
     * `glReadPixels` (RGBA, **hàng dưới trước**) → ARGB `IntArray` **hàng trên trước** (`out[y*w + x]`).
     *
     * Lật dọc ở đây, không ở chỗ gọi: `glReadPixels` đọc theo hệ toạ độ cửa sổ của GL (gốc **góc dưới-trái**) còn
     * `Bitmap`/`Canvas` của Android đánh hàng từ **trên xuống**. Bỏ bước lật thì PNG chụp về bị ngược, và trên một
     * ảnh fisheye đối xứng thì **mắt không nhận ra** — nó chỉ làm mọi phép đo tâm/bán kính sai dấu trục y, tức đúng
     * loại lỗi sẽ được quy oan cho tham số nắn.
     *
     * Không dùng `Bitmap.copyPixelsFromBuffer`: nó nhận **đúng** thứ tự byte của bitmap config (RGBA_8888 little
     * endian ⇒ ABGR khi đọc thành `Int`) và **không** lật hàng ⇒ hai lỗi phải sửa thay vì một phép chuyển tường minh.
     */
    fun argbFlipped(buf: ByteBuffer, w: Int, h: Int): IntArray {
        buf.rewind()
        val out = IntArray(w * h)
        val row = ByteArray(w * 4)
        for (y in 0 until h) {
            buf.get(row)
            // Hàng `y` của GL (từ dưới) là hàng `h - 1 - y` của bitmap (từ trên).
            var o = (h - 1 - y) * w
            var i = 0
            while (i < row.size) {
                val r = row[i].toInt() and 0xFF
                val g = row[i + 1].toInt() and 0xFF
                val b = row[i + 2].toInt() and 0xFF
                val a = row[i + 3].toInt() and 0xFF
                out[o++] = (a shl 24) or (r shl 16) or (g shl 8) or b
                i += 4
            }
        }
        return out
    }

    /** @param type `GLES20.GL_VERTEX_SHADER` hoặc `GL_FRAGMENT_SHADER`. */
    private fun compile(type: Int, src: String): Int? {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            Log.w(TAG, "GL glCreateShader($type) trả 0")
            return null
        }
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            Log.w(TAG, "GL biên dịch shader type=$type hỏng: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return null
        }
        return shader
    }

    private const val TAG = PanoramaHal.TAG
}
