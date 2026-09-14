package com.byd.clusternav.launcher

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
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
            AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(emptyText)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setItems(labels.toTypedArray()) { _, which -> onPick(which) }
            .setNegativeButton(android.R.string.cancel, null)
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
