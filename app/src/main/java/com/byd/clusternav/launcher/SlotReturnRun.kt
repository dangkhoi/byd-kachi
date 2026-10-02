package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.behind.BehindHomeRunner
import com.byd.clusternav.launcher.behind.BehindMarksStore
import com.byd.clusternav.launcher.behind.SlotReturn
import com.byd.clusternav.launcher.behind.SlotReturnSequence
import com.byd.clusternav.modules.navaccess.AccessibilityRebind
import java.io.IOException
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ F1 dòng 9 + R1.8 — bên THI HÀNH của Ô ⇄ TOÀN MÀN (K7 / K8) cho [VdAppHost] ═══════════════════════════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` §4.2.6 · §4.4.3 dòng 9 · §5 T-M2/T-M6. Chuỗi + mọi quyết định ở
 * `:core` ([SlotReturnSequence] · [SlotReturn], KDoc ở đó có số đo); tệp này chỉ cấp luồng, dấu bền và một thẻ chữ:
 *  - K7 / K8 của lối tắt chạy trên luồng `kachi-behind` ([BehindHomeRunner.execute]) — CÙNG mutex với chuỗi BEHIND-HOME
 *    (R-nf4: hai chuỗi đổi cửa sổ không bao giờ chồng lệnh); kết quả trả về luồng chính qua [View.post] của host;
 *  - R1.8 ([bringBackMarked]) chạy CHẶN ngay trong luồng mở app của ô (`VdAppHost.launchInto`) — chỉ khi gói có dấu bền
 *    (một lần đọc prefs, 0 lệnh shell khi không có ⇒ chuỗi golden giữ nguyên byte, CLAUDE.md §6).
 *
 * Kênh ném giữa chuỗi (dadb đứt, cổng thi hành từ chối) ⇒ bắt tại đây: lọt ra luồng nền là sập HOME (cùng lý do
 * `BehindHomeRunner.failed`).
 */
internal object SlotReturnRun {

    private const val TAG = "KachiDetach"

    private fun seq(sh: (String) -> String) = SlotReturnSequence(sh, AccessibilityRebind.GO_HOME_UNLESS_CAMERA)

    /**
     * K7 qua rào cho app [pkg] của ô [vd]; [done] (luồng chính) nhận kết quả: `taskId` = đã ra toàn màn; `null` + `back` =
     * app rời ô mà không còn ở trước, chuỗi đã xử lý như lượt về ô; cả hai `null` = không tách được.
     */
    fun detach(
        host: View, vd: Int, pkg: String, sh: (String) -> String, sig: String?, homeComps: List<String>,
        done: (SlotReturnSequence.Detached) -> Unit,
    ) =
        BehindHomeRunner.execute("detach $pkg") {
            val out = try {
                seq(sh).detach(vd, pkg, sig, homeComps)
            } catch (e: IOException) {
                SlotReturnSequence.Detached(null, "detach $pkg -> ${e.javaClass.simpleName}")
            } catch (e: RuntimeException) {
                SlotReturnSequence.Detached(null, "detach $pkg -> ${e.javaClass.simpleName}")
            }
            Log.i(TAG, out.line)
            host.post { done(out) }
        }

    /**
     * K8 đưa task [taskId] (đang toàn màn) về ô [vd]; [done] (luồng chính) nhận [SlotReturn.Back]. K8 chỉ khi màn nhà Kachi
     * ([DefaultHome.shownComponents]) đang là đỉnh display 0 — camera / app khác ở trên ⇒ `KEEP` (review lượt 6, `:core`).
     */
    fun bringBack(host: View, vd: Int, sh: (String) -> String, taskId: Int, done: (SlotReturn.Back) -> Unit) {
        val homes = DefaultHome.shownComponents(host.context)
        BehindHomeRunner.execute("return $taskId") {
            val out = try {
                seq(sh).bringBack(vd, taskId, homes)
            } catch (e: IOException) {
                SlotReturnSequence.Returned(SlotReturn.Back.KEEP, taskId, "return $taskId -> ${e.javaClass.simpleName}")
            } catch (e: RuntimeException) {
                SlotReturnSequence.Returned(SlotReturn.Back.KEEP, taskId, "return $taskId -> ${e.javaClass.simpleName}")
            }
            Log.i(TAG, out.line)
            host.post { done(out.result) }
        }
    }

    /**
     * R1.8 (T-M6 [ĐO]) — CHẶN, gọi trên luồng mở app của ô. Gói không mang dấu bền ⇒ `false` ngay, 0 lệnh (đường golden y như
     * cũ). Có ⇒ K8 + đọc lại; về ô ⇒ gỡ dấu (task không còn sau màn nhà) + `true`; khác ⇒ `false` (bên gọi đi đường golden).
     * Màn nhà Kachi không ở đỉnh display 0 (camera lùi đang lên) ⇒ 0 lệnh K8, `false` (review lượt 6, `:core`).
     */
    fun bringBackMarked(ctx: Context, vd: Int, pkg: String, sh: (String) -> String): Boolean {
        val store = BehindMarksStore(ctx)
        val marks = store.read()
        if (pkg !in marks.values) return false
        val out = try {
            seq(sh).bringBackMarked(vd, pkg, marks, DefaultHome.shownComponents(ctx))
        } catch (e: IOException) {
            SlotReturnSequence.Returned(SlotReturn.Back.UNREAD, null, "r1.8 $pkg -> ${e.javaClass.simpleName}")
        } catch (e: RuntimeException) {
            SlotReturnSequence.Returned(SlotReturn.Back.UNREAD, null, "r1.8 $pkg -> ${e.javaClass.simpleName}")
        }
        Log.i(TAG, out.line)
        if (out.result != SlotReturn.Back.IN_SLOT) return false
        out.taskId?.let { store.remove(it) }
        return true
    }

    /**
     * Thẻ "Đang mở toàn màn — chạm để đưa về ô" phủ ô [host] (chữ ở ĐÁY ô như thẻ "app đã đóng" — thẻ icon + tên của
     * `WorkspaceView.appCard` nằm giữa, lộ ra khi mặt vẽ bị giấu). Trả view để host gỡ khi app về ô.
     */
    fun fullCard(host: FrameLayout, onTap: () -> Unit): View {
        val ctx = host.context
        val d = ctx.resources.displayMetrics.density
        val card = TextView(ctx).apply {
            text = ctx.getString(R.string.kachi_sc_full_card)
            setTextColor(Color.parseColor(KachiTheme.MUT)); KachiType.apply(this, KachiType.BODY)
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setPadding((Sp.L * d).toInt(), (Sp.SLOT_HEAD_CLEAR * d).toInt(), (Sp.L * d).toInt(), (Sp.L * d).toInt())
            setOnClickListener { onTap() }
        }
        host.addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        return card
    }
}

/**
 * ═══ F1 dòng 9 — trạng thái "app của ô đang mở TOÀN MÀN" của MỘT [VdAppHost] (tách khỏi `VdAppHost.kt`, trần 500 dòng) ═══
 *
 * [task] = task K7 đã đọc lại thấy ở trước display 0 — cờ RAM chỉ để HIỂN THỊ thẻ và chọn K8 lúc về (CLAUDE.md §5); mọi
 * bước vẫn quyết theo bản đọc `am stack list` ở `:core` ([SlotReturnSequence]). Tiến trình chết ⇒ cờ mất ⇒ ô dựng lại
 * bằng đường golden như hôm nay (không có trạng thái bền nào mới).
 *
 * @param current host chưa nhả và vẫn đang giữ app [String] (kết quả về muộn của một app cũ phải im).
 * @param onClosed thẻ "app đã đóng — chạm để mở lại" của host · @param reopen đường mở ô golden (force-stop + mở lại).
 * @param onTap chạm thẻ "Đang mở toàn màn" ⇒ host gọi lại [bringBack] với màn ảo/kênh hiện tại.
 */
internal class SlotFullscreen(
    private val host: FrameLayout,
    private val surface: View,
    private val probeKey: String,
    private val current: (String) -> Boolean,
    private val onClosed: () -> Unit,
    private val reopen: () -> Unit,
    private val onTap: () -> Unit,
) {
    @Volatile private var task: Int? = null
    private var card: View? = null

    /** App của ô đang mở toàn màn (theo lần K7 đã đọc lại) — `VdAppHost.release` không giết app người dùng đang thấy. */
    val isDetached: Boolean get() = task != null

    /** K7: thôi đo ô TRƯỚC lệnh (app rời ô theo ý người dùng ≠ "app đã đóng"); tách được ⇒ giấu mặt vẽ + thẻ. */
    fun detach(vd: Int, pkg: String, sh: (String) -> String, sig: String?, homeComps: List<String>, done: (Boolean) -> Unit) {
        SlotLiveProbe.unwatch(probeKey)
        SlotReturnRun.detach(host, vd, pkg, sh, sig, homeComps) { out ->
            if (!current(pkg)) return@detach
            val t = out.taskId
            if (t == null) {
                // Chạm đúp: lượt trước đã tách (app không còn ở ô) ⇒ lượt này không có gì để làm — KHÔNG đo lại ô (đo lại
                // là 5 s sau thẻ "đã đóng" mọc lên trên app đang toàn màn).
                if (task != null) { done(true); return@detach }
                // Review lượt 4 [P2]: K7 đã đưa app rời ô mà nó ẩn ngay dưới màn nhà (HOME/Back trước lần đọc) ⇒ chuỗi đã xử lý
                // như lượt về ô — chỉ đo lại ô khi app THẬT ở ô, không thì ô đen mãi (bộ đo chưa từng thấy app sống ở đó).
                // (Review lượt 5: ẩn dưới camera / app khác ⇒ `:core` trả `taskId` ⇒ nhánh thẻ "Đang mở toàn màn" bên dưới.)
                when (out.back) {
                    null, SlotReturn.Back.IN_SLOT -> SlotLiveProbe.watch(probeKey, pkg, vd, sh) { onClosed() }
                    SlotReturn.Back.GONE -> onClosed()
                    else -> reopen()          // K8 không đưa được về ô ⇒ đường golden (force-stop + mở lại)
                }
            } else {
                task = t
                surface.visibility = View.GONE
                if (card == null) card = SlotReturnRun.fullCard(host, onTap)
            }
            done(t != null || out.back != null)
        }
    }

    /**
     * Ô đổi sang app khác TẠI CHỖ (`VdAppHost.swapApp` — lối tắt / giọng nói đặt tạm) ⇒ trạng thái "toàn màn" của app cũ
     * không còn là của ô này (review lượt 4 [P2]): bỏ cờ + thẻ, kẻo thẻ "Đang mở toàn màn" phủ lên app mới, chạm thẻ / màn nhà
     * hiện lại thì K8 kéo app CŨ đè lên app mới trong ô, và `VdAppHost.release` tha app mới khỏi `am force-stop`. App cũ (nếu
     * còn ở display 0) thành app người dùng tự mở — như mở từ ngăn kéo. Luồng chính.
     */
    fun reset() {
        task = null
        card?.let { host.removeView(it) }; card = null
    }

    /** K8 về ô; [SlotReturn.Back.KEEP] ⇒ giữ thẻ; về được ⇒ đo lại; app đã đóng ⇒ thẻ "đã đóng"; K8 không ăn ⇒ golden. */
    fun bringBack(vd: Int, pkg: String, sh: (String) -> String) {
        val t = task ?: return
        SlotReturnRun.bringBack(host, vd, sh, t) { r ->
            if (!current(pkg) || task != t || r == SlotReturn.Back.KEEP) return@bringBack
            task = null
            card?.let { host.removeView(it) }; card = null
            surface.visibility = View.VISIBLE
            when (r) {
                SlotReturn.Back.IN_SLOT -> SlotLiveProbe.watch(probeKey, pkg, vd, sh) { onClosed() }
                SlotReturn.Back.GONE -> onClosed()
                else -> reopen()          // R1.8: K8 không đưa được về ô ⇒ đường golden (force-stop + mở lại)
            }
        }
    }
}
