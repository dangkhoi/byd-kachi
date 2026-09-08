package com.byd.clusternav.navigation.screencapture

import com.byd.clusternav.navigation.Maneuver
import com.byd.clusternav.navigation.LaneInfo
import com.byd.clusternav.navigation.NavSignalSample
import java.util.concurrent.atomic.AtomicReference

/**
 * Một nhịp đọc MŨI TÊN của một app: hướng + mã AMAP + chủ + mốc, BẤT BIẾN trong một object.
 * Xem [NavSignalSample] để biết vì sao không được tách thành nhiều `@Volatile` rời.
 */
data class ArrowSample(
    override val pkg: String,
    val maneuver: Maneuver?,
    val amap: Int?,
    override val atMs: Long,
) : NavSignalSample

/** Một nhịp đọc DẢI LÀN của một app (xem [ArrowSample]). */
data class LaneSample(
    override val pkg: String,
    val info: LaneInfo,
    override val atMs: Long,
) : NavSignalSample

/** Một nhịp đọc BADGE CAMERA của một app (xem [ArrowSample]). */
data class CameraSample(
    override val pkg: String,
    val match: CameraMatch,
    override val atMs: Long,
) : NavSignalSample

/**
 * Kết quả nguồn ẢNH đã QUA trọng tài (`SourceArbiter.shouldFeed(..., NavChannel.IMAGE)` = true ⇒ data không
 * đè). Đây là SEAM mà đầu ra (làn cụm / HUD / badge camera — T7, on-car) đọc; ở lát B3 `:app` này ta CHỈ
 * capture→classify→trọng-tài→publish vào đây (KHÔNG tự ghi cụm — đó là T7, cần verify từng case trên xe).
 *
 * Lock-free (`AtomicReference` — cùng ngữ nghĩa bộ nhớ với `@Volatile`, thêm compare-and-set cho [dropDisallowed]):
 * một producer = luồng capture đơn, nhiều consumer đọc. Không Android → test được.
 *
 * ── B-I (§R-BI, 08-22): MỖI KÊNH LÀ MỘT SAMPLE BẤT BIẾN ───────────────────────────────────────────────────
 * Trước đây mỗi kênh là 3–4 ô rời (pkg / giá trị / mốc). Consumer ([com.byd.clusternav.NavOutputOwner])
 * đọc rời từng cái, nên một `publish*` xen giữa hai lần đọc là ghép được **pkg của app mới với giá trị của app
 * cũ** — khung lai NGAY TRONG một kênh, trước cả khi nói tới chuyện lệch kênh. Nay một lần đọc [arrow]/[lane]/
 * [camera] ra một bộ nhất quán; các getter cũ (`arrowPkg`, `arrowManeuver`, …) giữ nguyên để `NavRepository`
 * và test hiện có biên dịch không đổi.
 *
 * ⚠ VERIFY-ON-CAR: giá trị thật (mũi tên nhận đúng khi capture Waze; camera match khi có template OQ4) tuỳ xe.
 * Camera hiện luôn NONE ở production (BUILTIN template rỗng) tới khi thu template trên xe (OQ4).
 */
object ScreenCaptureSignal {

    // ── ARROW (Waze/GMaps mũi tên) ────────────────────────────────────────────
    private val arrowRef = AtomicReference<ArrowSample?>(null)

    /**
     * Nhịp đọc mũi tên gần nhất. [AtomicReference.get] có ĐÚNG ngữ nghĩa bộ nhớ của `@Volatile` (kiểu cũ của
     * field này), nên đường đọc của [com.byd.clusternav.NavOutputOwner] không đổi một chút nào; đổi kiểu chỉ
     * để [dropDisallowed] gỡ kênh bằng compare-and-set — xem KDoc hàm đó.
     */
    val arrow: ArrowSample? get() = arrowRef.get()

    /** Publish kết quả mũi tên đã qua trọng tài. [maneuver] = có hướng (vòng xuyến); [amap] = mã làn cụm. */
    fun publishArrow(pkg: String, maneuver: Maneuver?, amap: Int?, now: Long) {
        arrowRef.set(ArrowSample(pkg, maneuver, amap, now))
    }

    val arrowPkg: String? get() = arrow?.pkg
    val arrowManeuver: Maneuver? get() = arrow?.maneuver
    val arrowAmap: Int? get() = arrow?.amap
    val arrowAtMs: Long get() = arrow?.atMs ?: 0L

    /**
     * Mũi tên còn tươi không.
     *
     * ⚠ 2026-08-22: production KHÔNG còn gọi hàm này — `NavOutputOwner` chuyển sang đọc MỘT lần bằng
     * [NavSignalSample.stateAt] để pkg và giá trị không thể lệch nhau (bất biến §R-BI). Giữ lại làm API
     * CHẨN ĐOÁN/test: khi soi lỗi on-car vẫn cần hỏi nhanh "kênh này còn tươi không" mà không phải dựng
     * cả một [NavChannelState]. Nếu sau này không ai dùng nữa thì xoá — đừng để nó âm thầm thành đường
     * vòng qua bất biến.
     */
    fun arrowFresh(now: Long, staleMs: Long = STALE_MS): Boolean = fresh(arrow, now, staleMs)

    // ── CAMERA (VietMap phạt nguội) ─────────────────────────────────────────────
    private val cameraRef = AtomicReference<CameraSample?>(null)

    /** Xem [arrow] về ngữ nghĩa bộ nhớ. */
    val camera: CameraSample? get() = cameraRef.get()

    fun publishCamera(pkg: String, match: CameraMatch, now: Long) {
        cameraRef.set(CameraSample(pkg, match, now))
    }

    val cameraPkg: String? get() = camera?.pkg
    val cameraMatch: CameraMatch? get() = camera?.match
    val cameraAtMs: Long get() = camera?.atMs ?: 0L


    // ── LANE (dải lane-guidance Waze/VietMap) ───────────────────────────────────
    private val laneRef = AtomicReference<LaneSample?>(null)

    /** Xem [arrow] về ngữ nghĩa bộ nhớ. */
    val lane: LaneSample? get() = laneRef.get()

    /** Publish dải làn đã đọc (đã qua trọng tài). [info] TRÁI→PHẢI; rỗng ⇒ không có lane-guidance. */
    fun publishLane(pkg: String, info: LaneInfo, now: Long) {
        laneRef.set(LaneSample(pkg, info, now))
    }

    val lanePkg: String? get() = lane?.pkg
    val laneInfo: LaneInfo? get() = lane?.info
    val laneAtMs: Long get() = lane?.atMs ?: 0L

    fun laneFresh(now: Long, staleMs: Long = STALE_MS): Boolean = fresh(lane, now, staleMs)

    /**
     * B3.49 — XOÁ NGAY các kênh mà cổng [com.byd.clusternav.navigation.NavSourceMode] MỚI không cho phép
     * (người dùng vừa đổi menu nguồn trên màn chính).
     *
     * VÌ SAO PHẢI CÓ (cơ chế đọc thẳng từ source, 2026-08-23): [com.byd.clusternav.NavOutputOwner.tick]
     * quyết định bắn **chỉ** theo độ tươi của ba sample ở đây — nó không đọc `Prefs`, không hỏi
     * `SourceArbiter`. Cổng nguồn vì thế chỉ chặn được khung **MỚI** (`ScreenCaptureNavSource` thôi publish);
     * mẫu **CŨ** của app vừa bị loại vẫn tươi tới [STALE_MS] = 6 s ⇒ cụm còn vẽ mũi tên của app cũ sau khi
     * tài xế đã chọn app khác. Xoá tại đúng lúc đổi mode là đường ngắn nhất, và là đường DUY NHẤT không phải
     * nới thêm cổng nào.
     *
     * ⚠ **6 GIÂY LÀ CẬN DƯỚI, KHÔNG PHẢI TRẦN** (đính chính 08-23 vòng 2, [ĐO] probe `PROBE-A`/`PROBE-C`):
     * con số 6 s chỉ đúng khi app bị loại là app DUY NHẤT có kênh tươi. Nếu một app khác còn giữ một kênh
     * tươi thì khung không bao giờ được nhả (`clear = !anyFresh`) và register mũi tên giữ nguyên nội dung cũ
     * **vô hạn**. Vì thế hàm này còn đặt yêu cầu nhả khung — xem [consumeFrameRelease].
     *
     * ⚠ **CA HIỆN TRƯỜNG ĐÚNG LÀ Waze/VietMap, KHÔNG PHẢI GMaps** (đính chính 08-23 vòng 2, [ĐO] `PROBE-B`):
     * GMaps đang dẫn thì `shouldFeed(…, IMAGE)` của chính nó bị mốc DATA chặn (`SourceArbiter.kt` — noti
     * ~1 Hz giữ `isDataFresh` = true), nên GMaps hầu như không bao giờ là chủ kênh ẢNH lúc đang dẫn. Hai app
     * chỉ-có-kênh-ảnh (họ Waze, VietMap) mới là ca thật của hàm này.
     *
     * **CHỈ THU HẸP, KHÔNG NỚI**: [allow] trả false ⇒ bỏ kênh; trả true ⇒ **không đụng** (compare-and-set, xem
     * [drop]). Cố ý không dùng [clear] (xoá sạch ba kênh): đổi sang AUTO, hay chọn đúng app đang dẫn, sẽ làm
     * chính app vừa được chọn chớp tắt một nhịp (owner nhả frame → capture publish lại ~500 ms sau) — nháy
     * khung là "hiện SAI" chứ không phải "im lặng".
     *
     * ⚠ **KHÔNG được đổi thành `SourceArbiter.clear()`** ở call site (đề xuất đầu tiên trong backlog B3.49):
     * hàm đó đặt `activeSource = null`, mà `NavNotificationListener` dựa đúng vào `SourceArbiter.release(pkg)`
     * = true để phát lệnh dừng cụm khi cổng loại gói đang giữ (B3.48). `activeSource` bị xoá trước ⇒ `release`
     * trả false ⇒ không ai phát STOP ⇒ nhịp tim ghim khung cuối tới 180 s. Và `activeSource = null` còn mở
     * nhánh AUTO (`h == null` ⇒ cho MỌI gói qua) — đúng thứ "nới" mà việc này cấm.
     */
    fun dropDisallowed(allow: (String) -> Boolean): Set<String> {
        val dropped = LinkedHashSet<String>()
        drop(arrowRef, allow, dropped)
        drop(laneRef, allow, dropped)
        drop(cameraRef, allow, dropped)
        if (dropped.isNotEmpty()) {
            // Kênh vừa gỡ CÓ THỂ đang là khung trên cụm. Gỡ sample chỉ chặn được nhịp bắn SAU; nội dung đã ghi
            // vào register HAL thì nằm lại (xem [consumeFrameRelease]). Ghi yêu cầu nhả khung cho owner.
            pendingRelease.updateAndGet { it + dropped }
        }
        return dropped
    }

    /**
     * Gỡ MỘT kênh nếu chủ của nó không còn được [allow] — bằng compare-and-set, KHÔNG phải đọc-rồi-ghi.
     *
     * VÌ SAO CAS: giữa lúc đọc sample ra hỏi [allow] và lúc ghi `null`, luồng capture có thể publish một nhịp
     * MỚI (có thể của app KHÁC, app vẫn được phép). Ghi `null` thẳng là nuốt luôn nhịp mới đó ⇒ kênh tối thêm
     * ~500 ms cho tới lần capture kế — đúng cái "nháy khung" mà KDoc trên tuyên bố tránh được. [AtomicReference
     * .compareAndSet] chỉ xoá khi ô còn nguyên đúng sample đã bị từ chối; ô đã đổi ⇒ bỏ qua, nhịp mới sống.
     * (Nhịp mới nếu cũng không được phép thì cổng nguồn đã chặn từ `ScreenCaptureNavSource` — không lọt.)
     */
    private fun <T : NavSignalSample> drop(
        ref: AtomicReference<T?>,
        allow: (String) -> Boolean,
        dropped: MutableSet<String>,
    ) {
        val s = ref.get() ?: return
        if (allow(s.pkg)) return
        if (ref.compareAndSet(s, null)) dropped += s.pkg
    }

    /**
     * B3.49 vòng 2 — CÁC GÓI vừa bị [dropDisallowed] gỡ kênh, đọc-và-xoá (one-shot).
     *
     * VÌ SAO CẦN (lỗi THẬT, đo 2026-08-23 — probe `PROBE-A`): gỡ sample chỉ ngăn nhịp bắn SAU. Nội dung đã ghi
     * vào register HAL thì **nằm lại tới khi có ai đó ghi đè hoặc nhả khung**, mà `NavOutputOwner` chỉ nhả khung
     * khi **TẤT CẢ** ba kênh hết tươi (`NavOutputDecision.decide` — `clear = !anyFresh`). Nên nếu một app KHÁC
     * (vẫn được phép) còn giữ một kênh tươi, khung sống tiếp và `INSTRUMENT_GUIDE_INFO_SIMPLE_SET` **giữ nguyên
     * mũi tên của app vừa bị loại** — không phải 6 giây mà là **vô hạn** (VietMap publish lại làn mỗi ~500 ms).
     * [ĐO]: arrow=Waze + lane=VietMap ⇒ đổi sang `PREFER_VIETMAP` ⇒ `sink.clear()` **không hề được gọi**, làn
     * VietMap vẫn bắn đều trong khi mũi tên Waze còn trên cụm.
     *
     * Owner đọc tập này mỗi nhịp và **chỉ nhả khung khi danh tính khung ĐANG hiện nằm trong tập** — không nhả
     * khung của app không liên quan (nhả oan = nháy khung của chính app vừa được chọn, xem KDoc [dropDisallowed]).
     */
    fun consumeFrameRelease(): Set<String> = pendingRelease.getAndSet(emptySet())

    /** Yêu cầu nhả khung đang chờ owner tiêu thụ. Rỗng = không có gì phải nhả. */
    private val pendingRelease = AtomicReference<Set<String>>(emptySet())

    /** Xoá khi nav idle / nguồn dừng. */
    fun clear() {
        arrowRef.set(null); cameraRef.set(null); laneRef.set(null)
        // Không còn kênh nào ⇒ owner sẽ tự nhả khung ở nhịp kế (`clear = !anyFresh`); giữ lại yêu cầu cũ chỉ
        // tạo một lần nhả thừa cho khung của app khác dựng SAU đó.
        pendingRelease.set(emptySet())
    }

    /** Cùng công thức với `NavSignalSample.stateAt` — mốc 0 = chưa publish ⇒ không tươi. */
    private fun fresh(s: NavSignalSample?, now: Long, staleMs: Long): Boolean =
        s != null && s.atMs > 0L && now - s.atMs <= staleMs

    /** Cùng ngưỡng tươi với `SourceArbiter.STALE_MS`. */
    const val STALE_MS = 6000L
}
