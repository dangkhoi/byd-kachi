package com.byd.clusternav.modules.clustercast

import com.byd.clusternav.carexec.LocalDeviceShell
import com.byd.clusternav.carexec.LocalShellRetry
import android.content.Context
import com.byd.clusternav.AdbKeys

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

    private fun write(ctx: Context, stamp: String, body: String): String = runCatching {
        val dir = java.io.File(ctx.getExternalFilesDir(null), "diag").apply { mkdirs() }
        // giữ tối đa 20 file gần nhất — xe chạy nhiều phiên, không để phình vô hạn
        dir.listFiles()?.sortedBy { it.lastModified() }?.dropLast(19)?.forEach { runCatching { it.delete() } }
        val f = java.io.File(dir, "diag-$stamp.txt")
        f.writeText(body)
        f.absolutePath
    }.getOrElse { "(không ghi được file: ${it.message})" }
}
