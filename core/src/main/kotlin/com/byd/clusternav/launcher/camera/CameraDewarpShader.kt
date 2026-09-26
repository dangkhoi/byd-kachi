package com.byd.clusternav.launcher.camera

/**
 * ═══ GLSL ES 2.0 của phép nắn fisheye — nguồn shader nằm ở `:core`, THUẦN, ghim bằng test ══════════════════════
 *
 * Hai văn bản shader (vertex + fragment) cho đường `AVMCamera id 1 → SurfaceTexture(OES) → nắn → TextureView` của
 * phương án B (`docs/diagnostics/electro-camera-RE-2026-09-26.md` §6.2). Chỉ là **chuỗi**: không một lời gọi GL nào
 * ở đây, nên nó hợp luật Q1 của `:core` (JVM thuần) và **test được off-device** — `:app` chỉ biên dịch/liên kết và
 * gán uniform **theo tên** trong [UNIFORMS].
 *
 * ## Vì sao nguồn shader ở `:core` chứ không cạnh renderer trong `:app`
 * Cùng lý do [CameraOverlayTransform] ở `:core`: công thức là thứ dễ sai nhất và `:app` không test được off-device.
 * Ở đây công thức có **hai** bản — Kotlin ([CameraDewarp.mapDstToSrc]) và GLSL — nên bài `CameraDewarpTest` ghim
 * chúng vào nhau (cùng [FORMULA], cùng tập uniform, cùng các token `atan(`/`mix(`/`clamp(`). Đặt GLSL ở `:app` thì
 * hai bản trôi khỏi nhau mà không ai thấy, đúng cái bệnh CLAUDE.md §8 nói.
 *
 * ## Hợp đồng uniform — `:app` PHẢI gán đủ, đúng nghĩa (đọc kỹ, đây là chỗ dễ sai)
 * | Uniform | Kiểu | Ý nghĩa |
 * |---|---|---|
 * | `uTex` | `samplerExternalOES` | texture OES của `SurfaceTexture` nhận khung từ `AVMCamera` |
 * | `uTexMatrix` | `mat4` | ma trận của `SurfaceTexture.getTransformMatrix`, áp ở **bước cuối**. RE §7 **Q17 chưa biết** ma trận thật của id 1 trên xe ⇒ shader áp **vô điều kiện** và `:app` truyền **ma trận đơn vị** khi đo ra rằng không nên áp. Không có cờ, không có nhánh: một phép nhân mat4 là miễn phí so với một `if` mà không ai biết đúng chiều nào |
 * | `uSrcRect` | `vec4 (x,y,w,h)` | vùng con của texture nguồn, **chuẩn hoá**; lấy từ [CameraDewarp.srcRect] của `crop` trong [CameraSignalPolicy.CamView]. `w`/`h` **ÂM** = soi gương (RE §6.2 chi tiết 1) |
 * | `uRotation` | `float` độ | xoay nội dung, **dương = cùng chiều kim đồng hồ trên màn** — đúng quy ước [CameraSignalPolicy.rotationDegrees] và [CameraOverlayTransform]. Chỉ **bội của 90** là đẳng hình (xem [CameraDewarp.rotateDstToLocal]) |
 * | `uAmount` | `float` | `0..1`, cường độ nắn. `≤ 0.0001` ⇒ **bỏ qua hẳn** khối nắn (cổng của Electro) |
 * | `uFocal` | `float` | `F`, đơn vị nửa-bề-ngang ô. **Phải > 0** (RE §3.3 ràng buộc 4) |
 * | `uK` | `float` | `K`, hệ số f-theta của ống kính |
 * | `uScale` | `float` | `SCALE`, phóng thêm |
 * | `uAspect` | `float` | bề ngang/bề cao của ô **theo pixel NGUỒN** = `uSrcRect.z·texW / (uSrcRect.w·texH)` (dùng trị **tuyệt đối** khi soi gương). Đây là chỗ Electro thiếu ⇒ đồng-θ của nó là ellipse (RE §3.3) |
 * | `uCenter` | `vec2` | tâm quang trong toạ độ ô, **được phép ngoài `[0,1]`** ([CameraDewarp.centerInCrop]) |
 *
 * `:app` còn phải khớp **cửa sổ**: [CameraOverlayFrame.fit] đã cho khung đúng tỉ lệ vùng crop sau xoay, và phép nắn
 * dựa vào đúng điều đó — nếu khung lệch tỉ lệ thì ảnh vẫn nắn đúng *trong không gian nguồn* nhưng bị giãn không
 * đẳng hướng khi hiện ra, y như đường 2.36.
 *
 * ## Attribute + quad
 * [ATTRIBUTES] = `aPosition` (`vec4`, toạ độ clip) và `aTexCoord` (`vec2`, `0..1`) — đúng tên Electro dùng
 * (RE §3.4, @0x5e5b2/@0x5e5bf). Một quad toàn khung, `TRIANGLE_STRIP` 4 đỉnh.
 *
 * ## Điểm ngoài ô ⇒ ĐEN ĐẶC, không clamp
 * Giống Electro (§3.2, hai chỗ `gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0)`): sau khi nắn, toạ độ ra ngoài `[0,1]²`
 * thì trả đen **alpha 1**, không để sampler clamp — clamp sẽ *kéo dài* vành pixel biên thành vệt, trông như hình
 * thật nhưng là bịa. Đen đặc là câu trả lời trung thực "chỗ này không có dữ liệu".
 */
object CameraDewarpShader {

    /** Cùng chuỗi với [CameraDewarp.FORMULA] — ai sửa một bên, bài test đỏ ngay bên kia. */
    const val FORMULA: String = CameraDewarp.FORMULA

    /** Tên uniform, đúng thứ tự khai báo trong [FRAGMENT]. `:app` gán **theo tên**, không theo vị trí. */
    val UNIFORMS: List<String> = listOf(
        "uTex",
        "uTexMatrix",
        "uSrcRect",
        "uRotation",
        "uAmount",
        "uFocal",
        "uK",
        "uScale",
        "uAspect",
        "uCenter",
    )

    /** Tên attribute của [VERTEX]. */
    val ATTRIBUTES: List<String> = listOf("aPosition", "aTexCoord")

    /** Vertex shader — chỉ chuyển tiếp, mọi phép toán nằm ở fragment (đúng khuôn shader A của Electro). */
    val VERTEX: String = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
    """.trimIndent() + "\n"

    /**
     * Fragment shader — **bản dịch từng dòng** của [CameraDewarp.rotateDstToLocal] → [CameraDewarp.mapDstToSrc] →
     * [CameraDewarp.applySrcRect]. Đổi một dòng ở đây thì phải đổi đúng dòng tương ứng bên Kotlin.
     */
    val FRAGMENT: String = """
        #extension GL_OES_EGL_image_external : require
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
        uniform samplerExternalOES uTex;
        uniform mat4 uTexMatrix;
        uniform vec4 uSrcRect;
        uniform float uRotation;
        uniform float uAmount;
        uniform float uFocal;
        uniform float uK;
        uniform float uScale;
        uniform float uAspect;
        uniform vec2 uCenter;
        varying vec2 vTexCoord;
        void main() {
            // 1. xoay quanh tam o, TRONG KHONG GIAN O DA CHUAN HOA (khop CameraOverlayTransform:
            //    crop -> xoay quanh tam view -> bu ti le (vw/vh, vh/vw) rut gon thanh dung phep xoay nay).
            float rotationRad = radians(uRotation);
            float rotationCos = cos(rotationRad);
            float rotationSin = sin(rotationRad);
            vec2 q = vTexCoord - vec2(0.5, 0.5);
            vec2 local = vec2(0.5, 0.5) + vec2(
                (rotationCos * q.x) + (rotationSin * q.y),
                (rotationCos * q.y) - (rotationSin * q.x));

            // 2. nan fisheye: dich la phoi canh thang (r = F*tan t), nguon la fisheye dang khoang (r = f*t).
            //    uAspect quy doi truc y => dong-theta la tron THAT theo pixel (Electro thieu buoc nay).
            vec2 corrected = local;
            float amount = clamp(uAmount, 0.0, 1.0);
            float gain = uK * uScale;
            if (amount > 0.0001 && uFocal > 0.0 && gain > 0.0) {
                vec2 p = vec2((local.x - uCenter.x) * 2.0,
                              (local.y - uCenter.y) * 2.0 / uAspect);
                float pLen = length(p);
                if (pLen > 0.000001) {
                    vec2 radialDir = p / pLen;
                    float theta = atan(pLen, uFocal);
                    float rSrc = gain * theta;
                    vec2 projected = vec2(uCenter.x + (radialDir.x * rSrc * 0.5),
                                          uCenter.y + (radialDir.y * rSrc * 0.5 * uAspect));
                    corrected = mix(local, projected, amount);
                }
            }

            // 3. ngoai o => DEN DAC (khong de sampler clamp keo vet pixel bien thanh hinh bia).
            if (corrected.x < 0.0 || corrected.x > 1.0 ||
                corrected.y < 0.0 || corrected.y > 1.0) {
                gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
                return;
            }

            // 4. vao vung that cua texture nguon (uSrcRect.zw AM = soi guong), roi uTexMatrix.
            vec2 raw = uSrcRect.xy + (corrected * uSrcRect.zw);
            vec2 samplePos = (uTexMatrix * vec4(raw, 0.0, 1.0)).xy;
            gl_FragColor = texture2D(uTex, samplePos);
        }
    """.trimIndent() + "\n"

    /** `(vertex, fragment)` — đúng cặp mà `:app` nạp vào một `GLES20` program. */
    fun program(): Pair<String, String> = VERTEX to FRAGMENT

    /**
     * Các uniform thực sự được khai báo trong [FRAGMENT], đọc **từ chính văn bản shader**.
     *
     * Có hàm này vì [UNIFORMS] là một bản tay: nếu ai thêm một `uniform` vào GLSL mà quên thêm vào danh sách thì
     * `:app` sẽ không bao giờ gán nó và khung ra sai một cách im lặng. Bài test so hai bên.
     */
    fun declaredUniforms(): List<String> = DECLARATION.findAll(FRAGMENT).map { it.groupValues[2] }.toList()

    /** Attribute thực sự được khai báo trong [VERTEX] — cùng lý do [declaredUniforms]. */
    fun declaredAttributes(): List<String> = DECLARATION.findAll(VERTEX).map { it.groupValues[2] }.toList()

    private val DECLARATION =
        Regex("""(?m)^\s*(uniform|attribute)\s+[A-Za-z0-9_]+\s+([A-Za-z0-9_]+)\s*;""")
}
