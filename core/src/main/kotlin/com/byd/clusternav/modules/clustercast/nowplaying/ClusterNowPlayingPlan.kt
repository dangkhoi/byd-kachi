package com.byd.clusternav.modules.clustercast.nowplaying

/**
 * ═══ 3.1.11 · CLUSTER-NOW-PLAYING — phần THUẦN: ai được ghi, ghi gì, theo thứ tự nào, trả lại thế nào ════════════════
 *
 * Cơ chế ở `docs/diagnostics/cluster-now-playing-2026-10-16.md` §4 phương án A: cụm ẩn ô nhạc khi nguồn ∉ tập cho hiện
 * (app ngoài BYD nhận 26). Kachi ghi đè nguồn = [ClusterNowPlayingIds.overrideSource] rồi gửi lại tên bài + trạng thái.
 *
 * ## Thứ tự ghi (một bài mới) — doc §4.A
 *  `đọc nguồn` (cổng + giá trị trả về) → **dấu bền** (TRƯỚC lần ghi đè đầu, CLAUDE.md §5) → `nguồn` → `tên` → `trạng thái`
 *  → `đọc lại nguồn`. Đọc lại ra giá trị khác nguồn ghi đè ⇒ ghi lại nguồn + tên **một** lần cho bài đó (không vòng lặp).
 *  ⇒ 3 lượt ghi binder / bài; cùng bài, cùng trạng thái ⇒ **0 việc** (sự kiện phiên lặp lại bị nuốt ở đây).
 *
 * ## Cổng (generic — không tên gói, CLAUDE.md §7)
 *  công tắc BẬT · hồ sơ có số liệu · phiên đang chọn là app NGOÀI hệ thống ([NowPlayingMedia.thirdParty], quyết ở
 *  [NowPlayingOwner]) · ĐANG PHÁT · có tên · xe không ở OFF/ACC (đọc được mới chặn — cụm tự cổng `powerGear==3` [ĐO QML]) ·
 *  nếu đọc được nguồn hiện tại và nó ĐÃ nằm trong tập cho hiện ⇒ không đụng (đã hiện rồi, vd BT/Spotify/CarPlay).
 *
 * ## Trả lại (CLAUDE.md §5) — [restore]
 *  Ghi lại nguồn đã đọc trước lúc ghi đè (không đọc được ⇒ [ClusterNowPlayingIds.restoreFallback]) khi: dừng/tạm dừng quá
 *  [PAUSE_RESTORE_MS] · hết phiên · công tắc TẮT · Kachi dừng sạch · xe về OFF/ACC. **Không ghi** khi đọc lại thấy một giá trị
 *  KHÁC nguồn ghi đè (MediaController đã ghi nguồn của nó — giữ của nó), và không ghi khi phiên chuyển sang app HỆ THỐNG
 *  (MediaController tự ghi nguồn mới vì nó khác giá trị nó nhớ — ghi đè lúc đó có thể giấu ô của chính app BYD).
 *  Dấu bền dọn ở mọi lối; tiến trình chết giữa chừng ⇒ [restoreAtStartup] ở lần khởi động sau.
 *
 * Không luồng, không nhịp: bên gọi chạy mọi hàm trên MỘT luồng, và chỉ hẹn đúng một lượt [onPauseCheck] khi [onMedia] trả
 * về một độ trễ (lượt tạm dừng ĐẦU của một quãng dừng — sự kiện dừng lặp lại trả `null`, không dời mốc). Xe OFF/ACC ⇒ bài đó
 * không xét lại cho tới khi đổi bài/phiên (cổng nguồn là một lượt đọc HAL; sự kiện trùng không được tốn lượt đọc).
 * Không log nội dung người dùng nghe — chỉ độ dài/byte (cùng luật dòng `KachiYtResume`).
 */
class ClusterNowPlayingPlan(private val ids: ClusterNowPlayingIds?, private val sink: NowPlayingSink) {

    /** Lý do một lượt trả lại — mã ASCII cho dòng nhật ký. */
    enum class Why { PAUSE_TIMEOUT, NO_SESSION, SYSTEM_APP, SWITCH_OFF, STOP, POWER_OFF }

    @Volatile var status: NowPlayingStatus =
        NowPlayingStatus(if (ids == null) NowPlayingStatus.Kind.UNSUPPORTED else NowPlayingStatus.Kind.OFF)
        private set

    private var switchOn = false
    private var overriding = false
    private var prior = 0
    private var songKey: String? = null
    private var checkedKey: String? = null
    private var lastState: Int? = null
    private var pausedAtMs: Long? = null
    private var reassertedFor: String? = null

    /** Công tắc đổi. TẮT ⇒ trả lại ngay (nếu đang ghi đè). */
    fun onSwitch(on: Boolean, nowMs: Long) {
        switchOn = on
        if (ids == null) { set(NowPlayingStatus.Kind.UNSUPPORTED, nowMs); return }
        if (!on) {
            restore(Why.SWITCH_OFF, nowMs)
            set(NowPlayingStatus.Kind.OFF, nowMs)
        } else {
            // Soát Pass 12 [P2]: BẬT xong mà chưa có sự kiện nào thì tình trạng là "đang chờ", không phải "đang tắt" (dòng Cài đặt + cổng
            // NO_ACCESS ở `:app` dựa vào `kind != OFF`). Lượt bám phiên đầu sẽ thay bằng trạng thái thật ngay sau đó.
            set(NowPlayingStatus.Kind.NO_MEDIA, nowMs)
        }
    }

    /**
     * Phiên đang chọn đổi (metadata / trạng thái phát / danh sách phiên). [power] chỉ được hỏi khi sắp GHI (lười — 0 binder
     * cho sự kiện trùng). @return độ trễ (ms) để hẹn ĐÚNG một lượt [onPauseCheck], hoặc `null`.
     */
    fun onMedia(media: NowPlayingMedia?, power: () -> Int?, nowMs: Long): Long? {
        val ids = ids ?: run { set(NowPlayingStatus.Kind.UNSUPPORTED, nowMs); return null }
        if (!switchOn) return null
        if (media == null) { restore(Why.NO_SESSION, nowMs); set(NowPlayingStatus.Kind.NO_MEDIA, nowMs); return null }
        if (!media.thirdParty) { restore(Why.SYSTEM_APP, nowMs); set(NowPlayingStatus.Kind.SYSTEM_APP, nowMs); return null }
        val title = media.title?.trim().orEmpty()
        if (title.isEmpty()) {
            if (!overriding) set(NowPlayingStatus.Kind.NO_TITLE, nowMs)
            return null
        }
        if (!media.playing) return paused(ids, nowMs)
        pausedAtMs = null
        val key = media.pkg + "\u0000" + title
        if (overriding && key == songKey) {
            if (lastState != ids.statePlaying) {
                val rc = sink.writeInt(ids.stateId, ids.statePlaying)
                lastState = ids.statePlaying
                sink.log("np resume state=${ids.statePlaying} rc=$rc")
            }
            return null
        }
        if (!overriding && key == checkedKey) return null      // bài này đã xét (đã hiện sẵn / xe tắt) — sự kiện trùng
        checkedKey = key
        if (!carOn(power())) {
            restore(Why.POWER_OFF, nowMs)
            checkedKey = key      // Soát Pass 12 [P2]: `restore` xoá dấu xét — đặt lại để sự kiện trùng khi xe OFF/ACC không đọc HAL nữa
            set(NowPlayingStatus.Kind.POWER_OFF, nowMs)
            return null
        }
        val cur = readSource(ids)
        if (cur != null && cur != ids.overrideSource && ids.shown(cur)) {
            // Cụm đã hiện với nguồn này (vd BT/Spotify/CarPlay — hoặc MediaController vừa ghi nguồn được phép): không đụng.
            if (overriding) leave("np handoff src=$cur (cum da hien)")
            set(NowPlayingStatus.Kind.ALREADY_SHOWN, nowMs, "src=$cur")
            return null
        }
        if (!overriding) {
            prior = cur?.takeIf { it != ids.overrideSource } ?: ids.restoreFallback
            if (!sink.saveMarker(NowPlayingMarker.encode(ids.sourceId, prior, ids.overrideSource))) {
                sink.log("np marker FAILED — khong ghi de (khong co duong tra lai)")
                set(NowPlayingStatus.Kind.MARKER_FAILED, nowMs)
                return null
            }
            overriding = true
        }
        writeSong(ids, key, title, cur, nowMs)
        return null
    }

    /** Lượt hẹn sau tạm dừng: vẫn dừng đủ [PAUSE_RESTORE_MS] ⇒ trả lại. */
    fun onPauseCheck(nowMs: Long) {
        val since = pausedAtMs ?: return
        if (nowMs - since >= PAUSE_RESTORE_MS) {
            restore(Why.PAUSE_TIMEOUT, nowMs)
            set(NowPlayingStatus.Kind.PAUSED, nowMs)
        }
    }

    /** Kachi dừng sạch (tắt máy / gỡ bộ nghe). */
    fun stop(nowMs: Long) = restore(Why.STOP, nowMs)

    private fun paused(ids: ClusterNowPlayingIds, nowMs: Long): Long? {
        if (!overriding) { set(NowPlayingStatus.Kind.PAUSED, nowMs); return null }
        if (lastState != ids.statePaused) {
            val rc = sink.writeInt(ids.stateId, ids.statePaused)
            lastState = ids.statePaused
            sink.log("np pause state=${ids.statePaused} rc=$rc")
        }
        // Soát Pass 12 [P1]: chỉ lượt DỪNG ĐẦU mới hẹn; sự kiện dừng lặp lại (app báo trạng thái đệm/dừng nhiều lần) KHÔNG trả độ
        // trễ nữa — bên gọi hẹn lại từ đầu mỗi lần nhận độ trễ, nên trả lại ở mỗi sự kiện là dời mốc trả nguồn vô hạn.
        if (pausedAtMs != null) return null
        pausedAtMs = nowMs
        return PAUSE_RESTORE_MS
    }

    private fun writeSong(ids: ClusterNowPlayingIds, key: String, title: String, before: Int?, nowMs: Long) {
        val bytes = NowPlayingTitle.encode(title, ids.titleMaxBytes)
        val rcSrc = sink.writeInt(ids.sourceId, ids.overrideSource)
        val rcTitle = sink.writeBytes(ids.titleId, bytes)
        val rcState = sink.writeInt(ids.stateId, ids.statePlaying)
        val back = readSource(ids)
        songKey = key
        lastState = ids.statePlaying
        var line = "np write src=${hex(ids.sourceId)} v=${ids.overrideSource} rc=$rcSrc title=${hex(ids.titleId)} len=${title.length} " +
            "bytes=${bytes.size} rc=$rcTitle state=${hex(ids.stateId)} v=${ids.statePlaying} rc=$rcState before=${before ?: "-"} " +
            "back=${back ?: "-"} prior=$prior"
        if (back != null && back != ids.overrideSource && reassertedFor != key) {
            reassertedFor = key
            val rc2 = sink.writeInt(ids.sourceId, ids.overrideSource)
            val rcT2 = sink.writeBytes(ids.titleId, bytes)
            line += " reassert src rc=$rc2 title rc=$rcT2"
        }
        sink.log(line)
        set(NowPlayingStatus.Kind.ACTIVE, nowMs, "src=$rcSrc title=$rcTitle state=$rcState back=${back ?: "-"}")
    }

    /** Trả lại nguồn — xem KDoc lớp. Gọi khi không ghi đè ⇒ không làm gì. */
    private fun restore(why: Why, nowMs: Long) {
        checkedKey = null
        val ids = ids ?: return
        if (!overriding) return
        val cur = readSource(ids)
        val write = when {
            cur != null && cur != ids.overrideSource -> false          // đã có người ghi nguồn của họ — giữ của họ
            why == Why.SYSTEM_APP -> false                            // MediaController sẽ tự ghi nguồn của app BYD
            else -> true
        }
        val rc = if (write) sink.writeInt(ids.sourceId, prior) else "skip"
        leave("np restore why=$why src=${hex(ids.sourceId)} v=$prior rc=$rc cur=${cur ?: "-"}")
        set(NowPlayingStatus.Kind.RESTORED, nowMs, "why=$why rc=$rc")
    }

    private fun leave(line: String) {
        sink.clearMarker()
        overriding = false
        songKey = null
        lastState = null
        pausedAtMs = null
        reassertedFor = null
        sink.log(line)
    }

    private fun readSource(ids: ClusterNowPlayingIds): Int? = sink.readInt(ids.sourceId)?.takeIf { it in ClusterNowPlayingIds.SOURCE_RANGE }

    private fun set(kind: NowPlayingStatus.Kind, nowMs: Long, detail: String? = null) {
        status = NowPlayingStatus(kind, detail, nowMs)
    }

    companion object {
        /** Tạm dừng lâu hơn chừng này ⇒ trả nguồn (ô cụm về như hãng). Một lượt hẹn duy nhất, không nhịp. */
        const val PAUSE_RESTORE_MS = 15_000L

        /** Cấp nguồn `BYDAutoBodyworkDevice.getPowerLevel` (0 OFF · 1 ACC · 2 ON · 3 OK) — đọc được và không ON/OK ⇒ không ghi. */
        private val CAR_ON_LEVELS = setOf(2, 3)

        /** `null` (không đọc được) ⇒ không chặn: đây là tín hiệu hiển thị, cụm tự cổng `powerGear==3` [ĐO QML]. */
        /** Feature-id dạng `0x%08x` cho dòng nhật ký (trùng khuôn nhãn `BydHalGateway`). */
        fun hex(id: Int): String = "0x%08x".format(id)

        fun carOn(powerLevel: Int?): Boolean = powerLevel == null || powerLevel in CAR_ON_LEVELS

        /**
         * Lần khởi động sau khi tiến trình chết giữa lúc ghi đè: dấu bền còn ⇒ trả lại rồi dọn. Chỉ GHI khi đọc lại thấy đúng
         * nguồn ghi đè (vẫn là của Kachi); đọc ra giá trị khác ⇒ đã có người ghi, giữ; không đọc được ⇒ KHÔNG ghi mù (ghi 26
         * lên trên nguồn BT mà MediaController đang nhớ là giấu ô BT cho tới lần đổi nguồn sau; còn nguồn 11 sót lại thì vô
         * hại — cụm vẫn hiện tên, và MediaController ghi đè ở lần đổi nguồn kế). Dấu hỏng ⇒ chỉ dọn.
         */
        fun restoreAtStartup(marker: String?, sink: NowPlayingSink): String? {
            if (marker == null) return null
            val m = NowPlayingMarker.decode(marker)
            val line = if (m == null) {
                "np startup marker hong — chi don"
            } else {
                val cur = sink.readInt(m.sourceId)?.takeIf { it in ClusterNowPlayingIds.SOURCE_RANGE }
                val rc = if (cur == m.override) sink.writeInt(m.sourceId, m.prior) else "skip"
                "np startup restore src=${hex(m.sourceId)} v=${m.prior} rc=$rc cur=${cur ?: "-"}"
            }
            sink.clearMarker()
            sink.log(line)
            return line
        }
    }
}

/** Phiên nhạc đang được chọn (phiên ĐANG PHÁT trước, không thì phiên đầu — `MediaBridge.pick`). */
data class NowPlayingMedia(val pkg: String, val thirdParty: Boolean, val playing: Boolean, val title: String?)

/**
 * Cửa ra của [ClusterNowPlayingPlan] — `:app` nối vào đường ghi HAL chuẩn (`BydHal.cachedSetInt/cachedSetBytes`) + nhật ký
 * lệnh ghi xe (`CtlJournalStore`) + prefs. Mọi hàm KHÔNG ném (trả chuỗi rc / `null`).
 */
interface NowPlayingSink {
    /** Đọc một feature int; `null` = không đọc được (không device / sentinel / lỗi). */
    fun readInt(id: Int): Int?
    fun writeInt(id: Int, value: Int): String
    fun writeBytes(id: Int, bytes: ByteArray): String
    /** Ghi dấu bền ĐỒNG BỘ; `false` = không ghi được ⇒ bên gọi không được ghi đè. */
    fun saveMarker(marker: String): Boolean
    fun clearMarker()
    fun log(line: String)
}

/** Trạng thái cho dòng mô tả ở Cài đặt + lệnh cầu kiểm thử `np`. [detail] là ASCII (rc), không chứa tên bài. */
data class NowPlayingStatus(val kind: Kind, val detail: String? = null, val atMs: Long = 0L) {
    enum class Kind { UNSUPPORTED, OFF, NO_ACCESS, NO_MEDIA, SYSTEM_APP, NO_TITLE, PAUSED, POWER_OFF, ALREADY_SHOWN, MARKER_FAILED, ACTIVE, RESTORED }
}

/** Dấu bền "Kachi đang ghi đè nguồn": `<sourceId>:<prior>:<override>` — tự đủ để trả lại dù hồ sơ đã đổi. */
object NowPlayingMarker {
    data class Marker(val sourceId: Int, val prior: Int, val override: Int)

    fun encode(sourceId: Int, prior: Int, override: Int): String = "$sourceId:$prior:$override"

    fun decode(s: String): Marker? {
        val p = s.trim().split(':')
        if (p.size != 3) return null
        val id = p[0].toIntOrNull() ?: return null
        val prior = p[1].toIntOrNull()?.takeIf { it in ClusterNowPlayingIds.SOURCE_RANGE } ?: return null
        val ov = p[2].toIntOrNull()?.takeIf { it in ClusterNowPlayingIds.SOURCE_RANGE } ?: return null
        return Marker(id, prior, ov)
    }
}

/** Mã hoá tên bài như MediaController (`UnicodeUtils.java:15-23`): UTF-16LE, cắt ở biên điểm mã, ≤ [maxBytes]. */
object NowPlayingTitle {
    fun encode(title: String, maxBytes: Int): ByteArray {
        var end = 0
        var bytes = 0
        while (end < title.length) {
            val n = Character.charCount(title.codePointAt(end))
            if (bytes + 2 * n > maxBytes) break
            bytes += 2 * n
            end += n
        }
        return title.substring(0, end).toByteArray(Charsets.UTF_16LE)
    }
}

/**
 * App của phiên có phải app NGOÀI hệ thống không — phép quyết GENERIC (không tên gói, CLAUDE.md §7). App hệ thống (ảnh
 * `/system` chưa cập nhật · ký khoá nền tảng · uid hệ thống) ⇒ không đụng: MediaController đã gán nguồn đúng cho chúng.
 * App hệ thống ĐÃ cập nhật qua kho (YouTube cài sẵn rồi cập nhật) vẫn là app ngoài. Không đọc được ⇒ `false` (không đụng).
 */
object NowPlayingOwner {
    /** `android.os.Process.FIRST_APPLICATION_UID`. */
    const val FIRST_APPLICATION_UID = 10_000

    fun thirdParty(systemImage: Boolean, updatedSystem: Boolean, platformSigned: Boolean, uid: Int): Boolean =
        !(systemImage && !updatedSystem) && !platformSigned && uid >= FIRST_APPLICATION_UID
}
