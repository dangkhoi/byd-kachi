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
        // ⚠ (V) FEATURE-FILTER 2026-09-17: `rain_close` và `mirror_auto` rời danh sách miễn trừ cùng chính hai
        // nút đó (owner chấm NO) — giữ lại là nuôi một ngoại lệ không còn chủ.
        val exempt = setOf(
            "child_lock",       // khoá TRẺ EM — bật là an toàn hơn, không mở cabin
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

    // ══ C (owner test xe 2026-09-19) · CHỈ MỞ ĐƯỢC KHI XE ĐANG DỪNG ════════════════════════════════════════

    @Test fun `cop va ca-po chi mo duoc khi xe dung`() {
        listOf("trunk", "hood").forEach { id ->
            assertTrue(
                CtlSafetyPolicy.requiresStationary(id),
                "'$id' mở ra là BUNG khỏi bao xe / che tầm nhìn ⇒ phải gate theo vận tốc",
            )
        }
    }

    /**
     * …và **KHÔNG** gate thứ mở-lúc-đang-chạy là bình thường.
     *
     * Đây là nửa quan trọng hơn của cặp bài canh: một gate an toàn nới quá tay sẽ biến *"mở kính"* (việc ai cũng
     * làm khi đang chạy) thành một lời từ chối vô cớ — tức tự nó thành lỗi. Thêm một mã vào
     * [CtlSafetyPolicy.REQUIRES_STATIONARY] mà không thuộc diện *"bung khỏi bao xe"* sẽ làm bài này đỏ.
     */
    @Test fun `kinh · cua so troi · den · khoa cua KHONG bi gate theo van toc`() {
        listOf("window", "windows_all", "win_lf", "win_rf", "win_lr", "win_rr",
            "sunroof", "sunshade", "readl", "lock", "door", "fan", "temp").forEach { id ->
            assertFalse(
                CtlSafetyPolicy.requiresStationary(id),
                "'$id' mở/bật lúc đang chạy là bình thường ⇒ KHÔNG được gate (gate rộng = lỗi, không phải an toàn)",
            )
        }
    }

    @Test fun `moi ma trong tap dung-xe deu la control THAT`() {
        CtlSafetyPolicy.REQUIRES_STATIONARY.forEach { id ->
            assertTrue(ControlRegistry.byId(id) != null, "mã '$id' không còn trong ControlRegistry")
        }
    }

    /** …và cùng phép vệ sinh cho tập bộ-phận-chạy-chậm ([SOÁT lượt E · P1]): mã rữa thì cổng mất tác dụng im lặng. */
    @Test fun `moi ma trong tap chay-cham deu la control THAT`() {
        CtlSafetyPolicy.MOVES_SLOWLY.forEach { id ->
            assertTrue(ControlRegistry.byId(id) != null, "mã '$id' không còn trong ControlRegistry")
        }
    }

    /**
     * Tập gate-vận-tốc phải là **tập con** của tập cần xác nhận.
     *
     * Hai danh sách trả lời hai câu hỏi khác nhau ([CtlSafetyPolicy.CONFIRM_REQUIRED] = *"lệnh từ ngoài"*,
     * [CtlSafetyPolicy.REQUIRES_STATIONARY] = *"vận tốc"*) nên chúng cố ý không bằng nhau. Nhưng một mã nguy hiểm
     * tới mức phải gate theo vận tốc mà lại **không** cần xác nhận khi bắn từ broadcast thì là một lỗ: đường
     * broadcast bỏ qua cổng nào thì đường ấy thành cửa sau.
     */
    @Test fun `tap dung-xe la tap con cua tap can xac nhan`() {
        CtlSafetyPolicy.REQUIRES_STATIONARY.forEach { id ->
            assertTrue(
                CtlSafetyPolicy.needsConfirm(id),
                "'$id' gate theo vận tốc mà không cần auto_confirm ⇒ broadcast thành cửa sau",
            )
        }
    }

    /**
     * ⚠ [SOÁT lượt C · P3] **GÓI LỆNH KHÔNG ĐƯỢC CHỨA MÃ GATE THEO VẬN TỐC** — nếu không, gate có cửa sau.
     *
     * Gate C sống ở `VoiceDispatcher.runControl`, còn một gói lệnh đi đường khác hẳn:
     * `runMacro` → `MacroRunner.run` → `CarControlPort.actByKind`, **không** qua `runControl` ⇒ không qua gate. Hôm
     * nay chưa có lỗ ([ĐO] ba gói hiện tại chỉ chạm 4 kính · `readl` · `door` · `lock`), nên đây là bài canh cho
     * lượt SAU: thêm `trunk` vào một gói *"rời xe"* nào đó là mở lại đúng ca owner vừa báo, và mở **im lặng** vì mã
     * gate vẫn còn nguyên ở chỗ cũ.
     *
     * Chọn khoá bằng bất biến thay vì bơm tốc độ vào `MacroRunner`: một gói lệnh là thứ người lái tự dựng từ ý định
     * rõ ràng, và một gói nửa chạy nửa bị chặn còn khó hiểu hơn một gói không bao giờ chứa mã ấy.
     */
    @Test fun `khong goi lenh nao chua ma gate theo van toc`() {
        ActionMacros.ALL.forEach { macro ->
            macro.steps.forEach { step ->
                assertFalse(
                    CtlSafetyPolicy.requiresStationary(step.controlId),
                    "gói '${macro.id}' chứa '${step.controlId}' — gói lệnh KHÔNG qua gate C ⇒ cửa sau của gate",
                )
            }
        }
    }
}
