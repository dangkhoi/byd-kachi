package com.byd.clusternav.launcher

import android.view.View

/**
 * 2.93 `A11Y-STEPPER-NAME` — gắn chữ trợ năng "tên + giá trị" ([StepSpoken], `:core`) lên ba view của ô bước
 * (chữ giá trị · nút − · nút +) mỗi lần ô vẽ một giá trị. Chỉ ghi khi chữ ĐỔI (nhịp đọc lại xe 1 Hz không cấp phát
 * mô tả mới cho ô đứng yên). Tách khỏi `ControlTileFactory` vì trần 500 dòng.
 */
internal object StepA11y {

    fun speak(name: String, valueText: String, value: View, minus: View, plus: View) {
        val w = StepSpoken.of(name, valueText)
        if (value.contentDescription?.toString() != w.value) value.contentDescription = w.value
        if (minus.contentDescription?.toString() != w.down) minus.contentDescription = w.down
        if (plus.contentDescription?.toString() != w.up) plus.contentDescription = w.up
    }
}
