package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.voice.VoiceAppIntents
import com.byd.clusternav.launcher.voice.VoiceAppTarget
import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.launcher.voice.VoiceIntent
import com.byd.clusternav.launcher.voice.VoiceMediaOp
import com.byd.clusternav.launcher.voice.VoicePlaces
import com.byd.clusternav.launcher.voice.VoiceReply
import com.byd.clusternav.navigation.NavApps

/**
 * ═══ V1.1 · TỪ VỰNG MỞ → **APP ĐÍCH** (dẫn đường · nhạc) ═════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` R17. Tách khỏi [VoiceDispatcher] ở **voice pha 2 (2026-09-16)** vì trần
 * 500 dòng (CLAUDE.md §4.1) — tệp kia đã 499 dòng trước lượt này và R5 (đọc lại giá trị thật) + L7 (bố cục) đẩy
 * nó lên 571.
 *
 * ## Vì sao cắt ĐÚNG ở đây, không cắt chỗ khác
 * Khối này là **một vai trọn vẹn**: *"giao một chuỗi chữ / một cặp toạ độ cho một app ngoài, rồi nói đúng thứ đã
 * xảy ra"*. Nó KHÔNG chạm xe (`CarControlPort`), KHÔNG chạm launcher (ô, bố cục, hồ sơ), KHÔNG đọc
 * `ControlTileState` — nó chỉ cầm bảng đích ở `:core` ([VoiceAppTargets]) và bốn lambda ra ngoài. Mọi chỗ cắt
 * khác đều phải mang theo một nửa của một vai.
 *
 * ## Ranh giới KHÔNG đổi một chữ
 * Đây là lượt **tách tệp**, không phải lượt sửa luật: mọi thân hàm dưới đây giữ nguyên văn từ [VoiceDispatcher],
 * kể cả KDoc. Kachi vẫn **không tìm đường, không hiểu địa điểm, không tìm bài hát** (phương án C, RE Kiki §8.2)
 * — nó chuyển nguyên văn rồi đứng ra ngoài.
 *
 * @param state ảnh chụp state màn chính — chỉ đọc `savedPlaces` (sổ địa chỉ của hồ sơ đang dùng).
 * @param say · confirm · openApp · sendToApp · geocode · mediaPackage · media · onUi · background — CÙNG những
 *   lambda mà [VoiceDispatcher] nhận; xem KDoc ở đó về hợp đồng của từng cái.
 */
@Suppress("LongParameterList")
class VoiceTargetDispatch(
    private val state: () -> HomeUiState,
    private val media: () -> MediaTransport,
    private val openApp: (String) -> Boolean,
    private val confirm: (String, () -> Unit, () -> Unit) -> Unit,
    private val say: (String) -> Unit,
    private val sendToApp: (VoiceAppIntents.Handoff) -> Boolean,
    private val geocode: (String) -> VoiceAppIntents.Coords?,
    private val mediaPackage: () -> String?,
    private val onUi: (() -> Unit) -> Unit,
    private val background: (() -> Unit) -> Unit,
) {
    // ══ V1.1 · TỪ VỰNG MỞ → APP ĐÍCH ════════════════════════════════════════════════════════════

    /**
     * Dẫn đường.
     *
     * ## Cái gì đã đổi ở 1.50, và vì sao nó KHÔNG phá ranh giới cũ
     * Tới 1.49 nhánh này chỉ **mở app** rồi nói thẳng là chưa chuyển được điểm đến — đúng với bằng chứng có lúc
     * đó. Owner 2026-09-14 hỏi lại, và [ĐO] trên máy ảo + nguồn Kiki cho thấy ba app đều **có cửa** (bảng
     * [VoiceAppTargets]). Ranh giới phương án C không đổi một chữ: Kachi vẫn **không tìm đường, không hiểu địa
     * điểm** — nó chuyển nguyên văn chuỗi chữ (hoặc cặp toạ độ) cho app dẫn đường rồi đứng ra ngoài.
     *
     * Ba đường ra, mỗi đường nói một câu khác nhau vì chúng **là** ba chuyện khác nhau:
     *  • app đích nhận CHỮ (Google Maps · Waze) ⇒ bắn thẳng;
     *  • app đích chỉ nhận TOẠ ĐỘ (VietMap) ⇒ giải toạ độ ở luồng nền, **đọc lại tên nơi giải ra** rồi mới bắn;
     *  • không giải được / không ai nhận ⇒ mở app trơn và **nói rõ là chưa giao được** (không có dấu ✓ rỗng).
     */
    fun runNav(i: VoiceIntent.Nav, labels: Map<String, String>) {
        val installed = labels.values.toSet()
        val asked = i.app
        val target = pickNav(asked, installed)
        if (target == null) { say(if (asked != null) VoiceReply.appNotInstalled(i, asked) else VoiceReply.noNavApp(i)); return }
        val pkg = target.packageIn(installed) ?: run { say(VoiceReply.appNotInstalled(i, target.key)); return }

        if (!target.needsCoords) { deliver(i, target, pkg, i.query, null); return }
        // Cần toạ độ ⇒ lượt mạng/dịch vụ: **luồng nền**, và người lái phải biết là máy đang làm gì.
        say(VoiceReply.resolving(i))
        background {
            val coords = runCatching { geocode(i.query) }.getOrNull()
            onUi {
                // [SOÁT Pass 3 · P2] Tra cứu hỏng ≠ *"app không có cửa"*. Nói đúng cái vừa xảy ra, xem
                // [VoiceReply.navNoPlace] — dùng chung một câu là đổ lỗi cho app về một lần mất sóng.
                if (coords == null) {
                    say(if (openApp(pkg)) VoiceReply.navNoPlace(i, target) else VoiceReply.cannotOpen(i))
                    return@onUi
                }
                // Tên do bên giải trả về KHÁC câu người ta nói (*"chợ bến thành"* → *"Chợ Bến Thành"*, hoặc một
                // nơi trùng tên). Đọc lại rồi mới bắn — cùng lý do với cổng CONFIRM của từ vựng mở.
                confirm(
                    VoiceReply.confirmPlace(i, target, coords.place),
                    { deliver(i, target, pkg, coords.place, coords) },
                    { say(VoiceReply.cancelled(i, 0)) },
                )
            }
        }
    }

    /**
     * Dẫn đường tới một nơi **ĐÃ LƯU** (spec `docs/specs/kachi-voice-addresses.html` R3 · R4).
     *
     * ## Ba khác biệt so với [runNav], mỗi cái có lý do riêng
     *  1. **Không geocode, không hộp đọc-lại.** Đường kia phải tra mạng rồi hỏi lại vì điểm đến do nhận dạng tự
     *     do đọc ra. Ở đây dữ liệu là thứ chính người dùng đã gõ và đã có sẵn trên đĩa — thêm một lượt chờ mạng
     *     và một cú chạm cho câu người ta nói mỗi ngày là làm hỏng đúng thứ tính năng này sinh ra để chữa.
     *  2. **Chọn app theo DỮ LIỆU của mục**, không theo thứ tự ưu tiên trần ([VoiceAppTargets.navFor]): mục chỉ
     *     có chữ mà đẩy vào VietMap (chỉ nhận toạ độ) là mở app rồi bảo người ta tự gõ, trong khi Google Maps
     *     ngay dưới nhận được nguyên văn địa chỉ ấy.
     *  3. **Tra sổ lúc THI HÀNH**, không lúc phân tích: ý định chỉ mang nhãn (xem KDoc
     *     [VoiceIntent.NavigateSaved]) nên nếu người dùng vừa sửa địa chỉ xong, lượt này đi theo bản mới.
     */
    fun runNavSaved(i: VoiceIntent.NavigateSaved, labels: Map<String, String>) {
        val place = SavedPlaces.find(state().savedPlaces, i.placeName)
        if (place == null) { say(VoiceReply.placeNotSaved(i, VoicePlaces.displayLabel(i.placeName))); return }
        val installed = labels.values.toSet()
        val asked = i.app
        val target = if (asked != null) {
            VoiceAppTargets.byKey(asked)?.takeIf { it.packageIn(installed) != null }
        } else {
            VoiceAppTargets.navFor(place.hasCoords, NAV_PREFERENCE, installed)
        }
        if (target == null) {
            say(if (asked != null) VoiceReply.appNotInstalled(i, asked) else VoiceReply.noNavApp(i))
            return
        }
        val pkg = target.packageIn(installed) ?: run { say(VoiceReply.appNotInstalled(i, target.key)); return }
        // Mục KHÔNG toạ độ + app chỉ nhận toạ độ ⇒ mở app trơn, và nói ra **việc người dùng làm được** (thêm
        // lat/lng) thay vì câu chung chung "app này không nhận điểm đến" — xem [VoiceReply.placeNeedsCoords].
        if (!place.hasCoords && target.needsCoords) {
            say(if (openApp(pkg)) VoiceReply.placeNeedsCoords(i, target) else VoiceReply.cannotOpen(i))
            return
        }
        val coords = if (place.hasCoords) {
            VoiceAppIntents.Coords(place.lat!!, place.lng!!, place.query)
        } else {
            null
        }
        deliver(i, target, pkg, place.query, coords)
    }

    /**
     * Nhạc — cùng hình dạng với [runNav], trừ việc không có app nhạc nào cần toạ độ.
     *
     * ## [SOÁT P1] *"phát nhạc"* khi CHƯA CÓ PHIÊN NÀO ⇒ phải MỞ app, không phải báo lỗi
     * [ĐO] `docs/diagnostics/emulator-voice-e2e-2026-09-15.md` §3 L2 (t46/t50): bản trước mở đầu bằng
     * `if (i.op != QUERY) { runTransport(i); return }` ⇒ mọi lệnh không phải QUERY rơi thẳng vào transport và
     * trường [VoiceIntent.Media.app] **bị vứt** — *"mở nhạc trên YouTube Music"* trả lời *"chưa có phiên nhạc"*
     * mà không app nào lên màn. Luật mới: **PLAY** + (nêu đích danh app **hoặc** chưa có phiên) ⇒ mở app đó.
     * PAUSE/NEXT/PREV giữ transport — ở đó *"chưa có phiên nhạc"* là câu ĐÚNG, và mở một app nhạc lên để "dừng"
     * nó là làm việc khác hẳn việc được bảo.
     */
    fun runMedia(i: VoiceIntent.Media, labels: Map<String, String>) {
        if (i.op == VoiceMediaOp.QUERY) { runMediaQuery(i, labels); return }
        val playing = runCatching { mediaPackage() }.getOrNull()
        if (i.op == VoiceMediaOp.PLAY && (i.app != null || playing == null)) { runPlayInApp(i, labels, playing); return }
        runTransport(i)
    }

    /** *"phát bài &lt;tên&gt;"* — giao chuỗi chữ cho app nhạc (từ vựng mở, R17). */
    private fun runMediaQuery(i: VoiceIntent.Media, labels: Map<String, String>) {
        val (target, pkg) = musicTarget(i, labels) ?: return
        deliver(i, target, pkg, i.query, null)
    }

    /**
     * *"phát nhạc [trên &lt;app&gt;]"* — mở app nhạc rồi nói đúng thứ đã xảy ra. App đích **đang phát** ⇒ transport
     * (bắn `play` vào chính phiên đó), không mở đè: mở lại app đang phát là một lượt chuyển màn thừa lúc đang lái.
     */
    private fun runPlayInApp(i: VoiceIntent.Media, labels: Map<String, String>, playing: String?) {
        val (target, pkg) = musicTarget(i, labels) ?: return
        if (playing == pkg) { runTransport(i); return }
        say(if (openApp(pkg)) VoiceReply.musicAppOpened(i, target) else VoiceReply.cannotOpen(i))
    }

    /** App nhạc đích + gói của nó. `null` ⇒ **đã nói ra** lý do (chưa cài / không có app nhạc nào). */
    private fun musicTarget(i: VoiceIntent.Media, labels: Map<String, String>): Pair<VoiceAppTarget, String>? {
        val installed = labels.values.toSet(); val asked = i.app
        val target = pickMusic(asked, installed)
        if (target == null) { say(if (asked != null) VoiceReply.appNotInstalled(i, asked) else VoiceReply.noMusicApp(i)); return null }
        val pkg = target.packageIn(installed) ?: run { say(VoiceReply.appNotInstalled(i, target.key)); return null }
        return target to pkg
    }

    /** Bắn một lượt giao việc và nói đúng thứ đã xảy ra. */
    private fun deliver(
        i: VoiceIntent,
        target: VoiceAppTarget,
        pkg: String,
        query: String,
        coords: VoiceAppIntents.Coords?,
    ) {
        val launch = target.destinationLaunch(coords != null)
        val ok = launch != null && sendToApp(
            VoiceAppIntents.Handoff(pkg, launch, query, coords, target.fallback),
        )
        if (ok) { say(VoiceReply.handedOver(i, target)); return }
        openPlain(i, target, pkg)
    }

    /** Không giao được chữ ⇒ vẫn **mở app** (đó là phần chắc chắn làm được) rồi nói ra phần chưa làm được. */
    private fun openPlain(i: VoiceIntent, target: VoiceAppTarget, pkg: String) {
        say(if (openApp(pkg)) VoiceReply.navOpenedNoHandover(i, target) else VoiceReply.cannotOpen(i))
    }

    /**
     * Chọn app dẫn đường.
     *
     * Câu nêu đích danh ⇒ đúng app đó (không có nó thì nói *"chưa cài"*, **không** lặng lẽ đổi sang app khác —
     * người ta nói *"bằng Waze"* là có lý do). Không nêu ⇒ [NAV_PREFERENCE], tức thứ tự đã có từ 1.49.
     */
    private fun pickNav(key: String?, installed: Set<String>): VoiceAppTarget? {
        VoiceAppTargets.byKey(key)?.let { return it.takeIf { t -> t.packageIn(installed) != null } }
        return NAV_PREFERENCE.firstNotNullOfOrNull { pkg ->
            VoiceAppTargets.NAV.firstOrNull { pkg in it.packages && it.packageIn(installed) != null }
        }
    }

    /**
     * Chọn app nhạc: **phiên đang phát trước**, rồi mới tới thứ tự của bảng.
     *
     * Owner 2026-09-14 nói *"mở nhạc bằng yt music, youtube"* — tức app là một lựa chọn, không phải một hằng số.
     * Khi câu không nêu app thì đích đúng nhất là **app người ta đang nghe**: mở YouTube Music đè lên Spotify
     * đang phát là hai luồng nhạc cùng lúc, và đó là thứ người lái phải dừng xe mới dẹp được.
     */
    private fun pickMusic(key: String?, installed: Set<String>): VoiceAppTarget? {
        VoiceAppTargets.byKey(key)?.let { return it.takeIf { t -> t.packageIn(installed) != null } }
        val playing = runCatching { mediaPackage() }.getOrNull()
        VoiceAppTargets.MUSIC.firstOrNull { playing != null && playing in it.packages }?.let { return it }
        return VoiceAppTargets.MUSIC.firstOrNull { it.packageIn(installed) != null }
    }

    /**
     * Nhạc — phần TRANSPORT (phát / dừng / bài tiếp / bài trước).
     *
     * ## [SOÁT P1] Vì sao phải đọc kết quả của transport, không bắn rồi báo "✓"
     * [MediaBridge] chỉ điều khiển được **phiên đang hoạt động** mà nó đã thấy; chưa ai thấy phiên nào thì mọi
     * lệnh transport là no-op **im lặng** (KDoc `MediaBridge`: degrade-safe). Bản đầu bắn xong báo `✓ Phát nhạc`
     * bất kể có phiên hay không — tức nói dối đúng cái ca hay gặp nhất.
     */
    private fun runTransport(i: VoiceIntent.Media) {
        val bridge = media()
        val ok = when (i.op) {
            VoiceMediaOp.PLAY -> bridge.play()
            VoiceMediaOp.PAUSE -> bridge.pause()
            VoiceMediaOp.NEXT -> bridge.next()
            VoiceMediaOp.PREV -> bridge.prev()
            VoiceMediaOp.QUERY -> false
        }
        say(if (ok) VoiceReply.done(i) else VoiceReply.noMediaSession(i))
    }

    private companion object {
        /**
         * Thứ tự ƯU TIÊN app dẫn đường — **danh sách roster dùng chung** ở `:core`, không phải tên gói viết cứng
         * mới. VietMap đứng đầu vì đó là app đang nuôi badge tốc độ trên xe owner; Google Maps rồi Waze theo sau.
         */
        val NAV_PREFERENCE: List<String> =
            listOf(NavApps.VIETMAP_LIVE) + NavApps.GMAPS.toList() + NavApps.WAZE.toList()
    }
}
