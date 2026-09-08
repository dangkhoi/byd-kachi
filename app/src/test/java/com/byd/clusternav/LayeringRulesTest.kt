package com.byd.clusternav

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Cưỡng chế các quy tắc xếp chỗ trong docs/refactor-car-execution/layering-rules.md.
 *
 * Có test này vì trong một ngày tôi xếp sai chuồng bốn lần và mỗi lần chỉ phát hiện khi tình cờ đo lại.
 * Quy tắc viết trong tài liệu mà không ai cưỡng chế thì chỉ là ý định.
 */
class LayeringRulesTest {

    private fun root(vararg candidates: String): Path? =
        candidates.map(Paths::get).firstOrNull(Files::exists)

    private fun kotlinFiles(root: Path): List<Path> =
        Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.toList()
        }

    private val androidOrDadb = Regex("""(?m)^import (android|androidx|dadb)\.""")
    private val androidOnly = Regex("""(?m)^import android""")

    /**
     * Test của logic `:core` phải nằm trong `:core`.
     *
     * Checklist thủ công 2026-07-27 bắt được 8 file test nằm sai chuồng, trong đó hai file do chính tôi
     * viết cùng tối. Hại thật sự: bộ test riêng của `:core` không phủ chính lớp của nó, nên có thể phá
     * `:core` mà chỉ biết khi chạy bộ test Android — và tệ hơn, một test như thế có thể lỡ phụ thuộc
     * Android mà không ai thấy, làm mất luôn ý nghĩa "core chạy được không cần thiết bị".
     *
     * Tiêu chí: test trong `:app` mà KHÔNG import Android và KHÔNG dùng khai báo nào của riêng `:app` thì
     * nó đang kiểm `:core`. Ngoại lệ phải kể tên và nói lý do, không được để danh sách trống mọc dần.
     */
    @Test
    fun `test cua logic core khong duoc nam trong app`() {
        val appTests = root("app/src/test/java", "../app/src/test/java") ?: return
        val appMain = root("app/src/main/java", "../app/src/main/java") ?: return
        val coreMain = root("core/src/main/kotlin", "../core/src/main/kotlin") ?: return

        val declaration = Regex(
            """(?m)^(?:internal |private )?(?:data |sealed |enum |abstract )*(?:class|object|interface|fun|val) ([A-Za-z0-9_]+)""",
        )
        fun declarations(root: Path) = kotlinFiles(root)
            .flatMap { declaration.findAll(it.toFile().readText()).map { match -> match.groupValues[1] } }
            .toSet()

        val appOnly = declarations(appMain) - declarations(coreMain)

        // Ngoại lệ: đối tượng kiểm của nó là build script CỦA `:app`, nên nó thuộc `:app` dù không chạm
        // Android hay lớp Kotlin nào.
        val allowed = setOf("BuildArtifactNamingTest.kt")

        val misplaced = kotlinFiles(appTests)
            .filter { it.fileName.toString() !in allowed }
            .filter { file ->
                val text = file.toFile().readText()
                val touchesAndroid = androidOrDadb.containsMatchIn(text) || text.contains("Robolectric")
                val touchesAppOnly = appOnly.any { name -> Regex("""\b${Regex.escape(name)}\b""").containsMatchIn(text) }
                !touchesAndroid && !touchesAppOnly
            }
            .map { it.fileName.toString() }

        assertTrue(
            misplaced.isEmpty(),
            "test chỉ dùng logic :core mà nằm trong :app — dời sang core/src/test: $misplaced",
        )
    }

    @Test
    fun `core khong duoc biet Android hay dadb`() {
        val root = root("core/src/main/kotlin", "../core/src/main/kotlin") ?: return
        val offenders = kotlinFiles(root).filter { androidOrDadb.containsMatchIn(it.toFile().readText()) }
        assertEquals(emptyList<Path>(), offenders, "quy tắc Q1: :core là JVM thuần")
    }

    @Test
    fun `car-integration khong duoc biet Android`() {
        // Nó nói với head unit qua adb; API Android cục bộ là việc của :app. Nếu Android lọt vào đây thì
        // CLI runner không chạy được nữa — tức mất đúng lý do module này tồn tại.
        val root = root("car-integration/src/main/kotlin", "../car-integration/src/main/kotlin") ?: return
        val offenders = kotlinFiles(root).filter { androidOnly.containsMatchIn(it.toFile().readText()) }
        assertEquals(emptyList<Path>(), offenders, "quy tắc Q1: transport phải chạy trên JVM thuần")
    }

    /**
     * Số file trong `:app` không dùng Android/dadb gì cả — tức đang nằm sai chuồng theo Q1.
     *
     * Chỉ được phép **giảm**. Khởi điểm 27/7: 7 file / 995 LOC. Sau khi review B1–B5 theo checklist, bốn
     * file đã về đúng chuồng: `WmParse`, `StackParse`, `DisplayParse` (comment của chính chúng ghi "PURE
     * (không đụng Android)" — parser thuộc `:core` theo bảng xếp chỗ) và `AppScale` (dữ liệu cấu hình).
     *
     * Sau khi gỡ cast v2 (2026-08-17) không còn file thuần nào ở `:app`: `CastActivityRefresh` và
     * `CastOperationStatus` — hai file thuần cuối cùng — đã bị xoá cùng stack v2 (cả hai import gói v2
     * cũ). Parser thuần (`WmParse`/`StackParse`/`DisplayParse`) và dữ liệu (`AppScale`) đã về `:core`
     * từ trước.
     *
     * `LocalProcessShellGateway` đã bị xoá (dead code, 2026-08-02).
     * `ModuleRegistry` đã bị xoá hoặc di chuyển trước đó.
     */
    private val pureFilesStillInApp = 0

    @Test
    fun `so file thuan con nam trong app chi duoc giam`() {
        val root = root("app/src/main/java", "../app/src/main/java") ?: return
        val pure = kotlinFiles(root).filter { file ->
            val text = file.toFile().readText()
            !androidOrDadb.containsMatchIn(text) &&
                !Regex("""\b(Context|View|Activity|Service|Bitmap|Canvas)\b""").containsMatchIn(text)
        }
        assertEquals(
            pureFilesStillInApp,
            pure.size,
            "đổi rồi: nếu giảm thì hạ con số; nếu tăng thì file thuần mới đang bị đặt vào :app. " +
                "Hiện: ${pure.map { it.fileName.toString() }.sorted()}",
        )
    }

    /**
     * Q1 áp cho `:car-integration`: mọi file ở đây phải thật sự nói với thiết bị.
     *
     * Sinh ra từ một lỗi thật: `CarExecCommands` 276 dòng nằm trong module transport với **0** lần dùng
     * dadb, bị giữ lại chỉ vì phụ thuộc một kiểu kết quả. Logic thuần nằm nhờ trong module thiết bị thì
     * không ai test nó off-car được — đúng cái giá đã phải trả với `CastAndroidRuntime`.
     */
    @Test
    fun `moi file trong car-integration phai that su dung dadb`() {
        val root = root("car-integration/src/main/kotlin", "../car-integration/src/main/kotlin") ?: return
        val pure = kotlinFiles(root).filter { !it.toFile().readText().contains("dadb") }
        assertEquals(
            emptyList<String>(),
            pure.map { it.fileName.toString() },
            "file không dùng dadb thì thuộc :core, không phải :car-integration",
        )
    }

    /**
     * Q3: mọi file phải thuộc một cột feature. File nằm ở gốc package là file không có chủ.
     *
     * Sinh ra từ lỗi thật: sau khi dời, 5 file navigation nằm ở gốc `com.byd.clusternav` trong `:core`,
     * không thuộc cột nào — nên không ai biết ai sở hữu chúng khi cần đổi.
     */
    @Test
    fun `khong file nao nam o goc package cua core`() {
        val root = root("core/src/main/kotlin/com/byd/clusternav", "../core/src/main/kotlin/com/byd/clusternav")
            ?: return
        val orphans = Files.list(root).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.toList()
        }
        assertEquals(
            emptyList<String>(),
            orphans.map { it.fileName.toString() },
            "mỗi file phải nằm trong một cột feature (navigation/, modules/, carexec/, core/)",
        )
    }

    /**
     * P4: tên không được nói sai về chuồng.
     *
     * `CastAndroidGateway` sau khi sang module không có Android thì cái tên thành lời nói dối, và
     * `AndroidObservedStateParser` trong `:core` cũng vậy. Người đọc sau sẽ xếp chỗ sai theo cái tên.
     */
    @Test
    fun `khong ten nao chua Android trong module khong co Android`() {
        val roots = listOfNotNull(
            root("core/src/main/kotlin", "../core/src/main/kotlin"),
            root("car-integration/src/main/kotlin", "../car-integration/src/main/kotlin"),
        )
        val liars = roots.flatMap(::kotlinFiles).filter { file ->
            Regex("""(?m)^(?:internal )?(?:object|class|interface) [A-Za-z]*Android""")
                .containsMatchIn(file.toFile().readText())
        }
        assertEquals(emptyList<String>(), liars.map { it.fileName.toString() }, "tên nói sai về chuồng")
    }

    /** Q3: feature không gọi ngang feature, kiểm trong `:core` nơi cả hai cùng sống. */
    @Test
    fun `navigation va cast khong goi ngang nhau trong core`() {
        val root = root("core/src/main/kotlin/com/byd/clusternav", "../core/src/main/kotlin/com/byd/clusternav")
            ?: return
        val nav = root.resolve("navigation")
        val cast = root.resolve("modules")
        if (Files.exists(nav)) {
            kotlinFiles(nav).forEach { file ->
                assertTrue(
                    !file.toFile().readText().contains("modules.clustercast"),
                    "${file.fileName}: navigation không được import Cast",
                )
            }
        }
        if (Files.exists(cast)) {
            kotlinFiles(cast).forEach { file ->
                assertTrue(
                    !file.toFile().readText().contains("clusternav.navigation"),
                    "${file.fileName}: Cast không được import navigation",
                )
            }
        }
    }

    @Test
    fun `tai lieu quy tac ton tai va liet ke du cot cuong che`() {
        val doc = root(
            "docs/refactor-car-execution/layering-rules.md",
            "../docs/refactor-car-execution/layering-rules.md",
        )
        assertTrue(doc != null, "thiếu tài liệu quy tắc")
        val text = doc!!.toFile().readText()
        listOf(
            "Q1", "Q2", "Q3", "P1", "P2", "P3", "P4",
            "chưa cưỡng chế", "LayeringRulesTest", "attestation",
        ).forEach {
            assertTrue(text.contains(it), "tài liệu thiếu phần '$it'")
        }
    }
}
