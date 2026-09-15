package com.byd.clusternav.launcher.voice

import com.byd.clusternav.Lang
import java.io.File
import java.security.MessageDigest

/**
 * ═══ SIDE-LOAD MÔ HÌNH VOICE TỪ THẺ/USB/adb — cho xe KHÔNG có internet ═══════════════════════════════════════
 *
 * Owner 2026-09-15: *"voice trên xe hoàn toàn không work"*. [SUY mạnh, chốt bằng bridge `state.voice_model`] một
 * gốc hay gặp: mô hình chỉ có đường **tải qua mạng** ([VoiceModelStore.install] → `download`), mà đầu xe thường
 * không có internet ⇒ không bao giờ có mô hình ⇒ tấm chữ báo "chưa tải mô hình" rồi im. Lớp này mở đường thứ hai
 * **cùng phép kiểm** với đường mạng: tệp đặt sẵn ở thư mục ngoài của app (adb push / chép từ USB) → chép vào
 * staging **vừa chép vừa băm SHA-256** → so `bytes` + `sha256` với bản ghim trong [SherpaModelCatalog] → khớp mới
 * nhận. Không khớp ⇒ xoá tệp đích + trả lỗi NÓI RÕ là tệp side-load hỏng (không âm thầm rơi về mạng, vì trên xe
 * thường không có mạng để rơi về — người chép USB cần biết tệp sai).
 *
 * Thuần `java.io` (không Context) để test JVM bằng tệp tạm: [copyVerified] là toàn bộ logic; đường dẫn thư mục
 * ngoài do [VoiceModelStore] ghép (`getExternalFilesDir/sherpa/import/<model-id>/`).
 */
internal object VoiceModelSideload {

    /** Thư mục con (trong external files dir) mà người dùng đặt tệp mô hình: `<ext>/sherpa/import/<model-id>/<tên tệp>`. */
    const val IMPORT_SUBDIR = "sherpa/import"

    /** Kết quả chép-và-băm. */
    data class Copied(val bytes: Long, val sha256: String)

    /**
     * Tệp side-load cho [name] có sẵn không (tồn tại, là tệp thường, >0 byte). `null` khi không có — caller đi đường mạng.
     *
     * ⚠ **Hợp đồng:** [name] phải là MỘT đoạn tên đã qua luật chống leo thư mục của caller
     * (`VoiceModelStore.requireSafe`, CLAUDE.md §4.1) — ở đây tên luôn tới từ bản ghim [SherpaModelCatalog],
     * không bao giờ từ người dùng. Lớp này vẫn tự chặn lần nữa (rẻ, và ngăn một call site tương lai quên):
     * có `/ \ :` hay là `.`/`..` ⇒ coi như không có tệp side-load.
     */
    fun candidate(importDir: File?, name: String): File? {
        if (name.isBlank() || name == "." || name == ".." ||
            name.any { it == '/' || it == '\\' || it == ':' }
        ) return null
        return importDir?.let { File(it, name) }?.takeIf { it.isFile && it.length() > 0L }
    }

    /**
     * Chép [src] → [out] vừa chép vừa băm; so với bản ghim. Trả `null` = OK; chuỗi = lỗi (đã xoá [out]).
     * Cùng ngưỡng với đường mạng ([VoiceModelStore] `fetch`): lệch 1 byte hay 1 ký tự sha là từ chối.
     *
     * [onBytes] nhận **tổng byte đã chép được tới lúc này** sau mỗi khối — encoder là 249 MB, chép từ thẻ vào
     * bộ nhớ trong mất hàng chục giây; không có nhịp này thì thanh tiến trình đứng im và người dùng bấm lại
     * (đúng bệnh mà KDoc [VoiceModelSettings] đã mô tả cho bước băm).
     */
    fun copyVerified(
        src: File,
        out: File,
        expectedBytes: Long,
        expectedSha256: String,
        onBytes: ((Long) -> Unit)? = null,
    ): String? {
        val got = runCatching { copyHashed(src, out, onBytes) }
            .getOrElse { t ->
                runCatching { out.delete() }
                // Chữ hiện trên màn cài mô hình (Step.Failed) ⇒ song ngữ qua Lang.t như VoiceModelStore (bài canh i18n :app).
                return Lang.t(
                    "side-load ${src.name}: không đọc/chép được (${t.javaClass.simpleName})",
                    "side-load ${src.name}: cannot read/copy (${t.javaClass.simpleName})",
                )
            }
        if (got.bytes != expectedBytes || !got.sha256.equals(expectedSha256, ignoreCase = true)) {
            runCatching { out.delete() }
            return Lang.t(
                "side-load ${src.name} không khớp bản ghim (${got.bytes}/${expectedBytes} byte, sha ${got.sha256.take(12)}…) — chép lại tệp đúng",
                "side-load ${src.name} does not match pin (${got.bytes}/${expectedBytes} bytes, sha ${got.sha256.take(12)}…) — copy the correct file",
            )
        }
        return null
    }

    private fun copyHashed(src: File, out: File, onBytes: ((Long) -> Unit)?): Copied {
        val digest = MessageDigest.getInstance("SHA-256")
        var read = 0L
        out.parentFile?.mkdirs()
        src.inputStream().buffered().use { input ->
            out.outputStream().buffered().use { output ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    output.write(buf, 0, n)
                    digest.update(buf, 0, n)
                    read += n
                    onBytes?.invoke(read)
                }
            }
        }
        return Copied(read, digest.digest().joinToString("") { "%02x".format(it) })
    }
}
