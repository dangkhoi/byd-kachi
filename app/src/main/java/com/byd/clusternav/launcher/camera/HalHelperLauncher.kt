package com.byd.clusternav.launcher.camera

import android.content.Context
import android.util.Base64
import android.util.Log
import com.byd.clusternav.system.ShellTransport
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * ═══ ĐƯA HELPER HAL LÊN XE VÀ CHẠY NÓ DƯỚI uid SHELL ═════════════════════════════════════════════════════════
 *
 * Việc của lớp này: bảo đảm `127.0.0.1:19322` đang có người phục vụ. Ba bước, bỏ qua được bước nào thì bỏ:
 *  1. **Dò cổng** (400 ms) — đã mở ⇒ xong, KHÔNG chạm shell (đường nóng mỗi lần bật camera đi qua đây).
 *  2. **Đẩy jar** `assets/kachi_hal.jar` → `/data/local/tmp/kachi_hal.jar` nếu cỡ ở xe khác cỡ ở máy.
 *  3. **Chạy** `app_process` qua kênh dadb uid-2000, rồi đợi cổng mở.
 *
 * ── VÌ SAO ĐẨY BẰNG base64 QUA SHELL, KHÔNG PHẢI `Dadb.push` ─────────────────────────────────────────────────
 * `Dadb.push` sẽ cần MỘT transport thứ hai mở ra thiết bị. Kiến trúc dự án nói mọi lệnh cửa sổ/shell của launcher
 * đi qua **một chủ duy nhất** [ShellTransport] (xem KDoc lớp đó: trước B1 có 13 chỗ tự `Dadb.create` và đó là
 * nguồn của hỏng-ngầm). Jar chỉ ~5 KB ⇒ base64 chia 2 khúc = ~6 lệnh shell, quá rẻ để phải mở đường riêng.
 * Đây cũng đúng cách **kinex** làm ([ĐO] `Y0/F.java`: `echo '<b64>' >> serverBase64` rồi `base64 -d`).
 *
 * ⚠ **Khúc base64 phải chia theo bội số của 3.** Mỗi khúc được encode RIÊNG rồi nối lại ở phía xe; nếu độ dài
 *   khúc không chia hết cho 3 thì base64 chèn `=` vào GIỮA dòng và `base64 -d` giải ra file hỏng — im lặng.
 *   [CHUNK_RAW] = 3042 = 3 × 1014 (đúng con số kinex chọn, cùng lý do).
 *
 * ⚠ **CHẶN LUỒNG.** Mọi thứ ở đây là I/O mạng + shell nối tiếp. Gọi [ensure] trên thread NỀN. Nó không bao giờ
 *   ném (mọi bước `runCatching`), off-car chỉ trả `false`.
 *
 * ⚠ **KHÔNG dùng `exec`** cho lệnh chạy helper, dù [ĐO] kinex dùng: kinex giữ một phiên shell riêng sống mãi,
 *   còn ta đi qua [ShellTransport] — một hàng đợi MỘT worker dùng CHUNG với đặt-cửa-sổ và cluster-cast. `exec`
 *   (hoặc chạy tiền cảnh) sẽ chặn worker đó **vĩnh viễn** ⇒ treo cả launcher lẫn cast. Vì thế helper được
 *   `nohup … &` + chuyển hướng cả 3 fd để lệnh trả về ngay.
 */
object HalHelperLauncher {

    /** Cổng của kinex, giữ nguyên số cho dễ đối chiếu khi đọc log trên xe. */
    const val PORT = 19322

    private const val TAG = "KachiHalLaunch"
    private const val ASSET = "kachi_hal.jar"
    private const val REMOTE = "/data/local/tmp/kachi_hal.jar"
    private const val REMOTE_B64 = "/data/local/tmp/kachi_hal.b64"

    /** Helper ghi stdout vào đây — đường chẩn đoán duy nhất khi đăng ký HAL hỏng trên xe. */
    private const val REMOTE_LOG = "/data/local/tmp/kachi_hal.log"

    private const val MAIN_CLASS = "com.byd.clusternav.hal.KachiHalMain"

    /** Bội số của 3 — xem KDoc lớp. */
    private const val CHUNK_RAW = 3042

    private const val PROBE_TIMEOUT_MS = 400
    private const val WAIT_TRIES = 12
    private const val WAIT_STEP_MS = 300L

    /**
     * Đã đối chiếu cỡ jar ở xe trong LẦN chạy này của tiến trình chưa.
     *
     * Vì sao cần: nếu chỉ dò cổng rồi thoát sớm, một helper **bản cũ** đang chạy sau khi app cập nhật sẽ được
     * dùng mãi (cổng mở nên không ai kiểm gì) — đúng họ lỗi "im lặng chạy sai bản". Cờ này khiến việc đối chiếu
     * xảy ra ĐÚNG MỘT LẦN mỗi tiến trình: tiến trình mới (app vừa cập nhật) ⇒ `false` ⇒ kiểm lại.
     */
    @Volatile private var verified = false

    /** @return true nếu cổng đang phục vụ khi hàm trả về. */
    fun ensure(ctx: Context): Boolean {
        if (verified && probe()) return true

        val local = runCatching { stageAsset(ctx) }.getOrElse {
            Log.w(TAG, "không lấy được asset $ASSET: $it")
            return false
        }

        val shell = runCatching { ShellTransport.get(ctx) }.getOrElse {
            Log.w(TAG, "không có kênh shell: $it")
            return false
        }

        val fresh = runCatching { remoteMatches(shell, local.length()) }.getOrDefault(false)
        if (!fresh) {
            Log.i(TAG, "đẩy $ASSET (${local.length()} B) → $REMOTE")
            // Bản ở xe khác bản ở máy ⇒ helper đang chạy (nếu có) là bản CŨ, phải giết trước khi thay tệp.
            killHelper(shell)
            if (!runCatching { push(shell, local) }.getOrDefault(false)) {
                Log.w(TAG, "đẩy jar thất bại")
                return false
            }
        }

        if (probe()) {
            // Jar đã đúng bản và helper đang chạy — không khởi động thêm.
            verified = true
            return true
        }

        launch(shell)
        val up = waitForPort()
        if (up) {
            verified = true
            Log.i(TAG, "helper phục vụ 127.0.0.1:$PORT")
        } else {
            Log.w(TAG, "helper KHÔNG mở được cổng $PORT — xem $REMOTE_LOG trên xe")
        }
        return up
    }

    /** Nối thử cổng. Dùng cả cho đường nóng, nên hạn 400 ms như kinex ([ĐO] `Y0/F.java`). */
    fun probe(): Boolean = runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress("127.0.0.1", PORT), PROBE_TIMEOUT_MS)
            true
        }
    }.getOrDefault(false)

    // ── Các bước ────────────────────────────────────────────────────────────────────────────────────────────

    /** Sao asset ra `filesDir` (chỉ khi cỡ khác) để có một [File] đọc lại được nhiều lần. */
    private fun stageAsset(ctx: Context): File {
        val dest = File(ctx.filesDir, ASSET)
        val bytes = ctx.assets.open(ASSET).use { it.readBytes() }
        if (!dest.exists() || dest.length() != bytes.size.toLong()) {
            dest.outputStream().use { it.write(bytes) }
        }
        return dest
    }

    /**
     * Cỡ tệp ở xe có khớp [size] không.
     *
     * Cố ý dùng **cỡ tệp** chứ không md5: `wc -c` là builtin của toybox nên luôn có, còn `md5sum` thì tuỳ ROM —
     * một phép kiểm không chạy được sẽ luôn báo "khác" và đẩy lại jar mỗi lần. Đổi bản mà cỡ trùng y hệt là
     * [CHƯA BIẾT] nhưng rất khó xảy ra; nếu gặp thì xoá `$REMOTE` trên xe.
     */
    private fun remoteMatches(shell: ShellTransport, size: Long): Boolean {
        val out = shell.run("wc -c < $REMOTE 2>/dev/null").trim()
        return out.toLongOrNull() == size && size > 0
    }

    private fun push(shell: ShellTransport, jar: File): Boolean {
        val bytes = jar.readBytes()
        shell.run("rm -f $REMOTE $REMOTE_B64")
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + CHUNK_RAW, bytes.size)
            val chunk = Base64.encodeToString(bytes.copyOfRange(offset, end), Base64.NO_WRAP)
            // Bảng chữ base64 không có dấu nháy đơn; nếu có thì `echo '…'` sẽ vỡ thành lệnh khác ⇒ dừng.
            if (chunk.contains('\'')) {
                Log.w(TAG, "khúc base64 có dấu nháy ở offset $offset — huỷ")
                return false
            }
            shell.run("echo '$chunk' >> $REMOTE_B64")
            offset = end
        }
        shell.run("base64 -d < $REMOTE_B64 > $REMOTE && rm -f $REMOTE_B64")
        val ok = remoteMatches(shell, bytes.size.toLong())
        if (!ok) Log.w(TAG, "sau khi đẩy, cỡ ở xe vẫn không khớp (${bytes.size} B)")
        return ok
    }

    /**
     * Giết helper cũ (nếu có). Khớp theo TÊN LỚP nên không đụng tiến trình `app_process` của việc khác.
     *
     * ⚠ `[K]achiHalMain` KHÔNG phải lỗi chính tả. `pkill -f` so khớp **toàn bộ dòng lệnh** của tiến trình khác,
     * mà dòng lệnh của chính con shell đang chạy `pkill` cũng chứa chuỗi mẫu ⇒ nó tự giết shell cha của mình.
     * Đóng ngoặc một ký tự làm mẫu (regex) không còn khớp dòng lệnh chứa nó nguyên văn, trong khi vẫn khớp
     * `…hal.KachiHalMain 19322` của helper thật. (Cùng bẫy vừa cho `pgrep` một dương-tính-giả lúc dọn máy ảo.)
     */
    private fun killHelper(shell: ShellTransport) {
        shell.run("pkill -9 -f '[K]achiHalMain' 2>/dev/null; true")
    }

    /**
     * Chạy helper. Chuỗi `app_process64` → `app_process` → `app_process` (PATH) đúng thứ tự kinex dùng
     * ([ĐO] `Y0/F.java`) vì tên binary khác nhau giữa các bản ROM 64-bit.
     *
     * ⚠ **KHÔNG bọc trong `sh -c "…"`.** `ShellTransport.run` đã chạy chuỗi này qua shell của thiết bị rồi; thêm
     * một lớp `sh -c` với nháy ĐÔI thì shell NGOÀI sẽ khai triển `$BIN` (chưa có giá trị) thành **rỗng** trước
     * khi shell trong nhìn thấy nó ⇒ lệnh thành `nohup  / <class> <port>` và helper không bao giờ lên, im lặng.
     * Gửi thẳng một câu lệnh ghép: `BIN=` và `$BIN` nằm trong CÙNG một shell nên khai triển đúng.
     */
    private fun launch(shell: ShellTransport) {
        val pick = "if [ -x /system/bin/app_process64 ]; then BIN=/system/bin/app_process64; " +
            "elif [ -x /system/bin/app_process ]; then BIN=/system/bin/app_process; else BIN=app_process; fi"
        // `nohup … &` + chuyển hướng cả 3 fd ⇒ lệnh trả về NGAY, không giữ worker của ShellTransport (xem KDoc).
        val cmd = "$pick; CLASSPATH=$REMOTE nohup \$BIN / $MAIN_CLASS $PORT > $REMOTE_LOG 2>&1 < /dev/null &"
        Log.i(TAG, "khởi động helper: $MAIN_CLASS $PORT")
        shell.run(cmd)
    }

    private fun waitForPort(): Boolean {
        repeat(WAIT_TRIES) {
            if (probe()) return true
            runCatching { Thread.sleep(WAIT_STEP_MS) }.getOrElse { return false }
        }
        return false
    }
}
