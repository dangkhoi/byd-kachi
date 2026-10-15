package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.zip.ZipFile

/**
 * 3.1.6 · HAL-EVENTS — bài canh NỐI DÂY (CLAUDE.md §8: "compile xanh ≠ chạy"). Logic ở `:core` `HalEventsTest`; ở đây khoá rằng:
 *  - helper đăng ký listener CHUNG qua `AbsBYDAutoDevice.registerListener(IBYDAutoListener, int[])`, cài ĐỦ ba phương thức của giao
 *    diện thật, lọc id theo `BYDAutoDeviceFeaturesMap`, đọc dòng `sub` của client, và jar đóng gói là bản có giao thức này;
 *  - app đọc qua `EventCachedGateway`, nhịp thân xe hỏi `covered` (chỉ còn là đường lùi), hub nối sink + gửi `sub`, canh ghế xin
 *    sự kiện và hỏi dấu vân tay theo luồng sự kiện (không còn hằng UNOBSERVABLE cứng).
 * Bài đọc thẳng nguồn: xoá/đổi đúng chuỗi được khoá ⇒ ĐỎ theo cấu trúc. Thử ĐỎ có chạy thật nằm ở `:core` `HalEventsTest`.
 */
class HalEventsContractTest {

    /** Soát Pass 6: phần HAL-EVENTS tách sang `KachiHalEvents.java` (trần 500 dòng/tệp) — hợp đồng khoá trên CẢ HAI tệp. */
    private val helper = SourceRoots.text("../hal-helper/KachiHalMain.java") + SourceRoots.text("../hal-helper/KachiHalEvents.java")

    @Test
    fun `helper dang ky listener chung cho moi device`() {
        assertTrue(helper.contains("getMethod(\"registerListener\", IBYDAutoListener.class, int[].class)"))
        assertTrue(helper.contains("getMethod(\"unregisterListener\", IBYDAutoListener.class)"))
        assertTrue(helper.contains("implements IBYDAutoListener"))
        listOf("public void onDataChanged(IBYDAutoEvent e)", "public void onDataEventChanged(int featureId, BYDAutoEventValue value)",
            "public void onError(int code, String message)").forEach { assertTrue(helper.contains(it), it) }
        assertTrue(helper.contains("getFeatureIdsFromDevice"), "lọc id theo bảng device trước khi đăng ký (checkDeviceFeatures bỏ CẢ lượt)")
        assertTrue(Regex("""sendSnapshot\(os\);\s*KachiHalEvents\.startReader\(s, os\);""").containsMatchIn(helper), "mỗi client có luồng đọc `sub`")
        // Soát Pass 6 [P2]: chống trùng so GIÁ TRỊ (`LAST_VAL`), không so dòng đã dựng (so dòng với "v|d" không bao giờ trùng ⇒ phát mọi sự kiện).
        assertTrue(helper.contains("String prev = LAST_VAL.get(key);") && helper.contains("LAST_VAL.put(key, val);"), "chống trùng theo giá trị")
        assertTrue(helper.contains("h.post(") && helper.contains("MAIN = new Handler(Looper.getMainLooper())"), "đăng ký chạy trên main looper")
        assertFalse(helper.contains("onDataChanged(IBYDAutoEvent e) {\n            }"), "onDataChanged phải làm việc")
    }

    @Test
    fun `jar dong goi la ban co giao thuc su kien`() {
        val jar = SourceRoots.path("src/main/assets/kachi_hal.jar").toFile()
        val dex = ZipFile(jar).use { z -> z.getInputStream(z.getEntry("classes.dex")).readBytes() }
        val text = String(dex, Charsets.ISO_8859_1)
        listOf("hal.hello", "hal.reg", "hal.ev", "KachiHalEvents\$EventListener").forEach { assertTrue(text.contains(it), "dex thiếu $it — chạy scripts/build-hal-helper.sh") }
    }

    @Test
    fun `app noi day su kien, nhip than xe chi la duong lui`() {
        val app = SourceRoots.codeOf("src/main/java/com/byd/clusternav/AppContainer.kt")
        assertTrue(app.contains("EventCachedGateway(halGateway, halEvents)"), "đọc đi qua bộ nhớ sự kiện")
        assertTrue(app.contains("covered = halEvents::covers"))
        assertTrue(app.contains("h.onHalEvents({ carEvents.onMessage(it) }, { carEvents.onLinkDown() })"))
        assertTrue(app.contains("onDatums = { ids -> carStatusRepository.onEvents(ids) }"))
        assertTrue(app.contains("subscribe = { line -> halSignal?.subscribe(line) }"))
        val adapter = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/CarDataAdapter.kt")
        val body = SourceRoots.body(adapter, "override fun bodyNeeded()")
        assertTrue(body.contains("covered("), "nhịp 3 s phải hỏi `covered` (sự kiện phủ ⇒ nghỉ)")
        val client = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/camera/HalSignalClient.kt")
        assertTrue(client.contains("HalEventWire.parse(line)") && client.contains("send(subscription)"))
    }

    @Test
    fun `canh ghe xin su kien va hoi dau van tay theo luong su kien`() {
        val w = SourceRoots.codeOf("src/main/java/com/byd/clusternav/comfort/SeatAutoOffWatch.kt")
        assertTrue(w.contains("carEvents.seatWatch(datums)") && w.contains("carEvents.seatWatch(null)"))
        assertTrue(w.contains("signature(container, seat)"))
        assertTrue(w.contains("SeatAutoOffGuard.signatureOf("))
        assertFalse(w.contains("SIGNATURE = SeatAutoOffGuard.Signature.UNOBSERVABLE"))
    }
}
