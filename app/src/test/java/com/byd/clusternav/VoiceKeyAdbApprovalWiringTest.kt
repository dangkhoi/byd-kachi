package com.byd.clusternav

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * F2 (owner 2026-08-24): *"start app vẫn chưa hold mic gọi gemini/kiki được, phải tắt, mở lại thì mới xin
 * được quyền và mới sử dụng được"*.
 *
 * Hành vi thật của vòng chờ nằm ở `:car-integration`
 * ([com.byd.clusternav.carexec.LocalShellApprovalRetryTest], chạy được off-car với transport tiêm vào).
 * Test này khoá phần `:app` không chạy off-car được: **đúng đường người dùng đi** có được nối vào vòng chờ
 * đó không, và — quan trọng không kém — các đường DÙNG CHUNG khác có bị kéo theo không.
 *
 * Vì sao đọc source thay vì gọi hàm: `AssistantLauncher` cần `Context` thật (`AdbKeys.ensure` → `filesDir`)
 * và `:app` không có Robolectric; cùng khuôn với [AccessibilityForceBindTest].
 *
 * ⚠ Bài học 08-24 (F1): gỡ `WazeHudSource` đã vô tình gỡ 3 dòng nó đang **gánh hộ**. Test `duong nen dung
 * CHUP MU...` cuối chính là chốt chặn — các đường boot/FGS được phép nhận **chụp-mũ chống-treo**
 * ([LocalShellRetry.BACKGROUND_READ_CAP], 1 lần thử + hạn đọc 30 s, thêm ở F6 2026-08-25) NHƯNG tuyệt đối
 * KHÔNG được nhận chính sách **CHỜ+THỬ-LẠI** ([LocalShellRetry.AWAIT_ADB_APPROVAL], 4 lần ~31 s): không ai
 * đứng nhìn lúc boot nên thử lại chỉ làm `VietMapAutostart` (`BootSetupService`, đồng bộ, giữ FGS sống)
 * chậm ~31 s mà chẳng ai bấm "Cho phép". Cap ≠ retry — cap CẮT treo, retry KÉO DÀI boot.
 */
class VoiceKeyAdbApprovalWiringTest {

    private fun app(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve("src"))) current.resolve(relative) else current.resolve("app").resolve(relative)
    }

    private fun read(relative: String) = app("src/main/java/com/byd/clusternav/$relative").toFile().readText()

    private val assistantLauncher by lazy { read("modules/voicekey/AssistantLauncher.kt") }

    /** Thân hàm phát keyevent 231 — ĐÚNG đoạn chạy khi owner giữ phím mic trên vô-lăng. */
    private val voiceAssistKeyPath by lazy {
        val start = assistantLauncher.indexOf("private fun launchViaVoiceAssistKey")
        val end = assistantLauncher.indexOf("private fun reportProgress")
        assertTrue(start in 0 until end, "không tìm thấy thân launchViaVoiceAssistKey — test này đã lạc chỗ")
        assistantLauncher.substring(start, end)
    }

    @Test
    fun `duong phim mic cho owner bam Cho phep thay vi bo cuoc ngay lan dau`() {
        assertTrue(
            voiceAssistKeyPath.contains("LocalShellRetry.AWAIT_ADB_APPROVAL"),
            "giữ phím mic phải đi qua chính sách chờ-cấp-quyền, không phải một lần rồi thôi",
        )
        assertTrue(
            voiceAssistKeyPath.contains("LocalDeviceShell.sessionResult("),
            "phải dùng sessionResult (biết lý do hỏng), không phải session() (chỉ trả null câm)",
        )
        assertTrue(
            voiceAssistKeyPath.contains("input keyevent 231"),
            "vẫn đúng lệnh cũ — bản vá chỉ thêm đường chờ, không đổi việc đang chạy tốt",
        )
    }

    @Test
    fun `owner duoc bao dang cho gi va hong vi sao — khong con im lang`() {
        assertTrue(
            voiceAssistKeyPath.contains("onProgress = { attempt, reason, waitMs -> reportProgress("),
            "phải báo tiến trình lúc đang chờ, không để owner đoán",
        )
        assertTrue(
            voiceAssistKeyPath.contains("failureMessage(result.reason)"),
            "hỏng thì phải nói ra LÝ DO cho owner",
        )
        // Hỏng SAU khi đã gửi được lệnh nghĩa là bắt tay adb đã xong ⇒ nói "chưa cấp quyền" là nói SAI.
        assertTrue(
            voiceAssistKeyPath.contains("if (result.commandDispatched)"),
            "phải RẼ NHÁNH theo hỏng-trước-khi-gửi / hỏng-sau-khi-gửi trước khi chọn câu nói (log không tính)",
        )
        // Mỗi lý do phải có một câu riêng — gộp hết vào một câu là quay lại đúng chỗ cũ.
        for (reason in listOf("AWAITING_APPROVAL", "AUTH_REJECTED", "PORT_CLOSED", "IO_ERROR", "UNKNOWN")) {
            assertTrue(
                assistantLauncher.contains("LocalShellFailure.$reason"),
                "failureMessage phải phân biệt $reason",
            )
        }
    }

    @Test
    fun `chon tro ly trong app cung cho cap quyen`() {
        val setAssistant = assistantLauncher.substring(assistantLauncher.indexOf("fun setSystemAssistant"))
        assertTrue(
            setAssistant.contains("LocalShellRetry.AWAIT_ADB_APPROVAL"),
            "đường owner chọn Gemini trong app cũng bung hộp thoại adb lần đầu",
        )
        assertTrue(
            setAssistant.contains("failureMessage(result.reason)"),
            "chuỗi trả về cho Toast phải nói đúng lý do, không còn câu gộp 5555-hoặc-key",
        )
        // Hạn đọc 6 s có thể cắt ngang công thức 12 lệnh — khe giữa `voice_interaction_service ''` và lần
        // đặt lại là lúc trợ lý hệ thống đang RỖNG. Owner phải được bảo là "chạy dở", không phải "hỏng".
        assertTrue(
            setAssistant.contains("if (result.commandDispatched)"),
            "hỏng giữa công thức phải RẼ NHÁNH để nói ra là đã chạy một phần (log không tính)",
        )
    }

    @Test
    fun `vong cho la don-luong — bam mic nhieu lan khong bung nhieu hop thoai`() {
        assertTrue(
            voiceAssistKeyPath.contains("voiceAssistInFlight.compareAndSet(false, true)"),
            "vòng chờ ~30 s dài hơn debounce 1.5 s ⇒ phải có chốt đơn-luồng riêng",
        )
        assertTrue(
            voiceAssistKeyPath.contains("voiceAssistInFlight.set(false)") &&
                voiceAssistKeyPath.contains("finally"),
            "chốt phải nhả trong finally, nếu không một lần hỏng là câm phím mic vĩnh viễn",
        )
        // `start()` ném SAU khi CAS đã chiếm chốt ⇒ `finally` trong runnable không bao giờ chạy ⇒ chốt kẹt
        // true VĨNH VIỄN ⇒ phím mic chết tới khi kill process. Phải có đường nhả thứ hai ở ngoài luồng.
        assertTrue(
            voiceAssistKeyPath.contains("runCatching { worker.start() }") &&
                voiceAssistKeyPath.indexOf("runCatching { worker.start() }") <
                voiceAssistKeyPath.indexOf("return true   // đã nhận lệnh"),
            "khởi luồng hỏng phải nhả chốt, không được để kẹt",
        )
    }

    /**
     * Vòng chờ dài ~31 s. Trong khoảng đó mọi cú bấm mic bị nuốt — im lặng suốt nửa phút CHÍNH LÀ triệu
     * chứng owner báo ở F2, nên không được lặp lại nó dưới dạng mới.
     */
    @Test
    fun `bam mic trong luc dang cho thi duoc bao, va khong bi day them debounce`() {
        val cas = voiceAssistKeyPath.indexOf("voiceAssistInFlight.compareAndSet(false, true)")
        val stamp = voiceAssistKeyPath.indexOf("lastVoiceAssistEmitMs = now")
        assertTrue(cas in 0 until stamp, "cú bấm bị nuốt KHÔNG được đẩy mốc debounce (chết thêm 1,5 s)")
        assertTrue(
            voiceAssistKeyPath.contains("voiceAssistBusyNoticed.compareAndSet(false, true)"),
            "phải nói cho owner biết đang bận — đúng MỘT lần mỗi vòng, không phải ~20 toast",
        )
    }

    @Test
    fun `duong nen dung CHUP MU chong-treo (F6), KHONG duoc dung chinh sach CHO+THU-LAI`() {
        // 4 đường NỀN của F6 (note: VietMapAutostart nặng nhất — chạy ĐỒNG BỘ trong FGS boot — rồi
        // UpdateChecker/NavConnect/ClusterDiag). Nay CHỤP MŨ chống-treo: 1 lần thử + hạn đọc 30 s ⇒ adbd im
        // lặng không treo VĨNH VIỄN. CAP ≠ RETRY.
        for (file in listOf("VietMapAutostart.kt", "UpdateChecker.kt", "NavConnect.kt", "modules/clustercast/ClusterDiag.kt")) {
            val source = read(file)
            assertTrue(
                source.contains("LocalShellRetry.BACKGROUND_READ_CAP"),
                "$file phải nhận chụp-mũ chống-treo (F6) — nó chạy lúc khởi động máy / trong FGS boot, treo vĩnh viễn là chết tiến trình",
            )
            assertFalse(
                source.contains("AWAIT_ADB_APPROVAL"),
                "$file KHÔNG được CHỜ+THỬ-LẠI (~31 s, 4 lần): không owner nào đứng nhìn lúc boot ⇒ thử lại chỉ làm chậm boot mà chẳng ai bấm 'Cho phép'",
            )
        }
        // VietMapWidgetDiagActivity là đường DIAG owner-CHỦ-ĐỘNG (không boot/FGS) ⇒ để nguyên session() cũ:
        // owner đứng đó tự thoát được, không cần chụp mũ. Nhưng cũng KHÔNG được nhận chính sách chờ+thử-lại.
        assertFalse(
            read("vietmapwidget/VietMapWidgetDiagActivity.kt").contains("AWAIT_ADB_APPROVAL"),
            "VietMapWidgetDiagActivity (diag owner-chủ-động) không được chờ+thử-lại",
        )
    }
}
