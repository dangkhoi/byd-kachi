package com.byd.clusternav.launcher.voice

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.BuildConfig
import com.byd.clusternav.Prefs
import com.byd.clusternav.R
import com.byd.clusternav.setVoiceKeepLog
import com.byd.clusternav.launcher.DevMode
import com.byd.clusternav.launcher.SettingsDeps
import com.byd.clusternav.launcher.SettingsRows
import com.byd.clusternav.launcher.setVoiceFeedbackVoice
import com.byd.clusternav.launcher.setVoicePreferOffline
import com.byd.clusternav.launcher.setVoiceSpeakReplies
import com.byd.clusternav.launcher.voiceFeedbackVoice
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
        lightModelRows(body)
        attributionRows(body)
        ttsRow(body)
        ClipVoiceRow(context, rows).build(body)
        speakToggles(body)
        logRows(body)
        // V3 · R7 — mục *"Hỏi xác nhận trước khi chạy"* + nguồn micro. Lớp RIÊNG (trần 500 dòng, CLAUDE.md §4.1)
        // nhưng dựng **ở đây** để trang Cài đặt vẫn có đúng một khối "Giọng nói" liền mạch.
        VoiceConfirmSettings(context, rows, deps).build(body)
    }

    // ── Cái TAI: mô hình nhận dạng (R9) ──────────────────────────────────────────────────────────

    private fun modelRow(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_voice_model_title)))
        val status = rows.note(modelStatusText()) as TextView
        body.addView(status)

        val action = rows.button(modelActionLabel()) {} as TextView
        action.setOnClickListener {
            if (VoiceModelStore.isReady(context)) removeModel(status, action) else installModel(status, action)
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
        if (VoiceModelStore.isReady(context)) R.string.kachi_voice_model_remove else R.string.kachi_voice_model_get,
    )

    private fun modelStatusText(): String {
        val model = VoiceModelStore.selected(context)
        return if (!VoiceModelStore.isReady(context)) {
            context.getString(R.string.kachi_voice_model_absent, mb(model.totalBytes))
        } else {
            context.getString(
                R.string.kachi_voice_model_ready,
                model.label,
                model.files.size,
                mb(VoiceModelStore.sizeOnDisk(context)),
            )
        }
    }

    // ── H6 · Đổi sang MÔ HÌNH NHẸ, và gỡ bản nặng — HAI hàng, hai quyết định ─────────────────────

    /**
     * ═══ *"Chuyển sang mô hình nhẹ"* + *"Gỡ bản nặng"* — vì sao là hai nút chứ không một ═════════════════
     *
     * [ĐO xe 2026-09-16] `docs/diagnostics/oncar-trace-2026-09-16.md` §1: RSS của Kachi **537 MB** (native heap
     * 477 MB = encoder fp32), máy còn **56–94 MB** trống, lần bật mic đầu mất **15 giây**. [ĐO bridge 2026-09-16]
     * xe của owner trả `bytes: 270408094` ⇒ đúng bản fp32. [ĐO host] 25 tệp WAV: int8 ra **21/25 đúng ý định = y
     * hệt fp32**. Tức đây là một lượt đổi gần như không mất gì và được lại vài trăm MB.
     *
     * ## Hai nút, vì đó là hai rủi ro ngược nhau xảy ra ở hai thời điểm khác nhau
     *  1. **Tải + chuyển** là một lượt 74 MB qua mạng 4G của xe. Nó phải xong **hoàn toàn** rồi mới đổi lựa chọn:
     *     [VoiceModelStore.install] chỉ báo `Done` sau khi cả bốn tệp đã qua sha256 + đã đổi tên nguyên tử vào
     *     thư mục thật. Đổi `select` sớm hơn (vd ngay khi bấm) là để một chiếc xe mất sóng giữa chừng trỏ vào một
     *     thư mục rỗng ⇒ lần bấm mic sau ra *"chưa tải mô hình"*, mà bản cũ vẫn còn nguyên trên đĩa.
     *  2. **Gỡ bản nặng** là một lượt xoá **không hoàn tác được** (tải lại mất 266 MB). Người dùng chọn lúc nào
     *     thu lại chỗ — không bao giờ tự xoá sau khi đổi. Đó cũng là đường lùi duy nhất nếu bản nhẹ nghe tệ hơn
     *     trên cabin thật ([CHƯA BIẾT] — chưa ai đo tốc độ giải mã int8 trên ARM của đầu xe này).
     *
     * Hàng CHỈ hiện khi danh mục thật sự có một gói nhẹ hơn gói đang chọn ([SherpaModelCatalog.lighterThan]) —
     * không viết cứng tên mô hình nào ở tầng vẽ (CLAUDE.md §7).
     */
    private fun lightModelRows(body: LinearLayout) {
        val current = VoiceModelStore.selected(context)
        val light = SherpaModelCatalog.lighterThan(current) ?: return
        val status = rows.note(lightStatusText(current, light)) as TextView
        body.addView(status)
        // Ghi chú *"máy đã bỏ qua lượt nạp sẵn vì thiếu RAM"* — một DỮ KIỆN, không phải một lượt tự đổi mô hình.
        VoiceEngine.lastPreloadSkip?.let {
            body.addView(rows.note(context.getString(R.string.kachi_voice_model_preload_skipped, it)))
        }
        val action = rows.button(switchLabel(light)) {} as TextView
        action.setOnClickListener { switchToLight(light, status, action) }
        body.addView(action)
        // Nút GỠ chỉ có nghĩa khi bản nặng vẫn còn nằm trên đĩa.
        if (VoiceModelStore.isReady(context, current) && current.id != light.id) {
            val dropLabel = context.getString(R.string.kachi_voice_model_drop_heavy, current.label)
            val drop = rows.button(dropLabel) {} as TextView
            drop.setOnClickListener { dropHeavy(current, light, status, drop) }
            body.addView(drop)
        }
    }

    private fun switchToLight(
        light: SherpaModelCatalog.SherpaModel,
        status: TextView,
        action: TextView,
    ) {
        action.isEnabled = false
        action.text = context.getString(R.string.kachi_voice_model_working)
        background {
            VoiceModelStore.install(context, light) { step ->
                status.post { status.text = stepText(step) { lightDoneText(light) } }
                if (step is VoiceModelStore.Step.Done) {
                    // ⚠ CHỈ ở nhánh `Done` — xem KDoc [lightModelRows] rủi ro (1). `Done` tới sau khi cả bốn tệp
                    // đã qua sha256 và thư mục đã đổi tên xong, nên từ đây `isReady` chắc chắn đúng.
                    VoiceEngine.release()
                    VoiceModelStore.select(context, light.id)
                }
                if (step is VoiceModelStore.Step.Done || step is VoiceModelStore.Step.Failed) {
                    action.post { action.isEnabled = true; action.text = switchLabel(light) }
                }
            }
        }
    }

    private fun dropHeavy(
        heavy: SherpaModelCatalog.SherpaModel,
        light: SherpaModelCatalog.SherpaModel,
        status: TextView,
        drop: TextView,
    ) {
        drop.isEnabled = false
        background {
            // Đang CHỌN bản nặng mà xoá nó là tự tay làm câm đường nghe ⇒ từ chối, nói rõ phải đổi trước. Kiểm ở
            // tầng THI HÀNH (không chỉ ẩn nút): hàng được dựng một lần lúc mở Cài đặt, còn lựa chọn thì đổi được
            // ở chính màn này giữa chừng (CLAUDE.md §5 — guard cứng đặt ở tầng thi hành).
            if (VoiceModelStore.selected(context).id == heavy.id) {
                status.post { status.text = context.getString(R.string.kachi_voice_model_drop_blocked, light.label) }
                drop.post { drop.isEnabled = true }
                return@background
            }
            VoiceModelStore.remove(context, heavy)
            status.post { status.text = lightStatusText(VoiceModelStore.selected(context), light) }
            drop.post { drop.isEnabled = true }
        }
    }

    /** Nhãn nút *Chuyển sang mô hình nhẹ* — **một** chỗ dựng, vì nó được đặt lại sau mỗi lượt cài xong/hỏng. */
    private fun switchLabel(light: SherpaModelCatalog.SherpaModel): String =
        context.getString(R.string.kachi_voice_model_switch_light, mb(light.totalBytes))

    /** Một dòng: mô hình đang dùng · cỡ của nó · RAM còn trống — ba con số quyết định có nên đổi hay không. */
    private fun lightStatusText(current: SherpaModelCatalog.SherpaModel, light: SherpaModelCatalog.SherpaModel): String =
        context.getString(
            R.string.kachi_voice_model_current,
            current.label,
            mb(VoiceModelStore.sizeOnDisk(context, current).takeIf { it > 0 } ?: current.totalBytes),
            mb(freeRamBytes()),
            light.label,
            mb(light.totalBytes),
        )

    private fun lightDoneText(light: SherpaModelCatalog.SherpaModel): String =
        context.getString(R.string.kachi_voice_model_switched, light.label, mb(light.totalBytes))

    /** RAM còn trống của **hệ thống** (không phải của tiến trình) — cùng con số [VoicePreloadPolicy] quyết bằng. */
    private fun freeRamBytes(): Long = runCatching {
        android.app.ActivityManager.MemoryInfo().also { mi ->
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.getMemoryInfo(mi)
        }.availMem
    }.getOrDefault(0L)

    // ── GHI CÔNG tác giả mô hình — nghĩa vụ của giấy phép, không phải một dòng trang trí ─────────

    /**
     * *"Về mô hình nghe"* — tên tác giả · giấy phép · URL, cho **mọi** gói trong danh mục đòi ghi công.
     *
     * ## Vì sao nó là một hàng THẬT trong Cài đặt, không phải một dòng trong README
     * Mô hình mặc định từ 1.69 mang **CC BY-NC-ND 4.0**, và `BY` nghĩa là ghi công **ở nơi người dùng thấy**. Một
     * dòng nằm trong `voice/README.md` của kho mã thì người ngồi trên xe không bao giờ đọc tới. Ba chỗ hiển thị
     * (hàng này · `state.voice_model` · README) đều đọc **cùng một** trường dữ liệu
     * ([SherpaModelCatalog.attributions]) nên chúng không thể lệch nhau.
     *
     * Duyệt theo danh mục chứ không viết cứng tên gói: thêm một gói CC BY nữa là hàng này tự dài ra (CLAUDE.md §7).
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

    // ── H2 · NHẬT KÝ LƯỢT NÓI: một ô tích + một nút xuất ─────────────────────────────────────────

    /**
     * ═══ *"Giữ nhật ký lượt nói"* (BẬT sẵn) + *"Xuất nhật ký voice"* ══════════════════════════════════════
     *
     * ## Câu chữ phải nói ra chỗ tiếng NẰM Ở ĐÂU, không chỉ nói tính năng làm gì
     * Đây là ô tích duy nhất trong cả app bật một thứ **ghi lại giọng người dùng**. Một dòng phụ kiểu *"giúp cải
     * thiện nhận dạng"* là đúng chức năng mà không trả lời câu người ta thật sự hỏi. Nên dòng phụ nói thẳng hai
     * việc: tiếng **chỉ lưu trên xe, không gửi đi**, và nó **tự xoá** sau 30 lượt / 30 MB. Cả hai đều là tính
     * chất đo được từ mã ([VoiceUtteranceLog]), không phải một lời hứa suông.
     *
     * Nút *Xuất* nén ra `Download/` để người ta cắm USB chép, hoặc gửi Zalo — **một cú bấm**, không hướng dẫn ai
     * gõ `adb` (CLAUDE.md §11). Nó đổi chữ thành đường dẫn thật khi xong: một nút im lặng sau vài giây nén là một
     * nút người ta sẽ bấm lần thứ hai.
     */
    private fun logRows(body: LinearLayout) {
        // UX-OVERHAUL · WP7 — nhật ký lượt nói + nút Xuất là **đồ ĐO** (owner: "công tắc diag-log + xuất-log" nằm
        // trong danh sách ẩn), nên cả khối đứng sau cổng [DevMode.unlocked]. Qua adb: đọc/ghi công tắc bằng
        // `prefs_set --es key voice_keep_log --es text true|false`, lấy nhật ký bằng `voice_dump`.
        //
        // ⚠ Chỉ ẩn BỀ MẶT, không đổi HÀNH VI: `voice_keep_log` vẫn mặc định BẬT và [VoiceUtteranceLog] vẫn ghi như
        // trước — tắt nó ở đây nữa thì buổi RE mở test-mode lên sẽ không còn nhật ký nào của những lượt nói TRƯỚC đó.
        if (!DevMode.unlocked(context)) return
        body.addView(rows.checkRow(
            on = VoiceUtteranceLog.enabled(context),
            title = context.getString(R.string.kachi_voice_log_title),
            sub = context.getString(R.string.kachi_voice_log_sub, VoiceUtteranceLog.MAX_ENTRIES),
        ) { on -> Prefs.setVoiceKeepLog(context, on) })
        val export = rows.button(context.getString(R.string.kachi_voice_log_export)) {} as TextView
        export.setOnClickListener {
            export.isEnabled = false
            export.text = context.getString(R.string.kachi_voice_model_working)
            background {
                // Nén hàng chục MB ⇒ luồng NỀN (cùng luật mọi hàng khác của lớp này); chạm view qua `post`.
                val r = VoiceUtteranceLog.exportZip(context)
                export.post {
                    export.isEnabled = true
                    export.text = when (r) {
                        is VoiceUtteranceLog.Export.Ok ->
                            context.getString(R.string.kachi_voice_log_exported, r.path, r.entries)
                        is VoiceUtteranceLog.Export.Failed ->
                            context.getString(R.string.kachi_voice_model_failed, r.reason)
                    }
                }
            }
        }
        body.addView(export)
    }

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
            if (ttsReady()) removeTts(status, action) else installTts(status, action)
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
        if (ttsReady()) R.string.kachi_voice_tts_remove else R.string.kachi_voice_tts_get,
    )

    private fun ttsStatusText(): String = if (!ttsReady()) {
        context.getString(R.string.kachi_voice_tts_absent, mb(ttsPack.totalBytes))
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
        feedbackVoiceRow(body)
    }

    /**
     * ═══ *"Giọng phản hồi giọng bé"* — chọn Piper (mặc định) hay "Giọng Kachi bé" (spec `kachi-voice-clone.html` T8) ══
     *
     * Một **ô tích**, không phải một dãy chọn: chỉ có hai giọng, và một trong hai là mặc định rõ ràng (Piper) —
     * đúng dạng bật/tắt. TẮT = Piper ([VoiceSpeakerSelector.FEEDBACK_PIPER], mặc định); BẬT = giọng bé
     * ([VoiceSpeakerSelector.FEEDBACK_CHILD]). Pref vẫn là **Int** (không phải Boolean) để còn chỗ cho giọng thứ
     * ba mai sau; ô tích chỉ là bề mặt của hai giá trị đầu.
     *
     * Câu phụ nói thẳng ba điều owner cần biết: mặc định Piper · giọng bé là **clip clone chưa hoàn hảo** · câu
     * lạ vẫn đọc bằng Piper (để không ai tưởng giọng bé đọc được mọi thứ). Đi qua `deps.bridge` như mọi khoá
     * THEO XE khác — tầng vẽ không mở cửa riêng vào nơi lưu bền.
     */
    private fun feedbackVoiceRow(body: LinearLayout) {
        body.addView(rows.checkRow(
            on = deps.bridge.voiceFeedbackVoice() == VoiceSpeakerSelector.FEEDBACK_CHILD,
            title = context.getString(R.string.kachi_voice_feedback_title),
            sub = context.getString(R.string.kachi_voice_feedback_sub),
        ) { on ->
            deps.bridge.setVoiceFeedbackVoice(
                if (on) VoiceSpeakerSelector.FEEDBACK_CHILD else VoiceSpeakerSelector.FEEDBACK_PIPER,
            )
        })
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
