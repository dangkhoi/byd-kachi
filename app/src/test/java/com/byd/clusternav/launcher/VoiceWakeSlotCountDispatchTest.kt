package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.voice.SlotPlaceOutcome
import com.byd.clusternav.launcher.voice.VoiceGrammarSnapshot
import com.byd.clusternav.launcher.voice.VoiceHomeAction
import com.byd.clusternav.launcher.voice.VoiceHomeActions
import com.byd.clusternav.launcher.voice.VoiceHomeRelay
import com.byd.clusternav.launcher.voice.VoiceIntent
import com.byd.clusternav.launcher.voice.VoiceReply
import com.byd.clusternav.launcher.voice.VoiceSlotPlace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ VOICE-WAKE-SLOTCOUNT (2026-10-02) — *"mở YouTube vào ô 6"* qua `:wake` trên bố cục tự vẽ 6 khung ═══════════
 *
 * [ĐO ảnh owner 02/10, bản 2.85] bố cục tự vẽ 6 khung (bảng vẽ: *"6 khung · phủ kín màn"*), chip Ô1…Ô6 đúng, nhưng
 * câu giọng nói trả *"✗ Mở ứng dụng YouTube vào ô 6 — bố cục hiện chỉ có 3 ô"*. Gốc [ĐO nguồn]: phiên `:wake`
 * (`VoiceWakeSessionFactory.buildSession`) dựng dispatcher với `state = { grammar().homeState() }` — ảnh chụp chỉ mang
 * hồ sơ + sổ địa chỉ ⇒ bố cục = mặc định `THREE` ⇒ `runOpenApp` chặn TẠI CHỖ trước khi relay sang Activity.
 *
 * Giàn dưới đây dựng ĐÚNG hai hình dạng dispatcher của sản phẩm, chỉ thay tầng Android bằng bản thuần:
 *  • **wake** — `state` = `VoiceGrammarSnapshot.homeState()` (giả, 3 ô) + `placeInSlot` đi giao thức relay thật
 *    ([VoiceHomeRelay.encodeSlot] → [VoiceHomeActions.perform] của Activity với state THẬT → [VoiceHomeRelay.slotOutcome]);
 *  • **in-process** — `state` thật, `placeInSlot = null` (nút mic khi wake TẮT · ô Gõ lệnh chữ · cầu kiểm thử).
 * Phần intent/broadcast/hạn chờ (Android) khoá bằng bài canh nguồn `VoiceWakeFakeStateContractTest`.
 */
class VoiceWakeSlotCountDispatchTest {

    private companion object {
        const val YT = "com.google.android.youtube"
    }

    private val six = GridLayout((0 until 6).map { GridFrame(it * 2, 0, 2, 3) })
    private val customSix = HomeUiState(customLayout = six)
    private val presetThree = HomeUiState(workspace = WorkspaceState(LayoutPreset.THREE))

    private class Rig(real: HomeUiState, wake: Boolean) {
        val said = ArrayList<String>()
        val placed = ArrayList<Pair<Int, String>>()

        /** Phía Activity: CHÍNH lớp sản phẩm, state thật của màn chính. */
        val activity = VoiceHomeActions(
            openAppList = {}, openSettings = {}, openPermissions = {}, switchProfile = {},
            assignAppToSlot = { idx, pkg -> placed += idx to pkg; true },
            onLayout = { true },
            slotCount = { VoiceSlotPlace.slotCountOf(real) },
        )

        /** Giao thức relay thuần: mã hoá ở `:wake` → Activity thi hành + ack → `:wake` giải mã. */
        private fun relay(idx: Int, pkg: String): SlotPlaceOutcome =
            VoiceHomeRelay.slotOutcome(activity.perform(VoiceHomeAction.ASSIGN_APP_TO_SLOT, VoiceHomeRelay.encodeSlot(idx, pkg)))

        val dispatcher = VoiceDispatcher(
            control = { error("bài này không chạm nút xe") },
            // wake: ĐÚNG state mà `buildSession` đưa vào — ảnh chụp ngữ pháp, bố cục mặc định.
            state = if (wake) ({ VoiceGrammarSnapshot(profiles = listOf("Mặc định")).homeState() }) else ({ real }),
            media = { error("bài này không chạm nhạc") },
            appsByLabel = { mapOf("YouTube" to YT) },
            openApp = { error("câu có ô không được mở toàn màn") },
            openAppList = {},
            openSettings = {},
            onSwitchProfile = {},
            onListen = {},
            confirm = { _, y, _ -> y() },
            say = { said += it },
            // in-process: đường cũ của Activity (đặt tạm) — cùng bộ ghi với phía Activity.
            assignAppToSlot = { idx, pkg -> placed += idx to pkg; true },
            sendToApp = { error("không giao chữ") },
            geocode = { null },
            mediaPackage = { null },
            background = { it() },
            onUi = { it() },
            placeInSlot = if (wake) ({ idx, pkg -> relay(idx, pkg) }) else null,
        )

        fun say(text: String): VoiceIntent.OpenApp {
            val i = dispatcher.preview(text).single() as VoiceIntent.OpenApp
            dispatcher.execute(listOf(i))
            return i
        }
    }

    // ── wake: số ô là của MÀN THẬT ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `wake - bo cuc tu ve 6 khung - vao o 6 thi DAT VAO O, khong noi chi co 3 o`() {
        val r = Rig(customSix, wake = true)
        val i = r.say("mở youtube vào ô số sáu")
        assertEquals(6, i.slot)
        assertEquals(listOf(5 to YT), r.placed, "phải tới được Activity và đặt vào ô 6 (0-based 5)")
        assertEquals(listOf(VoiceReply.done(i)), r.said, "lời đáp phải là ✓ đã làm")
        assertTrue(r.said.none { it.contains(VoiceReply.slotOutOfRange(i, 3)) }, "đúng câu lỗi owner chụp 02/10: ${r.said}")
    }

    @Test
    fun `wake - bo cuc tu ve 6 khung - vao o 7 thi noi CHI CO 6 O (so that)`() {
        val r = Rig(customSix, wake = true)
        val i = r.say("mở youtube vào ô số bảy")
        assertEquals(listOf(VoiceReply.slotOutOfRange(i, 6)), r.said, "số ô trong câu phải là số THẬT của màn (6)")
        assertTrue(r.placed.isEmpty(), "ô ngoài dải ⇒ Activity không được đặt gì")
    }

    @Test
    fun `wake - bo cuc san 3 o - vao o 4 thi noi chi co 3 o`() {
        val r = Rig(presetThree, wake = true)
        val i = r.say("mở youtube vào ô số bốn")
        assertEquals(listOf(VoiceReply.slotOutOfRange(i, 3)), r.said)
        assertTrue(r.placed.isEmpty())
    }

    // ── in-process: hành vi 2.85 KHÔNG đổi ───────────────────────────────────────────────────────────────────

    @Test
    fun `in-process - cung ba ca, cung ba loi dap - duong Activity khong doi`() {
        listOf(
            Triple(customSix, "mở youtube vào ô số sáu", 6),
            Triple(customSix, "mở youtube vào ô số bảy", 6),
            Triple(presetThree, "mở youtube vào ô số bốn", 3),
        ).forEach { (real, text, n) ->
            val w = Rig(real, wake = true).also { it.say(text) }
            val p = Rig(real, wake = false).also { it.say(text) }
            assertEquals(p.said, w.said, "wake và in-process phải nói CÙNG một câu cho «$text» ở bố cục $n ô")
            assertEquals(p.placed, w.placed, "wake và in-process phải đặt CÙNG một ô cho «$text»")
        }
    }

    // ── tiền đề lỗi: dispatcher dùng state giả cho số ô là SAI — vì sao `:wake` bắt buộc truyền placeInSlot ──

    @Test
    fun `hinh dang 2-85 - wake ma khong giao Activity thi noi chi co 3 o tren bo cuc 6 khung`() {
        val placed = ArrayList<Pair<Int, String>>()
        val said = ArrayList<String>()
        val d = VoiceDispatcher(
            control = { error("") }, state = { VoiceGrammarSnapshot().homeState() }, media = { error("") },
            appsByLabel = { mapOf("YouTube" to YT) }, openApp = { error("") }, openAppList = {}, openSettings = {},
            onSwitchProfile = {}, onListen = {}, confirm = { _, y, _ -> y() }, say = { said += it },
            assignAppToSlot = { idx, pkg -> placed += idx to pkg; true },
            sendToApp = { error("") }, geocode = { null }, mediaPackage = { null }, background = { it() }, onUi = { it() },
        )
        val i = d.preview("mở youtube vào ô số sáu").single() as VoiceIntent.OpenApp
        d.execute(listOf(i))
        assertEquals(listOf(VoiceReply.slotOutOfRange(i, 3)), said, "tái hiện đúng câu lỗi 02/10 khi thiếu placeInSlot")
        assertTrue(placed.isEmpty())
    }
}
