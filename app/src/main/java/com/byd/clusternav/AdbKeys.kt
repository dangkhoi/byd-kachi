package com.byd.clusternav

import android.content.Context
import dadb.AdbKeyPair
import java.io.File

/**
 * Keypair ADB DÙNG CHUNG cho mọi client dadb tự-nối (NavConnect tự-heal nav listener + ClusterCast cấp
 * mock_location). Trước đây MỖI nơi tự `if (!exists) AdbKeyPair.generate(...)` trên CÙNG file `adb.key/adb.pub`
 * KHÔNG khóa chung → lúc CÀI LẠI (file chưa có) 2 thread cùng generate → keypair HỎNG (generate ghi 2
 * FileOutputStream không nguyên tử) → chết cả mock-grant lẫn nav-reconnect tới khi xoá data.
 *
 * Sửa: (1) `@Synchronized` trên 1 monitor DUY NHẤT → 2 caller không đua generate; (2) sinh ra file TẠM rồi
 * `renameTo` (nguyên tử cùng filesystem) → check `exists()` không bao giờ thấy cặp ghi-dở.
 */
object AdbKeys {
    @Synchronized
    fun ensure(ctx: Context): AdbKeyPair {
        val dir = ctx.applicationContext.filesDir
        val priv = File(dir, "adb.key"); val pub = File(dir, "adb.pub")
        if (!priv.exists() || !pub.exists()) generate(dir, priv, pub)
        // Cặp CÓ MẶT nhưng có thể HỎNG (crash lúc ghi / rename lỗi lần trước) → read ném → sinh lại 1 lần rồi read.
        return runCatching { AdbKeyPair.read(priv, pub) }.getOrElse {
            generate(dir, priv, pub)
            AdbKeyPair.read(priv, pub)
        }
    }

    /**
     * READY-AT-HOME §4.4.1 — vân tay của khoá CÔNG KHAI đang dùng (SHA-256 hex của `adb.pub`). Dấu bền "xe đã duyệt"
     * gắn với vân tay này: khoá sinh lại ([ensure] khi đọc hỏng) = khoá mới = chưa duyệt, dấu cũ tự hết hiệu lực.
     * Không bao giờ in khoá ra log — chỉ 8 ký tự đầu của vân tay (R-nf6). `null` = không đọc được khoá.
     */
    @Synchronized
    fun fingerprint(ctx: Context): String? = try {
        ensure(ctx)
        val pub = File(ctx.applicationContext.filesDir, "adb.pub")
        java.security.MessageDigest.getInstance("SHA-256").digest(pub.readBytes())
            .joinToString("") { String.format(java.util.Locale.ROOT, "%02x", it) }
    } catch (e: java.io.IOException) {
        android.util.Log.w("AdbKeys", "không đọc được adb.pub để lấy vân tay: ${e.message}"); null
    } catch (e: java.security.GeneralSecurityException) {
        android.util.Log.w("AdbKeys", "không băm được adb.pub: ${e.message}"); null
    } catch (e: RuntimeException) {
        // `ensure` ném `IllegalStateException` khi rename cặp khoá hỏng (xem [generate]) — không có khoá thì không có vân tay.
        android.util.Log.w("AdbKeys", "khoá adb lỗi khi lấy vân tay: ${e.message}"); null
    }

    /** Sinh keypair NGUYÊN TỬ: ghi file tạm rồi rename vào đích. Ném nếu rename hỏng (khỏi để lại cặp ghi-dở/thiếu). */
    private fun generate(dir: File, priv: File, pub: File) {
        val tp = File(dir, "adb.key.tmp"); val tb = File(dir, "adb.pub.tmp")
        runCatching { tp.delete(); tb.delete() }
        AdbKeyPair.generate(tp, tb)
        // xoá đích (có thể còn file partial từ lần crash) rồi rename → rename luôn thấy đích trống, không kẹt.
        runCatching { priv.delete(); pub.delete() }
        check(tp.renameTo(priv) && tb.renameTo(pub)) { "rename adb keypair thất bại (${dir.absolutePath})" }
    }
}
