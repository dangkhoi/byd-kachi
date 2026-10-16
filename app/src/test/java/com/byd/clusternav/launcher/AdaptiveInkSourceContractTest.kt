package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1.6 R29 (làm lại 15/10) — hợp đồng NGUỒN của mực thích ứng. Owner 15/10: *"cái làm tương phản chữ bằng cách vá 1 mớ text
 * background nhìn rất dị hợm… phải làm đổi màu chữ tự động, đồng đều, chứ không vá thế này được em, rất tởm"*.
 *
 * Khoá ba điều: (1) KHÔNG còn lớp đỡ / đệm nền nào dưới phần tử ở bất kỳ đâu trong mã chính (`:app` + `:core`) — đỏ ngay khi ai đó
 * thêm lại; (2) mỗi mặt MỘT họ: mọi chỗ tô của [AdaptiveInk] lấy mực từ họ của mặt gốc, không giải theo từng phần tử; (3) chỗ đã
 * nối vẫn nối (CLAUDE.md §8 — hàm mới phải có call site).
 */
class AdaptiveInkSourceContractTest {

    private val dir = "src/main/java/com/byd/clusternav/launcher/"
    private fun code(f: String) = SourceRoots.codeOf(dir + f)

    /** Mọi tệp `.kt` của mã chính (`:app`, `:core`, `:car-integration`), ĐÃ bỏ chú thích. */
    private val main: Map<String, String> by lazy {
        SourceRoots.moduleSourceRoots().flatMap { root -> root.toFile().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .associate { it.path to KotlinSource.stripComments(it.readText()) }
    }

    @Test
    fun `khong con lop do nao - InkHalo, HaloLayer, haloColor, lop do cham deu bang 0 trong ma chinh`() {
        assertTrue(main.size > 200, "quét quá ít tệp: ${main.size}")
        val banned = Regex("(?i)halo|InkCandidate|reinforced|ContrastInk\\.resolve|placeHalo|overlayHalo|AdaptiveInk\\.base\\(")
        val hits = main.flatMap { (f, s) -> banned.findAll(s).map { "${f.substringAfterLast('/')}: ${it.value}" }.toList() }
        assertEquals(emptyList<String>(), hits, "lớp đỡ/đệm nền theo phần tử đã bị owner bác 15/10 — không được quay lại")
    }

    @Test
    fun `AdaptiveInk khong dat nen len phan tu - chi doi mau muc`() {
        val ai = code("AdaptiveInk.kt")
        for (t in listOf("background", "setBackground", "GradientDrawable", "LayerDrawable", "Drawable(")) assertFalse(ai.contains(t), "AdaptiveInk chạm nền: $t")
        // Bộ vẽ canvas: không tham số/lớp đỡ trong ba hình vẽ; chấm chỉ nhận họ.
        val painter = code("StateDotPainter.kt")
        for (fn in listOf("fun draw(canvas", "private fun drawButton(", "fun drawLamp(")) assertFalse(SourceRoots.body(painter, fn).contains("halo"), fn)
        assertTrue(painter.contains("var family: InkFamily?"))
        // Nút đĩa: không còn "đĩa đậm thêm" theo từng nút; công thức theo HỌ.
        val btn = code("KachiButtonInks.kt")
        assertTrue(btn.contains("fun of(p: KachiPalette, f: InkFamily, alt: Int = 0): KachiButtonInks"))
        assertTrue(btn.contains("override val group get() = KachiButtonInks"), "mọi nút của một mặt CHUNG một kiểu đĩa")
        assertTrue(SourceRoots.body(code("AdaptiveInk.kt"), "private fun decide(").contains("probes.indices.groupBy { probes[it].group ?: probes[it] }"))
        assertFalse(btn.contains("under: ContrastBackdrop): KachiButtonInks"), "công thức nút theo nền TỪNG nút đã gỡ")
    }

    @Test
    fun `moi mat mot ho - to muc lay ho cua mat goc, chon ho qua ContrastInk choose`() {
        val ai = code("AdaptiveInk.kt")
        val refresh = SourceRoots.body(ai, "private fun refresh(")
        assertTrue(refresh.contains("val f = rs.family.coerceAtLeast(0)"), "một họ cho cả mặt gốc")
        assertTrue(refresh.contains("paint(it.view, it.mark, resolve(it.mark.ground!!, it.mark.key, inkRole(it.view, it.mark.key), f, fams[f]), fams[f])"), "mọi phần tử tô bằng CÙNG họ")
        // Nền tệ nhất đo dưới CHÍNH vùng từng phần tử (không lấy cả vùng thẻ — [ĐO máy ảo 15/10] cả thanh trên kẹt điểm 0,22).
        assertTrue(refresh.contains("groundOf(v, if (v === rideNow) h else v, m, fb)"))
        assertTrue(SourceRoots.body(ai, "private fun decide(").contains("val g = it.mark.ground ?: continue"))
        assertEquals(1, Regex("fun decide\\(").findAll(ai).count())
        val decide = SourceRoots.body(ai, "private fun decide(")
        assertTrue(decide.contains("ContrastInk.choose(scores, rs.family, pass)"), "trễ: họ đang hiện đi vào phép chọn")
        assertTrue(decide.contains("p.apply(fams[choice.family], alt[choice.family][i])"), "bộ vẽ tự tô nhận CÙNG họ")
        assertTrue(decide.contains("if (!choice.ok && (prev == null || prev.family != choice.family || prev.ok))") && decide.contains("logW(\"dưới sàn: mặt"), "dưới sàn ⇒ log (fail-loud)")
        // Đường một phần tử (nhịp 1 Hz) tô theo họ của mặt gốc, phần tử mới ⇒ chọn lại họ cho cả mặt (không tự chọn riêng).
        val one = SourceRoots.body(ai, "private fun inkUnsafe(")
        assertTrue(one.contains("val f = root.second.family") && one.contains("inkSlot(v, key, role) in root.second.seen"))
        // R29-RESOLVE-STORM: lời tô đi qua cổng THUẦN (khoá ở `InkSolveGateTest`); phần tử/mặt chưa đo được ⇒ không chọn họ;
        // lượt chọn của lời tô đi qua bộ gộp theo mặt gốc; mọi lượt chọn đếm theo kẻ kích.
        assertTrue(one.contains("InkSolveGate.onInk(g != null, inkMeasurable(v) && inkMeasurable(host.first) && inkMeasurable(root.first), stale, chosen,"))
        assertTrue(one.contains("InkSolveGate.Ink.DEFER -> ContrastLedger.deferred.incrementAndGet()"))
        assertTrue(one.contains("InkSolveGate.Ink.SOLVE -> if (coalesce.request(root.first))"))
        assertTrue(one.contains("mark.solvedOn === g") && one.contains("inkShown(v, mark, last)"), "cùng nền + vai + họ ⇒ không giải/tô lại")
        assertEquals(1, Regex("""refresh\(root\.first, root\.second, force = stale""").findAll(one).count(), "lời tô chỉ chọn họ qua bộ gộp")
        assertTrue(SourceRoots.body(ai, "private fun decide(").contains("ContrastLedger.trigger(why)"))
        // [ĐO máy ảo 15/10] lượt ÉP (`measure(v, s, true)` sau mỗi lần chọn — nút đĩa) bỏ qua `moved` ⇒ `loc` cũ của view KHÁC ⇒ 7 nút
        // hàng chân 3D cùng nhận vùng [917,983] ⇒ lượt sự kiện kế thấy "dời" ⇒ chọn lại ~1/giây mãi. Vùng phải đọc lại chỗ thật.
        for (fn in listOf("private fun measure(", "private fun groundOf(")) {
            val b = SourceRoots.body(ai, fn)
            val at = b.indexOf("getLocationInWindow(loc)")
            assertTrue(at > 0 && at < b.indexOf("rect.set(loc[0]"), "$fn: đọc chỗ thật TRƯỚC khi ghi vùng")
        }
        // Đoạn màu cùng họ với chữ chứa nó.
        assertTrue(SourceRoots.body(ai, "private fun paint(").contains("InkModel.ink(k, fam)"))
        // Họ: thứ tự + nâng chữ phụ cùng cực.
        val model = code("AdaptiveInkModel.kt")
        assertTrue(SourceRoots.body(model, "fun families(").contains("listOf(InkFamily(p, false), InkFamily(p, true), InkFamily(o, false), InkFamily(o, true))"))
    }

    @Test
    fun `pham vi mat - the, thanh tren, thanh nut, lop phu 3D, o nut la mat goc - chip, pill, dia, vung nhan la lop`() {
        assertTrue(code("WallGlass.kt").let { Regex("AdaptiveInk\\.surface\\(view, listen = (true|false), scope = AdaptiveInk\\.Scope\\.CARD\\)").findAll(it).count() } == 2)
        assertTrue(code("ControlDockView.kt").contains("AdaptiveInk.fill(this) { intArrayOf(c(KachiTheme.BAR)) }"), "thanh nút = CARD (mặc định)")
        assertTrue(code("KachiTopStrip.kt").contains("themed { AdaptiveInk.fill(strip) { intArrayOf(c(KachiTheme.BAR_TOP)) } }"))
        assertTrue(code("Car3dInk.kt").contains("scope = AdaptiveInk.Scope.OWN, variant = VARIANT"), "lớp phủ 3D = mặt gốc riêng")
        assertTrue(SourceRoots.body(code("AdaptiveInk.kt"), "fun tile(").contains("Scope.OWN"), "ô nút = mặt gốc riêng")
        assertTrue(code("Car3dInk.kt").contains("scope = AdaptiveInk.Scope.LAYER, variant = variant"), "vùng nhãn 3D = lớp")
        // Mặt đã thiết kế (pill · nút DIM) đi theo HỌ của mặt chứa nó — [ĐO máy ảo 15/10] pill màu chủ đề kẹt thanh trên ở mực sáng.
        assertTrue(code("KachiTopStrip.kt").contains("AdaptiveInk.face(v, ink, { p -> ColorMath.parse(if (primary) p.accentSoft else p.cardFill) }) { face.setColor(it) }"), "pill theo họ")
        assertTrue(code("ControlTileFactory.kt").contains("AdaptiveInk.face(this, KachiTheme.INK, { p -> ColorMath.parse(p.dim) }) { face.setColor(it) }"), "DIM theo họ")
        val face = SourceRoots.body(code("AdaptiveInk.kt"), "fun face(")
        assertTrue(face.contains("scope = Scope.LAYER") && face.contains("Layer.Flat(fill(f.palette))") && face.contains("faces[view] = f.palette"))
        assertTrue(code("Car3dHeroLayer.kt").contains("AdaptiveInk.fill(v, AdaptiveInk.Scope.LAYER, fades = false, variant = disc)"))
        assertTrue(code("KachiButtonInks.kt").contains("scope = AdaptiveInk.Scope.LAYER, variant = ADAPT, under = true"))
        // Mặt gốc: OWN gần nhất, không có thì CARD ngoài cùng.
        val root = SourceRoots.body(code("AdaptiveInk.kt"), "private fun rootOf(")
        assertTrue(root.contains("if (s.scope == Scope.OWN) return") && root.contains("return card ?: outer"))
    }

    @Test
    fun `bo ve tu to - nut dia va cham 3D gop diem vao ho cua mat, theo su kien`() {
        val btn = SourceRoots.body(code("KachiButtonInks.kt"), "fun adapt(")
        assertTrue(btn.contains("AdaptiveInk.probe(button, object : AdaptiveInk.Probe"))
        val dots = code("Car3dDotsLayer.kt")
        assertTrue(dots.contains("AdaptiveInk.probe(this, probe)") && dots.contains("inks.score(u, shapeOf(s), warn)"))
        assertTrue(dots.contains("painter.family = f"), "chấm vẽ bằng ĐÚNG họ đã chọn")
        val onDraw = SourceRoots.body(dots, "override fun onDraw(")
        assertFalse(onDraw.contains("backdropAt(") || onDraw.contains("score("), "onDraw không đo")
        assertTrue(dots.contains("if (!s.shown) continue") && dots.contains("AdaptiveInk.sameBackdrop(under, s.under)"), "chỉ điểm đang hiện, nền đổi mới đo")
        assertTrue(SourceRoots.body(code("Car3dInk.kt"), "fun settle(").contains("AdaptiveInk.rescore(host)"))
        val w = code("Car3dWidgetView.kt")
        val frame = SourceRoots.body(w, "private fun frame(")
        assertTrue(Regex("""if \(burst > 0\) \{[^\n]*Car3dInk\.settle\(this\) }""").containsMatchIn(frame), "chọn lại khi xoay DỪNG")
        assertEquals(1, Regex("Car3dInk\\.").findAll(frame).count())
        assertFalse(SourceRoots.body(code("Car3dHeroLayer.kt"), "fun project(").let { it.contains("AdaptiveInk") || it.contains("Car3dInk") }, "project chạy MỖI KHUNG")
        val all = code("AdaptiveInk.kt") + code("Car3dInk.kt") + dots
        assertFalse(all.contains("Thread("), "0 luồng")
        // perf §8.1 [ĐO máy ảo 15/10]: 0 nhịp LẶP — hẹn duy nhất là lượt chọn lại MỘT lần khi nội dung dời xong ([InkMotion]: đặt lại
        // hẹn cũ, không tích), và chỉ ở `hold`.
        assertEquals(1, Regex("postDelayed").findAll(all).count(), "chỉ một chỗ hẹn")
        assertTrue(SourceRoots.body(code("AdaptiveInk.kt"), "private fun hold(").let { it.contains("postDelayed") && it.contains("removeCallbacks") && it.contains("settling = true; try { update(root) } finally { settling = false }") })
    }

    /**
     * Khoá hồi quy perf §8.1 (`docs/diagnostics/perf-315-2026-10-14.md`): xoay xe 3D ⇒ mỗi lượt layout (~20 Hz) làm mặt khung 3D chọn
     * lại họ (~53 lượt `decide` + `prepare` của chấm mỗi đợt, luồng chính +80…+130 %). Lượt SỰ KIỆN thấy phần tử đã đo DỜI ⇒ GIỮ
     * (không đo, không chọn) và return TRƯỚC `decide`; lượt đầy đủ bỏ hẹn chờ ⇒ đúng một lượt sau khi dừng (`InkMotionTest`).
     */
    @Test
    fun `noi dung dang doi - luot su kien giu, chon lai mot lan khi dung`() {
        val ai = code("AdaptiveInk.kt")
        val refresh = SourceRoots.body(ai, "private fun refresh(")
        assertTrue(refresh.contains("val event = !force && !cascade && !rescore && !settling && rs.choice != null"), "lượt hẹn khi dừng không tự giữ lại")
        assertTrue(refresh.contains("if (!event) rs.motion?.solving()"), "lượt đầy đủ (settle) bỏ hẹn ⇒ không chọn lần hai")
        assertTrue(refresh.contains("if (event && rs.backdrop != null && fresh(rs) && moved(root, rs.rect)) { hold(root, rs); return }"), "mặt gốc dời")
        assertTrue(refresh.contains("if (event && !fb && s.backdrop != null && fresh(s) && moved(v, s.rect)) held = true"), "mặt con dời")
        assertTrue(refresh.contains("if (event && !fb && m.ground != null && moved(v, m.rect)) held = true"), "phần tử dời")
        val holdAt = refresh.indexOf("if (held) { hold(root, rs); return }")
        assertTrue(holdAt > 0 && holdAt < refresh.indexOf("decide(root, rs, items, probes, fams, seen)"), "giữ phải chặn TRƯỚC decide")
        assertTrue(SourceRoots.body(ai, "private fun updateUnsafe(").contains("&& moved(view, s.rect)) { hold(root.first, root.second); return }"), "mặt con tự nghe layout")
        // Log mỗi lượt (§8: 519 dòng / 2 đợt): họ chỉ khi đổi họ / đạt-sàn; chấm chỉ khi có điểm đổi nền; dưới sàn khoá theo mặt+họ.
        val decide = SourceRoots.body(ai, "private fun decide(")
        assertTrue(decide.contains("if (prev == null || prev.family != choice.family || prev.ok != choice.ok)"))
        assertTrue(decide.contains("key = \"dưới sàn:\$name#\${choice.family}\""))
        assertTrue(code("Car3dDotsLayer.kt").contains("if (moved > 0) Log.d(TAG, \"R29 chấm: đo lại"))
    }

    /** P0 14/10 + mọi cửa công khai là ranh giới fail-soft (log qua LogLineThrottle, không ném vào chỗ gọi). */
    @Test
    fun `muc thich ung khong bao gio nem vao cho goi`() {
        val ai = code("AdaptiveInk.kt")
        for (fn in listOf("private fun ink(", "fun update(view: View", "fun rescore(", "fun spans(", "fun reapply("))
            assertTrue(SourceRoots.body(ai, fn).contains("catch (e: RuntimeException)"), "$fn phải là ranh giới fail-soft")
        assertTrue(SourceRoots.body(ai, "private fun logW(").contains("throttle.suppressedBefore("))
        assertTrue(Regex("""catch \(e: RuntimeException\) \{\s*logW\(""").findAll(ai).count() >= 4)
        assertTrue(SourceRoots.body(code("Car3dDotsLayer.kt"), "private inline fun guard(").contains("catch (e: RuntimeException)"))
        assertTrue(code("KachiHomeActivity.kt").let { it.contains("KachiPerf.dueLine(") && it.contains("ContrastLedger.snapshot()") })
    }

    @Test
    fun `cho da noi - moi cho to chu icon trong pham vi T29 di qua AdaptiveInk`() {
        val art = code("WallArt.kt")
        assertTrue(SourceRoots.body(art, "fun build(cache: ArtCache").let { it.contains("sharpField(source, src)") && it.contains("LumaField.of(px, w, h, GLASS_COLS)") })
        val glass = code("WallGlass.kt")
        assertTrue(SourceRoots.body(glass, "fun relocate(").contains("AdaptiveInk.update(view, force = true)"))
        assertTrue(SourceRoots.body(glass, "fun refresh(").contains("AdaptiveInk.onRefresh(root)"))
        assertTrue(SourceRoots.body(glass, "fun plain(").contains("AdaptiveInk.clear(view)"))
        val type = code("KachiWidgetType.kt")
        for (fn in listOf("fun pending(", "fun hero(", "fun unit(", "fun caption(", "fun title(")) {
            val b = SourceRoots.body(type, fn)
            assertTrue(b.contains("AdaptiveInk.text(tv, ") && !b.contains("setTextColor("), "KachiWidgetType.$fn")
        }
        assertTrue(SourceRoots.body(type, "fun withUnit(").contains("AdaptiveInk.InkSpan(KachiTheme.MUT)"))
        assertTrue(SourceRoots.body(code("KachiTopStrip.kt"), "private fun applyChipFace(").contains("AdaptiveInk.text(v, color, compound = true)"))
        assertTrue(SourceRoots.body(code("ControlTileFactory.kt"), "private fun applyBg(").contains("AdaptiveInk.tile(v, active)"))
        assertTrue(SourceRoots.body(code("SlotSwapButton.kt"), "fun centered(").contains("AdaptiveInk.ride(visual, d)"))
        val icons = code("KachiIcons.kt")
        assertTrue(SourceRoots.body(icons, "fun tint(img: ImageView").contains("AdaptiveInk.icon(img, inkHex)"))
        assertTrue(SourceRoots.body(icons, "fun fadeOff(img: ImageView").contains("AdaptiveInk.onInk(img)"))
        assertFalse(code("TopStripChipInk.kt").contains("ForegroundColorSpan") || type.contains("ForegroundColorSpan"))
    }

    /**
     * Soát Pass 9 (3.1.9) [P2] — hai lỗ của R29-RESOLVE-STORM: (1) lời tô bị dồn/hoãn khi mặt gốc ĐÃ có họ phải tô tạm mực của họ ấy
     * (không mã thô ⇒ không nháy sai họ một khung trước lượt gộp); (2) `inkShown` cho `ImageView` so ĐỐI TƯỢNG bộ lọc đã đặt — ai khác
     * `setColorFilter`/`clearColorFilter` trên cùng view thì lời tô kế KHÔNG được coi là no-op (màu kẹt). ĐỎ khi hoàn một trong hai.
     */
    @Test
    fun `soat Pass 9 - to tam theo ho da chon, inkShown ImageView so doi tuong bo loc`() {
        val ink = SourceRoots.body(code("AdaptiveInk.kt"), "private fun inkUnsafe(")
        assertTrue(ink.contains("(if (chosen) InkModel.ink(key, InkModel.families(KachiTheme.palette)[root.second.family]) else c(hex)).also { inkRaw(v, it, compound) }"),
            "lời tô dồn/hoãn mà mặt đã có họ ⇒ tô tạm mực của họ, không phải mã thô")
        assertTrue(SourceRoots.body(code("AdaptiveInk.kt"), "private fun paint(").contains("m.filter = (v as? ImageView)?.colorFilter"))
        val parts = code("AdaptiveInkParts.kt")
        assertTrue(SourceRoots.body(parts, "internal fun inkShown(").contains("is ImageView -> v.colorFilter != null && v.colorFilter === m.filter"),
            "ImageView: no-op chỉ khi bộ lọc đang gắn đúng là cái AdaptiveInk đặt")
    }

    @Test
    fun `ham moi cua R29 deu co call site ngoai dinh nghia`() {
        val byName = main.mapKeys { it.key.substringAfterLast('/') }
        fun callers(token: String, except: String) = byName.filter { (n, s) -> n != except && s.contains(token) }.keys
        for (t in listOf("AdaptiveInk.text(", "AdaptiveInk.icon(", "AdaptiveInk.fill(", "AdaptiveInk.tile(", "AdaptiveInk.surface(",
            "AdaptiveInk.update(", "AdaptiveInk.rescore(", "AdaptiveInk.reapply(", "AdaptiveInk.onRefresh(", "AdaptiveInk.clear(",
            "AdaptiveInk.backdropAt(", "AdaptiveInk.ride(", "AdaptiveInk.onInk(", "AdaptiveInk.release(", "AdaptiveInk.probe(",
            "AdaptiveInk.under(", "AdaptiveInk.face(", "AdaptiveInk.spans(", "AdaptiveInk.InkSpan(", "AdaptiveInk.sameBackdrop(")) {
            assertTrue(callers(t, "AdaptiveInk.kt").isNotEmpty(), "$t không có call site")
        }
        for (t in listOf("InkModel.families(", "InkModel.ink(", "InkModel.car3dVariants(", "InkModel::paintSpan", "InkModel.bandVariants(",
            "InkModel.plainWall(", "InkModel.role(", "InkKey.of(")) {
            assertTrue(callers(t, "AdaptiveInkModel.kt").isNotEmpty(), "$t không có call site")
        }
        for ((t, home) in listOf("ContrastInk.choose(" to "ContrastInk.kt", "ContrastInk.score(" to "ContrastInk.kt", "ContrastInk.margin(" to "ContrastInk.kt",
            "KachiButtonInks.adapt(" to "KachiButtonInks.kt", "KachiButtonInks.at(" to "KachiButtonInks.kt", ".score(u, shapeOf(s), warn)" to "StateDotPainter.kt",
            "Car3dInk.declare(" to "Car3dInk.kt", "Car3dInk.settle(" to "Car3dInk.kt", "inkRegion(" to "Car3dInk.kt")) {
            assertTrue(callers(t, home).isNotEmpty(), "$t không có call site ngoài $home")
        }
    }
}
