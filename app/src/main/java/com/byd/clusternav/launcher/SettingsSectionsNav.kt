package com.byd.clusternav.launcher

import android.content.Context
import android.widget.LinearLayout
import com.byd.clusternav.R
import com.byd.clusternav.VmBubblePlacementView
import com.byd.clusternav.modules.clustercast.BadgePlacementView
import com.byd.clusternav.modules.clustercast.ClusterNavLaneWidget
import com.byd.clusternav.navigation.NavReadChannel
import com.byd.clusternav.navigation.NavigationOutputStatus
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * Nhóm **"Dẫn đường & cụm đồng hồ"** (IA v2 §4.1 nhóm 5) — dựng lại 11 điều khiển của màn ClusterNav cũ, ghi
 * **đúng khoá thật** qua [ClusterNavBridge] (spec §4.3, R2).
 *
 * ## Ba mục con, một thứ tự có lý do
 * `Dẫn đường + HUD` (công tắc chính + trạng thái) → `Biển báo tốc độ` → `Bong bóng VietMap`. Công tắc chính đứng
 * đầu vì hai mục dưới **chỉ có nghĩa khi nó bật**; và trạng thái nguồn đứng ngay dưới công tắc vì đó là câu trả
 * lời cho *"tôi vừa bật, có chạy không?"* — câu hỏi duy nhất người dùng có sau cú chạm đó.
 *
 * ## ⚠ Trạng thái phải ĐỌC LẠI, không chụp một lần
 * `bridge.navSource()` / `navOutputStatus()` đọc singleton `:core` đang chạy, nên giá trị lúc dựng trang là ảnh
 * chụp của **một** khoảnh khắc. Trang Cài đặt được **nhớ lại** ([SettingsPanel.pages]) ⇒ không tự dựng lại. Nên
 * hai dòng trạng thái giữ [SettingsRows.StatusRow] và được [refreshStatus] cập nhật **tại chỗ** sau mỗi hành
 * động có thể đổi chúng (bật công tắc · kết nối lại). Dựng lại cả trang thay vì vậy sẽ mất chỗ đang cuộn.
 *
 * ## N2 — section KHÔNG chạm `Prefs`/`NavConnect`/`NavRepository`
 * Mọi đường ghi đi qua `bridge.*`; `ClusterNavSettingsWiringContractTest` canh đúng điều đó. Lý do ở KDoc
 * [ClusterNavBridge]: một cầu duy nhất thì khi màn cũ bị gỡ (OQ1) không có chỗ nào khác phải sửa.
 */
class SettingsNavSection(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {

    private val bridge get() = deps.bridge

    private lateinit var sourceRow: SettingsRows.StatusRow
    private lateinit var outputRow: SettingsRows.StatusRow

    /**
     * Dòng op-39 "cụm đang hiện gì" (R2d) — chuyển từ `NavClusterOp39Status` của màn cũ (đã gỡ 2026-09-13).
     *
     * Nó KHÁC [outputRow]: [outputRow] nói *"ta gửi được chưa"* (phía mình), dòng này nói *"cụm có nhận không,
     * hay đang nhường chỗ cho Cast"* (phía cụm). Hai câu trả lời khác nhau cho cùng một triệu chứng "cụm trống",
     * và chính vì lẫn hai thứ đó mà phiên chẩn đoán 08-12 đi sai hướng — nên giữ đủ hai dòng.
     */
    private lateinit var op39Row: SettingsRows.StatusRow

    /** Hai stepper vị trí biển báo + khung kéo-thả — giữ tham chiếu để ba bề mặt nói CÙNG một toạ độ. */
    private var badgeX: SettingsRows.Stepper? = null
    private var badgeY: SettingsRows.Stepper? = null
    private var badgeView: BadgePlacementView? = null

    private var bubbleX: SettingsRows.Stepper? = null
    private var bubbleY: SettingsRows.Stepper? = null
    private var bubbleView: VmBubblePlacementView? = null

    fun build(body: LinearLayout) {
        nav(body)
        badge(body)
        bubble(body)
    }

    // ── Dẫn đường + HUD ──────────────────────────────────────────────────────────────────────────

    /**
     * Công tắc chính + hai dòng trạng thái + chế độ cụm + chạy chữ + nút kết nối lại.
     *
     * Công tắc **tự xin quyền**: `bridge.setNavEnabled` lặp lại nguyên chuỗi của màn cũ (selfGrant qua dadb rồi
     * toast kết quả) — người dùng không phải đi tìm màn Cài đặt hệ thống, vốn bị khoá trên nhiều bản IVI.
     */
    private fun nav(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_sec_nav)))
        body.addView(rows.checkRow(
            on = bridge.navEnabled(),
            title = context.getString(R.string.kachi_nav_enabled_title),
            sub = context.getString(R.string.kachi_nav_enabled_sub),
        ) { on -> bridge.setNavEnabled(on) { refreshStatus() } })

        sourceRow = rows.statusRow(KachiTheme.MUT2, "")
        outputRow = rows.statusRow(KachiTheme.MUT2, "")
        op39Row = rows.statusRow(KachiTheme.MUT2, "")
        body.addView(sourceRow.view)
        body.addView(outputRow.view)
        body.addView(op39Row.view)
        refreshStatus()

        body.addView(rows.chipRow(
            label = context.getString(R.string.kachi_nav_cluster_mode),
            options = ClusterNavSettingsModel.clusterModeOptions().map { it.name to clusterModeLabel(it) },
            current = ClusterNavSettingsModel.clusterModeOf(bridge.clusterMode()).name,
        ) { code ->
            ClusterNavSettingsModel.clusterModeOptions().firstOrNull { it.name == code }
                ?.let { bridge.setClusterMode(it.value) }
        })
        body.addView(rows.checkRow(
            on = bridge.marquee(),
            title = context.getString(R.string.kachi_nav_marquee_title),
            sub = context.getString(R.string.kachi_nav_marquee_sub),
        ) { on -> bridge.setMarquee(on) })
        body.addView(rows.button(context.getString(R.string.kachi_nav_reconnect)) {
            bridge.reconnect { refreshStatus() }
        })
    }

    /**
     * Nhãn hai nấc chế độ cụm — tra **tài nguyên của launcher** theo `enum`, KHÔNG đọc
     * `ClusterNavSettingsModel.NavClusterMode.label`.
     *
     * [ĐO] `NavClusterMode` mang `label`/`labelEn` dạng chuỗi thô (nó sinh ra cho phía ClusterNav, nơi nhãn đặt
     * lúc chạy bằng `Lang.t`). Tầng `launcher/` có luật ngược lại — mọi chữ qua tài nguyên, canh bởi
     * `LauncherI18nContractTest.tang ve khong doc nhan GOC cua core`. Nên chỗ này chỉ mượn **giá trị** (`value`)
     * và phép quy đổi của `:core`, còn chữ thì lấy từ `strings_kachi.xml`.
     */
    private fun clusterModeLabel(mode: ClusterNavSettingsModel.NavClusterMode): String = context.getString(
        when (mode) {
            ClusterNavSettingsModel.NavClusterMode.OFF -> R.string.kachi_nav_cluster_off
            ClusterNavSettingsModel.NavClusterMode.ON -> R.string.kachi_nav_cluster_on
        },
    )

    /**
     * Đọc lại HAI dòng trạng thái từ cầu và tô lại chấm màu.
     *
     * Màu là **vai trò**, không phải trang trí: xanh = đang chạy · hổ phách = có nhưng dữ liệu đã cũ / đang khởi
     * động · xám = chưa có gì. Người lái liếc một cái phải đọc ra được, không phải đọc chữ.
     */
    private fun refreshStatus() {
        val source = bridge.navSource()
        if (source == null) {
            sourceRow.update(KachiTheme.MUT2, context.getString(R.string.kachi_nav_source_none))
        } else {
            val channel = channelLabel(source.channel)
            val brand = source.brand + (if (channel.isEmpty()) "" else " ($channel)")
            sourceRow.update(
                if (source.stale) KachiTheme.AMBER else KachiTheme.GREEN,
                context.getString(
                    if (source.stale) R.string.kachi_nav_source_stale else R.string.kachi_nav_source,
                    brand,
                ),
            )
        }
        val status = bridge.navOutputStatus()
        outputRow.update(outputColour(status), context.getString(R.string.kachi_nav_output, outputLabel(status)))
        val op39 = bridge.clusterOp39()
        op39Row.update(op39Colour(op39), context.getString(op39Label(op39)))
    }

    /**
     * Bốn kết quả op-39 → bốn câu, chép nguyên nghĩa của `NavClusterOp39Status.refresh` (đã gỡ).
     *
     * `when` vét cạn trên enum: thêm một kết quả ở nhánh cast mà quên câu ở đây là **không biên dịch được**, chứ
     * không phải một dòng trống trên màn xe.
     */
    private fun op39Label(status: ClusterNavLaneWidget.Op39Status): Int = when (status) {
        ClusterNavLaneWidget.Op39Status.IDLE -> R.string.kachi_nav_op39_idle
        ClusterNavLaneWidget.Op39Status.ASSERTED -> R.string.kachi_nav_op39_asserted
        ClusterNavLaneWidget.Op39Status.GATED_CAST -> R.string.kachi_nav_op39_gated
        ClusterNavLaneWidget.Op39Status.SHELL_UNREACHABLE -> R.string.kachi_nav_op39_unreachable
    }

    /** Cùng bảng màu với bản cũ: xanh = đang hiện · hổ phách = nhường Cast · đỏ = không gửi được · xám = chờ. */
    private fun op39Colour(status: ClusterNavLaneWidget.Op39Status): String = when (status) {
        ClusterNavLaneWidget.Op39Status.ASSERTED -> KachiTheme.GREEN
        ClusterNavLaneWidget.Op39Status.GATED_CAST -> KachiTheme.AMBER
        ClusterNavLaneWidget.Op39Status.SHELL_UNREACHABLE -> KachiTheme.RED
        ClusterNavLaneWidget.Op39Status.IDLE -> KachiTheme.MUT2
    }

    /** Kênh đọc của nguồn — `UNKNOWN` trả chuỗi rỗng để câu không có cái ngoặc trống. */
    private fun channelLabel(channel: NavReadChannel): String = when (channel) {
        NavReadChannel.NOTIFICATION -> context.getString(R.string.kachi_nav_channel_notification)
        NavReadChannel.SCREEN_READ -> context.getString(R.string.kachi_nav_channel_screen)
        NavReadChannel.UNKNOWN -> ""
    }

    /** `when` vét cạn — thêm một trạng thái mới ở `:core` là trình dịch bắt ngay, không phải một dòng "—" im lặng. */
    private fun outputLabel(status: NavigationOutputStatus?): String = context.getString(
        when (status) {
            null -> R.string.kachi_nav_out_unknown
            NavigationOutputStatus.OFF -> R.string.kachi_nav_out_off
            NavigationOutputStatus.STARTING -> R.string.kachi_nav_out_starting
            NavigationOutputStatus.EMITTING -> R.string.kachi_nav_out_emitting
            NavigationOutputStatus.DISPLAY_VERIFIED -> R.string.kachi_nav_out_verified
            NavigationOutputStatus.STALE -> R.string.kachi_nav_out_stale
            is NavigationOutputStatus.FAULT -> R.string.kachi_nav_out_fault
        },
    )

    private fun outputColour(status: NavigationOutputStatus?): String = when (status) {
        NavigationOutputStatus.EMITTING, NavigationOutputStatus.DISPLAY_VERIFIED -> KachiTheme.GREEN
        NavigationOutputStatus.STARTING, NavigationOutputStatus.STALE -> KachiTheme.AMBER
        is NavigationOutputStatus.FAULT -> KachiTheme.RED
        else -> KachiTheme.MUT2
    }

    // ── Biển báo tốc độ ──────────────────────────────────────────────────────────────────────────

    /**
     * Bốn công tắc/bộ chọn + **ba** bề mặt chỉnh vị trí nói cùng một toạ độ: stepper ngang · stepper dọc · khung
     * kéo-thả.
     *
     * ## Vì sao vừa stepper vừa kéo-thả
     * Kéo-thả nhanh nhưng **không đặt lại được chính xác** (ngón tay che mất marker, và xe xóc); stepper chậm
     * nhưng đi từng 10px một, đặt được đúng chỗ đã ưng. Màn cũ chỉ có kéo-thả và [ĐO] owner từng phải kéo lại
     * nhiều lần. Cả hai đều ghi qua **một** đường `bridge.setBadgeCenter` (tự kẹp bằng `BadgeLayout.clampCenter`
     * trên kích cụm THẬT), rồi [syncBadge] đọc lại giá trị ĐÃ KẸP để ba bề mặt không bao giờ nói lệch nhau.
     */
    private fun badge(body: LinearLayout) {
        body.addView(rows.subHeader(context.getString(R.string.kachi_sub_badge)))
        body.addView(rows.checkRow(
            on = bridge.badgeEnabled(),
            title = context.getString(R.string.kachi_badge_enabled_title),
            sub = context.getString(R.string.kachi_badge_enabled_sub),
        ) { on -> bridge.setBadgeEnabled(on) })
        body.addView(rows.checkRow(
            on = bridge.upcomingBadge(),
            title = context.getString(R.string.kachi_badge_upcoming_title),
            sub = context.getString(R.string.kachi_badge_upcoming_sub),
        ) { on -> bridge.setUpcomingBadge(on) })
        body.addView(rows.checkRow(
            on = bridge.alertChip(),
            title = context.getString(R.string.kachi_badge_alert_title),
            sub = context.getString(R.string.kachi_badge_alert_sub),
        ) { on -> bridge.setAlertChip(on) })
        val sizes = ClusterNavSettingsModel.badgeSizeOptions()
        body.addView(rows.chipRow(
            label = context.getString(R.string.kachi_badge_size),
            options = sizes.map { it.toString() to context.getString(R.string.kachi_dp_value, it) },
            // ⚠ [SOÁT SENIOR 2026-09-13] Chip sáng phải là nấc GẦN NHẤT, không phải số lưu y nguyên.
            //
            // [ĐO] `BadgePlacementController.kt:100–106` — màn cũ dùng **SeekBar bước 1dp** trên dải 60..240, nên
            // người đã chỉnh cỡ ở màn đó gần chắc chắn đang giữ một giá trị NGOÀI lưới 20dp (vd 113). So chuỗi
            // thẳng ⇒ **0/10 chip sáng**: một hàng chọn mà không lựa chọn nào được chọn, đúng lỗi R-UI (o) vừa
            // phải chữa ở hàng "Bố cục sẵn". Nấc gần nhất nói đúng "cỡ bạn đang dùng nằm quanh đây", và chạm vào
            // nó thì giá trị về đúng lưới.
            current = (sizes.minByOrNull { kotlin.math.abs(it - bridge.badgeSizeDp()) } ?: bridge.badgeSizeDp())
                .toString(),
        ) { code ->
            code.toIntOrNull()?.let {
                bridge.setBadgeSizeDp(it)
                badgeView?.setBadgeSizeCluster(bridge.badgeSizePx())
            }
        })

        val (cx, cy) = bridge.badgeCenter()
        badgeX = rows.stepperRow(
            context.getString(R.string.kachi_badge_pos_x), px(cx),
            onMinus = { nudgeBadge(-STEP_PX, 0) }, onPlus = { nudgeBadge(STEP_PX, 0) },
        )
        badgeY = rows.stepperRow(
            context.getString(R.string.kachi_badge_pos_y), px(cy),
            onMinus = { nudgeBadge(0, -STEP_PX) }, onPlus = { nudgeBadge(0, STEP_PX) },
        )
        body.addView(badgeX!!.view)
        body.addView(badgeY!!.view)

        val (clusterW, clusterH) = bridge.clusterSize()
        badgeView = BadgePlacementView(context, clusterW, clusterH) { x, y ->
            bridge.setBadgeCenter(x, y)
            syncBadge()
        }.also {
            it.setBadgeCenterCluster(cx, cy)
            it.setBadgeSizeCluster(bridge.badgeSizePx())
        }
        // ⚠ [R4] `Sp.EMBED_M` chứ không `EMBED_TALL`: khung này LETTERBOX cụm 1920×720 nên chiều cao chỉ đổi CỠ
        // hình chiếu, không cắt mất gì ([ĐO] sau khi hạ: marker vẫn tròn, vẫn kéo được, biên vẫn thấy). 40dp tiết
        // kiệm ở đây nhân đôi vì nhóm có hai khung cùng loại — xem KDoc [bubble] về phần còn lại của R4.
        body.addView(rows.embed(badgeView!!, Sp.EMBED_M))
        body.addView(rows.note(context.getString(R.string.kachi_badge_drag_hint)))
    }

    private fun nudgeBadge(dx: Int, dy: Int) {
        val (x, y) = bridge.badgeCenter()
        bridge.setBadgeCenter(x + dx, y + dy)
        syncBadge()
    }

    /** Đọc lại toạ độ **đã kẹp** rồi áp lên cả ba bề mặt — nguồn sự thật là prefs, không phải biến trong màn. */
    private fun syncBadge() {
        val (x, y) = bridge.badgeCenter()
        badgeX?.setValue(px(x))
        badgeY?.setValue(px(y))
        badgeView?.setBadgeCenterCluster(x, y)
    }

    // ── Bong bóng VietMap ────────────────────────────────────────────────────────────────────────

    /**
     * Công tắc + vị trí (stepper + kéo-thả), cùng khuôn với biển báo.
     *
     * ⚠ Câu nhắc *"chỉnh được khi đang chiếu"* chỉ hiện khi `bridge.vmBubbleAdjustable()` = false: [ĐO]
     * `VmOverlayPosition.setAbsoluteTopLeft` **vẫn ghi prefs** nhưng chỉ bắn broadcast khi cast đang bật, nên
     * lúc cụm chưa chiếu thì kéo xong không thấy gì đổi trên cụm. Nói ra một câu rẻ hơn nhiều so với để người
     * dùng nghĩ tính năng hỏng — đúng luật *"cú bấm không có tác dụng thì phải nói lý do"*.
     */
    private fun bubble(body: LinearLayout) {
        body.addView(rows.subHeader(context.getString(R.string.kachi_sub_bubble)))
        body.addView(rows.checkRow(
            on = bridge.vmBubbleEnabled(),
            title = context.getString(R.string.kachi_bubble_enabled_title),
            sub = context.getString(R.string.kachi_bubble_enabled_sub),
        ) { on -> bridge.setVmBubbleEnabled(on) })
        // ⚠⚠ [R4 · ĐO] Nhóm này dài **2.68 màn cuộn** (trần R4 là 2), và phần dài nhất là bộ chỉnh vị trí bong bóng
        // — thứ mà lúc CHƯA CHIẾU thì kéo xong **không thấy gì đổi** (`VmOverlayPosition.setAbsoluteTopLeft` ghi
        // prefs nhưng chỉ bắn broadcast khi cast đang bật). Nên khi chưa chiếu: chỉ công tắc + một câu nói rõ vì
        // sao. Đây là cắt thứ KHÔNG DÙNG ĐƯỢC ở trạng thái hiện tại, không phải bỏ tính năng: bật chiếu lên là
        // toàn bộ bộ chỉnh trở lại (trang Cài đặt dựng lại theo lượt mở — xem KDoc lớp về "đọc lại, không chụp").
        if (!bridge.vmBubbleAdjustable()) {
            body.addView(rows.note(context.getString(R.string.kachi_bubble_need_cast)))
            return
        }

        val (bx, by) = bridge.vmBubblePos()
        bubbleX = rows.stepperRow(
            context.getString(R.string.kachi_bubble_pos_x), px(bx),
            onMinus = { nudgeBubble(-STEP_PX, 0) }, onPlus = { nudgeBubble(STEP_PX, 0) },
        )
        bubbleY = rows.stepperRow(
            context.getString(R.string.kachi_bubble_pos_y), px(by),
            onMinus = { nudgeBubble(0, -STEP_PX) }, onPlus = { nudgeBubble(0, STEP_PX) },
        )
        body.addView(bubbleX!!.view)
        body.addView(bubbleY!!.view)

        // ⚠ Khung của bong bóng KHÁC khung của biển báo — xem KDoc [ClusterNavBridge.vmBubbleFrame]: đường ghi
        // (`VmOverlayPosition.set`) kẹp vào hằng cố định 1920×720/371×158, nên bộ kéo-thả phải dựng trên ĐÚNG bốn
        // số đó. Lấy `bridge.clusterSize()` (kích cụm đang chiếu thật) sẽ làm bong bóng nhảy khỏi chỗ vừa thả.
        val (frameW, frameH) = bridge.vmBubbleFrame()
        val (bubbleW, bubbleH) = bridge.vmBubbleSize()
        bubbleView = VmBubblePlacementView(context, frameW, frameH, bubbleW, bubbleH, onMoved = { x, y ->
            bridge.setVmBubblePos(x, y)
            syncBubble()
        }).also { it.setBubbleTopLeftCluster(bx, by) }
        body.addView(rows.embed(bubbleView!!, Sp.EMBED_M))
        body.addView(rows.note(context.getString(R.string.kachi_bubble_drag_hint)))
    }

    private fun nudgeBubble(dx: Int, dy: Int) {
        val (x, y) = bridge.vmBubblePos()
        bridge.setVmBubblePos(x + dx, y + dy)
        syncBubble()
    }

    private fun syncBubble() {
        val (x, y) = bridge.vmBubblePos()
        bubbleX?.setValue(px(x))
        bubbleY?.setValue(px(y))
        bubbleView?.setBubbleTopLeftCluster(x, y)
    }

    private fun px(value: Int): String = context.getString(R.string.kachi_px_value, value)

    private companion object {
        /**
         * Một nấc stepper = **10 px trên CỤM**, không phải dp.
         *
         * Toạ độ badge/bong bóng lưu theo pixel của cụm (1920×720) — đổi sang dp ở đây sẽ nhân density của MÀN
         * GIỮA, một màn khác hẳn. 10px ≈ 0.5% bề ngang cụm: đủ nhỏ để canh, đủ lớn để không phải bấm 50 lần.
         */
        const val STEP_PX = 10
    }
}
