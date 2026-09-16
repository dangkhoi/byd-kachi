package com.byd.clusternav.launcher

/**
 * ═══ CHỈ MỤC `id → bản khai` CHO CÁC DANH MỤC — dựng MỘT lần, tra bằng băm ═══════════════════════════════════
 *
 * [SOÁT 2026-09-16 · P3] `ControlRegistry.byId` (≈54 nút) và `TelemetryRegistry.byId` (≈106 datum) trước đây là
 * `ALL.firstOrNull { it.id == id }` — **quét tuyến tính**, mà chúng nằm trên đường đọc nóng nhất của launcher:
 * `HalBindingTable.readRaw` hỏi 2–4 lượt cho **mỗi** datum (`specOf`, rồi `featureDeviceFor` hỏi lại **cả hai**
 * danh mục), `CapabilityCatalog.kindOf`/`pick` xâu thêm 4–5 lượt nữa cho mỗi capability. Ở nhịp [ĐO xe, bản
 * 1.67 H1] ~510 lượt đọc/phút, cộng phần tra của từng ô trên màn, đó là số phép so chuỗi **năm chữ số mỗi phút**
 * cho một việc lẽ ra là một lượt tra băm.
 *
 * ## Ba ràng buộc, và vì sao chỉ mục nằm ở tệp RIÊNG chứ không nằm trong từng danh mục
 *  1. **`ALL` và THỨ TỰ KHAI không được đụng tới.** Nhiều bài kiểm neo vào đúng thứ tự ấy (và
 *     `defaultEnabledIds()` đọc theo thứ tự) — bản vá này **chỉ** đổi phép TRA, không đổi dữ liệu.
 *  2. **`by lazy`, không phải khởi tạo sớm.** Hai danh mục là `object` với `ALL` khai bằng literal; dựng map ngay
 *     trong thân chúng là buộc thứ tự khởi tạo của hai lớp vào nhau. `lazy` ⇒ map chỉ dựng ở lượt `byId` đầu
 *     tiên, lúc `ALL` chắc chắn đã xong.
 *  3. **Một chỗ khai, hai danh mục dùng** (CLAUDE.md §4.1 DRY): cùng một mẫu lặp ở hai tệp là hai chỗ để lệch
 *     nhau ở lần vá sau — và cả hai tệp ấy đều đang sát trần 500 dòng, nên chỗ đúng cho nó là ở đây.
 *
 * ⚠ `associateBy` giữ **phần tử SAU** khi id trùng. Điều đó **không** làm hành vi khác `firstOrNull` (vốn giữ
 * phần tử ĐẦU) vì id trong hai danh mục là duy nhất — và đó không phải niềm tin suông: `ControlRegistryTest` /
 * `TelemetryRegistryTest` đã khoá "không có id trùng". Ngày nào cái khoá đó đỏ thì nó đỏ **trước** chỗ này.
 */
internal object RegistryIndex {

    /** `ControlRegistry.ALL` theo id. */
    val CONTROLS: Map<String, ControlDef> by lazy { ControlRegistry.ALL.associateBy { it.id } }

    /** `TelemetryRegistry.ALL` theo id. */
    val TELEMETRY: Map<String, TelemetrySpec> by lazy { TelemetryRegistry.ALL.associateBy { it.id } }
}
