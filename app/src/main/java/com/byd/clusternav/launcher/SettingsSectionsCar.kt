package com.byd.clusternav.launcher

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.byd.clusternav.R
import com.byd.clusternav.comfort.Pm25Filter
import com.byd.clusternav.comfort.Pm25GaugeView
import com.byd.clusternav.comfort.SeatComfort
import com.byd.clusternav.comfort.SeatDiagramView
import com.byd.clusternav.launcher.camera.CameraSignalPolicy
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * Nhóm **"Tiện nghi xe"** (IA v2 §4.1 nhóm 8) — lấy gió trong (đã có từ W3) + ghế mát/sưởi và lọc bụi mịn
 * (chuyển từ màn ClusterNav, spec §4.3).
 *
 * Tách khỏi [SettingsSections] cùng lý do với các nhóm mới khác: một tệp cho một nhóm, trần 500 dòng (N1).
 *
 * ## Hai view TỰ VẼ nhúng thẳng — và vì sao được
 * [SeatDiagramView] và [Pm25GaugeView] khai `@JvmOverloads constructor(context, attrs, defStyle)` ⇒ dựng được
 * **thuần mã**, không cần XML, không cần gì từ `MainActivity` (spec R8 đã dự liệu đúng điều này). Chúng đi qua
 * [SettingsRows.embed] để có nền + lề + một chiều cao TƯỜNG MINH — thả thẳng vào cột Settings thì view tự vẽ
 * `WRAP_CONTENT` có thể cao **0px** mà không báo lỗi gì.
 *
 * ## N5 — hai lượt đọc HAL đều BẤT ĐỒNG BỘ
 * `bridge.seatCount()` (dò `BydHal` bằng reflection) và `bridge.pm25Level()` (đọc HAL) chạy trên thread nền rồi
 * post về luồng vẽ. Sơ đồ ghế vì thế dựng với **2 ghế** (mặc định Seal) rồi tự sửa lại khi biết số thật — chứ
 * không chặn luồng vẽ chờ HAL như màn cũ làm.
 */
class SettingsCarSection(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {

    private val bridge get() = deps.bridge

    private var seatView: SeatDiagramView? = null
    private var gaugeView: Pm25GaugeView? = null
    private lateinit var pm25Row: SettingsRows.StatusRow

    fun build(body: LinearLayout) {
        recirc(body)
        rainDefrost(body)
        cameraSignal(body)
        seats(body)
        pm25(body)
    }

    // ── Camera theo xi-nhan (owner 2026-09-22) — ĐANG PHÁT TRIỂN, mặc định TẮT ────────────────────
    /**
     * Công tắc *"Camera theo xi-nhan"* — xi-nhan trái → camera trái (overlay bên trái), phải → phải. RE
     * `docs/diagnostics/camera-panorama-RE-2026-09-22.md` (`BYDAutoPanoramaDevice`). Mặc định TẮT vì tín hiệu
     * video vào overlay của app CHƯA verify trên xe (runbook option A–J). Dùng `Lang.t` inline (feature dev, chưa
     * đưa vào danh mục SettingsCatalog/i18n — tránh ghim số khi còn thử nghiệm).
     */
    private fun cameraSignal(body: LinearLayout) {
        body.addView(rows.subHeader(context.getString(R.string.kachi_sub_camera_signal)))
        body.addView(rows.checkRow(
            on = bridge.cameraSignal(),
            title = context.getString(R.string.kachi_camera_signal_title),
            sub = context.getString(R.string.kachi_camera_signal_sub),
        ) { on -> bridge.setCameraSignal(on) })
        body.addView(rows.checkRow(
            on = bridge.cameraOnCluster(),
            title = context.getString(R.string.kachi_camera_cluster_title),
            sub = context.getString(R.string.kachi_camera_cluster_sub),
        ) { on -> bridge.setCameraOnCluster(on) })
        // VỊ TRÍ GÓC từng bên (spec `camera-turn-signal-hal-socket.html` R3 · R4). Hai hàng RIÊNG vì owner chốt
        // "trái vẫn có thể hiện bên phải" — người lái ngồi bên trái nên góc trên-trái có thể bị vành lái che.
        // Mã chip = đúng giá trị lưu bền ("TL"/"TR", hằng ở `:core`) ⇒ không có bảng đổi mã↔nhãn thứ hai.
        val corners = listOf(
            CameraSignalPolicy.CORNER_TOP_LEFT to context.getString(R.string.kachi_pos_tl),
            CameraSignalPolicy.CORNER_TOP_RIGHT to context.getString(R.string.kachi_pos_tr),
        )
        body.addView(rows.subHeader(context.getString(R.string.kachi_camera_pos_sub)))
        body.addView(rows.chipRow(
            context.getString(R.string.kachi_camera_pos_left), corners, bridge.cameraPosLeft(),
        ) { v -> bridge.setCameraPos(left = true, v = v) })
        body.addView(rows.chipRow(
            context.getString(R.string.kachi_camera_pos_right), corners, bridge.cameraPosRight(),
        ) { v -> bridge.setCameraPos(left = false, v = v) })
        // XOAY video (spec R7 · owner 2026-09-26: "bị ngang, cần dọc lại; trái xoay qua trái, phải xoay sang phải,
        // thêm option rotation trong setting"). MỘT hàng cho cả hai bên vì mặc định "Theo bên" đã mã hoá trái ↺ /
        // phải ↻; năm chip còn lại cho xe ghép ảnh 4-in-1 khác chiều (SL6…). Mã chip = giá trị lưu bền (hằng `:core`).
        // Chip "Theo bên, ngược lại" là đường HOÀN TÁC mà §Verification của spec hứa: chiều đúng còn ở mức [SUY],
        // và nếu sai thì cái sai là CẶP theo bên — `↺90`/`↻90` (áp cả hai bên) không diễn tả nổi cặp đảo đó.
        val rotations = listOf(
            CameraSignalPolicy.ROTATE_BY_SIDE to context.getString(R.string.kachi_camera_rot_side),
            CameraSignalPolicy.ROTATE_BY_SIDE_INV to context.getString(R.string.kachi_camera_rot_side_inv),
            CameraSignalPolicy.ROTATE_NONE to context.getString(R.string.kachi_camera_rot_none),
            CameraSignalPolicy.ROTATE_LEFT to context.getString(R.string.kachi_camera_rot_left),
            CameraSignalPolicy.ROTATE_RIGHT to context.getString(R.string.kachi_camera_rot_right),
            CameraSignalPolicy.ROTATE_180 to context.getString(R.string.kachi_camera_rot_180),
        )
        body.addView(rows.subHeader(context.getString(R.string.kachi_camera_rot_sub)))
        body.addView(rows.chipRow(
            context.getString(R.string.kachi_camera_rot_title), rotations, bridge.cameraRotation(),
        ) { v -> bridge.setCameraRotation(v) })
        // Chọn cameraId từng bên (owner 2026-09-25: cho SL6/xe khác tự dò cam nào lên — cam gương xe khác id khác).
        val camIds = listOf("0" to "0", "1" to "1", "2" to "2", "3" to "3", "4" to "4", "5" to "5")
        body.addView(rows.subHeader(context.getString(R.string.kachi_camera_pick_sub)))
        body.addView(rows.chipRow(context.getString(R.string.kachi_camera_pick_left), camIds, bridge.cameraCamLeft().toString()) { v -> bridge.setCameraCamLeft(v.toInt()) })
        body.addView(rows.chipRow(context.getString(R.string.kachi_camera_pick_right), camIds, bridge.cameraCamRight().toString()) { v -> bridge.setCameraCamRight(v.toInt()) })
    }

    // ── AUTOMATION #1 · Tự sấy kính khi mưa ──────────────────────────────────────────────────────

    /**
     * Công tắc *"Tự sấy kính khi mưa"* (1.85, spec `kachi-automation.html` R1.1) — theo XE, mặc định TẮT.
     *
     * ## Vì sao ở nhóm *Tiện nghi xe* và đứng ngay sau lấy-gió-trong
     * Nhóm chia theo **thứ người dùng đang nghĩ tới** (KDoc [SettingsGroup]). Người ta vào đây để chỉnh những thứ
     * cabin tự làm hộ khi nổ máy/khi đang đi; đây đúng là thứ thứ hai trong danh sách đó. Đặt nó ở nhóm *Hệ
     * thống* (cùng chỗ với autostart) sẽ đúng về **cơ chế** (nó là một dịch vụ nền) mà sai về **chỗ người dùng đi
     * tìm** — cùng ranh giới mà sổ địa chỉ đã chọn khi nằm ở *Dẫn đường* dù dữ liệu theo hồ sơ.
     *
     * Bật/tắt đi qua cầu (`bridge.setRainDefrost`), và chính cầu đồng bộ động cơ nền ngay trong lượt đó — xem ⚠ ở
     * KDoc `ClusterNavBridgeAutomation` về vì sao lượt `sync` không được để chỗ gọi nhớ.
     *
     * ## V7 (owner 2026-09-25) — hai ô CON: *"Sấy kính trước"* · *"Sấy kính sau + gương"*
     * Owner chốt tách hai lựa chọn để dùng riêng được từng cái (kính sau + gương ăn điện liên tục). Cả hai **mặc
     * định BẬT** ⇒ ai không vào đây thì hành vi y như 1.85. Bỏ tích cả hai = tính năng tắt trên thực tế
     * ([RainDefrostApplier.selection] rỗng) — cố ý KHÔNG lùi về *"ghi cả hai"*, vì một người vừa bỏ tích cả hai ô
     * mà thấy xe bật cả hai cái sấy sẽ không có cách nào hiểu vì sao.
     */
    private fun rainDefrost(body: LinearLayout) {
        body.addView(rows.subHeader(context.getString(R.string.kachi_sub_rain_defrost)))
        // V7 (owner 2026-09-25) — hai ô CON: chọn kính nào được sấy. Dựng TRƯỚC công tắc chính để cú gạt công tắc
        // có tham chiếu tới chúng mà làm mờ/khoá ngay; thứ tự trên MÀN vẫn là chính → con (addView bên dưới).
        val front = rows.checkRow(
            on = bridge.rainDefrostFront(),
            title = context.getString(R.string.kachi_rain_defrost_front),
            sub = context.getString(R.string.kachi_rain_defrost_front_sub),
        ) { on -> bridge.setRainDefrostFront(on) }
        val rear = rows.checkRow(
            on = bridge.rainDefrostRear(),
            title = context.getString(R.string.kachi_rain_defrost_rear),
            sub = context.getString(R.string.kachi_rain_defrost_rear_sub),
        ) { on -> bridge.setRainDefrostRear(on) }
        body.addView(rows.checkRow(
            on = bridge.rainDefrost(),
            title = context.getString(R.string.kachi_rain_defrost_title),
            sub = context.getString(R.string.kachi_rain_defrost_sub),
        ) { on ->
            bridge.setRainDefrost(on)
            gateRainGlass(front, rear, on)
        })
        body.addView(front)
        body.addView(rear)
        gateRainGlass(front, rear, bridge.rainDefrost())
        body.addView(rows.note(context.getString(R.string.kachi_rain_defrost_note)))
    }

    /**
     * Công tắc chính TẮT ⇒ hai ô con **mờ + không bấm được** (V7). MỜ, không ẩn — bài học U12: *"cắt vì nhóm dài
     * không được biến thành ẩn tính năng"*; ẩn đi thì người bật công tắc lên không biết là có hai lựa chọn.
     *
     * ## Vì sao `isEnabled` trên hàng là ĐỦ ở đây (và vì sao thường thì không)
     * [SettingsRows.Stepper.isEnabled] có một KDoc dài về việc cờ `enabled` của cha **không** lan xuống con
     * ([ĐO] AOSP `View.setEnabled` không đệ quy, `ViewGroup.dispatchTouchEvent` không đọc cờ đó) — nên tắt một
     * hàng có **nút con bấm được** là khoá giả. Hàng của [SettingsRows.checkRow] thì khác: nó giữ
     * `setOnClickListener` trên **chính** `LinearLayout` gốc và **không con nào clickable**, nên cú chạm rơi về
     * `onTouchEvent` của đúng view đang bị tắt (`View.java` nhánh `DISABLED` trả về mà KHÔNG gọi listener).
     * Không dựa vào `alpha`: alpha chỉ là chuyện VẼ, một hàng mờ vẫn ăn cú chạm.
     */
    private fun gateRainGlass(front: View, rear: View, on: Boolean) {
        listOf(front, rear).forEach {
            it.isEnabled = on
            it.alpha = if (on) 1f else 0.4f
        }
    }

    // ── Lấy gió trong ────────────────────────────────────────────────────────────────────────────

    /**
     * W3 — ô tick "nổ máy thì tự lấy gió trong". Câu chữ giữ **nguyên văn**, kể cả câu *"chưa kiểm trên xe"*
     * (R10): mã `recirc` ở tier OVERDRIVE, chưa xác nhận trên xe owner. `Goi2FeatureWiringContractTest` đọc
     * CHÍNH tệp tài nguyên nên câu cảnh báo không thể biến mất mà bài canh vẫn xanh.
     *
     * Bật ⇒ áp NGAY (không chờ lần nổ máy sau); tắt ⇒ CHỈ đặt lại cờ, KHÔNG tắt chế độ đang bật trên xe (người
     * dùng có thể đang muốn dùng, chỉ là không muốn tự bật nữa) — hành vi chuyển **y nguyên** từ `HomePanels`.
     *
     * Đường "áp ngay" nay nằm ở cầu (`applyRecircNow`) — nhóm này 100% đi qua cầu (N2), không còn ngoại lệ.
     */
    private fun recirc(body: LinearLayout) {
        // ⚠ [ĐO] soát ảnh Settings v2: nhóm này TRƯỚC ĐÂY mở đầu thẳng bằng thẻ ô tick ⇒ thẻ dán sát mép trên vùng
        // cuộn và nhóm là nhóm DUY NHẤT không có tiêu đề mục ở dòng đầu. Lề trên của thẻ thì không chữa được (nó
        // phải bằng lề giữa hai thẻ), nên cách nhất quán với 7 nhóm kia là có TIÊU ĐỀ MỤC — `sectionLabel` đã mang
        // sẵn lề trên `Sp.L` (xem [SettingsRows.stackLp]), tức vừa vá khoảng cách vừa vá thứ bậc bằng một dòng.
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_sec_recirc)))
        body.addView(rows.checkRow(
            on = bridge.recircOnStart(),
            title = context.getString(R.string.kachi_recirc_title),
            sub = context.getString(R.string.kachi_recirc_sub),
        ) { on ->
            bridge.setRecircOnStart(on)
            if (on) bridge.applyRecircNow()
        })
    }

    // ── Ghế mát / sưởi ───────────────────────────────────────────────────────────────────────────

    /**
     * Công tắc + chế độ (mát/sưởi) + sơ đồ ghế chạm-để-đổi-mức.
     *
     * Mức từng ghế ghi qua `bridge.setSeatLevel(i, level)` — [ĐO] KDoc cầu: đường đó dùng
     * `SeatComfortApplier.applySeat` cho **đúng ghế đó**, KHÔNG dùng đường bulk `applyNow` (đường bulk bỏ qua
     * mức "Tắt" ⇒ trước v1.34 không tắt được ghế). Đây là loại chi tiết mà chép nhầm thì lỗi quay lại y hệt.
     */
    private fun seats(body: LinearLayout) {
        body.addView(rows.subHeader(context.getString(R.string.kachi_sub_seat)))
        body.addView(rows.checkRow(
            on = bridge.seatEnabled(),
            title = context.getString(R.string.kachi_seat_enabled_title),
            sub = context.getString(R.string.kachi_seat_enabled_sub),
        ) { on -> bridge.setSeatEnabled(on) })
        body.addView(rows.chipRow(
            label = context.getString(R.string.kachi_seat_mode),
            options = listOf(
                SeatComfort.SeatMode.COOL.ordinal.toString() to context.getString(R.string.kachi_seat_cool),
                SeatComfort.SeatMode.HEAT.ordinal.toString() to context.getString(R.string.kachi_seat_heat),
            ),
            current = bridge.seatMode().toString(),
        ) { code ->
            code.toIntOrNull()?.let {
                bridge.setSeatMode(it)
                seatView?.setMode(it == SeatComfort.SeatMode.COOL.ordinal)
            }
        })
        val view = SeatDiagramView(context).apply {
            setMode(bridge.seatMode() == SeatComfort.SeatMode.COOL.ordinal)
            // ⚠ Bảng màu phải do CHỖ NHÚNG cấp: view đọc `@color` theo uiMode của MÁY, còn thẻ bọc nó tô theo
            // [KachiTheme] (công tắc sáng/tối RIÊNG của launcher). [ĐO] soát ảnh v2: máy sáng + Kachi tối ⇒ thân xe
            // `#e5e7ee` nằm giữa thẻ tối, là hình chữ nhật sáng duy nhất trên màn. Nền lấy `FIELD` (không phải nền
            // thẻ `CELL`) để thân xe vẫn tách khỏi thẻ; ghế tắt lấy `CARD2` — cùng cặp mà ô tick đang dùng.
            setPalette(
                body = KachiTheme.c(KachiTheme.FIELD), seatOff = KachiTheme.c(KachiTheme.CARD2),
                line = KachiTheme.c(KachiTheme.LINE_STRONG), ink = KachiTheme.c(KachiTheme.INK),
                mutedInk = KachiTheme.c(KachiTheme.MUT), cool = KachiTheme.c(KachiTheme.CYAN),
                warm = KachiTheme.c(KachiTheme.AMBER),
            )
            onSeatLevelChanged = { seat, level -> bridge.setSeatLevel(seat, level) }
        }
        seatView = view
        body.addView(rows.embed(view, Sp.EMBED_M))
        body.addView(rows.note(context.getString(R.string.kachi_seat_hint)))
        // Số ghế đọc HAL trên thread nền (N5): dựng xong mới sửa lại, và chỉ mức của những ghế THẬT mới nạp.
        bridge.seatCount { count ->
            view.setSeatCount(count)
            repeat(count) { i -> view.setLevel(i, bridge.seatLevel(i)) }
        }
    }

    // ── Lọc bụi mịn ──────────────────────────────────────────────────────────────────────────────

    /**
     * Công tắc tự lọc + nút "Lọc ngay" + mức bụi hiện tại (dòng trạng thái **và** đồng hồ).
     *
     * "Lọc ngay" chạy **bất kể** công tắc auto — lặp lại `MainActivity.kt:1303`: đó là một việc làm tức thời,
     * không phải một cấu hình, nên nó không được phụ thuộc cấu hình.
     */
    private fun pm25(body: LinearLayout) {
        body.addView(rows.subHeader(context.getString(R.string.kachi_sub_pm25)))
        body.addView(rows.checkRow(
            on = bridge.pm25Enabled(),
            title = context.getString(R.string.kachi_pm25_title),
            sub = context.getString(R.string.kachi_pm25_sub),
        ) { on -> bridge.setPm25Enabled(on) })
        pm25Row = rows.statusRow(KachiTheme.MUT2, context.getString(R.string.kachi_pm25_level, DASH))
        body.addView(pm25Row.view)
        val gauge = Pm25GaugeView(context)
        gaugeView = gauge
        body.addView(rows.embed(gauge, Sp.EMBED_S))
        body.addView(rows.button(context.getString(R.string.kachi_pm25_clean_now)) {
            bridge.pm25CleanNow()
            refreshPm25()
        })
        refreshPm25()
    }

    /** Đọc mức bụi (thread nền) rồi cập nhật CẢ dòng chữ lẫn đồng hồ — hai bề mặt, một lượt đọc. */
    private fun refreshPm25() = bridge.pm25Level { level ->
        pm25Row.update(pm25Colour(level), context.getString(R.string.kachi_pm25_level, pm25Label(level)))
        gaugeView?.setLevel(level)
    }

    /**
     * Nhãn mức bụi — tra tài nguyên launcher theo hằng của `:core` (`Pm25Filter.EXCELLENT`…`SERIOUS`).
     *
     * KHÔNG gọi `Pm25Filter.levelLabelVi/En`: hai hàm đó là bảng nhãn **của nhánh ClusterNav** (dịch lúc chạy
     * bằng `Lang.t`), còn tầng `launcher/` bắt mọi chữ đi qua tài nguyên. Mã số thì dùng chung, chữ thì mỗi bên
     * một nguồn — đúng ranh giới mà `ClusterNavBridgeMsg` lập ra.
     */
    private fun pm25Label(level: Int): String = context.getString(
        when (level) {
            Pm25Filter.EXCELLENT -> R.string.kachi_pm25_excellent
            Pm25Filter.GOOD -> R.string.kachi_pm25_good
            Pm25Filter.LOW_GRADE -> R.string.kachi_pm25_low
            Pm25Filter.MIDDLE -> R.string.kachi_pm25_middle
            Pm25Filter.HEAVY -> R.string.kachi_pm25_heavy
            Pm25Filter.SERIOUS -> R.string.kachi_pm25_serious
            else -> R.string.kachi_pm25_unknown
        },
    )

    /** Màu = mức bẩn, dùng lại đúng ngưỡng `Pm25Filter.isDirty` để dòng chữ và cái đồng hồ không nói khác nhau. */
    private fun pm25Colour(level: Int): String = when {
        level == Pm25Filter.INVALID -> KachiTheme.MUT2
        Pm25Filter.isDirty(level) -> KachiTheme.RED
        level >= Pm25Filter.LOW_GRADE -> KachiTheme.AMBER
        else -> KachiTheme.GREEN
    }

    private companion object {
        /** "Chưa đọc được" — cùng ký hiệu với mọi chỗ off-car của launcher. */
        const val DASH = "—"
    }
}
