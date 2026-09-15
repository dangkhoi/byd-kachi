package com.byd.clusternav.launcher

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R

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
 */
internal object SettingsDialogs {

    /**
     * Chọn MỘT mục trong [labels]; [onPick] nhận **chỉ số** (không phải nhãn) để chỗ gọi tra lại vật thật của nó —
     * nhãn có thể trùng nhau (hai app cùng tên) còn chỉ số thì không.
     */
    fun pick(context: Context, title: String, labels: List<String>, emptyText: String, onPick: (Int) -> Unit) {
        if (labels.isEmpty()) {
            notice(context, title, emptyText)
            return
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setItems(labels.toTypedArray()) { _, which -> onPick(which) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * **Nói một điều rồi thôi** — không có gì để chọn, không có gì để huỷ (danh sách rỗng · sổ đã đầy).
     *
     * Tách ra khỏi [pick] (nó vốn chứa nguyên khối này) vì nay có chỗ gọi thứ hai: một cú bấm **không có tác
     * dụng** thì phải NÓI lý do — luật đã lập ở `TopStripPicker.toggle`. Để mỗi chỗ tự dựng một `AlertDialog`
     * thông báo là mở lại đúng cánh cửa mà KDoc lớp này đóng.
     */
    fun notice(context: Context, title: String, message: String) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
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
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(confirmLabel) { _, _ -> onConfirm() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Hỏi MỘT MỤC SỔ ĐỊA CHỈ: nhãn · địa chỉ · (tuỳ chọn) toạ độ — spec `docs/specs/kachi-voice-addresses.html` R1.
     *
     * ## Vì sao là hàm thứ tư ở đây chứ không phải một `AlertDialog` dựng trong section
     * KDoc lớp cấm bản dựng thứ hai, và lý do vẫn đúng nguyên: ba quyết định (nút huỷ, nhãn nút lưu, kiểu bàn
     * phím) phải giống mọi hộp khác. [askName] không dùng lại được vì nó chỉ có **một** ô — mà một mục địa chỉ
     * thiếu bất kỳ trường nào trong hai trường đầu thì vừa không gọi được bằng giọng, vừa không dẫn đi đâu.
     *
     * ## Vì sao toạ độ là MỘT ô *"lat, lng"*, không phải hai ô
     * Đó đúng dạng người ta **dán** từ app bản đồ (`10.7769, 106.7009`). Bắt tách tay là bắt người dùng làm một
     * việc máy làm được, trên bàn phím ảo, trong xe. Phép tách + kiểm dải nằm ở `:core` ([SavedPlaces.parseCoords])
     * nên nó kiểm được off-car; hộp này **không** kiểm gì, chỉ chuyển ba chuỗi đi (ô hỏng ⇒ mục không toạ độ).
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
        fun field(value: String, hintRes: Int, capWords: Boolean) = EditText(context).apply {
            setText(value)
            hint = context.getString(hintRes)
            inputType = InputType.TYPE_CLASS_TEXT or
                (if (capWords) InputType.TYPE_TEXT_FLAG_CAP_WORDS else InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS)
        }
        val label = field(initial?.name.orEmpty(), R.string.kachi_places_hint_label, capWords = true)
        val address = field(initial?.query.orEmpty(), R.string.kachi_places_hint_address, capWords = false)
        val coords = field(
            initial?.let { SavedPlaces.formatCoords(it) }.orEmpty(),
            R.string.kachi_places_hint_coords,
            capWords = false,
        )
        val pad = KachiTheme.dpi(context, KachiSpace.M)
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
            addView(label)
            addView(address)
            addView(coords)
            addView(TextView(context).apply {
                text = context.getString(R.string.kachi_places_coords_note)
                setTextColor(KachiTheme.c(KachiTheme.MUT))
                KachiType.apply(this, KachiType.CAPTION)
            })
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(body)
            .setPositiveButton(context.getString(R.string.kachi_save)) { _, _ ->
                onOk(label.text.toString(), address.text.toString(), coords.text.toString())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .also { builder ->
                onDelete?.let { del ->
                    builder.setNeutralButton(context.getString(R.string.kachi_delete)) { _, _ -> del() }
                }
            }
            .show()
    }

    /**
     * Hỏi một cái TÊN (đặt tên nút vừa học). Ô nhập điền sẵn [initial] và **bôi chọn hết** để gõ đè được ngay —
     * cùng khuôn với mọi chỗ khác hỏi tên. S4 · R8 — nút *"Thêm hồ sơ (bản sao…)"* của nhóm Hồ sơ tài xế nay cũng
     * gọi CHÍNH hàm này (`ProfileBar.addDialog()` — bản dựng `AlertDialog` thứ hai — đã xoá cùng `ProfileBar`).
     *
     * Tên trắng ⇒ lùi về [initial] chứ không từ chối im lặng: người dùng vừa bấm một nút vật lý xong, bỏ công đó
     * đi vì một ô trống là mất cả phiên học.
     */
    fun askName(context: Context, title: String, initial: String, onOk: (String) -> Unit) {
        val input = EditText(context).apply {
            setText(initial)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSelection(0, initial.length)
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(context.getString(R.string.kachi_save)) { _, _ ->
                onOk(input.text.toString().trim().ifEmpty { initial })
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
