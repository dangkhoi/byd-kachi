package com.byd.clusternav.launcher

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.byd.clusternav.modules.clustercast.ClusterProfile
import com.byd.clusternav.system.PackageQueries
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * ═══ 3.01 R25 — nguồn mô hình cho widget xe 3D (chỉ mô hình THẬT của xe — không mô hình mẫu) ════════════════════
 *
 * 1. **Bản phát hành** — `car.mesh` + `wheel.mesh` + ảnh vật liệu + `car_body_color.csv` trong `assets/` của app AVM hệ thống
 *    `com.byd.avc` (`/system/app/AutoVideo/AutoVideo.apk`, đọc được từ uid thường [ĐO xe 13/10 `08-oem-apk-read.txt`]),
 *    đọc LÚC CHẠY qua `createPackageContext(...).assets` (+ `<queries>` trong manifest) — APK Kachi không chở byte nào của BYD.
 *    Thư mục theo `car.type` ([BydAvmModels], bảng chép từ mã AVM).
 * 2. **Bản `vehicleTest`** — thêm một chỗ: `assets/oem-3d/<group>/CarModel/<folder>/…` của CHÍNH app (chỉ có trong
 *    `src/vehicleTest`, `.gitignore`; `OemCarArtSourceSetContractTest` canh) để thử trên máy ảo không có gói BYD.
 * Không có cả hai / tệp hỏng ⇒ `null` ⇒ widget lùi bảng 2D (không sập, không mô hình giả).
 *
 * Lưới → `.glb` trong bộ nhớ ([Car3dGlb]) một lần, ghi đệm `cacheDir` (khoá = nguồn + phiên bản gói + đường dẫn); lần sau
 * ánh xạ tệp đệm thẳng thành đệm trực tiếp (không qua heap Java). Chạy trên luồng NỀN.
 */
object Car3dModelSource {

    /** [paints] = các màu sơn của đời xe ([Car3dPaint.presets] trên `car_body_color.csv`), rỗng nếu tệp vắng. */
    class Loaded(val model: Car3dModel, val glb: ByteBuffer, val origin: String, val paints: List<Car3dPaint.Preset>)

    /**
     * MỘT lượt nạp/dựng mỗi lúc (soát Pass 2): view rời rồi gắn lại cửa sổ khi lượt dựng đầu (~0,5 s, lưới nén → GLB) còn chạy
     * ⇒ trước đây hai luồng cùng ghi `car3d-src.tmp` (mở = CẮT tệp mà luồng kia đang ánh xạ ⇒ SIGBUS) + cùng ngốn heap 48 MB.
     * Luồng sau chờ, rồi ánh xạ tệp đệm luồng trước vừa ghi. Chỉ chạy trên luồng nền.
     */
    fun load(ctx: Context): Loaded? = synchronized(lock) { loadLocked(ctx) }

    private val lock = Any()

    /** Bỏ tệp đệm (gltfio từ chối tệp đệm ⇒ lần gắn sau dựng lại thay vì lùi 2D vĩnh viễn với một tệp hỏng). */
    fun invalidate(ctx: Context) = synchronized(lock) { purge(ctx) }

    /** Xoá mọi tệp của widget trong `cacheDir` ([Car3dCache.owned]) — gọi trong [lock]. */
    private fun purge(ctx: Context) {
        ctx.cacheDir.listFiles { f -> Car3dCache.owned(f.name) }?.forEach { Log.i(TAG, "xoá ${it.name} (${it.length()} B)"); it.delete() }
    }

    private fun loadLocked(ctx: Context): Loaded? {
        for (m in Car3dCatalog.ORDER) {
            val r = runCatching { when (m.source) { Car3dSource.BydAvm -> fromAvm(ctx, m) } }
                .onFailure { Log.w(TAG, "nguồn ${m.id} hỏng — lùi 2D", it) }.getOrNull()
            if (r != null) { Log.i(TAG, "model=${m.id} origin=${r.origin} bytes=${r.glb.capacity()}"); return r }
        }
        Log.w(TAG, "không nguồn mô hình nào nạp được ⇒ bảng 2D")
        return null
    }

    @Suppress("UNUSED_PARAMETER")
    private fun fromAvm(ctx: Context, generic: Car3dModel): Loaded? {
        val src = source(ctx) ?: return null
        // Phần chung + phần riêng ĐÃ ĐO của nhóm thư mục AVM (bảng đèn số, neo đèn/cốp) — dữ liệu ở `:core`, không `if` theo xe.
        val m = Car3dCatalog.forAvm(src.entry.group)
        fun path(name: String) = src.path(name)
        val paints = Car3dPaint.withExtras(readPaints(src), m.extraPaints)
        val cache = File(ctx.cacheDir, "car3d-${(src.key + path("car.mesh") + CACHE_VERSION).hashCode().toUInt()}.glb")
        if (cache.isFile && cache.length() > 0) return Loaded(m, map(cache), "cache:${cache.name}", paints)
        // Một bản đệm duy nhất: bản của khoá cũ (đổi phiên bản AVM / CACHE_VERSION) + bản dở/bản chép của lần dựng bị giết
        // giữa chừng ([Car3dCache.owned]) bỏ TRƯỚC khi dựng (trong [lock] ⇒ không luồng nào đang dùng chúng).
        purge(ctx)
        val t0 = System.nanoTime()
        val assets = src.assets
        val body = BydAvmMesh.parse(native(ctx, assets, path("car.mesh")))
        val wheels = runCatching { BydAvmMesh.parse(native(ctx, assets, path("wheel.mesh"))) }
            .onFailure { Log.w(TAG, "wheel.mesh không đọc được — xe không bánh", it) }.getOrNull()
        val merged = wheels?.let { body + it } ?: body
        // Bộ phận GỘP (bản sao hợp của cả cánh cửa mở — `doorlf_open` Seal) phủ lớp đen lên cửa mở ⇒ bỏ ([Car3dDupes], đo, không theo tên).
        val dupes = Car3dDupes.aggregates(merged)
        val mesh0 = Car3dDupes.without(merged, dupes)
        Log.i(TAG, "bỏ bộ phận gộp: $dupes")
        // Kiểm hợp lý (hộp thân · 4 bánh trong hốc · neo kính) TRƯỚC khi ghi tệp đệm: trượt ⇒ bảng 2D + log phép kiểm hỏng.
        // Tệp đệm chỉ được ghi khi qua ⇒ lần sau nạp từ đệm không cần kiểm lại (khoá đệm gồm CACHE_VERSION).
        val verdict = Car3dSanity.check(mesh0, m)
        Log.i(TAG, "sanity ${m.id} ${src.entry.group}: ${if (verdict.ok) "OK" else "FAIL ${verdict.failures}"} · ${verdict.notes}")
        if (!verdict.ok) return null
        val t1 = System.nanoTime()
        val tex = (m.materials.values.mapNotNull { it.texture }).distinct().mapNotNull { name ->
            runCatching { assets.open(path(name)).use { it.readBytes() } }.getOrNull()?.let { name to it }
        }.toMap()
        // Sơn trong GLB = màu mặc định của AVM; màu người dùng chọn áp LÚC VẼ ([Car3dRenderer.paint]) — đổi màu không dựng lại GLB.
        val paint = Car3dPaint.pick(paints, null)?.style
        val styles = if (paint == null) m.materials else m.materials + mapOf("paint" to paint, "open" to paint)
        val tmp = File(ctx.cacheDir, cache.name + ".part")
        // Đệm kết quả = tệp đệm ánh xạ GHI (native, không heap — trần heap 48 MB [ĐO máy ảo]); xong thì đổi tên thành tệp đệm.
        // Bản lề suy từ cặp đóng/mở (cửa, cốp) — bên vẽ diễn hoạt mở/đóng thay vì đổi bản ngay.
        // Nắp cốp: cặp đóng/mở của tệp, hoặc nhóm `trunk_*`, hoặc CẮT từ thân theo hình học đo ([Car3dLid]).
        val lid = Car3dLid.of(mesh0)
        val mesh = lid?.mesh ?: mesh0
        Log.i(TAG, "nắp cốp: ${lid?.let { "${it.lid.size} bộ phận · bản lề ${it.hinge.pivot}" } ?: "theo cặp đóng/mở của tệp hoặc không có"}")
        val hinges = Car3dHinge.fitAll(mesh)
        val lamps = Car3dLamps.styles(mesh, m.whiteLamps)
        Log.i(TAG, "hinges ${hinges.size}: ${hinges.entries.take(HINGE_LOG).joinToString { (k, h) -> "$k %.0f° rms %.4f".format(Math.toDegrees(h.angle), h.rms) }}")
        val glb = Car3dGlb.write(mesh, styles, tex, m.partStyles + lamps, m.uvOriginBottom, hinges, lid) { n ->
            // Tệp THƯA: hết chỗ lúc ghi trang ánh xạ = SIGBUS (HOME sập). Thiếu chỗ ⇒ ném ⇒ [load] trả `null` ⇒ bảng 2D.
            val usable = ctx.cacheDir.usableSpace
            if (!Car3dCache.roomFor(n.toLong(), usable)) throw IOException("cacheDir thiếu chỗ: cần $n B, còn $usable B")
            RandomAccessFile(tmp, "rw").use { f -> f.setLength(n.toLong()); f.channel.map(FileChannel.MapMode.READ_WRITE, 0, n.toLong()) }
        }
        (glb as? java.nio.MappedByteBuffer)?.force()
        if (!tmp.renameTo(cache)) { tmp.delete(); Log.w(TAG, "không ghi được tệp đệm ${cache.name}") }
        Log.i(TAG, "AVM ${path("car.mesh")}: ${mesh.buffers.size} bộ đỉnh ${mesh.buffers.sumOf { it.count }} đỉnh · ${mesh.parts.size} bộ phận · " +
            "${tex.size} ảnh · ${paints.size} màu sơn · glb ${glb.capacity()} B · " +
            "đọc+phân tích ${(t1 - t0) / 1_000_000} ms · dựng ${(System.nanoTime() - t1) / 1_000_000} ms")
        Log.i(TAG, "bộ phận: ${mesh.parts.joinToString(",") { it.name }}")
        return Loaded(m, glb, "avm:${path("car.mesh")}", paints)
    }

    /** Nguồn tệp của đời xe này: gói AVM hệ thống, không có thì bản đóng gói vehicleTest; không có cả hai ⇒ `null`. */
    private fun source(ctx: Context): Src? {
        val type = runCatching { ClusterProfile.carType(ctx) }.getOrNull()
        val entry = BydAvmModels.of(type)
        val sys = entry?.let { runCatching { ctx.createPackageContext(BydAvmModels.PACKAGE, 0).assets }.getOrNull() }
            ?.takeIf { exists(it, entry.meshPath) }
        return when {
            sys != null && entry != null -> Src(sys, entry, "", "${BydAvmModels.PACKAGE}:${pkgVersion(ctx)}")
            else -> bundled?.invoke(ctx)?.let { am -> bundledEntry(am, entry)?.let { Src(am, it, BUNDLED, "bundled") } } ?: run {
                Log.i(TAG, "AVM: car.type=$type entry=${entry?.meshPath} — không có gói ${BydAvmModels.PACKAGE} / bản đóng gói"); null
            }
        }
    }

    private fun readPaints(src: Src): List<Car3dPaint.Preset> =
        Car3dPaint.presets(runCatching { src.assets.open(src.path("car_body_color.csv")).use { String(it.readBytes()) } }.getOrNull())

    /**
     * Màu sơn của đời xe cho hàng "Màu xe" trong Cài đặt (đọc tệp CSV ~300 B, nhớ lại sau lần đầu). Rỗng ⇒ không có mô hình 3D
     * cho xe này ⇒ Cài đặt ẩn hàng. Gọi được trên luồng chính (một tệp nhỏ trong tài sản).
     */
    fun paints(ctx: Context): List<Car3dPaint.Preset> =
        paintCache ?: (source(ctx)?.let { Car3dPaint.withExtras(readPaints(it), Car3dCatalog.forAvm(it.entry.group).extraPaints) } ?: emptyList()).also { paintCache = it }

    @Volatile private var paintCache: List<Car3dPaint.Preset>? = null

    private data class Src(val assets: AssetManager, val entry: BydAvmModels.Entry, val prefix: String, val key: String) {
        fun path(name: String) = prefix + entry.file(name).removePrefix(if (prefix.isEmpty()) "" else "resource_pi/")
    }

    /**
     * Móc CHỈ bản `vehicleTest` gán (`OemCarArtTestProvider`): assets của chính app có `oem-3d/`. Bản phát hành để `null` ⇒
     * chỉ đọc từ gói hệ thống (bài `OemCarArtSourceSetContractTest` canh: không mã phát hành nào gán móc này).
     */
    @Volatile var bundled: ((Context) -> AssetManager?)? = null

    /** Bản đóng gói vehicleTest: dòng của đời xe nếu có tệp, không thì dòng Seal (138) — máy ảo không có `car.type`. */
    private fun bundledEntry(am: AssetManager, e: BydAvmModels.Entry?): BydAvmModels.Entry? {
        fun has(x: BydAvmModels.Entry) = exists(am, BUNDLED + x.meshPath.removePrefix("resource_pi/"))
        return e?.takeIf(::has) ?: BydAvmModels.of(FALLBACK_TYPE)?.takeIf(::has)
    }

    private fun exists(a: AssetManager, path: String) = runCatching { a.open(path).close(); true }.getOrDefault(false)

    private fun pkgVersion(ctx: Context): Long =
        PackageQueries.packageInfo(ctx.packageManager, BydAvmModels.PACKAGE)?.longVersionCode ?: 0L

    /**
     * Tài sản [path] thành đệm NATIVE (ánh xạ tệp — không tính vào heap Java; trên ART `allocateDirect` VẪN nằm trong heap).
     * Tài sản lưu thẳng (không nén) ⇒ ánh xạ ngay vùng của nó trong APK; nén (car.mesh của AutoVideo.apk là `Defl:N` [ĐO
     * `unzip -lv`]) ⇒ chép luồng ra tệp tạm trong `cacheDir` theo từng mảnh 64 KB, ánh xạ, xoá tên tệp (vùng ánh xạ vẫn sống).
     */
    private fun native(ctx: Context, assets: AssetManager, path: String): ByteBuffer {
        runCatching { assets.openFd(path) }.getOrNull()?.use { fd ->
            return java.io.FileInputStream(fd.fileDescriptor).channel
                .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.length).order(ByteOrder.LITTLE_ENDIAN)
        }
        val tmp = File(ctx.cacheDir, "${Car3dCache.PREFIX}src.tmp")
        try {
            assets.open(path).use { input -> tmp.outputStream().use { out -> input.copyTo(out, CHUNK) } }
            return map(tmp)
        } finally { tmp.delete() }
    }

    /** Ánh xạ tệp đệm thành đệm trực tiếp (gltfio đọc thẳng, không chép vào heap). */
    private fun map(f: File): ByteBuffer = RandomAccessFile(f, "r").use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length()) }

    /** Thư mục lưới BYD trong assets của bản vehicleTest (dưới nó là `<group>/CarModel/<folder>/car.mesh`). */
    const val BUNDLED = "oem-3d/"
    private const val FALLBACK_TYPE = "138"
    /** Tăng khi đổi cách dựng GLB / bảng vật liệu ⇒ đệm cũ tự bỏ. */
    private const val CACHE_VERSION = "v17"   // v17: mâm phát sáng nền 0,8 (mép dưới không tối; 1,5 cháy trắng) · v16: thử 1,5 · v15: lòng cốp một màu đen sâu · v14: dải sau khoang cũng ẩn kết cấu trong · v13: khoang quấn đúng pháp tuyến · v12: khoang: ẩn theo trọng tâm trong lòng vách · dải sàn sau hẹp · v11: khoang sạch (ẩn kết cấu trong theo đo) · mâm điện môi · v10: khoang cốp lùi vào trong vỏ nắp · v9: nội thất khai phát sáng (đèn đọc) · v8: nắp cốp theo khe tấm + khoang · nẹp metal đen · v7: bỏ bộ phận gộp · nắp cốp · vai đèn + phát sáng theo ảnh · v6: lật UV · mipmap · bảng đèn AVM · kiểm hợp lý · vật liệu theo khai báo · bản lề · phát sáng
    private const val HINGE_LOG = 12
    private const val CHUNK = 64 * 1024
    private const val TAG = "Car3d"
}
