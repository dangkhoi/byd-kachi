package com.byd.clusternav.launcher

import android.content.Context
import android.text.InputType
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * BA HỘP THOẠI dùng chung của màn Cài đặt: **chọn một mục** · **hỏi lại trước khi làm** · **hỏi một cái tên**.
 *
 * ## Vì sao gom một chỗ (IA v2 §4.4, cuối mục)
 * Bốn bề mặt mới của T4 đều cần đúng ba việc đó: nhóm *Chiếu cụm* chọn app (bốn chỗ: tự chiếu full/trái/phải +
 * chiếu ngay) và hỏi lại trước khi dọn sạch cụm; nhóm *Phím vô-lăng* chọn nút → chọn đích → đặt tên nút vừa học.
 * [ĐO] tổng cộng **9 chỗ gọi**. Mỗi chỗ tự dựng `AlertDialog.Builder` là 9 bản sao của cùng ba quyết định (nút
 * huỷ có hay không · danh sách rỗng thì hiện gì · bàn phím kiểu nào) — và bản thứ hai trở đi sẽ lệch, đúng bẫy
 * hai-bản-sao mà dự án đã trả giá nhiều lần.
 *
 * Spec §4.4 nói *"tái dùng danh sách app đã có của ngăn kéo trong một hộp thoại danh sách đơn giản; không dựng
 * picker mới"* — lớp này là "hộp thoại danh sách đơn giản" đó, và nó **không biết** danh sách của mình từ đâu ra.
 *
 * ## Danh sách RỖNG phải nói ra, không im lặng
 * [pick] với danh sách rỗng mà `show()` một hộp trắng thì người dùng bấm nút xong thấy… không có gì. Mọi ca rỗng
 * ở đây đều là ca THẬT (máy chưa cài app nào chiếu được; chưa học nút nào), nên nó hiện [emptyText] — cùng luật
 * *"cú bấm không có tác dụng thì phải NÓI lý do"* mà `TopStripPicker.toggle` và nút bố cục sẵn ở P9 đã lập ra.
 *
 * ## KACHI-301 R8 — khung nay là [KachiDialog], không còn `AlertDialog`
 * Chữ ký mọi hàm GIỮ NGUYÊN (0 chỗ gọi đổi); chỉ ruột đổi sang bốn khuôn [KachiDialogs] ⇒ nền/bo/chữ/nút theo bảng
 * Kachi Sáng/Tối thay vì khung BYD. `KachiDialogContractTest` đỏ nếu `AlertDialog.Builder` quay lại tệp này.
 */
internal object SettingsDialogs {

    /**
     * Chọn MỘT mục trong [labels]; [onPick] nhận **chỉ số** (không phải nhãn) để chỗ gọi tra lại vật thật của nó —
     * nhãn có thể trùng nhau (hai app cùng tên) còn chỉ số thì không.
     */
    fun pick(context: Context, title: String, labels: List<String>, emptyText: String, onPick: (Int) -> Unit) {
        KachiDialogs.pick(context, title, labels.map { PickItem(it) }, emptyText = emptyText, onPick = onPick)
    }

    /**
     * **Nói một điều rồi thôi** — không có gì để chọn, không có gì để huỷ (danh sách rỗng · sổ đã đầy).
     *
     * Tách ra khỏi [pick] (nó vốn chứa nguyên khối này) vì nay có chỗ gọi thứ hai: một cú bấm **không có tác
     * dụng** thì phải NÓI lý do — luật đã lập ở `TopStripPicker.toggle`. Để mỗi chỗ tự dựng một `AlertDialog`
     * thông báo là mở lại đúng cánh cửa mà KDoc lớp này đóng.
     */
    fun notice(context: Context, title: String, message: String) {
        KachiDialogs.notice(context, title, message)
    }

    /**
     * Hỏi lại trước một việc **không hoàn lại được** (dọn sạch cụm: force-stop app khác + reset VD cụm).
     *
     * Nút đồng ý mang nhãn do chỗ gọi cấp ([confirmLabel]) chứ không phải "OK": trên màn xe, nhãn nói ĐÚNG VIỆC
     * sắp xảy ra là lớp bảo vệ cuối cùng — "OK" thì người dùng đã quên mất mình vừa được hỏi gì.
     */
    fun confirm(
        context: Context,
        title: String,
        message: String,
        confirmLabel: String,
        onConfirm: () -> Unit,
    ) {
        KachiDialogs.confirm(context, title, message, confirmLabel, onConfirm = onConfirm)
    }

    /**
     * Như [confirm] nhưng nút từ chối cũng mang nhãn của chỗ gọi — FIX286 · PI3: sau khi nhập hồ sơ thì việc đã XONG,
     * không có gì để "Huỷ"; hai lựa chọn thật là *"Dùng hồ sơ này ngay"* / *"Để sau"*.
     */
    fun offer(
        context: Context,
        title: String,
        message: String,
        confirmLabel: String,
        dismissLabel: String,
        onConfirm: () -> Unit,
    ) {
        KachiDialogs.confirm(context, title, message, confirmLabel, cancelLabel = dismissLabel, onConfirm = onConfirm)
    }

    /**
     * Hỏi MỘT MỤC SỔ ĐỊA CHỈ: nhãn · địa chỉ · (tuỳ chọn) toạ độ — spec `docs/specs/kachi-voice-addresses.html` R1.
     *
     * ## Vì sao là hàm thứ tư ở đây chứ không phải một `AlertDialog` dựng trong section
     * KDoc lớp cấm bản dựng thứ hai, và lý do vẫn đúng nguyên: ba quyết định (nút huỷ, nhãn nút lưu, kiểu bàn
     * phím) phải giống mọi hộp khác. [askName] không dùng lại được vì nó chỉ có **một** ô — mà một mục địa chỉ
     * thiếu bất kỳ trường nào trong hai trường đầu thì vừa không gọi được bằng giọng, vừa không dẫn đi đâu.
     *
     * KACHI-301 R11 — ruột nay ở [SettingsPlaceDialog] (chip nhãn nhanh · toạ độ gập "Nâng cao" · kiểm tại chỗ qua
     * `PlaceForm`); hàm này giữ chữ ký để 0 chỗ gọi đổi.
     *
     * @param initial mục đang sửa, `null` = thêm mới (ba ô trống).
     * @param onDelete đường XOÁ mục đang sửa (`null` khi thêm mới ⇒ không có nút xoá).
     *   ⚠ Xoá nằm **trong hộp sửa** chứ không phải một nút thứ hai trên mỗi hàng danh sách: hàng danh sách trên
     *   xe là nơi ngón tay lướt qua khi xe xóc, và một nút xoá không hoàn lại được ngay cạnh nút sửa là ca bấm
     *   nhầm kinh điển. Vào hộp là đã nhìn thấy mình đang đứng ở mục nào.
     */
    fun askPlace(
        context: Context,
        title: String,
        initial: SavedPlace?,
        onDelete: (() -> Unit)? = null,
        onOk: (label: String, address: String, coords: String) -> Unit,
    ) {
        SettingsPlaceDialog.ask(context, title, initial, onDelete, onOk)
    }

    /**
     * Hỏi một cái TÊN (đặt tên nút vừa học). Ô nhập điền sẵn [initial] và **bôi chọn hết** để gõ đè được ngay —
     * cùng khuôn với mọi chỗ khác hỏi tên. S4 · R8 — nút *"Thêm hồ sơ (bản sao…)"* của nhóm Hồ sơ tài xế nay cũng
     * gọi CHÍNH hàm này (`ProfileBar.addDialog()` — bản dựng `AlertDialog` thứ hai — đã xoá cùng `ProfileBar`).
     *
     * Tên trắng ⇒ lùi về [initial] chứ không từ chối im lặng: người dùng vừa bấm một nút vật lý xong, bỏ công đó
     * đi vì một ô trống là mất cả phiên học. (QA-301 P3 ghi "ô rỗng vẫn Lưu được" — GIỮ: đây là hành vi cố ý của
     * askName cũ, nút Lưu không khoá.)
     *
     * @param showKeyboard `true` khi người dùng VỪA chọn gõ (nút *"Gõ tên…"*) ⇒ bàn phím lên ngay; mặc định `false` (đặt
     * tên nút vừa học / hồ sơ: tên điền sẵn thường dùng luôn, bàn phím che màn là thừa).
     */
    fun askName(
        context: Context,
        title: String,
        initial: String,
        detail: ((TextView) -> Unit)? = null,
        showKeyboard: Boolean = false,
        onOk: (String) -> Unit,
    ) {
        val name = KachiDialogs.field(
            context,
            label = null,
            value = initial,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS,
            selectAll = true,
        )
        val input = name.view
        // L7 — dòng CHI TIẾT dưới ô tên (học phím: mã · scan · thiết bị · nguồn) để anh em chụp màn hình gửi về.
        // [detail] tự điền/làm mới TextView; không có ⇒ hộp y nguyên như cũ (chỉ một ô nhập).
        val view = if (detail == null) input else LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(input)
            addView(KachiDialogs.caption(context, "").apply { detail(this) }, KachiDialog.stackLp(context, top = Sp.XS))
        }
        KachiDialogs.form(
            context,
            title = title,
            body = listOf(view),
            primaryLabel = context.getString(R.string.kachi_save),
            focus = name.edit,
            showKeyboard = showKeyboard,
        ) { onOk(name.text.trim().ifEmpty { initial }) }
    }

    /**
     * Hỏi một dòng CHỮ TỰ DO được phép XOÁ TRẮNG (F3 — ô *"Phát gì"*: rỗng = tiếp tục phiên của app). Khác [askName] đúng
     * một chỗ: ô trống là một câu trả lời hợp lệ, không lùi về [initial] — ở đây xoá chữ chính là lựa chọn của người dùng.
     */
    fun askText(context: Context, title: String, initial: String, onOk: (String) -> Unit) {
        val input = KachiDialogs.field(context, label = null, value = initial, selectAll = true)
        KachiDialogs.form(
            context,
            title = title,
            body = listOf(input.view),
            primaryLabel = context.getString(R.string.kachi_save),
            focus = input.edit,
        ) { onOk(input.text.trim()) }
    }
}
