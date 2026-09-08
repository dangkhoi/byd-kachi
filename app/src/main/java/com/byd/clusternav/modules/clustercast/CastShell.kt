package com.byd.clusternav.modules.clustercast

/**
 * ĐỘNG TÁC SHELL DÙNG CHUNG cho đường chiếu — tách khỏi [ClusterCast] để file điều phối không phình quá ngưỡng
 * guardrail của repo, và để từng động tác đọc/test được độc lập.
 *
 * Mọi hàm ở đây nhận `sh` (chạy 1 lệnh shell qua dadb, trả stdout) + `log` (đẩy ra panel log của màn Chiếu) —
 * KHÔNG giữ state của phiên chiếu (state nằm ở [ClusterCast]).
 */
internal object CastShell {
    /**
     * Poll tới khi [pkg] thực sự nằm trên [vd] (LOẠI stack pinned = PIP), tối đa [timeoutMs]. Thay `sleep(900)` mù cũ:
     * app nặng (projection/video) khởi động chậm hơn 900ms → verdict sai → rơi xuống rung phá hoại một cách oan uổng.
     */
    fun landedOn(sh: (String) -> String, pkg: String, vd: Int, timeoutMs: Long = 2500, stepMs: Long = 250): StackEntry? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val ents = StackParse.parse(sh("am stack list"))
            StackParse.of(ents, pkg).firstOrNull { it.displayId == vd && !it.isPinned }?.let { return it }
            if (System.currentTimeMillis() >= deadline) return null
            Thread.sleep(stepMs)
        }
    }

    /** `am display move-stack` bị từ chối? (display/stack không tồn tại, đã ở đó, thiếu quyền…) */
    fun moveRejected(o: String) =
        o.contains("Exception", true) || o.contains("Error", true) || o.contains("does not exist", true)

    /** In TỪNG DÒNG output shell (thay `take(60)` cũ — nó luôn cắt đúng chỗ "Warning: Activity not started…"). */
    fun logLines(out: String, log: (String) -> Unit) =
        out.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(6).forEach { log("      $it") }

    /**
     * Ghi 3 setting freeform 1 LẦN mỗi phiên app (không phải mỗi lần chiếu). Chúng CHỈ được framework đọc lúc BOOT
     * (`ATMS.retrieveSettings`, không có ContentObserver) → ghi runtime chỉ có nghĩa cho lần khởi động SAU.
     * ★ v0.36 ĐỔI 0 → 1 (bản cũ ghi 0 MỖI lần chiếu). Cần =1 vì HAI lý do, đều verify trên source AOSP 10:
     *   (a) `ATMS.retrieveSettings` chỉ GÁN `mSupportsFreeformWindowManagement` BÊN TRONG nhánh
     *       `(supportsMultiWindow || forceResizable)`. OEM đặt `config_supportsMultiWindow=false` (hoặc
     *       `ro.config.low_ram=true`) là vế trái tắt → `enable_freeform_support` một mình bị VỨT ĐI.
     *   (b) `ActivityDisplay.validateWindowingMode` sẽ downgrade FREEFORM cho app khai
     *       `resizeableActivity="false"`, vì nó gate trên `TaskRecord.isResizeable()` =
     *       `mForceResizableActivities || isResizeableMode(...) || mSupportsPictureInPicture`.
     *   ⚠ KHÔNG hoàn tác được từ trong app (đảo về 0 sẽ phá freeform ở máy đã seed + power-cycle rồi).
     *     Muốn gỡ tay: `adb shell settings delete global force_resizable_activities` rồi tắt-mở máy xe.
     */
    /** Khoá prefs đánh dấu ĐÃ ghi cờ freeform ra Settings.Global — ghi TRƯỚC khi đổi, để lần chạy sau còn biết đường gỡ. */
    const val PREF_FREEFORM = "clusternav_state"
    /** 0 = chưa seed · 1 = đã seed · 2 = NGƯỜI DÙNG ĐÃ GỠ (không được tự bật lại). */
    const val K_FREEFORM_STATE = "freeform_state"
    const val FF_NONE = 0
    const val FF_SEEDED = 1
    const val FF_USER_REMOVED = 2

    private val FREEFORM_KEYS = listOf("enable_freeform_support", "force_resizable_activities")

    @Volatile private var freeformSeeded = false

    /**
     * Ghi hai cờ freeform vào `Settings.Global`.
     *
     * ⚠ ĐÂY LÀ STATE SỐNG NGOÀI TIẾN TRÌNH (§5): `Settings.Global` sống qua reboot, qua gỡ app, qua xoá data.
     * `ActivityTaskManagerService.retrieveSettings` đọc hai khoá này ĐÚNG MỘT LẦN lúc boot (không có
     * ContentObserver) ⇒ **lần tắt-mở máy sau đó không chữa bệnh, nó KÍCH HOẠT hiệu lực**: trước power-cycle mọi
     * yêu cầu freeform rơi xuống display 0 bị hạ cấp im lặng (vô hại); sau đó đúng lệnh ấy tạo cửa sổ nổi THẬT
     * trên màn hình giữa của tài xế. Đó chính là lỗi hiện trường 22/07 ("Vietmap bị scale ở màn chính, khởi động
     * lại vẫn bị").
     *
     * Vì thế: MARKER được ghi vào prefs (commit, đồng bộ) TRƯỚC khi chạm Settings.Global — để dù tiến trình
     * chết ngay sau đó, lần khởi động sau vẫn biết mình đã bật và còn đường [unseedFreeform] để gỡ.
     */
    fun ensureFreeformSeed(ctx: android.content.Context, sh: (String) -> String, log: (String) -> Unit) {
        if (freeformSeeded) return
        // ★ ĐỌC MARKER BỀN TRƯỚC, không phải cờ RAM. Bản v0.50 chỉ gate bằng cờ RAM nên nút "GỠ CHẾ ĐỘ CỬA SỔ
        //   NỔI" bị chính lần CHIẾU kế tiếp ghi lại — người dùng bấm gỡ, chiếu thêm một lần trước khi tắt máy
        //   (rất dễ, cùng một màn), thế là công cốc và họ kết luận "nút gỡ không ăn".
        if (freeformState(ctx) == FF_USER_REMOVED) {
            log("  ⚙ bỏ qua cờ freeform — người dùng đã chủ động gỡ. Chỉnh kích thước sẽ dùng wm size/overscan.")
            return
        }
        // ★ marker TRƯỚC khi đổi — §5. commit() chứ không apply(): apply() ghi nền, chết trước khi flush là mất marker.
        ctx.applicationContext.getSharedPreferences(PREF_FREEFORM, android.content.Context.MODE_PRIVATE)
            .edit().putInt(K_FREEFORM_STATE, FF_SEEDED).commit()
        freeformSeeded = true
        // ★ W2-8(a): BỎ `development_enable_freeform_windows_support` — nó KHÔNG PHẢI khoá của framework.
        //   AOSP 10 Settings.java ánh xạ hằng DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT về đúng chuỗi
        //   "enable_freeform_support"; ghi thêm tên kia chỉ làm bẩn bảng settings, không ai đọc.
        FREEFORM_KEYS.forEach { sh("settings put global $it 1") }
        log("  ⚙ đã ghi cờ freeform (có hiệu lực sau khi TẮT MÁY XE hẳn 1 lần rồi mở lại)")
    }

    /** Trạng thái cờ freeform: [FF_NONE] / [FF_SEEDED] / [FF_USER_REMOVED]. Marker BỀN, không phải cờ RAM. */
    fun freeformState(ctx: android.content.Context): Int =
        ctx.applicationContext.getSharedPreferences(PREF_FREEFORM, android.content.Context.MODE_PRIVATE)
            .getInt(K_FREEFORM_STATE, FF_NONE)

    fun freeformSeedMarked(ctx: android.content.Context): Boolean = freeformState(ctx) == FF_SEEDED

    /**
     * GỠ hai cờ freeform. Đường trả lại mà §5 bắt buộc phải có — và nó chạy được cả khi tiến trình lần trước đã chết,
     * vì marker nằm trong prefs chứ không trong RAM.
     *
     * Đánh đổi phải nói rõ với người dùng: gỡ xong thì tầng 1 (`am task resize`, chỉnh khung mượt trên cụm) hết
     * tác dụng, việc chỉnh kích thước tụt xuống `wm size`/`wm overscan` — hai đường vốn vẫn chạy tốt cho Vietmap
     * và CarPlay. Đổi lại: không còn cửa sổ nổi kẹt trên màn hình giữa của tài xế.
     * Chỉ có hiệu lực sau khi TẮT MÁY XE hẳn một lần.
     */
    fun unseedFreeform(ctx: android.content.Context, sh: (String) -> String, log: (String) -> Unit) {
        FREEFORM_KEYS.forEach { sh("settings delete global $it") }
        ctx.applicationContext.getSharedPreferences(PREF_FREEFORM, android.content.Context.MODE_PRIVATE)
            .edit().putInt(K_FREEFORM_STATE, FF_USER_REMOVED).commit()
        freeformSeeded = false
        log("  ⚙ đã GỠ cờ freeform — cần TẮT MÁY XE hẳn 1 lần rồi mở lại mới có hiệu lực")
    }

    /** freeform đã sống chưa? Probe rẻ + KHÔNG phá: resize task về ĐÚNG bounds hiện có → thành công = freeform sống. */
    fun freeformAlive(sh: (String) -> String, e: StackEntry, vd: Int): Boolean {
        if (e.displayId != vd) return false
        val (w, h) = DisplayParse.realSize(sh("dumpsys display"), vd)
        return !resizeRejected(sh("am task resize ${e.taskId} 0 0 $w $h 2>&1"))
    }

    /**
     * ★ SỬA MÀN GIỮA sau khi chiếu hụt — bê mọi stack của [pkg] còn trên VD về display 0 rồi ÉP FULLSCREEN.
     * Cần thiết vì: sau power-cycle (freeform sống), một stack đã bị set freeform mà quay về display 0 sẽ Ở LẠI
     * dạng CỬA SỔ NỔI trên màn hình giữa của tài xế — trước v0.36 không có đường nào đưa nó về bình thường.
     * `am start --windowingMode 1` (WINDOWING_MODE_FULLSCREEN) là verb shell duy nhất đổi được windowing-mode
     * của task đang chạy trên A10.
     */
    fun restoreFullscreenOnMain(adb: dadb.Dadb, sh: (String) -> String, pkg: String, vd: Int, log: (String) -> Unit) {
        runCatching {
            var ents = StackParse.parse(sh("am stack list"))
            // ★ v0.50 PHẠM VI TƯỜNG MINH (§4). Bản cũ lọc mù `displayId >= 1`. Trên xe có Dudu launcher,
            //   display 1 là `launcher-split` RIÊNG (FLAG_PRIVATE) của nó — quét mù sẽ giật app ra khỏi khung
            //   chia đôi của launcher, đúng mẫu lỗi từng làm đơ launcher. Nay chỉ đụng display MANG TÊN cụm,
            //   và lấy cả TẬP (opcode 16 tái tạo VD → id mới, id cũ có thể còn stack bám).
            val clusterIds = WmParse.clusterDisplayIds(sh("dumpsys display")).let { m ->
                if (vd >= 1) m + vd else m          // vd đang dùng luôn được tính, kể cả khi dump hụt
            }
            if (clusterIds.isEmpty()) log("  ⚠ không xác định được display của cụm — KHÔNG bê stack nào (thà không làm gì)")
            // ★ §4 câu 3 — LOẠI stack. v0.50 mới trả lời được câu 1 (đúng display) mà bỏ câu này: bê cả stack
            //   `pinned`/`home` là đúng lớp lệnh đã từng làm đơ Dudu launcher. Stack khác chỉ log, để VD tự trả
            //   nội dung khi teardown (removeMode 0).
            val skipped = StackParse.of(ents, pkg).filter { it.displayId in clusterIds && !(it.isStandard && !it.isPinned) }
            if (skipped.isNotEmpty()) log("  ⏭ giữ nguyên ${skipped.size} stack home/PIP trên cụm (đụng vào là hỏng launcher)")
            StackParse.of(ents, pkg).filter { it.displayId in clusterIds && it.isStandard && !it.isPinned }
                .map { it.stackId }.distinct().forEach {
                log("  ↩ bê stack $it của $pkg về màn giữa"); sh("am display move-stack $it 0 2>&1"); Thread.sleep(400)
            }
            ents = StackParse.parse(sh("am stack list"))
            // freeform HOẶC pinned đều là "cửa sổ nổi" trên màn giữa của tài xế → ép về fullscreen.
            // ★ v0.42: xử MỌI stack kẹt của app (bản cũ chỉ lấy firstOrNull) và KIỂM LẠI sau khi ép.
            val stuck = StackParse.of(ents, pkg).filter { it.displayId == 0 && (it.isFreeform || it.isPinned) }
            if (stuck.isNotEmpty()) {
                val comp = resolveComp(adb, pkg)
                log("  ↩ $pkg còn ${stuck.size} cửa sổ NỔI trên màn giữa → ép fullscreen")
                if (comp != null) {
                    // -f 0x20000000 = FLAG_ACTIVITY_SINGLE_TOP: lệnh dọn này chạy trên app ĐANG chạy, không cần
                    // instance mới. Thiếu cờ thì AOSP đi nhánh mAddingToTask=true → thêm một activity vào cùng task.
                    // KHÔNG thêm cờ này cho rung R3 (nó dùng --activity-clear-task và cố ý muốn khởi động lạnh).
                    sh("am start -f 0x20000000 --windowingMode 1 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $comp 2>&1")
                    Thread.sleep(400)
                    val left = StackParse.of(StackParse.parse(sh("am stack list")), pkg)
                        .count { it.displayId == 0 && (it.isFreeform || it.isPinned) }
                    if (left > 0) log("  ⚠ vẫn còn $left cửa sổ nổi — mở app từ launcher 1 lần là hết")
                }
            }
            if (vd >= 1) resetDisplayAll(sh, vd)
        }.onFailure { log("  ⚠ dọn màn giữa lỗi: ${it.message}") }
    }

    // ── CHẶN PIP (picture-in-picture) ──
    /**
     * appops PICTURE_IN_PICTURE — enforce trong system_server (`ActivityRecord.checkEnterPictureInPictureAppOpsState`)
     * nên bản YouTube mod cũng không lách được. Bị chặn thì `enterPictureInPictureMode()` trả **false**, KHÔNG ném,
     * KHÔNG crash — app đơn giản ở lại fullscreen. uid-2000 (shell) có MANAGE_APP_OPS_MODES nên set được.
     */
    fun pipCmd(pkg: String, mode: String) = "cmd appops set --user 0 $pkg PICTURE_IN_PICTURE $mode 2>&1"
    /** component launcher "pkg/cls" của [pkg] (dòng cuối có dấu '/'), null nếu app không có launcher activity. */
    fun resolveComp(adb: dadb.Dadb, pkg: String): String? = resolveComp({ c -> adb.shell(c).output }, pkg)
    /**
     * ★ v0.7x — biến thể chạy qua seam `sh` (KHÔNG cần `adb`). Nhờ nó [returnAppToMain] bỏ được tham số `adb`,
     * để [ClusterCast.guardSinksOffVd] (chỉ có `sh`) gọi được returnAppToMain mà KHÔNG phải đổi chữ ký (giữ
     * nguyên test CastFlowTest/CastStressTest gọi guardSinksOffVd theo sh). Cùng logic: lấy dòng cuối có '/'.
     */
    fun resolveComp(sh: (String) -> String, pkg: String): String? =
        sh("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $pkg")
            .lineSequence().map { it.trim() }.lastOrNull { it.contains("/") && !it.contains(" ") }

    /** `am task resize` bị framework từ chối? (task fullscreen trên A10 → IllegalArgumentException "not allowed"). */
    fun resizeRejected(o: String) =
        o.contains("Error", true) || o.contains("Exception", true) ||
        o.contains("not allowed", true) || o.contains("must be", true)

    /**
     * ★ TẦNG 2 — `wm size`: đổi THẬT logical size của VD. Đây là tầng DUY NHẤT đổi được kích thước cho app
     * chiếu điện thoại khi freeform chưa sống.
     * GỐC RỄ (verify AOSP 10): `wm overscan` KHÔNG đổi khung cửa sổ — `DisplayFrames.onBeginLayout` giữ
     * `mOverscan`/`mRestrictedOverscan` bằng NGUYÊN kích thước display, chỉ `mUnrestricted/mContent/mStable` bị co;
     * mà `DisplayInfo.appWidth/appHeight` (→ Configuration của app) tính từ `getNonDecorDisplayWidth/Height`, vốn
     * chỉ trừ thanh hệ thống chứ không trừ overscan. Nên overscan chỉ là *content inset*: app nào bỏ qua inset
     * (SurfaceView/video/immersive — đúng kiểu Android Auto) thì KHÔNG hề nhỏ đi.
     * `wm size` thì đổi `logicalWidth/Height` → `appWidth/appHeight` → Configuration mới → app BUỘC phải vẽ lại.
     * ⚠ Đánh đổi: LogicalDisplay letterbox CĂN GIỮA và giữ tỉ lệ → mất khả năng đặt khung lệch tâm.
     * ⚠ WM LƯU BỀN cái này vào /data/system/display_settings.xml theo uniqueId của VD → SỐNG QUA CẢ REBOOT.
     *   Mọi đường teardown/reconcile BẮT BUỘC gọi [resetDisplayAll].
     * @return mô tả nếu ăn thật (đã đọc lại `cur=WxH` để xác nhận), null nếu không → phía gọi rơi xuống overscan.
     */
    fun forceDisplaySize(sh: (String) -> String, vd: Int, wh: IntArray, dpi: Int, userDpi: Int, restoreOi: IntArray): String? {
        if (vd < 1 || wh[0] <= 0 || wh[1] <= 0) return null
        sh("wm overscan reset -d $vd")                      // overscan + wm size chồng nhau = co hai lần
        sh("wm size ${wh[0]}x${wh[1]} -d $vd")
        sh("wm density $dpi -d $vd")                        // dpi BÙ cho phần LogicalDisplay phóng, khác dpi user chọn
        val dump = sh("dumpsys window displays")
        val cur = DisplayParse.logicalSize(dump, vd)
        if (cur != null && cur.first == wh[0] && cur.second == wh[1]) {
            val eff = DisplayParse.density(dump, vd)?.first ?: dpi
            return "wm size ${wh[0]}x${wh[1]} · dpi thực $eff (bạn chọn $userDpi, đã bù cho phần phóng) — căn giữa"
        }
        // ★ không ăn → hoàn tác SẠCH: trả size, dpi user chọn, VÀ overscan đã gỡ ở đầu hàm.
        //   Thiếu bước trả overscan thì applyBounds sẽ báo "đã áp overscan […]" cho một VD trống trơn.
        sh("wm size reset -d $vd")
        sh("wm density $userDpi -d $vd")
        if (hasOverscan(sh)) sh("wm overscan ${restoreOi[0]},${restoreOi[1]},${restoreOi[2]},${restoreOi[3]} -d $vd")
        return null
    }

    /**
     * ★ TẦNG OVERSCAN CÓ KIỂM CHỨNG — đường ĐÃ CHẠY TỐT trên xe cho app thường (CarPlay, Vietmap, 21/07).
     * Ưu điểm so với [forceDisplaySize]: giữ được khung LỆCH TÂM, và không đụng vào logical size của VD.
     * Nhược điểm: app nào phớt lờ content inset (Android Auto) thì KHÔNG nhỏ đi — nên phải đọc lại khung
     * cửa sổ thật để biết có ăn không, rồi mới quyết định có leo lên `wm size` hay không.
     * @return mô tả nếu app CÓ co lại thật; null nếu app phớt lờ (phía gọi leo tầng).
     */
    /**
     * ★★ W2-4/C-4: `wm overscan` ĐÃ BỊ GỠ khỏi Android 11 trở lên. Trên DiLink5 (Android 12) gọi nó là lỗi cứng.
     * Chừng nào parser còn chết trên DL5 thì DL5 "hỏng an toàn" — cast tự huỷ trước khi đụng tới cụm. Sửa parser
     * mà KHÔNG có cổng này là biến nó thành "hỏng nguy hiểm": cast chạy tiếp, tầng overscan (tầng duy nhất ăn
     * ngoài hiện trường) hỏng, và cụm bị đổi mà không ai trả lại được.
     * Dò MỘT LẦN bằng chính shell, không đoán theo Build.VERSION của MÁY CHẠY APP (app và shell cùng máy, nhưng
     * để nhất quán với mọi thứ khác trong file này: đo, đừng đoán).
     */
    @Volatile private var overscanSupported: Boolean? = null
    fun hasOverscan(sh: (String) -> String): Boolean {
        overscanSupported?.let { return it }
        val out = sh("wm overscan 2>&1")
        val ok = !out.contains("Unknown command", true) && !out.contains("unknown option", true)
        overscanSupported = ok
        return ok
    }

    fun overscanVerified(sh: (String) -> String, vd: Int, pkg: String, oi: IntArray, w: Int, h: Int): String? {
        if (!hasOverscan(sh)) return null       // A11+ đã gỡ lệnh này → để tầng sau (wm size) lo
        sh("wm size reset -d $vd")                                   // gỡ wm size lần trước, tránh co hai lần
        sh("wm overscan ${oi[0]},${oi[1]},${oi[2]},${oi[3]} -d $vd")
        if (oi.all { it == 0 }) return "overscan [0,0,0,0] (full cụm)"
        val f = appWindowFrameOf(sh, pkg) ?: return "overscan [${oi.joinToString(",")}] (không đọc được khung app)"
        val ignored = f[0] <= 1 && f[1] <= 1 && f[2] >= w - 1 && f[3] >= h - 1
        return if (ignored) null                                     // khung vẫn full → app phớt lờ inset
        else "overscan [${oi.joinToString(",")}] (khung app ${f[2] - f[0]}×${f[3] - f[1]})"
    }

    private fun appWindowFrameOf(sh: (String) -> String, pkg: String): IntArray? =
        DisplayParse.appWindowFrame(sh("dumpsys window windows"), pkg)

    /**
     * Gỡ ÉP HÌNH HỌC trên VD (size + overscan) nhưng **GIỮ NGUYÊN density**.
     * ★ v0.38 SỬA LỖI TỰ GÂY: bản trước gộp cả `wm density reset` vào đây, mà applyBounds tầng 1 lại gọi hàm này
     *   NGAY SAU khi applyScaleLive vừa ghi DPI của user → lệnh sau xoá lệnh trước, nút DPI thành vô dụng.
     *   DPI là thứ NGƯỜI DÙNG chọn, chỉ được xoá khi teardown thật ([resetDisplayAll]).
     */
    fun resetDisplayGeometry(sh: (String) -> String, vd: Int) {
        if (vd < 1) return
        sh("wm size reset -d $vd")
        if (hasOverscan(sh)) sh("wm overscan reset -d $vd")     // A11+ đã gỡ lệnh này
    }

    /** Trả VD về gốc HOÀN TOÀN (kể cả density). Chỉ dùng ở teardown/reconcile — `wm size` sống qua reboot. */
    fun resetDisplayAll(sh: (String) -> String, vd: Int) {
        if (vd < 1) return
        sh("wm size reset -d $vd"); sh("wm density reset -d $vd")
        if (hasOverscan(sh)) sh("wm overscan reset -d $vd")     // A11+ đã gỡ lệnh này
    }

    /**
     * ★ v0.7x (T7 · R11) — BÊ 1 APP KHỎI VD VỀ MÀN CHÍNH (display 0) FULLSCREEN, OCCLUDE-CORRECT.
     *
     * Trung tâm hoá "trả app khỏi cụm" cho ĐƯỜNG SWITCH — THAY cho MỌI `am display move-stack …0` (primitive NPE B).
     *
     * ★★ TIỀN ĐỀ BẮT BUỘC [oldOccluded] (R11 — bài học CP/AA orphan v0.67): caller PHẢI chạy OCCLUDE-VERIFY
     *   ([occludeVerify] → target fresh-launch full-VD đã phủ lên → app cũ `isVisible==false`) rồi truyền kết quả vào.
     *   Gentle `am start --display 0` một task size-compat/sink CÒN VISIBLE vượt ranh freeform →
     *   `AppWindowToken.initializeChangeTransition` (research RISKY) → reparent nửa vời → cửa sổ MỒ CÔI (brick tới reboot).
     *   • [oldOccluded]==true  → GENTLE an toàn (app cũ vô hình → snapshot-free): `am start --display 0 --wm1` GIỮ
     *     pid/state (validated on-car 2026-07-24: 4 switch Vietmap↔Maps NPE=0, pid không đổi). Còn freeform-bé → ép fullscreen (H4).
     *   • [oldOccluded]==false → KHÔNG gentle-move task visible. SINK/keep-session → ĐỂ YÊN sau target (R11/OQ3:
     *     KHÔNG force-stop = giữ phiên chiếu điện thoại, KHÔNG move-stack, chấp nhận 2-on-VD); app THƯỜNG → PHAO
     *     `am force-stop` (process death = CLOSE transition, an toàn kể cả khi visible) + relaunch d0.
     *
     * ★ keepSession TÍNH BÊN TRONG (F2): caller KHÔNG truyền cờ này → chống wire nhầm chỉ [ClusterCast.isKeepSession]
     *   mà QUÊN [ClusterCast.isPhoneProjection] — `keepSessionApps` MẶC ĐỊNH RỖNG nên CP/AA chỉ được che bởi
     *   `isPhoneProjection` (nhận diện theo hành vi, khớp `placeLadder` R3).
     *
     * @return true CHỈ KHI app (a) RỜI VD **VÀ** (b) `displayId==0` **VÀ** (c) `mode==fullscreen` (R3/H4 — đủ 3
     *   điều kiện; freeform-bé sót = nghi phạm freeze bấm Home). TUYỆT ĐỐI KHÔNG dùng `am display move-stack …0`.
     */
    fun returnAppToMain(sh: (String) -> String, app: String, vd: Int, oldOccluded: Boolean, log: (String) -> Unit): Boolean {
        if (app.isBlank()) return false
        val comp = resolveComp(sh, app) ?: run { log("  ⚠ returnAppToMain: không resolve được $app → bỏ"); return false }
        val keepSession = ClusterCast.isKeepSession(app) || ClusterCast.isPhoneProjection(comp, app)   // R5 — CẢ HAI vế (keepSessionApps rỗng mặc định)
        val startD0 = "am start --display 0 --windowingMode 1 -f 0x20000000 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $comp 2>&1"
        // Đạt "về màn chính" chưa: app RỜI VD ∧ ở display 0 ∧ fullscreen (R3/H4 — freeform-bé sót = nghi phạm freeze bấm Home).
        fun reachedMain(): Boolean {
            val ents = StackParse.parse(sh("am stack list"))
            val e = StackParse.pick(ents, app, preferDisplay = 0)
            return StackParse.of(ents, app).none { it.displayId == vd } && e != null && e.displayId == 0 && e.mode == "fullscreen"
        }

        // ★★ ① GENTLE — CHỈ KHI app cũ ĐÃ occlude (R11). Gentle-move một task CÒN VISIBLE = đúng đường tạo orphan v0.67.
        //   `am start --display 0` → moveTaskToFrontLocked → prepareAppTransition(TRANSIT_TASK_TO_FRONT) →
        //   isTransitionSet()=true → shouldStartChangeTransition=FALSE → KHÔNG createTaskSnapshot → né NPE A+B,
        //   reparent về d0 GIỮ NGUYÊN pid/state (On-car 2026-07-24: 4 switch Vietmap↔Maps NPE=0, pid không đổi).
        //   App cũ ĐÃ invisible (target fresh-launch full-VD phủ lên) nên không có surface đang hiện để animate → an toàn thêm 1 lớp.
        if (oldOccluded) {
            log("  ↩ returnAppToMain: $app đã occlude (invisible) → am start d0 (gentle — GIỮ state, không force-stop, không move-stack)")
            sh(startD0); Thread.sleep(600)
            run {                                                                   // còn freeform-bé trên d0 → ép fullscreen 1 lần nữa (H4)
                val e = StackParse.pick(StackParse.parse(sh("am stack list")), app, preferDisplay = 0)
                if (e != null && e.displayId == 0 && e.mode != "fullscreen") { log("  ↩ $app còn '${e.mode}' → ép fullscreen"); sh(startD0); Thread.sleep(400) }
            }
            if (reachedMain()) { log("  ✓ $app về màn chính fullscreen (GIỮ state)"); return true }
        }

        // ② Tới đây: app cũ CHƯA occlude (KHÔNG được gentle-move task visible → orphan), HOẶC đã gentle mà chưa rời VD.
        //   SINK/keep-session (CP/AA): TUYỆT ĐỐI không giết phiên + KHÔNG move-stack visible → ĐỂ YÊN sau target (F2/F5/R11/OQ3).
        if (keepSession) {
            log("  ⚠ $app (giữ-phiên/sink) ${if (!oldOccluded) "còn VISIBLE trên VD" else "chưa rời VD sau gentle"} → ĐỂ YÊN sau target, " +
                "KHÔNG force-stop, KHÔNG move-stack (chấp nhận 2-on-VD, KHÔNG treo — R11/OQ3)")
            return reachedMain()      // false nếu sink còn trên VD (soft-R2) — nhưng KHÔNG treo
        }

        // ③ APP THƯỜNG: force-stop (process death = CLOSE transition, né A+B kể cả khi còn visible) + relaunch fullscreen d0.
        //   CHỈ nhánh này mất state — đổi lấy chắc chắn rời VD + KHÔNG treo (app thường không có phiên chiếu điện thoại để mất).
        log("  ↩ $app (thường) ${if (!oldOccluded) "chưa occlude" else "gentle chưa rời VD"} → force-stop + relaunch fullscreen d0 (an toàn, hiếm)")
        sh("am force-stop $app"); Thread.sleep(400)                                 // process death → CLOSE transition (né A+B)
        sh("am start --display 0 --windowingMode 1 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $comp 2>&1"); Thread.sleep(500)
        return reachedMain()
    }

    /**
     * ★ v0.7x (T7 · D5 U3) — OCCLUDE-VERIFY: poll `dumpsys window displays` tới khi [oldApp] token trên [vd]
     * `isVisible==false` (đã bị target fresh-launch full-VD phủ lên), tối đa [timeoutMs]. Chỉ khi TRẢ TRUE thì
     * [returnAppToMain] mới được gentle-move app cũ (né change-transition → orphan). Sink cưỡng lại (size-compat,
     * tự re-front) → hết giờ → false → caller ĐỂ YÊN sink. Đọc-only, KHÔNG mutate.
     */
    internal fun occludeVerify(sh: (String) -> String, oldApp: String, vd: Int, log: (String) -> Unit,
                               timeoutMs: Long = 1200, stepMs: Long = 200): Boolean {
        if (oldApp.isBlank() || vd < 1) return true
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            if (!WmParse.isVisibleOn(sh("dumpsys window displays"), oldApp, vd)) {
                log("  ✓ occlude-verify: $oldApp đã bị target phủ (isVisible==false) → gentle-return an toàn")
                return true
            }
            if (System.currentTimeMillis() >= deadline) {
                log("  ⚠ occlude-verify: $oldApp VẪN visible trên VD sau ${timeoutMs}ms → KHÔNG gentle-move (né orphan): sink để yên / app thường force-stop")
                return false
            }
            Thread.sleep(stepMs)
        }
    }

    /**
     * ★ v0.66 — kết quả 1 lần hot-swap ở tầng shell. [target] = StackEntry app đích trên VD SAU swap (ĐÃ RE-PICK,
     * F10), null nếu swap KHÔNG hoàn tất (B chưa bám VD / B bounce) → caller GIỮ app cũ, KHÔNG commit. [note] để log.
     */
    internal data class SwapResult(val target: StackEntry?, val note: String)

    /**
     * ★ v0.7x (T7) — LÕI SHELL của HOT-SWAP, tách khỏi [ClusterCast] để test off-xe (FakeShell), Context-free.
     * Bất biến cốt tử: "VD KHÔNG BAO GIỜ rỗng" (né NPE A) + KHÔNG `move-stack …0` (né NPE B) + OCCLUDE-CORRECT (R11).
     * Bám recipe DashCast (docs/reference/dashcast-projection-recipe.md, validated on-car v0.23):
     *
     *  ② ĐẶT [target] lên VD ĐANG SỐNG — SPLIT theo loại app (khôi phục CP/AA đúng như cold path):
     *       • target THƯỜNG (NEW, `oldApp != target`) → FRESH-LAUNCH: `am force-stop <target>` +
     *         `am start --display vd --wm5 --activity-clear-task` (recipe #4 — buffer full-VD composite đúng,
     *         hết trắng/ADAS-đen; force-stop giết TaskRecord để `--display` được tôn trọng, né willClearTask short-circuit).
     *       • target SINK (isPhoneProjection‖isKeepSession) HOẶC re-cast CHÍNH app đang trên VD → RESUME:
     *         `am start --display vd --wm5` (KHÔNG force-stop, KHÔNG clear-task) — giữ phiên chiếu điện thoại /
     *         KHÔNG reset app đang đúng chỗ (F1: force-stop chính app duy nhất trên VD → VD rỗng → NPE A).
     *     R2 = move-stack LÊN VD (đặt app MỚI lên VD, KHÔNG bê KHỎI VD) nếu am start chưa relocate. GATE [landedOn] (R6):
     *     B chưa bám VD → GIỮ old, thoát (F5: TUYỆT ĐỐI KHÔNG [restoreFullscreenOnMain] target).
     *  ③ Bê [oldApp] về màn chính — CHỈ khi `oldApp.isNotBlank() && oldApp != target` (F1). TRƯỚC đó:
     *       (F4 TOCTOU) RE-CHECK B còn bám VD; B bounce → HỦY, giữ old.
     *       (U3 OCCLUDE-VERIFY, R11) re-assert target on top rồi [occludeVerify] tới khi old `isVisible==false`.
     *     Truyền `oldOccluded` vào [returnAppToMain]: occluded → gentle (giữ state); sink cưỡng lại → để yên (KHÔNG orphan).
     *  ④ [evictVd] dọn app lạ khác. ⑤ RE-ASSERT B (H7) rồi RE-PICK StackEntry của B (F10 — taskId đổi sau fresh-launch/re-assert).
     */
    internal fun swapOnVd(sh: (String) -> String, target: String, comp: String, oldApp: String, vd: Int, log: (String) -> Unit): SwapResult {
        // RESUME/re-assert (bring-to-front trên VD, full-VD freeform) — KHÔNG force-stop, KHÔNG clear-task.
        val resumeCmd = "am start --display $vd --windowingMode 5 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $comp 2>&1"
        // FRESH-LAUNCH (target thường mới): --activity-clear-task để buffer full-VD composite đúng (recipe #4).
        val freshCmd = "am start --display $vd --windowingMode 5 --activity-clear-task -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $comp 2>&1"
        val isSink = ClusterCast.isPhoneProjection(comp, target) || ClusterCast.isKeepSession(target)
        // FRESH-LAUNCH chỉ cho target THƯỜNG & MỚI. Sink → resume (giữ phiên). Re-cast chính app (oldApp==target) →
        //   resume (KHÔNG force-stop app duy nhất trên VD → chống VD rỗng → NPE A, F1).
        val freshLaunch = !isSink && oldApp != target

        // ② đặt B lên VD ĐANG SỐNG (VD còn app cũ → KHÔNG rỗng → KHÔNG huỷ)
        if (freshLaunch) {
            log("  ② fresh-launch $target (am force-stop + --activity-clear-task) — recipe #4, composite full-VD")
            sh("am force-stop $target"); Thread.sleep(400)                           // giết TaskRecord → --display honored
            logLines(sh(freshCmd), log); Thread.sleep(500)
        } else {
            log("  ② resume $target (am start --wm5" + (if (isSink) ", SINK — KHÔNG force-stop/clear-task để giữ phiên chiếu điện thoại)" else ", re-cast cùng app — KHÔNG force-stop)"))
            logLines(sh(resumeCmd), log); Thread.sleep(500)
        }
        var landed = landedOn(sh, target, vd)
        if (landed == null) {                                                        // R2: move-stack B LÊN VD (đặt app MỚI lên VD → an toàn)
            val src = StackParse.pick(StackParse.parse(sh("am stack list")), target, preferDisplay = 0)
            if (src != null && src.displayId != vd) {
                log("  R2 move-stack ${src.stackId} → VD $vd"); sh("am display move-stack ${src.stackId} $vd 2>&1"); Thread.sleep(400)
                logLines(sh(resumeCmd), log); landed = landedOn(sh, target, vd)      // re-composite (task đã ở VD → plain start)
            }
        }
        if (landed == null)                                                          // R6: B chưa bám VD → GIỮ old (VD còn old → không rỗng → né NPE A)
            return SwapResult(null, "$target chưa bám VD (bounce?) — giữ app cũ trên cụm, switch hụt (KHÔNG treo)")

        // ③ Bê app CŨ ra display 0 — F1 (P0): chỉ khi oldApp != target (chống force-stop chính B → VD rỗng → NPE A).
        if (oldApp.isNotBlank() && oldApp != target) {
            // ★ F4 (TOCTOU): B (Maps GL) có thể bounce khỏi VD SAU gate landedOn → RE-CHECK bằng stack list MỚI
            //   NGAY TRƯỚC bước phá hoại. B bounce → HỦY swap, giữ old (chưa đụng), KHÔNG force-stop, KHÔNG treo.
            val bStillOnVd = StackParse.of(StackParse.parse(sh("am stack list")), target).any { it.displayId == vd }
            if (!bStillOnVd)
                return SwapResult(null, "$target vừa bounce khỏi VD trước khi bê app cũ → HỦY swap, giữ old (KHÔNG force-stop, KHÔNG treo)")
            // ★ U3 OCCLUDE-VERIFY (R11): re-assert target on top (full-VD phủ lên) rồi CONFIRM old.isVisible==false
            //   TRƯỚC khi bê. Chỉ khi old vô hình thì gentle mới né change-transition → orphan. Sink cưỡng lại → oldOccluded=false → để yên.
            logLines(sh(resumeCmd), log); Thread.sleep(300)                          // re-assert target on top (H7 sớm) để phủ old
            val oldOccluded = occludeVerify(sh, oldApp, vd, log)
            if (returnAppToMain(sh, oldApp, vd, oldOccluded, log)) log("  ✓ $oldApp đã về màn chính (fullscreen d0)")
            else log("  ⚠ $oldApp chưa về d0 sạch (sink để yên / chưa occlude) — KHÔNG treo, tiếp tục (xem log trên)")
        }

        // ④ dọn app lạ khác khỏi VD (cụm chỉ 1 app — R2). ⑤ RE-ASSERT B trên top (H7) + RE-PICK (F10).
        evictVd(sh, vd, target, log)
        logLines(sh(resumeCmd), log); Thread.sleep(300)
        val repicked = landedOn(sh, target, vd) ?: landed
        return SwapResult(repicked, "ok")
    }

    /**
     * ★ CỤM CHỈ ĐƯỢC CÓ ĐÚNG 1 APP: bê MỌI stray (≠ [keepPkg]) khỏi VD về màn chính bằng [returnAppToMain]
     *   (THAY `am display move-stack …0` = primitive NPE B). Mỗi stray tự tính OCCLUDE (isVisibleOn) → occluded
     *   thì gentle (giữ state), còn visible thì app thường force-stop / sink để yên (R11). Thừa hưởng miễn-trừ
     *   keep-session/projection (stray = CP/AA thì KHÔNG force-stop). [StackParse.evictableOnVd] vẫn lọc home/pinned (H8).
     *   ⚠ evictVd CÒN được gọi ở đường COLD first-cast ([ClusterCast.placeAppOnVd]) → CHẤP NHẬN (stray hiếm ở cold,
     *   sink vẫn miễn — F6). Chỉ đổi CƠ CHẾ relocate, KHÔNG đổi filter nạn nhân. KHÔNG move-stack ở BẤT KỲ nhánh nào.
     */
    fun evictVd(sh: (String) -> String, vd: Int, keepPkg: String, log: (String) -> Unit) {
        if (vd < 1) return
        val victims = StackParse.evictableOnVd(StackParse.parse(sh("am stack list")), vd)
            .filter { it.pkg != keepPkg }
            .map { it.pkg }.distinct()
        if (victims.isEmpty()) return
        for (pkg in victims) {
            val occluded = !WmParse.isVisibleOn(sh("dumpsys window displays"), pkg, vd)   // stray bị target/keep phủ?
            log("  ⇤ dọn khỏi cụm: $pkg (${if (occluded) "occluded → gentle" else "visible → force-stop thường / để yên sink"})")
            returnAppToMain(sh, pkg, vd, occluded, log)                              // gentle nếu occluded; KHÔNG move-stack; sink miễn force-stop
        }
    }
}
