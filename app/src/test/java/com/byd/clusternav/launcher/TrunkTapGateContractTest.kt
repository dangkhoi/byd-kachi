package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 · owner 13/10 *"Chặn MỞ cốp khi xe chạy (Khuyên dùng)"* — khoá ĐƯỜNG CHẠM: mọi lượt ghi của ô nút (toggle · cover ·
 * select) đi qua `gated(` ⇒ [CtlSafetyPolicy.blocked] TRƯỚC khi gọi cổng xe; mọi đường chạm tới cốp (ô thanh nút, ô
 * nhóm Cửa & khoang, chấm cốp bảng Kính) đều là ô của [ControlTileFactory] ⇒ không có đường chạm nào vòng qua cổng.
 */
class TrunkTapGateContractTest {

    private val dir = "src/main/java/com/byd/clusternav/launcher/"
    private fun code(f: String) = SourceRoots.codeOf(dir + f)
    private val factory = code("ControlTileFactory.kt")

    @Test fun `moi luot ghi cua o qua cong cham truoc cong xe`() {
        val gated = SourceRoots.body(factory, "private fun gated(")
        assertTrue(gated.contains("CtlSafetyPolicy.blocked(def.id, arg, motion)"), "cổng chạm là phép :core, một nguồn")
        assertTrue(gated.contains("TileSafetyGate.refuse(tile, def)") && gated.contains("undo()"), "chặn ⇒ hoàn hình + báo")
        assertTrue(Regex("""else act\(\)""").containsMatchIn(gated), "chỉ khi KHÔNG chặn mới chạy lệnh")
        listOf("control().toggle(def.id, nv)", "control().coverLevel(def.id, next)", "control().select(def.id, next)").forEach { w ->
            val i = factory.indexOf(w)
            assertTrue(i > 0, "mất lượt ghi $w")
            assertTrue(factory.substring(maxOf(0, i - 160), i).contains("gated(def, "), "$w không qua gated(")
        }
    }

    /** Mã gate vận tốc phải là kiểu ô mà `gated` bao (STEP/BUTTON không bao) — thêm mã kiểu khác ⇒ đỏ tại đây. */
    @Test fun `ma gate van toc thuoc kieu o duoc bao`() {
        CtlSafetyPolicy.REQUIRES_STATIONARY.forEach { id ->
            val k = ControlRegistry.byId(id)!!.kind
            assertTrue(k == ControlKind.TOGGLE || k == ControlKind.COVER || k == ControlKind.SELECT, "'$id' kiểu $k chưa qua gated(")
        }
    }

    @Test fun `cham coc tren hinh xe di qua o nut that va lac khi bi chan`() {
        val compact = code("ActionMatrixCompact.kt")
        assertTrue(compact.contains("hidden[a?.id ?: return]?.performClick()"), "chấm cốp = performClick ô nút THẬT (qua gated)")
        assertTrue(compact.contains("TileSafetyGate.listen(refused)") && compact.contains("TileSafetyGate.unlisten(refused)"))
        assertTrue(compact.contains("levels.shake("), "chấm lắc khi bị chặn")
        val gate = code("TileSafetyGate.kt")
        assertTrue(gate.contains("CtlSafetyPolicy.readMotion(fresh = c::readFresh"), "chuyển động TƯƠI qua phép đọc chung")
        assertTrue(gate.contains("announceForAccessibility(text)") && gate.contains("Toast.makeText("), "báo bằng chữ + đọc to")
    }

    @Test fun `cau bao co du 5 ngon ngu`() {
        val dirs = listOf("values", "values-en", "values-zh-rCN", "values-th", "values-ms")
        dirs.forEach { d ->
            val x = SourceRoots.text("src/main/res/$d/strings_kachi_safety301.xml")
            listOf("kctl_stop_to_open_trunk", "klight_tap_on", "klight_tap_off").forEach { k ->
                assertEquals(1, Regex("name=\"$k\"").findAll(x).count(), "$d thiếu $k")
            }
        }
        assertTrue(SourceRoots.text("src/main/res/values/strings_kachi_safety301.xml").contains(">Dừng xe để mở cốp<"))
    }

    /**
     * ═══ 3.01 · owner 13/10 *"vậy làm theo luật mới hết đi cho chắc ăn"* — MỘT phép quyết + MỘT phép đọc cho cả ba đường ═══
     * Chạm (`ControlTileFactory.gated`) · giọng nói + phím (`VoiceControlDispatch.run`, phím giao intent xuống đó) · Đảo từ trí
     * nhớ (`KeyCtlPlan`) đều hỏi [CtlSafetyPolicy.blocked]; chuyển động đọc qua [CtlSafetyPolicy.readMotion]. Phép fail-open cũ
     * (`blockedAtSpeed`) quay lại ở bất kỳ đâu ⇒ đỏ.
     */
    @Test fun `ba duong cung mot phep quyet va mot phep doc`() {
        val voice = code("VoiceControlDispatch.kt")
        assertTrue(voice.contains("CtlSafetyPolicy.blocked(def.id, arg, ::motion)"), "giọng nói/phím: phép chung")
        assertTrue(voice.contains("CtlSafetyPolicy.readMotion(fresh = freshCar"), "giọng nói/phím: phép đọc chung")
        assertTrue(voice.contains("VoiceReply.notWhileMoving(shown, l)"), "từ chối qua kênh trả lời sẵn có (nói / toast phím)")
        val plan = SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/launcher/KeyCtlPlan.kt")
        assertTrue(plan.contains("CtlSafetyPolicy.blocked(def.id, value, motion)"), "Đảo từ trí nhớ: phép chung")
        val all = listOf("ControlTileFactory.kt", "VoiceControlDispatch.kt", "TileSafetyGate.kt").map(::code) + plan +
            SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/launcher/CtlSafetyPolicy.kt")
        all.forEach { src ->
            listOf("blockedAtSpeed", "blockedOnTap", "fun speedKmh()").forEach { old -> assertTrue(!src.contains(old), "còn phép cũ $old") }
        }
    }

    /** Câu từ chối của giọng nói/phím = ĐÚNG chữ của cổng chạm ở cả 5 ngôn ngữ (bảng i18n tsv ↔ res values). */
    @Test fun `cau tu choi giong noi trung chu cau cong cham o ca 5 ngon ngu`() {
        val res = mapOf(Lang.VI to "values", Lang.EN to "values-en", Lang.ZH to "values-zh-rCN", Lang.TH to "values-th", Lang.MS to "values-ms")
        val intent = com.byd.clusternav.launcher.voice.VoiceIntent.Control("trunk", 1)
        res.forEach { (lang, d) ->
            val x = SourceRoots.text("src/main/res/$d/strings_kachi_safety301.xml")
            val want = Regex("name=\"kctl_stop_to_open_trunk\">([^<]*)<").find(x)!!.groupValues[1]
            assertEquals(want, com.byd.clusternav.launcher.voice.VoiceReply.notWhileMoving(intent, lang), "$lang")
        }
    }
}
