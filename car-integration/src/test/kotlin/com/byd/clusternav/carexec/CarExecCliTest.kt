package com.byd.clusternav.carexec

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Runner là lời hứa cốt lõi của Track 1: đánh giá capability trên xe mà không cần APK. Test này giữ hai
 * tính chất khiến lời hứa đó có giá trị — nó không cần thiết bị để trả lời câu hỏi sai, và nó không im
 * lặng khi thất bại.
 */
class CarExecCliTest {

    @Test
    fun `help chay duoc ma khong can thiet bi`() {
        val out = capture { CarExecCli.main(arrayOf("--help")) }
        assertTrue(out.contains("không cần APK"), out)
        assertTrue(out.contains("steps"), out)
    }

    @Test
    fun `lenh khong biet duoc bao ro rang chu khong im lang`() {
        val out = capture { CarExecCli.main(arrayOf("khong-ton-tai")) }
        assertTrue(out.contains("lệnh không biết"), out)
    }

    @Test
    fun `thieu khoa adb thi noi ro thay vi nem stack trace`() {
        val empty = Files.createTempDirectory("carexec-keys").toString()
        val out = capture { CarExecCli.main(arrayOf("run", "cast-app", "--keys", empty)) }
        assertTrue(out.contains("không thấy cặp khoá adb"), out)
    }

    @Test
    fun `runner khong duoc phu thuoc Android`() {
        val source = listOf(
            "car-integration/src/main/kotlin/com/byd/clusternav/carexec/CarExecCli.kt",
            "src/main/kotlin/com/byd/clusternav/carexec/CarExecCli.kt",
            "../car-integration/src/main/kotlin/com/byd/clusternav/carexec/CarExecCli.kt",
        ).map(Paths::get).first(Files::exists).toFile().readText()
        assertFalse(source.contains("import android."), "runner phải chạy trên JVM thuần")
        // Chỉ đọc: chưa có capability gây biến đổi nào cho tới khi Q1 được trả lời.
        assertFalse(source.contains("requestStop"), "runner hiện tại phải là chỉ-đọc")
        assertFalse(source.contains("runManualIntent"), "runner hiện tại phải là chỉ-đọc")
    }

    private fun capture(block: () -> Unit): String {
        val buffer = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(buffer, true, "UTF-8"))
        try {
            block()
        } finally {
            System.setOut(original)
        }
        return buffer.toString("UTF-8")
    }
}
