package com.byd.clusternav.modules.clustercast

import com.byd.clusternav.carexec.LocalDeviceShell
import com.byd.clusternav.carexec.LocalShellRetry
import com.byd.clusternav.carexec.LocalShellText
import android.content.Context
import com.byd.clusternav.AdbKeys
import com.byd.clusternav.launcher.ProfileScopeCluster
import com.byd.clusternav.modules.clustercast.simplified.CastStyle
import com.byd.clusternav.modules.clustercast.simplified.ClusterCarType
import com.byd.clusternav.modules.clustercast.simplified.ShellResult
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastShell
import com.byd.clusternav.modules.clustercast.simplified.ThemeGatePreview

/**
 * ★ CHỤP CHẨN ĐOÁN TỪ TRONG XE (v0.39).
 *
 * VÌ SAO CẦN: cắm CarPlay/Android Auto là đầu xe TẮT WIFI → không adb từ máy ngoài vào được, mà đó lại đúng lúc
 * cần nhìn nhất. Nhưng app chạy NGAY TRÊN đầu xe và nối dadb qua `localhost:5555` (loopback, không đụng WiFi) —
 * bằng chứng: app vẫn chiếu được CarPlay lên cụm trong lúc CarPlay đang cắm, mà chiếu thì toàn lệnh shell.
 * ⇒ App tự chụp được mọi thứ adb chụp, không cần mạng.
 *
 * Ghi ra `getExternalFilesDir()/diag/` — mở bằng app Quản lý tệp trên xe, hoặc kéo về sau bằng
 * `adb pull /sdcard/Android/data/com.byd.clusternav2/files/diag/` khi có WiFi lại.
 */
object ClusterDiag {

    /** Lệnh chụp + nhãn. Giữ GỌN: mỗi lệnh là một round-trip shell, chụp lúc đang lái phải nhanh. */
    private fun commands(pkg: String, vd: Int): List<Pair<String, String>> = listOf(
        // Self-version: query THIS app's INSTALLED package (BuildConfig.APPLICATION_ID = com.byd.clusternav2),
        // NOT the code namespace / legacy package — otherwise the diag reports the wrong (or missing) version.
        "PHIÊN BẢN" to "dumpsys package ${com.byd.clusternav.BuildConfig.APPLICATION_ID} | grep -E 'versionName|versionCode'",
        "DISPLAY (size/density/overscan)" to "dumpsys window displays",
        // ★ v0.51: PHẢI lưu. Đây đúng là dump mà DisplayParse.clusterDisplayId/realSize ăn vào — thiếu nó thì
        //   nhìn log KHÔNG xác minh được việc dò cụm chạy đúng hay sai (đúng lỗ hổng của bộ log 22/07).
        "DISPLAY (nguồn dò cụm)" to "dumpsys display",
        "STACK LIST" to "am stack list",
        // ★ CÂU HỎI 1: cửa sổ app có co theo overscan không, hay phớt lờ (cờ LAYOUT_IN_OVERSCAN/FULLSCREEN)?
        "CỬA SỔ CỦA $pkg" to "dumpsys window windows",
        // ★ CÂU HỎI 2: app có rơi vào size-compat không (framework ĐÓNG BĂNG densityDpi → DPI không bao giờ ăn)?
        // ★ v0.51: hỏi CẢ HAI nguồn. Bản cũ chỉ grep `dumpsys activity activities`, mà `mSizeCompatScale` được in
        //   ở phía WindowManager → mục này KHÔNG BAO GIỜ ra kết quả, rồi phần tóm tắt lại phát biểu
        //   "không thấy dấu hiệu size-compat" như một kết luận. Đó là lấy THIẾU DỮ LIỆU làm bằng chứng.
        "SIZE-COMPAT (activity)" to "dumpsys activity activities | grep -iE 'sizeCompat|mCompatDisplayInsets|$pkg'",
        "SIZE-COMPAT (window)" to "dumpsys window windows | grep -iE 'sizeCompat|mCompatDisplayInsets|mOverrideConfig'",
        "DENSITY VD $vd" to "wm density -d $vd",
        "SIZE VD $vd" to "wm size -d $vd",
        // ★ v0.58: hai cờ QUYẾT ĐỊNH size-compat (AOSP r47 ActivityRecord:2834 shouldUseSizeCompatMode:
        //   `&& !mForceResizableActivities`). Trước đây diag không chụp → phải SUY RA cờ tắt từ việc có
        //   size-compat. Đọc thẳng thì chốt được ngay: AA size-compat trên cụm ⇔ force_resizable đang tắt.
        "CỜ FREEFORM/RESIZABLE" to "echo force_resizable=$(settings get global force_resizable_activities) enable_freeform=$(settings get global enable_freeform_support)",
        // ★ 2.89 · B2 VM-PREREQ-TRUTH (chỉ ĐỌC): miễn pin THẬT (`system,`/`user,` mới tính — KDoc `DozeWhitelistRead`) + dấu vết
        //   hộp "IVI không hỗ trợ" (`UnsupportActivity` của CarSetting, ý-định REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) — chụp
        //   NGAY sau khi hộp hiện là chốt [SUY]→[ĐO] (spec `kachi-289-field-fixes.html` §B2 · B2-V1). Lọc theo chuỗi, không quét mù.
        "MIỄN PIN (deviceidle)" to com.byd.clusternav.system.DozeWhitelistRead.READ,
        // Review 2.89 Pass 3 · vietmap-dock-r2-5: + bộ đệm `system` — dòng `START u0 {act=… cmp=…} from uid …` in bằng `Slog.i` ⇒
        //   LOG_ID_SYSTEM [ĐO nguồn r47 `ActivityStarter.java:643-644` + `Slog.java:51-52`]; thiếu nó thì chỉ còn `am_create_activity`
        //   (events, không uid, vắng khi activity được dùng lại).
        "HỘP 'IVI KHÔNG HỖ TRỢ' (logcat)" to
            "logcat -d -b main -b system -b events | grep -E 'REQUEST_IGNORE_BATTERY_OPTIMIZATIONS|UnsupportActivity' | tail -20",
    )

    /**
     * Chụp toàn bộ, ghi file, trả về (đường dẫn file, tóm tắt vài dòng để hiện ngay trên màn).
     * Chạy trên luồng gọi (đã được gọi từ nền) — KHÔNG gọi trên main thread.
     */
    fun capture(ctx: Context, pkg: String, vd: Int, stamp: String): Pair<String, String> {
        val app = ctx.applicationContext
        val sb = StringBuilder()
        val summary = StringBuilder()
        sb.append("=== ClusterNav diag $stamp ===\npkg=$pkg\n")
        runCatching {
            LocalDeviceShell.session(AdbKeys.ensure(app), LocalShellRetry.BACKGROUND_READ_CAP) { shell ->
                fun sh(c: String): String {
                    val r = shell(c)
                    return (r.output + (if (r.errorOutput.isNotBlank()) "\n[stderr] ${r.errorOutput}" else "")).trim()
                }
                // ★ v0.51 ĐO, KHÔNG NHẬN CỜ. `vd` truyền vào là ClusterCast.lastDisplayId — cờ RAM, chết theo
                //   tiến trình. Bộ log 22/07 có 4/4 file ghi `vd=-1` chỉ vì lúc chụp app không đang chiếu, mà
                //   người đọc (kể cả tôi) suýt hiểu thành "không dò được cụm". Ghi CẢ HAI, nói rõ cái nào là gì.
                val measured = WmParse.clusterDisplayIds(sh("dumpsys display"))
                val vdUse = measured.firstOrNull() ?: vd
                sb.append("vd(đo được)=").append(if (measured.isEmpty()) "KHÔNG THẤY CỤM" else measured.joinToString(","))
                    .append(" · vd(cờ RAM lastDisplayId)=").append(vd)
                    .append(if (vd < 1) "  ← -1 nghĩa là 'lúc chụp app không đang chiếu', KHÔNG phải 'dò cụm hỏng'" else "")
                    .append("\n\n")
                var capturedWins: String? = null
                var capturedDisp: String? = null
                var capturedDisplay: String? = null
                for ((label, cmd) in commands(pkg, vdUse)) {
                    val outp = sh(cmd)
                    when (cmd) {
                        "dumpsys window windows" -> capturedWins = outp
                        "dumpsys window displays" -> capturedDisp = outp
                        "dumpsys display" -> capturedDisplay = outp
                    }
                    sb.append("---------- $label ----------\n$ $cmd\n").append(outp).append("\n\n")
                }
                // 2.96 · R14: báo cáo ANR gần nhất của CHÍNH Kachi (gói lúc chạy, không chuỗi cứng) — chỉ mục của gói này.
                val anr = anrSection(app.packageName) { sh(it) }
                sb.append(anr.second)
                summary.append("ANR gần nhất của Kachi: ").append(anr.first).append("\n")
                // ── TÓM TẮT: trả lời thẳng 2 câu hỏi đang treo, khỏi bắt người đọc lội hết file ──
                // ★ tái dùng dump đã chụp ở vòng commands() thay vì gọi lại — `dumpsys window windows` chạy dưới
                //   synchronized(mGlobalLock) của WindowManager, gọi 4 lần lúc đang lái là tự làm nghẽn WM.
                val wins = capturedWins ?: sh("dumpsys window windows")
                val frame = DisplayParse.appWindowFrame(wins, pkg)
                val winDisp = DisplayParse.appWindowDisplay(wins, pkg)
                val disp = capturedDisp ?: sh("dumpsys window displays")
                val dens = DisplayParse.density(disp, vdUse)
                val size = DisplayParse.logicalSize(disp, vdUse)
                val real = DisplayParse.realSizeOrNull(capturedDisplay ?: sh("dumpsys display"), vdUse)
                val osc = DisplayParse.overscan(disp, vdUse)
                summary.append("cụm: display ").append(if (measured.isEmpty()) "KHÔNG THẤY" else measured.joinToString(",")).append("\n")
                // CLUSTER-THEME-SAFE (2.89): lượt gửi / bỏ opcode đổi theme gần nhất và vì sao (SEND · VD_PRESENT · TOO_SOON …).
                summary.append("cổng theme: ")
                    .append(SimpleCastRuntime.themeVerdict() ?: "chưa có lượt nào trong tiến trình này").append("\n")
                // 2.89 · B4 DISPLAY-OWNER-DYNAMIC: cổng sở hữu display đang coi id nào là màn ảo Kachi / cụm (id dò live, không hằng 1).
                val own = com.byd.clusternav.system.WindowCommandDispatcher.get(app).ownership
                summary.append("cổng sở hữu: VD Kachi=").append(own.registeredVirtualDisplays().sorted())
                    .append(" · cụm=").append(own.castDisplay() ?: "chưa dò").append("\n")
                // B1a — "Cổng theme (chỉ đọc)": chạy lại các lượt đọc + quyết NGAY BÂY GIỜ, 0 lệnh ghi (kênh chỉ đọc).
                val gateNow = themeGateWith(app, shell)
                sb.append("---------- CỔNG THEME (chỉ đọc) ----------\n").append(gateNow).append("\n\n")
                summary.append(gateNow.lineSequence().firstOrNull { it.startsWith("kế hoạch:") } ?: "").append("\n")
                summary.append("cửa sổ $pkg: ").append(frame?.let { "[${it[0]},${it[1]}][${it[2]},${it[3]}]" } ?: "KHÔNG THẤY")
                    .append(winDisp?.let { " trên display $it" } ?: "").append("\n")
                summary.append("VD: size ").append(size?.let { "${it.first}x${it.second}" } ?: "?")
                    .append(" (thật ").append(real?.let { "${it.first}x${it.second}" } ?: "KHÔNG ĐO ĐƯỢC").append(") · dpi ")
                    .append(dens?.let { "${it.first} (gốc ${it.second})" } ?: "?").append("\n")
                // ★ v0.51 CHỈ kết luận khi cửa sổ THẬT SỰ nằm trên cụm. Bản cũ rút kết luận "app phớt lờ overscan"
                //   từ bất kỳ cửa sổ nào tìm thấy — kể cả cửa sổ đang nằm trên MÀN GIỮA, nơi chẳng bao giờ có
                //   overscan để mà phớt lờ. Kết luận sai kiểu đó đắt hơn là không kết luận.
                when {
                    frame == null -> summary.append("→ chưa kết luận: không thấy cửa sổ nào của app\n")
                    winDisp == null || winDisp !in measured ->
                        summary.append("→ chưa kết luận về overscan: cửa sổ đang ở display ")
                            .append(winDisp ?: "?").append(", KHÔNG phải cụm. Chụp lại khi app ĐANG chiếu.\n")
                    real == null -> summary.append("→ chưa kết luận: không đo được kích thước thật của cụm\n")
                    // ★ overscan đang [0,0][0,0] thì cửa sổ full là ĐƯƠNG NHIÊN — kết luận "app phớt lờ overscan"
                    //   lúc đó là sai, và nó đẩy đợt sau leo thẳng lên `wm size`, đúng thứ §6 cấm.
                    osc == null || osc.all { it == 0 } ->
                        summary.append("→ chưa kết luận: cụm KHÔNG đang đặt overscan (")
                            .append(osc?.let { "[${it[0]},${it[1]}][${it[2]},${it[3]}]" } ?: "không đọc được")
                            .append(") — chưa có gì để app phớt lờ\n")
                    else -> {
                        val full = frame[0] <= 1 && frame[1] <= 1 && frame[2] >= real.first - 1 && frame[3] >= real.second - 1
                        summary.append(if (full) "→ cửa sổ VẪN FULL: app PHỚT LỜ overscan (phải dùng wm size)\n"
                                       else "→ cửa sổ ĐÃ CO: app tôn trọng overscan\n")
                    }
                }
                // size-compat: hỏi cả hai nguồn; im lặng ở CẢ HAI mới dám nói "không có"
                val scA = sh("dumpsys activity activities | grep -iE 'sizeCompat|mCompatDisplayInsets' | head -5")
                val scW = sh("dumpsys window windows | grep -iE 'sizeCompat|mCompatDisplayInsets' | head -5")
                // mSizeCompatScale/Bounds nằm ở dumpsys window displays (block token của app) — grep riêng để
                //   in ĐÚNG con số (vd scale 0.727 + pillarbox), thay vì chỉ "có dấu hiệu".
                val scD = sh("dumpsys window displays | grep -iE 'mSizeCompatScale|mSizeCompatBounds' | head -3")
                val scAll = listOf(scA, scW, scD).filter { it.isNotBlank() }
                summary.append(when {
                    scAll.isEmpty() -> "→ không thấy size-compat (DPI sẽ ăn bình thường)\n"
                    else -> "→ CÓ size-compat — DPI sẽ KHÔNG ăn, app bị đóng băng cấu hình + thu nhỏ:\n" +
                        scAll.joinToString("\n") + "\n   (nguyên nhân: app non-resizeable + force_resizable tắt — xem mục CỜ FREEFORM/RESIZABLE)\n"
                })
                // ★ cảnh báo trạng thái hỏng — thứ đắt nhất mà bộ log cũ không hề nói
                // soi MỌI id cụm, không chỉ cái đầu — đúng tình huống VD tái tạo mà WmParse sinh ra để bắt
                val amEnts = StackParse.parse(sh("am stack list"))
                val orphan = (if (measured.isEmpty()) listOf(vdUse) else measured.toList())
                    .flatMap { WmParse.orphanStacksOn(disp, amEnts, it) }.distinct()
                if (orphan.isNotEmpty()) summary.append("⛔ CỤM CÓ ").append(orphan.size)
                    .append(" CỬA SỔ MỒ CÔI (WM thấy, ActivityManager không) → chỉ tắt máy xe mới sạch\n")
            }
        }.onFailure {
            sb.append("!! LỖI dadb: ${it.message}\n")
            summary.append("❌ không nối được dadb: ${it.message}\n")
        }
        // ★ v0.51: nhét tóm tắt vào ĐẦU file. Bản cũ chỉ hiện tóm tắt trên màn hình, file gửi đi mở đầu thẳng
        //   bằng 1200 dòng dump thô — người nhận phải lội hết mới biết bệnh, mà mục đích của file là để GỬI.
        val path = write(app, stamp, "=== TÓM TẮT ===\n" + summary.toString().trim() + "\n\n" + sb.toString())
        return path to summary.toString().trim()
    }

    /**
     * 2.96 · R14 — mục "ANR gần nhất của Kachi" (màn Chẩn đoán, CLAUDE.md §11): một phiên dadb, MỘT lệnh chỉ đọc
     * ([AnrDropbox.CMD]), lọc thuần [AnrDropbox.latestFor] theo gói LÚC CHẠY (`packageName`) — mục của app khác không bao giờ
     * lọt vào; ≤ [AnrDropbox.MAX_BYTES]. Chạy trên luồng gọi (nền) — KHÔNG gọi trên main thread.
     */
    fun latestAnr(ctx: Context): String {
        val app = ctx.applicationContext
        return runCatching {
            LocalDeviceShell.session(AdbKeys.ensure(app), LocalShellRetry.BACKGROUND_READ_CAP) { shell ->
                anrSection(app.packageName) { shell(it).output }.second
            } ?: "❌ không nối được dadb — không đọc được gì"
        }.getOrElse { "❌ ${it.javaClass.simpleName}: ${it.message}" }
    }

    /** (dòng tóm tắt, khối báo cáo) cho ANR gần nhất của [selfPkg]; [run] = kênh shell sẵn có của lượt gọi. */
    private fun anrSection(selfPkg: String, run: (String) -> String): Pair<String, String> {
        val entry = AnrDropbox.latestFor(run(AnrDropbox.CMD), selfPkg)
        val body = "---------- ANR GẦN NHẤT CỦA $selfPkg (dropbox) ----------\n$ ${AnrDropbox.CMD}\n" +
            (entry ?: "(không có mục data_app_anr nào của $selfPkg)") + "\n\n"
        return (entry?.lineSequence()?.firstOrNull() ?: "không có") to body
    }

    /**
     * CLUSTER-THEME-SAFE B1a — mục "Cổng theme (chỉ đọc)" (màn Chẩn đoán, CLAUDE.md §11): mở MỘT phiên dadb, chạy đúng các
     * lượt đọc của cổng (`dumpsys display`, — chỉ khi mức B bật — `am stack list` + cửa sổ) + `getprop persist.sys.car.type`,
     * quyết bằng chính `ClusterStylePlan` của lượt mở chiếu, in kết quả. KHÔNG gửi gì: kênh bọc
     * [ThemeGatePreview.ReadOnlyShell] (lệnh ngoài danh sách đọc bị từ chối ở tầng thi hành), sổ theme chỉ đọc. Chạy trên
     * luồng gọi (nền) — KHÔNG gọi trên main thread.
     */
    fun themeGate(ctx: Context): String {
        val app = ctx.applicationContext
        return runCatching {
            LocalDeviceShell.session(AdbKeys.ensure(app), LocalShellRetry.BACKGROUND_READ_CAP) { shell -> themeGateWith(app, shell) }
                ?: "❌ không nối được dadb — không đọc được gì"
        }.getOrElse { "❌ ${it.javaClass.simpleName}: ${it.message}" }
    }

    private fun themeGateWith(app: Context, run: (String) -> LocalShellText): String {
        val raw = object : SimpleCastShell {
            override fun execute(command: String): ShellResult = run(command).let { ShellResult(it.exitCode, it.output, it.errorOutput) }
        }
        val ro = ThemeGatePreview.ReadOnlyShell(raw)
        val inProc = ClusterCarType.parse(com.byd.clusternav.SysProps.get(ClusterCarType.PROP))
        val saved = ClusterProfile.carType(app)
        val viaDadb = ClusterCarType.parse(ro.execute(ClusterCarType.CMD).takeIf { it.success }?.stdout)
        val resolved = ClusterProfile.resolve(app)
        // Tiến trình chưa đọc được mã mà dadb đọc được ⇒ quyết theo mã THẬT (lượt mở chiếu đầu sẽ tự lưu nó — `refineByShell`).
        val profile = if (saved == null && viaDadb != null) resolved.forCarType(viaDadb) else resolved
        // Review 2.89 Pass 2 · cluster-r1-6 / whole-r1-3: kiểu muốn = ĐÚNG lựa chọn mà lượt mở thật đọc (`cast_style` của hồ sơ —
        // `SimpleCastRuntime` › `desiredStyle`), đọc thẳng prefs, KHÔNG dựng coordinator. Trước đây gõ cứng Bo tròn (bản B1a) ⇒
        // người lái chọn Chữ nhật chụp màn được opcode/kế hoạch SAI so với thứ xe vừa làm.
        val desired = desiredStyle(app)
        val header = listOf(
            "car.type: trong tiến trình=${inProc ?: "không đọc được"} · dùng=${saved ?: "chưa có"} · dadb=${viaDadb ?: "không đọc được"}",
            "hồ sơ: ${profile.summary()}",
            "kiểu muốn: $desired (lựa chọn Bo tròn / Chữ nhật của hồ sơ, khoá cast_style — lượt mở chiếu kế đọc đúng giá trị này)",
        )
        val body = ThemeGatePreview.report(
            ro, com.byd.clusternav.BuildConfig.APPLICATION_ID, profile.projectionRecipe(),
            SimpleCastRuntime.themeLedgerStore(app), SimpleCastRuntime.themeClock(app), desired, header,
        )
        return if (ro.refused.isEmpty()) body else "$body\n⛔ kênh chỉ đọc đã chặn: ${ro.refused}"
    }

    /**
     * Lựa chọn kiểu chiếu cụm của HỒ SƠ đang dùng (`simple_cast_prefs` › `cast_style`) — cùng tệp + khoá + phép parse với
     * `SharedPrefsSimpleCastPrefs.castStyle()` (vắng / lạ ⇒ Bo tròn, D3). Chỉ đọc.
     */
    private fun desiredStyle(app: Context): CastStyle = CastStyle.parse(
        app.getSharedPreferences(ProfileScopeCluster.SIMPLE_CAST_FILE, Context.MODE_PRIVATE).all[CAST_STYLE_KEY] as? String,
    )

    /** Khoá lựa chọn kiểu chiếu cụm — trùng `SharedPrefsSimpleCastPrefs` (bài canh `ClusterThemeB1aWiringContractTest`). */
    private const val CAST_STYLE_KEY = "cast_style"

    private fun write(ctx: Context, stamp: String, body: String): String = runCatching {
        val dir = java.io.File(ctx.getExternalFilesDir(null), "diag").apply { mkdirs() }
        // giữ tối đa 20 file gần nhất — xe chạy nhiều phiên, không để phình vô hạn
        dir.listFiles()?.sortedBy { it.lastModified() }?.dropLast(19)?.forEach { runCatching { it.delete() } }
        val f = java.io.File(dir, "diag-$stamp.txt")
        f.writeText(body)
        f.absolutePath
    }.getOrElse { "(không ghi được file: ${it.message})" }
}
