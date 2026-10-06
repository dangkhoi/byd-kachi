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

    /**
     * 2.93 · DOCS-QA-REL-PATHS (spec `kachi-293-wave2a.html` §4.6) — đường dẫn TƯƠNG ĐỐI vào thư mục QA của một phiên (dạng
     * l5/<ảnh>.png · qa2/a/<nhật ký>.log · p3/e2e-L4/<lượt>, đặt trong dấu huyền) cũng chết theo phiên như thư mục nháp: người
     * đọc sau không lần lại được, và nó trông như một đường dẫn trong repo. [ĐO grep 06/10] 46 lần ở 34 tệp mã/test ⇒ thay
     * bằng TÊN tệp + "bằng chứng phiên, ngoài repo". Mẫu nhận đúng dạng đã gặp (thư mục phiên l<số> · p<số> · qa<số> đứng đầu
     * chuỗi trong dấu huyền); đường dẫn mã nguồn RE (b1/RunnableC0170d) và tên fixture trong repo (l4-hidden-*) không khớp.
     */
    @Test
    fun `ma test khong ghi duong dan tuong doi vao thu muc QA cua phien`() {
        val roots = modules().map { "$it/src" } + EXTRA_ROOTS
        val hits = roots.flatMap(::files).flatMap { f ->
            QA_SESSION_PATH.findAll(f.readText()).map { "${rel(f)}: ${it.value}" }.toList()
        }
        assertTrue(hits.isEmpty(), "đường dẫn thư mục QA của phiên (chết theo phiên) — ghi tên tệp + 'bằng chứng phiên, ngoài repo': $hits")
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

        /** Chuỗi trong dấu huyền mở đầu bằng thư mục QA của phiên (l5/ · p3/ · qa2/ …) — bài `ma test khong ghi duong dan tuong doi…`. */
        val QA_SESSION_PATH = Regex("""`(?:qa\d+|[lp]\d)/[^`\s]*""")

        /** Cây có mã/script ngoài mô-đun Gradle. */
        val EXTRA_ROOTS = listOf("scripts", "voice", "tools", "hal-helper")

        /**
         * [ĐO 2026-10-04, `git ls-files -co --exclude-standard -- docs`] số lần dạng đường dẫn trong các tệp tài liệu CŨ — chỉ được
         * giảm. Đề xuất backlog: chép bằng chứng vào `docs/diagnostics/` rồi xoá dòng tương ứng ở đây.
         *
         * 2.93 · DOCS-SCRATCH-PATHS [ĐO grep 06/10]: 19 tệp đã thay đường dẫn bằng tên tệp + "bằng chứng phiên, ngoài repo" (thư
         * mục nháp chết theo phiên — không còn gì để chép vào repo) ⇒ rút khỏi bảng; tệp đã rút mà mọc lại một lần ⇒ đỏ.
         * 2.93 wave 2A · DOCS-QA-REL-PATHS: ba lần cuối ở `PROJECT-BACKLOG.md` đã gỡ ⇒ bảng RỖNG — mọi tệp tài liệu nay ở mức 0.
         */
        val DOCS_LEGACY: Map<String, Int> = emptyMap()
    }
}
