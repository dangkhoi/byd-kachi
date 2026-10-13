package com.byd.clusternav.launcher

import android.view.Surface
import android.view.TextureView
import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.SwapChain
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer

/**
 * ═══ 3.01 R25 — lớp Filament của widget xe 3D (chỉ vẽ; quyết định ở `:core` Car3d*) ═════════════════════════════
 *
 * Cách làm (học CÁCH từ bộ vẽ xe của Kim/Kinex — Filament vào bề mặt, trần khung, ánh sáng hướng, tắt bóng — không
 * chép mã/tài sản): `TextureView` (widget nằm trong vùng làm việc có cuộn/chồng lớp ⇒ không SurfaceView) qua `UiHelper`;
 * nền TRONG SUỐT (thẻ kính của Kachi lộ ra sau xe); không bóng đổ, không SSAO, không bloom; ánh sáng = nền SH 1 dải dựng
 * bằng số (không cần `filament-utils`/KTX) + 2 đèn hướng. Mức LOW = không khử răng cưa · MEDIUM = FXAA.
 *
 * KHÔNG tự vẽ theo nhịp: chủ (widget) gọi [render] khi có thay đổi/hoạt cảnh, trần ~24 khung/giây ở chủ. Mọi lời gọi
 * trên luồng chính (Filament Java API không đa luồng). [destroy] trả hết tài nguyên GPU — gọi khi view rời cửa sổ.
 */
internal class Car3dRenderer private constructor(
    private val engine: Engine, tv: TextureView, medium: Boolean, night: Boolean, private val onSize: (Int, Int) -> Unit,
) : UiHelper.RendererCallback {
    private val renderer: Renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view: View = engine.createView()
    private val camEntity = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(camEntity)
    private val ui = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private var swap: SwapChain? = null
    private val materials = UbershaderProvider(engine)
    private val loader = AssetLoader(engine, materials, EntityManager.get())
    private val resources = ResourceLoader(engine)
    private var asset: FilamentAsset? = null
    private val lights = IntArray(3) { EntityManager.get().create() }
    private var ibl: IndirectLight? = null
    private var grading: ColorGrading? = null
    private val hidden = HashSet<Int>()
    var width = 0; private set
    var height = 0; private set

    init {
        renderer.clearOptions = Renderer.ClearOptions().apply { clear = true; setClearColor(0.0, 0.0, 0.0, 0.0) }
        view.scene = scene
        view.camera = camera
        view.blendMode = View.BlendMode.TRANSLUCENT
        view.setShadowingEnabled(false)
        aa(medium)
        camera.setExposure(16f, 1f / 125f, 100f)
        // Ánh xạ tông GIỮ MÀU (Khronos PBR Neutral): mặc định của Filament làm đèn đỏ/hổ phách sáng ngả trắng khi gần bão hoà
        // (owner 13/10: đèn đuôi "trắng cháy"). Màu nền ≤ ~0,8 đi qua gần như nguyên vẹn ⇒ sơn đúng màu tệp AVM.
        grading = ColorGrading.Builder().toneMapper(ToneMapper.PBRNeutral()).build(engine).also { view.colorGrading = it }
        lighting(night)
        ui.renderCallback = this
        ui.isOpaque = false
        ui.attachTo(tv)
    }

    /**
     * Khử răng cưa. Owner 13/10 *"viền trên kính nhoè"* · *"đèn sau sáng nhoè cạnh"*: bản đầu = KHÔNG khử ở mức LOW (mọi đầu
     * xe) ⇒ cạnh mảnh tương phản cao (gờ kính đen trên thân trắng, dải đèn đuôi sáng) bậc thang + lấp lánh khi xoay; FXAA ở
     * MEDIUM thì làm MỜ cạnh (lọc ảnh sau). Nay MSAA 4× (mẫu hình học, không làm mờ kết cấu) ở mọi mức; chi phí đo
     * [ĐO máy ảo — xem diagnostics §5]. Chỉ tốn khi VẼ (rảnh = 0 khung).
     */
    private fun aa(@Suppress("UNUSED_PARAMETER") medium: Boolean) {
        view.antiAliasing = View.AntiAliasing.NONE
        view.multiSampleAntiAliasingOptions = View.MultiSampleAntiAliasingOptions().apply { enabled = true; sampleCount = MSAA_SAMPLES }
    }

    /**
     * Màu sơn (tuyến tính 0..1) cho mọi vật liệu thân xe ([PAINT_MATERIALS] — tên vật liệu glTF do [Car3dGlb] đặt theo bộ đỉnh AVM).
     * Đổi tại chỗ một tham số — không dựng lại GLB. Trả `true` nếu có vật liệu nhận (chủ xin vẽ lại).
     */
    fun paint(rgb: List<Double>): Boolean {
        val a = asset ?: return false
        val rm = engine.renderableManager
        var hit = false
        for (e in renderables()) {
            // Theo NÚT (`extras.mat`, do Car3dGlb ghi): mọi bộ phận sơn — thân, cửa đóng, CÁNH CỬA MỞ — cùng nhận màu. Bản trước
            // lọc theo tên vật liệu của gltfio ⇒ cửa (thực thể vật liệu riêng mỗi primitive) giữ màu cũ [ĐO máy ảo 13/10].
            if (Car3dBox.material(a.getExtras(e)) !in PAINT_MATERIALS) continue
            val inst = rm.getInstance(e)
            for (p in 0 until rm.getPrimitiveCount(inst)) {
                rm.getMaterialInstanceAt(inst, p).setParameter("baseColorFactor", rgb[0].toFloat(), rgb[1].toFloat(), rgb[2].toFloat(), 1f)
                hit = true
            }
        }
        return hit
    }

    /** Nền SH 1 dải (một màu trời đều) + nắng chéo trước-trái-trên + đèn phụ sau-phải. Đêm: dịu + lạnh hơn. */
    private fun lighting(night: Boolean) {
        val k = if (night) NIGHT_K else 1f
        ibl = IndirectLight.Builder().irradiance(1, if (night) floatArrayOf(0.85f, 0.9f, 1f) else floatArrayOf(1f, 1f, 1f))
            .intensity(IBL_LUX * k).build(engine)
        scene.indirectLight = ibl
        LightManager.Builder(LightManager.Type.DIRECTIONAL).color(1f, 0.98f, 0.95f).intensity(SUN_LUX * k)
            .direction(-0.5f, -0.8f, -0.4f).castShadows(false).build(engine, lights[0])
        LightManager.Builder(LightManager.Type.DIRECTIONAL).color(0.85f, 0.9f, 1f).intensity(FILL_LUX * k)
            .direction(0.6f, -0.3f, 0.6f).castShadows(false).build(engine, lights[1])
        // POLISH2 (owner 13/10 "mâm phía dưới mất màu"): ánh dội mặt đất — đèn yếu chiếu LÊN, mặt chúi xuống (nửa dưới mâm, mặt dưới
        // nắp cốp mở) không còn đen kịt dưới nắng + SH đều.
        LightManager.Builder(LightManager.Type.DIRECTIONAL).color(0.95f, 0.95f, 1f).intensity(BOUNCE_LUX * k)
            .direction(0.1f, 1f, 0.05f).castShadows(false).build(engine, lights[2])
        lights.forEach { scene.addEntity(it) }
    }

    /** Nạp `.glb` (ByteBuffer trực tiếp). `false` ⇒ tệp hỏng (chủ lùi 2D). Ảnh nhúng giải mã trong `loadResources`. */
    fun load(glb: ByteBuffer): Boolean {
        val a = loader.createAsset(glb) ?: return false
        resources.loadResources(a)
        a.releaseSourceData()
        scene.addEntities(a.entities)
        asset = a
        return true
    }

    /** Đổi ánh sáng ngày/đêm tại chỗ (không dựng lại engine). */
    fun night(night: Boolean) {
        val k = if (night) NIGHT_K else 1f
        ibl?.setIntensity(IBL_LUX * k)
        val lm = engine.lightManager
        lm.setIntensity(lm.getInstance(lights[0]), SUN_LUX * k)
        lm.setIntensity(lm.getInstance(lights[1]), FILL_LUX * k)
        lm.setIntensity(lm.getInstance(lights[2]), BOUNCE_LUX * k)
    }

    /** Hộp bao mô hình (khung mô hình): tâm + nửa cạnh. */
    fun bounds(): Car3dBounds? = asset?.boundingBox?.let { b ->
        Car3dBounds(V3(b.centerX.toDouble(), b.centerY.toDouble(), b.centerZ.toDouble()),
            V3(b.halfExtentX.toDouble(), b.halfExtentY.toDouble(), b.halfExtentZ.toDouble()))
    }

    /**
     * Hộp bao của các bộ phận ĐANG HIỆN (khung mô hình), hợp từ hộp riêng trong `extras`. Hộp của cả tài sản tính cả bản
     * cửa-MỞ đang ẩn ⇒ rộng 3,7 m thay vì 1,86 m [ĐO máy ảo 13/10] — xe nhỏ trong ô và chấm sườn tính sai mặt. Không có
     * `extras` ⇒ hộp của tài sản.
     */
    fun visibleBounds(): Car3dBounds? {
        val mn = FloatArray(3) { Float.MAX_VALUE }; val mx = FloatArray(3) { -Float.MAX_VALUE }; var hit = false
        for (e in renderables()) {
            if (e in hidden) continue
            val b = Car3dBox.parse(asset?.getExtras(e)) ?: return bounds()
            for (k in AXES) { if (b[k] < mn[k]) mn[k] = b[k]; if (b[k + 3] > mx[k]) mx[k] = b[k + 3] }
            hit = true
        }
        if (!hit) return bounds()
        return Car3dBounds(V3(((mn[0] + mx[0]) / 2).toDouble(), ((mn[1] + mx[1]) / 2).toDouble(), ((mn[2] + mx[2]) / 2).toDouble()),
            V3(((mx[0] - mn[0]) / 2).toDouble(), ((mx[1] - mn[1]) / 2).toDouble(), ((mx[2] - mn[2]) / 2).toDouble()))
    }

    /** Tên mọi bộ phận vẽ được (tên nút, hoặc tên nút cha khi nút mang lưới không tên). */
    fun partNames(): List<String> = renderables().toList().mapNotNull { nameOf(it) }.distinct()

    /**
     * Neo [Car3dAnchor] → điểm khung MÔ HÌNH (gọi TRƯỚC [place], khi gốc còn đơn vị). Nút: hộp bao hợp của mọi bộ phận
     * khớp mẫu, độ cao theo `ty`. Không khớp ⇒ `null` (chủ ẩn chấm + log).
     */
    fun anchor(a: Car3dAnchor?): V3? = when (a) {
        null -> null
        is Car3dAnchor.Point -> V3(a.x, a.y, a.z)
        is Car3dAnchor.FirstOf -> a.options.firstNotNullOfOrNull { anchor(it) }
        is Car3dAnchor.Node -> nodeBox(a.pattern)?.let { (mn, mx) -> V3((mn.x + mx.x) / 2, mn.y + (mx.y - mn.y) * a.ty, (mn.z + mx.z) / 2) }
    }

    /** Hộp (khung MÔ HÌNH) của neo [a]: hợp hộp các bộ phận khớp (neo điểm ⇒ hộp suy biến). Cho [Car3dPick]. */
    fun box(a: Car3dAnchor?, posed: Boolean = false): Pair<V3, V3>? = when (a) {
        null -> null
        is Car3dAnchor.Point -> V3(a.x, a.y, a.z).let { it to it }
        is Car3dAnchor.FirstOf -> a.options.firstNotNullOfOrNull { box(it, posed) }
        is Car3dAnchor.Node -> nodeBox(a.pattern, posed)
    }

    /**
     * POLISH2 — đèn khoang (đèn đọc): nội thất hắt sáng ấm khi [on] — đổi `emissiveFactor` (nhân ảnh nội thất) của vật liệu nội thất
     * tại chỗ. `true` = có đổi (chủ xin vẽ lại).
     */
    fun glow(on: Boolean): Boolean {
        if (glowing == on) return false
        val a = asset ?: return false
        glowing = on
        val rm = engine.renderableManager
        val k = if (on) GLOW else floatArrayOf(0f, 0f, 0f)
        for (e in renderables()) {
            if (Car3dBox.material(a.getExtras(e)) != INTERIOR) continue
            val inst = rm.getInstance(e)
            for (p in 0 until rm.getPrimitiveCount(inst)) {
                // [ĐO máy ảo 13/10] `MaterialInstance.material` của thực thể gltfio là null (vỏ Java không giữ Material) ⇒ không hỏi
                // hasParameter được. Vật liệu nội thất KHAI `emissiveFactor` (+ ảnh) trong GLB ([Car3dCatalog] `interior`) ⇒ có tham số.
                rm.getMaterialInstanceAt(inst, p).setParameter(EMISSIVE, k[0], k[1], k[2])
            }
        }
        return true
    }
    private var glowing: Boolean? = null

    /** Có bộ phận nào tên khớp [pattern] (kể cả đang ẩn). */
    fun hasPart(pattern: String): Boolean = renderables().any { hit(pattern, it) }

    /**
     * Nút khớp [pattern] theo TÊN hoặc theo NHÓM diễn hoạt (`extras.grp`): nắp cốp cắt từ thân ([Car3dLid]) giữ tên gốc
     * (`body_paint`…) để biến thể sơn/đèn theo tên vẫn đúng, còn neo/diễn hoạt cốp tìm nó qua nhóm `trunk`.
     */
    private fun hit(pattern: String, e: Int): Boolean =
        Car3dNames.matches(pattern, nameOf(e)) || Car3dNames.matches(pattern, groups.getOrPut(e) { Car3dBox.group(asset?.getExtras(e)) })

    private val groups = HashMap<Int, String?>()
    private val ownBox = HashMap<Int, FloatArray?>()

    /** [posed] = hộp theo tư thế HIỆN TẠI (nắp cốp đang mở: góc hộp quay theo bản lề) — đích chạm đi theo bộ phận. */
    private fun nodeBox(pattern: String, posed: Boolean = false): Pair<V3, V3>? {
        val rm = engine.renderableManager
        val mn = DoubleArray(3) { Double.MAX_VALUE }; val mx = DoubleArray(3) { -Double.MAX_VALUE }
        val box = Box(); var hit = false
        val cBuf = FloatArray(3); val hBuf = FloatArray(3)
        for (e in renderables()) {
            if (!hit(pattern, e)) continue
            // Hộp RIÊNG của bộ phận (extras do Car3dGlb ghi); hộp của Filament = min/max của accessor CHUNG ⇒ mọi bộ phận
            // cùng bộ đỉnh trùng hộp [ĐO máy ảo 13/10]. Không có extras (tệp glTF khác) ⇒ hộp của Filament.
            // ⚠ KHÔNG gọi `Box.set(...)`: [ĐO máy ảo 13/10 19:43] `Box.set` → native `nSet` abort cả tiến trình HOME
            // ("JNI DETECTED ERROR … invalid jobject") — lỗi CheckJNI không bắt được bằng try/catch. Tính tâm/nửa cạnh bằng tay.
            val own = ownBox.getOrPut(e) { Car3dBox.parse(asset?.getExtras(e)) }   // POLISH2: một lần/thực thể (đích cốp đi theo nắp mỗi khung)
            val c: FloatArray; val h: FloatArray
            if (own != null) {
                c = cBuf; h = hBuf
                for (k in AXES) { c[k] = (own[k] + own[k + 3]) / 2f; h[k] = (own[k + 3] - own[k]) / 2f }
            } else {
                rm.getAxisAlignedBoundingBox(rm.getInstance(e), box)
                c = box.getCenter(cBuf); h = box.getHalfExtent(hBuf)
            }
            // Hộp NGHỈ trong khung mô hình: không cộng biến đổi cục bộ của nút (kính đang trượt) — nút GLB không có biến đổi riêng.
            val pose = if (posed) swingOf[e] else null
            repeat(CORNERS) { i ->
                var p = floatArrayOf(c[0] + h[0] * (if (i and 1 == 0) -1 else 1), c[1] + h[1] * (if (i and 2 == 0) -1 else 1), c[2] + h[2] * (if (i and 4 == 0) -1 else 1))
                if (pose != null) pose.first.apply(V3(p[0].toDouble(), p[1].toDouble(), p[2].toDouble()), pose.second).let { q -> p = floatArrayOf(q.x.toFloat(), q.y.toFloat(), q.z.toFloat()) }
                for (k in AXES) {
                    val w = p[k].toDouble()
                    if (w < mn[k]) mn[k] = w; if (w > mx[k]) mx[k] = w
                }
            }
            hit = true
        }
        return if (!hit) null else V3(mn[0], mn[1], mn[2]) to V3(mx[0], mx[1], mx[2])
    }

    /**
     * Kính TỤT theo % mở (owner 13/10 *"kính cửa sổ có chuyển động mở không?"*): mọi bộ phận khớp [pattern] (bản đóng + bản cửa
     * mở) dịch xuống [fraction] × chiều cao kính (trục đứng mô hình, biến đổi cục bộ của nút — gốc vẫn mang phép đặt). Kính
     * nằm TRONG tấm cửa ⇒ phần tụt xuống bị tấm cửa che như xe thật. Trả `true` nếu có bộ phận nhận.
     */
    fun slide(pattern: String, fraction: Float): Boolean {
        var hit = false
        for (e in renderables()) {
            if (!Car3dNames.matches(pattern, nameOf(e))) continue
            val b = Car3dBox.parse(asset?.getExtras(e)) ?: continue
            slideOf[e] = -fraction.coerceIn(0f, 1f) * (b[4] - b[1]) * GLASS_TRAVEL
            compose(e); hit = true
        }
        return hit
    }

    /**
     * Cánh cửa/nắp cốp đang mở dở: mọi bộ phận khớp [pattern] CÓ bản lề (`extras.hinge` — [Car3dHinge], suy từ cặp đóng/mở)
     * quay tỉ lệ [s] góc mở. Trả `true` nếu có bộ phận nhận (không có bản lề ⇒ chủ đổi bản ngay, không diễn hoạt).
     */
    fun swing(pattern: String, s: Float): Boolean {
        var hit = false
        for (e in renderables()) {
            if (!hit(pattern, e)) continue
            val h = hingeOf(e) ?: continue
            swingOf[e] = h to s
            compose(e); hit = true
        }
        return hit
    }

    fun hasHinge(pattern: String): Boolean = renderables().any { hit(pattern, it) && hingeOf(it) != null }

    private fun hingeOf(e: Int): Car3dHinge.Hinge? = hinges.getOrPut(e) { Car3dHinge.parse(asset?.getExtras(e)) }

    /** Biến đổi cục bộ = bản lề(s) · dịch kính(dy) — kính trên cánh cửa vừa tụt vừa quay theo cửa. */
    private fun compose(e: Int) {
        val dy = slideOf[e] ?: 0f
        val m = swingOf[e]?.let { (h, s) -> h.matrix(s) } ?: floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        // M · T(0,dy,0): cột 4 += dy · cột 2.
        m[12] += dy * m[4]; m[13] += dy * m[5]; m[14] += dy * m[6]
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(e), m)
    }

    private val slideOf = HashMap<Int, Float>()
    private val swingOf = HashMap<Int, Pair<Car3dHinge.Hinge, Float>>()
    private val hinges = HashMap<Int, Car3dHinge.Hinge?>()

    /** Đặt gốc mô hình (ma trận cột-chính từ [Car3dOrient.Placement.matrix]). */
    fun place(matrix: FloatArray) {
        val a = asset ?: return
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(a.root), matrix)
    }

    /** Hiện/ẩn bộ phận theo [match] (tên → hiện?; `null` = không đụng). Trả `true` nếu có đổi (chủ xin vẽ lại). */
    fun showByName(match: (String?) -> Boolean?): Boolean {
        var changed = false
        for (e in renderables()) {
            val want = match(nameOf(e)) ?: continue
            val isHidden = e in hidden
            if (want && isHidden) { scene.addEntity(e); hidden -= e; changed = true }
            if (!want && !isHidden) { scene.remove(e); hidden += e; changed = true }
        }
        return changed
    }

    fun setCamera(c: Car3dCamera) {
        if (width <= 0 || height <= 0) return
        camera.setProjection(c.fovDeg, width.toDouble() / height, c.near, c.far, Camera.Fov.VERTICAL)
        val e = c.eye; val t = c.target
        camera.lookAt(e.x, e.y, e.z, t.x, t.y, t.z, 0.0, 1.0, 0.0)
    }

    /** Một khung. `false` = chưa vẽ được (bề mặt chưa sẵn / Filament bỏ khung) ⇒ chủ thử lại khung sau. */
    fun render(frameTimeNanos: Long): Boolean {
        val s = swap ?: return false
        if (!ui.isReadyToRender) return false
        if (!renderer.beginFrame(s, frameTimeNanos)) return false
        renderer.render(view)
        renderer.endFrame()
        return true
    }

    override fun onNativeWindowChanged(surface: Surface) {
        swap?.let { engine.destroySwapChain(it) }
        swap = engine.createSwapChain(surface, ui.swapChainFlags)
    }

    override fun onDetachedFromSurface() {
        swap?.let { engine.destroySwapChain(it); engine.flushAndWait() }
        swap = null
    }

    override fun onResized(w: Int, h: Int) {
        width = w; height = h
        view.viewport = Viewport(0, 0, w, h)
        android.util.Log.i("Car3d", "surface ${w}x$h px")
        onSize(w, h)
    }

    /**
     * Trả hết GPU, MỘT lần (chủ bỏ tham chiếu ngay sau). Thứ tự: bề mặt → tài sản (thực thể + vật liệu con) → bộ cấp vật liệu
     * gltfio → đèn/nền → renderer/view/scene/camera → Engine. `Engine.destroy` nằm trong `finally`: một bước giữa chừng ném lỗi
     * Java thì Engine VẪN được huỷ — Engine tự giải phóng mọi tài nguyên người dùng còn sót (tài liệu `Engine.destroy` 1.77:
     * *"frees any user-allocated resources not already destroyed (logs a warning)"*), không giữ ngữ cảnh GL + luồng driver mồ côi.
     */
    fun destroy() {
        try {
            ui.detach()
            swap?.let { engine.destroySwapChain(it) }; swap = null
            asset?.let { loader.destroyAsset(it) }; asset = null
            materials.destroyMaterials(); materials.destroy()
            resources.destroy(); loader.destroy()
            lights.forEach { engine.destroyEntity(it); EntityManager.get().destroy(it) }
            ibl?.let { engine.destroyIndirectLight(it) }
            grading?.let { engine.destroyColorGrading(it) }
            engine.destroyRenderer(renderer); engine.destroyView(view); engine.destroyScene(scene)
            engine.destroyCameraComponent(camEntity); EntityManager.get().destroy(camEntity)
        } finally {
            engine.destroy()
        }
    }

    private fun renderables(): IntArray = asset?.renderableEntities ?: IntArray(0)

    private fun nameOf(e: Int): String? {
        val a = asset ?: return null
        a.getName(e)?.takeIf { it.isNotEmpty() }?.let { return it }
        val tm = engine.transformManager
        val parent = tm.getParent(tm.getInstance(e))
        return if (parent != 0) a.getName(parent) else null
    }

    companion object {
        /**
         * Dựng bộ vẽ. Soát Pass 2: hàm dựng ném lỗi giữa chừng (vật liệu/IBL/`attachTo`) ⇒ trước đây Engine (ngữ cảnh GL + luồng
         * driver) mồ côi — mỗi lần widget được dựng lại (đổi hồ sơ…) lại rò một Engine. Nay huỷ Engine rồi ném tiếp (chủ lùi 2D).
         * An toàn: `ui.attachTo` là bước CUỐI của hàm dựng ⇒ hỏng trước đó thì chưa có callback bề mặt nào trỏ vào Engine.
         */
        fun create(tv: TextureView, medium: Boolean, night: Boolean, onSize: (Int, Int) -> Unit): Car3dRenderer {
            val e = Engine.create()
            return try { Car3dRenderer(e, tv, medium, night, onSize) } catch (t: Throwable) { runCatching { e.destroy() }; throw t }
        }

        /** Nạp thư viện native một lần/tiến trình; hỏng ⇒ `false` (widget lùi 2D). */
        @Volatile private var ready: Boolean? = null
        fun init(): Boolean = ready ?: synchronized(this) {
            ready ?: runCatching { Filament.init(); Gltfio.init(); true }.getOrElse { false }.also { ready = it }
        }

        /** [ĐO máy ảo 13/10] 30k/90k/30k ⇒ thân xe trắng "cháy", mất nếp gấp ⇒ hạ còn ~60 %. */
        const val IBL_LUX = 20_000f
        const val SUN_LUX = 55_000f
        const val FILL_LUX = 15_000f
        const val BOUNCE_LUX = 25_000f
        const val NIGHT_K = 0.7f
        const val MSAA_SAMPLES = 4
        /** Kính mở hẳn tụt chừng này × chiều cao kính (chừa mép trên trong gioăng như kính thật hạ hết). */
        const val GLASS_TRAVEL = 0.92f
        /** Vật liệu sơn của lưới AVM (bộ đỉnh `paint` + `open` — cánh cửa mở). */
        private val PAINT_MATERIALS = setOf("paint", "open")
        private const val INTERIOR = "interior"
        private const val EMISSIVE = "emissiveFactor"
        /** Ánh ấm của đèn đọc trên nội thất (tuyến tính, × ảnh nội thất). R28 [ĐO máy ảo 13/10]: 0,22/0,15/0,06 (POLISH2) KHÔNG đổi một điểm ảnh nào ở khoang qua kính (phơi sáng nhân phát sáng ≈ 0,1) ⇒ ×12. */
        private val GLOW = floatArrayOf(5.2f, 3.6f, 1.5f)
        /** Ba trục x/y/z · 8 đỉnh hộp bao (không viết khoảng số cứng — bài `GridSeamGuardTest`). */
        private val AXES = IntRange(0, 2)
        private const val CORNERS = 8
    }
}
