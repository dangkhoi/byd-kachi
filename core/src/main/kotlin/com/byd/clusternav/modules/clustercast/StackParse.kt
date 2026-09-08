package com.byd.clusternav.modules.clustercast

/**
 * PARSE `am stack list` → danh sách [StackEntry]. PURE (không đụng Android) → unit-test off-device được.
 *
 * ★ VÌ SAO PHẢI CÓ (v0.36): 3 helper cũ (appStack/appDisplay/appTaskId) đều "first-match wins" và MÙ windowing-mode.
 *   App có 2 task (wrapper Android Auto, launcher trampoline) → dễ báo "chưa bám VD" OAN dù đã lên cụm;
 *   app rơi vào PIP → stack `pinned` là stack riêng → lấy nhầm taskId → `am task resize` bắn sai task.
 *
 * ⚠ ĐÍNH CHÍNH (bắt được lúc review, 2026-07-21): ĐỪNG giả định thứ tự in ra.
 *   `getAllStackInfos` duyệt `mActivityDisplays` theo **THỨ TỰ Z, KHÔNG PHẢI theo display id**:
 *   `POSITION_TOP = Integer.MAX_VALUE` / `POSITION_BOTTOM = Integer.MIN_VALUE` (ActivityDisplay.java:84-85);
 *   lúc boot display 0 được đẩy về CUỐI danh sách (RootActivityContainer.java:256 `positionChildAt(default, POSITION_TOP)`),
 *   còn VD tạo lúc chạy vào ĐẦU danh sách (:315 `addChild(..., POSITION_BOTTOM)`) — nhưng thứ tự này ĐỔI mỗi lần
 *   một display được đưa lên trên (vd `am start --display N` vừa chạy). Nên "display 0 in trước" hay "VD in trước"
 *   đều SAI như nhau. Cách đúng DUY NHẤT: lấy HẾT match rồi lọc theo displayId — xem [pick].
 *
 * ĐỊNH DẠNG (Android 10, ActivityManager.StackInfo.toString):
 *   Stack id=78 bounds=[0,0][1920,1080] displayId=0 userId=0
 *     configuration={... winConfig={ ... mWindowingMode=fullscreen ... } ...}
 *     taskId=80: com.byd.androidauto/.MainActivity bounds=[...] userId=0 visible=true topActivity=...
 */
data class StackEntry(
    val stackId: Int,
    val displayId: Int,
    val mode: String,      // fullscreen · freeform · pinned · split-screen-* · "" (không đọc được)
    /** standard · home · recents · assistant · undefined — LOẠI stack. Đọc từ "mActivityType=" cùng dòng configuration. */
    val activityType: String = "",
    val taskId: Int,
    val comp: String,      // "pkg/cls"
    val visible: Boolean,
    /** bounds của STACK (từ dòng "Stack id=… bounds=[l,t][r,b]") — dùng để KIỂM CHỨNG lệnh resize có ăn thật không. */
    val bounds: IntArray? = null,
) {
    val pkg: String get() = comp.substringBefore('/')
    val isPinned: Boolean get() = mode == "pinned"
    /** stack app THƯỜNG — thứ DUY NHẤT được phép bê qua lại giữa các display. */
    val isStandard: Boolean get() = activityType.isBlank() || activityType == "standard"
    /** home/recents/assistant — ĐỘNG VÀO LÀ HỎNG MÁY, xem [StackParse.evictableOnVd]. */
    val isSystemStack: Boolean get() = activityType == "home" || activityType == "recents" || activityType == "assistant"
    val isFreeform: Boolean get() = mode == "freeform"
    override fun equals(other: Any?) = other is StackEntry && other.stackId == stackId && other.taskId == taskId &&
        other.displayId == displayId && other.mode == mode && other.comp == comp && other.visible == visible &&
        other.activityType == activityType
    override fun hashCode() = ((stackId * 31 + taskId) * 31 + displayId) * 31 + comp.hashCode()

    /** 1 dòng gọn cho log hiện trường: "task 80/stack 78 @d0 fullscreen". */
    fun brief(): String = "task $taskId/stack $stackId @d$displayId ${mode.ifBlank { "?" }}${if (visible) "" else " (ẩn)"}"
}

object StackParse {
    // ★ Regex biên dịch 1 LẦN ở object level (trước đây Regex(...) nằm trong thân hàm → biên dịch lại MỖI lần parse).
    // ★ W2-4: Android 12 đổi tên ActivityManager.StackInfo → ActivityTaskManager.RootTaskInfo, nên toString()
    //   in "RootTask id=" thay vì "Stack id=". Bản cũ chỉ nhận "Stack id=" → trên DiLink5 parse ra RỖNG, mọi
    //   kiểm-chứng-bằng-sự-thật đều thấy "không có gì ở đâu cả". Một regex nhận cả hai, không rẽ nhánh.
    private val RE_STACK = Regex("^(?:Stack|RootTask) id=(\\d+).*?displayId=(\\d+)")
    private val RE_BOUNDS = Regex("bounds=\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]")
    private val RE_MODE = Regex("mWindowingMode=([A-Za-z-]+)")
    private val RE_ATYPE = Regex("mActivityType=([A-Za-z-]+)")
    private val RE_TASK = Regex("^(?:taskId|Task id)=(\\d+):\\s*(\\S+/\\S+)")
    private val RE_VISIBLE = Regex("visible=(true|false)")

    /**
     * Parse toàn bộ output. Dòng "Stack id=" mở block; dòng "configuration=" cho mode; mỗi dòng "taskId=N: comp"
     * sinh 1 entry. Dòng không khớp → bỏ qua (firmware lạ không làm vỡ parse).
     */
    fun parse(out: String): List<StackEntry> {
        val res = ArrayList<StackEntry>()
        var stackId = -1
        var displayId = -1
        var mode = ""
        var atype = ""
        var bounds: IntArray? = null
        for (raw in out.lineSequence()) {
            val line = raw.trim()
            RE_STACK.find(line)?.let {
                stackId = it.groupValues[1].toInt()
                displayId = it.groupValues[2].toInt()
                mode = ""; atype = ""                      // thuộc về stack MỚI, chưa đọc
                bounds = RE_BOUNDS.find(line)?.let { b -> IntArray(4) { k -> b.groupValues[k + 1].toInt() } }
                return@let
            }
            if (stackId < 0) continue
            if (mode.isBlank()) RE_MODE.find(line)?.let { mode = it.groupValues[1] }
            if (atype.isBlank()) RE_ATYPE.find(line)?.let { atype = it.groupValues[1] }
            RE_TASK.find(line)?.let {
                res.add(
                    StackEntry(
                        stackId = stackId, displayId = displayId, mode = mode, activityType = atype,
                        taskId = it.groupValues[1].toInt(), comp = it.groupValues[2],
                        visible = RE_VISIBLE.find(line)?.groupValues?.get(1) != "false",
                        bounds = bounds,
                    )
                )
            }
        }
        return res
    }

    /** Mọi entry của [pkg] (khớp TRỌN tên gói — "com.foo" KHÔNG dính "com.foobar"). */
    fun of(entries: List<StackEntry>, pkg: String): List<StackEntry> = entries.filter { it.pkg == pkg }

    /**
     * Entry "thật" của [pkg] để thao tác: ƯU TIÊN đúng [preferDisplay], LOẠI pinned (PIP là stack riêng, resize
     * nó là bắn nhầm task). Không có trên display mong muốn → lấy entry non-pinned đầu tiên. Không có gì → null.
     */
    fun pick(entries: List<StackEntry>, pkg: String, preferDisplay: Int = -1): StackEntry? {
        val mine = of(entries, pkg).filter { !it.isPinned }
        return mine.firstOrNull { it.displayId == preferDisplay } ?: mine.firstOrNull()
    }

    /** Display mà [pkg] đang thực sự bám, ưu tiên xác nhận [preferDisplay]. -1 = không thấy. */
    fun displayOf(entries: List<StackEntry>, pkg: String, preferDisplay: Int = -1): Int =
        pick(entries, pkg, preferDisplay)?.displayId ?: -1

    /**
     * ★★ STACK ĐƯỢC PHÉP BÊ KHỎI VD — bản THAY THẾ cho `stacksOnVd` cũ (đã gây ĐƠ LAUNCHER trên xe anh em).
     *
     * Bản cũ là `filter { it.displayId >= 1 }`: không khớp ĐÚNG display, không lọc loại stack, không lọc app.
     * Hậu quả đo được trên AOSP 10:
     *  • Bê một surface OEM đang đậu trên cụm về display 0 → nó vào ON_TOP → `ActivityStack.getVisibility`
     *    cho MỌI stack bên dưới (KỂ CẢ HOME) thành INVISIBLE → launcher đứng hình, mà UI đó lại thuộc display
     *    `touch NONE` nên bấm không ăn. Đúng triệu chứng "Dudu đơ, không nhấn được gì".
     *  • Bê nhầm stack home/recents/pinned thì CHẾT HẲN: `ActivityStack.reparent` chạy `removeFromDisplay()`
     *    TRƯỚC, rồi `ActivityDisplay.addChild` → `addStackReferenceIfNeeded` NÉM IllegalArgumentException vì
     *    display 0 đã có stack cùng loại. Stack thành mồ côi (AM đã gỡ, WM còn giữ), `postReparent()` không chạy
     *    nên launcher KHÔNG BAO GIỜ được resume lại. `am stack list` cũng không thấy nó → không lệnh nào cứu được,
     *    chỉ còn khởi động lại đầu xe.
     * ⇒ Chỉ bê stack app THƯỜNG, ĐÚNG display đích, KHÔNG pinned. vd < 1 → trả rỗng (không bao giờ quét mù).
     * Bỏ qua home/pinned KHÔNG mất gì: VD có removeMode 0 (MOVE_CONTENT_TO_PRIMARY) nên khi teardown huỷ VD,
     * `ActivityDisplay.remove()` tự đưa chúng về display 0 đúng thứ tự.
     */
    fun evictableOnVd(entries: List<StackEntry>, vd: Int): List<StackEntry> {
        if (vd < 1) return emptyList()
        return entries.filter { it.displayId == vd && it.isStandard && !it.isPinned }.distinctBy { it.stackId }
    }

    /** Stack trên VD mà ta CỐ Ý KHÔNG đụng (home/recents/pinned) — để log cho hiện trường biết còn gì ở lại. */
    fun untouchableOnVd(entries: List<StackEntry>, vd: Int): List<StackEntry> {
        if (vd < 1) return emptyList()
        return entries.filter { it.displayId == vd && (!it.isStandard || it.isPinned) }.distinctBy { it.stackId }
    }

    /**
     * ★★ W2-3 (senior review): CHỌN NHÁNH CHIẾU BẰNG SỰ THẬT, không bằng cờ RAM.
     *
     * Bản cũ: `val curVd = if (casting) clusterDisplayId(...) else -1` — cờ `casting` vừa là kết luận vừa là
     * thứ quyết định CÓ ĐI NHÌN HAY KHÔNG. Cờ chỉ sống trong RAM, nên sau khi tiến trình bị kill (xe ngủ, LMK,
     * cài đè) nó về false và **không bằng chứng nào sửa được**: app đi đường LẠNH, chạy lại cả chuỗi ~8 giây
     * trên một cụm đang chiếu, ADAS nháy lại, app cũ bị trả về màn giữa mà không ép fullscreen.
     *
     * Điều kiện CÓ NGƯỜI Ở là bắt buộc, không được rút gọn thành `vd >= 1`: VD cụm TỒN TẠI SẴN kể cả khi
     * không chiếu gì, nên chỉ xét vd sẽ đẩy lần chiếu đầu tiên sau khi nổ máy vào nhánh ấm và bỏ qua opcode 30/35.
     */
    fun isWarm(liveVd: Int, entries: List<StackEntry>): Boolean =
        liveVd >= 1 && evictableOnVd(entries, liveVd).isNotEmpty()

    /** Có stack PIP nào của [pkg] không (để cảnh báo + dọn). */
    fun pinnedOf(entries: List<StackEntry>, pkg: String): List<StackEntry> =
        of(entries, pkg).filter { it.isPinned }

    /**
     * Cửa sổ NỔI còn kẹt trên MÀN GIỮA (display 0) — của BẤT KỲ app nào, không riêng app vừa chiếu.
     *
     * Vì sao cần (lỗi hiện trường 22/07): sau khi bật cờ `enable_freeform_support` và tắt-mở máy, một task từng
     * ở freeform trên cụm quay về display 0 sẽ Ở LẠI dạng cửa sổ nổi với đúng khung cụm — người lái thấy app
     * "bị scale" trên màn hình giữa. `reconcileOnStart` bản cũ chỉ soi ĐÚNG MỘT app (`lastCastApp`), mà
     * `lastCastApp` là cờ RAM nên sau khi app chết là mất; app của phiên trước đó thì không ai dọn.
     *
     * Phạm vi CỐ Ý HẸP (CLAUDE.md §4): chỉ display 0, chỉ stack `standard`, KHÔNG đụng `home`/`recents`/`pinned`.
     * `pinned` bị loại vì đó là PIP thật của người dùng (đang xem video thu nhỏ), không phải rác của ta.
     */
    fun floatingOnMain(entries: List<StackEntry>): List<StackEntry> =
        entries.filter { it.displayId == 0 && it.isStandard && !it.isPinned && it.isFreeform }
            .distinctBy { it.stackId }
}
