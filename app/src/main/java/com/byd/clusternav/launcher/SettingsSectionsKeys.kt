package com.byd.clusternav.launcher

import android.content.Context
import android.view.KeyEvent
import android.widget.LinearLayout
import com.byd.clusternav.R
import com.byd.clusternav.launcher.camera.CameraDemand
import com.byd.clusternav.voicekey.KeySourceKind
import com.byd.clusternav.voicekey.KeySourceProbes
import com.byd.clusternav.voicekey.KeySourceVerdict

/**
 * Nhóm **"Phím vô-lăng"** (IA v2 §4.1 nhóm 7) — công tắc nhận nút vật lý · trạng thái dịch vụ Hỗ trợ · danh sách
 * gán (xoá từng dòng) · thêm gán · học phím mới · danh sách nút tự học.
 *
 * ## Hai danh sách phải DỰNG LẠI được, nên chúng nằm trong hai khối con
 * Xoá một gán / học thêm một nút làm **đổi nội dung danh sách**, mà trang Cài đặt thì được nhớ lại
 * ([SettingsPanel.pages]) nên nó không tự dựng lại. Mỗi danh sách vì thế là một `LinearLayout` con riêng
 * ([bindingList] / [buttonList]) và chỉ khối đó được dựng lại — giữ nguyên chỗ đang cuộn, đúng lý do R1 bắt vỏ
 * bảng giữ chính thực thể `ScrollView`.
 *
 * ## Nhãn NÚT và nhãn ĐÍCH tra ở đâu
 * Cầu trả **mã** ([ButtonOption] `code` + `customName?`, [TargetOption] `spec` + `appLabel?`), không trả câu —
 * xem KDoc [ClusterNavBridge]. Quy ước ở đây:
 *  - nút **preset** (`customName == null`) ⇒ tra tài nguyên theo mã ([presetLabel]); nút **tự học** ⇒ hiện đúng
 *    tên người dùng đã đặt (chữ của họ, không dịch);
 *  - đích là **app** (`appLabel != null`) ⇒ tên do `PackageManager` dịch; đích **sentinel** ⇒ tra tài nguyên
 *    theo **thứ tự khai** của cầu ([sentinelLabel]) — xem KDoc hàm đó để biết vì sao không so chuỗi `__ASSIST__`.
 */
class SettingsKeysSection(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {

    private val bridge get() = deps.bridge

    private lateinit var statusRow: SettingsRows.StatusRow
    private val bindingList = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val buttonList = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    /**
     * Phiên "học phím" của **section này** đang mở hay không — xem [learn]/[dispose].
     *
     * Có cờ chứ không gọi [ClusterNavBridge.stopLearn] vô điều kiện lúc đóng bảng: `stopLearn` gỡ listener của
     * bus DÙNG CHUNG và hạ cờ `voicekey_learn`. Gọi khi mình chưa từng học là cướp phiên học của chỗ khác (màn
     * nâng cao cũng dùng đúng bus đó) — đúng họ lỗi "lệnh không có phạm vi tường minh" của CLAUDE.md §4.
     */
    private var learning = false

    fun build(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_sec_keys)))
        body.addView(rows.checkRow(
            on = bridge.voiceKeyEnabled(),
            title = context.getString(R.string.kachi_keys_enabled_title),
            sub = context.getString(R.string.kachi_keys_enabled_sub),
        ) { on -> bridge.setVoiceKeyEnabled(on) { refreshStatus() } })
        statusRow = rows.statusRow(KachiTheme.MUT2, "")
        body.addView(statusRow.view)
        refreshStatus()
        body.addView(rows.button(context.getString(R.string.kachi_keys_check)) {
            bridge.checkFix { refreshStatus() }
        })

        body.addView(rows.subHeader(context.getString(R.string.kachi_sub_keys_bindings)))
        body.addView(bindingList)
        rebuildBindings()
        body.addView(rows.button(context.getString(R.string.kachi_keys_add)) { addBinding() })

        body.addView(rows.subHeader(context.getString(R.string.kachi_sub_keys_custom)))
        body.addView(buttonList)
        rebuildButtons()
        body.addView(rows.button(context.getString(R.string.kachi_keys_learn)) { learn() })
    }

    // ── Trạng thái dịch vụ ───────────────────────────────────────────────────────────────────────

    /**
     * Ba trạng thái, ba màu — luật ba ca nằm ở `:core` ([ClusterNavSettingsModel.voiceKeyStatus] / cầu trả
     * [VoiceKeyStatus]); chỗ này chỉ dịch mã sang chữ + màu.
     *
     * "MẤT KẾT NỐI" là ca hay gặp nhất sau khi khởi động lại xe (service ENABLED trong setting nhưng chưa
     * bound), nên câu của nó **nói luôn cách chữa**: bấm nút Kiểm tra / Sửa ngay ngay phía dưới.
     */
    private fun refreshStatus() {
        val (colour, text) = when (bridge.voiceKeyStatus()) {
            VoiceKeyStatus.OFF -> KachiTheme.MUT2 to R.string.kachi_keys_status_off
            VoiceKeyStatus.ACTIVE -> KachiTheme.GREEN to R.string.kachi_keys_status_active
            VoiceKeyStatus.DISCONNECTED -> KachiTheme.RED to R.string.kachi_keys_status_broken
        }
        statusRow.update(colour, context.getString(text))
    }

    // ── Danh sách GÁN ────────────────────────────────────────────────────────────────────────────

    /**
     * ⚠ [SOÁT SENIOR 2026-09-13] Hai bảng tra đọc **một lần** cho cả danh sách, không một lần mỗi dòng.
     *
     * [ĐO] `ClusterCast.listInstalledApps` (nguồn của `bridge.targetOptions()`) chạy `queryIntentActivities` rồi
     * `loadLabel` + `loadIcon` cho **mọi** app có launcher. Bản trước gọi `buttonLabel`/`targetLabel` trong vòng
     * lặp, mà mỗi hàm đó lại gọi lại `bridge.buttonOptions()`/`targetOptions()` ⇒ N dòng gán = **2N lượt duyệt
     * toàn bộ app đã cài, trên luồng vẽ**, mỗi lần xoá một dòng lại làm lại. Trên đầu xe đó là đơ thấy được.
     */
    private fun rebuildBindings() {
        bindingList.removeAllViews()
        val bindings = bridge.bindings()
        if (bindings.isEmpty()) {
            bindingList.addView(rows.note(context.getString(R.string.kachi_keys_no_bindings)))
            return
        }
        val buttons = bridge.buttonOptions()
        val targets = bridge.targetOptions()
        bindings.forEach { binding ->
            bindingList.addView(rows.listRow(
                title = buttonLabel(binding.keyCode, binding.source, buttons),
                sub = targetLabel(binding.targetSpec, targets),
                actionLabel = context.getString(R.string.kachi_delete),
            ) {
                // 2.88 · R4: xoá đúng (mã, nguồn) của dòng này — dòng cùng mã khác nguồn giữ nguyên.
                bridge.removeBinding(binding.keyCode, binding.source)
                rebuildBindings()
            })
        }
    }

    /**
     * "Thêm gán" = ba bước: **chọn nút** → **loại đích** → **việc**. Huỷ giữa chừng không ghi gì: dòng gán chỉ được ghi
     * trong bước CUỐI.
     *
     * Từng bước một danh sách một cột chứ không một màn hai cột: trên xe, một danh sách dài một cột bấm dễ hơn hẳn; và
     * [ĐO] màn cũ dùng hai `Spinner` cạnh nhau — cùng số bước, nhưng `Spinner` không nói ra là mình bấm được.
     *
     * QA-301 D3 (spec §4.3 #8) — cả chuỗi là MỘT hộp [KachiDialogs.steps]: thanh bước 1-2-3, *"← Quay lại"* + phím Back lùi
     * một bước (bản trước: 3 hộp chồng nhau, Back ở bước 3 đóng cả chuỗi). Dữ liệu + đích của từng bước giữ nguyên.
     */
    private fun addBinding() {
        val buttons = bridge.buttonOptions()
        // KACHI-301 R13 · #8 — dòng phụ của mỗi nút = đích ĐANG gán cho đúng (mã, nguồn) đó (cùng phép tra của danh sách gán).
        // `targetOptions()` (duyệt mọi app) chỉ chạy khi thật có dòng gán — `by lazy`, xem KDoc [rebuildBindings].
        val bindings = bridge.bindings()
        val targets by lazy { bridge.targetOptions() }
        val first = KachiDialogs.Step(
            context.getString(R.string.kachi_keys_pick_button),
            buttons.map { o ->
                val bound = bindings.filter { it.keyCode == o.code && it.source == o.source }
                    .joinToString(SUB_SEP) { targetLabel(it.targetSpec, targets) }
                PickItem(optionLabel(o), subtitle = bound.ifEmpty { null }?.let { context.getString(R.string.kmisc_keys_bound, it) })
            },
            context.getString(R.string.kachi_keys_no_buttons),
        ) { buttonIndex ->
            // 2.88 · R2: nút học kèm nguồn ⇒ dòng gán theo nguồn (mục preset / nút không nguồn ⇒ `source` null như 2.87).
            val option = buttons[buttonIndex]
            targetStep { spec ->
                bridge.addBinding(option.code, spec, option.source)
                rebuildBindings()
            }
        }
        KachiDialogs.steps(context, STEP_COUNT, first) { n, total -> context.getString(R.string.kmisc_keys_step, n, total) }
    }

    /**
     * FIX286 · R-KC — bước 2 chọn **loại đích**: *"Ứng dụng · trợ lý"* (danh sách cũ, y nguyên) hoặc một NHÓM nút xe;
     * nhóm ⇒ bước 3 chọn việc (*"Gió +1"*, *"Bật Kính lái"*…). Nhóm + việc SINH từ `ControlRegistry` qua
     * [KeyCtlTargets.groups] (`:core`) — không chép tay, thêm nút vào registry là có mặt ở đây.
     */
    private fun targetStep(onSpec: (String) -> Unit): KachiDialogs.Step {
        val groups = KeyCtlTargets.groups()
        // 2.93 · CAMERA-ON-DEMAND — loại đích thứ hai *"Camera theo yêu cầu"* (owner 06/10 *"trigger từ bind phím vật lý"*).
        return KachiDialogs.Step(
            context.getString(R.string.kachi_keys_pick_kind),
            listOf(
                PickItem(context.getString(R.string.kachi_keys_kind_apps), icon = R.drawable.ic_grid),
                PickItem(context.getString(R.string.kachi_keys_kind_camera), icon = R.drawable.ic_cam),
            ) + groups.map { g ->
                // Icon nhóm: cùng tên icon `:core` khai cho nhóm khả năng (nhóm suy từ Domain không có ⇒ 0; khuôn chừa ô).
                val icon = CapabilityGroups.ALL.firstOrNull { it.id == g.id }?.let { KachiTheme.iconRes(it.icon) } ?: 0
                PickItem(g.displayLabel, subtitle = previewOf(g), icon = icon)
            },
            context.getString(R.string.kachi_keys_no_targets),
        ) { kind ->
            when (kind) {
                0 -> appStep(onSpec)
                1 -> cameraStep(onSpec)
                else -> controlStep(groups[kind - 2], onSpec)
            }
        }
    }

    /**
     * 2.93 — năm đích camera: bốn camera (mỗi phím = BẬT/TẮT camera ấy — owner *"các nút đều là toggle"*) + *Tắt camera*.
     * Danh sách + mã bền sinh từ `:core` [CameraDemand.KEY_OPS] / [CameraDemand.keySpec] — không chép tay mã nào.
     */
    private fun cameraStep(onSpec: (String) -> Unit): KachiDialogs.Step {
        val ops = CameraDemand.KEY_OPS
        return KachiDialogs.Step(
            context.getString(R.string.kachi_keys_pick_camera),
            ops.map { PickItem(camOpLabel(it), icon = camOpIcon(it)) },
            context.getString(R.string.kachi_keys_no_targets),
        ) { i -> CameraDemand.keySpec(ops[i])?.let(onSpec); null }
    }

    /** Nhãn một đích camera: *"Camera sau — bật/tắt"* · *"Tắt camera"*. */
    private fun camOpLabel(op: CameraDemand.Op): String = when (op) {
        is CameraDemand.Op.Toggle ->
            context.getString(R.string.kachi_key_cam_toggle, CameraSettingsLabels.cameraName(context, op.which))
        else -> context.getString(R.string.kachi_key_cam_off)
    }

    /**
     * Đích app/trợ lý. KHÔNG icon app: bung icon MỌI app trên luồng vẽ lúc mở bước là đúng cái đơ mà KDoc [rebuildBindings]
     * cấm; chỉ mục trợ lý (sentinel, `appLabel == null`) mang icon micro — tài nguyên sẵn, 0 lượt `PackageManager`.
     */
    private fun appStep(onSpec: (String) -> Unit): KachiDialogs.Step {
        val targets = bridge.targetOptions()
        return KachiDialogs.Step(
            context.getString(R.string.kachi_keys_pick_target),
            targets.map { PickItem(targetOptionLabel(it, targets), icon = if (it.appLabel == null) R.drawable.ic_mic else 0) },
            context.getString(R.string.kachi_keys_no_targets),
        ) { targetIndex -> onSpec(targets[targetIndex].spec); null }
    }

    private fun controlStep(group: KeyCtlGroup, onSpec: (String) -> Unit): KachiDialogs.Step = KachiDialogs.Step(
        context.getString(R.string.kachi_keys_pick_action, group.displayLabel),
        group.targets.map { PickItem(KeyCtlTargets.displayLabel(it)) },
        context.getString(R.string.kachi_keys_no_targets),
    ) { i -> onSpec(group.targets[i].spec); null }

    /** Hai việc đầu của nhóm làm dòng phụ (*"Gió +1 · Gió −1 …"*) — người lái nhận ra nhóm bằng việc, không chỉ bằng tên. */
    private fun previewOf(group: KeyCtlGroup): String {
        val head = group.targets.take(PREVIEW_N).joinToString(SUB_SEP) { KeyCtlTargets.displayLabel(it) }
        return if (group.targets.size > PREVIEW_N) head + ELLIPSIS else head
    }

    /** Icon một đích camera: CÙNG icon vị trí của nút thanh xe ([LauncherActions], luật U7) — không bảng thứ hai. */
    private fun camOpIcon(op: CameraDemand.Op): Int {
        val id = if (op is CameraDemand.Op.Toggle) LauncherActions.cameraId(op.which) else LauncherActions.CAM_OFF
        return LauncherActions.byId(id)?.let { KachiTheme.iconRes(it.icon) } ?: 0
    }

    // ── Nút TỰ HỌC ───────────────────────────────────────────────────────────────────────────────

    private fun rebuildButtons() {
        buttonList.removeAllViews()
        val custom = bridge.customButtons()
        if (custom.isEmpty()) {
            buttonList.addView(rows.note(context.getString(R.string.kachi_keys_no_custom)))
            return
        }
        custom.forEach { button ->
            buttonList.addView(rows.listRow(
                title = button.name,
                sub = context.getString(R.string.kachi_key_code, button.keyCode),
                actionLabel = context.getString(R.string.kachi_delete),
            ) {
                bridge.removeCustomButton(button.keyCode, button.source)   // 2.88 · R4: đúng (mã, nguồn)
                rebuildButtons()
            })
        }
    }

    /**
     * "Học phím mới": mở bus học → người dùng bấm nút vật lý → hỏi tên → lưu.
     *
     * ⚠ **Tên lưu phải giữ khuôn `"<tên> (mã <code>)"` của màn cũ** — [ĐO] KDoc
     * `ClusterNavBridge.addCustomButton`: màn cũ tự ghép chuỗi đó rồi lưu nguyên, còn cầu không được mang chữ
     * nên khuôn ấy nay là tài nguyên `kachi_key_custom_name` của launcher. Lệch khuôn thì cùng một nút hiện hai
     * tên khác nhau ở hai màn.
     *
     * 2.88 · R1 — lúc bấm Lưu đọc lại số đo nguồn của CHÍNH lần học ([learnedSource]): ra nguồn rõ (núm / vô-lăng) ⇒
     * lưu nút kèm nguồn, tên theo khuôn `kachi_key_custom_name_src` (`"<tên> (mã <code> · <nguồn>)"`); chưa xong / hụt /
     * giá trị lạ / phím không đo ⇒ lưu y như 2.87.
     *
     * [ClusterNavBridge.stopLearn] gọi ngay sau khi nhận được mã: bus chỉ giữ **một** listener, để treo là
     * phiên Settings sau (hoặc màn cũ) không học được nữa.
     *
     * ⚠⚠ [SOÁT SENIOR 2026-09-13] Và phải gỡ cả khi người dùng **KHÔNG** bấm nút nào: mở "Học phím mới" rồi đóng
     * Cài đặt là ca thường gặp nhất (bấm nhầm, hoặc không tìm ra nút trên vô-lăng). Không gỡ thì
     * `VoiceKeyLearnBus` giữ mãi một listener trỏ về `ui` của Activity ĐÃ HUỶ (rò cả màn hình), và cờ
     * `voicekey_learn` nằm lại `true` — tức xe vẫn ở chế độ học sau khi người dùng đã bỏ đi. Đường gỡ là
     * [dispose], được [SettingsSections.dispose] gọi khi bảng rời khỏi cây view.
     */
    private fun learn() {
        learning = true
        bridge.startLearn { code ->
            learning = false
            bridge.stopLearn()
            SettingsDialogs.askName(
                context,
                context.getString(R.string.kachi_keys_learn_name),
                bridge.defaultLearnName(code),
                // L7 tầng 1 — chỉ HIỆN số đo nguồn phím (mã · scan · thiết bị · nguồn HAL) để chụp màn hình; tên/gán y nguyên.
                detail = { tv -> KeySourceDetailText.bind(tv, code) { bridge.learnedKeySource(code) } },
            ) { name ->
                val source = learnedSource(code)
                val label = if (source == null) context.getString(R.string.kachi_key_custom_name, name, code)
                else context.getString(R.string.kachi_key_custom_name_src, name, code, KeySourceDetailText.kindLabel(context, source))
                bridge.addCustomButton(label, code, source)
                rebuildButtons()
            }
        }
    }

    /**
     * 2.88 · R1 — nguồn RÕ của lần học [code] (dòng nhật ký tầng 1 của lần bấm vừa học), hoặc `null` khi chưa đo xong /
     * đọc hụt / giá trị ngoài bảng / phím không thuộc đầu dò. Cùng [KeySourceProbes.verdict] mà dòng chi tiết hiện, nên
     * nút được lưu kèm nguồn ⟺ hộp vừa ghi đúng nguồn đó.
     */
    private fun learnedSource(code: Int): KeySourceKind? =
        (KeySourceProbes.verdict(bridge.learnedKeySource(code)?.reading) as? KeySourceVerdict.Source)?.kind

    /**
     * Bảng Cài đặt đóng ⇒ đóng nốt phiên học còn treo.
     *
     * Chỉ làm gì khi CHÍNH section này đang học ([learning]) — xem KDoc cờ đó. Gọi nhiều lần an toàn.
     */
    fun dispose() {
        if (!learning) return
        learning = false
        bridge.stopLearn()
    }

    // ── Nhãn ─────────────────────────────────────────────────────────────────────────────────────

    /** Nhãn một dòng trong danh sách chọn NÚT. */
    private fun optionLabel(option: ButtonOption): String =
        option.customName ?: presetLabel(option.code)

    /**
     * Nhãn của một (mã phím, nguồn) ĐÃ GÁN — tra trong cùng bảng mà hộp chọn dùng, để hai chỗ không hiện khác nhau.
     *
     * [buttons] truyền VÀO (không tự gọi `bridge.buttonOptions()`): xem KDoc [rebuildBindings].
     *
     * 2.88 · R4: tra theo (mã, nguồn). Dòng có nguồn mà nút tự học của nó đã bị xoá ⇒ vẫn ghi rõ nguồn
     * (`kachi_key_custom_name_src` với tên hằng framework) để hai dòng núm / vô-lăng cùng mã không trông giống hệt nhau.
     */
    private fun buttonLabel(code: Int, source: KeySourceKind?, buttons: List<ButtonOption>): String =
        // 2.93 · KEY-LABEL-PRESET-SHADOW — nút tự học trùng mã thắng preset (tra cũ lấy mục khớp ĐẦU TIÊN = preset).
        buttons.labelOwner(code, source)?.let { optionLabel(it) }
            ?: source?.let {
                context.getString(
                    R.string.kachi_key_custom_name_src,
                    KeyEvent.keyCodeToString(code).removePrefix(KEYCODE_PREFIX), code, KeySourceDetailText.kindLabel(context, it),
                )
            }
            ?: presetLabel(code)

    /**
     * Tên của một mã phím preset. Mã lạ (nút tự học đã bị xoá, hoặc dữ liệu cũ) ⇒ ghép từ tên hằng của framework
     * + mã số: nó KHÔNG đẹp, nhưng nó **nhận ra được** — người dùng còn biết dòng nào để xoá.
     */
    private fun presetLabel(code: Int): String = when (code) {
        328 -> context.getString(R.string.kachi_key_mic_hold)
        231 -> context.getString(R.string.kachi_key_voice_assist)
        219 -> context.getString(R.string.kachi_key_assist)
        85 -> context.getString(R.string.kachi_key_play_pause)
        88 -> context.getString(R.string.kachi_key_prev)
        87 -> context.getString(R.string.kachi_key_next)
        79 -> context.getString(R.string.kachi_key_headset)
        5 -> context.getString(R.string.kachi_key_call)
        84 -> context.getString(R.string.kachi_key_search)
        else -> context.getString(
            R.string.kachi_key_unknown, KeyEvent.keyCodeToString(code).removePrefix(KEYCODE_PREFIX), code,
        )
    }

    /**
     * Nhãn một ĐÍCH: app thì dùng tên hệ thống dịch; sentinel thì tra tài nguyên.
     *
     * [targets] truyền VÀO để [sentinelLabel] khỏi gọi lại `bridge.targetOptions()` — xem KDoc [rebuildBindings].
     */
    private fun targetOptionLabel(option: TargetOption, targets: List<TargetOption>): String =
        option.appLabel ?: sentinelLabel(option.spec, targets)

    /**
     * [targets] truyền VÀO (không tự gọi `bridge.targetOptions()`): xem KDoc [rebuildBindings].
     * FIX286 · R-KC — đích nút xe (`ctl:…`) ⇒ nhãn sinh từ registry (*"Gió +1"*); mã hỏng/nút không còn ⇒ nguyên chuỗi
     * (vẫn nhận ra dòng để xoá).
     */
    private fun targetLabel(spec: String, targets: List<TargetOption>): String = when {
        KeyCtlTargets.isCtl(spec) -> KeyCtlTargets.displayLabelOf(spec)
        // 2.93 — đích camera (`cam:…`): nhãn theo mã; mã hỏng ⇒ nguyên chuỗi (vẫn nhận ra dòng để xoá).
        CameraDemand.isKey(spec) -> CameraDemand.parseKey(spec)?.let { camOpLabel(it) } ?: spec
        else -> targets.firstOrNull { it.spec == spec }?.let { targetOptionLabel(it, targets) } ?: spec
    }

    /**
     * Tên các mục đặc biệt (ba mục cũ + *"Kachi nghe"* của V1 pha NGHE), tra theo **THỨ TỰ KHAI** của `ClusterNavBridge.targetOptions()` chứ không so chuỗi
     * `"__ASSIST__"`.
     *
     * ## Vì sao không so chuỗi
     * Ba sentinel là hằng của `Prefs` (phía ClusterNav). Viết lại chúng ở đây là **bản sao thứ hai của một khoá
     * lưu bền** — đổi tên hằng bên kia thì nhãn ở đây im lặng lùi về hiện nguyên chuỗi `__ASSIST__` cho người
     * dùng, và không bài canh nào thấy. Còn `import Prefs` thì section chạm thẳng lớp lưu của ClusterNav, đúng
     * thứ N2 cấm (`ClusterNavSettingsWiringContractTest`). Thứ tự khai thì cầu **đã ghim bằng KDoc** và
     * `ClusterNavKeysContractTest` canh — mượn lại phép ghim đó là rẻ nhất và không tạo bản sao nào.
     */
    private fun sentinelLabel(spec: String, targets: List<TargetOption>): String {
        val sentinels = targets.filter { it.appLabel == null }.map { it.spec }
        return when (sentinels.indexOf(spec)) {
            0 -> context.getString(R.string.kachi_key_target_assist)
            1 -> context.getString(R.string.kachi_key_target_gemini)
            2 -> context.getString(R.string.kachi_key_target_recognizer)
            3 -> context.getString(R.string.kachi_key_target_kachi_voice)
            else -> spec
        }
    }

    private companion object {
        /** Tiền tố hằng mã phím của framework (`KEYCODE_VOICE_ASSIST`) — cắt đi cho dòng chữ đỡ ồn. */
        const val KEYCODE_PREFIX = "KEYCODE_"

        /** Chuỗi gán phím: nút → loại đích → việc (R13 · #8) — số ô của thanh bước. */
        const val STEP_COUNT = 3

        /** Số việc xem trước dưới tên nhóm. */
        const val PREVIEW_N = 2

        /** Ký hiệu nối/cắt dòng phụ — dấu câu, không phải chữ cần dịch. */
        const val SUB_SEP = " · "
        const val ELLIPSIS = " …"
    }
}
