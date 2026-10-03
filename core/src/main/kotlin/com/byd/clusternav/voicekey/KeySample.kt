package com.byd.clusternav.voicekey

/**
 * ═══ L7 · KEY-SOURCE-SPLIT tầng 1 — CHỮ KÝ ĐẦY ĐỦ của một lần bấm phím vật lý ═══════════════════════════════════
 *
 * Spec addendum L7 (`kachi-287-look-and-keys` › KEY-SOURCE-SPLIT). Owner 03/10: xe có HAI nút âm lượng (vô-lăng và núm
 * xoay trên yên ngựa) mà Kachi học cả hai thành cùng mã 291/292. Tầng 1 **chỉ đo** — không đổi cách gán/khớp phím.
 *
 * ## Vì sao là giá trị THUẦN chép ra từ `KeyEvent`, không giữ chính `KeyEvent`
 * [ĐO AOSP r47 `AccessibilityService.java:1873-1890`] sau khi `onKeyEvent` trả về, framework gọi `event.recycle()` —
 * giữ tham chiếu sang luồng khác là đọc một đối tượng đã bị trả về pool (giá trị của phím KHÁC). Vì thế tầng app chép
 * đúng các field nguyên thuỷ ở đây, ngay trên luồng nhận phím, rồi mới đưa đi.
 *
 * Mọi field là getter thuần của `KeyEvent` [ĐO AOSP r47 `KeyEvent.java` — `obtain(other)` :1640-1655 chép đủ
 * `mDeviceId/mSource/mAction/mKeyCode/mRepeatCount/mMetaState/mScanCode/mFlags/mDownTime/mEventTime`]: không I/O,
 * không binder ⇒ an toàn trong hạn 500 ms của `KeyEventDispatcher` (`KeyEventDispatcher.java:51`).
 *
 * @property downTime / eventTime mốc `SystemClock.uptimeMillis` (cùng gốc với đồng hồ tầng app dùng đo độ trễ).
 * @property device thông tin `InputDevice` — **không** tra trên luồng nhận phím (lần đầu có thể là một lượt binder,
 *   `InputManager.getInputDevice` r47 :250-271); luồng đo điền sau ([KeySourceJournal.complete]).
 */
data class KeySample(
    val keyCode: Int,
    val action: Int,
    val downTime: Long,
    val eventTime: Long,
    val scanCode: Int,
    val deviceId: Int,
    val source: Int,
    val flags: Int,
    val repeatCount: Int,
    val metaState: Int = 0,
)

/**
 * Thông tin thiết bị nhập của một [KeySample] (`InputDevice` r47: `getName` :633 · `getDescriptor` :587 ·
 * `isVirtual` :603 · `getVendorId` :547 · `getProductId` :561 — không cần quyền).
 *
 * `descriptor` là khoá BỀN của thiết bị (không đổi qua khởi động lại); `deviceId` thì không — tầng 3 (nếu có) chỉ được
 * dùng descriptor/tên, không bao giờ dùng id.
 */
data class KeyDeviceInfo(
    val name: String,
    val descriptor: String,
    val isVirtual: Boolean,
    val vendorId: Int,
    val productId: Int,
)
