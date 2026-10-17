package com.byd.clusternav.launcher.perf

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1.14 `PERF-SL6-3113` — SL6 báo giật mà không có log; CLAUDE.md §11: dữ liệu cần để chốt phải nằm trên màn Chẩn đoán (chụp màn
 * hình là đủ). Khoá: màn Chẩn đoán in khối hiệu năng, khối đó mang đúng các trường phân biệt nguồn giật (đời xe · máy · Hey Kachi ·
 * dòng KachiPerf có chi phí xe 3D), và chi phí xe 3D thật sự được đếm ở bộ vẽ.
 */
class PerfDiagWiringContractTest {
    private fun src(n: String) = Files.readString(SourceRoots.path("src/main/java/com/byd/clusternav/$n"))

    @Test
    fun `man Chan doan in khoi hieu nang voi du truong phan biet`() {
        assertTrue(src("modules/clustercast/DiagActivity.kt").contains("PerfDiag.describe(applicationContext)"), "Diag phải in khối hiệu năng")
        val d = src("launcher/perf/PerfDiag.kt")
        for (k in listOf("car.type=", "availableProcessors()", "totalMem", "com.byd.avc", "Prefs.wakeEnabled(", "KachiPerf.lastLine")) {
            assertTrue(d.contains(k), "khối hiệu năng thiếu $k")
        }
    }

    @Test
    fun `bo ve xe 3D dem luot dung va ms luong chinh`() {
        val r = src("launcher/Car3dRenderer.kt")
        // 3.1.14 R3: gltfio dời sang car3d-gl ⇒ lượt dựng đếm ở việc nạp trên luồng đó; ms luồng chính = tay cầm + phần dựng neo/chấm của widget.
        assertTrue(r.substringAfter("fun load(glb: ByteBuffer").substringBefore("\n    }\n").contains("Counter.CAR3D_BUILD"), "load phải đếm lượt dựng")
        assertTrue(r.substringAfter("fun create(").substringBefore("\n        }\n").contains("mainMs(t0)"), "create phải tính ms luồng chính")
        assertTrue(src("launcher/Car3dWidgetView.kt").substringAfter("private fun onAsset(").substringBefore("\n    }\n").contains("Car3dRenderer.mainMs(tm)"),
            "phần luồng chính của lượt dựng (neo/chấm) phải vào KachiPerf")
        assertTrue(r.contains("Counter.CAR3D_BUILD") && r.contains("Counter.CAR3D_MAIN_MS"), "đếm vào KachiPerf")
    }
}
