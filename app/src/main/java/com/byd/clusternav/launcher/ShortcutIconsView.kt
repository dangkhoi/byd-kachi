package com.byd.clusternav.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.byd.clusternav.R
import com.byd.clusternav.ShellReadiness
import com.byd.clusternav.carexec.ShellReadinessState
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.byd.clusternav.launcher.KachiBars as Bars
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ F1 — ICON LỐI TẮT ỨNG DỤNG: khối trên thanh nút (U3) + lưới của widget `w_apps` (U4) ════════════════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` R1.2 · R1.3 · §4.4.2. MỘT lớp cho hai bề mặt (cùng danh
 * sách [ShortcutHub], cùng ô icon, cùng luật mờ) — hai lớp là hai chỗ để quên một luật.
 *
 *  - **Khối thanh nút** ([grid] = `false`): một hàng icon dọc theo TRỤC của thanh ([vertical]); bề dài =
 *    [shortcutStripLength] (mỗi app một khe `KachiBars.SHORTCUT_CELL`, icon `SHORTCUT_ICON`). Không tự cuộn —
 *    tràn thì khung cuộn sẵn có của thanh (`DockAreaLayout.scrollWrap`) cuộn.
 *  - **Widget `w_apps`** ([grid] = `true`, cả ô to lẫn ô nén): R-SI1 (2.87) → 2.92 — icon đặt bởi [ShortcutGridLayout]
 *    theo `ShortcutGridFit` (`:core`): cùng cỡ, cỡ lớn nhất vừa khung THẬT với khe CỐ ĐỊNH 8 dp (icon to, lề nhỏ — owner
 *    06/10), phần dư chia đều, hàng cuối căn giữa; nhiều app tới mức icon chạm sàn 40 dp ⇒ cuộn theo trục dài. Icon app
 *    đã gỡ (hình chung) được nạp lại đúng cỡ khớp khi cỡ đổi ([fitIcons]).
 *
 * Mỗi icon: `contentDescription` = tên app. Mờ khi (a) app đã gỡ, hoặc (b) kiểu cần kênh (*Ô n* · *Chạy ngầm*) mà kênh
 * điều khiển cửa sổ không dùng được ([ShellAccessUi.usableNow]) — tự sáng lại khi kênh lên. Ba bên nghe (danh sách ·
 * kênh · cài/gỡ gói) đăng ký ở [onAttachedToWindow], gỡ ở [onDetachedFromWindow] (mẫu `ShellAccessUi.tileHint`) ⇒ view
 * tháo khỏi cây không còn bị gọi, không rò Activity. Icon + nhãn bung trên luồng nền ([IO]) rồi gắn về luồng vẽ ([MAIN]),
 * cùng lẽ `AppDrawerApps.tile`: `rebuild` chạy ở mọi lần `setConfig`/`restyle` của thanh nút.
 *
 * 2.93 · SHORTCUT-GRID-SCROLL-KEEP (spec `kachi-293-slot.html` R1, luật `ShortcutScrollKeep`): DỰNG LẠI cấu trúc ([rebuild]) chỉ
 * khi danh sách khác danh sách đã dựng ([built]) — gắn lại view cùng danh sách và phát tin cài/gỡ gói CỦA DANH SÁCH chỉ nạp lại
 * icon tại chỗ ([refresh]); gói ngoài danh sách không chạm gì. Lượt dựng lại thật chuyển vị trí cuộn người lái đã chọn sang
 * khung mới. 2.92 dựng lại cả lưới ở MỌI phát tin của hệ thống ⇒ vị trí về 0, cú kéo dở bị cắt.
 *
 * 2.93 wave 2A · SHORTCUT-SCROLL-REBUILD (spec `kachi-293-wave2a.html` §4.3, senior review SLOT Pass 2 mục 4): một view MỚI của
 * cùng ô (đổi Sáng/Tối `WorkspaceView.restyle`, đổi đơn vị `rebuildWidgetSlots`, Activity dựng lại) bắt đầu từ vị trí người lái
 * đã chọn ở view cũ — nhớ trong tiến trình theo [scrollKey] (ô + tổ hợp widget, [ShortcutScrollMemory]); `null` (khối thanh
 * nút · bản nháp đo sức chứa) ⇒ như cũ.
 */
internal class ShortcutIconsView @JvmOverloads constructor(
    context: Context,
    private val grid: Boolean = false,
    private val compact: Boolean = false,
    private val scrollKey: String? = null,
) : LinearLayout(context) {

    /** Thanh nút đang DỌC (viền trái/phải) ⇒ khối cao ra; ngang ⇒ khối rộng ra. Chỉ có nghĩa khi [grid] = `false`. */
    var vertical: Boolean = false
        set(v) { field = v; built = null; if (isAttachedToWindow) rebuild() }

    /**
     * 2.89 · B3 DOCK-SCALE — khối thanh nút ở cỡ ≠ 100 %: mỗi khe lấp TRỌN bề dày thanh (ngang trục) để vùng chạm của icon
     * = khe ≥ 48 dp thật × bề dày thanh, dù icon vẽ nhỏ theo %. `false` (100 % · lưới widget) ⇒ khe vuông như 2.88.
     */
    var fillAcross: Boolean = false
        set(v) { field = v; built = null; if (isAttachedToWindow) rebuild() }

    private class Cell(val sc: AppShortcut, val view: ImageView) {
        var installed = true

        /** Ô đang vẽ HÌNH CHUNG (app đã gỡ) — hình này nạp theo cỡ dp nên phải nạp lại khi lưới đổi cỡ icon. */
        var generic = false
    }

    private val cells = ArrayList<Cell>()

    /** 2.93 · R1 — danh sách mà [cells] đang vẽ (`null` = chưa dựng / cấu hình khối đổi lúc tháo ⇒ lần gắn sau dựng lại). */
    private var built: List<AppShortcut>? = null

    /** Lượt dựng — icon bung xong của lượt CŨ không được gắn vào ô của lượt mới. */
    private var generation = 0

    /** Cỡ icon (dp) lưới vừa khớp theo khung thật (R-SI1); 0 = chưa khớp lượt này ⇒ dùng [baseIconDp]. */
    private var fittedDp = 0

    private val onList: () -> Unit = { MAIN.post { if (isAttachedToWindow && ShortcutScrollKeep.needsRebuild(built, ShortcutHub.items())) rebuild() } }

    // Đọc trạng thái MỚI NHẤT lúc vẽ (bên nghe có thể tới ngược thứ tự từ hai luồng) — cùng luật `ShellAccessUi.tileHint`.
    private val onReady: (ShellReadinessState) -> Unit = { _ -> MAIN.post { paintDim() } }

    /** 2.93 · R1 — gói NGOÀI danh sách ⇒ không làm gì; gói trong danh sách ⇒ nạp lại icon/nhãn/mờ tại chỗ (không dựng lại). */
    private val onPackages = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart
            MAIN.post { if (isAttachedToWindow && ShortcutScrollKeep.touches(pkg, cells.map { it.sc.pkg })) refresh() }
        }
    }
    private var receiverOn = false

    init {
        gravity = Gravity.CENTER
        orientation = if (grid) VERTICAL else HORIZONTAL
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ShortcutHub.addListener(onList)
        ShellReadiness.addListener(onReady)
        registerPackages()
        // 2.93 · R1 — gắn lại cùng danh sách ⇒ chỉ làm mới (gói có thể đã cài/gỡ lúc tháo); khác ⇒ dựng lại (giữ vị trí cuộn).
        if (ShortcutScrollKeep.needsRebuild(built, ShortcutHub.items())) rebuild() else refresh()
    }

    override fun onDetachedFromWindow() {
        ShortcutHub.removeListener(onList)
        ShellReadiness.removeListener(onReady)
        unregisterPackages()
        super.onDetachedFromWindow()
    }

    /**
     * Dựng lại toàn bộ theo danh sách hiện tại (đổi danh sách là chuyện hiếm). 2.92: không còn trần 8 — tối đa trần kỹ
     * thuật `AppShortcutCodec.MAX` ô; lưới widget cuộn khi icon chạm sàn ([ShortcutGridLayout]), khối thanh nút trong
     * khung cuộn của thanh.
     */
    private fun rebuild() {
        // R1: vị trí người lái chọn — view cũ cùng lượt; view MỚI của ô ⇒ bản nhớ trong tiến trình (wave 2A).
        val keep = (getChildAt(0) as? ShortcutGridLayout)?.keep ?: scrollKey?.let(ShortcutScrollMemory::recall) ?: ShortcutScrollKeep.Wanted.ORIGIN
        generation++
        fittedDp = 0
        removeAllViews()
        cells.clear()
        val items = ShortcutHub.items()
        built = items
        if (!grid) {
            orientation = if (vertical) VERTICAL else HORIZONTAL
            val pad = dpi(context, Bars.SHORTCUT_PAD)
            if (vertical) setPadding(0, pad, 0, pad) else setPadding(pad, 0, pad, 0)
            // Bề dài khối do `:core` tính (R1.2) — đặt TƯỜNG MINH, không phó cho WRAP: bài canh + E7 đo đúng con số này.
            layoutParams?.let { lp ->
                val len = shortcutStripLength(context, items.size)
                if (vertical) lp.height = len else lp.width = len
                layoutParams = lp
            }
        }
        if (items.isEmpty()) { if (fillAcross) addView(emptyCell(), cellLp()) else addView(emptyCell()); return }
        if (grid) buildGrid(items, keep) else items.forEach { addView(cell(it), cellLp()) }
        load(generation)
        paintDim()
    }

    /** 2.93 · R1 — nạp lại icon + nhãn + "còn cài" của các ô ĐANG có (cùng lượt dựng) rồi tô mờ: không đổi cây view, không đổi vị trí cuộn. */
    private fun refresh() {
        load(generation)
        paintDim()
    }

    /**
     * R-SI1 — lưới widget (ô to + ô nén): MỘT [ShortcutGridLayout] lấp khung, đặt icon theo `ShortcutGridFit`. Báo cỡ
     * của lượt CŨ (khung đã tháo) bị bỏ qua nhờ [generation].
     */
    private fun buildGrid(items: List<AppShortcut>, keep: ShortcutScrollKeep.Wanted) {
        val gen = generation
        val box = ShortcutGridLayout(context, keep, { w -> scrollKey?.let { ShortcutScrollMemory.remember(it, w) } }) { px ->
            if (gen == generation) fitIcons(px)
        }
        items.forEach { box.addView(cell(it)) }
        addView(box, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Lưới vừa khớp cỡ icon [iconPx] ⇒ hình chung (app đã gỡ) nạp lại đúng biến thể/tint của cỡ đó. */
    private fun fitIcons(iconPx: Int) {
        val dp = (iconPx / resources.displayMetrics.density).toInt()
        if (dp <= 0 || dp == fittedDp) return
        fittedDp = dp
        cells.forEach { if (it.generic) genericIcon(it.view) }
    }

    /** Khe cố định — CHỈ khối thanh nút; lưới widget không có khe cố định (R-SI1). B3: cùng phép với [shortcutStripLength]. */
    private fun cellPx(): Int = shortcutSlotPx(context)

    /** Cỡ icon gốc: thanh nút + ô nén = [Bars.SHORTCUT_ICON], ô to = [Bars.SHORTCUT_GRID_ICON] (ô rỗng · trước lượt đo). */
    private fun baseIconDp(): Int = if (grid && !compact) Bars.SHORTCUT_GRID_ICON else Bars.SHORTCUT_ICON

    /** Cỡ icon đang vẽ: lưới đã khớp ⇒ cỡ khớp; khối thanh nút không bao giờ khớp ⇒ luôn [baseIconDp]. */
    private fun iconSizeDp(): Int = if (fittedDp > 0) fittedDp else baseIconDp()

    /** Khe vuông; B3 [fillAcross] ⇒ ngang trục lấp trọn bề dày thanh (icon vẫn đúng cỡ: lề dọc trục + FIT_CENTER). */
    private fun cellLp() = when {
        !fillAcross -> LayoutParams(cellPx(), cellPx())
        vertical -> LayoutParams(LayoutParams.MATCH_PARENT, cellPx())
        else -> LayoutParams(cellPx(), LayoutParams.MATCH_PARENT)
    }

    private fun cell(sc: AppShortcut): ImageView = ImageView(context).apply {
        // Lưới: lề do ShortcutGridLayout đặt theo phép khớp (nửa khe) — khe cố định chỉ còn ở khối thanh nút.
        if (!grid) {
            val pad = (cellPx() - dpi(context, iconSizeDp())) / 2
            // Review 2.89 Pass 2 · vietmap-dock-r1-8: [fillAcross] ⇒ ngang trục là TRỌN bề dày thanh, không phải khe vuông —
            // lề vuông ở đó cắt hộp hình (50 % ngang @240 dpi: 69 − 2·19 = 31 px < icon 33 px). Lề chỉ dọc trục; FIT_CENTER canh
            // giữa ngang trục.
            when {
                !fillAcross -> setPadding(pad, pad, pad, pad)
                vertical -> setPadding(0, pad, 0, pad)
                else -> setPadding(pad, 0, pad, 0)
            }
        }
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = sc.pkg                 // tên app thay vào khi bung xong (luồng nền)
        isClickable = true
        setOnClickListener { ShortcutHub.tap(context, sc) }
        cells.add(Cell(sc, this))
    }

    /** Ô "chưa có lối tắt" — chạm ⇒ chỗ chọn (Cài đặt). Thanh nút: một khe icon; widget: icon + một dòng chữ. */
    private fun emptyCell(): LinearLayout = LinearLayout(context).apply {
        orientation = VERTICAL; gravity = Gravity.CENTER
        contentDescription = context.getString(R.string.kachi_sc_empty)
        isClickable = true
        setOnClickListener { ShortcutHub.pick(context) }
        addView(ImageView(context).apply {
            genericIcon(this)
            alpha = EMPTY_ALPHA
        }, LayoutParams(dpi(context, iconSizeDp()), dpi(context, iconSizeDp())))
        if (grid) addView(TextView(context).apply {
            text = context.getString(R.string.kachi_sc_empty)
            setTextColor(c(KachiTheme.MUT)); KachiType.apply(this, KachiType.CAPTION)
            gravity = Gravity.CENTER; setPadding(0, dpi(context, Sp.XS), 0, 0)
        })
    }

    /** Hình app CHUNG (cùng glyph nút *Ứng dụng*) — ô rỗng và ô của app đã gỡ. */
    private fun genericIcon(v: ImageView) {
        val r = KachiIcons.res(GENERIC_ICON, iconSizeDp())
        if (r != 0) v.setImageResource(r)
        KachiIcons.tint(v, iconSizeDp(), false)
    }

    /** Bung icon + nhãn + "còn cài không" trên luồng nền, gắn về luồng vẽ nếu vẫn đúng lượt dựng. */
    private fun load(gen: Int) {
        val pm = context.packageManager
        cells.forEach { cell ->
            IO.execute {
                val (icon, label) = probe(pm, cell.sc.pkg)
                MAIN.post {
                    if (gen != generation) return@post
                    cell.installed = icon != null || label != null
                    cell.generic = icon == null
                    // [ĐO máy ảo 02/10 m8] app đã gỡ không có icon ⇒ ô TRỐNG, "mờ" không nhìn ra được — vẽ hình app chung
                    // (cùng hình ô "chưa có lối tắt") để R1.2 "icon mờ" có thứ để mờ.
                    // 2.93 QA F1 [ĐO máy ảo 07/10]: nạp lại TẠI CHỖ (R1) ⇒ app cài lại nhận icon thật trên view vừa mang hình
                    // chung đã tint — phải gỡ tint, không thì icon xám tới lần dựng lại.
                    if (icon != null) { KachiIcons.untint(cell.view); cell.view.setImageDrawable(icon) } else genericIcon(cell.view)
                    label?.let { cell.view.contentDescription = it }
                    paintDim()
                }
            }
        }
    }

    private fun probe(pm: PackageManager, pkg: String): Pair<Drawable?, String?> = try {
        val ai = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationIcon(ai) to pm.getApplicationLabel(ai).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null to null                                // đã gỡ ⇒ ô mờ; chạm thì bảng chạm báo "chưa cài" (dòng 0)
    }

    /** Mờ theo hai luật của R1.2/R1.5 — đọc kênh MỚI NHẤT mỗi lần vẽ. */
    private fun paintDim() {
        val usable = ShellAccessUi.usableNow()
        cells.forEach { cell ->
            cell.view.alpha = when {
                !cell.installed -> GONE_ALPHA
                cell.sc.mode.needsChannel && !usable -> NO_CHANNEL_ALPHA
                else -> 1f
            }
        }
    }

    private fun registerPackages() {
        if (receiverOn) return
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        }
        // Phát tin của HỆ THỐNG (không app nào giả được) ⇒ không cần xuất; cờ tường minh cho API 33+ (lint).
        receiverOn = runCatching {
            ContextCompat.registerReceiver(context.applicationContext, onPackages, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        }.onFailure { Log.w(TAG, "không nghe được cài/gỡ gói: ${it.javaClass.simpleName}") }.isSuccess
    }

    private fun unregisterPackages() {
        if (!receiverOn) return
        receiverOn = false
        runCatching { context.applicationContext.unregisterReceiver(onPackages) }
    }

    private companion object {
        const val TAG = "KachiShortcut"

        /** App đã gỡ — mờ hơn "chưa có kênh" để hai trạng thái phân biệt được bằng mắt. */
        const val GONE_ALPHA = 0.3f

        /** Kiểu cần kênh mà kênh chưa dùng được (R1.5) — đúng độ mờ 40 % của §4.4.2. */
        const val NO_CHANNEL_ALPHA = 0.4f

        const val EMPTY_ALPHA = 0.7f

        /** Glyph app chung — CÙNG tên icon với `LauncherActions.SHORTCUTS`/`APPS` (luật U6: một việc, một hình). */
        const val GENERIC_ICON = "ic-apps"

        /** Hai luồng bung icon (cùng lý do `AppDrawerApps.ICONS`: thêm luồng chỉ thêm tranh chấp binder). Daemon. */
        val IO: ExecutorService = Executors.newFixedThreadPool(2) { r -> Thread(r, "kachi-shortcut-icons").apply { isDaemon = true } }

        /** `by lazy`: Handler dựng lúc nạp lớp sẽ giết mọi bài JVM chạm lớp này (lẽ của `AppDrawerApps.MAIN`). */
        val MAIN: Handler by lazy { Handler(Looper.getMainLooper()) }
    }
}

/**
 * Bề dài (px) của khối lối tắt trên thanh nút cho [n] app: `cells(n) × khe + 2 × SHORTCUT_PAD` (R1.2). MỘT
 * phép cho cả `ControlDockView` (lúc dựng) và chính khối (lúc danh sách đổi) — hai bản sao là hai chỗ để lệch.
 */
internal fun shortcutStripLength(ctx: Context, n: Int): Int =
    ShortcutStrip.cells(n) * shortcutSlotPx(ctx) + 2 * dpi(ctx, Bars.SHORTCUT_PAD)

/**
 * KHE một app của khối thanh nút (px) = `max(SHORTCUT_CELL, 48 dp THẬT)`. 2.89 · B3: [ctx] là `Context` co/giãn của thanh
 * ⇒ khe co theo % nhưng không dưới đích chạm thật (`DockScaleContext.touchFloorPx`); ở 100 % đúng `SHORTCUT_CELL` (52 ≥ 48)
 * như 2.88. MỘT phép cho [ShortcutIconsView.cellPx] và [shortcutStripLength] (luật "MỘT phép bề dài" ở trên).
 */
internal fun shortcutSlotPx(ctx: Context): Int = maxOf(dpi(ctx, Bars.SHORTCUT_CELL), DockScaleContext.touchFloorPx(ctx))
