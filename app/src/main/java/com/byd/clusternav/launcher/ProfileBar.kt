package com.byd.clusternav.launcher

import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText
import android.widget.Toast
import com.byd.clusternav.R

/**
 * Hồ sơ tài xế cho HOME — tách khỏi [KachiHomeActivity] (B5b). Chạm avatar = xoay hồ sơ kế tiếp.
 *
 * Đẩy quyết định về [HomeViewModel] (một chiều: switchProfile/addProfile → collector nạp lại workspace/dock/preset/
 * avatar). Chạm android (dialog/toast) nên là đơn vị :app hợp lệ.
 *
 * ⚠ S1·§4.5 — [addDialog] còn ở đây nhưng **chỉ được gọi từ màn Cài đặt** (`SettingsDeps.onAddProfile` dùng LẠI nó,
 * không dựng hộp thoại thứ hai). [cycle] KHÔNG còn mở nó.
 */
class ProfileBar(
    private val activity: Activity,
    private val viewModel: HomeViewModel,
) {
    /**
     * Xoay sang hồ sơ kế tiếp.
     *
     * ⚠⚠ **[SOÁT S1 · P2] Chỉ có MỘT hồ sơ thì KHÔNG mở hộp thoại tạo nữa.** §4.5 chốt: avatar ở thanh trên chỉ còn
     * *hiển thị + chạm để đổi*, việc **tạo** hồ sơ chuyển hẳn vào Cài đặt → Hồ sơ tài xế, vì "giữ luôn đường tạo ở
     * thanh trên thì có hai đường tạo cho cùng một việc".
     *
     * [ĐO] bản đầu của S1 chỉ gỡ `setOnLongClickListener` ở avatar, nhưng nhánh `size <= 1` ở đây **vẫn** mở hộp
     * thoại tạo — mà đó đúng là trạng thái của MỌI máy mới cài (một hồ sơ "Mặc định") ⇒ trong ca thường gặp nhất,
     * đường tạo ở thanh trên còn nguyên và KDoc của [KachiTopStrip.profileAvatar] nói sai sự thật.
     *
     * Không để cú chạm thành vô nghĩa: **nói CHỖ tạo hồ sơ** (luật "cú bấm không có tác dụng thì phải nói lý do" —
     * bài học nút bố cục sẵn ở P9), chứ không im lặng.
     */
    fun cycle() {
        val s = viewModel.uiState.value
        val list = s.profiles
        if (list.size <= 1) { toast(activity.getString(R.string.kachi_profile_only_one)); return }
        val i = (list.indexOf(s.activeProfile) + 1) % list.size
        viewModel.switchProfile(list[i]); toast(activity.getString(R.string.kachi_profile_switched, list[i]))   // collector nạp lại workspace/dock/preset/avatar
    }

    /** Dialog tạo hồ sơ mới → [HomeViewModel.addProfile]. Đường tới nó: **Cài đặt → Hồ sơ tài xế → "Thêm hồ sơ…"**. */
    fun addDialog() {
        val input = EditText(activity).apply { hint = activity.getString(R.string.kachi_profile_name_hint) }
        AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.kachi_profile_new_title)
            .setView(input)
            .setPositiveButton(R.string.kachi_create) { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) viewModel.addProfile(n)
            }
            .setNegativeButton(R.string.kachi_cancel, null)
            .show()
    }

    private fun toast(m: String) = Toast.makeText(activity, m, Toast.LENGTH_SHORT).show()
}
