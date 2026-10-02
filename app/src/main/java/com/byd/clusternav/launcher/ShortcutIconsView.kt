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
 *  - **Widget `w_apps`** ([grid] = `true`): lưới ≤ [ShortcutStrip.GRID_MAX_COLS] cột, khe ≥ [Sp.TOUCH] (đích chạm ≥ 48 dp).
 *
 * Mỗi icon: `contentDescription` = tên app. Mờ khi (a) app đã gỡ, hoặc (b) kiểu cần kênh (*Ô n* · *Chạy ngầm*) mà kênh
 * điều khiển cửa sổ không dùng được ([ShellAccessUi.usableNow]) — tự sáng lại khi kênh lên. Ba bên nghe (danh sách ·
 * kênh · cài/gỡ gói) đăng ký ở [onAttachedToWindow], gỡ ở [onDetachedFromWindow] (mẫu `ShellAccessUi.tileHint`) ⇒ view
 * tháo khỏi cây không còn bị gọi, không rò Activity. Icon + nhãn bung trên luồng nền ([IO]) rồi gắn về luồng vẽ ([MAIN]),
 * cùng lẽ `AppDrawerApps.tile`: `rebuild` chạy ở mọi lần `setConfig`/`restyle` của thanh nút.
 */
internal class ShortcutIconsView @JvmOverloads constructor(
    context: Context,
    private val grid: Boolean = false,
    private val compact: Boolean = false,
) : LinearLayout(context) {

    /** Thanh nút đang DỌC (viền trái/phải) ⇒ khối cao ra; ngang ⇒ khối rộng ra. Chỉ có nghĩa khi [grid] = `false`. */
    var vertical: Boolean = false
        set(v) { field = v; if (isAttachedToWindow) rebuild() }

    private class Cell(val sc: AppShortcut, val view: ImageView) {
        var installed = true
    }

    private val cells = ArrayList<Cell>()

    /** Lượt dựng — icon bung xong của lượt CŨ không được gắn vào ô của lượt mới. */
    private var generation = 0

    private val onList: () -> Unit = { MAIN.post { if (isAttachedToWindow) rebuild() } }

    // Đọc trạng thái MỚI NHẤT lúc vẽ (bên nghe có thể tới ngược thứ tự từ hai luồng) — cùng luật `ShellAccessUi.tileHint`.
    private val onReady: (ShellReadinessState) -> Unit = { _ -> MAIN.post { paintDim() } }

    private val onPackages = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) { MAIN.post { if (isAttachedToWindow) rebuild() } }
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
        rebuild()
    }

    override fun onDetachedFromWindow() {
        ShortcutHub.removeListener(onList)
        ShellReadiness.removeListener(onReady)
        unregisterPackages()
        super.onDetachedFromWindow()
    }

    /** Dựng lại toàn bộ theo danh sách hiện tại (≤ 8 ô — rẻ; đổi danh sách là chuyện hiếm). */
    private fun rebuild() {
        generation++
        removeAllViews()
        cells.clear()
        val items = ShortcutHub.items()
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
        if (items.isEmpty()) { addView(emptyCell()); return }
        if (grid) buildGrid(items) else items.forEach { addView(cell(it), cellLp()) }
        load(generation)
        paintDim()
    }

    private fun buildGrid(items: List<AppShortcut>) {
        items.chunked(ShortcutStrip.gridCols(items.size)).forEach { row ->
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL; gravity = Gravity.CENTER
                row.forEach { addView(cell(it), cellLp()) }
            }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
    }

    private fun cellPx(): Int = if (grid && !compact) dpi(context, Bars.SHORTCUT_GRID_CELL) else dpi(context, Bars.SHORTCUT_CELL)
    private fun iconSizeDp(): Int = if (grid && !compact) Bars.SHORTCUT_GRID_ICON else Bars.SHORTCUT_ICON

    private fun cellLp() = LayoutParams(cellPx(), cellPx())

    private fun cell(sc: AppShortcut): ImageView = ImageView(context).apply {
        val pad = (cellPx() - dpi(context, iconSizeDp())) / 2
        setPadding(pad, pad, pad, pad)
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
                    // [ĐO máy ảo 02/10 m8] app đã gỡ không có icon ⇒ ô TRỐNG, "mờ" không nhìn ra được — vẽ hình app chung
                    // (cùng hình ô "chưa có lối tắt") để R1.2 "icon mờ" có thứ để mờ.
                    if (icon != null) cell.view.setImageDrawable(icon) else genericIcon(cell.view)
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
 * Bề dài (px) của khối lối tắt trên thanh nút cho [n] app: `cells(n) × SHORTCUT_CELL + 2 × SHORTCUT_PAD` (R1.2). MỘT
 * phép cho cả `ControlDockView` (lúc dựng) và chính khối (lúc danh sách đổi) — hai bản sao là hai chỗ để lệch.
 */
internal fun shortcutStripLength(ctx: Context, n: Int): Int =
    ShortcutStrip.cells(n) * dpi(ctx, Bars.SHORTCUT_CELL) + 2 * dpi(ctx, Bars.SHORTCUT_PAD)
