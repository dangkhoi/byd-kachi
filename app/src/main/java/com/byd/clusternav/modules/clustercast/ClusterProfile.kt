package com.byd.clusternav.modules.clustercast

import android.content.Context
import com.byd.clusternav.launcher.camera.CameraProfileDefaults
import com.byd.clusternav.launcher.camera.ClusterBandSpec
import com.byd.clusternav.SysProps

/**
 * HỒ SƠ CỤM THEO MODEL XE (R8) — tách phần phụ-thuộc-xe ra sau 1 lớp, resolve = auto-detect trước, override sau.
 * Đa-model BYD (Seal · SL6 · Han · Tang…) cùng DiLink3 + XDJA container → recipe Seal-like, biến thiên chính = kích cụm.
 *
 *  • [castSeq]     = chuỗi lệnh AutoContainer bật chiếu. Seal DL3 = [30,16,35] (30=cong giữ km/h, 16=chiếu, 35=DI40).
 *  • [teardownSeq] = chuỗi lệnh tắt chiếu. Seal = [18,0] (18=đóng chiếu, 0=refresh video).
 *  • [vdNameHint]  = tên VD cụm để dò (chứa "xdja"/"fission").
 *
 * Serialize (export/import share nhóm): "id;diLink;W;H;cast(-nối);tear(-nối);vdNameHint;svcName;styleOps".
 * Chuỗi 7 hoặc 8 phần (bản cũ) VẪN NHẬP ĐƯỢC — anh em đã share nhau trong nhóm; thiếu trường nào thì suy ra
 * theo mặc định an toàn (svcName=AutoContainer; styleOps suy từ việc castSeq có chứa opcode 30 hay không).
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
    val svcName: String = "AutoContainer",
    /**
     * ★★ W2-6 (senior review): opcode ĐỔI KIỂU CỤM (cong ↔ thẳng), do CHÍNH HỒ SƠ khai — không đoán từ ngoài.
     * Trước đây `ClusterCast` tự suy: "phần tử nào trong castSeq bằng 30 thì đó là opcode kiểu". DiLink5 có
     * `castSeq = [16]`, không chứa 30 → nút ◠/▭ lưu được, nhãn đổi được, mà **cụm không bao giờ đổi hình**:
     * một nút nói dối. null = đời xe này KHÔNG đổi kiểu được → UI phải ẨN nút đi, không được hiện rồi im lặng.
     */
    val styleOps: Pair<Int, Int>? = 30 to 31,
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
) {
    /** Đời xe này có đổi được kiểu cong/thẳng không — UI dựa vào đây để hiện hay ẩn nút. */
    val supportsStyle: Boolean get() = styleOps != null
    /** Chuỗi text để XUẤT (share nhóm) / lưu override. Round-trip qua [parse]. */
    fun export(): String = listOf(
        id, diLink.toString(), clusterW.toString(), clusterH.toString(),
        castSeq.joinToString("-"), teardownSeq.joinToString("-"), vdNameHint, svcName,
        styleOps?.let { "${it.first}-${it.second}" } ?: ""      // rỗng = đời xe không đổi kiểu được
    ).joinToString(";")

    /**
     * ★★ W2-1 (senior review): lệnh opcode chỉ dựng được TỪ MỘT PROFILE ĐÃ RESOLVE.
     * Trước đây `ClusterCast.svcName` là một field toàn cục chỉ được gán trong `cast()`, còn `stop()`/`rollback()`/
     * `reconcileOnStart()` lại ĐỌC nó ở tiến trình có thể chưa từng chạy `cast()` → trên DiLink5 (service tên
     * `auto_container`) toàn bộ đường trả đồng hồ gửi tới một service KHÔNG TỒN TẠI, im lặng.
     * Đặt hàm dựng lệnh ở đây thì không còn cách nào gọi nhầm: muốn có lệnh phải có profile.
     */
    fun svcCall(n: Int) = "service call $svcName 2 i32 1000 i32 $n s16 \"\""

    /** Mô tả ngắn cho UI. */
    fun summary(): String =
        "$id · DL$diLink · ${clusterW}×$clusterH · chiếu[${castSeq.joinToString(",")}] · tắt[${teardownSeq.joinToString(",")}]"

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

        // ★ SEED đã VERIFY trên xe (2026-07-19): Seal DL3, VD XDJA/fission, chiếu 30→16→35, tắt 18→0.
        //   Tái tạo CHÍNH XÁC sequence hiện tại (behavior-preserving). clusterW/H fallback 1920×720 (auto-detect đè khi có VD thật).
        val SEAL_DL3 = ClusterProfile(
            id = "seal_dl3", diLink = 3, clusterW = 1920, clusterH = 720,
            castSeq = listOf(30, 16, 35), teardownSeq = listOf(18, 0), vdNameHint = "xdja",
            camera = SEAL_DL3_CAMERA,
            // ĐỜI DUY NHẤT đã đo đường cong kính (2.77) ⇒ đời duy nhất được mang bảng `leftEdge`.
            band = ClusterBandSpec.SEAL_DL3,
        )

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
            castSeq = listOf(16), teardownSeq = listOf(18, 0), vdNameHint = "fission", svcName = "auto_container",
            styleOps = null      // DL5 castSeq không có opcode kiểu → KHÔNG đổi cong/thẳng được
        )

        // Model BYD lạ chưa verify: cùng DiLink3 + XDJA (de-risk Q5) → recipe Seal-like, dò xdja/fission.
        val GENERIC_FALLBACK = ClusterProfile(
            id = "generic_dl3", diLink = 3, clusterW = 1920, clusterH = 720,
            castSeq = listOf(30, 16, 35), teardownSeq = listOf(18, 0), vdNameHint = "fission"
        )

        /** tên service chỉ được là chữ/số/_ — chuỗi hồ sơ là dữ liệu KHÔNG TIN CẬY, nó đi thẳng vào lệnh shell. */
        private val SVC_OK = Regex("^[A-Za-z0-9_]{1,32}$")

        private const val PREF = "clustercast"
        private const val KEY_OVERRIDE = "profileOverride"

        /** parse chuỗi export → ClusterProfile. null nếu hỏng (dùng cho import + load override).
         *  VALIDATE (R-hardening): chuỗi share trong nhóm là UNTRUSTED → ép W/H trong (0,8192], diLink 1..9,
         *  mã cast/teardown trong [0,255] (chống nạp mã `service call AutoContainer` tùy ý/âm + bounds suy biến vỡ resize). */
        /**
         * Làm sạch chuỗi DÁN TỪ CHAT trước khi parse: bỏ NBSP/zero-width/BOM, đổi en/em-dash và dấu full-width về
         * ASCII, và LẤY DÒNG CUỐI có 6 HOẶC 7 dấu ';' (6 = định dạng cũ 7 phần, 7 = có thêm tên service).
         * Người dùng hay copy kèm dòng nhãn "Hồ sơ hiện tại (…):" nên phải bỏ dòng nhãn đó đi.
         */
        fun sanitize(s: String): String {
            val cleaned = s
                .replace(' ', ' ').replace("​", "").replace("\uFEFF", "")
                .replace('–', '-').replace('—', '-').replace('−', '-')
                .replace('；', ';').replace('－', '-')
            return cleaned.lineSequence().map { it.trim() }
                .lastOrNull { it.count { c -> c == ';' } in 6..8 } ?: cleaned.trim()
        }

        fun parse(raw: String): ClusterProfile? {
            val f = sanitize(raw).split(";")
            // Tương thích NGƯỢC với chuỗi anh em đã chia sẻ trong nhóm:
            //   7 phần = bản gốc · 8 phần = + svcName · 9 phần = + styleOps.
            if (f.size !in 7..9) return null
            val id = f[0].trim()
            if (id.isEmpty() || id.length > 32) return null
            val diLink = f[1].trim().toIntOrNull() ?: return null
            if (diLink !in 1..9) return null
            val w = f[2].trim().toIntOrNull() ?: return null
            val h = f[3].trim().toIntOrNull() ?: return null
            if (w !in 1..8192 || h !in 1..8192) return null
            val cast = parseSeq(f[4]) ?: return null
            val tear = parseSeq(f[5]) ?: return null
            val hint = f[6].trim()
            val svc = f.getOrNull(7)?.trim()?.takeIf { it.isNotEmpty() && SVC_OK.matches(it) } ?: "AutoContainer"
            // styleOps: có trường thứ 9 thì dùng; chuỗi CŨ (7-8 phần) thì SUY từ castSeq — hồ sơ nào có opcode
            // 30 trong chuỗi chiếu thì đời xe đó đổi được kiểu (đúng như mọi hồ sơ đang lưu hành trước v0.44).
            val style = when (val raw9 = f.getOrNull(8)?.trim()) {
                null -> if (cast.contains(30)) 30 to 31 else null
                "" -> null
                else -> parseSeq(raw9)?.takeIf { it.size == 2 }?.let { it[0] to it[1] }
            }
            return ClusterProfile(
                id, diLink, w, h, cast, tear, hint, svc, style,
                camera = cameraFor(id), band = bandFor(id),
            )
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
         * (Mọi head-unit BYD báo Build.MODEL = "BYD AUTO" → nhận diện bằng chuỗi "byd".)
         */
        fun detectSeed(model: String, brand: String, manufacturer: String, extraProps: String): ClusterProfile {
            val hay = "$model $brand $manufacturer $extraProps".lowercase()
            // ★ DiLink5 phải nhận ra TRƯỚC: nó dùng tên service khác hẳn, đi nhầm nhánh là không chiếu được gì.
            //   Chuỗi nhận diện lấy theo DashCast Platform.java:82.
            if (listOf("dilink5", "dilink_5", "dilink 5").any { hay.contains(it) }) return DL5
            return if (hay.contains("byd")) SEAL_DL3 else GENERIC_FALLBACK
        }

        /** resolve profile: user-override (prefs) ưu tiên; else detect từ Build.MODEL + getprop; else GENERIC_FALLBACK. */
        fun resolve(ctx: Context): ClusterProfile {
            loadOverride(ctx)?.let { return it }
            return detectSeed(
                android.os.Build.MODEL ?: "",
                android.os.Build.BRAND ?: "",
                android.os.Build.MANUFACTURER ?: "",
                getProp("ro.product.model") + " " + getProp("ro.product.name")
            )
        }

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
