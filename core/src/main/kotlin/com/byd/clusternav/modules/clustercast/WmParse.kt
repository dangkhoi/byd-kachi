package com.byd.clusternav.modules.clustercast

/**
 * PARSE `dumpsys window displays` — nguồn sự thật THỨ HAI, độc lập với `am stack list`. PURE → test off-device.
 *
 * VÌ SAO CẦN (lỗi hiện trường 22/07, xe Seal):
 * `diag-0722-073807.txt` cho thấy WindowManager có `mStackId=9` / `taskId=13` (CarPlay) nằm trên display 1,
 * cửa sổ đã vẽ xong (`firstWindowDrawn=true`) — trong khi `am stack list` CÙNG FILE liệt kê 7 stack và
 * **tất cả đều `displayId=0`**, không hề có stack 9.
 *
 * Đó là stack MỒ CÔI: ActivityManager không còn biết nó tồn tại nên **không lệnh `am`/`wm` nào chạm tới được**
 * — chỉ khởi động lại đầu xe mới sạch (đúng lớp hỏng CLAUDE.md §4 mô tả). Mọi cơ chế "cụm chỉ được có 1 app"
 * của ClusterNav đều đọc từ `am stack list` nên **mù tuyệt đối** với trạng thái này, và mọi lệnh bắn tiếp
 * chỉ làm hỏng thêm.
 *
 * ⇒ Phát hiện được lệch WM↔AM là điều kiện để DỪNG đúng lúc thay vì bắn thêm lệnh.
 */
object WmParse {

    /** Một stack theo góc nhìn của WindowManager. [pkgs] rút từ appTokens (có thể rỗng nếu stack không có app token). */
    data class WmStack(
        val displayId: Int,
        val stackId: Int,
        val taskIds: List<Int>,
        val pkgs: List<String>,
        /** WM đang GIỮ LẠI stack chờ animation xong. Đây là cơ chế HỢP LỆ khiến WM sống lâu hơn AM — không phải hỏng. */
        val deferRemoval: Boolean = false,
    )

    private val RE_DISPLAY = Regex("Display: mDisplayId=(\\d+)")
    private val RE_STACK = Regex("^mStackId=(\\d+)")
    private val RE_TASK = Regex("^taskId=(\\d+)")
    // ActivityRecord{... u0 com.pkg/.Cls t13}  → bắt "com.pkg" đứng trước dấu '/'
    private val RE_PKG = Regex("ActivityRecord\\{\\S+ u\\d+ ([A-Za-z][A-Za-z0-9_.]*)/")
    private val RE_DEFER = Regex("^mDeferRemoval=(\\w+)")
    /** Cờ hiển thị của MỘT app-token (`dumpsys window displays`) — nguồn sự thật cho OCCLUDE-VERIFY (D5 U3). */
    private val RE_TOKEN_VIS = Regex("isVisible=(true|false)")
    /** Mở vùng liệt kê stack. Ngoài vùng này, `ActivityRecord{...}` còn xuất hiện ở mFocusedApp/DisplayPolicy… */
    private val RE_TOKENS_BEGIN = Regex("^Application tokens in top down Z order:")
    /** Các mục ĐỒNG CẤP đóng vùng token lại (đọc từ dump thật: đây là các block anh em của "Application tokens"). */
    private val RE_TOKENS_END = Regex("^(DockedStackDividerController|PinnedStackController|DisplayFrames|DisplayPolicy|DisplayRotation|WindowInsetsStateController|mSystemGestureExclusion)")

    /**
     * Mọi stack WM nhìn thấy, kèm display của nó.
     *
     * Cấu trúc dump (A10): mỗi block mở bằng `Display: mDisplayId=N`, bên trong phần
     * "Application tokens in top down Z order:" có các `mStackId=`, dưới mỗi stack là `taskId=` rồi `appTokens=[...]`.
     * Block kết thúc khi gặp `Display: mDisplayId=` kế tiếp — KHÔNG dựa vào thứ tự id (z-order, không phải id tăng dần).
     */
    fun stacks(dump: String): List<WmStack> {
        val out = ArrayList<WmStack>()
        var display = -1
        var stackId = -1
        var tasks = ArrayList<Int>()
        var pkgs = LinkedHashSet<String>()
        var defer = false
        var inTokens = false

        fun flush() {
            if (stackId >= 0 && display >= 0) out.add(WmStack(display, stackId, tasks.toList(), pkgs.toList(), defer))
            stackId = -1; tasks = ArrayList(); pkgs = LinkedHashSet(); defer = false
        }

        for (raw in dump.lineSequence()) {
            val line = raw.trim()
            RE_DISPLAY.find(line)?.let { m ->
                flush()
                display = m.groupValues[1].toIntOrNull() ?: -1
                inTokens = false
                return@let
            }
            if (RE_TOKENS_BEGIN.containsMatchIn(line)) { inTokens = true; continue }
            // ★ ĐÓNG VÙNG. Không đóng thì `mFocusedApp=Token{… com.byd.carplay.ui/.VideoActivity}` ở block
            //   DisplayPolicy bị tính vào stack cuối cùng → pkgsOn báo thừa một app không hề ở trên cụm.
            if (RE_TOKENS_END.containsMatchIn(line)) { flush(); inTokens = false; continue }
            if (!inTokens) continue
            RE_STACK.find(line)?.let { m ->
                flush()
                stackId = m.groupValues[1].toIntOrNull() ?: -1
                return@let
            }
            if (stackId < 0) continue
            RE_DEFER.find(line)?.let { defer = it.groupValues[1] == "true" }
            RE_TASK.find(line)?.let { m -> m.groupValues[1].toIntOrNull()?.let { tasks.add(it) } }
            RE_PKG.findAll(line).forEach { pkgs.add(it.groupValues[1]) }
        }
        flush()
        return out
    }

    /**
     * Id các stack WM thấy trên display [vd]. Truy vấn THUẦN, không guard — hỏi display 0 cũng trả lời thật,
     * vì việc dọn cửa sổ nổi kẹt trên màn giữa cần đọc đúng display 0.
     * Guard an toàn ("không đụng vào display không phải cụm") nằm ở tầng RA QUYẾT ĐỊNH bên dưới, không nằm ở đây —
     * đặt guard trong hàm đọc thì vừa mất một truy vấn hợp lệ, vừa khiến người gọi tưởng là đã an toàn.
     */
    fun stackIdsOn(dump: String, vd: Int): Set<Int> =
        stacks(dump).filter { it.displayId == vd }.map { it.stackId }.toSet()

    /** Gói WM thấy trên display [vd] — dùng để báo cho người dùng biết CÁI GÌ đang kẹt ở đó. */
    fun pkgsOn(dump: String, vd: Int): List<String> =
        stacks(dump).filter { it.displayId == vd }.flatMap { it.pkgs }.distinct()

    /**
     * ★ v0.7x (T7 · D5 U3) — OCCLUDE-VERIFY: [pkg] có app-token đang HIỂN THỊ (isVisible==true) trên [vd] không?
     *
     * Vì sao cần (CP/AA orphan v0.67): trước khi gentle-move app cũ khỏi VD ([CastShell.returnAppToMain]), phải
     * xác nhận app cũ ĐÃ bị target (fresh-launch full-VD) PHỦ LÊN → `isVisible==false`. Gentle-move một task
     * size-compat/sink CÒN VISIBLE vượt ranh freeform → `AppWindowToken.initializeChangeTransition` (research
     * #2 RISKY) → reparent nửa vời → cửa sổ MỒ CÔI (brick tới reboot). Chỉ khi app cũ vô hình thì gentle mới
     * né được change-transition (snapshot-free). PURE → unit-test off-device.
     *
     * ★★ MẶC ĐỊNH AN TOÀN (khi thiếu bằng chứng xe — MUST-VALIDATE-ON-CAR §3.3): nếu tìm THẤY token của [pkg]
     *   trên [vd] nhưng KHÔNG đọc được cờ `isVisible=` (format dump đời xe khác) → COI NHƯ VISIBLE (true). Nhờ vậy
     *   occlude-verify KHÔNG xác nhận invisible → oldOccluded=false → caller KHÔNG gentle-move (sink để yên / app
     *   thường force-stop) → tuyệt đối KHÔNG bao giờ gentle-move một task chưa-chắc-vô-hình (chống tái tạo orphan).
     *
     * @return true nếu token của [pkg] trên [vd] `isVisible==true` (hoặc có mặt mà không xác định được cờ);
     *   false CHỈ KHI: [pkg] KHÔNG có token trên [vd] (vắng mặt/occluded hẳn), HOẶC token có cờ `isVisible=false`; hoặc [vd] không hợp lệ.
     */
    fun isVisibleOn(dump: String, pkg: String, vd: Int): Boolean {
        if (vd < 1) return false
        var cur = -1
        var foundOnVd = false
        var explicitFalse = false
        for (raw in dump.lineSequence()) {
            val line = raw.trim()
            RE_DISPLAY.find(line)?.let { cur = it.groupValues[1].toIntOrNull() ?: cur }
            if (cur != vd) continue
            val m = RE_PKG.find(line) ?: continue
            if (m.groupValues[1] != pkg) continue
            foundOnVd = true
            when (RE_TOKEN_VIS.find(line)?.groupValues?.get(1)) {
                "true" -> return true                 // rõ ràng đang hiển thị
                "false" -> explicitFalse = true       // token này invisible — quét tiếp (token khác của pkg có thể visible)
                else -> {}                            // token có mặt mà không đọc được cờ → xét mặc-định-an-toàn bên dưới
            }
        }
        // Không có token trên vd → vắng mặt (false). Có token + đọc được toàn 'false' → occluded (false).
        // Có token nhưng KHÔNG đọc được cờ nào (format lạ) → MẶC ĐỊNH AN TOÀN = VISIBLE (true) → KHÔNG gentle-move.
        return foundOnVd && !explicitFalse
    }

    /**
     * Stack MỒ CÔI trên [vd]: WM thấy mà AM không thấy.
     *
     * @param wmDump output `dumpsys window displays`
     * @param amEntries kết quả [StackParse.parse] của `am stack list`
     *
     * KHÔNG so chiều ngược lại (AM có mà WM không): stack vừa tạo chưa có cửa sổ nào là chuyện bình thường,
     * báo động ở đó chỉ tạo cảnh báo giả.
     */
    fun orphanStacksOn(wmDump: String, amEntries: List<StackEntry>, vd: Int): Set<Int> {
        if (vd < 1) return emptySet()
        // ★ KHÔNG CÓ DỮ LIỆU THÌ NÓI "CHƯA BIẾT", ĐỪNG KẾT LUẬN (§2). `am stack list` trả rỗng khi shell hụt —
        //   mà lúc cắm CarPlay/AA là lúc đầu xe bận nhất, adbd hay trả lời hụt nhất. Coi rỗng là "AM không thấy gì"
        //   sẽ biến MỌI stack thành mồ côi và khoá người dùng khỏi tính năng bằng một câu báo động sai.
        if (amEntries.isEmpty()) return emptySet()
        val am = amEntries.map { it.stackId }.toSet()
        return stacks(wmDump)
            .filter { it.displayId == vd }
            // stack RỖNG: StackParse chỉ sinh entry khi có `taskId=`, nên stack không task KHÔNG BAO GIỜ xuất hiện
            // phía AM. So hai tập lệch luật nhau là tự tạo mồ côi giả — phải đối xứng.
            .filter { it.taskIds.isNotEmpty() || it.pkgs.isNotEmpty() }
            // WM giữ lại chờ animation — hợp lệ, không phải hỏng.
            .filterNot { it.deferRemoval }
            .map { it.stackId }
            .filterNot { it in am }
            .toSet()
    }

    /**
     * Tập display id của CỤM (tên chứa xdja/fission) đọc từ `dumpsys display`.
     *
     * Vì sao là TẬP chứ không phải một số: (a) opcode 16 có thể tái tạo VD → id mới, id cũ còn trong dump;
     * (b) trên xe có Dudu launcher, `launcher-split` của nó chiếm display 1 và cụm lùi xuống 2 — mọi chỗ lọc
     * `displayId >= 1` sẽ đụng nhầm vào display RIÊNG (FLAG_PRIVATE) của launcher, đúng mẫu lỗi §4 từng làm đơ launcher.
     */
    fun clusterDisplayIds(dumpsysDisplay: String): Set<Int> {
        val out = LinkedHashSet<Int>()
        var cur = -1
        for (raw in dumpsysDisplay.lineSequence()) {
            val line = raw.trim()
            Regex("Display (\\d+):").find(line)?.let { cur = it.groupValues[1].toIntOrNull() ?: cur }
            val l = line.lowercase()
            if (!l.contains("fission") && !l.contains("xdja")) continue
            val inline = Regex("(?:displayId|Display) (\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
            val id = inline ?: cur
            if (id >= 1) out.add(id)
        }
        return out
    }
}
