package com.byd.clusternav.launcher.camera

/**
 * ═══ CAMERA THEO XI-NHAN — luật thuần (`:core`, cấm android.*) ═══════════════════════════════════════════════
 *
 * Owner 2026-09-22: *"xi-nhan trái → camera trái → overlay bên trái màn; xi-nhan phải → camera phải → bên phải.
 * Cho chọn loại camera + vị trí trong Setting, default TẮT (đang phát triển)."*
 *
 * RE `docs/diagnostics/camera-panorama-RE-2026-09-22.md`: HAL `BYDAutoPanoramaDevice` chọn view bằng
 * `setPanoOutputState(int)` với hằng `APA_OUTPUT_STATE_*`. Lớp này chỉ QUYẾT ĐỊNH (view nào + bên nào của màn);
 * gọi HAL + dựng SurfaceView là việc `:app`.
 *
 * ## Hằng output [ĐO jadx-tmap BYDAutoPanoramaDevice]
 * Đây là các giá trị THẬT của HAL — KHÔNG bịa. Owner có thể đổi mapping trong Setting (mỗi xe khác nhau).
 */
object CameraSignalPolicy {

    /** Bên xi-nhan. */
    enum class Turn { LEFT, RIGHT, NONE }

    /** Vị trí overlay trên màn. */
    enum class Side { LEFT, RIGHT }

    // ── GÓC hiện overlay (spec `camera-turn-signal-hal-socket.html` R2–R4) ───────────────────────
    //
    // Chuỗi, KHÔNG enum: đây là **giá trị lưu bền** của `camera_pos_left`/`camera_pos_right` và cũng là mã của
    // chip trong Cài đặt. Một enum sẽ cần bảng đổi enum↔chuỗi ở CẢ HAI đầu (prefs + chipRow) — tức hai bản sao
    // của cùng một sự thật, đúng bẫy mà `ProfileNames` (khoá ≠ nhãn) đã trả giá.
    //
    // Chỉ có hai góc TRÊN: overlay phải nằm dưới thanh trên và KHÔNG đè nội dung dưới cùng của khung làm việc
    // (R2). Góc dưới không có trong tập chọn vì đó là chỗ thanh nút xe.

    /** Góc trên-TRÁI của khung launcher. */
    const val CORNER_TOP_LEFT = "TL"

    /** Góc trên-PHẢI của khung launcher. */
    const val CORNER_TOP_RIGHT = "TR"

    /** R4 — mặc định "cùng bên": xi-nhan trái → [CORNER_TOP_LEFT], phải → [CORNER_TOP_RIGHT]. */
    fun defaultCorner(left: Boolean): String = if (left) CORNER_TOP_LEFT else CORNER_TOP_RIGHT

    /**
     * Chuỗi góc đọc lên có dùng được không.
     *
     * Cần vì prefs là dữ liệu **sửa tay được** (`prefs_set`) và còn giữ giá trị của bản cũ (`camera_lvds_option`
     * từng nhận "A".."BCDH"). Giá trị lạ ⇒ chỗ đọc rơi về [defaultCorner], KHÔNG ném và cũng không im lặng đặt
     * overlay vào một góc thứ ba không tồn tại.
     */
    fun isCorner(v: String): Boolean = v == CORNER_TOP_LEFT || v == CORNER_TOP_RIGHT

    // ── XOAY video (spec `camera-turn-signal-hal-socket.html` R7 · owner 2026-09-26) ─────────────
    //
    // Owner (nguyên văn, off-car 2.67): *"cái xinhan bật cam mình cắt video ok, nhưng nó bị ngang, cần dọc video
    // lại, nên cần phải rotation 90 độ, bên trái là rotation 90 độ xoay qua trái, bên phải thì rotation 90 độ xoay
    // sang phải, nếu đc thì thêm option rotation trong setting"*.
    //
    // Owner (trên xe 2.69, 2026-09-26): *"xoay video cần làm 2 line setting độc lập cho camera trái và phải (khi
    // xinhan trái/phải), có thể 2 camera cần xoay khác nhau"*. ⇒ 2.71 bỏ mô hình "một chế độ cho cả hai bên"
    // (6 mã, có `SIDE`/`SIDEINV`): mỗi BÊN giữ một GÓC riêng trong bốn góc dưới đây. Khác biệt trái/phải không còn
    // là một "chế độ" nữa mà là hai lựa chọn — đúng khuôn `camera_pos_left/right` (R4), và khớp RE kinex
    // [ĐO `Y0/C0094o.java:308-315`]: hai số xoay ĐỘC LẬP theo bên, không có luật "trái ngược phải".
    //
    // [ĐO code] Vùng crop gương của ảnh fisheye 4-in-1 (5120×960) là dải DỌC (x rộng 0.10 × y cao 1.0 ở mặc định
    // 2.73; `camera_span`/`camera_shape` đổi được — xem [CameraPanoCrop]) ⇒ căng vào cửa sổ vuông thì hình nằm NGANG
    // (`CamView.MIRROR_*`). Xoay ±90° đưa nó về chiều dọc. Góc xoay áp **SAU** crop, ở cả ba bề rộng/hình khung.
    //
    // Chuỗi, KHÔNG enum — cùng lý do với `CORNER_*` ở trên: đây vừa là giá trị lưu bền của `camera_rot_left/right`,
    // vừa là mã chip trong Cài đặt; một bảng đổi mã ở hai đầu là hai bản sao của cùng một sự thật.
    //
    // Quy ước độ: **âm = ngược chiều kim đồng hồ (xoay qua trái)**, **dương = cùng chiều (xoay sang phải)** —
    // đúng chiều dương của `Matrix.postRotate` trên hệ toạ độ màn (trục y hướng xuống), nên tầng vẽ dùng thẳng số này.

    /** Không xoay. */
    const val ROTATE_NONE = "0"

    /** −90° (↺ qua trái). Mặc định của bên TRÁI. */
    const val ROTATE_LEFT = "L90"

    /** +90° (↻ sang phải). Mặc định của bên PHẢI. */
    const val ROTATE_RIGHT = "R90"

    /** 180°. */
    const val ROTATE_180 = "180"

    /**
     * Mã CŨ của khoá đơn `camera_rotation` (2.67–2.70): theo bên, trái −90 / phải +90. **Không còn trong
     * [ROTATIONS]** — chỉ [migrateRotation] còn đọc nó, để prefs đã ghi trên xe không bị mất khi nâng cấp.
     */
    const val ROTATE_BY_SIDE = "SIDE"

    /** Mã CŨ (review Pass 1 · 2.67): theo bên NGƯỢC, trái +90 / phải −90. Cùng số phận [ROTATE_BY_SIDE]. */
    const val ROTATE_BY_SIDE_INV = "SIDEINV"

    /** Mọi góc xoay hợp lệ cho MỘT bên — thứ tự này cũng là thứ tự chip của mỗi hàng trong Cài đặt. */
    val ROTATIONS: List<String> = listOf(ROTATE_NONE, ROTATE_LEFT, ROTATE_RIGHT, ROTATE_180)

    /**
     * Mặc định theo bên — trái [ROTATE_LEFT] (−90 ↺), phải [ROTATE_RIGHT] (+90 ↻): đúng nguyên văn owner 2.67
     * ("bên trái xoay qua trái, bên phải xoay sang phải"). Xe khác (SL6…) ghép ảnh 4-in-1 khác chiều thì chỉnh
     * từng hàng trong *Cài đặt › Tiện nghi xe*, không đổi hằng này. [ĐOÁN — CHƯA đo trên xe] rằng hai mặc định
     * này đúng chiều: RE kinex mặc định 0 cho cả hai, tức không có sự thật dòng xe nào để dựa; chốt bằng mắt owner.
     */
    fun defaultRotation(left: Boolean): String = if (left) ROTATE_LEFT else ROTATE_RIGHT

    /** Chuỗi góc xoay đọc lên có dùng được không — cùng vai [isCorner] (prefs sửa tay được qua `prefs_set`). */
    fun isRotation(v: String): Boolean = v in ROTATIONS

    /**
     * Góc xoay (độ) cho overlay của bên [left] theo mã [mode]: −90 / 0 / 90 / 180.
     *
     * [mode] lạ (kể cả mã cũ `SIDE`/`SIDEINV` lọt tới đây mà chưa qua [migrateRotation]) ⇒ coi như
     * [defaultRotation] của bên đó — không ném, không im lặng ra 0 (0 là một lựa chọn THẬT của owner, không phải
     * giá trị "không biết").
     */
    fun rotationDegrees(mode: String, left: Boolean): Int =
        when (if (isRotation(mode)) mode else defaultRotation(left)) {
            ROTATE_NONE -> 0
            ROTATE_LEFT -> -90
            ROTATE_RIGHT -> 90
            else -> 180   // ROTATE_180 — nhánh cuối vì `when` trên chuỗi cần else; tập đã đóng bởi isRotation
        }

    /**
     * Đổi giá trị của khoá đơn cũ `camera_rotation` (2.67–2.70) sang góc của bên [left] cho khoá mới
     * `camera_rot_left/right` (2.71). Thuần, không đụng prefs — `Prefs.cameraRotation` gọi đúng một lần khi thấy
     * khoá cũ còn mà khoá mới chưa có.
     *
     * - `SIDE`    → trái [ROTATE_LEFT] / phải [ROTATE_RIGHT] (giữ nguyên hành vi mặc định cũ);
     * - `SIDEINV` → trái [ROTATE_RIGHT] / phải [ROTATE_LEFT];
     * - một trong [ROTATIONS] → giữ nguyên cho CẢ hai bên (xe đã chọn `R90` thì cả hai bên vẫn +90 — đúng thứ
     *   owner đang nhìn thấy trên xe trước khi nâng cấp, không âm thầm đổi);
     * - lạ → [defaultRotation] của bên.
     */
    fun migrateRotation(old: String, left: Boolean): String = when (old) {
        ROTATE_BY_SIDE -> if (left) ROTATE_LEFT else ROTATE_RIGHT
        ROTATE_BY_SIDE_INV -> if (left) ROTATE_RIGHT else ROTATE_LEFT
        else -> if (isRotation(old)) old else defaultRotation(left)
    }

    // ── ĐƯỜNG KẾT XUẤT khung hình (CLOSE-14 · CAM-LAG, owner 2026-09-26: camera "hơi giật lag khi xe chạy") ─────
    //
    // Phân tích `docs/diagnostics/camera-lag-analysis-2026-09-26.md` L2: `TextureView` vẽ **trong** cây view ⇒ mỗi
    // khung đi qua HWUI rồi mới tới SurfaceFlinger; `SurfaceView` là **layer riêng**, rẻ hơn một lượt GPU. Nhưng
    // `SurfaceView` mất `setTransform` ⇒ mất cả crop-bằng-ma-trận lẫn xoay (R7). [ĐO xe 2026-09-26] hai lượt
    // `gfxinfo` CÙNG bản 2.70 có camera hiện cho 26,9 % và 4,67 % khung giật — số liệu **mâu thuẫn**, nên L2 vẫn ở
    // mức [CHƯA BIẾT]. Vì thế đây là một LỰA CHỌN có mã lưu bền, mặc định giữ đúng đường đang chạy (CLAUDE.md §6:
    // đường mới xuống cuối, không đảo mặc định), để buổi xe tới đo được hai đường cạnh nhau mà không build lại.
    //
    // Chuỗi, KHÔNG enum — cùng lý do với `CORNER_*`/`ROTATE_*`: vừa là giá trị lưu bền của `camera_render`, vừa là
    // mã chip trong Cài đặt.

    /** `TextureView` — đường ĐANG CHẠY hiện trường (crop + xoay bằng ma trận). Mặc định. */
    const val RENDER_TEXTURE = "TV"

    /** `SurfaceView` + `setZOrderMediaOverlay` — layer riêng, rẻ hơn một lượt GPU; KHÔNG xoay được bằng ma trận. */
    const val RENDER_SURFACE = "SV"

    /**
     * **Nắn méo (GL)** — R8-B (2.74): `TextureView` vẫn là cửa ra, nhưng khung đi qua **một lượt shader** trên một
     * luồng vẽ riêng có ngữ cảnh `EGL14` (ES 2.0): `AVMCamera → SurfaceTexture(OES) → `[CameraDewarpShader]` →
     * EGL window surface của chính `SurfaceTexture` mà `TextureView` cấp`.
     *
     * ## Vì sao là mã THỨ BA, không phải một công tắc trên [RENDER_TEXTURE]
     * Cả ba đường dùng **cùng** cửa sổ, cùng bo góc, cùng hình TRÒN — chúng chỉ khác nhau ở *ai vẽ khung và bằng gì*,
     * đúng chiều mà `camera_render` đã cắt từ CLOSE-14. Một công tắc riêng (`camera_dewarp_on`) sẽ tạo ra **bốn** tổ
     * hợp trong đó hai cái vô nghĩa (`SV` + nắn = không có ngữ cảnh GL nào để nắn), và owner sẽ gặp một cú chạm không
     * làm gì cả mà không có lời giải thích nào trên màn.
     *
     * ## Mặc định KHÔNG đổi (CLAUDE.md §6) — đứng CUỐI danh sách
     * Cả tầng này là đường **mới**, chưa một khung nào của nó chạy trên xe: `GL_MAX_TEXTURE_SIZE` của GPU đầu xe với
     * texture rộng 5120 vẫn **[CHƯA BIẾT]** (RE §7 Q13) và ma trận `getTransformMatrix` thật của camera id 1 cũng
     * vậy (Q17). Hai điều đó chỉ chốt được **trên xe**, nên đường mới xuống cuối và mặc định vẫn là [RENDER_TEXTURE].
     */
    const val RENDER_GL = "GL"

    /** Mọi đường kết xuất hợp lệ — cũng là thứ tự chip trong Cài đặt (mặc định đứng đầu, đường MỚI đứng cuối). */
    val RENDERS: List<String> = listOf(RENDER_TEXTURE, RENDER_SURFACE, RENDER_GL)

    /** Đường kết xuất mặc định = thứ đã chạy trên xe từ 2.3x. */
    fun defaultRender(): String = RENDER_TEXTURE

    /** Mã đường kết xuất đọc lên có dùng được không — cùng vai [isRotation] (prefs sửa tay được qua `prefs_set`). */
    fun isRender(v: String): Boolean = v in RENDERS

    /** Mã đã kiểm, hoặc [defaultRender] nếu lạ — một cửa duy nhất cho ba phép hỏi dưới đây. */
    private fun sane(render: String): String = if (isRender(render)) render else defaultRender()

    /**
     * Đường [render] có xoay được bằng MA TRẬN hay không.
     *
     * `false` ⇒ tầng vẽ phải (a) nhờ HAL xoay hộ nếu ROM cho (`AVMCamera.setDisplayOrientation`, [ĐO RE] có trong
     * lớp framework) và (b) tính cỡ cửa sổ theo tỉ lệ **chưa xoay** khi HAL cũng không nhận — chứ KHÔNG im lặng bỏ
     * góc owner đã chọn rồi để khung sai tỉ lệ. Mã lạ ⇒ coi như [defaultRender] (mã lạ chỉ tới từ prefs sửa tay).
     *
     * ⚠ [RENDER_GL] trả **`false`**: nó xoay trong **shader** ([rotatesInShader]), và `setTransform` trên đường đó
     * PHẢI là ma trận đơn vị — áp cả hai là xoay hai lần.
     */
    fun rotatesByMatrix(render: String): Boolean = sane(render) == RENDER_TEXTURE

    /** Đường [render] xoay + cắt vùng bằng **shader** (uniform `uRotation`/`uSrcRect`) — chỉ [RENDER_GL]. */
    fun rotatesInShader(render: String): Boolean = sane(render) == RENDER_GL

    /**
     * Đường [render] dựng lớp video bằng `TextureView` hay không — **[RENDER_TEXTURE] và [RENDER_GL]**.
     *
     * Tách khỏi [rotatesByMatrix] vì hai câu hỏi đó **không còn trùng nhau** từ R8-B: đường GL vẫn là `TextureView`
     * (nên bo góc vẫn ăn, hình TRÒN vẫn ăn, `getBitmap` vẫn chụp được) nhưng **không** xoay bằng ma trận. Trước đây
     * một hàm trả lời cả hai — dùng lại nó cho GL sẽ đẩy đường GL vào nhánh `SurfaceView` và không có một khung nào
     * hiện ra, đúng loại lỗi *"compile xanh, chưa từng chạy"* của CLAUDE.md §8.
     */
    fun usesTextureView(render: String): Boolean = sane(render) != RENDER_SURFACE

    /**
     * Góc xoay owner chọn có **thật sự** được ai đó thi hành hay không — [rotationDeg] `0` thì không cần ai làm.
     *
     * Gộp ở đây (thay vì một biểu thức ba nhánh rải trong `:app`) vì nó quyết **tỉ lệ cửa sổ**
     * ([CameraOverlayFrame.fit]): nói sai một nhánh thì ảnh nằm trong một khung sai tỉ lệ và bị giãn, mà không có
     * lỗi nào được báo — CLAUDE.md §2, cơ chế ≠ quy kết.
     *
     * @param halAccepted `AVMCamera.setDisplayOrientation` có **nhận** lời gọi không (đường `SurfaceView`). "Nhận"
     *   ≠ "có tác dụng" — đó là giới hạn của chính phép đo, xem KDoc `AvmCamera.setDisplayOrientation`.
     */
    fun rotationEffective(render: String, rotationDeg: Int, halAccepted: Boolean): Boolean =
        rotationDeg == 0 || rotatesByMatrix(render) || rotatesInShader(render) || halAccepted

    // ── VÙNG GƯƠNG: bề rộng · dải pano · hình khung (R8-A · 2.74) ────────────────────────────────
    //
    // RE `docs/diagnostics/electro-camera-RE-2026-09-26.md` §5 K10 [ĐO]: crop của 2.73 rộng `0.10` bề ngang = **40 %
    // của MỘT dải** (một dải = 0.25 — hằng `0.25` nằm thẳng trong shader của Electro @0x493b5) ⇒ Kachi đang nhìn một
    // vệt hẹp ở rìa vòng fisheye, đúng chỗ méo nặng nhất. §6.4 xếp **A** (nới/đổi crop chữ nhật) trước **B** (shader
    // nắn) vì A rẻ, không đụng đường vẽ, và có thể làm B thành không cần thiết.
    //
    // Cả bốn pref dưới đây **mặc định = hành vi 2.73 từng pixel** (CLAUDE.md §6: đường mới xuống cuối, không đảo mặc
    // định để chữa cho một thứ còn [CHƯA BIẾT]). Hình học ở [CameraPanoCrop] (thuần, có test bằng số); ở đây chỉ có
    // **mã lưu bền + phép kiểm** — cùng khuôn `CORNER_*`/`ROTATE_*`/`RENDER_*`: chuỗi, không enum, vì mã chip trong
    // Cài đặt và giá trị trên đĩa là MỘT.

    /** Bề rộng crop của 2.73: `0.10` bề ngang ảnh = 40 % một dải, neo vào mép NGOÀI của dải. Mặc định. */
    const val SPAN_NARROW = "NARROW"

    /** **Trọn** một dải pano (0.25 bề ngang) — thứ Electro coi là "một camera" của khung 4-in-1. */
    const val SPAN_STRIP = "STRIP"

    /** Mọi bề rộng hợp lệ — cũng là thứ tự chip trong Cài đặt (mặc định đứng đầu). */
    val SPANS: List<String> = listOf(SPAN_NARROW, SPAN_STRIP)

    /** Bề rộng mặc định = đúng vệt đã chạy trên xe từ 2.36. */
    fun defaultSpan(): String = SPAN_NARROW

    /** Mã bề rộng đọc lên có dùng được không — cùng vai [isRender] (prefs sửa tay được qua `prefs_set`). */
    fun isSpan(v: String): Boolean = v in SPANS

    // ── NGUỒN ảnh: **chỉ** khung GHÉP 4-in-1 (2.77 — tuỳ chọn *Một camera* đã gỡ) ──────────────────────────
    //
    // 2.75/2.76 có một mã nguồn thứ hai (`camera_source = CHANNEL`, MỘT kênh camera đổ đầy buffer). Owner gỡ trên xe
    // 27/09 sau một phép ĐO: cùng cỡ cảnh, dải ghép có **năng lượng cạnh 686 vs 351** và tỉ lệ chi tiết ngang/dọc
    // **0,30 (ghép) vs 0,19 (một kênh)** ⇒ một kênh chỉ bị KÉO NGANG nhiều hơn, **không** mang thêm điểm ảnh thật.
    // Bản đồ kênh đo được (1 = sau · 2 = trái · 3 = phải · 4 = trước) giữ lại làm **kiến thức** ở
    // `docs/diagnostics/offcar-2026-09-27/camera-ia-profile.md` §7, không còn đường code nào đọc nó.
    //
    // ⇒ Ở đây KHÔNG còn hằng nguồn nào: một enum một-phần-tử là một chỗ để ai đó thêm phần tử thứ hai mà không đo.
    // `addPreviewSurface` chỉ còn đường dò `0..3` của 2.73 (`AvmCamera.open`), tức luôn là khung ghép.

    /** Khung CHỮ NHẬT bo góc — đúng cửa sổ 2.73. Mặc định. */
    const val SHAPE_RECT = "RECT"

    /**
     * Khung TRÒN: lấy **ô vuông giữa dải** (cạnh = chiều cao dải) rồi bo thành hình tròn — tức hiện **trọn vòng ảnh
     * fisheye** như app Electro vẽ, KHÔNG nắn méo (nắn là phương án B, spec riêng sau buổi xe).
     *
     * ⚠ "đường kính vòng ảnh ≈ chiều cao dải, đặt giữa dải" là **[ĐOÁN]** tới khi có khung PNG thật từ xe ⇒ có núm
     * `camera_circle_scale` ([isCirclePct]) để owner co/giãn ô vuông ngay trên xe.
     */
    const val SHAPE_ROUND = "ROUND"

    /**
     * Khung **THEO CỤM** (2.76 · R4, làn L2): khi chiếu lên cụm `1920×720` chỉ vẽ trong **dải giữa** của cụm — hình
     * học ở `CameraClusterBand` (`:core`, làn L2). Trên màn chính mã này **thoái về** [SHAPE_RECT]: mọi phép so
     * `shape == SHAPE_ROUND` trả `false` nên tầng vẽ/crop đi nhánh chữ nhật, không có nhánh nào ném. Ô chip sinh từ
     * [SHAPES] ⇒ Cài đặt không phải sửa khi L2 nối dây.
     */
    const val SHAPE_CLUSTER = "CLUSTER"

    /** Mọi hình khung hợp lệ — cũng là thứ tự chip trong Cài đặt (mặc định đứng đầu, mã MỚI đứng cuối — §6). */
    val SHAPES: List<String> = listOf(SHAPE_RECT, SHAPE_ROUND, SHAPE_CLUSTER)

    /** Hình khung mặc định = chữ nhật bo góc của 2.73. */
    fun defaultShape(): String = SHAPE_RECT

    /** Mã hình khung đọc lên có dùng được không — cùng vai [isSpan]. */
    fun isShape(v: String): Boolean = v in SHAPES

    /**
     * Phần trăm cạnh ô vuông của [SHAPE_ROUND] so với **chiều cao dải** — `100` = trọn chiều cao (mặc định).
     *
     * Trần là **100**, không phải một số lớn hơn: cạnh ô vuông ở 100 % đã bằng đúng chiều cao ảnh nguồn, nên không có
     * pixel nào để giãn thêm. Vòng ảnh thật rộng hơn cao (bị cắt trên/dưới) là một **phát hiện phải báo**, không phải
     * một núm — ghi vào runbook 🚗 thay vì kẹp im lặng.
     */
    const val CIRCLE_PCT_MIN = 50

    /** Xem [CIRCLE_PCT_MIN]. */
    const val CIRCLE_PCT_MAX = 100

    /** Mặc định = trọn chiều cao dải. */
    const val CIRCLE_PCT_DEFAULT = 100

    /** Phần trăm đọc lên có dùng được không (ngoài dải ⇒ chỗ đọc rơi về [CIRCLE_PCT_DEFAULT]). */
    fun isCirclePct(v: Int): Boolean = v in CIRCLE_PCT_MIN..CIRCLE_PCT_MAX

    // ── TRẦN NHỊP VẼ của đường GL (2.75) ──────────────────────────────────────────────────────────────────

    /**
     * ═══ Trần khung/giây của đường GL — [ĐO CAM-B4 xe 27/09 11:25] ════════════════════════════════════════
     *
     * Đường GL vẽ **mỗi khi có khung** (`onFrameAvailable`) và HAL đẩy nhanh hơn `setCameraFps(15)` Kachi xin:
     * 2 phút xi-nhan thật, cửa sổ TRÒN `495×495` ⇒ **4052 khung ≈ 34 fps**, giật **11,15 %**, p99 **61 ms**,
     * CPU **10,7 %** (đường `TV` cùng cảnh: 982 khung, giật 1,0 %, p99 16 ms, CPU ≈0 %). 34 fps là công vô ích
     * trả bằng đúng thứ owner cảm thấy. Chi tiết + runbook CAM-B6: `camera-dewarp-gl.md` §G.
     */
    const val RENDER_FPS_CAP = 15

    /**
     * Khoảng cách tối thiểu giữa hai lượt **vẽ** (ms) cho trần [fps]. `fps <= 0` ⇒ `0` = không chặn (đường 2.74).
     *
     * Lấy **3/4** chu kỳ chứ không trọn: khung HAL tới mỗi ~29,4 ms, ngưỡng trọn chu kỳ (66 ms) rơi **giữa** hai
     * khung ⇒ lượt vẽ trượt sang khung thứ ba ⇒ chỉ còn ~11 fps. Với 3/4 (50 ms) nhịp thật ≈ 17 fps, sát dưới trần.
     */
    fun renderMinGapMs(fps: Int = RENDER_FPS_CAP): Long = if (fps > 0) (1000L * 3) / (fps * 4) else 0L

    // ── `vehicle.config.cam_sort` — phép thử NĂNG LỰC pano (RE §5 K2) ───────────────────────────
    //
    // [ĐO firmware] launcher gốc dò camera bằng đúng khoá này: `VehicleUtils.java:187-192`
    // `getAvailableCameraType()` → `SystemProperties.get("vehicle.config.cam_sort","")`, rồi `hasAVMRecorder() =
    // contains(CAMERA_CAR_PANO_H)` (`:176`). Rẻ hơn mở camera để xem có ra hình. Phân tích thì THUẦN nên nằm ở đây;
    // đọc getprop là việc của `:app`. 2.74 **chỉ ghi nhật ký**, KHÔNG gate gì (CLAUDE.md §3: chưa đo trên xe thì chưa
    // được làm cổng).

    /** Tag khung pano ghép 4-in-1 [ĐO `DiLinkCameraConstants.java:19`]. */
    const val CAM_TAG_PANO = "pano_h"

    /** Tag camera lùi [ĐO `DiLinkCameraConstants.java:21`]. */
    const val CAM_TAG_REAR = "rear"

    /**
     * Phân tích `vehicle.config.cam_sort` — `"rear:0;pano_h:1;"` ⇒ `{rear=0, pano_h=1}`.
     *
     * Mảnh không đúng dạng `<tag>:<số>` bị **bỏ qua** (không ném): đây là chuỗi của ROM, đời xe khác có thể thêm tag
     * lạ hoặc dấu phân cách khác, và một ngoại lệ ở đây sẽ giết một dòng nhật ký chẩn đoán.
     */
    fun camSortIds(raw: String): Map<String, Int> = raw
        .split(';', ',')
        .mapNotNull { part ->
            val tag = part.substringBefore(':', "").trim()
            val id = part.substringAfter(':', "").trim().toIntOrNull()
            if (tag.isEmpty() || id == null) null else tag to id
        }
        .toMap()

    /** Id của [tag] trong chuỗi `cam_sort`, `null` = không có tag đó (⇒ xe/trim này không có luồng ấy). */
    fun camSortId(raw: String, tag: String): Int? = camSortIds(raw)[tag]

    /** Một view camera [ĐO BYDAutoPanoramaDevice.APA_OUTPUT_STATE_*]. */
    /**
     * Một góc camera. `cameraId` = tham số AVMCamera.open. `crop` = vùng cắt (x0,y0,x1,y1 chuẩn hoá 0..1) của
     * ảnh camera; `null` = hiện nguyên khung.
     *
     * [hintW]×[hintH] = **gợi ý** cỡ ảnh nguồn (px), chỉ dùng cho lượt dựng cửa sổ ĐẦU TIÊN, trước khi
     * `AVMCamera.getPreviewWidth/getPreviewHeight` trả số thật (xem [CameraOverlayFrame]). `0` = không biết ⇒ cửa
     * sổ giữ đúng ô vuông của 2.72 tới khi đo được — KHÔNG đoán tỉ lệ. Hai view GƯƠNG có gợi ý vì crop của chúng
     * lấy từ ảnh 4-in-1 [ĐO RE kinex `Y0/C0094o.java:318,342,347`: pano `5120×960`, một cam `1280×960`], tức chính
     * cái crop đã giả định ảnh nguồn là 4-in-1; các view khác chưa có bằng chứng cỡ nào nên để trống.
     *
     * ## Camera nào là id mấy — [ĐO carlog xe của chính Kachi]
     * `docs/diagnostics/carlog-kachi-20260914-2044/10-logcat-baseline.txt:1776` →
     * `vehicle.config.cam_sort:rear:0;pano_h:1;` (dạng `<tag>:<id>;`, tag là hằng của BYD —
     * `DiLinkCameraConstants.java:19,21` `CAMERA_CAR_PANO_H = "pano_h"` / `CAMERA_CAR_REAR = "rear"`). Tức
     * **AVMCamera phơi ra 2 LUỒNG (id)**, KHÔNG phải "xe chỉ có 2 camera":
     *  • **id 0 = `rear`** — luồng camera lùi, đứng riêng;
     *  • **id 1 = `pano_h`** — 4 camera fisheye (trước · sau · hai gương) đã được HAL **ghép sẵn thành MỘT khung**
     *    `5120×960` = 4 dải DỌC bằng nhau, mỗi dải 25 % bề ngang ([CameraPanoCrop]).
     * Xe Seal Performance VN có **4 camera vật lý** (owner xác nhận 2026-09-26) — chúng đi chung một luồng id 1.
     * Khớp Electro: chuỗi log `selectedCameraId=1 renders-full-frame` (RE `electro-camera-RE-2026-09-26.md` §0-4).
     * ⚠ Tới 2.73 KDoc chỗ này ghi **ngược** ("id 0 fisheye 4-in-1… id 1 cam trước") trong khi enum dưới đây vẫn dùng
     * `cameraId = 1` cho hai view GƯƠNG (tức **code đúng, doc sai**) — sửa 2.74, KHÔNG đổi một hằng nào.
     *
     * Cam GƯƠNG trái/phải KHÔNG phải cameraId riêng — chúng là **CROP một dải của ảnh pano id 1**
     * (RE kinex `Y0/C0094o.java:70,73`: crop trái x[0.25..0.35], phải x[0.65..0.75] của ảnh 5120×960).
     * **Dải nào là hướng nào vẫn [CHƯA BIẾT]** tới khi có khung PNG thật từ xe (RE §7 Q1/Q2) — vì thế chỉ số dải là
     * một **pref** (`camera_strip_left/right`, xem [CameraPanoCrop]), không phải một hằng.
     */
    enum class CamView(
        val outputState: Int,
        val cameraId: Int,
        val labelVi: String,
        val labelEn: String,
        val crop: FloatArray? = null,
        val hintW: Int = 0,
        val hintH: Int = 0,
    ) {
        // ⚠ 2.76: `channel` (kênh HAL per-side) KHÔNG còn ở đây — đó là số đo của MỘT xe (Seal: trái 2 / phải 3
        // [ĐO 27/09]) và nay nằm trong hồ sơ xe (`ClusterProfile.camera` → [CameraProfileDefaults.channel]),
        // đúng CLAUDE.md §7. [P3] review Pass 2 đóng.
        // Gương = cameraId 1 = fisheye 4-in-1 [ĐO owner 2026-09-25: id 1 ra fisheye đúng nguồn] + CROP vùng
        // trái/phải (kinex pano crop trái x[0.25-0.35], phải x[0.65-0.75] của ảnh 4-in-1). id 0 crop ra sai.
        MIRROR_LEFT(1, 1, "Gương trái", "Left mirror", floatArrayOf(0.25f, 0f, 0.35f, 1f), hintW = 5120, hintH = 960),
        MIRROR_RIGHT(2, 1, "Gương phải", "Right mirror", floatArrayOf(0.65f, 0f, 0.75f, 1f), hintW = 5120, hintH = 960),
        FRONT_LEFT(1, 0, "Trước-trái", "Front-left"),
        FRONT_RIGHT(2, 1, "Trước-phải", "Front-right"),
        REAR_LEFT(3, 2, "Sau-trái", "Rear-left"),
        REAR_RIGHT(4, 3, "Sau-phải", "Rear-right"),
        LEFT_FRONT(13, 0, "Trái (trước)", "Left (front)"),
        RIGHT_FRONT(14, 1, "Phải (trước)", "Right (front)");
    }

    /**
     * Mọi góc nhìn người lái chọn được — cũng là THỨ TỰ chip trong Cài đặt.
     *
     * Vì sao để người lái chọn (owner 2026-09-28, trên SL6): *"không mở được cam phải (cam trái ok)"*. Mỗi
     * [CamView] mang HAI số: [CamView.outputState] (bảo HAL xuất hình nào) và [CamView.cameraId] (mở camera
     * nào). Cặp đúng **khác nhau theo đời xe** và chưa đo hết: [ĐO ảnh owner] fisheye 4-in-1 là id 1 trên Seal
     * nhưng id 0 trên SL6. Theo CLAUDE.md §7 thì khác biệt đời xe KHÔNG được rải `if` trong mã — hoặc vào
     * `ClusterProfile`, hoặc để người lái tự dò. Chưa đo đủ để đặt vào hồ sơ xe, nên mở cho người lái dò, và
     * mặc định giữ nguyên [defaultView] để xe đang chạy tốt không đổi một pixel nào (CLAUDE.md §6).
     */
    val VIEWS_ALL: List<CamView> = CamView.entries.toList()

    /** Tên góc đọc từ prefs (sửa tay được qua `prefs_set`) có dùng được không. */
    fun isView(name: String?): Boolean = viewOf(name) != null

    /** [CamView] theo tên, chịu hoa/thường và khoảng trắng thừa; `null` nếu không có tên đó. */
    fun viewOf(name: String?): CamView? {
        val n = name?.trim()?.uppercase().orEmpty()
        return if (n.isEmpty()) null else CamView.entries.firstOrNull { it.name == n }
    }

    /** Mặc định: xi-nhan trái → cam GƯƠNG trái (crop fisheye); phải → gương phải — owner đổi được qua picker. */
    fun defaultView(turn: Turn): CamView? = when (turn) {
        Turn.LEFT -> CamView.MIRROR_LEFT
        Turn.RIGHT -> CamView.MIRROR_RIGHT
        Turn.NONE -> null
    }

    /** Vị trí overlay mặc định: xi-nhan trái → overlay bên TRÁI màn; phải → bên PHẢI (owner: "cùng bên"). */
    fun defaultSide(turn: Turn): Side? = when (turn) {
        Turn.LEFT -> Side.LEFT
        Turn.RIGHT -> Side.RIGHT
        Turn.NONE -> null
    }

    /** Từ trạng thái xi-nhan (leftTurn/rightTurn của CarStatus.lights) → [Turn]. Cả hai bật ⇒ NONE (đèn khẩn). */
    fun turnOf(left: Boolean, right: Boolean): Turn = when {
        left && !right -> Turn.LEFT
        right && !left -> Turn.RIGHT
        else -> Turn.NONE
    }
}
