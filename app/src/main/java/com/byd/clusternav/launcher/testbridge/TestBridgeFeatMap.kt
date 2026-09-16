package com.byd.clusternav.launcher.testbridge

import android.content.Context
import android.util.Log
import com.byd.clusternav.launcher.BydFeatureIds
import com.byd.clusternav.launcher.KachiLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ═══ T-BRIDGE · LỆNH `featmap` — ĐỔ **BẢNG FEATURE-ID THẬT CỦA XE** RA JSON ═══════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-fast-natural.html` **R11(c)**.
 *
 * ## Vì sao một lệnh riêng, khi đã có `sweep`
 * `sweep` trả lời *"mục nào đọc được"*. Nó **không** trả lời được câu mà lượt xe 09-16 để lại: *"trên chiếc xe
 * NÀY, hằng `ENGINE_POWER` mang số mấy, và số ấy thuộc device nào"*. Không có câu trả lời đó thì mỗi dòng
 * `bindingKey` còn ngờ vực lại tốn **một lượt lên xe** để thử — mà xe thì không phải lúc nào cũng có.
 *
 * [ĐO nguồn fw-dl3 2026-09-16] cả hai bảng đều đọc được bằng reflection:
 *  • `BYDAutoFeatureIds` — hằng gán trong `static {}` theo `isCanFD`/`isToyota`, nên **chỉ chính chiếc xe** biết
 *    số thật (bản decompile chỉ cho biết *các khả năng*);
 *  • `BYDAutoDeviceFeaturesMap.getFeatureIdsFromDevice(int)` — tập id của từng device, tức lời giải trực tiếp cho
 *    *"You have no permission to use the feature 0x… with this device N"*.
 *
 * ⇒ Một lệnh, một tệp JSON trên thẻ, và **mọi** dòng bind còn NEEDS-ONCAR tra được off-car sau đó.
 *
 * ## Chỉ ĐỌC — không cần confirm
 * Không gọi `set`/`get` HAL nào; chỉ đọc hằng tĩnh và một `HashMap` của framework. Không đổi state xe.
 *
 * ## Luồng nền
 * Đổ ~10k trường qua reflection là việc **CHẶN** hàng trăm ms — không chạy trên luồng broadcast, cùng lẽ `sweep`.
 * Không cần màn chính (không đọc `HomeUiState`).
 */
internal object TestBridgeFeatMap {

    /** Không ghi được tệp ra thẻ (vắng external) — vẫn trả số đếm trong lời đáp. */
    const val NOTE_NO_FILE = "khong ghi duoc tep (vang the) — chi co so dem"

    /** Framework BYD không có trên máy này (máy ảo / máy soạn thảo) — lời đáp nói thẳng thay vì trả bảng rỗng. */
    const val NOTE_OFF_CAR = "khong co BYDAutoFeatureIds tren may nay (off-car)"

    fun run(app: Context, reply: TestBridgeReply) {
        Thread({
            runCatching { dump(app, reply) }
                .onFailure { t ->
                    Log.w(TestBridgeReply.TAG, "featmap nem: ${t.javaClass.simpleName}", t)
                    reply.fail(KachiTestBridge.ERR_THREW, "exception" to t.javaClass.simpleName)
                }
        }, "KachiTestFeatMap").start()
    }

    private fun dump(app: Context, reply: TestBridgeReply) {
        val (names, devices) = BydFeatureIds.dump()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val json = TestBridgeJson.obj(
            "stamp" to stamp,
            "names" to TestBridgeJson.Raw(
                TestBridgeJson.arr(
                    names.entries.sortedBy { it.key }.map {
                        TestBridgeJson.Raw(TestBridgeJson.obj("name" to it.key, "id" to it.value))
                    },
                ),
            ),
            "devices" to TestBridgeJson.Raw(
                TestBridgeJson.arr(
                    devices.entries.sortedBy { it.key }.map {
                        TestBridgeJson.Raw(
                            TestBridgeJson.obj(
                                "type" to it.key,
                                "count" to it.value.size,
                                // Danh sách id **đầy đủ**, không cắt: cả điểm của tệp này là tra ngược được
                                // *"id X thuộc device nào"* mà không phải lên xe lần nữa.
                                "ids" to TestBridgeJson.Raw(TestBridgeJson.arr(it.value)),
                            ),
                        )
                    },
                ),
            ),
        )
        val file = writeToCard(app, "featmap-$stamp.json", json)
        reply.ok(
            listOf(
                "file" to (file?.absolutePath ?: ""),
                "names" to names.size,
                "devices" to devices.size,
                "note" to when {
                    names.isEmpty() -> NOTE_OFF_CAR
                    file == null -> NOTE_NO_FILE
                    else -> ""
                },
                "pull" to KachiLog.pullCommand(app),
            ),
        )
    }

    /** Ghi ra thư mục log trên thẻ (cùng chỗ `sweep-*.json`) — `null` khi vắng thẻ/ghi hỏng. */
    private fun writeToCard(app: Context, name: String, text: String): File? = runCatching {
        val f = File(KachiLog.dir(app) ?: return null, name)
        f.writeText(text)
        f
    }.onFailure { Log.w(TestBridgeReply.TAG, "featmap ghi tep hong: ${it.message}") }.getOrNull()
}
