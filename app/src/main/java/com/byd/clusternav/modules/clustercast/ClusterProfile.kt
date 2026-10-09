package com.byd.clusternav.modules.clustercast

import android.content.Context
import com.byd.clusternav.launcher.camera.CameraProfileDefaults
import com.byd.clusternav.launcher.camera.ClusterBandSpec
import com.byd.clusternav.launcher.escape.TaskBinderCodes
import com.byd.clusternav.modules.navaccess.AccessibilityRebind
import com.byd.clusternav.SysProps
import com.byd.clusternav.modules.clustercast.simplified.CastStyle
import com.byd.clusternav.modules.clustercast.simplified.ClusterCarType
import com.byd.clusternav.modules.clustercast.simplified.ProjectionRecipe

/**
 * HỒ SƠ CỤM THEO MODEL XE (R8) — tách phần phụ-thuộc-xe ra sau 1 lớp, resolve = auto-detect trước, override sau.
 * Đa-model BYD (Seal · SL6 · Han · Tang…) cùng DiLink3 + XDJA container → recipe Seal-like, biến thiên chính = kích cụm.
 *
 *  • [castSeq]     = chuỗi lệnh AutoContainer bật chiếu, KHÔNG có opcode theme (B1a 2.89). Seal DL3 = [16,35] (16=chiếu,
 *                    35=DI40); opcode ép kiểu (30=cong giữ km/h) nằm ở [styleOps].
 *  • [teardownSeq] = chuỗi lệnh tắt chiếu. Seal = [18,0] (18=đóng chiếu, 0=refresh video).
 *  • [vdNameHint]  = tên VD cụm để dò (chứa "xdja"/"fission").
 *
 * Serialize (export/import share nhóm): "id;diLink;W;H;cast(-nối);tear(-nối);vdNameHint;svcName;styleOps[;nativeStyle]".
 * DẠNG DÂY GIỮ NGUYÊN cho bản cũ: trường cast xuất `<opcode CURVED>-<castSeq>` (Seal vẫn `30-16-35`), trường 9 = `C-R`
 * (opcode cong-chữ nhật); trường 10 (`RECT`/`CURVED`) chỉ xuất khi kiểu gốc đã biết. Chuỗi 7–9 phần (bản cũ) VẪN NHẬP ĐƯỢC:
 * opcode theme đầu tiên trong trường cast được BÓC ra làm `styleOps[CURVED]` ([ProjectionRecipe.peelTheme]) — đúng thứ chuỗi
 * cũ gửi lúc mở chiếu; svcName vắng = AutoContainer.
 * Phần companion PURE (parse/export/detectSeed) không đụng Context → unit-test off-device được; [resolve] mới cần ctx.
 *
 * ## [camera] — mặc định CAMERA theo đời xe (2.76 · spec `kachi-276-closing.html` R2)
 * KHÔNG nằm trong chuỗi export/parse: bản đồ kênh HAL, bộ nắn ống kính, chiều ảnh là **số đo của một đời DiLink**,
 * không phải thứ anh em chỉnh tay rồi share (chuỗi share vốn là *"chiếu cụm thế nào"*). [parse] gán nó theo `id` của
 * seed đã đo ([cameraFor]); id lạ ⇒ [CameraProfileDefaults.NEUTRAL] = đúng hành vi 2.75.
 */
data class ClusterProfile(
    val id: String,
    val diLink: Int,
    val clusterW: Int,
    val clusterH: Int,
    val castSeq: List<Int>,
    val teardownSeq: List<Int>,
    val vdNameHint: String,
    /**
     * ★ TÊN SERVICE AutoContainer — KHÁC NHAU THEO ĐỜI DiLink (RE từ DashCast v1.5.4, 2026-07-21):
     * DiLink 2/3/4 = `AutoContainer` · **DiLink 5 = `auto_container`** (chữ thường).
     * `ClusterManager.SERVICE_NAME = "AutoContainer"` và `DiagActivity:2287`
     * `Platform.get().isDiLink5(this) ? "auto_container" : ClusterManager.SERVICE_NAME`.
     * Trước đây ClusterNav hardcode "AutoContainer" → trên DiLink5 mọi lệnh chiếu đều rơi vào hư không.
     */
    val svcName: String = ProjectionRecipe.SVC_DILINK3,
    /**
     * ★★ W2-6 (senior review): opcode ĐỔI KIỂU CỤM, do CHÍNH HỒ SƠ khai — không đoán từ ngoài. DiLink5 không có opcode kiểu
     * ⇒ rỗng ⇒ UI phải ẨN lựa chọn, không được hiện rồi im lặng.
     * B1a (2.89): `Map<CastStyle, Int>` thay `Pair`; CURVED = opcode gửi trước chuỗi chiếu (Seal 30, đường đã chạy từ 08/02),
     * RECT = opcode ép chữ nhật (31 [ĐO xe 05/10]) — chỉ HIỆN khi [nativeStyle] = RECT ([ProjectionRecipe.offers]).
     */
    val styleOps: Map<CastStyle, Int> = DEFAULT_STYLE_OPS,
    /**
     * Kiểu GỐC của cụm sau nổ máy — sự thật phần cứng, [resolve] đặt theo `persist.sys.car.type` ([forCarType]); `null` =
     * chưa biết ⇒ RECT ẩn. Trường export thứ 10 chỉ để chuỗi share mang theo thông tin (round-trip), không đè được dò thật.
     */
    val nativeStyle: CastStyle? = null,
    /**
     * Mặc định camera của đời xe này — pref VẮNG mới lấy ở đây, pref đã đặt tường minh luôn thắng
     * (`Prefs.camera*`). Seal DL3 = bộ [ĐO 27/09] owner duyệt; mọi đời khác = [CameraProfileDefaults.NEUTRAL].
     */
    val camera: CameraProfileDefaults = CameraProfileDefaults.NEUTRAL,
    /**
     * Số đo DẢI GIỮA của cụm cho hình camera *theo cụm* (2.76 R4; nợ chéo L2 → L7). **Không** vào [export]/[parse] —
     * chuỗi override do owner chia sẻ chỉ mang thứ đã đo được bằng tay trên xe (cụm, chuỗi lệnh, tên VD); dải cụm là
     * số của bộ vẽ, đổi theo seed đời xe, không phải thứ ai gõ vào. `CameraSignalController` đọc qua
     * `CameraDefaults.band`; đời xe chọn bộ nào thì [bandFor] nói.
     *
     * ⚠ 2.77 — mặc định là [ClusterBandSpec.SEAL_DL3_NO_CURVE] (**không** đường cong), không phải
     * [ClusterBandSpec.SEAL_DL3]: từ 2.77 bảng `leftEdge` không chỉ đặt cửa sổ mà còn **CẮT** điểm ảnh theo miếng
     * kính của Seal, nên đời cụm chưa đo phải rơi về tường thẳng của 2.76 chứ không được xén theo kính xe khác
     * (CLAUDE.md §7). Xem KDoc [ClusterBandSpec.SEAL_DL3_NO_CURVE].
     */
    val band: ClusterBandSpec = ClusterBandSpec.SEAL_DL3_NO_CURVE,
    /**
     * Dấu hiệu MÀN CAMERA (lùi/360) của đời xe trong `am stack list` — đầu vào của rào `CameraGuard.unlessCamera` cho
     * mọi lệnh đưa app lên TRƯỚC display 0 (spec shortcuts-autostart §4.8). Không vào [export]/[parse], gán theo id như
     * [camera]. Seal DL3 = `com.byd.avc/` [ĐO dump SurfaceFlinger + dịch ngược 28/09]; đời khác `null` [CHƯA BIẾT] ⇒
     * tính năng mới KHÔNG mở gì lên trước display 0 (CLAUDE.md §7 — khác biệt đời xe nằm ở đây, không rải trong mã).
     */
    val cameraSignature: String? = null,
    /**
     * CLUSTER-THEME-SAFE: cho phép gửi theme khi màn ảo cụm CÒN mà trống (mức B). Mặc định `false`; [forCarType] bật cho mọi đời
     * service `AutoContainer` (DiLink3 — 2.95, trước đó chỉ mã 138; DiLink5 không có opcode theme): [ĐO xe Seal 06/10] màn ảo cụm có từ lúc đầu máy
     * khởi động (mức A không bao giờ gửi được) và gửi `31` khi màn ảo có 0 task + 0 cửa sổ ⇒ không sập, màn ảo dựng lại id mới.
     * Không vào [export]/[parse]: chuỗi share (không tin cậy) không được bật đường đổi theme [ĐO 05/10: sập khi còn lớp].
     */
    val themeOnVacantVd: Boolean = false,
    /**
     * 2.98 · R7 (SLOT-ESCAPE-POLICY) — bảng mã binder `activity_task` cho việc đưa app thoát ô về khung ô bằng freeform
     * (`SlotEscape`). Mặc định = Android 10 r47 [ĐO xe Seal 09/10 + máy ảo A10]; chỉ có hiệu lực khi đúng đời API của bảng
     * (`TaskBinderCodes.usableOn`). DiLink 5 (Android 12) ⇒ `null` ⇒ R7 tắt tới khi đo (OQ6-c). Không vào [export]/[parse].
     */
    val taskBinder: TaskBinderCodes? = TaskBinderCodes.ANDROID_10_R47,
) {
    /** Đời xe này cho chọn Bo tròn / Chữ nhật không — UI dựa vào đây để hiện hay ẩn lựa chọn (B1b). */
    val supportsStyle: Boolean get() = projectionRecipe().let { it.offers(CastStyle.CURVED) && it.offers(CastStyle.RECT) }

    /**
     * Chuỗi text để XUẤT (share nhóm) / lưu override. Round-trip qua [parse]. Dạng dây giữ cho bản cũ: trường cast có opcode
     * CURVED đứng đầu (bản cũ gửi nguyên chuỗi đó), trường 9 `C-R`; trường 10 chỉ khi kiểu gốc đã biết.
     */
    fun export(): String {
        val curved = styleOps[CastStyle.CURVED]
        val rect = styleOps[CastStyle.RECT]
        val wireCast = listOfNotNull(curved) + castSeq
        val style = if (curved == null) "" else listOfNotNull(curved, rect).joinToString("-")   // rỗng = không đổi kiểu
        val fields = listOf(
            id, diLink.toString(), clusterW.toString(), clusterH.toString(),
            wireCast.joinToString("-"), teardownSeq.joinToString("-"), vdNameHint, svcName, style,
        ) + listOfNotNull(nativeStyle?.name)
        return fields.joinToString(";")
    }

    /**
     * B1a — hồ sơ cho ĐÚNG đời xe [carType] (`persist.sys.car.type`; `null` = không đọc được ⇒ xe lạ): kiểu gốc RECT chỉ
     * khi mã nằm trong [RECT_NATIVE_CAR_TYPES] và hồ sơ có opcode ép RECT; mọi trường hợp khác `null` (RECT ẩn — bảng B.2).
     * Áp cho cả override: kiểu gốc là sự thật phần cứng của CHIẾC xe này, không phải của chuỗi share.
     */
    fun forCarType(carType: String?): ClusterProfile {
        val native = if (carType != null && carType in RECT_NATIVE_CAR_TYPES && styleOps[CastStyle.RECT] != null) {
            CastStyle.RECT
        } else {
            null
        }
        // 2.95 · CLUSTER-VACANT-THEME-DL3 — mức B cho MỌI đời DiLink3 `AutoContainer` (không còn theo mã xe): [ĐO log SL6 07/10]
        // màn ảo cụm có sẵn từ lúc nổ máy ⇒ 2.89–2.94 bỏ `30` ở MỌI lượt (`Skip(VD_PRESENT)`) ⇒ cụm cong không vào chế độ chiếu
        // (GMaps nằm trên màn ảo, cụm hiện đồng hồ); tới 06/10 08:47 — trước cổng theme — mọi phiên gửi `30` và chiếu được. Rào an
        // toàn của mức B giữ nguyên (màn ảo trống, gỡ placeholder trước, giãn 15 s, bóng nổi). Kể cả với override.
        val vacant = svcName == ProjectionRecipe.SVC_DILINK3
        return if (native == nativeStyle && vacant == themeOnVacantVd) this else copy(nativeStyle = native, themeOnVacantVd = vacant)
    }

    /**
     * ★★ W2-1 (senior review): lệnh opcode chỉ dựng được TỪ MỘT PROFILE ĐÃ RESOLVE.
     * Trước đây `ClusterCast.svcName` là một field toàn cục chỉ được gán trong `cast()`, còn `stop()`/`rollback()`/
     * `reconcileOnStart()` lại ĐỌC nó ở tiến trình có thể chưa từng chạy `cast()` → trên DiLink5 (service tên
     * `auto_container`) toàn bộ đường trả đồng hồ gửi tới một service KHÔNG TỒN TẠI, im lặng.
     * Đặt hàm dựng lệnh ở đây thì không còn cách nào gọi nhầm: muốn có lệnh phải có profile.
     */
    fun svcCall(n: Int) = ProjectionRecipe.svcCall(svcName, n)

    /**
     * CLUSTER-THEME-SAFE (2.89): công thức lệnh chiếu cho đường SimpleCast ở `:core` — `castSeq`/`teardownSeq`/`svcName`/
     * `styleOps` trước đây là trường CHẾT với đường đó (`ProjectionManager` ghi cứng 30-16-35). Seal DL3 ra đúng chuỗi cũ;
     * opcode cấm (17, 41 — ghi bền) bị lọc; opcode theme chỉ đi qua cổng `ClusterThemeGuard` + `ClusterStylePlan`.
     */
    fun projectionRecipe(): ProjectionRecipe =
        ProjectionRecipe.of(svcName, castSeq, teardownSeq, styleOps, nativeStyle, themeOnVacantVd)

    /** Mô tả ngắn cho UI. */
    fun summary(): String =
        "$id · DL$diLink · ${clusterW}×$clusterH · chiếu[${(listOfNotNull(styleOps[CastStyle.CURVED]) + castSeq).joinToString(",")}]" +
            " · tắt[${teardownSeq.joinToString(",")}] · kiểu gốc ${nativeStyle ?: "?"}"

    companion object {
        /**
         * ═══ Camera Seal DL3 — bộ owner DUYỆT trên xe [ĐO 27/09 10:30–11:30, backlog `ONCAR-2026-09-27` F1/CAM-B1] ═══
         *
         *  • `span = STRIP` (trọn dải 0,25 — vệt hẹp 0,10 của 2.73 là rìa vòng fisheye, méo như ống — RE Electro K10);
         *  • `render = GL` (đường nắn; Adreno 610 `GL_MAX_TEXTURE_SIZE = 16384` — CAM-B1 chốt Q13);
         *  • `amount 100 · focal 55 · k 100 · scale 130 · cx 0 · cy 0` — owner: *"thẳng, tự nhiên, thấy 2 bánh"*;
         *    bác bỏ tại chỗ: `cx/cy ≠ 0` (cong), `scale ≥ 140` ("heavy");
         *  • `pan 0/0` — núm 2.75, chiều tuyệt đối còn 🚗 G7;
         *  • `rot 0/0` — khung HAL vốn ĐỨNG (F1: 4 dải fisheye đứng, mặt đất dưới) và research
         *    `research-side-camera-orientation-2026-09-27.md` §6.1: 18/20 hệ [ĐO] hiện ĐỨNG ⇒ mặc định đứng.
         *    ⚠ Pref `↺90` owner đã đặt trên xe **không** bị ghi đè (pref thắng) — chốt bằng 🚗 CAM-C2;
         *  • ⚠ 2.77: **không còn** bản đồ kênh HAL. [ĐO 11:16] `addPreviewSurface(surface, n)` cho `trái = 2 ·
         *    phải = 3` (bản đồ đầy đủ: 1 = sau · 2 = trái · 3 = phải · 4 = trước, owner xác nhận từng kênh) — số đo
         *    ấy giữ ở `camera-ia-profile.md` §7 làm **kiến thức**, nhưng nguồn *Một camera* đã bỏ hẳn: cùng cỡ cảnh,
         *    dải ghép có năng lượng cạnh **686 vs 351** và tỉ lệ chi tiết ngang/dọc **0,30 vs 0,19** ⇒ một kênh chỉ
         *    bị kéo ngang, không nét hơn.
         *
         * Mọi số ở đây là **số đo**, không phải phép suy. Đời xe khác KHÔNG chép bộ này (KDoc [CameraProfileDefaults]).
         */
        // ⚠ Khai TRƯỚC [SEAL_DL3]: companion khởi tạo theo thứ tự khai, một `val` tham chiếu tới cái đứng SAU là null lúc chạy.
        val SEAL_DL3_CAMERA = CameraProfileDefaults(
            span = com.byd.clusternav.launcher.camera.CameraSignalPolicy.SPAN_STRIP,
            render = com.byd.clusternav.launcher.camera.CameraSignalPolicy.RENDER_GL,
            amountPct = 100, focalPct = 55, kPct = 100, scalePct = 130,
            centerXPct = 0, centerYPct = 0, panXPct = 0, panYPct = 0,
            rotLeft = com.byd.clusternav.launcher.camera.CameraSignalPolicy.ROTATE_NONE,
            rotRight = com.byd.clusternav.launcher.camera.CameraSignalPolicy.ROTATE_NONE,
        )

        /**
         * Mặc định opcode kiểu (DiLink 3): 30 = cong 12.3" (đường đã chạy từ 08/02), 31 = chữ nhật 10.25" [ĐO xe 05/10]. Khai
         * TRƯỚC các seed (companion khởi tạo theo thứ tự khai).
         */
        val DEFAULT_STYLE_OPS: Map<CastStyle, Int> = mapOf(CastStyle.CURVED to 30, CastStyle.RECT to 31)

        /**
         * Mã `persist.sys.car.type` có cụm GỐC chữ nhật (theme2 10.25"): 138 = Seal [ĐO getprop 14/09 + 29/09; ĐO-gv 05/10 tắt/
         * mở máy ⇒ cụm về theme2]. Mã khác (SL6 162 gốc cong [ĐO-RE], cụm 8.8"…) ⇒ RECT ẩn: 31 trên cụm 8.8" đẩy cụm về
         * "simple mode" [ĐO DashCast INC-20260625]. Thêm mã CHỈ sau khi đo trên xe đó (CLAUDE.md §7, §14).
         */
        val RECT_NATIVE_CAR_TYPES: Set<String> = setOf("138")

        // ★ SEED đã VERIFY trên xe (2026-07-19): Seal DL3, VD XDJA/fission, chiếu 30→16→35, tắt 18→0.
        //   Tái tạo CHÍNH XÁC sequence hiện tại (behavior-preserving): 30 nằm ở styleOps[CURVED] (B1a), chuỗi dây vẫn 30-16-35.
        //   clusterW/H fallback 1920×720 (auto-detect đè khi có VD thật).
        val SEAL_DL3 = ClusterProfile(
            id = "seal_dl3", diLink = 3, clusterW = 1920, clusterH = 720,
            castSeq = listOf(16, 35), teardownSeq = listOf(18, 0), vdNameHint = "xdja",
            camera = SEAL_DL3_CAMERA,
            // ĐỜI DUY NHẤT đã đo đường cong kính (2.77) ⇒ đời duy nhất được mang bảng `leftEdge`.
            band = ClusterBandSpec.SEAL_DL3,
            cameraSignature = AccessibilityRebind.CAMERA_SCREEN_SIGNATURE,
        )

        /** 2.98 · R7 — bảng mã binder theo đời DiLink: 5 (Android 12) chưa đo ⇒ `null`; còn lại = A10 r47 (vẫn bị chặn theo API lúc chạy). */
        fun taskBinderFor(diLink: Int): TaskBinderCodes? = if (diLink >= 5) null else TaskBinderCodes.ANDROID_10_R47

        /** Dấu hiệu màn camera theo `id` seed — cùng luật với [cameraFor]: chỉ đời ĐÃ ĐO mới có; khác ⇒ `null`. */
        fun cameraSignatureFor(id: String): String? = if (id == SEAL_DL3.id) SEAL_DL3.cameraSignature else null

        /** Mặc định camera theo `id` seed: chỉ seed ĐÃ ĐO mới có bộ riêng; id lạ/đời khác ⇒ trung tính. */
        fun cameraFor(id: String): CameraProfileDefaults =
            if (id == SEAL_DL3.id) SEAL_DL3_CAMERA else CameraProfileDefaults.NEUTRAL

        /**
         * Dải cụm theo `id` seed — cùng luật với [cameraFor]: chỉ đời **ĐÃ ĐO đường cong kính** mới mang bảng
         * `leftEdge`; id lạ/đời khác ⇒ [ClusterBandSpec.SEAL_DL3_NO_CURVE] = tường thẳng, đúng hành vi 2.76.
         */
        fun bandFor(id: String): ClusterBandSpec =
            if (id == SEAL_DL3.id) ClusterBandSpec.SEAL_DL3 else ClusterBandSpec.SEAL_DL3_NO_CURVE

        /**
         * DiLink 5 (Android 12) — RE từ DashCast: service đổi thành `auto_container`, VD tên
         * `fission_bg_XDJAScreenProjection` (có hậu tố _0/_1/…), và ROM **cắt bỏ**
         * `cmd activity set-task-windowing-mode`; `cmd activity task resize` trả exit 0 mà KHÔNG có tác dụng
         * (DashCast CHANGELOG 1.2.59-beta, log field-test byd_report_20260528). ⇒ trên DL5 đừng trông vào resize.
         */
        val DL5 = ClusterProfile(
            id = "dilink5", diLink = 5, clusterW = 1920, clusterH = 720,
            castSeq = listOf(16), teardownSeq = listOf(18, 0), vdNameHint = "fission", svcName = ProjectionRecipe.SVC_DILINK5,
            styleOps = emptyMap(),     // DL5 không có opcode kiểu (30 là no-op trên DL5 — DashCast CHANGELOG:591) → KHÔNG đổi kiểu
            taskBinder = taskBinderFor(5),   // 2.98 · R7: mã binder Android 12 chưa đo (OQ6-c) ⇒ null ⇒ không đưa app thoát ô về khung
        )

        // Model BYD lạ chưa verify: cùng DiLink3 + XDJA (de-risk Q5) → recipe Seal-like (30 → 16 → 35), dò xdja/fission.
        val GENERIC_FALLBACK = ClusterProfile(
            id = "generic_dl3", diLink = 3, clusterW = 1920, clusterH = 720,
            castSeq = listOf(16, 35), teardownSeq = listOf(18, 0), vdNameHint = "fission"
        )

        /** Tệp prefs của hồ sơ đời xe — phạm vi XE (`ProfileScopeCluster.DEVICE_KEYS`). Cũng giữ sổ theme ([ThemeLedger.KEY]). */
        const val PREF = "clustercast"
        private const val KEY_OVERRIDE = "profileOverride"

        /** B1a — mã `car.type` đọc được qua dadb (khi tiến trình không đọc được prop) — sự thật phần cứng, phạm vi XE. */
        const val KEY_CAR_TYPE = "car_type_dadb"

        /** parse chuỗi export → ClusterProfile. null nếu hỏng (dùng cho import + load override).
         *  VALIDATE (R-hardening): chuỗi share trong nhóm là UNTRUSTED → ép W/H trong (0,8192], diLink 1..9,
         *  mã cast/teardown trong [0,255] (chống nạp mã `service call AutoContainer` tùy ý/âm + bounds suy biến vỡ resize). */
        /**
         * Làm sạch chuỗi DÁN TỪ CHAT trước khi parse: bỏ NBSP/zero-width/BOM, đổi en/em-dash và dấu full-width về
         * ASCII, và LẤY DÒNG CUỐI có 6..9 dấu ';' (6 = định dạng cũ 7 phần … 9 = có thêm kiểu gốc, B1a).
         * Người dùng hay copy kèm dòng nhãn "Hồ sơ hiện tại (…):" nên phải bỏ dòng nhãn đó đi.
         */
        fun sanitize(s: String): String {
            val cleaned = s
                .replace(' ', ' ').replace("​", "").replace("\uFEFF", "")
                .replace('–', '-').replace('—', '-').replace('−', '-')
                .replace('；', ';').replace('－', '-')
            return cleaned.lineSequence().map { it.trim() }
                .lastOrNull { it.count { c -> c == ';' } in 6..9 } ?: cleaned.trim()
        }

        fun parse(raw: String): ClusterProfile? {
            val f = sanitize(raw).split(";")
            // Tương thích NGƯỢC với chuỗi anh em đã chia sẻ trong nhóm:
            //   7 phần = bản gốc · 8 phần = + svcName · 9 phần = + styleOps · 10 phần = + kiểu gốc (B1a).
            if (f.size !in 7..10) return null
            val id = f[0].trim()
            if (id.isEmpty() || id.length > 32) return null
            val diLink = f[1].trim().toIntOrNull() ?: return null
            if (diLink !in 1..9) return null
            val w = f[2].trim().toIntOrNull() ?: return null
            val h = f[3].trim().toIntOrNull() ?: return null
            if (w !in 1..8192 || h !in 1..8192) return null
            val wireCast = parseSeq(f[4]) ?: return null
            val tear = parseSeq(f[5]) ?: return null
            val hint = f[6].trim()
            val svc = f.getOrNull(7)?.trim()?.takeIf { it.isNotEmpty() && ProjectionRecipe.svcOk(it) } ?: ProjectionRecipe.SVC_DILINK3
            // B1a: opcode theme đầu tiên của trường cast = styleOps[CURVED] (thứ chuỗi cũ thật sự gửi lúc mở chiếu); phần còn
            // lại là castSeq. Opcode cấm KHÔNG bị lọc ở đây (lọc ở tầng dựng lệnh — ProjectionRecipe.of — để tệp nhập thấy được).
            // Opcode trường 9 khai cũng tính là opcode theme khi bóc (chuỗi khai kiểu lạ ⇒ không bao giờ gửi ngoài cổng).
            val raw9 = f.getOrNull(8)?.trim()
            val declared9 = raw9?.takeIf { it.isNotEmpty() }?.let(::parseSeq).orEmpty()
            val (curved, castLeft) = peelLegacy(wireCast, declared9.toSet())
            // RECT: trường 9 dạng `C-R` thì lấy R; chuỗi 7–8 phần thì suy như trước v0.44 (có 30 ⇒ đổi được sang 31).
            val rect = if (raw9 == null) (if (curved == 30) 31 else null) else declared9.getOrNull(1)
            val style = buildMap {
                if (curved != null) put(CastStyle.CURVED, curved)
                if (curved != null && rect != null) put(CastStyle.RECT, rect)
            }
            val native = f.getOrNull(9)?.trim()?.let { raw -> CastStyle.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
            return ClusterProfile(
                id, diLink, w, h, castLeft, tear, hint, svc, style, native,
                camera = cameraFor(id), band = bandFor(id), cameraSignature = cameraSignatureFor(id), taskBinder = taskBinderFor(diLink),
            )
        }

        /**
         * Bóc opcode theme ĐẦU TIÊN (29/30/31 ∪ [declared]) khỏi trường cast của chuỗi dây; mọi opcode theme khác cũng rời
         * chuỗi chiếu; opcode thường (kể cả opcode cấm, để tầng dựng lệnh lọc và tệp nhập vẫn thấy) giữ nguyên thứ tự.
         */
        private fun peelLegacy(wire: List<Int>, declared: Set<Int>): Pair<Int?, List<Int>> {
            val theme = ProjectionRecipe.KNOWN_THEME_OPS + declared
            val first = wire.firstOrNull { it in theme }
            return first to wire.filter { it !in theme }
        }

        /** "30-16-35" → [30,16,35]. Rỗng → []. null nếu có phần không phải số / ngoài dải [0,255] / quá dài (>16). */
        private fun parseSeq(s: String): List<Int>? {
            if (s.isBlank()) return emptyList()
            val parts = s.split("-")
            if (parts.size > 16) return null
            val out = ArrayList<Int>(parts.size)
            for (p in parts) {
                val n = p.trim().toIntOrNull() ?: return null
                if (n !in 0..255) return null
                out.add(n)
            }
            return out
        }

        /**
         * Detect SEED từ Build + getprop (PURE, offline). Fleet nhóm = BYD DL3 XDJA → [SEAL_DL3]; khác → [GENERIC_FALLBACK].
         * (Mọi head-unit BYD báo Build.MODEL = "BYD AUTO" → nhận diện bằng chuỗi "byd".) B1a: [carType] (`persist.sys.car.type`,
         * `null` = không đọc được) đặt kiểu gốc qua [forCarType] — Seal 138 ⇒ RECT, còn lại ⇒ RECT ẩn.
         */
        fun detectSeed(
            model: String, brand: String, manufacturer: String, extraProps: String, carType: String? = null,
        ): ClusterProfile {
            val hay = "$model $brand $manufacturer $extraProps".lowercase()
            // ★ DiLink5 phải nhận ra TRƯỚC: nó dùng tên service khác hẳn, đi nhầm nhánh là không chiếu được gì.
            //   Chuỗi nhận diện lấy theo DashCast Platform.java:82.
            if (listOf("dilink5", "dilink_5", "dilink 5").any { hay.contains(it) }) return DL5.forCarType(carType)
            return (if (hay.contains("byd")) SEAL_DL3 else GENERIC_FALLBACK).forCarType(carType)
        }

        /**
         * resolve profile: user-override (prefs) ưu tiên; else detect từ Build.MODEL + getprop; else GENERIC_FALLBACK. Kiểu gốc
         * LUÔN theo `car.type` của chính xe ([carType]) — kể cả với override.
         */
        fun resolve(ctx: Context): ClusterProfile {
            val carType = carType(ctx)
            loadOverride(ctx)?.let { return it.forCarType(carType) }
            return detectSeed(
                android.os.Build.MODEL ?: "",
                android.os.Build.BRAND ?: "",
                android.os.Build.MANUFACTURER ?: "",
                getProp("ro.product.model") + " " + getProp("ro.product.name"),
                carType,
            )
        }

        /**
         * B1a — `persist.sys.car.type` của xe: đọc TRONG tiến trình ([SysProps], không shell — an toàn trên luồng chính); không
         * được ⇒ mã đã dò qua dadb lần trước ([KEY_CAR_TYPE], [refineByShell]); không có ⇒ `null` = xe lạ. [CHƯA BIẾT d] uid app
         * có đọc được prop này không.
         */
        fun carType(ctx: Context): String? =
            ClusterCarType.parse(getProp(ClusterCarType.PROP))
                ?: ClusterCarType.parse(prefs(ctx).all[KEY_CAR_TYPE] as? String)

        /**
         * B1a — đường lùi dadb cho [carType]: chỉ chạy khi tiến trình KHÔNG đọc được prop và chưa có mã đã dò. [read] chạy MỘT
         * lệnh ĐỌC ([ClusterCarType.CMD]) trên kênh shell của bên gọi (không bao giờ luồng chính). Đọc được ⇒ ghi mã (phạm vi
         * XE), xoá đệm, trả hồ sơ MỚI; không có gì mới ⇒ `null`. Không ném.
         */
        fun refineByShell(ctx: Context, read: (String) -> String?): ClusterProfile? {
            if (ClusterCarType.parse(getProp(ClusterCarType.PROP)) != null) return null
            if (ClusterCarType.parse(prefs(ctx).all[KEY_CAR_TYPE] as? String) != null) return null
            val type = ClusterCarType.parse(runCatching { read(ClusterCarType.CMD) }.getOrNull()) ?: return null
            prefs(ctx).edit().putString(KEY_CAR_TYPE, type).apply()
            cached = null
            return resolve(ctx)
        }

        private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        /**
         * [resolve] có nhớ đệm — cho tầng prefs camera hỏi *"mặc định của xe này"* ở **mỗi** lượt đọc khoá vắng
         * (vài lần mỗi lượt xi-nhan) mà không mở prefs + reflection `getprop` lại từng lần. Build.* không đổi trong
         * một tiến trình; override chỉ đổi qua [saveOverride]/[clearOverride] ⇒ hai chỗ ấy xoá đệm. `@Volatile`:
         * đọc từ luồng socket xi-nhan lẫn main.
         */
        fun resolveCached(ctx: Context): ClusterProfile =
            cached ?: resolve(ctx).also { cached = it }

        @Volatile
        private var cached: ClusterProfile? = null

        fun loadOverride(ctx: Context): ClusterProfile? {
            val s = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(KEY_OVERRIDE, "") ?: ""
            return if (s.isBlank()) null else parse(s)
        }

        fun saveOverride(ctx: Context, p: ClusterProfile) {
            ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY_OVERRIDE, p.export()).apply()
            cached = null
        }

        fun clearOverride(ctx: Context) {
            ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().remove(KEY_OVERRIDE).apply()
            cached = null
        }

        /** getprop in-proc — uỷ quyền [SysProps.get] (một cửa reflection cho cả app, L6-debt 2026-09-27). "" nếu lỗi/off-device. */
        private fun getProp(key: String): String = SysProps.get(key)
    }
}
