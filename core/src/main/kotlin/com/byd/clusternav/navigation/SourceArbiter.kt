package com.byd.clusternav.navigation

import com.byd.clusternav.navigation.NavSourceMode

import java.util.concurrent.ConcurrentHashMap

/**
 * Trọng tài chọn nguồn khi mở >1 app dẫn đường. Máy trạng thái THUẦN (không Android Context) —
 * caller truyền mode (NavSourceMode.AUTO/PREFER_*). UI đọc snapshot [activeSource] thay vì chọc vào listener.
 *
 * AUTO: app nào dẫn TRƯỚC giữ khoá; app khác bị bỏ qua tới khi nguồn giữ DỪNG (release) hoặc IM > STALE.
 *
 * PREFER_*: **CHỈ** app thuộc nhóm user chọn được lên — không có cửa thoát nào. Nhóm đó không dẫn ⇒ KHÔNG
 * app nào lên ⇒ cụm IM LẶNG. Đó là hành vi owner MUỐN, không phải suy giảm cần bù (quyết định 2026-08-23,
 * nguyên văn: *"chọn đích danh app thì luôn lấy app đích danh, nếu app đó ko dẫn thì ko hiện gì, không cần
 * auto switch làm gì cả"* — backlog **B3.48**). TUYỆT ĐỐI KHÔNG thêm fallback/timeout/degrade vào đây.
 */
object SourceArbiter {
    const val STALE_MS = 6000L
    private val GMAPS_PKGS = NavApps.GMAPS
    private val WAZE_PKGS = NavApps.WAZE
    private val VIETMAP_PKGS = NavApps.VIETMAP

    @Volatile var activeSource: String? = null; private set
    @Volatile private var activeSeen: Long = 0L
    private val lastDataByPkg = ConcurrentHashMap<String, Long>()

    /**
     * Có nên nuôi cụm bằng khung [pkg] không.
     *
     * Hai tầng gate:
     *   1. NGUỒN (app): AUTO giữ-khoá-app-dẫn-trước / PREFER_* chỉ cho thành viên nhóm đã chọn ([allows]).
     *   2. KÊNH (B3, R6): kênh [NavChannel.IMAGE] (screen-capture) bị CHẶN khi kênh [NavChannel.DATA] của
     *      CÙNG app còn tươi (≤ [STALE_MS]) — data-channel luôn thắng ảnh; ảnh chỉ lên khi data im.
     *
     * [channel] mặc định [NavChannel.DATA] để caller cũ (HLP/1, widget, a11y) giữ nguyên hành vi + tự ghi
     * mốc data. Nguồn ảnh gọi với [NavChannel.IMAGE].
     */
    fun shouldFeed(pkg: String, mode: Int, now: Long, channel: NavChannel = NavChannel.DATA): Boolean {
        if (channel == NavChannel.DATA) lastDataByPkg[pkg] = now
        if (!allows(pkg, mode, now)) return false
        // Tầng kênh: ảnh là FALLBACK — data tươi của cùng app thì bỏ frame ảnh (KHÔNG chiếm khoá nguồn).
        if (channel == NavChannel.IMAGE && isDataFresh(pkg, now)) return false
        activeSource = pkg; activeSeen = now
        return true
    }

    /**
     * B-II — Cổng NGUỒN THUẦN: HỎI mà KHÔNG chiếm khoá, KHÔNG đóng mốc gì. [shouldFeed] dùng lại chính hàm này.
     *
     * VÌ SAO tách: vòng enum cửa sổ (`NavAccessibilityService.resolveNavWindowRegardlessOfFocus`) phải hỏi cổng
     * cho NHIỀU ứng viên trong CÙNG một nhịp rồi mới bầu ra một nguồn. Hỏi bằng [shouldFeed] thì mỗi câu hỏi đã
     * đồng thời đặt `activeSource = ứng viên đó` — kể cả ứng viên rốt cuộc bị [NavSourceDwell] loại. Đó đúng là
     * cú nhảy nguồn mà B-II đang chữa.
     *
     * Khác biệt duy nhất giữa hàm này và [shouldFeed] là TÁC DỤNG PHỤ (chiếm `activeSource`, đóng mốc
     * `lastDataByPkg`) và tầng kênh — phần QUYẾT ĐỊNH nguồn thì dùng chung đúng một biểu thức, ở đây.
     */
    fun allows(pkg: String, mode: Int, now: Long): Boolean = when (mode) {
        // PREFER_*: cổng CHỈ là lựa chọn của user ⇒ uỷ cho [allowedByMode] (một biểu thức, một nơi — §4.1 DRY).
        NavSourceMode.PREFER_GMAPS, NavSourceMode.PREFER_WAZE, NavSourceMode.PREFER_VIETMAP ->
            allowedByMode(pkg, mode, now)
        // `else` (KHÔNG phải `NavSourceMode.AUTO ->`) là CỐ Ý: `Prefs.sourceMode` đọc int thô
        // (Prefs.kt:30) nên máy đã dùng lâu có thể còn giá trị của hai hằng SPEED_* vừa gỡ (xem KDoc
        // NavSourceMode). Giá trị lạ phải rơi vào khoá-giữ AUTO y như trước, không được thành "cho qua tất".
        else -> {
            val h = activeSource
            // B3.50: `!withinFreshWindow(...)` thay cho `now - activeSeen > STALE_MS` — chốt chặn LỆCH ĐỒNG
            // HỒ. Forward-clock cho kết quả GIỐNG HỆT (elapsed ≥ 0 ⇒ `!in 0..STALE_MS` ⟺ `> STALE_MS`); chỉ
            // khác khi activeSeen ở TƯƠNG LAI (đồng hồ nhảy lùi) ⇒ nay coi là STALE ⇒ nhả khoá, không giữ
            // cứng vào nguồn đã chết tới khi đồng hồ đuổi kịp.
            h == null || h == pkg || !withinFreshWindow(now - activeSeen)
        }
    }

    /**
     * Cổng **CHỈ THEO [NavSourceMode]** — lựa chọn TƯỜNG MINH của user, KHÔNG có khoá-giữ AUTO.
     *
     * Ở `PREFER_X` cổng này là **thành viên nhóm, không hơn**: `pkg in X`. Không đọc đồng hồ, không đọc lịch
     * sử, nên [now] không được dùng (giữ trong chữ ký vì đây là public API đang có call site —
     * `NavAccessibilityService.resolveNavWindowRegardlessOfFocus` truyền `nowWall`).
     *
     * ⚠ **KHÔNG ĐƯỢC thêm lại cửa thoát "nhóm ưu tiên im thì cho app khác lên"** (vế `|| !isGroupFresh(X)`,
     * gỡ 2026-08-23 — backlog **B3.48**). Cửa đó đọc sổ `lastSeenByPkg` mà writer duy nhất còn lại sau B3.44
     * là kênh ẢNH — kênh chỉ đập nhịp khi phân loại được mũi tên. Mỗi khoảng lặng > [STALE_MS] là GMaps lọt
     * cổng ở `PREFER_VIETMAP` ⇒ HAI owner cùng ghi `INSTRUMENT_GUIDE_INFO_SIMPLE_SET` (`NavigationHudOwner
     * .writeNavFrame` + `NavOutputOwner.pushNavigation`) ⇒ cự ly app này cạnh mũi tên app kia trên cụm-centre
     * và HUD. Owner chốt: app đích danh không dẫn thì **không hiện gì**, im lặng là ĐÚNG.
     *
     * VÌ SAO TÁCH KHỎI [allows] (lỗi thật, sửa 08-22 vòng 3): [NavSourceDwell.onTick] có tham số
     * `holderAllowed` mà KDoc của nó định nghĩa là "holder còn qua cổng [NavSourceMode] không (user đổi
     * PREFER_* ⇒ nhường NGAY)", và R3 dùng nó để **bỏ qua TOÀN BỘ dwell + guard cam-kết-rẽ**. Call site
     * `NavAccessibilityService.resolveNavWindowRegardlessOfFocus` lại truyền [allows] — mà ở AUTO [allows]
     * KHÔNG phải cổng mode, nó là khoá-giữ theo [activeSource].
     *
     * Ca hỏng tất định từ source: ở AUTO, `allows(candidate)=true` + `allows(holder)=false` chỉ xảy ra khi
     * `activeSource == candidate` còn tươi (hai vế kia của biểu thức AUTO đều cho holder true luôn) — tức khi
     * MỘT app khác đang thật sự nuôi khung. Lúc đó R3 bắn `adopt(candidate, Reason.MODE)` **tức thì**, vượt
     * mặt R5 (`holderTurnMeters in 0..commit`) ⇒ đổi nguồn ĐÚNG GIÂY tài xế vào cua — đúng "cú nhảy nguy hiểm
     * nhất" mà R5 sinh ra để chặn, và nhãn `Reason.MODE` trong log còn nói dối là "user đổi chế độ".
     *
     * Ở AUTO hàm này trả `true` ⇒ R3 KHÔNG bao giờ bắn ở AUTO ⇒ mọi lần đổi nguồn AUTO phải đi qua
     * R4/R5/R6. Đây là thu hẹp (chậm hơn, an toàn hơn), không nới: nguồn thật sự đang nuôi khung vẫn tiếp
     * quản sau dwell ([NavSourceDwell.DWELL_TICKS] nhịp), và trong lúc chờ, cổng `allows` ở cuối vòng enum
     * vẫn chặn không cho publish nhầm — im lặng, không ghi bừa.
     */
    @Suppress("UNUSED_PARAMETER")
    fun allowedByMode(pkg: String, mode: Int, now: Long): Boolean = when (mode) {
        NavSourceMode.PREFER_GMAPS -> pkg in GMAPS_PKGS
        NavSourceMode.PREFER_WAZE -> pkg in WAZE_PKGS
        NavSourceMode.PREFER_VIETMAP -> pkg in VIETMAP_PKGS
        // AUTO (và mọi giá trị lạ) KHÔNG diễn đạt ưu tiên nào ⇒ không có lý do để nhường ngay.
        else -> true
    }

    /** Kênh DATA của [pkg] còn tươi không (≤ [STALE_MS]) — UI/nguồn ảnh hỏi để biết data có đang thắng. */
    fun isDataFresh(pkg: String, now: Long): Boolean {
        val t = lastDataByPkg[pkg] ?: return false
        return t > 0 && withinFreshWindow(now - t)
    }

    /**
     * Quãng trôi [elapsed] (= now − mốc) có nằm trong cửa sổ tươi `[0, STALE_MS]` không.
     *
     * ⚠ Chốt chặn LỆCH ĐỒNG HỒ (B3.50): mọi mốc ở đây là WALL clock (`System.currentTimeMillis` từ caller),
     * mà NTP/GPS có thể kéo đồng hồ NHẢY LÙI ⇒ mốc đã lưu rơi vào TƯƠNG LAI so với [now] ⇒ [elapsed] ÂM.
     * Phép so cũ `elapsed <= STALE_MS` coi số âm là "tươi" ⇒ (a) kênh IMAGE của gói đó bị chặn tới khi đồng
     * hồ đuổi kịp (đúng kênh mà B3.44 biến thành đường sống DUY NHẤT của VietMap), (b) khoá-giữ AUTO không
     * bao giờ hết hạn = khoá cứng vào một nguồn đã chết. Cận DƯỚI 0 khép cửa đó: mốc tương lai ⇒ STALE ⇒
     * an toàn (nhả khoá / cho ảnh lên), không giữ nhầm. Cận trên `STALE_MS` (bao gồm) giữ nguyên ngữ nghĩa cũ.
     */
    private fun withinFreshWindow(elapsed: Long): Boolean = elapsed in 0..STALE_MS

    /** Gọi khi noti dẫn đường của [pkg] bị gỡ. true nếu [pkg] đang giữ khoá (caller nên dừng cụm). */
    fun release(pkg: String): Boolean {
        if (pkg == activeSource) { activeSource = null; activeSeen = 0L; return true }
        return false
    }

    /** Nhả khoá hoàn toàn (vd nav stale -> idle). */
    fun clear() { activeSource = null; activeSeen = 0L; lastDataByPkg.clear() }

    /** Nguồn đang giữ còn tươi không (UI hiện trạng thái). */
    fun isFresh(now: Long): Boolean {
        activeSource ?: return false
        return withinFreshWindow(now - activeSeen)
    }
}
