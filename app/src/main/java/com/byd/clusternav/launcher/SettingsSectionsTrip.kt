package com.byd.clusternav.launcher

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.byd.clusternav.R
import com.byd.clusternav.ShellReadiness
import com.byd.clusternav.launcher.trip.TripAppCodec
import com.byd.clusternav.launcher.trip.TripConfig
import com.byd.clusternav.launcher.trip.TripGate
import com.byd.clusternav.launcher.trip.TripMusicMode
import com.byd.clusternav.launcher.trip.TripStart
import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.modules.clustercast.ClusterProfile

/**
 * Hai cổng mà trang Cài đặt chuyến lên xe cần — chủ là `KachiHomeTrip` (màn chính). Cùng khuôn [ShortcutSettingsPort].
 */
interface TripSettingsPort {
    /** Ngăn kéo chọn app ở chế độ `AppDrawer.Mode.PICK_TRIP` (trần 6); [onApply] nhận gói theo thứ tự chạm. */
    fun openPicker(selected: List<String>, onApply: (List<String>) -> Unit)

    /** Ghi CẢ cấu hình chuyến — intent ViewModel (`HomeViewModel.setTripConfig`), KHÔNG ghi bền trực tiếp. */
    fun save(cfg: TripConfig)
}

/**
 * ═══ F2 · U6 — Cài đặt › Hệ thống & quyền › Khởi động › **Mở app khi nổ máy** ═══════════════════════════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` R2.1 · R2.5–R2.7. Đứng ngay dưới hai công tắc khởi động: cả
 * ba trả lời *"nổ máy thì Kachi làm gì"*. Nút *+ Thêm app* mở ngăn kéo (đa chọn, trần 6, nguồn = danh sách app có màn
 * khởi chạy như ngăn kéo "Ứng dụng"); mỗi app một hàng chip *Chạy nền · Mở bình thường · Bỏ* (một app *Mở bình thường*
 * duy nhất — phép sửa thuần [TripAppCodec.setMode]). Đời xe chưa biết màn camera ⇒ chip *Mở bình thường* mờ + một câu lý
 * do (R2.5, chuyến bỏ qua lúc chạy). Bố cục của hồ sơ không có ô app ⇒ cảnh báo chạy nền sẽ bị bỏ (§4.2.4). Cuối trang:
 * *"Đang chờ kênh"* (chạm ⇒ thẻ READY-AT-HOME) khi kênh chưa dùng được + kết quả chuyến gần nhất (R2.7).
 */
class SettingsTripAppsSection(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {
    private val cfg: TripConfig get() = deps.state().trip
    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var addButton: TextView? = null

    fun section(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_trip_title)))
        body.addView(rows.note(context.getString(R.string.kachi_trip_hint)))
        addButton = (rows.button(addText()) { openPicker() } as TextView).also { body.addView(it) }
        body.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        paint()
    }

    /** Tập đang chọn ĐỌC LẠI lúc mở (trang Cài đặt được nhớ lại, ảnh chụp lúc dựng có thể đã cũ). */
    private fun openPicker() = deps.trip.openPicker(cfg.apps.map { it.pkg }) { picked ->
        save(cfg.copy(apps = TripAppCodec.apply(cfg.apps, picked)))
    }

    private fun save(next: TripConfig) {
        if (next == cfg) return
        deps.trip.save(next)
        addButton?.text = addText()
        paint()
    }

    private fun paint() {
        list.removeAllViews()
        val st = deps.state()
        val cameraKnown = ClusterProfile.resolveCached(context).cameraSignature != null
        cfg.apps.forEach { a ->
            val options = listOf(
                BG to context.getString(R.string.kachi_trip_mode_bg),
                NORMAL to context.getString(R.string.kachi_trip_mode_normal),
                REMOVE to context.getString(R.string.kachi_trip_remove),
            )
            val row = rows.chipRow(appLabel(a.pkg), options, if (a.background) BG else NORMAL) { picked ->
                save(
                    cfg.copy(
                        apps = when (picked) {
                            REMOVE -> TripAppCodec.remove(cfg.apps, a.pkg)
                            else -> TripAppCodec.setMode(cfg.apps, a.pkg, background = picked == BG)
                        },
                    ),
                )
            }
            if (!cameraKnown) (row as? ViewGroup)?.getChildAt(1 + options.indexOfFirst { it.first == NORMAL })?.alpha = DIM
            // L4 · D5 — app hệ thống: chạy nền bị từ chối (R0.6, giữ nguyên) ⇒ chip *Chạy nền* MỜ + KHÔNG đổi kiểu được, chạm
            // chỉ nói lý do; một dòng lý do ngay dưới hàng. Đo bằng `FLAG_SYSTEM` (CÙNG phép của chuyến), không bảng tên gói.
            if (InstalledApps.isSystem(context, a.pkg)) {
                val reason = context.getString(R.string.kachi_trip_system_bg, appLabel(a.pkg))
                (row as? ViewGroup)?.getChildAt(1 + options.indexOfFirst { it.first == BG })?.apply {
                    alpha = DIM
                    setOnClickListener { Toast.makeText(context, reason, Toast.LENGTH_SHORT).show() }
                }
                list.addView(row)
                list.addView(rows.note(reason))
                return@forEach
            }
            list.addView(row)
        }
        if (!cameraKnown && cfg.apps.isNotEmpty()) list.addView(rows.note(context.getString(R.string.kachi_trip_normal_unsupported)))
        val count = EffectiveLayout.slotCount(st.preset, st.customLayout)
        if (cfg.apps.any { it.background } && st.workspace.slots.take(count).none { it is SlotContent.App }) {
            list.addView(rows.note(context.getString(R.string.kachi_trip_no_slot_warn)))
        }
        if (cfg.apps.isNotEmpty()) list.addView(rows.note(context.getString(R.string.kachi_trip_slot_hint)))
        statusRows(list, context, rows)
    }

    private fun addText() = context.getString(R.string.kachi_trip_add_n, cfg.apps.size, TripAppCodec.MAX)

    /** Nhãn app; đã gỡ ⇒ tên gói + "chưa cài" (vẫn hiện để người dùng bỏ được). */
    private fun appLabel(pkg: String): String =
        InstalledApps.labelOf(context, pkg) ?: context.getString(R.string.kachi_sc_not_installed, pkg)

    private companion object {
        const val BG = "B"
        const val NORMAL = "N"
        const val REMOVE = "X"
        const val DIM = 0.4f
    }
}

/**
 * ═══ F3 · U6 — Cài đặt › Hệ thống & quyền › Khởi động › **Tự mở nhạc khi lên xe** ═════════════════════════════════════
 *
 * Spec R3.1. 2.89 · A5(a) (spec `kachi-289-field-fixes.html`): rời trang Giọng nói, đứng NGAY dưới *"Mở app khi nổ máy"* —
 * cùng câu hỏi "nổ máy thì Kachi làm gì" (owner 05/10 hỏi về nhạc ở ô 1 khi đang đọc trang *Mở app khi nổ máy* — A2). KHÁC khoá với *"App nhạc
 * mặc định"* (vẫn ở Giọng nói): miền giá trị khác (§4.6 — dùng chung thì đổi nhạc-lên-xe âm thầm đổi app nhạc của giọng nói). Chip *Tắt · Theo player của xe ·
 * <app đã cài>* (YouTube / YT Music lấy từ bảng DỮ LIỆU [VoiceAppTargets], nhãn = nhãn app thật, không dịch) + ô
 * *"Phát gì"* (từ khoá hoặc link YouTube; trống = tiếp tục phiên của app) + một câu nói thật về giới hạn.
 */
class SettingsTripMusicSection(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {
    private val cfg: TripConfig get() = deps.state().trip
    private val extra = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    fun section(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_trip_music_title)))
        val installed = InstalledApps.launchable(context).mapTo(HashSet()) { it.pkg }
        val options = TripMusicMode.values().mapNotNull { m ->
            val pkg = VoiceAppTargets.byKey(m.targetKey)?.packageIn(installed)
            when {
                !m.plays -> m.code to context.getString(if (m == TripMusicMode.OFF) R.string.kachi_trip_music_off else R.string.kachi_trip_music_car)
                pkg != null -> m.code to (InstalledApps.labelOf(context, pkg) ?: pkg)
                m == cfg.music.mode -> m.code to context.getString(R.string.kachi_sc_not_installed, m.code)   // đã chọn mà gỡ: vẫn hiện
                else -> null
            }
        }
        body.addView(rows.chipRow(context.getString(R.string.kachi_trip_music_mode), options, cfg.music.mode.code) { code ->
            save(cfg.copy(music = cfg.music.copy(mode = TripMusicMode.of(code))))
        })
        body.addView(extra, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        paint()
    }

    private fun save(next: TripConfig) {
        if (next == cfg) return
        deps.trip.save(next)
        paint()
    }

    /** Ô "Phát gì" + câu giới hạn chỉ có nghĩa khi Kachi có việc (YouTube / YT Music). */
    private fun paint() {
        extra.removeAllViews()
        if (!cfg.music.mode.plays) return
        val q = cfg.music.query
        val label = if (q.isEmpty()) context.getString(R.string.kachi_trip_music_query) else context.getString(R.string.kachi_trip_music_query_set, q)
        extra.addView(rows.button(label) {
            SettingsDialogs.askText(context, context.getString(R.string.kachi_trip_music_query), cfg.music.query) { text ->
                save(cfg.copy(music = cfg.music.copy(query = text)))
            }
        })
        // L4 · D3(iii) — kiểu không "phát tiếp" ([TripMusicMode.resumable], dữ liệu của kiểu — lúc chạy vẫn quyết bằng phiên đo
        // được) mà ô "Phát gì" trống ⇒ nói thật: chỉ mở app; dán link để tự phát, hoặc chọn YT Music.
        if (!cfg.music.mode.resumable && q.isEmpty()) extra.addView(rows.note(context.getString(R.string.kachi_trip_music_link_hint)))
        extra.addView(rows.note(context.getString(R.string.kachi_trip_music_note)))
    }
}

/**
 * Hai hàng trạng thái của chuyến (R2.6/R2.7): kênh chưa dùng được ⇒ nút *"Đang chờ kênh"* (chạm ⇒ `ShellAccessUi.allowOrPrompt`
 * = thẻ READY-AT-HOME, không đường hiện thẻ thứ hai); kết quả chuyến gần nhất đọc từ sổ (chỉ đọc).
 */
private fun statusRows(list: LinearLayout, context: Context, rows: SettingsRows) {
    if (!ShellAccessUi.usableNow()) {
        list.addView(rows.button(context.getString(R.string.kachi_trip_wait_channel)) { ShellAccessUi.allowOrPrompt(context) })
    }
    // L4 · D1 — lần nổ máy NÀY chưa có kết quả (kênh không lên ⇒ chuyến không chạy — [ĐO `e2e-L4 · e12-channel-down` (bằng chứng phiên, ngoài repo)]) ⇒
    // nói ra, để dòng kết quả bên dưới không bị đọc nhầm là của lần này.
    when (TripStart.now(context)) {
        TripGate.Now.SHOWN -> Unit
        // A2 (4): đang chờ gì thì nói ra (*"đang chạy — chờ YouTube ở ô 1…"*) — cờ RAM chỉ để hiển thị (`TripStart.progress`).
        TripGate.Now.RUNNING -> list.addView(rows.note(
            TripStart.progress()?.let { context.getString(R.string.kachi_trip_now_waiting, tripWaitText(context, it)) }
                ?: context.getString(R.string.kachi_trip_now_running),
        ))
        TripGate.Now.NOT_RUN -> list.addView(rows.note(context.getString(
            if (ShellReadiness.isUp()) R.string.kachi_trip_now_wait_home else R.string.kachi_trip_now_wait_channel,
        )))
    }
    // L4 · D1(b): mã chuyến (RAN / NOOP / PARTIAL …) + MỘT câu cho từng bước — `SettingsTripResult.kt`.
    val r = TripStart.last(context) ?: return
    tripResultRows(list, context, rows, r)
}
