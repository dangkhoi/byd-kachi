package com.byd.clusternav.launcher

/**
 * 3.01 POLISH2 — hàng nút chân (ba nút kính của xe 3D + chân bảng Kính 2D) CANH GIỮA widget (owner 13/10: *"mớ icon bên dưới đó,
 * phải canh giữa widget nhé, đừng để lệch"*). Gốc lệch [ĐO mã]: mỗi nút mang lề trái kể cả nút ĐẦU ⇒ cụm lệch phải nửa khe.
 * Luật: khe CHỈ giữa hai nút, cụm đặt sao cho tâm cụm = tâm khung.
 */
object CenteredRow {
    /** Bề rộng cụm [n] nút cỡ [item] cách nhau [gap] (không khe ở hai đầu). */
    fun width(n: Int, item: Int, gap: Int): Int = if (n <= 0) 0 else n * item + (n - 1) * gap

    /** Toạ độ trái của cụm trong khung rộng [total]. */
    fun start(total: Int, n: Int, item: Int, gap: Int): Int = (total - width(n, item, gap)) / 2

    /** Lề trái của nút thứ [i] khi xếp trong một hàng tự canh (nút đầu 0). */
    fun leading(i: Int, gap: Int): Int = if (i == 0) 0 else gap
}
