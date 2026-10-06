package com.byd.clusternav.launcher.camera

/**
 * ═══ MIỀN GIÁ TRỊ của sáu núm NẮN MÉO — THUẦN, là nguồn sự thật DUY NHẤT của cả prefs lẫn Cài đặt ══════════════
 *
 * R8-B (2.74) · `docs/diagnostics/offcar-2026-09-26/camera-dewarp-gl.md` §Prefs. Sáu núm của [DewarpParams] cộng
 * một công tắc `uTexMatrix` được owner chỉnh **trên xe**, nên chúng phải là **prefs lưu bền** — và miền hợp lệ của
 * chúng phải nằm ở `:core` đúng một chỗ: `PrefsAutomation` (đọc), `TestBridgePrefsSet` (ghi qua cầu kiểm thử) và
 * `SettingsSectionsCar` (hàng −/+) đều tra ở đây, không ai chép một con số nào.
 *
 * ## Vì sao PHẦN TRĂM của bộ suy ra, KHÔNG phải trị tuyệt đối của `F`/`K`
 * `F` và `K` đo bằng **nửa bề ngang ô** ⇒ chúng **tỉ lệ nghịch với bề ngang ô** (`camera-dewarp-math.md` §3.1: dải
 * đầy `1280×960` cho `K = 0,452`, còn vệt hẹp `512×960` của 2.73 cho `K = 1,131` — gấp đúng 2,5 lần). Owner đổi chip
 * *Vùng gương* (`camera_span`: NARROW ⇄ STRIP) hay chip *Dải* là đổi bề ngang ô ⇒ **một trị tuyệt đối vừa chỉnh
 * đúng sẽ lập tức sai** sau một cú chạm ở hàng khác, và owner sẽ đi chỉnh lại từ đầu mà không hiểu vì sao.
 *
 * ⇒ Núm là **phần trăm của giá trị SUY RA từ hình học** ([base]): `100` = đúng bộ mặc định đã suy cho ô đang hiện,
 * bất kể ô ấy rộng bao nhiêu. Owner chỉnh *lệch bao nhiêu phần trăm so với phép suy*, và con số ấy **sống qua** mọi
 * lượt đổi crop. Đây cũng là lý do [apply] nhận [base] chứ không tự dựng: bề ngang ô chỉ tầng vẽ biết.
 *
 * Tâm quang đi theo cùng lẽ nhưng là **độ LỆCH** (`camera_dewarp_cx/cy`, `0` = đúng tâm đã suy): tâm suy ra của
 * crop gương nằm **ngoài** ô (`centerX = 1,25`, xem [CameraDewarp.centerInCrop]), nên một phần trăm *tuyệt đối*
 * của ô sẽ có mặc định khác nhau cho từng dải — tức không có một con số nào viết được vào `Settings` mà đúng cho
 * cả bốn dải. Lệch thì luôn mặc định `0`.
 *
 * ## Bước nhảy của núm — chọn theo thứ owner phân biệt được bằng MẮT trên xe đang đỗ
 * `±5 %` cho bốn núm tỉ lệ (dưới mức đó một cú chạm không thấy gì ⇒ owner sẽ bấm mười lần rồi kết luận núm chết),
 * `±1 %` cho tâm (tâm lệch 5 % là **quá** một bước: nắn lệch tâm thì một bên thẳng bên kia còng, và đó đúng là
 * thứ phải rà từng chút). Bước nhảy ở đây, không ở `SettingsSectionsCar`: hàng −/+ chỉ được cộng/trừ con số này.
 *
 * ## `uTexMatrix` là công tắc, không phải núm
 * RE §7 **Q17 [CHƯA BIẾT]** ma trận `SurfaceTexture.getTransformMatrix` thật của camera id 1 trên xe này. Shader
 * áp **vô điều kiện** (hợp đồng uniform của `camera-dewarp-math.md` §5), nên đường duy nhất để *thử không áp* là
 * `:app` truyền **ma trận đơn vị**. Mặc định BẬT vì đó là thứ AOSP dặn phải làm
 * ([ĐO] `android-10.0.0_r47` `graphics/java/android/graphics/SurfaceTexture.java:44-47`: *"When sampling from the
 * texture one should first transform the texture coordinates using the matrix queried via getTransformMatrix"*).
 */
object CameraDewarpPrefs {

    // ── Độ nắn (`camera_dewarp_amount`) — phần trăm TUYỆT ĐỐI, vì `amount` vốn là một tỉ lệ trộn 0..1 ──────

    const val AMOUNT_MIN = 0

    const val AMOUNT_MAX = 100

    /** `100 %` = nắn đủ, đúng [DewarpParams.DEFAULT_AMOUNT]. Xem KDoc [DewarpParams] về việc `amount` là phép TRỘN. */
    const val AMOUNT_DEFAULT = 100

    const val AMOUNT_STEP = 5

    // ── Tiêu cự · K · Phóng (`camera_dewarp_focal` · `_k` · `_scale`) — % của bộ SUY RA ────────────────────

    /** Trần dưới `25 %`: dưới mức đó `F` bé tới mức cả khung ra chỉ còn vài tia — không phải một ảnh để nhìn. */
    const val PCT_MIN = 25

    /** Trần trên `400 %`: `K` gấp bốn đã với ra **ngoài** vòng ảnh ở mọi ô ⇒ khung toàn viền đen. */
    const val PCT_MAX = 400

    /** `100 %` = đúng giá trị [base] suy ra từ hình học cho ô đang hiện. */
    const val PCT_DEFAULT = 100

    const val PCT_STEP = 5

    // ── Tâm quang (`camera_dewarp_cx` · `_cy`) — độ LỆCH, % bề ô ──────────────────────────────────────────

    /** Lệch tối đa ba bề ô mỗi chiều — đủ để với tới tâm của một dải KHÁC nếu phép suy dải sai (RE §7 Q1/Q2). */
    const val CENTER_MIN = -300

    /** Xem [CENTER_MIN]. */
    const val CENTER_MAX = 300

    /** `0` = đúng tâm quang đã suy ([CameraDewarp.centerInCrop] của tâm dải). */
    const val CENTER_DEFAULT = 0

    const val CENTER_STEP = 1

    // ── Dịch cửa sổ (`camera_dewarp_pan_x` · `_pan_y`) — % bề ô, trong ô CHƯA XOAY ─────────────────────────

    /**
     * Dịch tối đa **nửa ô** mỗi chiều ([DewarpParams.MIN_PAN]) — quá nửa thì tâm quang rơi hẳn ra ngoài cửa sổ.
     *
     * Đây là núm sinh ra từ một yêu cầu **[ĐO] trên xe 27/09**: owner duyệt bộ `F 55 % · K 100 % · S 130 %` ở rot 0
     * (*"thẳng và tự nhiên"*) rồi xin *"chỉ cần dịch 1 tý ra sau nữa thôi"*. Hai đường khác đã bị **bác tại chỗ**:
     * `scale` 140–145 % (*"nặng"*) và `cx −10 %` (hết thẳng — dời tâm quang là đổi chính trục của phép nắn).
     * Dịch cửa sổ là đường còn lại, và là đường duy nhất giữ nguyên độ thẳng — xem KDoc [CameraDewarp.panLocal].
     */
    const val PAN_MIN = -50

    /** Xem [PAN_MIN]. */
    const val PAN_MAX = 50

    /** `0` = đúng khung của 2.74 ⇒ xe không chạm núm thì không thấy khác một pixel nào. */
    const val PAN_DEFAULT = 0

    /**
     * `±5 %` — cùng bước với bốn núm tỉ lệ, KHÔNG phải `1 %` của tâm.
     *
     * Vì sao không `1 %`: cửa sổ gương trên xe [ĐO] rộng `371×495` px ⇒ `1 %` ≈ 4 px, dưới ngưỡng owner phân biệt
     * được khi đang ngồi trên xe (cùng lý lẽ đã chọn `PCT_STEP`). `5 %` ≈ 19–25 px — thấy ngay mà vẫn đủ mịn để
     * *"dịch 1 tý"*, và trọn dải `−50..50` đi hết trong 20 cú chạm.
     */
    const val PAN_STEP = 5

    /** `camera_dewarp_pan_x` · `_pan_y` đọc lên có dùng được không. */
    fun isPanPct(v: Int): Boolean = v in PAN_MIN..PAN_MAX

    // ── κ họ phép chiếu khung ra (2.92 · `camera_wide_kappa`) — phần trăm TUYỆT ĐỐI, `100` = phối cảnh thẳng ─────

    /** `100 %` = κ 1 = phối cảnh thẳng — [DewarpParams.MIN_KAPPA]. */
    const val KAPPA_MIN = 100

    /** `800 %` = κ 8 — [DewarpParams.MAX_KAPPA]. */
    const val KAPPA_MAX = 800

    /** κ mặc định của MỌI phép nắn = 1 (Nắn thẳng hôm nay). Kiểu *Thẳng rộng* có mặc định riêng ở [CameraViewMode]. */
    const val KAPPA_DEFAULT = 100

    /** `camera_wide_kappa` đọc lên có dùng được không. */
    fun isKappaPct(v: Int): Boolean = v in KAPPA_MIN..KAPPA_MAX

    /**
     * ═══ Dấu của `camera_dewarp_pan_x` theo **BÊN** đang xem — một núm, một nghĩa vật lý ở cả hai gương ═══════
     *
     * ## Vì sao phải có dấu — [ĐO khung THÔ xe 2026-09-27 09:58, `camera_frame` 5120×960]
     * Hai camera gương là **ảnh soi gương của nhau**: thân xe của chính mình nằm ở mép **PHẢI** của ô gương trái
     * (dải 1) và ở mép **TRÁI** của ô gương phải (dải 2). Đo bằng tương quan chuẩn hoá trên đúng vùng thân xe của
     * khung ấy: lật ngang rồi so ⇒ **0,715**; không lật ⇒ **0,152** (nền so sánh của hai nửa cùng một ô: 0,550).
     *
     * ⇒ Trục `+x` của ô trỏ về **hai phía ngược nhau** trên hai bên. Một pref dùng chung, cộng thẳng vào `local`
     * như [CameraDewarp.panLocal] làm, sẽ kéo khung gương trái *ra sau* và khung gương phải *ra trước* — owner xin
     * *"kéo ra sau chút"* cho **cả hai** bên thì chỉ được một bên đúng, và bên kia sai mà không có lời báo nào.
     *
     * ## Vì sao bên TRÁI giữ `+1`
     * Nghĩa của núm (*"âm = về phía đuôi xe"*) đã được owner **duyệt bằng mắt trên xe** ở gương trái 27/09
     * (`camera-dewarp-gl.md` §B: `pan_x −20 %`). CLAUDE.md §6 — không đảo đường đã chạy tốt ⇒ bên trái không đổi
     * một ly, chỉ bên phải được nhân `−1` để nói **cùng một câu**.
     *
     * ⚠ Chỉ trục **x** lật: hai camera soi gương quanh trục dọc của xe, nên *"lên/xuống"* (`pan_y`) giống nhau.
     *
     * @param left đang hiện gương TRÁI hay không — một sự thật **lúc chạy** (`turn == Turn.LEFT`), không phải một
     *   nhánh theo tên gói/đời xe (CLAUDE.md §7).
     */
    fun panXSign(left: Boolean): Int = if (left) 1 else -1

    /** `uTexMatrix` mặc định BẬT — xem KDoc lớp về AOSP `SurfaceTexture.java:44-47`. */
    const val TEX_MATRIX_DEFAULT = true

    /** `camera_dewarp_amount` đọc lên có dùng được không (prefs sửa tay được qua `prefs_set`). */
    fun isAmountPct(v: Int): Boolean = v in AMOUNT_MIN..AMOUNT_MAX

    /** `camera_dewarp_focal` · `_k` · `_scale` đọc lên có dùng được không. */
    fun isPct(v: Int): Boolean = v in PCT_MIN..PCT_MAX

    /** `camera_dewarp_cx` · `_cy` đọc lên có dùng được không. */
    fun isCenterPct(v: Int): Boolean = v in CENTER_MIN..CENTER_MAX

    /**
     * Bộ tham số **SUY RA từ hình học** của ô đang hiện — mốc `100 %` của bốn núm tỉ lệ.
     *
     * Chỉ là một lượt chuyển tiếp sang [DewarpParams.derive] (nơi giữ phép suy và mọi cảnh báo về nó), có mặt ở đây
     * để chỗ gọi không phải nhớ *"đường kính vòng ảnh mặc định = bề cao khung"* — giả định **[ĐOÁN]** ấy chốt bằng
     * một khung `5120×960` chụp từ xe (`camera-dewarp-math.md` §3.2 D1).
     *
     * @param cellWidthPx bề ngang ô (= bề rộng crop × bề ngang ảnh nguồn), px.
     * @param cellHeightPx bề cao ô, px.
     * @param imageCircleDiameterPx đường kính vòng ảnh fisheye, px. `<= 0` ⇒ bề cao **ảnh nguồn** (do chỗ gọi truyền).
     */
    fun base(cellWidthPx: Float, cellHeightPx: Float, imageCircleDiameterPx: Float): DewarpParams =
        DewarpParams.derive(
            rectWidthPx = if (cellWidthPx > 0f) cellWidthPx else 1f,
            rectHeightPx = if (cellHeightPx > 0f) cellHeightPx else 1f,
            imageCircleDiameterPx = if (imageCircleDiameterPx > 0f) imageCircleDiameterPx else cellHeightPx,
        )

    /**
     * Áp sáu núm lên [base] ⇒ bộ tham số cuối cùng cho uniform, **đã [DewarpParams.clamped]**.
     *
     * Giá trị ngoài miền ⇒ dùng mặc định của núm ấy (**không** kẹp im lặng về biên): một pref sửa tay hỏng phải cho
     * ra *hành vi mặc định biết trước*, không cho ra một giá trị biên mà owner tưởng là mình đã chọn. Lượt GHI thì
     * từ chối hẳn (`bad_prefs_value:` ở `TestBridgePrefsSet`) — đây là lưới an toàn của lượt ĐỌC.
     *
     * @param centerX,centerY tâm quang đã suy, trong toạ độ **ô** ([CameraDewarp.centerInCrop]); được phép ngoài `[0,1]`.
     */
    fun apply(
        base: DewarpParams,
        centerX: Float,
        centerY: Float,
        amountPct: Int = AMOUNT_DEFAULT,
        focalPct: Int = PCT_DEFAULT,
        kPct: Int = PCT_DEFAULT,
        scalePct: Int = PCT_DEFAULT,
        centerXPct: Int = CENTER_DEFAULT,
        centerYPct: Int = CENTER_DEFAULT,
        panXPct: Int = PAN_DEFAULT,
        panYPct: Int = PAN_DEFAULT,
        kappaPct: Int = KAPPA_DEFAULT,
    ): DewarpParams = DewarpParams(
        amount = pct(amountPct, isAmountPct(amountPct), AMOUNT_DEFAULT),
        focal = base.focal * pct(focalPct, isPct(focalPct), PCT_DEFAULT),
        k = base.k * pct(kPct, isPct(kPct), PCT_DEFAULT),
        scale = base.scale * pct(scalePct, isPct(scalePct), PCT_DEFAULT),
        centerX = centerX + pct(centerXPct, isCenterPct(centerXPct), CENTER_DEFAULT),
        centerY = centerY + pct(centerYPct, isCenterPct(centerYPct), CENTER_DEFAULT),
        panX = pct(panXPct, isPanPct(panXPct), PAN_DEFAULT),
        panY = pct(panYPct, isPanPct(panYPct), PAN_DEFAULT),
        kappa = pct(kappaPct, isKappaPct(kappaPct), KAPPA_DEFAULT),
    ).clamped()

    /** `%` → tỉ lệ; giá trị ngoài miền ⇒ [fallback] (xem KDoc [apply] về *"mặc định biết trước"*). */
    private fun pct(v: Int, ok: Boolean, fallback: Int): Float = (if (ok) v else fallback) / 100f
}
