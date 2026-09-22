package com.byd.clusternav.launcher.voice

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.BuildConfig
import com.byd.clusternav.R
import com.byd.clusternav.launcher.SettingsDeps
import com.byd.clusternav.launcher.SettingsRows
import com.byd.clusternav.launcher.setVoicePreferOffline
import com.byd.clusternav.launcher.setVoiceSpeakReplies
import com.byd.clusternav.launcher.voicePreferOffline
import com.byd.clusternav.launcher.voiceSpeakReplies

/**
 * ═══ KHỐI **GIỌNG NÓI** TRONG CÀI ĐẶT — cái TAI, cái MIỆNG, và hai công tắc ══════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **R9** (mô hình nghe) + `kachi-voice-feedback.html` **R4 · T8 · T9**
 * (gói đọc + công tắc). Nằm ở *Cài đặt › Hệ thống & quyền › Nâng cao*, ngay trên ô *"Gõ lệnh chữ"* — cùng chỗ,
 * vì đó là hai nửa của một việc: lắp cái tai và cái miệng, rồi thử cái đầu.
 *
 * ## Vì sao bốn hàng ở CÙNG một lớp, không phải bốn chỗ
 * Chúng trả lời cùng một câu hỏi của người dùng (*"Kachi nghe/nói thế nào trên xe này"*) và chúng **phụ thuộc
 * nhau**: *"Ưu tiên giọng offline"* chỉ có nghĩa khi hàng *Giọng đọc offline* đã báo "đã cài". Tách ra hai lớp
 * thì câu mô tả của công tắc phải nói về một hàng mà nó không nhìn thấy — và câu ấy sẽ lệch ở lần đổi đầu tiên.
 *
 * ## Vì sao là một cú bấm của NGƯỜI DÙNG, không tự tải lúc mở app
 * 61 MB (gói đọc) + 266 MB (mô hình nghe) qua mạng 4G của xe là tiền của người ta và là băng thông mà app dẫn
 * đường đang cần. Tự tải nền cũng là thứ không ai đoán được đang xảy ra (và trên xe thì "đang xảy ra" có thể là
 * đang chạy 80 km/h). Một hàng nói rõ cỡ tệp, một nút, một thanh tiến trình.
 *
 * ## Tiến trình phải HIỆN, và phải hiện đúng bước nào
 * Ba bước dài khác nhau về bản chất: **tải** (phụ thuộc mạng, có phần trăm), **kiểm** (băm hàng chục MB, vài
 * giây, im), **hoàn tất** (đổi tên thư mục). Gộp cả ba vào một chữ *"Đang cài…"* thì một lần kiểm băm chậm
 * trông y hệt một lần treo — và người dùng sẽ bấm lại, tức tải lại từ đầu.
 *
 * ## ⚠ Gói ĐỌC: nút *Tải* hôm nay đi vào một địa chỉ CHƯA CÓ ASSET
 * Xem TODO(owner) ở KDoc [SherpaTtsCatalog]: 13 tệp còn chờ owner đăng lên GitHub Release. Tới lúc đó nút *Tải*
 * sẽ báo lỗi fail-safe (404 / sha không khớp) **nói rõ tệp nào**, còn đường **side-load USB** chạy ngay — đó là
 * lý do câu mô tả của hàng nói cả hai đường thay vì chỉ mời bấm.
 */
class VoiceModelSettings(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
    /** Chạy việc dài trên luồng NỀN — tách ra để đo/kiểm được, mặc định là một Thread. */
    private val background: (() -> Unit) -> Unit = { block -> Thread(block, "KachiVoiceModel").start() },
) {

    /** Gói giọng ĐỌC — một gói duy nhất hôm nay; đọc từ danh mục `:core`, không viết cứng đường dẫn nào ở đây. */
    private val ttsPack = SherpaTtsCatalog.PIPER_VI_VAIS1000

    fun build(body: LinearLayout) {
        modelRow(body)
        // ⚠ `lightModelRows(body)` đã BỎ (owner 2026-09-21, bản release production) — xem chú thích ở chỗ hàm đó
        // từng đứng. Danh mục chỉ còn MỘT mô hình nghe, nên không còn gì để chọn giữa.
        attributionRows(body)
        ttsRow(body)
        speakToggles(body)
        // ⚠ `logRows(body)` đã BỎ cùng khối nhật ký lượt nói (owner 2026-09-21) — xem chú thích ở chỗ hàm đó từng
        // đứng. Gọi một hàm rỗng thì không ai thấy nó rỗng; bỏ hẳn lời gọi mới là thứ đọc ra được.
        // V3 · R7 — mục *"Hỏi xác nhận trước khi chạy"* + nguồn micro. Lớp RIÊNG (trần 500 dòng, CLAUDE.md §4.1)
        // nhưng dựng **ở đây** để trang Cài đặt vẫn có đúng một khối "Giọng nói" liền mạch.
        VoiceConfirmSettings(context, rows, deps).build(body)
    }

    // ── Cái TAI: mô hình nhận dạng (R9) ──────────────────────────────────────────────────────────

    /**
     * Hàng mô hình nghe: **trạng thái + một nút Tải/Gỡ** cho gói duy nhất trong danh mục.
     *
     * ⚠ Đây KHÔNG phải (và chưa bao giờ là) một bộ chọn mô hình — bề mặt chọn nằm ở `lightModelRows`, đã gỡ
     * 2026-09-21. Hàng này ở lại vì nó trả lời đúng một câu người dùng cần: *"cái tai đã lắp chưa, nặng bao nhiêu
     * trên đĩa, và tôi lấy lại chỗ bằng cách nào"*.
     */
    private fun modelRow(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_voice_model_title)))
        val status = rows.note(modelStatusText()) as TextView
        body.addView(status)
        // Ghi chú *"máy đã bỏ qua lượt nạp sẵn vì thiếu RAM"* — một DỮ KIỆN giải thích vì sao lần bấm mic đầu chờ
        // lâu, không phải một lượt tự đổi mô hình. Nó từng nằm trong khối chọn-mô-hình vừa gỡ; để nguyên ở đó thì
        // nó biến mất cùng khối, mà lý do nó tồn tại (RAM xe eo hẹp — [ĐO] còn 56–94 MB trống) thì không mất đi.
        VoiceEngine.lastPreloadSkip?.let {
            body.addView(rows.note(context.getString(R.string.kachi_voice_model_preload_skipped, it)))
        }

        val action = rows.button(modelActionLabel()) {} as TextView
        action.setOnClickListener {
            // 3 trạng thái như gói giọng đọc: chưa có ⇒ Tải · có bản mới ⇒ Cập nhật · mới nhất ⇒ Gỡ (owner 2026-09-22).
            if (!VoiceModelStore.isReady(context)) installModel(status, action)
            else if (VoiceModelStore.needsUpdate(context)) installModel(status, action)
            else removeModel(status, action)
        }
        body.addView(action)
    }

    private fun installModel(status: TextView, action: TextView) {
        action.isEnabled = false
        action.text = context.getString(R.string.kachi_voice_model_working)
        background {
            VoiceModelStore.install(context) { step ->
                // `onStep` tới từ luồng nền ⇒ mọi lần chạm view phải qua `post` (view chỉ đụng được trên luồng vẽ).
                status.post { status.text = stepText(step) { modelStatusText() } }
                if (step is VoiceModelStore.Step.Done || step is VoiceModelStore.Step.Failed) {
                    action.post { action.isEnabled = true; action.text = modelActionLabel() }
                }
            }
        }
    }

    private fun removeModel(status: TextView, action: TextView) {
        action.isEnabled = false
        background {
            // Trả mô hình khỏi bộ nhớ TRƯỚC khi xoá tệp: xoá trước thì mã native vẫn giữ các tệp đã mmap và
            // lần cài sau nạp nhầm bản cũ — xem KDoc [VoiceEngine.release].
            VoiceEngine.release()
            VoiceModelStore.remove(context)
            status.post { status.text = modelStatusText() }
            action.post { action.isEnabled = true; action.text = modelActionLabel() }
        }
    }

    private fun modelActionLabel(): String = context.getString(
        when {
            !VoiceModelStore.isReady(context) -> R.string.kachi_voice_model_get
            VoiceModelStore.needsUpdate(context) -> R.string.kachi_voice_model_update
            else -> R.string.kachi_voice_model_remove
        },
    )

    private fun modelStatusText(): String {
        val model = VoiceModelStore.selected(context)
        return if (!VoiceModelStore.isReady(context)) {
            context.getString(R.string.kachi_voice_model_absent, mb(model.totalBytes))
        } else if (VoiceModelStore.needsUpdate(context)) {
            context.getString(R.string.kachi_voice_model_has_update, model.label)
        } else {
            context.getString(
                R.string.kachi_voice_model_ready,
                model.label,
                model.files.size,
                mb(VoiceModelStore.sizeOnDisk(context)),
            )
        }
    }

    // ── H6 · ĐỔI SANG MÔ HÌNH NHẸ — BỀ MẶT ĐÃ GỠ (owner 2026-09-21, bản release production) ──────
    //
    // `lightModelRows` (hàng trạng thái + nút *Chuyển sang mô hình nhẹ* + nút *Gỡ bản nặng*) cùng `switchToLight`
    // · `dropHeavy` · `switchLabel` · `lightStatusText` · `lightDoneText` · `freeRamBytes` đã XOÁ. Đây là bề mặt
    // **chọn giữa nhiều mô hình nghe**, và danh mục nay chỉ còn ĐÚNG MỘT gói ([SherpaModelCatalog.ALL]) — owner
    // chốt dừng thử nghiệm mô hình, chỉ giữ gói đang chạy tốt trên xe.
    //
    // Vì sao xoá thay vì để nó tự ẩn: `SherpaModelCatalog.lighterThan` nay luôn trả `null` ⇒ hàng không bao giờ
    // hiện ⇒ cả khối là mã **không bao giờ chạy**, mà một hàng Cài đặt không bao giờ hiện thì không ai thấy nó đã
    // chết — nó chỉ nằm đó rữa dần cùng 5 chuỗi tài nguyên không ai đọc (đã xoá khỏi cả `values/` lẫn `values-en/`).
    //
    // ⚠ Hai thứ CỐ Ý ở lại:
    //   • ghi chú *"bỏ qua nạp sẵn vì thiếu RAM"* dời lên [modelRow] — nó là dữ kiện về RAM/độ trễ lần bấm mic
    //     đầu, không phải một lời mời đổi mô hình;
    //   • `SherpaModelCatalog.lighterThan` ở `:core` — chỗ gọi còn lại là trường **máy đọc**
    //     `state.voice_model.alt_available` của cầu kiểm thử, nơi `null` là câu trả lời ĐÚNG, không phải chỗ trống.
    //
    // Việc mà hai nút ấy từng làm (tải một gói khác rồi trỏ `selected` sang nó) nay **không còn đường nào** trong
    // app: `VoiceModelStore.select` đã gỡ cùng lượt, khoá `model` trong `kachi_voice` thành khoá CHỈ ĐỌC của các
    // bản ≤ 1.87 — và `SherpaModelCatalog.byId` lùi mọi id lạ về gói duy nhất.

    // ── GHI CÔNG tác giả mô hình — nghĩa vụ của giấy phép, không phải một dòng trang trí ─────────

    /**
     * *"Về mô hình nghe"* — tên tác giả · giấy phép · URL, cho **mọi** gói trong danh mục đòi ghi công.
     *
     * ## Vì sao nó là một hàng THẬT trong Cài đặt, không phải một dòng trong README
     * `BY` nghĩa là ghi công **ở nơi người dùng thấy**; một dòng nằm trong `voice/README.md` của kho mã thì người
     * ngồi trên xe không bao giờ đọc tới. Ba chỗ hiển thị (hàng này · `state.voice_model` · README) đều đọc **cùng
     * một** trường dữ liệu ([SherpaModelCatalog.attributions]) nên chúng không thể lệch nhau.
     *
     * ⚠ 2026-09-21: gói duy nhất còn trong danh mục là **Apache-2.0** ⇒ danh sách rỗng ⇒ hàng này **không hiện
     * gì**. Giữ lại vì nghĩa vụ ghi công là nghĩa vụ của DỮ LIỆU: thêm một gói CC BY vào danh mục là hàng tự hiện
     * lại, không phải nhớ dựng lại nó (CLAUDE.md §7) — và một tiêu đề trống thì tệ hơn là không có tiêu đề.
     */
    private fun attributionRows(body: LinearLayout) {
        val credits = SherpaModelCatalog.attributions()
        if (credits.isEmpty()) return
        body.addView(rows.subHeader(context.getString(R.string.kachi_voice_model_credits_title)))
        credits.forEach { m ->
            body.addView(rows.note(
                context.getString(R.string.kachi_voice_model_credits_line, m.label, m.attribution, m.license, m.sourceUrl),
            ))
        }
    }

    // ── H2 · NHẬT KÝ LƯỢT NÓI — BỀ MẶT ĐÃ GỠ (owner 2026-09-21, bản release production) ──────────
    //
    // `private fun logRows(body)` (ô tích *Giữ nhật ký lượt nói* + nút *Xuất nhật ký voice*) đã XOÁ, cùng lượt dọn
    // mọi bề mặt dev/debug/log khỏi màn Cài đặt: cả màn nay chỉ còn ĐÚNG một công tắc đồ đo — *Chế độ kiểm thử qua
    // adb* ở nhóm Hệ thống (`SettingsSections.testBridge`).
    //
    // ⚠ Chỉ gỡ BỀ MẶT, KHÔNG đổi HÀNH VI: `voice_keep_log` vẫn **mặc định BẬT** (xem KDoc `Prefs.voiceKeepLog`) và
    // [VoiceUtteranceLog] vẫn ghi + tự dọn (30 mục / 30 MB) như trước. Tắt ghi luôn ở lượt này thì buổi RE sau cắm
    // máy vào sẽ không còn nhật ký của những lượt nói TRƯỚC đó — tức mất đúng thứ nhật ký sinh ra để giữ.
    //
    // Hai đường qua adb thay cho hai bề mặt vừa gỡ (cả hai đã có sẵn, không phải dựng mới):
    //   • công tắc → `prefs_set --es key voice_keep_log --es text true|false`;
    //   • xuất zip → `voice_dump` ([com.byd.clusternav.launcher.testbridge.TestBridgeVoiceDump]) — nó gọi **chính**
    //     `VoiceUtteranceLog.exportZip` mà nút cũ gọi, nên không có đường nén thứ hai nào phải đi dọn.

    // ── Cái MIỆNG: gói giọng đọc offline (T8) ────────────────────────────────────────────────────

    /**
     * Hàng *Giọng đọc offline* — **cùng khuôn** với hàng mô hình ở trên, và cố ý vậy: hai việc giống hệt nhau
     * (tải nhiều tệp có ghim, gỡ, báo tiến trình) thì không được trông khác nhau trên màn.
     */
    private fun ttsRow(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_voice_tts_title)))
        val status = rows.note(ttsStatusText()) as TextView
        body.addView(status)

        val action = rows.button(ttsActionLabel()) {} as TextView
        action.setOnClickListener {
            // 3 trạng thái: chưa có ⇒ Tải · có bản mới ⇒ Cập nhật (tải đè) · đã mới nhất ⇒ Gỡ. (owner 2026-09-22)
            if (!ttsReady()) installTts(status, action)
            else if (VoiceModelStore.needsUpdate(context, ttsPack)) installTts(status, action)
            else removeTts(status, action)
        }
        body.addView(action)
        // ⚠ [SOÁT Pass 4 · P1] Đường dẫn side-load phải sinh từ **dữ liệu thật**, không chép tay vào chuỗi:
        // bản đầu viết cứng một tên gói SAI (`com.byd.clusternav2` — id của app cũ; `applicationId` thật là
        // `com.byd.launcher`) và bỏ mất đoạn `<id gói>`, tức người cầm USB chép đúng theo câu hướng dẫn thì tệp
        // rơi vào một thư mục không ai đọc, rồi nút Tải báo lỗi mạng mà không ai hiểu vì sao.
        body.addView(rows.note(context.getString(
            R.string.kachi_voice_tts_sideload, BuildConfig.APPLICATION_ID, ttsPack.id,
        )))
    }

    private fun installTts(status: TextView, action: TextView) {
        action.isEnabled = false
        action.text = context.getString(R.string.kachi_voice_model_working)
        background {
            VoiceModelStore.install(context, ttsPack) { step ->
                status.post { status.text = stepText(step) { ttsStatusText() } }
                if (step is VoiceModelStore.Step.Done || step is VoiceModelStore.Step.Failed) {
                    action.post { action.isEnabled = true; action.text = ttsActionLabel() }
                }
            }
        }
    }

    /**
     * Gỡ gói đọc.
     *
     * ⚠ KHÔNG gọi [VoiceEngine.release] ở đây (hàng mô hình NGHE thì có): engine đọc offline là một đối tượng
     * khác hẳn, và chủ sở hữu duy nhất của nó là `VoiceSpeakerRouter` của phiên nói — nó tự hỏi lại đĩa ở **mỗi
     * câu** (`SherpaTtsSpeaker.available` đọc `filesPresent`, không tin một cờ nào), nên xoá tệp là lượt nói sau
     * tự lùi về máy đọc của hệ thống. Gọi nhầm `release()` của đường NGHE ở đây là gỡ cái tai khi người ta bảo
     * gỡ cái miệng.
     */
    private fun removeTts(status: TextView, action: TextView) {
        action.isEnabled = false
        background {
            VoiceModelStore.remove(context, ttsPack)
            status.post { status.text = ttsStatusText() }
            action.post { action.isEnabled = true; action.text = ttsActionLabel() }
        }
    }

    private fun ttsReady(): Boolean = VoiceModelStore.isReady(context, ttsPack)

    private fun ttsActionLabel(): String = context.getString(
        when {
            !ttsReady() -> R.string.kachi_voice_tts_get
            VoiceModelStore.needsUpdate(context, ttsPack) -> R.string.kachi_voice_tts_update
            else -> R.string.kachi_voice_tts_remove
        },
    )

    private fun ttsStatusText(): String = if (!ttsReady()) {
        context.getString(R.string.kachi_voice_tts_absent, mb(ttsPack.totalBytes))
    } else if (VoiceModelStore.needsUpdate(context, ttsPack)) {
        context.getString(R.string.kachi_voice_tts_has_update, ttsPack.label)
    } else {
        context.getString(
            R.string.kachi_voice_tts_ready,
            ttsPack.label,
            ttsPack.files.size,
            mb(VoiceModelStore.sizeOnDisk(context, ttsPack)),
        )
    }

    // ── Hai công tắc (R4 · T9) ───────────────────────────────────────────────────────────────────

    /**
     * *"Đọc phản hồi bằng giọng"* (mặc định BẬT) + *"Ưu tiên giọng offline"* (mặc định TẮT).
     *
     * Cả hai đi qua `deps.bridge` như mọi khoá THEO XE khác (`voiceMicPill` · `headlessAutostart`): tầng vẽ của
     * launcher **không mở cửa riêng vào nơi lưu bền**, và một ngoại lệ là chỗ ngoại lệ thứ hai bắt đầu.
     */
    private fun speakToggles(body: LinearLayout) {
        body.addView(rows.checkRow(
            on = deps.bridge.voiceSpeakReplies(),
            title = context.getString(R.string.kachi_voice_speak_title),
            sub = context.getString(R.string.kachi_voice_speak_sub),
        ) { on -> deps.bridge.setVoiceSpeakReplies(on) })
        body.addView(rows.checkRow(
            on = deps.bridge.voicePreferOffline(),
            title = context.getString(R.string.kachi_voice_offline_title),
            sub = context.getString(R.string.kachi_voice_offline_sub),
        ) { on -> deps.bridge.setVoicePreferOffline(on) })
    }


    // ── chữ ──────────────────────────────────────────────────────────────────────────────────────

    /** Chữ cho một bước cài; [done] trả câu trạng thái của **đúng hàng** đang chạy (hai hàng, một bộ chữ bước). */
    private fun stepText(step: VoiceModelStore.Step, done: () -> String): String = when (step) {
        is VoiceModelStore.Step.Downloading ->
            if (step.percent < 0) context.getString(R.string.kachi_voice_model_downloading_unknown)
            else context.getString(R.string.kachi_voice_model_downloading, step.percent)
        VoiceModelStore.Step.Verifying -> context.getString(R.string.kachi_voice_model_verifying)
        VoiceModelStore.Step.Extracting -> context.getString(R.string.kachi_voice_model_extracting)
        is VoiceModelStore.Step.Done -> done()
        is VoiceModelStore.Step.Failed -> context.getString(R.string.kachi_voice_model_failed, step.reason)
    }

    /** Byte → "32 MB". Một chỗ đổi ⇒ mọi câu chữ nói cùng một đơn vị. */
    private fun mb(bytes: Long): String = "${(bytes + HALF_MB) / MB} MB"

    private companion object {
        const val MB = 1024L * 1024L
        const val HALF_MB = MB / 2
    }
}
