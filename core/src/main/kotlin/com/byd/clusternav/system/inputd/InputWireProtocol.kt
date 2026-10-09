package com.byd.clusternav.system.inputd

import java.nio.ByteBuffer

/** Một sự kiện chạm ĐÃ NÉN gửi cho input-daemon: display đích + action + toạ độ (đã map sang hệ display). */
data class TouchFrame(val displayId: Int, val action: Int, val x: Int, val y: Int)

/**
 * Giao thức dây (wire) CỐ ĐỊNH-ĐỘ-DÀI cho input-daemon (scrcpy-style). PURE JVM (:core) → round-trip test
 * off-device, không phụ thuộc android.
 *
 * Mỗi khung = [FRAME_BYTES] byte big-endian:
 *   `[version:1][type:1][displayId:4][action:4][x:4][y:4]`
 *
 * Khung CỐ ĐỊNH độ dài ⇒ daemon đọc đúng [FRAME_BYTES] byte mỗi lần (`readFully`) — tự đồng bộ, không cần
 * delimiter. [decode] trả `null` cho khung sai độ dài / sai [VERSION] / sai [TYPE_TOUCH] (rác) ⇒ daemon BỎ QUA
 * khung đó thay vì sập.
 */
object InputWireProtocol {
    /** Phiên bản giao thức (nằm ở byte đầu). */
    const val VERSION: Int = 1

    /** Loại khung: sự kiện chạm. */
    const val TYPE_TOUCH: Int = 1

    /** version(1) + type(1) + 4×int32 (displayId/action/x/y) = 18 byte. */
    const val FRAME_BYTES: Int = 1 + 1 + 4 * 4

    /** Nén [frame] thành đúng [FRAME_BYTES] byte big-endian. */
    fun encode(frame: TouchFrame): ByteArray =
        ByteBuffer.allocate(FRAME_BYTES).apply {
            put(VERSION.toByte())
            put(TYPE_TOUCH.toByte())
            putInt(frame.displayId)
            putInt(frame.action)
            putInt(frame.x)
            putInt(frame.y)
        }.array()

    /**
     * 2.98 · R18 — loại khung: ĐIỀU KHIỂN. Đầu cố định [FRAME_BYTES] byte `[version:1][type=2:1][len:4][0 × 12]`, theo sau là
     * đúng `len` byte thân UTF-8 (≤ [MAX_CONTROL_BYTES]) — bảng "app thoát ô ⇒ về lại màn ảo ô" (`EscapeReturnWire`). Khung chạm
     * giữ nguyên byte. Daemon CŨ không hiểu khung này (đọc thân như khung rác) ⇒ Kachi mới chỉ nói với daemon mới: dải cổng
     * đổi theo [WIRE_GENERATION] (`InputDaemonLaunch.portFor`).
     */
    const val TYPE_CONTROL: Int = 2

    /** Trần thân khung điều khiển — bảng ≤ 16 mục gói ≈ vài trăm byte. */
    const val MAX_CONTROL_BYTES: Int = 4096

    /** Thế hệ dây: 1 = chỉ chạm (≤ 2.97) · 2 = + khung điều khiển + dòng báo cáo chiều ngược (2.98 · R18). */
    const val WIRE_GENERATION: Int = 2

    /** Khung điều khiển = đầu [FRAME_BYTES] byte + [payload]. Thân quá trần ⇒ ném (lỗi lập trình phía Kachi, không phải dữ liệu lạ). */
    fun controlFrame(payload: ByteArray): ByteArray {
        require(payload.size <= MAX_CONTROL_BYTES) { "control payload ${payload.size} > $MAX_CONTROL_BYTES" }
        return ByteBuffer.allocate(FRAME_BYTES + payload.size).apply {
            put(VERSION.toByte())
            put(TYPE_CONTROL.toByte())
            putInt(payload.size)
            put(ByteArray(FRAME_BYTES - 6))
            put(payload)
        }.array()
    }

    /** Độ dài thân nếu [header] là đầu khung điều khiển hợp lệ; khác ⇒ `null` (khung chạm / rác). */
    fun controlLength(header: ByteArray): Int? {
        if (header.size != FRAME_BYTES) return null
        val buf = ByteBuffer.wrap(header)
        val version = buf.get().toInt() and 0xFF
        val type = buf.get().toInt() and 0xFF
        if (version != VERSION || type != TYPE_CONTROL) return null
        return buf.int.takeIf { it in 0..MAX_CONTROL_BYTES }
    }

    /** Giải mã một khung [FRAME_BYTES] byte. Trả `null` nếu độ dài/version/type sai (khung rác) → caller bỏ qua. */
    fun decode(bytes: ByteArray): TouchFrame? {
        if (bytes.size != FRAME_BYTES) return null
        val buf = ByteBuffer.wrap(bytes)
        val version = buf.get().toInt() and 0xFF
        val type = buf.get().toInt() and 0xFF
        if (version != VERSION || type != TYPE_TOUCH) return null
        val displayId = buf.int
        val action = buf.int
        val x = buf.int
        val y = buf.int
        return TouchFrame(displayId, action, x, y)
    }
}
