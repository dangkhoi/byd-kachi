package com.byd.clusternav.launcher

import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText
import android.widget.Toast

/**
 * Hồ sơ tài xế cho HOME — tách khỏi [KachiHomeActivity] (B5b). Chạm avatar = xoay hồ sơ kế tiếp; giữ = dialog tạo mới.
 *
 * Đẩy quyết định về [HomeViewModel] (một chiều: switchProfile/addProfile → collector nạp lại workspace/dock/preset/
 * avatar). Byte-giữ so với `cycleProfile()`/`addProfileDialog()` cũ của activity. Chạm android (dialog/toast) nên là
 * đơn vị :app hợp lệ.
 */
class ProfileBar(
    private val activity: Activity,
    private val viewModel: HomeViewModel,
) {
    /** Xoay sang hồ sơ kế tiếp; chỉ có 1 hồ sơ → mở dialog tạo mới. */
    fun cycle() {
        val s = viewModel.uiState.value
        val list = s.profiles
        if (list.size <= 1) { addDialog(); return }
        val i = (list.indexOf(s.activeProfile) + 1) % list.size
        viewModel.switchProfile(list[i]); toast("Hồ sơ: ${list[i]}")   // collector nạp lại workspace/dock/preset/avatar
    }

    /** Dialog tạo hồ sơ mới → [HomeViewModel.addProfile] (collector nạp lại; hồ sơ mới = bố cục mặc định). */
    fun addDialog() {
        val input = EditText(activity).apply { hint = "Tên hồ sơ (vd: Đường trường)" }
        AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Hồ sơ mới")
            .setView(input)
            .setPositiveButton("Tạo") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) viewModel.addProfile(n)
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    private fun toast(m: String) = Toast.makeText(activity, m, Toast.LENGTH_SHORT).show()
}
