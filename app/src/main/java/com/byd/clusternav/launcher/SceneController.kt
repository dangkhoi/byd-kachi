package com.byd.clusternav.launcher

import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText
import android.widget.Toast
import com.byd.clusternav.R

/**
 * CỔNG VÀO của mọi việc liên quan tới **cảnh** (P7 + P6) — lưu · gọi · đánh dấu nổ máy · đổi tên · xoá.
 *
 * ## Vì sao là MỘT giao diện chứ không năm lambda rời
 * Năm việc này luôn đi cùng nhau và luôn đi tới cùng một chỗ (`HomeViewModel`). Truyền năm lambda thì
 * [KachiHomeActivity] phải thêm năm dòng — mà tệp đó đang **đúng 500 dòng**, tức đúng trần của dự án, nên "thêm năm
 * dòng" sẽ thành một lượt tách tệp ngoài phạm vi. Một tham số cũng đúng hơn về mặt khái niệm: bề mặt cài đặt cần *"bộ
 * việc làm với cảnh"*, không cần biết mỗi việc đi qua intent nào.
 */
interface SceneActions {

    /** Hỏi tên rồi lưu trạng thái đang dùng thành cảnh. Đủ trần ⇒ **nói ra**, không im lặng bỏ qua. */
    fun save()

    /** Gọi lại cảnh [id] (R2 — và R4: ô không đổi thì app trong ô không bị mở lại). */
    fun apply(id: String)

    /** Đánh dấu cảnh [id] là **cảnh lúc nổ máy**; `null` = bỏ dấu. */
    fun setBoot(id: String?)

    /** Hỏi tên mới rồi đổi tên cảnh [id]. Tên đã thuộc cảnh khác ⇒ nói ra. */
    fun rename(id: String)

    fun delete(id: String)
}

/**
 * Bản thật của [SceneActions]: hộp thoại nhập tên + chuyển tiếp sang intent của [HomeViewModel].
 *
 * Cùng khuôn với [ProfileBar] (hộp thoại + `AlertDialog` + `EditText`, một chỗ duy nhất dựng nó) — cố ý, vì hai bề
 * mặt cùng làm việc "hỏi một cái tên rồi lưu" mà dựng hai kiểu hộp thoại khác nhau thì người dùng thấy hai app.
 *
 * ## Ở đây là chỗ NÓI RA khi một cú chạm không có tác dụng
 * Ba ca: tên rỗng · đã đủ trần cảnh · tên đã thuộc cảnh khác. Cả ba đều **từ chối** ở `:core` (xem [SceneBook]), và
 * nếu tầng UI không nói gì thì cú bấm của người dùng biến mất — đúng họ lỗi dự án đã vá ba lần
 * (`DockConfig.setEnabled` bỏ qua im lặng · trần 8 mục của ngăn kéo · nút bố cục sẵn không có tác dụng).
 *
 * ⚠ Nói bằng `Toast` là **đúng ở bề mặt này**: màn Cài đặt là con của cửa sổ Activity (`mBaseLayer` ~21000) nên toast
 * (81000) nằm TRÊN nó. Luật *"bề mặt phủ không được nói bằng Toast"* chỉ áp cho `TYPE_APPLICATION_OVERLAY`
 * (`mBaseLayer` 121000 — ngăn kéo, dải header), xem `PickerCapNoticeContractTest`.
 */
class SceneController(
    private val activity: Activity,
    private val viewModel: HomeViewModel,
) : SceneActions {

    private val book: SceneBook get() = viewModel.uiState.value.scenes

    override fun save() = askName(R.string.kachi_scene_save_title, "") { name ->
        // Trùng tên = GHI ĐÈ cảnh đó ⇒ vẫn cho phép dù đã đủ trần (không thêm cảnh mới nào).
        if (book.byName(name) == null && book.full) {
            toast(activity.getString(R.string.kachi_scenes_full, SceneBook.CAP))
            return@askName
        }
        viewModel.saveScene(name)
        toast(activity.getString(R.string.kachi_scene_saved, Scene.sanitiseName(name)))
    }

    override fun apply(id: String) {
        val scene = book.byId(id) ?: return
        viewModel.applyScene(id)
        toast(activity.getString(R.string.kachi_scene_applied, scene.name))
    }

    override fun setBoot(id: String?) {
        viewModel.setBootScene(id)
        if (id == null) toast(activity.getString(R.string.kachi_scene_boot_cleared))
    }

    override fun rename(id: String) {
        val cur = book.byId(id) ?: return
        askName(R.string.kachi_scene_rename_title, cur.name) { name ->
            val taken = book.byName(name)
            if (taken != null && taken.id != id) {
                toast(activity.getString(R.string.kachi_scene_name_taken, Scene.sanitiseName(name)))
                return@askName
            }
            viewModel.renameScene(id, name)
        }
    }

    override fun delete(id: String) {
        val scene = book.byId(id) ?: return
        viewModel.deleteScene(id)
        toast(activity.getString(R.string.kachi_scene_deleted, scene.name))
    }

    /**
     * Hộp thoại nhập tên. Tên rỗng sau khi làm sạch ⇒ **nói ra** rồi thôi (hộp thoại của hồ sơ im lặng bỏ qua ca đó;
     * ở đây nói, vì "Lưu" mà không có gì xảy ra là lúc người dùng bắt đầu nghi app hỏng).
     */
    private fun askName(titleRes: Int, initial: String, onName: (String) -> Unit) {
        val input = EditText(activity).apply {
            hint = activity.getString(R.string.kachi_scene_name_hint)
            setText(initial)
            setSelection(initial.length)
        }
        AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(titleRes)
            .setView(input)
            .setPositiveButton(R.string.kachi_scene_confirm) { _, _ ->
                val name = Scene.sanitiseName(input.text.toString())
                if (name.isEmpty()) toast(activity.getString(R.string.kachi_scene_name_empty)) else onName(name)
            }
            .setNegativeButton(R.string.kachi_cancel, null)
            .show()
    }

    private fun toast(m: String) = Toast.makeText(activity, m, Toast.LENGTH_SHORT).show()
}
