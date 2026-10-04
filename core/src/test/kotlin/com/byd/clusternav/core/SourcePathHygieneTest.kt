package com.byd.clusternav.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ Soát vòng 4 (quét bảo mật, mục INFO) + vòng 5 — mã/test/script/tài liệu KHÔNG trỏ vào thư mục tạm ngoài repo ═══
 *
 * Chú thích từng ghi đường dẫn thư mục nháp của phiên làm việc (vd tệp đo PIL, bảng kết quả Piper→ASR, bản nguồn AOSP chép
 * về). Thư mục ấy không có trong repo, chết theo phiên, và lộ cấu trúc máy dev ⇒ người đọc sau không lần lại được bằng chứng.
 * Chú thích nói "công cụ ngoài repo" / "bản nguồn AOSP" (kèm tag + `tệp:dòng` của AOSP), không ghi đường dẫn.
 *
 * Soát vòng 5 [P3]: bản vòng 4 chỉ quét 4 gốc cố định (`app/src`, `core/src`, `car-integration/src`, `scripts`) ⇒ hai mô-đun
 * Gradle (`vehicle-contracts`, `offcar-planner`) và `voice/`, `tools/`, `hal-helper/` nằm ngoài lưới; lại đọc cả tệp bị
 * `.gitignore` chặn. Nay:
 *  - MÃ: `src` của MỌI mô-đun khai trong `settings.gradle.kts` (đổi tên/thêm mô-đun tự vào lưới; thiếu `src` ⇒ đỏ) + `scripts`,
 *    `voice`, `tools`, `hal-helper` — chữ ấy không được xuất hiện ở đâu;
 *  - TÀI LIỆU (`docs/`): văn xuôi được nhắc chữ ấy (mô tả quy trình), chỉ cấm dạng ĐƯỜNG DẪN (`…/<chữ ấy>/…`). Các tệp cũ đã
 *    có dạng ấy (sổ bàn giao, chẩn đoán, spec trước 04/10) giữ ở [DOCS_LEGACY] — số lần chỉ được GIẢM; tệp mới / thêm lần mới ⇒
 *    đỏ. Dọn chúng là việc của tài liệu (đề xuất backlog), không làm bài này đỏ;
 *  - danh sách tệp = cái `git add -A` sẽ đưa vào (`git ls-files -co --exclude-standard`), không có git ⇒ duyệt thư mục (bỏ
 *    `build/`, `.git/`, `.gradle/`). Nội dung ĐÃ vào chỉ mục (index) thì quét bảo mật trước commit (CLAUDE.md §6) soi.
 */
class SourcePathHygieneTest {

    /** Ghép từ hai mảnh để chính tệp này không tự khớp. */
    private val token = "scratch" + "pad"

    /** Dạng ĐƯỜNG DẪN của chữ ấy (`/` hoặc `\` liền sau). */
    private val pathLike = Regex(token + """[/\\]""", RegexOption.IGNORE_CASE)

    private val textExt = setOf(
        "kt", "kts", "java", "xml", "py", "sh", "bat", "tsv", "txt", "json", "md", "html", "htm", "js", "css", "yml", "yaml",
        "toml", "gradle", "properties", "c", "h", "cpp", "csv", "svg",
    )

    private val repo: Path = listOf(Paths.get(""), Paths.get(".."))
        .map { it.toAbsolutePath().normalize() }
        .first { Files.isRegularFile(it.resolve("settings.gradle.kts")) }

    /** Mô-đun Gradle khai trong `settings.gradle.kts` (`include(":tên")`). */
    private fun modules(): List<String> =
        Regex("""include\(\s*"([^"]+)"\s*\)""").findAll(repo.resolve("settings.gradle.kts").readText())
            .map { it.groupValues[1].trimStart(':').replace(':', '/') }.toList()

    /** Tệp chữ dưới [rel] mà `git add -A` sẽ đưa vào (tracked + untracked không bị chặn); không có git ⇒ duyệt thư mục. */
    private fun files(rel: String): List<Path> {
        val git = runCatching {
            val p = ProcessBuilder("git", "ls-files", "-co", "--exclude-standard", "-z", "--", rel)
                .directory(repo.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
            check(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0)
            out.split('\u0000').filter { it.isNotEmpty() }.map { repo.resolve(it) }
        }.getOrNull()
        val all = git ?: Files.walk(repo.resolve(rel)).use { s ->
            s.filter { f -> repo.relativize(f).none { it.name in SKIP_DIRS } }.toList()
        }
        return all.filter { Files.isRegularFile(it) && it.extension.lowercase() in textExt && it.fileSize() < MAX_BYTES }
    }

    private fun rel(p: Path): String = repo.relativize(p).toString().replace('\\', '/')

    @Test
    fun `ma test script khong ghi duong dan thu muc nhap ngoai repo`() {
        val mods = modules()
        assertTrue(mods.size >= 5 && mods.containsAll(listOf("app", "core", "car-integration", "vehicle-contracts", "offcar-planner")),
            "đọc thiếu mô-đun ở settings.gradle.kts: $mods")
        val roots = mods.map { "$it/src" } + EXTRA_ROOTS
        roots.forEach { assertTrue(repo.resolve(it).isDirectory(), "gốc quét '$it' không tồn tại — mô-đun đổi tên? sửa bài này") }
        val hits = roots.flatMap(::files).filter { it.readText().contains(token, ignoreCase = true) }.map(::rel)
        assertTrue(hits.isEmpty(), "chú thích/mã ghi đường dẫn thư mục nháp ngoài repo — viết 'công cụ ngoài repo': $hits")
    }

    @Test
    fun `tai lieu khong them duong dan thu muc nhap moi`() {
        val counts = files("docs").associate { rel(it) to pathLike.findAll(it.readText()).count() }.filterValues { it > 0 }
        val grown = counts.filter { (f, n) -> n > (DOCS_LEGACY[f] ?: 0) }
        assertTrue(grown.isEmpty(),
            "tài liệu thêm đường dẫn thư mục nháp ngoài repo (chép bằng chứng vào docs/diagnostics rồi trỏ tới đó): $grown")
    }

    private companion object {
        /** Tệp dữ liệu lớn (bảng giọng, mô hình dạng chữ) không phải chỗ viết chú thích. */
        const val MAX_BYTES = 4L * 1024 * 1024

        val SKIP_DIRS = setOf("build", ".git", ".gradle", ".idea", "node_modules")

        /** Cây có mã/script ngoài mô-đun Gradle. */
        val EXTRA_ROOTS = listOf("scripts", "voice", "tools", "hal-helper")

        /**
         * [ĐO 2026-10-04, `git ls-files -co --exclude-standard -- docs`] số lần dạng đường dẫn trong các tệp tài liệu CŨ — chỉ được
         * giảm. Đề xuất backlog: chép bằng chứng vào `docs/diagnostics/` rồi xoá dòng tương ứng ở đây.
         */
        val DOCS_LEGACY = mapOf(
            "docs/_handoff/closeout-plan-2026-09-29.md" to 1,
            "docs/_handoff/session-2026-09-17-visual-voice-touch.md" to 5,
            "docs/_handoff/session-2026-09-27-closing.md" to 1,
            "docs/archive/clusternav2-backlog-inherited-2026-09-10.md" to 1,
            "docs/archive/review/HANDOFF-2026-07-23.md" to 8,
            "docs/archive/review/README-v0.56.md" to 1,
            "docs/catalog/features.json" to 1,
            "docs/diagnostics/offcar-2026-09-27/camera-cluster-band.md" to 14,
            "docs/diagnostics/offcar-2026-09-27/seat-level-glyphs-mirror.md" to 1,
            "docs/diagnostics/ram-audit-2026-09-25.md" to 3,
            "docs/diagnostics/voice-bench-ship-vs-ft-2026-09-17.md" to 4,
            "docs/kachi-feature-catalog.html" to 1,
            "docs/PROJECT-BACKLOG.md" to 3,
            "docs/specs/kachi-276-closing.html" to 2,
            "docs/specs/kachi-286-field-fixes.html" to 2,
            "docs/specs/kachi-hal187-cast-remediation.html" to 1,
            "docs/specs/kachi-launcher-shortcuts-autostart.html" to 1,
            "docs/specs/kachi-ready-at-home.html" to 6,
            "docs/specs/kachi-voice-clone.html" to 2,
            "docs/specs/kachi-voice-fast-natural.html" to 3,
        )
    }
}
