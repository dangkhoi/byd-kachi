package com.byd.clusternav

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeadReckonRetirementTest {
    @Test
    fun `active product has exactly two tracks and no DR or mock provider wiring`() {
        val manifest = app("src/main/AndroidManifest.xml").toFile().readText()
        // Màn chính của app nay là Kachi (màn ClusterNav cũ gỡ 2026-09-13 — S3 · R1).
        val home = app("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt").toFile().readText()
        val receiver = app("src/main/java/com/byd/clusternav/RebindReceiver.kt").toFile().readText()
        val prefs = app("src/main/java/com/byd/clusternav/Prefs.kt").toFile().readText()
        listOf(manifest, home, receiver, prefs).forEach { text ->
            assertFalse(text.contains("DeadReckon"))
            assertFalse(text.contains("modules.deadreckon"))
            assertFalse(text.contains("modules.mockloc"))
            assertFalse(text.contains("ACCESS_MOCK_LOCATION"))
        }
        assertFalse(prefs.contains("gpsAuto"))
        // Hai nhánh sản phẩm phải còn nguyên TÊN trên bề mặt người dùng. Trước 2026-09-13 bài này đọc nhãn
        // trong `activity_main.xml`; nhãn nay ở tài nguyên của Kachi (`strings_kachi.xml`, cả hai ngôn ngữ).
        val labels = app("src/main/res/values-en/strings_kachi.xml").toFile().readText()
        assertTrue(labels.contains("Navigation + HUD"))
        assertTrue(labels.contains("Cluster cast"))
    }

    @Test
    fun `nguon da nghi phai bien khoi cay lam viec`() {
        // 2026-07-27, chủ xe quyết: bỏ hẳn Dead Reckon, "đang fail quá, sau này cần làm thì tìm giải pháp
        // mới sau". Bản trước bài kiểm này ĐÒI file phải còn, với lý do "đọc lại được để rollback" — nhưng
        // git đã giữ nguyên lịch sử, nên giữ thêm bản trong cây chỉ để lại 1.096 dòng không ai chạy, không
        // ai kiểm, mà vẫn phải đọc mỗi lần soát kiến trúc. Giờ đảo lại: chúng PHẢI biến mất.
        assertFalse(app("src/main/java/com/byd/clusternav/modules/deadreckon").toFile().exists())
        assertFalse(app("src/main/java/com/byd/clusternav/modules/mockloc").toFile().exists())
    }

    @Test
    fun `no active source outside the retired packages can reach DR or mock injection`() {
        // The five-file check above cannot catch a future caller in some other file, and mock
        // location changes what the head unit believes its position is. Lock the whole tree:
        // comments may mention the retired code, executable references may not.
        val retired = Regex("""(deadreckon|mockloc)""")
        val callSite = Regex(
            """(^|[^\w.])(import\s+com\.byd\.clusternav\.modules\.(deadreckon|mockloc)|""" +
                """MockLoc\s*\.\s*\w+\s*\(|DeadReckon(Module|Service|State)\s*[.(])"""
        )
        val offenders = mutableListOf<String>()
        val root = app("src/main/java/com/byd/clusternav")
        Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .filter { !retired.containsMatchIn(it.parent.fileName.toString()) }
                .forEach { file ->
                    file.toFile().readText().lineSequence().forEachIndexed { index, line ->
                        val code = line.substringBefore("//").substringBefore("* ")
                        if (callSite.containsMatchIn(code)) offenders += "$file:${index + 1}"
                    }
                }
        }
        assertTrue(offenders.isEmpty(), "Dead Reckon / mock injection reachable from: $offenders")
    }

    /**
     * ⚠⚠ **BẤT BIẾN AN TOÀN — 1.85 thu HẸP, không nới lỏng.**
     *
     * Bản trước chặn **năm** tên quyền, gộp `ACCESS_FINE_LOCATION` cùng bốn tên kia. Nhưng hai việc bị gộp vào một
     * bài kiểm là hai việc khác hẳn nhau:
     *  • **GHI** vị trí giả — đúng sự cố 2026-07-27 (Kachi làm app mock-location ⇒ **ghim GPS của cả xe**; README
     *    ghi *"Đừng chọn ClusterNav làm app mock-location"*). Đây là thứ phải chặn vĩnh viễn.
     *  • **ĐỌC** tuổi của lần định vị gần nhất — không đổi vị trí của ai, không ghi gì, và là **điều kiện cần** của
     *    cổng *"đã ra khỏi hầm chưa"* mà spec `kachi-automation.html` R2.4/R3 (owner đã duyệt) yêu cầu.
     *
     * [ĐO] `isProviderEnabled` một mình **không** trả lời được câu hỏi của R2.4: trong hầm GPS provider vẫn BẬT,
     * chỉ là không có fix ⇒ nếu chỉ hỏi provider thì luật sẽ dẫn ngay trong hầm, đúng ca tính năng sinh ra để chặn.
     * Nên quyền ĐỌC là bắt buộc, còn phần nguy hiểm thì **vẫn bị ghim, và nay ghim CHẶT HƠN**: bốn tên quyền kia ở
     * lại đây, và [ca hành vi][the app never writes or subscribes to location] mới chặn cả `addTestProvider` /
     * `requestLocationUpdates` — hai đường mà bài cũ **không** soi (bài cũ chỉ đọc manifest, nên một lượt
     * `requestLocationUpdates` bằng `ACCESS_FINE_LOCATION` vẫn qua được nó).
     *
     * ⚠ Owner cần biết: đây là một thay đổi có ý thức lên một bất biến an toàn (spec §OQ1 đã nêu trước, bản này
     * thực hiện). Nếu owner muốn giữ *"tuyệt đối không quyền định vị"* thì cổng GPS của automation #2 phải bỏ, và
     * luật `requireGps` mất nghĩa — ghi rõ ở `docs/_handoff/1.85-stage3.md`.
     */
    @Test
    fun `the shipped manifest requests no mock or broad location permission`() {
        val manifest = app("src/main/AndroidManifest.xml").toFile().readText()
        listOf(
            "ACCESS_MOCK_LOCATION",
            "ACCESS_COARSE_LOCATION",
            "ACCESS_BACKGROUND_LOCATION",
            "FOREGROUND_SERVICE_LOCATION",
        ).forEach { permission ->
            assertFalse(manifest.contains(permission), "manifest still requests $permission")
        }
    }

    /**
     * Quyền định vị chỉ được dùng để **ĐỌC**. Quét toàn bộ `app/src/main` (không chỉ manifest — đó là lỗ của bài
     * cũ): không một dòng mã nào được dựng test-provider, ghi vị trí, hay đăng ký nhận định vị liên tục.
     *
     * Chú thích được phép nhắc tên (KDoc của `GpsAvailability` giải thích chính những điều này); **mã** thì không.
     */
    @Test
    fun `the app never writes or subscribes to location`() {
        val banned = listOf(
            "addTestProvider",
            "setTestProviderLocation",
            "setTestProviderEnabled",
            "clearTestProviderLocation",
            "requestLocationUpdates",
            "requestSingleUpdate",
            "LocationListener",
        )
        val offenders = mutableListOf<String>()
        val root = app("src/main/java/com/byd/clusternav")
        Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.forEach { file ->
                file.toFile().readText().lineSequence().forEachIndexed { index, line ->
                    // Bỏ chú thích TRƯỚC khi soi — cùng cách bài `no active source…` ở trên làm.
                    val code = line.substringBefore("//").substringBefore("* ")
                    banned.forEach { needle ->
                        if (code.contains(needle)) offenders += "$file:${index + 1} → $needle"
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "định vị chỉ được ĐỌC (isProviderEnabled + getLastKnownLocation): $offenders")
    }

    private fun app(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve("src"))) current.resolve(relative) else current.resolve("app").resolve(relative)
    }
}
