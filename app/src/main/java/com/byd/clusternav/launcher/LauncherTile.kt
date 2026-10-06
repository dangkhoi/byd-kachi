package com.byd.clusternav.launcher

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.camera.CameraDemandDispatch
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ Ô **HÀNH ĐỘNG CỦA CHÍNH LAUNCHER** — tách khỏi `ControlTileFactory.kt` ngày 2026-09-26 (UX4) ═════════════
 *
 * Lý do tách: tệp kia đã **497/500 dòng** (trần CLAUDE.md §4.1, có bài canh
 * `ControlStateUxContractTest.moi tep cua luot WP2 duoi tran 500 dong`) TRƯỚC khi UX4 thêm một dòng nào.
 *
 * Đường cắt theo **VAI**, không theo số dòng — và đây đúng là vai duy nhất trong tệp ấy **không nói về cái xe**:
 * `ControlTileFactory` mặc áo cho **khả năng của XE** (mã nó dựng đều có một dòng trong [ControlRegistry], đều đi
 * qua [CarControlPort], đều có thể mang dấu *"chưa kiểm trên xe"*), còn ô này mặc áo cho **việc của chính
 * launcher** (*Ứng dụng* · *Cài đặt* · *Nói với xe*): không có dòng registry nào, không chạm cổng xe, tier luôn
 * [EvidenceTier.PROVEN]. Cùng lệ đã dùng khi [readTileOf] rời sang `ReadTile.kt` ở WP2.
 *
 * ⚠ Không đổi một dòng hành vi nào lúc tách: cùng package, cùng thứ tự dựng view, cùng 220 ms nháy sáng. Ba tính
 * chất mà `LauncherActionTileWiringContractTest` canh (không `control()`, không `withBadge(`, cú bấm ra `onTap()`)
 * vẫn đo được ở cả hai đầu — cửa vào `ControlTileFactory.launcherTile` giữ nguyên.
 *
 * ## 2.93 wave 2B · CAMERA-DOCK-ACTIVE-STATE — ô camera theo yêu cầu SÁNG như mọi ô bật/tắt
 * Bốn ô camera ([LauncherActions.cameraOf] ≠ `null`) có trạng thái: nền NGHỈ = sáng khi chạm là TẮT
 * ([CameraDemandDispatch.isOn] — luật `CameraDemand.isOn`), nghe controller theo vòng đời cửa sổ
 * ([CameraDemandDispatch.watchWhileAttached]) — không vòng hỏi nào. Phím vật lý / giọng nói / ô khác đổi camera ⇒ ô vẽ lại
 * ngay. Ô khác (*Ứng dụng* · *Cài đặt* · *Nói với xe* · *Tắt camera*) vẫn là cú bấm một phát: nền nghỉ = tắt, y như trước.
 */
internal fun launcherTileOf(
    ctx: Context,
    size: TileSize,
    pick: CapabilityPick,
    icons: Boolean,
    /** Nền + mực của một ô BẤM (`active` = đang nhấn). Chuyền vào để không có bản sao thứ hai của phép tô màu. */
    dress: (LinearLayout, ImageView, TextView, Boolean) -> Unit,
    onTap: () -> Unit,
): View {
    val tile = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        val p = dpi(ctx, size.padDp); setPadding(p, p, p, p)
    }
    val r = KachiIcons.res(pick.icon, size.iconDp)
    val icon = ImageView(ctx).apply { if (r != 0) setImageResource(r) }
    if (icons) tile.addView(icon, LinearLayout.LayoutParams(dpi(ctx, size.iconDp), dpi(ctx, size.iconDp)))
    val label = TextView(ctx).apply {
        text = pick.displayLabel; setTextSize(TypedValue.COMPLEX_UNIT_SP, size.labelSp)
        gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
    }
    tile.addView(reserveTwoLines(label))
    val camera = LauncherActions.cameraOf(pick.id)
    // Nền NGHỈ: ô camera theo trạng thái THẬT (+ `isSelected` cho TalkBack), ô khác luôn tắt (cú bấm một phát).
    fun rest() {
        val on = camera != null && CameraDemandDispatch.isOn(ctx, camera)
        if (camera != null) tile.isSelected = on
        dress(tile, icon, label, on)
    }
    rest()
    tile.setOnClickListener {
        dress(tile, icon, label, true)
        onTap()
        tile.postDelayed({ rest() }, TAP_FLASH_MS)   // nháy sáng momentary, rồi về nền NGHỈ
    }
    if (camera != null) CameraDemandDispatch.watchWhileAttached(tile) { rest() }
    return tile
}

/**
 * ═══ 2.93 wave 2B · CAMERA-WIDGET-TILE (spec OQ3) — ô camera theo yêu cầu trong WIDGET lưới ô giữa màn ═══════════════
 *
 * CÙNG bộ dựng ô của thanh nút ([ControlTileFactory.launcherTile] → [launcherTileOf]: hình mang vị trí, sáng theo trạng
 * thái) và CÙNG đường thi hành ([CameraDemandDispatch.tap] — như nút thanh nút / phím vật lý). Lưới khớp khung theo luật
 * widget sẵn có (`FitGridLayout` + `GridFit`): bốn hình camera cùng một bóng (`ic-cam-view-*`) ⇒ dạng chỉ-icon bị chặn
 * (`IconRepeat.ofIds`), nhãn luôn còn. Ô to ([TileSize.BIG]) có đệm [Sp.M] như ô nút xe (`WidgetViews.actionTile`).
 * Chỗ gọi rẽ bằng [LauncherActions.isCamera] TRƯỚC (mã camera luôn có trong danh mục — bài `CameraWidgetTileTest`); nhánh
 * ô trống chỉ là lưới an toàn không sập cho một mã không thể có.
 */
internal fun cameraDemandTile(ctx: Context, id: String, size: TileSize): View {
    val pick = CapabilityCatalog.pick(id)?.takeIf { LauncherActions.isCamera(id) }
    val tile = if (pick == null) View(ctx)
    else ControlTileFactory(ctx, control = { NoCar }, size = size).launcherTile(pick) { CameraDemandDispatch.tap(ctx, id) }
    val pad = if (size == TileSize.BIG) dpi(ctx, Sp.M) else 0
    return FrameLayout(ctx).apply {
        setPadding(pad, pad, pad, pad)
        addView(tile, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }
}

/**
 * Thời gian nháy sáng của một ô bấm-một-phát. Dùng lại **đúng con số** của `ControlTileFactory.tileButton` để hai
 * ô cạnh nhau trên cùng một thanh không nháy hai nhịp khác nhau.
 */
private const val TAP_FLASH_MS = 220L
