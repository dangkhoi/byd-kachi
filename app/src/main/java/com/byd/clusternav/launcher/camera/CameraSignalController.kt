package com.byd.clusternav.launcher.camera

import android.content.Context
import android.util.Log
import com.byd.clusternav.Prefs
import com.byd.clusternav.cameraSignalEnabled
import com.byd.clusternav.cameraPos
import com.byd.clusternav.cameraOnCluster
import com.byd.clusternav.cameraCamId
import com.byd.clusternav.cameraRotation
import com.byd.clusternav.cameraMirror
import com.byd.clusternav.cameraRender
import com.byd.clusternav.cameraSource
import com.byd.clusternav.cameraSpan
import com.byd.clusternav.cameraShape
import com.byd.clusternav.cameraStrip
import com.byd.clusternav.cameraCirclePct
import com.byd.clusternav.cameraHalMode
import com.byd.clusternav.cameraGlUniforms
import com.byd.clusternav.setCameraChannelFallback
import com.byd.clusternav.launcher.camera.CameraSignalPolicy.Turn
import com.byd.clusternav.launcher.testbridge.TestBridgeStore

/**
 * ═══ CAMERA THEO XI-NHAN · điều phối (`:app`) ═══════════════════════════════════════════════════════════════
 *
 * Mỗi nhịp nhận trạng thái xi-nhan (từ [HalSignalClient] — KHÔNG phải `CarStatus.lights`, xem KDoc [tick]) → nếu
 * bật tính năng (pref, mặc định TẮT) và bên xi-nhan ĐỔI thì: mở camera view tương ứng ([PanoramaHal]) + hiện overlay
 * bên đó ([CameraOverlayView]); hết xi-nhan ⇒ đóng. Chỉ ĐỔI khi khác nhịp trước (không dựng lại overlay mỗi nhịp —
 * cùng lẽ RainDefrostOwner).
 *
 * ⚠ Off-car: PanoramaHal no-op (device null) nhưng overlay vẫn dựng (TextureView đen) — đo được wiring. Tín hiệu
 *   video thật = on-car (runbook camera-panorama).
 */
class CameraSignalController(private val appCtx: Context) {

    private val hal by lazy { PanoramaHal(appCtx) }
    private val avm by lazy { AvmCamera() }
    private val overlay by lazy { CameraOverlayView(appCtx) }
    private var current: Turn = Turn.NONE

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
        if (!Prefs.cameraSignalEnabled(appCtx)) { if (current != Turn.NONE) stop(); return }
        val now = clockMs()
        val turn = hold.observe(left, right, now)
        // BG-13: hẹn ĐÚNG mốc HOLD hết hạn gần nhất (đặt lại mỗi nhịp — sự kiện ON mới đẩy mốc lùi). Không có bên
        // nào đang giữ ⇒ không hẹn gì. Handler main ⇒ [expiry] cũng chạy trên main.
        main.removeCallbacks(expiry)
        hold.expiresInMs(now)?.let { main.postDelayed(expiry, it) }
        if (turn == current) return   // không đổi ⇒ giữ nguyên (không dựng lại, không nháy theo đèn)
        // ═══ [P0 · xe 27/09] ĐỔI BÊN (LEFT ⇄ RIGHT) phải DỠ phiên cũ TRƯỚC ══════════════════════════════════════
        // [ĐO] `usage-1790477853304.log`: 09:58:15 mở LEFT (TV) → 09:58:26,017 rẽ RIGHT → `overlay.show` dựng lớp
        // video MỚI (huỷ `SurfaceTexture` của LEFT) và `avm.open` GHI ĐÈ tham chiếu AVMCamera cũ — **không** một
        // lời `stopPreview`/`close` nào cho phiên LEFT. Đúng 09:58:26,104, tức 87 ms sau, bắt đầu
        // `E/BufferQueueProducer [SurfaceTexture-0-4893-0] dequeueBuffer: BufferQueue has been abandoned` ở
        // ~16 dòng/giây và **không bao giờ dứt** (55 004 dòng tới 10:58, launcher 3,5 % CPU lúc rảnh, nhật ký
        // 130 KB/phút so với trần 20): HAL vẫn giữ `Surface` của một hàng đệm đã bị bỏ và cứ dequeue.
        //
        // Chỉ **một** hàng đệm duy nhất trong cả bản log nói đúng bệnh: phiên KHÔNG được đóng mới rò, còn mọi phiên
        // đi qua [stop] (09:58:36 · 09:59:25 · 10:02:21 · 10:04:28) đều im. ⇒ thuốc là gọi đúng đường dỡ đã có.
        //
        // KHÔNG gọi [stop]: nó `hold.reset()` + `removeCallbacks(expiry)`, mà [turn] vừa tính RA từ chính `hold` —
        // xoá nền HOLD ngay sau đó sẽ làm lượt sau đọc pha TẮT của đèn nháy thành NONE ⇒ overlay chớp tắt giữa
        // chuyến. [closeSession] là đúng phần *"dỡ phần cứng + cửa sổ"*, không đụng máy trạng thái.
        if (current != Turn.NONE && turn != Turn.NONE) closeSession(keepPano = true)
        current = turn
        if (turn == Turn.NONE) stop() else openSession(turn, forcedPano = false)
    }

    /**
     * ═══ Dựng MỘT phiên camera cho bên [turn] — đường chung của xi-nhan **và** của phép lùi CHANNEL → PANO ═══════
     *
     * Tách khỏi [tickMain] ở 2.76 (R3): phép lùi phải dựng lại phiên *"y như xi-nhan vừa bật"* vì bộ uniform/crop là
     * **bất biến theo phiên** (đổi giữa hai khung không có tác dụng) — hai bản sao của lượt dựng là hai chỗ để lệch.
     *
     * @param forcedPano `true` = phiên do [CameraChannelFallback] mở: nguồn ép về PANO, cờ lùi **giữ** (không lùi
     *   lần hai). `false` = phiên do xi-nhan mở: đọc pref nguồn như thường, cờ lùi đặt lại.
     */
    private fun openSession(turn: Turn, forcedPano: Boolean) {
        val view = CameraSignalPolicy.defaultView(turn) ?: return
        val side = CameraSignalPolicy.defaultSide(turn) ?: return
        val left = turn == Turn.LEFT
        fallback.onSessionStart(forcedPano)
        val session = ++sessionId
        // Góc hiện overlay = pref TỪNG BÊN (`camera_pos_left/right`, mặc định trái→TL / phải→TR). KHÔNG suy
        // từ `side`: owner chốt xi-nhan trái vẫn được hiện ở góc trên-phải (spec R4).
        val corner = Prefs.cameraPos(appCtx, left = turn == Turn.LEFT)
        // cameraId: picker TỪNG BÊN. defId = view.cameraId (Seal fisheye = id 1). SL6 fisheye = id 0
        // (ảnh owner: cam 0 ra 4-in-1) ⇒ SL6 chọn id 0 trong Cài đặt. crop [0.25-0.35]/[0.65-0.75] là VÙNG
        // GƯƠNG của ẢNH FISHEYE — đúng cho CẢ id 0 (SL6) lẫn id 1 (Seal), nên LUÔN áp view.crop (đừng gate
        // theo camId: gate camId==defId từng chặn SL6-chọn-id-0 khỏi crop = REGRESSION 2.42→2.44 khi đổi id 0→1).
        val defId = view.cameraId
        val camId = Prefs.cameraCamId(appCtx, left = turn == Turn.LEFT, defId)
        // R8-A (2.74 · RE `electro-camera-RE-2026-09-26.md` §5 K10 · §6.1): vùng cắt được **SUY RA** từ (dải,
        // bề rộng, hình khung) ở `:core` thay vì lấy hằng `view.crop` — vì **dải nào là hướng nào vẫn
        // [CHƯA BIẾT]** (§7 Q1/Q2) nên owner phải dò được trên xe mà không build lại. Bốn pref mặc định cho
        // ĐÚNG hai rect của 2.73 (bài `mac dinh trung 2 rect cua 2 73` ghim literal) ⇒ xe không chạm Cài đặt
        // thì không thấy khác một pixel nào (CLAUDE.md §6).
        val span = Prefs.cameraSpan(appCtx)
        val shape = Prefs.cameraShape(appCtx)
        // 2.75 · NGUỒN ảnh (`camera_source`): `PANO` = khung ghép rồi cắt dải (đường 2.36…2.74, mặc định),
        // `CHANNEL` = MỘT kênh camera đổ đầy buffer, **kéo ngang ×STRIPS** ([ĐO] xe 27/09 — KDoc
        // `CameraSignalPolicy.SOURCE_CHANNEL`). 2.76 (R2/R3): nguồn đi qua [CameraChannelFallback.sourceFor]
        // (đã lùi ⇒ PANO), kênh per-side đến từ HỒ SƠ XE ([CameraDefaults]) thay cho hằng trên `CamView`, và
        // `channel` chỉ `true` khi kênh THẬT SỰ hợp ra được ([CameraSignalPolicy.channelActive]) — chọn CHANNEL trên
        // xe chưa có bản đồ kênh thì HAL dò `0..3` trả khung ghép, mà chia bề ngang cho 4 là `aspect` sai im lặng.
        val source = fallback.sourceFor(Prefs.cameraSource(appCtx))
        // [P2 · SOÁT Opus 2026-09-27] Phiên do PHÉP LÙI mở đi đúng đường 2.73: AUTO ⇒ `AvmCamera` dò `0..3` ⇒ khung
        // GHÉP. Không ép thì `channelFor` trả thẳng `halModePref` (nguồn đã là PANO — `CameraChannel.kt`), tức phiên
        // "đã lùi về toàn cảnh" vẫn gọi `addPreviewSurface(surface, <kênh vừa chết>)`: ở nhánh `no-frame` (HAL NHẬN
        // kênh mà không đẩy khung) kênh ấy lại được nhận, lại không có khung, mà `fellBack` đã chốt và hình học đã
        // coi là khung ghép ⇒ ô gương đen suốt lượt rẽ trong khi log nói *"LÙI VỀ TOÀN CẢNH"* (CLAUDE.md §2: một
        // dòng log nói đã chữa trong khi chưa chữa gì). Ghim ở `CameraChannelFallbackWiringContractTest`; 🚗 CAM-F3.
        // (Viết một biểu thức, KHÔNG khối `} else {`: bài canh `CameraMirrorWiringContractTest` cắt vùng GL bằng mốc
        // `} else {` ĐẦU TIÊN trong `openSession` — thêm một khối nữa ở trên là cắt sai vùng.)
        val halMode = if (forcedPano) CameraSignalPolicy.HAL_MODE_AUTO else CameraSignalPolicy.channelFor(
            source = source,
            halModePref = Prefs.cameraHalMode(appCtx),
            profileChannel = CameraDefaults.of(appCtx).channel(left = turn == Turn.LEFT),
        )
        val channel = CameraSignalPolicy.channelActive(source, halMode)
        // Phiên xi-nhan MỚI xoá dấu "đã lùi" của lần trước: Cài đặt in dấu của phiên CHANNEL GẦN NHẤT, không phải
        // của một lần nào đó trong quá khứ. Phiên do phép lùi mở thì không đụng (nó vừa được ghi ngay trước đó).
        if (!forcedPano) Prefs.setCameraChannelFallback(appCtx, "")
        val crop = CameraPanoCrop.cropFor(
            view = view,
            left = turn == Turn.LEFT,
            strip = Prefs.cameraStrip(appCtx, left = turn == Turn.LEFT),
            span = span,
            shape = shape,
            circlePct = Prefs.cameraCirclePct(appCtx),
            channel = channel,
        )
        // R7 (owner 2026-09-26): vùng gương crop từ fisheye là dải DỌC ⇒ căng vào ô vuông thì NGANG; xoay
        // theo pref TỪNG BÊN `camera_rot_left/right` (2.71; mặc định theo hồ sơ xe — Seal 0, xe chưa đo trái ↺ −90 /
        // phải ↻ +90). Tính ở `:core`, overlay chỉ nhận số độ.
        val rot = CameraSignalPolicy.rotationDegrees(Prefs.cameraRotation(appCtx, left = turn == Turn.LEFT), left = turn == Turn.LEFT)
        // 2.76 L7 — LẬT GƯƠNG từng bên (`camera_mirror_*`, research §6.2; tay gương HAL [CHƯA BIẾT] tới CAM-M1). Lật ở
        // không gian NGUỒN, trước xoay, ở CẢ hai đường: GL qua `flipH` (`uSrcRect.z < 0`), TV qua ma trận (bước 1b).
        val mirror = Prefs.cameraMirror(appCtx, left = turn == Turn.LEFT)
        // CLOSE-14 (CAM-LAG): đường kết xuất là một LỰA CHỌN có mã lưu bền (`camera_render`), mặc định theo hồ sơ
        // xe. Đọc mỗi lượt dựng overlay ⇒ đổi chip trong Cài đặt là lượt xi-nhan sau đã theo.
        val render = Prefs.cameraRender(appCtx)
        // R8-B: đường `GL` cần TRỌN bộ uniform. Dựng ở đây — cùng nhịp đã quyết crop/xoay/dải — chứ không để
        // tầng vẽ tự tra prefs: bộ số phải thuộc về ĐÚNG cái crop vừa suy (hai lượt tra là hai kết quả lệch
        // được, và lệch thì không ai thấy vì ảnh vẫn ra hình). `null` ở hai đường kia ⇒ không đọc một khoá nào.
        val hintW = CameraPanoCrop.contentWidth(view.hintW, channel)
        val gl = if (CameraSignalPolicy.rotatesInShader(render)) {
            Prefs.cameraGlUniforms(
                appCtx, view = view, crop = crop,
                strip = Prefs.cameraStrip(appCtx, left = turn == Turn.LEFT),
                rotationDeg = rot, streamW = hintW, streamH = view.hintH,
                // Dấu của `camera_dewarp_pan_x` theo BÊN: hai camera gương soi gương nhau ([ĐO khung thô
                // 27/09 09:58]) nên một pref dùng chung phải đổi dấu, nếu không hai khung đi hai phía
                // ngược nhau — xem KDoc [CameraDewarpPrefs.panXSign].
                left = turn == Turn.LEFT,
                mirror = mirror,
                channel = channel,
            )
        } else {
            null
        }
        Log.i(PanoramaHal.TAG, "xi-nhan $turn → camera ${view.name} camId=$camId (def=$defId) crop=${crop?.joinToString() ?: "-"} vùng=$span hình=$shape halMode=$halMode nguồn=${if (channel) "CHANNEL" else "PANO"}${if (forcedPano) " (đã lùi)" else ""} ảnh-tổng-hợp=$synth overlay $side góc=$corner kết xuất=$render rot=$rot lật=$mirror")
        // Bật panorama HAL (best-effort — vài ROM cần WORK_ON để camera stack sống) rồi ĐỔ frame AVMCamera
        // vào Surface của overlay (RE kinex `b1/RunnableC0170d`: đây mới là đường có HÌNH, LVDS thụ động ra đen).
        // Ghi lại NGỮ CẢNH của khung đang hiện cho lệnh chẩn đoán `camera_frame` (chỉ ĐỌC). Ghi ở đây —
        // đúng chỗ đã quyết — chứ không để cầu kiểm thử tự tra lại prefs: bản tra thứ hai sẽ nói theo
        // prefs HIỆN TẠI, không theo cái khung đang treo trên màn (owner đổi chip giữa hai lượt xi-nhan).
        shown = Shown(view.name, camId, crop?.joinToString(",") ?: "", rot, channel)
        hal.open(view)
        // CAM-ROT-2 (owner 2026-09-26 "không muốn có viền đen … đúng tỷ lệ camera"): cửa sổ overlay lấy tỉ lệ
        // vùng crop SAU xoay ⇒ cần cỡ ảnh nguồn. `view.hintW/hintH` chỉ là **gợi ý** cho lượt dựng đầu;
        // số THẬT đo bằng `AVMCamera.getPreviewWidth/Height` ngay sau khi mở camera rồi báo lại tầng vẽ.
        overlay.show(
            corner = corner,
            side = side,
            onCluster = Prefs.cameraOnCluster(appCtx),
            crop = crop,
            rotationDeg = rot,
            mirror = mirror,
            render = render,
            shape = shape,
            streamW = hintW,
            streamH = view.hintH,
            gl = gl,
            synthOn = synth,
            synthFile = synthFile,
            // L7 (nợ chéo L2): số đo dải cụm đến từ HỒ SƠ XE qua cửa duy nhất [CameraDefaults], không phải hằng `:core`.
            band = CameraDefaults.band(appCtx),
        ) { surface ->
            runCatching {
                // `camera_synth`: producer đã là ảnh tổng hợp ([CameraSynthFeeder]) ⇒ KHÔNG mở HAL. Mở cả hai
                // là hai producer trên cùng một `BufferQueue`, tức một lượt đo trên một ảnh chắp vá.
                if (!synth) avm.open(camId, surface, halMode)
                // Xoay: `TextureView` làm bằng ma trận; `SurfaceView` không có `setTransform` nên chỉ còn
                // đường nhờ HAL — và "nhận" ≠ "có tác dụng", nên cửa sổ chỉ lấy tỉ lệ ĐÃ XOAY khi một trong
                // hai đường thật sự đứng ra làm (rot = 0 thì không cần ai làm).
                val byShell = CameraSignalPolicy.rotatesByMatrix(render) || CameraSignalPolicy.rotatesInShader(render)
                val byHal = !byShell && rot != 0 && !synth && avm.setDisplayOrientation(surface, rot)
                val size = if (synth) null else avm.previewSize()
                overlay.onStreamMeasured(
                    // Cỡ ĐO được là cỡ BUFFER; tầng vẽ cần cỡ NỘI DUNG (kênh đơn bị kéo ngang ×STRIPS).
                    streamW = CameraPanoCrop.contentWidth(size?.getOrNull(0) ?: 0, channel),
                    streamH = size?.getOrNull(1) ?: 0,
                    // Phép hợp ba nhánh nằm ở `:core` ([CameraSignalPolicy.rotationEffective]) — nói sai một
                    // nhánh là cửa sổ lấy tỉ lệ sai và ảnh bị giãn mà không ai báo lỗi (CLAUDE.md §2).
                    rotationEffective = CameraSignalPolicy.rotationEffective(render, rot, byHal),
                )
                // R3 (2.76): HAL từ chối kênh đơn ⇒ lùi về PANO — quyết ở `:core`, làm ở đây, đúng một lần/phiên.
                if (!synth && fallback.onHalResult(channel, avm.channelRefused) == CameraChannelFallback.Action.REBUILD_PANO) {
                    fallbackToPano(turn, session, "rc=false")
                }
            }
        }
        // R3 (2.76): ngân sách khung ĐẦU — không có khung sau [CameraChannelFallback.FIRST_FRAME_BUDGET_MS] ⇒ lùi.
        // Chỉ đo được ở đường có bộ đếm (GL: `frames=N`); đường khác trả `null` ⇒ `:core` không lùi theo số không có.
        main.removeCallbacks(firstFrameCheck)
        if (channel) {
            firstFrameSession = session
            main.postDelayed(firstFrameCheck, CameraChannelFallback.FIRST_FRAME_BUDGET_MS)
        }
    }

    /** Số hiệu phiên đang dựng — để một callback/hẹn giờ của phiên CŨ (đã đổi bên) không lùi nhầm phiên MỚI. */
    private var sessionId = 0
    private var firstFrameSession = -1

    /** Máy trạng thái lùi CHANNEL → PANO (`:core`, thuần, có bài test) — một cho cả controller, đặt lại mỗi phiên. */
    private val fallback = CameraChannelFallback()

    private val firstFrameCheck = Runnable {
        if (firstFrameSession != sessionId || current == Turn.NONE) return@Runnable
        val frames = CameraChannelFallback.framesOf(overlay.glStats())
        if (fallback.onFirstFrameBudget(channel = shown?.channel == true, frames = frames) ==
            CameraChannelFallback.Action.REBUILD_PANO
        ) {
            fallbackToPano(current, sessionId, "no-frame")
        }
    }

    /**
     * Lùi phiên [turn] về **PANO** — dỡ phiên đang treo theo đúng đường đổi bên (`closeSession(keepPano = true)`,
     * HAL trước cửa sổ sau) rồi [openSession] `forcedPano = true`. **Một dòng log to**, một dấu lưu bền cho Cài đặt.
     *
     * `post` về main thay vì gọi thẳng: chỗ gọi đầu tiên nằm **trong** callback `onSurfaceTextureAvailable` của lớp
     * video đang bị dỡ — `overlay.hide()` tái nhập ở đó là huỷ chính `SurfaceTexture` đang phát callback.
     */
    private fun fallbackToPano(turn: Turn, session: Int, reason: String) {
        main.post {
            if (session != sessionId || current != turn) return@post   // phiên đã đổi ⇒ dấu cũ, bỏ
            Log.w(PanoramaHal.TAG, "NGUỒN MỘT KÊNH không lên ($reason) ⇒ LÙI VỀ TOÀN CẢNH (PANO) cho $turn — một lần/phiên")
            Prefs.setCameraChannelFallback(appCtx, reason)
            closeSession(keepPano = true)
            openSession(turn, forcedPano = true)
        }
    }

    /**
     * Bật/tắt bơm ảnh tổng hợp (`camera_synth`) — **chỉ** cầu kiểm thử gọi.
     *
     * Đổi cờ rồi **đóng overlay đang treo** (nếu có): producer gắn vào `Surface` lúc dựng lớp video, nên đổi cờ giữa
     * hai khung không có tác dụng gì cả — và một lệnh đo báo `ok:true` mà không đổi gì là điều tệ hơn một lệnh lỗi.
     * Lượt xi-nhan/`camera --es name left` kế tiếp dựng lại theo cờ mới.
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
        if (current != Turn.NONE) stop()
        Log.i(
            PanoramaHal.TAG,
            "camera_synth = $on tệp=${resolved.ifEmpty { "(ảnh sinh)" }} (overlay đã đóng, lượt xi-nhan sau dựng lại)",
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

    /** Ngữ cảnh của khung ĐANG hiện — đặt ở [tickMain] lúc dựng overlay, xoá ở [stop]. `null` = không hiện gì. */
    private class Shown(val view: String, val camId: Int, val crop: String, val rot: Int, val channel: Boolean)

    @Volatile
    private var shown: Shown? = null

    /**
     * Chụp khung camera đang hiện ở cỡ [w] × [h] — đường của lệnh chẩn đoán `camera_frame`
     * (`TestBridgeCameraFrame`), spec `docs/specs/camera-turn-signal-hal-socket.html` R7.
     *
     * KHÔNG đổi một chữ nào trong đường frame: không mở/đóng camera, không dựng lại overlay, không chạm prefs. Nó
     * chỉ ĐỌC — đúng vai của một bề mặt đo (CLAUDE.md §15 bước 2/3), nên gọi giữa lúc đang lái không đổi hành vi.
     *
     * Đường kết xuất và cờ `available` được **ĐO** lại từ tầng vẽ (`overlay.renderPath()/capturable()`), không lấy
     * từ [Shown]: pref có thể đã đổi sau lượt dựng, mà câu hỏi ở đây là *"cái đang treo là thứ gì"* (CLAUDE.md §5).
     *
     * ⚠ PHẢI gọi trên main thread: [CameraOverlayView.captureFrame] đi qua `TextureView.getBitmap`, và cả cây view
     * lẫn `WindowManager` ở đây đều là main-thread-only (xem KDoc [tick]). Chỗ gọi duy nhất (cầu kiểm thử) `post`
     * về main trước khi gọi.
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
            // ĐO từ tầng vẽ, không suy từ pref: câu hỏi là *"cái đang treo vẽ bằng gì"*, và pref có thể đã đổi sau
            // lượt dựng (cùng lẽ `render`/`capturable` ngay trên).
            content = if (CameraSignalPolicy.rotatesInShader(overlay.renderPath())) {
                CameraFrameShot.CONTENT_DEWARPED
            } else {
                CameraFrameShot.CONTENT_RAW
            },
            glStats = overlay.glStats(),
            synthSize = overlay.synthSize(),
            glInfo = CameraGlInfo.summary(),
            channel = s?.channel == true,
        )
    }

    /**
     * Chụp một khung **THÔ** cỡ [w] × [h] qua FBO của đường GL — `camera_frame --es name raw`.
     *
     * `null` = đường đang treo không phải GL, hoặc chưa có khung nào, hoặc FBO không dựng được. Chỗ gọi nói THẲNG lý
     * do nào (xem `TestBridgeCameraFrame`) thay vì để người đang ngồi trong xe đọc một mã lỗi trống.
     *
     * ⚠ Cùng ràng buộc luồng với [grabFrame]: main thread (đi qua cây view). Lượt đọc pixel thật thì chạy trên luồng
     * vẽ và hàm này **chặn** chờ nó — xem KDoc [CameraGlRenderer.grabRaw].
     */
    fun grabRawFrame(w: Int, h: Int): IntArray? = overlay.grabRawFrame(w, h)

    private fun stop() {
        current = Turn.NONE   // [P1 fix] reset để bật lại KHỚP lượt rẽ sau (không kẹt current cũ → return sớm)
        shown = null
        hold.reset()
        main.removeCallbacks(expiry)
        main.removeCallbacks(firstFrameCheck)
        closeSession()
    }

    /**
     * ═══ Dỡ PHẦN CỨNG + CỬA SỔ của phiên đang treo — **thứ tự này là hợp đồng**, có bài canh ═══════════════════
     *
     * Gọi từ hai chỗ: [stop] (về NONE) và [tickMain] khi **đổi bên** LEFT ⇄ RIGHT. Tách khỏi [stop] vì lượt đổi bên
     * KHÔNG được chạm máy trạng thái (`hold`/`current`/hẹn giờ) — xem chú thích ở [tickMain].
     *
     * @param keepPano `true` ở lượt **đổi bên**: chỉ dỡ `AVMCamera` (thứ đang giữ `Surface`), **không** tắt thiết bị
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
