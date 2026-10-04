package com.byd.clusternav.launcher

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/**
 * Tra cây view của MỘT ô widget cho [FitScale] (tách khỏi `FitScale.kt` ở soát vòng 5 để tệp đó dưới trần 500 dòng — CLAUDE.md
 * §4.1). Chỉ đọc cây, không đổi gì.
 */
internal object FitTree {

    /** Khối chính: `LinearLayout` DỌC đầu tiên (duyệt theo bề rộng) có ≥ 2 con đang hiện — thứ dạng NGANG lật. */
    fun main(root: View): LinearLayout? {
        val queue = ArrayDeque<View>().apply { add(root) }
        while (queue.isNotEmpty()) {
            val v = queue.removeFirst()
            if (v is LinearLayout && v.orientation == LinearLayout.VERTICAL &&
                (0 until v.childCount).count { v.getChildAt(it).visibility != View.GONE } >= 2
            ) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) queue.add(v.getChildAt(i))
        }
        return null
    }

    /** Ô bấm gần nhất bọc [v] (kể cả chính nó), không vượt quá [root]; `null` = không có. */
    fun clickableAncestor(v: View, root: View): View? {
        var cur: View? = v
        while (cur != null) {
            if (cur.isClickable) return cur
            if (cur === root) return null
            cur = cur.parent as? View
        }
        return null
    }
}
