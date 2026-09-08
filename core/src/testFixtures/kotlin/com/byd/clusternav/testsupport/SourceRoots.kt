package com.byd.clusternav.testsupport

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Giải đường dẫn source cho các test quét source, không phụ thuộc module nào đang chạy.
 *
 * Đặt ở namespace trung lập, không nằm trong feature nào. Trước 2026-07-27 nó sống trong package của
 * Cluster Cast, nên test của Navigation phải import một lớp mang tên Cast chỉ để đọc file — chính là kiểu
 * "gọi ngang feature" mà quy tắc Q3 cấm, và trớ trêu là nó xảy ra ngay trong test đang canh quy tắc đó.
 *
 * Từ 2026-07-27 code Cast nằm ở hai module (:core thuần và :app cho phần biết-thiết-bị), và Gradle đặt
 * working directory theo module. Trước đó mọi test giả định gốc là app/, nên vừa dời file là chúng chết
 * hàng loạt mà không phải vì logic sai. Helper này thử mọi gốc hợp lý, kể cả chuyển java/ ↔ kotlin/.
 */
object SourceRoots {

    fun path(relative: String): Path {
        val kotlin = relative.replace("main/java/", "main/kotlin/").replace("test/java/", "test/kotlin/")
        val candidates = listOf(
            "app/$relative", "../app/$relative", relative,
            "core/$kotlin", "../core/$kotlin",
            "car-integration/$kotlin", "../car-integration/$kotlin",
            "app/$kotlin", "../app/$kotlin",
        ).map(Paths::get)
        return candidates.firstOrNull { Files.exists(it) }
            ?: error("không tìm thấy $relative; đã thử: ${candidates.joinToString()}")
    }

    fun text(relative: String): String = path(relative).toFile().readText()

    fun exists(relative: String): Boolean =
        runCatching { path(relative) }.isSuccess

    /** Gốc chứa source Kotlin của cả hai module, dùng cho test cần quét toàn bộ cây. */
    fun moduleSourceRoots(): List<Path> = listOf(
        "app/src/main/java", "../app/src/main/java",
        "core/src/main/kotlin", "../core/src/main/kotlin",
        "car-integration/src/main/kotlin", "../car-integration/src/main/kotlin",
    ).map(Paths::get).filter(Files::exists)
}
