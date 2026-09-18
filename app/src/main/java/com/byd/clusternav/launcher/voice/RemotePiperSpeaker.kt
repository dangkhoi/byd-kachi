package com.byd.clusternav.launcher.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * ═══ #0 · ĐẦU LAUNCHER CỦA ĐƯỜNG PIPER — nói với [PiperTtsService] qua ranh giới tiến trình ═══════════════════
 *
 * Doc gốc `docs/diagnostics/oncar-piper-crash-binding-2026-09-18.md`. Đây là thứ [VoiceSpeakerRouter] cầm thay
 * cho [SherpaTtsSpeaker]: **cùng một giao diện** [VoiceSpeaker], nên `ClipSpeaker` (đường lùi) ·
 * `VoiceSpeakerSelector` · `VoiceSession` không biết gì đã đổi.
 *
 * ## ⚠⚠ HỢP ĐỒNG CHỊU LỰC: `onDone` PHẢI CHẠY ĐÚNG MỘT LẦN — KỂ CẢ KHI `:tts` CHẾT
 * `VoiceSession` (cổng xác nhận, `onReplyDone`, overlay) **chờ** mốc ấy; nuốt nó là treo im lặng. Ranh giới tiến
 * trình thêm đúng một đường hỏng mà bản trong-tiến-trình không có: **đầu kia biến mất giữa câu** — mà đó lại
 * chính là ca đang vá, tức ca *chắc chắn sẽ xảy ra*. Nên mọi đường đều đóng sổ:
 *
 * | đường | ai đóng sổ |
 * |---|---|
 * | đọc hết · bị cắt ở `:tts` · tổng hợp ném | `MSG_DONE` về ⇒ [settle] |
 * | `bindService` trả `false` / ném | [speakInternal] đóng **ngay**, trả `false` (degrade) |
 * | `Messenger.send` ném (`RemoteException`) | [onRemoteGone] ⇒ đóng **hết** |
 * | **`:tts` SIGSEGV** / LMK dọn | `onServiceDisconnected` / `onBindingDied` ⇒ [onRemoteGone] ⇒ đóng **hết** |
 * | launcher huỷ phiên | [stop] ⇒ đóng hết (câu bị cắt = coi như đọc xong) |
 * | màn chính chết | [shutdown] ⇒ [stop] rồi unbind |
 *
 * Nguyên tắc khi phân vân: **thiên về gọi `onDone`**. Gọi thừa là vô hại ([settle] `remove` một lần); gọi thiếu
 * là một cổng chết im — thứ không ai phát hiện ra trong phòng.
 *
 * ## Nối muộn, nối lười
 * Không bind trong hàm dựng: [available] chỉ đọc đĩa ([SherpaTtsSpeaker.voiceFilesPresent]), nên một máy **chưa
 * lắp gói Piper** không bao giờ dựng tiến trình `:tts` nào. Câu đầu tiên mới bind, và câu ấy được **giữ lại**
 * (`queued`) rồi gửi ngay khi `onServiceConnected` về — không mất câu, không chặn luồng gọi.
 *
 * ## Khoá
 * Một `lock` duy nhất cho 4 mảnh trạng thái (`remote` · `bound` · `queued` · `pending`), vì chúng **đổi cùng
 * nhau**: `speak` có thể tới từ luồng vẽ (`VoiceSession`) hoặc từ luồng `KachiClip` (`ClipSpeaker` nhường Piper),
 * còn `ServiceConnection` chạy trên luồng chính. Mọi thao tác trong khoá đều là gán/`HashMap` — không gọi
 * `bindService`, không gọi `send`, không chạy `onDone` bên trong khoá (đó là đường tới deadlock).
 */
class RemotePiperSpeaker(ctx: Context) : VoiceSpeaker {

    /** Chỉ để BÁO CÁO: với `VoiceSpeakerSelector` đây vẫn đúng là *"đường Piper offline"*, chỉ khác chỗ ở. */
    override val kind: VoiceSpeakerKind = VoiceSpeakerKind.SHERPA_OFFLINE

    private val app = ctx.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val dead = AtomicBoolean(false)

    /** Số thế hệ câu — cũng là `id` đi sang `:tts` và quay về trong `MSG_DONE`. Đếm từ 1 ([PiperTtsService]). */
    private val generation = AtomicInteger(0)

    private val lock = Any()

    /** Hộp thư của `:tts` khi đã nối; `null` = chưa nối / vừa mất. Đọc-ghi trong [lock]. */
    private var remote: Messenger? = null

    /** Đã `bindService` và **chưa** `unbindService`. Phân biệt *"đang nối"* với *"chưa gọi bind"*. Trong [lock]. */
    private var bound = false

    /** Câu đang chờ nối xong để gửi; chỉ giữ câu MỚI NHẤT (câu cũ đã bị đè ⇒ đóng sổ). Trong [lock]. */
    private var queued: Queued? = null

    /** Các chỗ đang chờ *"đọc xong"*, theo số thế hệ. Trong [lock]. */
    private val pending = HashMap<Int, () -> Unit>()

    private class Queued(val gen: Int, val text: String)

    /**
     * Hộp thư của launcher — đặt trên **luồng chính**, nên `onDone` chạy trên luồng vẽ.
     *
     * Hợp đồng [VoiceSpeaker.speak] vế (2) cho phép `onDone` ở luồng bất kỳ, nhưng chỗ gọi (`VoiceSession`,
     * overlay) sống trên luồng vẽ, nên về đúng luồng ấy là bớt đi một lượt `post` có thể quên.
     */
    private val inbox = Messenger(
        Handler(Looper.getMainLooper()) { msg ->
            if (msg.what == PiperTtsService.MSG_DONE) {
                settle(msg.data?.getInt(PiperTtsService.KEY_ID) ?: -1)
            }
            true
        },
    )

    private val conn = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val to = binder?.let { Messenger(it) }
            val waiting: Queued?
            synchronized(lock) {
                remote = to
                waiting = queued
                queued = null
            }
            if (to == null) {
                Log.w(TAG, "nối được tiến trình đọc nhưng binder rỗng")
                onRemoteGone()
                return
            }
            // Câu đã chờ từ trước lúc nối — gửi NGAY, không bỏ (người lái đã nghe tiếng bíp rồi).
            waiting?.let { sendSpeak(to, it.gen, it.text) }
        }

        /**
         * `:tts` chết (đúng ca SIGSEGV đang vá) — nền tảng gọi trên luồng chính.
         *
         * ⚠ Lý do viết **trong chính lời gọi `Log`** ở từng chỗ, không truyền vào [onRemoteGone] dưới dạng chuỗi:
         * `LauncherI18nContractTest` nhận chuỗi chẩn đoán bằng **cấu trúc** (`Log.*` · `throw` · `require` ·
         * `Lang.t`), và một câu nhật ký tiếng Việt đi làm tham số hàm thì nó **không** phân biệt được với chữ trên
         * màn — [ĐO] lượt đầu của lượt này đỏ đúng 5 chuỗi ấy. Nới danh sách `allowed` của bài canh để lách là
         * làm yếu đúng cái bài đang gác 119 chuỗi khác; đặt câu vào chỗ nó thuộc về thì không phải sửa bài canh
         * nào, và mỗi chỗ còn nói được nguyên nhân RIÊNG của nó ngay khi xảy ra.
         */
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "tiến trình :tts chết (nghi SIGSEGV trong OfflineTts.generate)")
            onRemoteGone()
        }

        /** Binding không còn cứu được (service bị gỡ / crash-loop) ⇒ phải unbind rồi bind lại. */
        override fun onBindingDied(name: ComponentName?) {
            Log.w(TAG, "binding tới tiến trình đọc đã chết")
            onRemoteGone()
        }

        /** Service có đó mà trả binder rỗng — không đọc được câu nào, đừng để ai chờ. */
        override fun onNullBinding(name: ComponentName?) {
            Log.w(TAG, "tiến trình đọc trả binder rỗng")
            onRemoteGone()
        }
    }

    /**
     * Có đáng đi đường này không — **chỉ đọc đĩa**, không bind, không IPC.
     *
     * `VoiceSpeakerRouter.probe()` gọi mỗi câu (và cả khi vẽ Cài đặt), nên nó phải rẻ và phải **không** có tác
     * dụng phụ: dựng tiến trình `:tts` chỉ để trả lời một công tắc là tốn 61 MB cho một câu hỏi.
     */
    override fun available(): Boolean = !dead.get() && SherpaTtsSpeaker.voiceFilesPresent(app)

    override fun speak(text: String): Boolean = speakInternal(text, null)

    override fun speak(text: String, onDone: () -> Unit): Boolean = speakInternal(text, onDone)

    @Suppress("ReturnCount")
    private fun speakInternal(text: String, onDone: (() -> Unit)?): Boolean {
        if (dead.get() || text.isBlank() || !available()) {
            // Không đọc được ⇒ *"đọc xong"* là ngay bây giờ (vế (1) của hợp đồng).
            onDone?.let { fire(it) }
            return false
        }
        val my = generation.incrementAndGet()
        var to: Messenger? = null
        var superseded: (() -> Unit)? = null
        var needBind = false
        synchronized(lock) {
            if (onDone != null) pending[my] = onDone
            to = remote
            if (to == null) {
                // Câu đang chờ nối bị đè bởi câu này ⇒ đóng sổ cho nó, không để chỗ gọi kia chờ mãi.
                superseded = queued?.let { pending.remove(it.gen) }
                queued = Queued(my, text)
                needBind = !bound
                if (needBind) bound = true
            }
        }
        superseded?.let { fire(it) }

        to?.let { return sendSpeak(it, my, text) }
        // Lượt bind trước còn đang bay ⇒ `onServiceConnected` sẽ gửi câu vừa xếp.
        if (!needBind) return true
        if (bindRemote()) return true

        // Bind HỎNG (service bị gỡ / hệ thống từ chối) ⇒ degrade NGAY: không có gì sẽ về, nên đóng sổ tại đây.
        //
        // ⚠ [SOÁT senior 2026-09-18 · P2] Phải đóng sổ cho **MỌI** chỗ đang chờ, không chỉ cho `my`. `bound` được
        // đặt `true` bên trong khoá TRƯỚC khi gọi [bindRemote] (cố ý — để hai luồng không cùng bind), nên một câu
        // thứ hai tới giữa hai bước ấy thấy `bound == true` ⇒ nó xếp vào `queued` + `pending` rồi trả `true`. Bản
        // trước ở đây chỉ `settle(my)` và `queued = null` ⇒ **`onDone` của câu thứ hai không bao giờ chạy**, tức
        // đúng cái treo im lặng mà cả lớp này sinh ra để chặn (hai luồng gọi là chuyện thật: luồng vẽ của
        // `VoiceSession` và luồng `KachiClip` của `ClipSpeaker` — xem KDoc [lock]).
        //
        // [onRemoteGone] làm đúng cả ba việc: mở mọi sổ · `bound = false` để câu sau bind lại · và `unbindService`
        // — bước cuối là bắt buộc kể cả khi `bindService` trả `false` (tài liệu Android: lượt gọi đó VẪN đăng ký
        // [conn]). Bỏ nó là rò một `ServiceConnection` mỗi lần bind hỏng, và lượt bind sau nổ
        // `IllegalArgumentException: Service not registered` ở một chỗ hoàn toàn khác.
        onRemoteGone()
        return false
    }

    /** `true` = hệ thống đã nhận lượt bind. Ném/`false` đều coi là hỏng (chỗ gọi tự đóng sổ). */
    private fun bindRemote(): Boolean = runCatching {
        app.bindService(Intent(app, PiperTtsService::class.java), conn, Context.BIND_AUTO_CREATE)
    }.onFailure { Log.w(TAG, "không bind được tiến trình đọc", it) }.getOrDefault(false)

    private fun sendSpeak(to: Messenger, id: Int, text: String): Boolean {
        val msg = Message.obtain(null, PiperTtsService.MSG_SPEAK).apply {
            data = Bundle().apply {
                putInt(PiperTtsService.KEY_ID, id)
                putString(PiperTtsService.KEY_TEXT, text)
            }
            replyTo = inbox
        }
        return runCatching { to.send(msg); true }
            .onFailure {
                // `RemoteException` = đầu kia vừa chết. `onServiceDisconnected` thường tới sau ĐỘ TRỄ, nên đóng
                // sổ ngay ở đây; [settle] chỉ chạy một lần nên lượt callback kia tới sau là no-op.
                Log.w(TAG, "gửi câu sang tiến trình đọc hỏng — coi như đã mất tiến trình", it)
                onRemoteGone()
            }
            .getOrDefault(false)
    }

    /**
     * Đầu kia không còn — **mở mọi chỗ đang chờ** rồi đánh dấu unbound để câu sau nối lại.
     *
     * Chủ ý `unbindService` chứ không dựa vào lượt tự-nối-lại của `BIND_AUTO_CREATE`: một đường duy nhất, đo
     * được, và `onBindingDied` thì bắt buộc phải unbind mới bind lại được.
     *
     * Nguyên nhân cụ thể do **chỗ gọi** ghi nhật ký (xem KDoc [ServiceConnection.onServiceDisconnected] ở trên).
     */
    private fun onRemoteGone() {
        val waiters: List<() -> Unit>
        val doUnbind: Boolean
        synchronized(lock) {
            remote = null
            queued = null
            doUnbind = bound
            bound = false
            waiters = pending.values.toList()
            pending.clear()
        }
        if (doUnbind) runCatching { app.unbindService(conn) }
        if (waiters.isNotEmpty()) {
            Log.w(TAG, "mở ${waiters.size} chỗ đang chờ 'đọc xong'; câu sau sẽ nối lại tiến trình đọc")
        }
        waiters.forEach { fire(it) }
    }

    /** Đóng sổ cho đúng thế hệ [id]; đã đóng rồi ⇒ không làm gì (giữ *"nhiều nhất một lần"*). */
    private fun settle(id: Int) {
        val done = synchronized(lock) { pending.remove(id) } ?: return
        fire(done)
    }

    /** Chạy một việc chờ trên luồng chính; ném thì chỉ ghi lại — một `onDone` hỏng không được lan ra IPC. */
    private fun fire(done: () -> Unit) {
        main.post { runCatching { done() }.onFailure { Log.w(TAG, "việc chờ 'đọc xong' ném", it) } }
    }

    /**
     * Cắt câu đang đọc. Câu bị cắt = **coi như đọc xong** (đúng nghĩa của [SherpaTtsSpeaker.stop]:
     * `VoiceSpeakerRouter` gọi `stop()` trước MỖI câu, nên chỗ đang chờ phải được mở trước câu mới).
     */
    override fun stop() {
        generation.incrementAndGet()
        val to: Messenger?
        val waiters: List<() -> Unit>
        synchronized(lock) {
            to = remote
            queued = null
            waiters = pending.values.toList()
            pending.clear()
        }
        to?.let { r ->
            runCatching { r.send(Message.obtain(null, PiperTtsService.MSG_STOP)) }
                .onFailure { Log.i(TAG, "không gửi được lệnh cắt câu", it) }
        }
        waiters.forEach { fire(it) }
    }

    /**
     * Nhả hẳn — màn chính đã chết. Unbind là thứ cho tiến trình `:tts` tắt, và [PiperTtsService.onDestroy] mới
     * nhả được phiên ONNX (chỉ nó cầm engine).
     */
    override fun shutdown() {
        dead.set(true)
        stop()
        val doUnbind: Boolean
        synchronized(lock) {
            doUnbind = bound
            bound = false
            remote = null
        }
        if (doUnbind) runCatching { app.unbindService(conn) }.onFailure { Log.i(TAG, "unbind hỏng", it) }
    }

    private companion object {
        const val TAG = "KachiVoiceTtsLink"
    }
}
