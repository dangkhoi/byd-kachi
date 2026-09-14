package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá tập DENYLIST/CONFIRM của lệnh `ctl` — nguồn duy nhất cho cả cầu kiểm thử lẫn script sweep (spec §9).
 */
class CtlSafetyPolicyTest {

    @Test fun `moi ma trong denylist deu la control THAT`() {
        CtlSafetyPolicy.CONFIRM_REQUIRED.forEach { id ->
            assertTrue(ControlRegistry.byId(id) != null, "mã denylist '$id' không còn trong ControlRegistry")
        }
    }

    @Test fun `moi control mo mo-khoa than xe deu nam trong denylist`() {
        // Khoá theo NGỮ NGHĨA: mọi control mở/khoá cửa·kính·nóc·cốp·ca-pô phải cần xác nhận. Thêm một mã kính/cửa
        // mới mà quên đưa vào CONFIRM_REQUIRED sẽ làm bài này đỏ tại chỗ khai (CLAUDE.md §4 phạm vi tường minh).
        val bodyOpenKeywords = listOf("kính", "cửa", "nóc", "cửa sổ trời", "cốp", "ca-pô", "khoá")
        val exempt = setOf(
            "child_lock",       // khoá TRẺ EM — bật là an toàn hơn, không mở cabin
            "rain_close",       // tự ĐÓNG kính khi mưa — không mở
            "mirror_auto",      // gập gương KHI KHOÁ — không mở cabin
        )
        ControlRegistry.ALL.filter { def ->
            def.domain == Domain.BODY &&
                bodyOpenKeywords.any { def.label.contains(it, ignoreCase = true) } &&
                def.id !in exempt
        }.forEach { def ->
            assertTrue(
                CtlSafetyPolicy.needsConfirm(def.id),
                "control mở/khoá thân xe '${def.id}' (${def.label}) phải cần auto_confirm",
            )
        }
    }

    @Test fun `control khi hau vo hai khong can confirm`() {
        listOf("readl", "fan", "temp", "pm25", "seatc", "recirc", "vol").forEach { id ->
            assertFalse(CtlSafetyPolicy.needsConfirm(id), "'$id' không mở thân xe ⇒ không cần confirm")
        }
    }

    @Test fun `denylist phu dung tap task yeu cau`() {
        // lock/unlock (lock,door) · cốp (trunk) · kính mở-hết + từng kính · nóc/rèm.
        listOf("lock", "door", "trunk", "windows_all", "win_lf", "win_rf", "win_lr", "win_rr", "sunroof", "sunshade")
            .forEach { assertTrue(CtlSafetyPolicy.needsConfirm(it), "denylist thiếu '$it'") }
    }
}
