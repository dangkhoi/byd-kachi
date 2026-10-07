package com.byd.clusternav.launcher.camera

import android.content.Context
import android.util.Log
import com.byd.clusternav.Prefs
import com.byd.clusternav.cameraSignalEnabled
import com.byd.clusternav.cameraGlUniforms
import com.byd.clusternav.launcher.camera.CameraSignalPolicy.Turn
import com.byd.clusternav.launcher.testbridge.TestBridgeStore

/**
 * ═══ CAMERA THEO XI-NHAN + THEO YÊU CẦU · điều phối (`:app`) ═══════════════════════════════════════════════════════
 *
 * Mỗi nhịp nhận trạng thái xi-nhan (từ [HalSignalClient] — KHÔNG phải `CarStatus.lights`, xem KDoc [tick]) → nếu
 * bật tính năng (pref, mặc định TẮT) và bên xi-nhan ĐỔI thì: mở camera view tương ứng ([PanoramaHal]) + hiện overlay
 * bên đó ([CameraOverlayView]); hết xi-nhan ⇒ đóng. Chỉ ĐỔI khi khác nhịp trước (không dựng lại overlay mỗi nhịp —
 * cùng lẽ RainDefrostOwner).
 *
 * 2.93 · CAMERA-ON-DEMAND (spec `docs/specs/kachi-293-cam.html`): nguồn thứ hai — [demand] (phím vật lý · nút thanh nút ·
 * giọng nói, qua `CameraDemandDispatch`). MỘT cửa sổ + MỘT luồng `AVMCamera` cho cả hai nguồn ⇒ camera hiện là
 * [CameraDemand.shown] (sự kiện mới nhất thắng; hết xi-nhan thì camera theo yêu cầu quay lại — không hẹn giờ tắt).
 * Mọi phiên (xi-nhan hay theo yêu cầu) dựng từ CÙNG một lượt đọc cấu hình của camera ấy ([CameraSessionSpec]).
 *
 * ⚠ Off-car: PanoramaHal no-op (device null) nhưng overlay vẫn dựng (TextureView đen) — đo được wiring. Tín hiệu
 *   video thật = on-car (runbook camera-panorama).
 */
class CameraSignalController(private val appCtx: Context) {

    private val hal by lazy { PanoramaHal(appCtx) }
    private val avm by lazy { AvmCamera() }
    private val overlay by lazy { CameraOverlayView(appCtx) }

    /** Bên xi-nhan đang GIỮ (đầu ra của [hold]) — `NONE` khi không xi-nhan hoặc tính năng TẮT. Chỉ main ghi. */
    private var current: Turn = Turn.NONE

    /** 2.93 — camera theo yêu cầu (RAM, chỉ main) — KDoc [CameraDemandState]. */
    private val od = CameraDemandState()

    /** 2.93 — camera của phiên ĐANG treo (`null` = không có phiên). Sự thật của cửa sổ, không phải của nguồn. */
    private var showing: CameraWhich? = null

    /**
     * `camera_synth` đang bật hay không — bơm ảnh fisheye TỔNG HỢP thay HAL ([CameraSynthFeeder]).
     *
     * **KHÔNG** là một pref: nó chỉ sống trong RAM của tiến trình và chỉ đặt được qua cầu kiểm thử (tức chỉ khi chế
     * độ kiểm thử đang mở, ~58 phút). Lý do: một cờ lưu bền có thể sống sót qua một lần nổ máy và khi ấy owner sẽ
     * thấy *một ảnh vẽ sẵn* ở chỗ đáng lẽ là camera gương — trên xe đang lăn bánh. Chết theo tiến trình là hướng
     * sai AN TOÀN, cùng lẽ `RainDefrostState` (KDoc ở đó).
     */
    @Volatile
    private var synth = false

    // Nguồn xi-nhan THẬT = HAL helper (app_process uid shell) publish socket 19322 → [HalSignalClient] subscribe.
    // [ĐO xe 2026-09-25] register listener dưới uid APP bị SecurityException BYDAUTO_LIGHT_GET (perm signature);
    // chạy dưới uid shell (mô hình kinex) thì OK. getLightStatus poll trả 0 trên trim này ⇒ không dùng poll.
    // BG-15 + [SOÁT Pass 1 · 2026-09-25]: MỘT controller dùng chung ⇒ `tick()` chạy CẢ trên main (HOME) lẫn luồng
    // vòng `AutomationService`. Cờ phải đổi bằng **một phép nguyên tử**: `if (started) … started = true` cho hai
    // luồng đi qua cùng lúc ⇒ hai lượt `start` xếp hàng (hoặc một `stop` lọt giữa) ⇒ cờ nói khác sự thật của socket.
    private val started = java.util.concurrent.atomic.AtomicBoolean(false)
    private val signal by lazy { HalSignalClient() }
    private val bg = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "kachi-camera-hal").apply { isDaemon = true }
    }

    /**
     * Đồng bộ với công tắc — gọi từ `AutomationService` (mỗi nhịp 60 s + ngay khi `sync`) và từ HOME khi state đổi.
     * Bật ⇒ đảm bảo helper + socket đang nghe; tắt ⇒ dừng luồng socket (BG-15) và đóng overlay (trên main).
     *
     * Trạng thái xi-nhan đến từ socket ([HalSignalClient] onTurn → tick(l,r)). Ở đây KHÔNG đọc evtLeft/evtRight
     * sticky của `CarStatus`: [ĐO xe 2026-09-25] cờ evt kẹt `true` nếu tài xế tắt đúng pha ON ⇒ đọc lại mỗi nhịp là
     * tự nói "đang bật" mãi. Nguồn duy nhất được tin là **dòng sự kiện** của socket — nó báo cả ON lẫn OFF
     * ([ĐO xe 2026-09-26]), và khi đứt dây thì `HalSignalClient` tự báo `(false,false)` nên không có đường kẹt ON.
     * Mốc HOLD (chỉ dùng cho nguồn nháy) được HẸN đúng lúc bằng [expiry] (BG-13), không cần vòng 250 ms nào nữa.
     */
    fun tick() {
        if (Prefs.cameraSignalEnabled(appCtx)) ensureSignal() else release()
        tick(null, null)
    }

    /** Khởi HAL helper (uid shell) + socket client MỘT lần, trên thread NỀN (ensure() chặn: push jar + shell). */
    private fun ensureSignal() {
        if (!started.compareAndSet(false, true)) return
        bg.execute {
            logCamSort()
            runCatching { HalHelperLauncher.ensure(appCtx) }.onFailure { Log.w(PanoramaHal.TAG, "HAL helper ensure lỗi: ${it.message}") }
            runCatching { signal.start { l, r -> tick(l, r) } }.onFailure { Log.w(PanoramaHal.TAG, "HAL signal start lỗi: ${it.message}") }
        }
    }

    /**
     * Ghi **MỘT dòng** `vehicle.config.cam_sort` lúc bật tính năng — phép thử năng lực pano rẻ nhất (RE
     * `electro-camera-RE-2026-09-26.md` §5 K2). [ĐO firmware] launcher gốc dò camera bằng đúng khoá này
     * (`VehicleUtils.java:176,187-192`); trên xe owner nó đã trả `rear:0;pano_h:1;` [ĐO carlog 2026-09-14].
     *
     * ⚠ **KHÔNG gate gì** theo dòng này (CLAUDE.md §3): chưa ai đo trên một đời xe thứ hai, nên biến nó thành điều kiện
     * mở camera là tự tắt tính năng trên mọi trim mà ROM viết khác dấu phân cách. Nó ở đây để buổi xe tự đọc được
     * `logcat` mà không phải gõ thêm một lệnh nào (CLAUDE.md §11: app tự chụp).
     */
    private fun logCamSort() {
        val raw = AvmCamera.systemProp(AvmCamera.PROP_CAM_SORT)
        Log.i(
            PanoramaHal.TAG,
            "${AvmCamera.PROP_CAM_SORT}='$raw' ids=${CameraSignalPolicy.camSortIds(raw)}" +
                " panoId=${CameraSignalPolicy.camSortId(raw, CameraSignalPolicy.CAM_TAG_PANO)}",
        )
    }

    /**
     * BG-15 (2026-09-25): dừng luồng socket `KachiHalSignal` khi không còn ai cần (công tắc TẮT / automation hết
     * việc). Trước đây không có đường này ⇒ mỗi controller một luồng sống tới khi xe tắt máy. Idempotent; đi qua
     * [bg] để KHÔNG vượt mặt một `start` đang xếp hàng (executor một luồng giữ thứ tự). Bật lại ⇒ [ensureSignal]
     * dựng lại (helper `ensure` idempotent: dò cổng trước khi khởi).
     */
    fun release() {
        if (!started.compareAndSet(true, false)) return
        bg.execute { runCatching { signal.stop() }.onFailure { Log.w(PanoramaHal.TAG, "HAL signal stop lỗi: ${it.message}") } }
    }

    /**
     * Một nhịp với trạng thái xi-nhan cho sẵn (từ socket, test bridge, hoặc null = không có tin mới).
     *
     * ⚠ Luật giữ nằm ở [CameraHold] (thuần, test với đồng hồ giả) và chịu được CẢ HAI kiểu nguồn — xem KDoc ở đó:
     * bên đang ở trạng thái ON thì giữ tới khi có sự kiện OFF ([ĐO xe 2026-09-26, helper báo trạng thái]); sau một
     * OFF còn giữ thêm [HOLD_MS] để không nháy theo bóng ([ĐO xe 2026-09-24, nguồn nháy ~1.5Hz]). Mốc hết hạn được
     * hẹn bằng [expiry] sau MỖI nhịp (BG-13) — trước 2026-09-25 cần vòng automation 250 ms gọi `tick(false,false)`.
     */
    fun tick(left: Boolean?, right: Boolean?) {
        // overlay/hal/avm là op WindowManager + View ⇒ PHẢI main thread. tick(l,r) có thể được gọi từ LUỒNG ĐỌC
        // socket ([HalSignalClient]) ⇒ marshal về main. tick() no-arg (FGS) cũng đi qua đây, main-post là no-op-an-toàn.
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            main.post { tickMain(left, right) }; return
        }
        tickMain(left, right)
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private fun tickMain(left: Boolean?, right: Boolean?) {
        // Tính năng xi-nhan TẮT ⇒ hạ phần xi-nhan; camera theo yêu cầu (nếu đang bật) KHÔNG bị tắt theo — hai nguồn độc lập.
        if (!Prefs.cameraSignalEnabled(appCtx)) { if (current != Turn.NONE) endBlinker(); return }
        val now = clockMs()
        val turn = hold.observe(left, right, now)
        // BG-13: hẹn ĐÚNG mốc HOLD hết hạn gần nhất (đặt lại mỗi nhịp — sự kiện ON mới đẩy mốc lùi). Không có bên
        // nào đang giữ ⇒ không hẹn gì. Handler main ⇒ [expiry] cũng chạy trên main.
        main.removeCallbacks(expiry)
        hold.expiresInMs(now)?.let { main.postDelayed(expiry, it) }
        if (turn == current) return   // không đổi ⇒ giữ nguyên (không dựng lại, không nháy theo đèn)
        if (turn == Turn.NONE) { endBlinker(); return }
        // Xi-nhan BẬT/đổi bên = sự kiện MỚI NHẤT ⇒ camera xi-nhan hiện như cũ, kể cả khi đang mở camera theo yêu cầu.
        current = turn
        od.newer = false
        show(want())
    }

    /** Camera nên hiện lúc này — luật ở `:core` [CameraDemand.shown]. */
    private fun want(): CameraWhich? = od.shown(CameraWhich.ofTurn(current))

    /**
     * ═══ Đưa cửa sổ về camera [next] — MỘT cửa cho xi-nhan lẫn theo yêu cầu ═════════════════════════════════════
     *
     * ═══ [P0 · xe 27/09] ĐỔI CAMERA phải DỠ phiên cũ TRƯỚC ══════════════════════════════════════════════════════
     * [ĐO] `usage-1790477853304.log`: 09:58:15 mở LEFT (TV) → 09:58:26,017 rẽ RIGHT → `overlay.show` dựng lớp video MỚI
     * (huỷ `SurfaceTexture` của LEFT) và `avm.open` GHI ĐÈ tham chiếu AVMCamera cũ — **không** một lời
     * `stopPreview`/`close` nào cho phiên LEFT. 87 ms sau bắt đầu `E/BufferQueueProducer … BufferQueue has been
     * abandoned` ~16 dòng/giây, **không bao giờ dứt** (55 004 dòng tới 10:58). ⇒ thuốc là gọi đúng đường dỡ đã có —
     * nay áp cho MỌI lượt đổi camera (xi-nhan đổi bên · bấm camera khác · xi-nhan chen camera theo yêu cầu).
     *
     * KHÔNG chạm máy trạng thái (`hold`/hẹn giờ): [show] chỉ là *"dỡ phần cứng + cửa sổ"* rồi dựng — xoá nền HOLD giữa
     * chuyến sẽ làm lượt sau đọc pha TẮT của đèn nháy thành NONE ⇒ overlay chớp tắt.
     */
    private fun show(next: CameraWhich?) {
        val prev = showing
        if (next == prev) return
        if (prev != null && next != null) closeSession(keepPano = true)
        showing = next
        if (next == null) {
            shown = null
            closeSession()
        } else {
            openSession(next)
        }
        od.notifyChanged()   // soát senior 2.93 [P3]: xi-nhan chen/hết cũng đổi thứ ĐANG HIỆN ⇒ nút Xem thử vẽ lại đúng
    }

    /**
     * 2.93 · CAMERA-ON-DEMAND — một lệnh bật/tắt camera theo yêu cầu (spec R1). Gọi được từ luồng nào cũng được (phím
     * vật lý ở luồng dịch vụ Hỗ trợ, cầu `:wake` ở luồng broadcast) — marshal về main như [tick].
     *
     * Không hẹn giờ tắt (owner *"không nên timeout"*): camera ở lại tới lệnh tắt kế tiếp hoặc tiến trình chết.
     */
    fun demand(op: CameraDemand.Op) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            main.post { demand(op) }; return
        }
        val before = od.apply(op, visible = showing)   // nút = toggle theo thứ ĐANG HIỆN (KDoc `CameraDemand.next`)
        Log.i(PanoramaHal.TAG, "theo-yêu-cầu ${CameraDemand.encode(op)}: $before → ${od.current} (xi-nhan=$current đang-hiện=$showing)")
        show(want())
        od.notifyChanged()
    }

    /** Camera theo yêu cầu đang bật (`null` = không) — cho nút *Xem thử* ở Cài đặt. Chỉ ĐỌC, trên main. */
    fun demanded(): CameraWhich? = od.current

    /** Camera của phiên ĐANG treo (xi-nhan hay theo yêu cầu) — cho lệnh `state` của cầu kiểm thử. Chỉ ĐỌC. */
    fun showingCamera(): CameraWhich? = showing

    /** Nghe đổi camera theo yêu cầu HOẶC camera đang hiện ([show]); trả hàm GỠ. Gọi trên main — KDoc [CameraDemandState.listen]. */
    fun onDemandChanged(l: (CameraWhich?) -> Unit): () -> Unit = od.listen(l)

    /**
     * ═══ Dựng MỘT phiên camera cho [which] ═════════════════════════════════════════════════════════════════════════
     *
     * Tách khỏi [tickMain] ở 2.76; 2.93: mọi pref của phiên đọc MỘT lượt ở [CameraSessionSpec] (cũng là thứ bộ chỉnh
     * *Từng camera* đọc) — ở đây chỉ còn phần *"đưa xuống phần cứng + cửa sổ"*. Bộ uniform/crop là **bất biến theo
     * phiên** (đổi giữa hai khung không có tác dụng).
     *
     * ⚠ 2.77 gỡ tham số `forcedPano` cùng cả máy lùi CHANNEL → PANO: không còn nguồn một-kênh nào để lùi khỏi
     * (`CameraSettingsIa` — owner chốt trên xe 27/09 sau phép ĐO *"một kênh KHÔNG nét hơn"*).
     */
    private fun openSession(which: CameraWhich) {
        val s = CameraSessionSpec.read(appCtx, which) ?: return
        val render = s.render
        val mode = s.mode
        val crops = s.crops
        val rot = s.rot
        val mirror = s.mirror
        // R8-B: đường `GL` cần TRỌN bộ uniform. Dựng ở đây — cùng nhịp đã quyết crop/xoay/dải — chứ không để
        // tầng vẽ tự tra prefs: bộ số phải thuộc về ĐÚNG cái crop vừa suy (hai lượt tra là hai kết quả lệch
        // được, và lệch thì không ai thấy vì ảnh vẫn ra hình). `null` ở hai đường kia ⇒ không đọc một khoá nào.
        val gl = if (CameraSignalPolicy.rotatesInShader(render)) {
            Prefs.cameraGlUniforms(
                appCtx, mode = mode, zoomPct = s.zoom, crops = crops,
                strip = s.effStrip,
                rotationDeg = rot, streamW = s.streamW, streamH = s.streamH,
                // Dấu dịch-x theo CAMERA: hai gương soi gương nhau ([ĐO khung thô 27/09 09:58]) ⇒ trái +1 / phải −1;
                // camera giữa 0 (hai núm dịch theo bên không áp) — xem KDoc [CameraWhich.panXSign].
                left = which == CameraWhich.LEFT,
                mirror = mirror,
                panXSign = which.panXSign,
                fullAmount = CameraViewMode.forcesFullAmount(s.asked, mode),
            )
        } else {
            null
        }
        // 2.92: tỉ lệ MÀN cho ma trận TextureView (đường TV + đường RƠI của GL) — `null` ở *Nắn thẳng* 100 % ⇒ y hệt.
        val videoScale = CameraViewPlan.tvScale(mode, s.zoom, crops, s.streamW, s.streamH, rot)
        val asked = s.asked
        Log.i(PanoramaHal.TAG, "camera $which (xi-nhan=$current theo-yêu-cầu=${od.current}) → ${s.view.name} camId=${s.camId} (def=${s.defId}) crop=${crops.frame?.joinToString() ?: "-"} kiểu=$mode${if (asked != mode) " (chọn $asked)" else ""} thu-phóng=${s.zoom}% nội-dung=${crops.content?.joinToString() ?: "-"} vùng=${s.span} hình=${s.shape} chỗ=${s.place?.encode() ?: s.corner} cỡ=${s.sizePct}% ảnh-tổng-hợp=$synth kết xuất=$render rot=$rot lật=$mirror")
        // Ghi lại NGỮ CẢNH của khung đang hiện cho lệnh chẩn đoán `camera_frame` (chỉ ĐỌC) — đúng chỗ đã quyết, không
        // để cầu kiểm thử tự tra lại prefs (owner đổi chip giữa hai lượt).
        shown = Shown(s.view.name, s.camId, crops.content?.joinToString(",") ?: "", rot,
            gl?.describe() ?: "mode=$mode${if (asked != mode) " asked=$asked" else ""} zoom=${s.zoom}% render=$render")
        openedSessions++; od.sessionOpened()   // wave 2C: phiên MỚI đọc cấu hình tươi ⇒ hết hẹn dựng lại lúc nhả xi-nhan
        // Bật panorama HAL (best-effort — vài ROM cần WORK_ON để camera stack sống) rồi ĐỔ frame AVMCamera
        // vào Surface của overlay (RE kinex `b1/RunnableC0170d`: đây mới là đường có HÌNH, LVDS thụ động ra đen).
        hal.open(s.view)
        // CAM-ROT-2 (owner 2026-09-26 "không muốn có viền đen … đúng tỷ lệ camera"): cửa sổ overlay lấy tỉ lệ
        // vùng crop SAU xoay ⇒ cần cỡ ảnh nguồn. `view.hintW/hintH` chỉ là **gợi ý** cho lượt dựng đầu;
        // số THẬT đo bằng `AVMCamera.getPreviewWidth/Height` ngay sau khi mở camera rồi báo lại tầng vẽ.
        overlay.show(
            corner = s.corner,
            which = which,
            onCluster = s.onCluster,
            crop = crops.frame,
            rotationDeg = rot,
            mirror = mirror,
            render = render,
            // 2.92: hình KHUNG thật sự dùng (*Theo cụm* + kiểu trọn dải ⇒ chữ nhật — spec R7); *Nắn thẳng* = pref y nguyên.
            shape = crops.frameShape,
            streamW = s.streamW,
            streamH = s.streamH,
            gl = gl,
            synthOn = synth,
            synthFile = synthFile,
            // L7 (nợ chéo L2): số đo dải cụm đến từ HỒ SƠ XE qua cửa duy nhất [CameraDefaults], không phải hằng `:core`.
            band = CameraDefaults.band(appCtx),
            // 2.92: TV/đường rơi lấy mẫu vùng NỘI DUNG + tỉ lệ vừa khung; cụm đặt cửa sổ VỪA (không phóng-cắt).
            video = CameraVideoContent(crops.content, videoScale, letterbox = CameraViewMode.fullView(mode)),
            // 2.93: vị trí kéo-thả + cỡ riêng camera; `null` ⇒ đường đặt chỗ 2.73–2.92 nguyên văn (CameraPlacement.custom).
            place = CameraOverlayPlace.of(s.place, s.sizePct),
        ) { surface ->
            runCatching {
                // `camera_synth`: producer đã là ảnh tổng hợp ([CameraSynthFeeder]) ⇒ KHÔNG mở HAL. Mở cả hai
                // là hai producer trên cùng một `BufferQueue`, tức một lượt đo trên một ảnh chắp vá.
                if (!synth) avm.open(s.camId, surface)
                // Xoay: `TextureView` làm bằng ma trận; `SurfaceView` không có `setTransform` nên chỉ còn
                // đường nhờ HAL — và "nhận" ≠ "có tác dụng", nên cửa sổ chỉ lấy tỉ lệ ĐÃ XOAY khi một trong
                // hai đường thật sự đứng ra làm (rot = 0 thì không cần ai làm).
                val byShell = CameraSignalPolicy.rotatesByMatrix(render) || CameraSignalPolicy.rotatesInShader(render)
                val byHal = !byShell && rot != 0 && !synth && avm.setDisplayOrientation(surface, rot)
                val size = if (synth) null else avm.previewSize()
                overlay.onStreamMeasured(
                    streamW = size?.getOrNull(0) ?: 0,
                    streamH = size?.getOrNull(1) ?: 0,
                    // Phép hợp ba nhánh nằm ở `:core` ([CameraSignalPolicy.rotationEffective]) — nói sai một
                    // nhánh là cửa sổ lấy tỉ lệ sai và ảnh bị giãn mà không ai báo lỗi (CLAUDE.md §2).
                    rotationEffective = CameraSignalPolicy.rotationEffective(render, rot, byHal),
                )
            }
        }
    }

    /**
     * Bật/tắt bơm ảnh tổng hợp (`camera_synth`) — **chỉ** cầu kiểm thử gọi.
     *
     * Đổi cờ rồi **dựng lại phiên đang treo** theo cờ mới: producer gắn vào `Surface` lúc dựng lớp video, nên đổi cờ
     * giữa hai khung không có tác dụng gì cả — và một lệnh đo báo `ok:true` mà không đổi gì là điều tệ hơn một lệnh
     * lỗi. Phần xi-nhan (nếu có) hạ như cũ (lượt xi-nhan/`camera --es name left` kế tiếp dựng lại); camera theo yêu
     * cầu đang bật (2.93) thì dựng lại NGAY — trạng thái của nó là "đang bật", không được thành "bật mà không hiện".
     *
     * @return cờ sau lượt đặt (đọc lại, không phải giá trị vừa nhận — cùng luật `read_back` của `prefs_set`).
     */
    fun setSynth(on: Boolean, file: String = ""): Boolean {
        // Tên tệp đã được [TestBridgeSynth.safeName] lọc còn **phần tên**; ghép với hộp cát của app ở đây — tầng
        // dưới (CameraVideoLayer/CameraSynthFeeder) chỉ nhận một đường TUYỆT ĐỐI đã tồn tại, không tự ghép gì.
        val resolved = if (on && file.isNotEmpty()) {
            val f = java.io.File(appCtx.getExternalFilesDir(null) ?: appCtx.filesDir, file)
            if (f.isFile) f.absolutePath else ""
        } else {
            ""
        }
        if (on && file.isNotEmpty() && resolved.isEmpty()) {
            Log.w(PanoramaHal.TAG, "camera_synth file «$file» KHÔNG có trong getExternalFilesDir ⇒ dùng ảnh sinh")
        }
        if (synth == on && synthFile == resolved) return synth
        synth = on
        synthFile = resolved
        dropBlinker()
        rebuild()
        Log.i(
            PanoramaHal.TAG,
            "camera_synth = $on tệp=${resolved.ifEmpty { "(ảnh sinh)" }} (xi-nhan đã hạ; camera theo yêu cầu=${od.current} dựng lại)",
        )
        return synth
    }

    /** Đường TUYỆT ĐỐI của PNG đang bơm thay ảnh sinh, rỗng = ảnh sinh bằng mô hình. Chỉ cầu kiểm thử đặt. */
    private var synthFile: String = ""

    /** Đồng hồ ĐƠN ĐIỆU cho HOLD (test override được). */
    internal var clockMs: () -> Long = { android.os.SystemClock.elapsedRealtime() }
    private val hold = CameraHold(HOLD_MS)
    /** Hẹn hết hạn HOLD (BG-13): `observe(null,null)` đúng lúc mốc ON cuối + HOLD_MS trôi qua ⇒ đóng camera. */
    private val expiry = Runnable { tickMain(null, null) }

    /** Ngữ cảnh của khung ĐANG hiện — đặt ở [openSession], xoá khi dỡ hẳn. `null` = không hiện gì. */
    private class Shown(val view: String, val camId: Int, val crop: String, val rot: Int, val note: String = "")

    /** Một dòng ngữ cảnh của phiên đang treo (kiểu + bộ uniform) — cho nút *Khung thô* ở Chẩn đoán. Chỉ ĐỌC. */
    fun sessionNote(): String = shown?.note.orEmpty()

    /** 2.92 · số hiệu PHIÊN, tăng ở mỗi [openSession] (xi-nhan · đổi bên · áp lại · xem thử · theo yêu cầu). Chỉ main ghi. */
    @Volatile
    private var openedSessions = 0L

    /** Số hiệu phiên vừa dựng — nút *Khung thô* chụp + đóng ĐÚNG phiên nó mở ([endPreview]). Chỉ ĐỌC. */
    fun sessionSeq(): Long = openedSessions

    @Volatile
    private var shown: Shown? = null

    /**
     * Chụp khung camera đang hiện ở cỡ [w] × [h] — đường của lệnh chẩn đoán `camera_frame` (`TestBridgeCameraFrame`),
     * spec `docs/specs/camera-turn-signal-hal-socket.html` R7. Chỉ ĐỌC: không mở/đóng camera, không chạm prefs; đường
     * kết xuất + cờ `available` ĐO lại từ tầng vẽ (CLAUDE.md §5). ⚠ PHẢI gọi trên main thread (`TextureView.getBitmap`).
     */
    fun grabFrame(w: Int, h: Int): CameraFrameShot {
        val s = shown
        return CameraFrameShot(
            // Chụp TRƯỚC khi đọc cờ: hai thứ cùng một nhịp main thread (không có lượt vẽ nào chen vào giữa).
            bitmap = overlay.captureFrame(w, h),
            available = overlay.available(),
            capturable = overlay.capturable(),
            overlayShowing = overlay.showing(),
            render = overlay.renderPath(),
            view = s?.view ?: "",
            camId = s?.camId ?: -1,
            crop = s?.crop ?: "",
            rotationDeg = s?.rot ?: 0,
            // ĐO từ tầng vẽ, không suy từ pref: câu hỏi là *"cái đang treo vẽ bằng gì"*.
            content = if (CameraSignalPolicy.rotatesInShader(overlay.renderPath())) {
                CameraFrameShot.CONTENT_DEWARPED
            } else {
                CameraFrameShot.CONTENT_RAW
            },
            glStats = overlay.glStats(),
            synthSize = overlay.synthSize(),
            glInfo = CameraGlInfo.summary(),
        )
    }

    /**
     * Chụp một khung **THÔ** cỡ [w] × [h] qua FBO của đường GL — `camera_frame --es name raw`. `null` = đường đang treo
     * không phải GL / chưa có khung / FBO không dựng được. ⚠ main thread; lượt đọc pixel chạy trên luồng vẽ và hàm này
     * **chặn** chờ nó — xem KDoc [CameraGlRenderer.grabRaw].
     */
    fun grabRawFrame(w: Int, h: Int): IntArray? = overlay.grabRawFrame(w, h)

    /**
     * XEM THỬ ngay một bên với pref VỪA đổi — dùng cho khối *Nếu camera không hiện* trong Cài đặt.
     *
     * Phải dỡ phiên đang treo TRƯỚC: [openSession] chỉ đọc pref lúc MỞ phiên, nên nếu overlay đang mở (người dùng bấm
     * nhiều chip liên tiếp, hoặc camera theo yêu cầu đang hiện đúng bên ấy) thì đổi pref không có tác dụng gì và người
     * ta tưởng chip không ăn. Sau đó để luật giữ ([CameraHold]) tự hạ như một lượt xi-nhan thật — hết giữ thì camera
     * theo yêu cầu (nếu đang bật) quay lại; không có hẹn giờ riêng, không đường nào làm overlay kẹt.
     */
    fun previewSide(left: Boolean) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            main.post { previewSide(left) }; return
        }
        dropBlinker()
        show(null)
        tickMain(left = left, right = !left)
        // Công tắc xi-nhan TẮT ⇒ `tickMain` không mở gì: camera theo yêu cầu (nếu đang bật) phải hiện lại — không được
        // thành "bật mà không hiện" (soát 2.93).
        if (showing == null) show(want())
    }

    /**
     * 2.92 — áp NGAY kiểu hình/thu phóng nếu khung đang hiện: dựng lại đúng camera theo đường *đổi camera* (không chạm
     * HOLD). 2.93: cũng là cửa áp của bộ chỉnh *Từng camera* (vị trí · cỡ · hình · kiểu · xoay · lật). Không hiện ⇒ không
     * làm gì (lượt sau tự đọc pref; không bật khung bất ngờ).
     */
    fun reapplyIfShowing() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            main.post { reapplyIfShowing() }; return
        }
        val which = showing ?: return
        closeSession(keepPano = true)
        openSession(which)
    }

    /** D6 (wave 2B) + CAM-D6-SAME-CAMERA-EDGE (wave 2C) — đổi hồ sơ: luật [CameraDemand.profileReapply] (ngay · hẹn lúc nhả xi-nhan · để yên). */
    fun reapplyIfDemandShowing() { main.post { if (od.onProfileSwitched(CameraWhich.ofTurn(current), showing)) reapplyIfShowing() } }

    /**
     * 2.92 — đóng lượt xem thử do nút *Khung thô* (Chẩn đoán) mở, CHỈ khi phiên đang treo vẫn là phiên ấy ([seq]): xi-nhan
     * thật / lượt khác đã thay phiên thì để yên (spec R8 *"đóng nếu chính nút đã mở"*). Gọi trên main.
     */
    fun endPreview(seq: Long) {
        if (current != Turn.NONE && openedSessions == seq) endBlinker()
    }

    /**
     * Hạ phần XI-NHAN (bên giữ + nền HOLD + hẹn giờ) rồi đưa cửa sổ về camera nên hiện ([want] — camera theo yêu cầu nếu
     * đang bật, không thì đóng). Đây là thứ `stop()` 2.73–2.92 làm khi chỉ có một nguồn. Wave 2C · CAM-D6-SAME-CAMERA-EDGE:
     * hồ sơ đổi lúc xi-nhan giữ ĐÚNG camera sẽ còn hiện ⇒ dựng lại theo cấu hình mới (`show` cùng camera = không dựng lại).
     */
    private fun endBlinker() {
        dropBlinker()
        if (od.takeReapplyAtRelease() && showing != null && want() == showing) reapplyIfShowing() else show(want())
    }

    /** Chỉ phần TRẠNG THÁI xi-nhan (không đụng cửa sổ) — cho lượt sắp tự dựng lại ([setSynth] · [previewSide]). */
    private fun dropBlinker() {
        current = Turn.NONE   // [P1 fix] reset để bật lại KHỚP lượt rẽ sau (không kẹt current cũ → return sớm)
        hold.reset()
        main.removeCallbacks(expiry)
    }

    /** Dựng lại phiên đang treo theo trạng thái hiện tại (dùng sau khi đổi nguồn ảnh) — không hiện gì ⇒ mở theo [want]. */
    private fun rebuild() {
        val next = want()
        show(null)
        show(next)
    }

    /**
     * ═══ Dỡ PHẦN CỨNG + CỬA SỔ của phiên đang treo — **thứ tự này là hợp đồng**, có bài canh ═══════════════════
     *
     * Gọi từ [show] (về `null`, hoặc **đổi camera**) và [reapplyIfShowing]. Lượt đổi camera KHÔNG được chạm máy trạng
     * thái (`hold`/`current`/hẹn giờ) — xem chú thích ở [show].
     *
     * @param keepPano `true` ở lượt **đổi camera**: chỉ dỡ `AVMCamera` (thứ đang giữ `Surface`), **không** tắt thiết bị
     *   panorama — `hal.open(view)` ngay sau đó sẽ đặt kênh mới. Một vòng `WORK_OFF → WORK_ON` giữa hai lượt rẽ là
     *   một thay đổi hành vi **chưa ai đo** trên xe (CLAUDE.md §6: đường mới không được đảo đường đang chạy), và
     *   con bọ 27/09 nằm ở `AVMCamera`, không ở thiết bị panorama.
     *
     * **Thứ tự bắt buộc: HAL trước, cửa sổ sau.** `overlay.hide()` huỷ `SurfaceTexture` của lớp video; nếu nó chạy
     * trước `avm.close()` thì HAL còn đang dequeue trên một hàng đệm vừa bị bỏ — đúng triệu chứng
     * `E/BufferQueueProducer … BufferQueue has been abandoned` mà xe 27/09 ghi 55 004 dòng. Đảo hai dòng này là
     * dựng lại đúng con bọ ấy, nên `CameraGlWiringContractTest.do phien cu TRUOC khi mo phien moi` ghim nó.
     */
    private fun closeSession(keepPano: Boolean = false) {
        // [SOÁT Opus 2026-09-27] Móc ĐO `rmPreviewSurface` chỉ chạy khi chế độ kiểm thử đang mở — lý do đầy đủ ở
        // KDoc [AvmCamera.rmOnClose]. Đọc ở ĐÂY (đúng nhịp dỡ) chứ không lúc mở: câu hỏi là *"lúc đóng, buổi đo có
        // đang chạy không"*.
        avm.rmOnClose = runCatching { TestBridgeStore.isOn(appCtx) }.getOrDefault(false)
        runCatching { avm.close() }
        if (!keepPano) hal.close()
        overlay.hide()
    }

    companion object {
        const val LIGHT_DEVICE = "android.hardware.bydauto.light.BYDAutoLightDevice"

        /** Giữ camera qua pha TẮT của nháy — giá trị + lý do ở [CameraHold.HOLD_MS]. */
        const val HOLD_MS = CameraHold.HOLD_MS
    }
}
