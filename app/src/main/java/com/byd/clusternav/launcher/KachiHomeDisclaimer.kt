package com.byd.clusternav.launcher

import android.app.Activity
import com.byd.clusternav.Prefs
import com.byd.clusternav.R

/*
 * Tách THUẦN khỏi `KachiHomeWiring.kt` (499 dòng → trần 500) ở 2.87 · R-AH để có chỗ cho dây công tắc "Tự ẩn nút ⇄":
 * thân hàm giữ nguyên byte, cùng package, cùng chữ ký (cùng khuôn `KachiHomeRender.kt` · `WorkspaceViewSlotDomain.kt`).
 */

/**
 * Hộp thoại **miễn trừ trách nhiệm lần đầu mở app** — chuyển nguyên từ `MainActivity.maybeShowDisclaimer`
 * (đã gỡ 2026-09-13), **cùng khoá bền** `disclaimer_shown` ([Prefs.disclaimerShown]) nên máy đã hiện một lần ở
 * màn cũ thì không hiện lại sau khi cập nhật (spec R6: không mất trạng thái người dùng).
 *
 * ## Cờ đặt TRƯỚC `show()`, đúng như bản cũ
 * Đặt sau thì một lần xoay màn / dựng lại Activity trong lúc hộp thoại đang mở là hiện lại lần hai. Người dùng
 * đọc xong một câu pháp lý rồi thấy nó quay lại thì lần sau họ bấm cho xong — mất luôn mục đích của nó.
 *
 * Câu chữ đầy đủ vẫn ĐỌC LẠI ĐƯỢC bất cứ lúc nào ở *Cài đặt › Giới thiệu* (`kachi_about_disclaimer`): hộp thoại
 * một-lần trả lời câu hỏi *"đã báo chưa"*, còn dòng ở About trả lời *"cái này là gì"* — hai câu hỏi khác nhau.
 */
internal fun Activity.maybeShowDisclaimer() {
    if (isFinishing || isDestroyed || Prefs.disclaimerShown(this)) return
    Prefs.setDisclaimerShown(this, true)
    // KACHI-301 R13 · #20 — khuôn THÔNG BÁO dựng trực tiếp trên [KachiDialog] (không qua [KachiDialogs.notice]) chỉ để giữ
    // nhãn nút riêng `kachi_disclaimer_ok` ("Đã hiểu") thay cho "OK" chung. Đóng được bằng Back/chạm ngoài như hộp cũ
    // (AlertDialog mặc định cancelable) — cờ đã đặt TRƯỚC nên đóng kiểu nào cũng không hiện lại.
    KachiDialog(this)
        .title(getString(R.string.kachi_disclaimer_title))
        .icon(R.drawable.ic_alert)
        .message(getString(R.string.kachi_disclaimer_body))
        .noCancel()
        .primary(getString(R.string.kachi_disclaimer_ok)) {}
        .show()
}
