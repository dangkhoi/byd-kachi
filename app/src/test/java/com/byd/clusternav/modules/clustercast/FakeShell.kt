package com.byd.clusternav.modules.clustercast

/**
 * ★ HARNESS test off-device — mô phỏng `sh: (String)->String` (dadb shell) bằng một [FakeDevice] state model.
 *
 * Vì sao: không có emulator BYD DiLink (cụm XDJA/AutoContainer/HAL proprietary). Nhưng mọi logic quyết định
 * của ClusterCast đi qua đúng một seam `sh(cmd)` → render output `am stack list` / `dumpsys window displays` /
 * `dumpsys display` KHỚP ĐỊNH DẠNG Android 10, và MUTATE state khi nhận `am display move-stack` → chạy được
 * E2E flow + stress off-xe. Fixture định dạng nguyên văn (khớp StackParse/WmParse/DisplayParse regex).
 */

/** 1 stack trên FakeDevice. [amVisible]=false → chỉ WM thấy (mồ côi: WM có, AM không). */
data class FakeStack(
    val stackId: Int,
    var displayId: Int,
    val taskId: Int,
    val comp: String,                 // "pkg/.Cls"
    var mode: String = "fullscreen",  // fullscreen · freeform · pinned  (var: `am start --windowingMode` đổi được)
    val activityType: String = "standard",  // standard · home
    val amVisible: Boolean = true,    // false = orphan (WM thấy, `am stack list` không liệt kê)
) {
    /**
     * ★ v0.7x — Z-ORDER seq (do [FakeDevice] cấp lúc add / mỗi lần `am start`/`move-stack` đưa stack lên top).
     * Cao hơn = ở TRÊN. Dùng cho MODEL OCCLUSION ([FakeDevice.isVisibleModel]). KHAI NGOÀI primary constructor
     * → KHÔNG vào equals/hashCode/copy của data class (không phá test so-sánh StackEntry/FakeStack).
     */
    var front: Int = 0
}

/**
 * Mô hình đầu xe giả. [vd]=display cụm; [freeformAlive]=`am task resize` có được chấp nhận không;
 * [moveFailStacks]=stackId sẽ bị `am display move-stack` từ chối (mô phỏng lỗi hiện trường).
 */
class FakeDevice(
    val vd: Int = 1,
    var freeformAlive: Boolean = false,
    var vdW: Int = 1920,
    var vdH: Int = 720,
    val moveFailStacks: MutableSet<Int> = mutableSetOf(),
    /** pkg mà `am start --windowingMode 1` KHÔNG ép được fullscreen (kẹt freeform-bé) — mô phỏng nghi phạm freeze H4/F3. */
    val stuckFreeformPkgs: MutableSet<String> = mutableSetOf(),
    /**
     * pkg mà `am start --display <vd>` KHÔNG relocate được sang VD (ActivityStarter "looking on all displays"
     * → app ở lại display 0) — mô phỏng đúng triệu chứng hiện trường, buộc code phải LEO R2 `am display
     * move-stack … <vd>`. Rỗng mặc định → mọi test cũ giữ nguyên hành vi (am start luôn land).
     */
    val startNoLandPkgs: MutableSet<String> = mutableSetOf(),
    /** pkg mà GENTLE `am start --display 0` KHÔNG relocate được khỏi VD (mô phỏng gentle hụt → buộc force-stop fallback, HIẾM). */
    val gentleStayOnVdPkgs: MutableSet<String> = mutableSetOf(),
    /**
     * ★ v0.7x (T7) — SINK size-compat CƯỠNG LẠI occlude: LUÔN `isVisible==true` trên VD (occlude-verify không bao
     * giờ xác nhận invisible → oldOccluded=false) VÀ không rời VD khi gentle. Dùng để test đường ĐỂ-YÊN (R11):
     * sink cũ cưỡng lại → KHÔNG force-stop, KHÔNG move-stack, chấp nhận 2-on-VD.
     */
    val resistReturnPkgs: MutableSet<String> = mutableSetOf(),
) {
    val stacks = mutableListOf<FakeStack>()
    var moveCount = 0
    var resizeCount = 0
    var overscanCount = 0

    // ── v0.66 (F9) — mở rộng cho test HOT-SWAP freeze-proof ──
    /** MỌI lệnh shell đã chạy (để assert: có force-stop? KHÔNG có `move-stack …0`?). */
    val commands = mutableListOf<String>()
    /** Các pkg đã bị `am force-stop` (để assert force-stop ĐÚNG app, KHÔNG force-stop sink/target). */
    val forceStoppedPkgs = mutableListOf<String>()
    /** Hook chạy NGAY SAU mỗi render `am stack list` — mô phỏng B (Maps GL) bounce khỏi VD giữa gate và bước sau (F4 TOCTOU). */
    var afterStackList: (() -> Unit)? = null
    private var seq = 900
    /** Cấp id stack/task mới (cho `am start` dựng lại app sau force-stop). */
    fun nextId(): Int = ++seq
    // ★ v0.7x — Z-ORDER seq: mỗi lần add / `am start` / `move-stack` đưa stack lên TOP → front cao nhất.
    private var frontSeq = 0
    fun nextFront(): Int = ++frontSeq

    /**
     * ★ v0.7x — MODEL OCCLUSION cho OCCLUDE-VERIFY (WmParse.isVisibleOn đọc `isVisible=` render dưới đây).
     * Một standard stack bị INVISIBLE khi có standard stack KHÁC cùng display, front CAO HƠN (target full-VD phủ lên).
     * [resistReturnPkgs] (size-compat sink tự re-front) → LUÔN visible (occlude-verify fail → oldOccluded=false).
     * Non-standard (home) → không xét ở đây (luôn coi visible).
     */
    fun isVisibleModel(s: FakeStack): Boolean {
        if (s.comp.substringBefore('/') in resistReturnPkgs) return true
        if (s.activityType != "standard") return true
        return stacks.none { o -> o !== s && o.displayId == s.displayId && o.activityType == "standard" && o.front > s.front }
    }

    fun add(s: FakeStack) = apply { s.front = nextFront(); stacks.add(s) }

    /** Render `am stack list` — CHỈ các stack amVisible (mồ côi bị ẩn, đúng như AM thật). */
    fun amStackList(): String = buildString {
        for (s in stacks.filter { it.amVisible }) {
            appendLine("Stack id=${s.stackId} bounds=[0,0][1920,1080] displayId=${s.displayId} userId=0")
            appendLine("  configuration={1.0 winConfig={ mWindowingMode=${s.mode} mActivityType=${s.activityType}} s.1}")
            appendLine("  taskId=${s.taskId}: ${s.comp} bounds=[0,0][1920,1080] userId=0 visible=true")
        }
    }

    /** Render `dumpsys window displays` — MỌI stack (kể cả mồ côi), nhóm theo display, đúng vùng token WmParse cần. */
    fun windowDisplays(): String = buildString {
        for (d in stacks.map { it.displayId }.distinct().sorted()) {
            appendLine("  Display: mDisplayId=$d")
            appendLine("    Application tokens in top down Z order:")
            for (s in stacks.filter { it.displayId == d }) {
                appendLine("      mStackId=${s.stackId}")
                appendLine("        taskId=${s.taskId}")
                // ★ v0.7x: kèm isVisible= (MODEL OCCLUSION) trên CÙNG dòng ActivityRecord → WmParse.isVisibleOn đọc được.
                appendLine("          appTokens=[AppWindowToken{a1 token=Token{a2 ActivityRecord{a3 u0 ${s.comp} t${s.taskId}}}}] isVisible=${isVisibleModel(s)}")
            }
            appendLine("    DockedStackDividerController")   // RE_TOKENS_END → đóng vùng token
        }
    }

    /** Render `dumpsys display` tối thiểu cho DisplayParse.realSize(vd). */
    fun dumpsysDisplay(): String = buildString {
        appendLine("Display Devices: size=2")
        appendLine("  mDisplayId=0")
        appendLine("""    mBaseDisplayInfo=DisplayInfo{"built-in", displayId 0, real 1920 x 1080, largest app 1920 x 1080, smallest app 1080 x 1080, density 240}""")
        appendLine("  mDisplayId=$vd")
        appendLine("""    mBaseDisplayInfo=DisplayInfo{"fission_bg_xdjaVirtualSurface", displayId $vd, real $vdW x $vdH, largest app $vdW x $vdW, smallest app $vdH x $vdH, density 320}""")
    }
}

/** sh giả: dispatch lệnh → đọc/mutate [dev]. Trả output khớp định dạng thật. */
fun fakeShell(dev: FakeDevice): (String) -> String = fun(cmd: String): String {
    val c = cmd.trim()
    dev.commands.add(c)                                   // ★ ghi MỌI lệnh để test assert chuỗi (force-stop? move-stack …0?)
    fun toks() = c.split(Regex("\\s+"))
    fun valAfter(flag: String): String? { val t = toks(); val i = t.indexOf(flag); return if (i >= 0 && i + 1 < t.size) t[i + 1] else null }
    return when {
        c.startsWith("am stack list") -> { val out = dev.amStackList(); dev.afterStackList?.invoke(); out }   // hook SAU render (F4)
        c.contains("dumpsys window displays") || c.startsWith("dumpsys window") -> dev.windowDisplays()
        c.startsWith("dumpsys display") -> dev.dumpsysDisplay()
        // ★ v0.66 (F9): resolve-activity → trả component (ưu tiên comp thật của stack pkg → giữ hint carplay/aa cho
        //   isPhoneProjection; else synth). resolveComp lấy dòng cuối có '/' và không có space.
        c.startsWith("cmd package resolve-activity") -> {
            val pkg = toks().lastOrNull().orEmpty()
            dev.stacks.firstOrNull { it.comp.substringBefore('/') == pkg }?.comp ?: "$pkg/.Main"
        }
        // ★ v0.66 (F9): force-stop → GỠ mọi stack của pkg khỏi model (process death) + ghi lại (KHÔNG treo trong fake).
        c.startsWith("am force-stop") -> {
            val pkg = c.removePrefix("am force-stop").trim().substringBefore(' ').trim()
            if (pkg.isNotEmpty()) { dev.forceStoppedPkgs.add(pkg); dev.stacks.removeAll { it.comp.substringBefore('/') == pkg } }
            ""
        }
        // ★ v0.66 (F9): am start → ĐẶT/DI CHUYỂN stack của pkg lên display d với windowing-mode (để app "land").
        //   --windowingMode 5=freeform · 1=fullscreen. Chưa có stack (đã force-stop) → dựng MỚI. Mỗi lần đặt/dời
        //   lên top → cấp front MỚI (Z-order) để MODEL OCCLUSION (target phủ app cũ).
        c.startsWith("am start") -> {
            val comp = valAfter("-n")
            if (comp == null || !comp.contains("/")) "" else {
                val d = valAfter("--display")?.toIntOrNull() ?: 0
                val modeStr = when (valAfter("--windowingMode")) { "5" -> "freeform"; "1" -> "fullscreen"; else -> "fullscreen" }
                val pkg = comp.substringBefore('/')
                // ★ F3: app kẹt freeform-bé (phớt lờ windowingMode 1) → returnAppToMain phải trả false, không claim thành công.
                val finalMode = if (pkg in dev.stuckFreeformPkgs) "freeform" else modeStr
                // ★ v0.7x (T7): --activity-clear-task = FRESH RESET (recipe #4) → gỡ task cũ của pkg TRƯỚC (mô phỏng
                //   clear-task giết TaskRecord) rồi dựng mới. RESUME (không cờ này) → relocate stack cũ (GIỮ taskId=state).
                val clearTask = c.contains("--activity-clear-task")
                if (clearTask) dev.stacks.removeAll { it.comp.substringBefore('/') == pkg && it.mode != "pinned" }
                // ★ R2 fallback: app mà `am start --display <vd>` KHÔNG relocate (ActivityStarter redirect về d0);
                //   am start thành NO-OP placement → buộc swapOnVd/placeLadder leo R2 `am display move-stack … <vd>`.
                //   Chỉ chặn khi yêu cầu đẩy LÊN VD (d ≠ 0); về d0 vẫn land bình thường (dựng lại fullscreen).
                val noLand = pkg in dev.startNoLandPkgs && d != 0
                val existing = dev.stacks.filter { it.comp.substringBefore('/') == pkg && it.mode != "pinned" }
                // ★ gentle hụt / sink CƯỠNG LẠI (R11): `am start --display 0` KHÔNG bê task đang trên VD (leave-in-place).
                val gentleStay = (pkg in dev.gentleStayOnVdPkgs || pkg in dev.resistReturnPkgs) && d == 0 && existing.any { it.displayId == dev.vd }
                when {
                    existing.isNotEmpty() -> if (!noLand && !gentleStay) existing.forEach { it.displayId = d; it.mode = finalMode; it.front = dev.nextFront() }
                    noLand -> dev.stacks.add(FakeStack(dev.nextId(), 0, dev.nextId(), comp, mode = "fullscreen", activityType = "standard").also { it.front = dev.nextFront() })
                    else -> dev.stacks.add(FakeStack(dev.nextId(), d, dev.nextId(), comp, mode = finalMode, activityType = "standard").also { it.front = dev.nextFront() })
                }
                ""
            }
        }
        c.startsWith("am display move-stack") -> {
            val t = toks()
            val sid = t.getOrNull(3)?.toIntOrNull()
            val target = t.getOrNull(4)?.toIntOrNull()
            if (sid == null || target == null) "Error: bad move-stack"
            else if (sid in dev.moveFailStacks) "java.lang.SecurityException: move rejected for stack $sid"
            else {
                dev.moveCount++
                dev.stacks.filter { it.stackId == sid }.forEach { it.displayId = target; it.front = dev.nextFront() }
                ""
            }
        }
        c.startsWith("am task resize") -> {
            dev.resizeCount++
            if (dev.freeformAlive) "" else "java.lang.IllegalArgumentException: Task ... not allowed"
        }
        c.startsWith("wm overscan") -> { dev.overscanCount++; "" }
        else -> ""
    }
}
