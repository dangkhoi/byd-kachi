package com.byd.clusternav.launcher

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.byd.clusternav.R
import com.byd.clusternav.launcher.SlotHeadActions.Button
import com.byd.clusternav.launcher.SlotRevertPlan.Event
import com.byd.clusternav.launcher.SlotRevertPlan.Next
import com.byd.clusternav.launcher.behind.BehindReason

/**
 * ═══ L6 · VÒNG ĐỜI Ô — keo của màn chính cho (a) app chết · (b) hết lượt đặt tạm · (c) nút *chạy nền* / *tắt* ═══════════
 *
 * Owner 03/10 (xe thật, 2.86) — ba yêu cầu, MỘT luật: mọi lối kết thúc nội dung đang hiện của ô đi qua [revert] ⇒
 * [SlotRevertPlan] (`:core`, đọc ĐÚNG hai lớp LƯU / đang HIỆN của state) ⇒ chỉ lớp TẠM đổi, không ghi hồ sơ. Không đường
 * nào ở đây tự quyết "ô về đâu".
 *
 * | Lối vào | Phần Android TRƯỚC khi state đổi | Sự kiện |
 * |---|---|---|
 * | nhịp đo thấy app rời màn ảo ([onAppGone]) | host thôi giữ app (không `force-stop`) | `APP_DIED` |
 * | … mà task còn ở display khác (2.93 · R3: tự `launchToSide` / mở toàn màn / chiếu cụm) | như trên + một câu báo đúng | `APP_ELSEWHERE` |
 * | *tắt* ô app | `am stack remove` ĐÚNG stack của app trên màn ảo ô ([SlotCloseRun], luồng nền) → đọc lại → host thôi giữ | `APP_CLOSED` |
 * | *chạy nền* ô app | 2.89-thử1 (ô 7, spec 287 §4.6d): host ĐỖ app trong chính màn ảo của nó ([VdAppHost.park], 0 lệnh shell) — đường lớp che + BEHIND-HOME ([toBack]) giữ biên dịch, không gọi | `APP_BACKGROUND` |
 * | *tắt* ô widget | — (chỉ lớp tạm; id widget bên thứ ba ở lớp LƯU nên không bị thu hồi) | `WIDGET_CLOSED` |
 *
 * Vì sao "host thôi giữ app" phải đi TRƯỚC `applySlotRevert`: lượt render nhả host của ô, mà `VdAppHost.release` còn giữ
 * gói thì `am force-stop` cả gói — đúng thứ owner không muốn khi app chỉ vừa chết / vừa bị gỡ khỏi Ô (có thể còn dịch vụ
 * tiền cảnh: dẫn đường, nhạc) — với *chạy nền* thì `force-stop` là giết đúng app vừa đưa ra sau màn nhà. Cổng kênh:
 * `ShellAccessUi.allowOrPrompt` ngay trước lệnh; cổng thi hành READY-AT-HOME vẫn đứng sau (kênh của màn chính). Luồng
 * chính, trừ lượt gỡ stack / chuỗi chạy nền.
 */
internal class KachiHomeSlotActions(
    private val activity: Activity,
    private val viewModel: HomeViewModel,
    private val workspace: () -> WorkspaceView,
    /** Kênh shell (dadb) — `null` khi chưa dò ra; đọc MỖI LẦN (gán ở luồng nền sau khi màn mở). */
    private val shell: () -> ((String) -> String)?,
    /** L8 — chuỗi *chạy nền* của ô (`KachiHomeSlots.toBack`: ô, màn ảo, gói, kết quả "đã rời ô" + lý do trên luồng chính). */
    private val toBack: (Int, Int, String, (BehindReason.Report) -> Unit) -> Unit,
    /** Soát 2.87 · P3 — BEHIND-HOME còn dùng được trong tiến trình (`KachiHomeSlots.behindUsable`); `false` ⇒ không nút *chạy nền*. */
    private val behindUsable: () -> Boolean,
    /** Cửa duy nhất xuống luồng nền của màn chính (đã huỷ ⇒ tự bỏ) — `KachiHomeActivity.submitBg`. */
    private val submitBg: (() -> Unit) -> Boolean,
) : SlotActionsPort {

    private val main = Handler(Looper.getMainLooper())
    private val closer by lazy { SlotCloseRun(activity.packageName) }

    /**
     * Ô đang có MỘT việc đầu ô đang bay (*chạy nền*: chuỗi `kachi-behind` 1–5 s · *tắt*: `am stack remove` + đọc lại trên luồng
     * nền). Soát 2.87 · P3: trước chỉ *chạy nền* xét tập này ⇒ *tắt* chạy CHỒNG lên chuỗi chạy nền của cùng ô — hai bên thi hành
     * không xếp hàng nhau, bên thua đọc lại thấy app đã rời ô và báo "không làm được" dù ô vừa đổi. Một việc một ô một lúc.
     */
    private val busy = HashSet<Int>()

    override fun buttons(index: Int, kind: SlotHeadRest.Kind, projector: SlotHeadRest.Projector, hostLive: Boolean): List<Button> =
        // Kênh dùng được NGAY + khung có bộ chiếu màn ảo chưa nhả. Lượt mở app chưa bắt đầu ⇒ *tắt* vẫn làm được (thả host, 0
        // lệnh); *chạy nền* lúc app chưa mở xong ⇒ 0 lệnh + log (hiếm) — thà vậy còn hơn nút kẹt ẩn ở chế độ "luôn hiện".
        // 2.89-thử1 (ô 7): *chạy nền* = ĐỖ trong màn ảo của ô — không đi qua BEHIND-HOME ⇒ không phụ thuộc [behindUsable].
        SlotHeadActions.of(kind, projector, ShellAccessUi.usableNow() && hostLive, behind = true)

    override fun onAction(index: Int, button: Button) {
        when (button) {
            Button.CLOSE -> close(index)
            Button.BACKGROUND -> park(index)
            Button.SWAP -> Unit   // ⇄ có đường riêng (`WorkspaceView.slotHead` → ngăn kéo)
        }
    }

    /**
     * 2.93 · SLOT-APP-ESCAPE + SHORTCUTS-B-ESCAPE (spec `kachi-293-slot.html` R3): [elsewhere] = app RA KHỎI ô mà vẫn mở ở display
     * khác ([ĐO máy ảo] Waze `launchToSide` ⇒ toàn màn display 0). Ô đi CÙNG luật hoàn ô như app vừa rời (owner 04/10: trong suốt /
     * widget LƯU về — không kéo app nào khác vào khung); khác duy nhất: một câu NÓI ĐÚNG (trước 2.93 coi là "app chết", câu giọng
     * nói "✓ Mở … vào ô n" đứng không đính chính). Không lệnh nào chạm app đó (không K12, không kéo về ô — cơ chế MỚI, chưa đo
     * trên app thật thoát ô, CLAUDE.md §14). Câu nói TRƯỚC [revert] để [sayIfStill] còn thấy ô hiện [pkg].
     */
    override fun onAppGone(index: Int, pkg: String, elsewhere: Boolean) {
        if (elsewhere) sayIfStill(index, R.string.kachi_slot_app_elsewhere, pkg)
        revert(index, if (elsewhere) Event.APP_ELSEWHERE else Event.APP_DIED, pkg)
    }

    private fun shownAt(index: Int): SlotContent =
        viewModel.uiState.value.effectiveWorkspace.slots.getOrElse(index) { SlotContent.Empty }

    private fun close(index: Int) {
        if (index in busy) return
        when (val shown = shownAt(index)) {
            is SlotContent.App -> closeApp(index, shown.pkg)
            is SlotContent.Widget, is SlotContent.AppWidget -> revert(index, Event.WIDGET_CLOSED, null)
            SlotContent.Empty -> Unit
        }
    }

    /**
     * *Tắt* app [pkg] của ô [index] — bốn câu CLAUDE.md §4 ở KDoc [SlotClosePlan]: display = màn ảo CỦA Ô (host giữ, không
     * quét) · app = đúng gói ô đang giữ · stack `standard` bằng chữ, không ghim · không hoàn tác được (≈ vuốt khỏi Gần đây;
     * mở lại bằng ⇄ / lối tắt / khởi động lại). Gỡ xong (hoặc app đã không còn ở màn ảo) ⇒ luật hoàn ô; chưa gỡ được ⇒ ô
     * giữ nguyên + một câu báo. Không `am force-stop`.
     *
     * A3 · SLOT-CLOSE-SETTLE (2.89, [ĐO xe 05/10]: lệnh gỡ trên xe chậm hơn cửa sổ đọc lại 5 × 250 ms ⇒ báo nhầm *"chưa tắt
     * được"*, ô chỉ trống ~27 s sau ở nhịp đo ô): lệnh đã GỬI = ĐANG TẮT ⇒ giấu mặt vẽ ngay ([VdAppHost.closing], không khung
     * đứng); bản đọc xác nhận rời ô (lịch ~4 s, `SlotCloseSettle`) ⇒ luật hoàn ô; hết lịch mà app còn ⇒ hiện lại
     * mặt vẽ + câu báo (chỉ khi ô vẫn hiện app).
     */
    private fun closeApp(index: Int, pkg: String) {
        if (!ShellAccessUi.allowOrPrompt(activity)) return
        val host = workspace().hostAt(index)
        val stage = host?.stage()
        if (stage == null && host?.holds(pkg) == true) {
            // Bộ chiếu chưa mở app vào màn ảo (chưa có mặt vẽ ⇒ chưa có task nào của nó ở đó): không có gì để gỡ — thả host
            // (lượt mở chưa bắt đầu sẽ không bao giờ bắt đầu) rồi luật hoàn ô. 0 lệnh shell; nút không "chết" trong lúc mở.
            Log.i(TAG, "ô $index: tắt $pkg — chưa mở vào màn ảo, 0 lệnh")
            return revert(index, Event.APP_CLOSED, pkg)
        }
        val sh = shell()
        if (host == null || stage == null || stage.pkg != pkg || sh == null) { Log.i(TAG, "ô $index: tắt $pkg — ô chưa sẵn, 0 lệnh"); return }
        busy += index
        val accepted = submitBg {
            val r = closer.run(sh, stage.vd, pkg) { main.post { host.closing(pkg, on = true) } }
            Log.i(TAG, "ô $index: ${r.line()}")
            main.post {
                busy -= index
                if (r.slotFree) revert(index, Event.APP_CLOSED, pkg)
                else { host.closing(pkg, on = false); sayIfStill(index, R.string.kachi_slot_close_failed, pkg) }
            }
        }
        if (!accepted) { busy -= index; say(R.string.kachi_slot_close_failed, pkg) }
    }

    /**
     * ═══ Ô 7 · A (2.89-thử1 · bản THỬ, spec 287 §4.6d) — *chạy nền* = ĐỖ ẨN ═══
     *
     * Owner 05/10 trên xe: *"sao ko giả lập 1 ô số 7 gì đó, để nhét các app chạy nền vào đó"*. [ĐO xe 05/10] chuỗi lớp che +
     * BEHIND-HOME ([backgroundCovered]) hỏng ở bước giữ chỗ (NPE trong system_server), còn dời app sang display 0 thì app
     * relaunch (nhạc dừng). Nay: host ĐỖ app trong CHÍNH màn ảo của ô ([VdAppHost.park] — 0 lệnh shell, không `force-stop`,
     * không đổi cỡ, không dời task) rồi luật hoàn ô `APP_BACKGROUND` (khung trong suốt; ô LƯU widget ⇒ widget về). Mở lại app
     * vào ô bằng mọi đường (⇄ · lối tắt · giọng nói) ⇒ nhận lại đúng màn ảo đó. Không đỗ được (lượt mở dở · app đã chết ·
     * đang toàn màn) ⇒ một câu MANG LÝ DO, ô giữ app. Không kênh shell nào được chạm ⇒ không cổng kênh; app hệ thống đỗ được
     * (R0.6 chặn `move-task` app hệ thống — đường này không dời task nào).
     */
    private fun park(index: Int) {
        val pkg = (shownAt(index) as? SlotContent.App)?.pkg ?: return
        if (index in busy) return
        val parked = workspace().hostAt(index)?.park() == true
        Log.i(TAG, "ô $index: chạy nền $pkg ⇒ ${if (parked) "đỗ ô 7 ${ParkedApps.summary()}" else "không đỗ được (ô chưa sẵn) · 0 lệnh"}")
        if (parked) revert(index, Event.APP_BACKGROUND, pkg) else say(R.string.kachi_sc_bg_failed_why, pkg, PARK_NOT_READY)
        workspace().heads.refreshAll()
    }

    /**
     * ⚠ 2.89-thử1: KHÔNG còn chỗ gọi (nút *chạy nền* đi [park]) — giữ biên dịch để bản sau quyết khi BEHIND-HOME được chữa.
     *
     * L8 — *chạy nền* app đang hiện ở ô [index], MỌI ô app (owner 03/10; D-L6-1 mở khoá): [toBack] chạy chuỗi lớp che trên
     * màn ảo CỦA Ô (`BehindHomeSequence.evictCovered` — display = màn ảo ô, app = đúng gói ô, stack `standard`, màn nhà phải ở
     * đỉnh display 0); bản đọc cuối thấy app đã rời ô ⇒ luật hoàn ô (`APP_BACKGROUND`: ô LƯU widget ⇒ widget về, còn lại ⇒
     * trong suốt — owner 04/10); không ⇒ ô giữ app + một câu MANG LÝ DO NGẮN (lỗi xe 2.87: *"bấm vào nó đen cái khung, xong rồi
     * lại lòi lên lại"* — chưa có log xe ⇒ CLAUDE.md §11: câu trên ảnh chụp phải nói chuỗi dừng ở đâu) + một dòng `KachiSlotLife`
     * mang dòng `KachiBehind` đầy đủ. App hệ thống ⇒ nói lý do, 0 lệnh (R0.6, cùng phép `InstalledApps.isSystem` với chip
     * *Chạy nền* của Cài đặt). Host chưa sẵn ⇒ 0 lệnh.
     */
    @Suppress("unused")
    private fun backgroundCovered(index: Int) {
        val pkg = (shownAt(index) as? SlotContent.App)?.pkg ?: return
        if (index in busy) return
        // P3: BEHIND-HOME đã tắt cả tiến trình (sau `ANCHOR_IN_FRONT`) ⇒ hỏi lại nút (nút chạy nền biến mất), 0 lệnh, không toast.
        if (!behindUsable()) { Log.i(TAG, "ô $index: chạy nền $pkg — BEHIND-HOME đã tắt, 0 lệnh"); workspace().heads.refreshAll(); return }
        if (InstalledApps.isSystem(activity, pkg)) { say(activity.getString(R.string.kachi_sc_refuse_system)); return }
        if (!ShellAccessUi.allowOrPrompt(activity)) return
        val stage = workspace().hostAt(index)?.stage()
        if (stage == null || stage.pkg != pkg) { Log.i(TAG, "ô $index: chạy nền $pkg — ô chưa sẵn, 0 lệnh"); return }
        busy += index
        toBack(index, stage.vd, pkg) { r ->
            busy -= index
            Log.i(TAG, "ô $index: chạy nền $pkg ⇒ ${if (r.left) "đã rời ô" else "ô giữ app"} · ${r.line}")
            if (r.left) revert(index, Event.APP_BACKGROUND, pkg) else sayIfStill(index, R.string.kachi_sc_bg_failed_why, pkg, r.why)
            workspace().heads.refreshAll()   // P3: chuỗi có thể vừa TẮT BEHIND-HOME (`ANCHOR_IN_FRONT`) ⇒ hỏi lại nút mọi ô
        }
    }

    /** MỘT cửa của luật hoàn ô: quyết ([HomeViewModel.slotRevert]) → host thôi giữ app → áp lớp tạm. */
    private fun revert(index: Int, event: Event, pkg: String?) {
        val next = viewModel.slotRevert(index, event, pkg)
        Log.i(TAG, "ô $index: $event ${pkg ?: "-"} -> $next")
        if (next == Next.Keep) return
        if (pkg != null) workspace().hostAt(index)?.relinquish(pkg)
        viewModel.applySlotRevert(index, next)
    }

    /** [why] khác `null` ⇒ chuỗi [res] có `%2$s` lý do (chẩn đoán, lỗi xe 2.87) ⇒ hiện LÂU để kịp chụp màn hình. */
    private fun say(res: Int, pkg: String, why: String? = null) {
        val label = InstalledApps.labelOf(activity, pkg) ?: pkg
        if (why == null) say(activity.getString(res, label)) else say(activity.getString(res, label, why), Toast.LENGTH_LONG)
    }

    /** Câu "chưa làm được" chỉ khi ô VẪN hiện [pkg] — ô đã đổi bằng đường khác (app chết, kéo-thả) thì câu đó sai (P3). */
    private fun sayIfStill(index: Int, res: Int, pkg: String, why: String? = null) {
        if ((shownAt(index) as? SlotContent.App)?.pkg == pkg) say(res, pkg, why) else Log.i(TAG, "ô $index: $pkg đã rời ô — bỏ câu báo")
    }

    private fun say(text: String, length: Int = Toast.LENGTH_SHORT) = Toast.makeText(activity.applicationContext, text, length).show()

    private companion object {
        /** Một thẻ log cho cả vòng đời ô (đọc trên màn Chẩn đoán / logcat). */
        const val TAG = "KachiSlotLife"

        /** Lý do ngắn trên câu báo khi ô 7 không đỗ được — mã nhật ký ASCII (không dịch, cùng lẽ `BehindReason`). */
        const val PARK_NOT_READY = "park: not-ready"
    }
}
