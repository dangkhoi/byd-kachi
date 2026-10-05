package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.89 · B3 DOCK-SCALE — NGÂN SÁCH px của thanh nút ở MỌI cỡ (K5 của bản soát thiết kế) ═══════════════════════════════
 *
 * Tính bằng CHÍNH các hằng `KachiBars` / `KachiSpace` / [TileSize.DOCK] theo đúng phép đổi của mã (`(dp × density).toInt()`,
 * `density` = [BarScale.scaledDensity] — trùng bit với `Context` ghi đè, `BarScaleTest`), ở 4 mật độ gốc × 21 vị trí:
 *  (a) mỗi ô bấm: khung dọc trục ≥ 48 dp THẬT; ô STEP ngang: mỗi nửa ≥ min(48 dp, nửa ô ở 100 %);
 *  (b) ngang trục: hình ô + lề nằm trong bề dày thanh; đích chạm ngang trục = trọn bề dày (50 % ngang ≈ 46,5 dp);
 *  (c) nội dung ô vừa bề cao ô — ô thường (icon + nhãn 2 dòng) và ô STEP (icon + hàng −/+), CẢ thanh ngang lẫn dọc
 *      (K5: bản thiết kế chỉ kiểm chiều trục ô dọc);
 *  (d) khe lối tắt ≥ 48 dp thật;
 *  (e) thanh co thì không bao giờ DÀI hơn ở 100 % (trần của sàn chạm), dài đơn điệu theo %.
 *
 * ⚠ Giả định ghi rõ (không đo được off-device): chiều cao một dòng chữ ≈ [LINE_EM] × cỡ chữ (Roboto ≈ 1,17 em) và
 * `fontScale` = 1 [ĐOÁN cho font xe — 🚗 spec §B3 V-B3-3]. Vì MỌI thứ (kể cả chữ) co cùng hệ số, tỉ lệ nội dung/ô giữ như
 * 100 %; chỉ sai số làm tròn px đổi ⇒ cho phép [ROUND_PX]. Thử ĐỎ: thêm "sàn chữ 90 %" (chữ × max(k, 0,9)) ⇒ (c) đỏ ở 50 %.
 */
class BarScaleBudgetContractTest {

    private val bases = listOf(160, 213, 240, 320)
    private val positions = (0..BarScale.POSITIONS).map(BarScale::ofPosition)
    private val dock = TileSize.DOCK

    private fun density(base: Int, pct: Int) = BarScale.scaledDensity(base * (1.0f / 160), pct)
    private fun px(dp: Int, d: Float) = (dp * d).toInt()
    private fun line(sp: Float, d: Float) = sp * d * LINE_EM

    /** Khung dọc trục của một ô ở cỡ ≠ 100 % — đúng phép `ControlDockView.hitCell`. */
    private fun cellAlong(base: Int, pct: Int, vertical: Boolean, targets: Int): Int {
        val d = density(base, pct)
        val real = base * (1.0f / 160)
        val along = if (vertical) KachiBars.DOCK_TILE_H_VERTICAL else KachiBars.DOCK_TILE_W
        return if (BarScale.isIdentity(pct)) px(along, d) + 2 * px(KachiSpace.XS, d)
        else BarScale.cellAlongPx(px(along, d), px(KachiSpace.XS, d), px(KachiSpace.TOUCH, real), targets, px(along, real))
    }

    @Test
    fun `a - vung cham doc truc moi o du 48dp that, o STEP khong te hon 100`() {
        bases.forEach { base ->
            val real = base * (1.0f / 160)
            val touch = px(KachiSpace.TOUCH, real)
            positions.filter { it < 100 }.forEach { p ->
                listOf(false, true).forEach { v ->
                    assertTrue(cellAlong(base, p, v, 1) >= touch, "@$base dpi $p % ${if (v) "dọc" else "ngang"}: ô một-đích < 48 dp")
                }
                val stepHalf = cellAlong(base, p, false, 2) / 2
                assertTrue(stepHalf >= minOf(touch, px(KachiBars.DOCK_TILE_W, real) / 2),
                    "@$base dpi $p %: nửa −/+ của ô STEP ngang nhỏ hơn cả mức 100 %")
            }
        }
    }

    @Test
    fun `b - hinh o nam trong be day thanh, dich cham ngang truc bang be day`() {
        bases.forEach { base ->
            positions.forEach { p ->
                val d = density(base, p)
                val pad = if (BarScale.isIdentity(p)) 2 * px(KachiBars.DOCK_PAD, d) else 0   // ≠ 100 %: lề chỉ ở hai đầu trục
                assertTrue(px(KachiBars.DOCK_TILE_H, d) + 2 * px(KachiSpace.XS, d) + pad <= px(KachiBars.DOCK_THICK, d),
                    "@$base dpi $p %: ô ngang tràn bề dày thanh")
                assertTrue(px(KachiBars.DOCK_TILE_W_VERTICAL, d) + pad + (if (pad > 0) 2 * px(KachiSpace.XS, d) else 0) <=
                    px(KachiBars.DOCK_WIDE, d), "@$base dpi $p %: ô dọc tràn bề rộng thanh")
            }
        }
        // Số thật để spec dẫn: 50 % thanh ngang ở 240 dpi = 69 px = 46 dp (liên tục: 93 × 0,5 = 46,5 dp).
        assertEquals(69, px(KachiBars.DOCK_THICK, density(240, 50)))
    }

    @Test
    fun `c - noi dung o vua be cao o o moi co, ca ngang lan doc`() {
        bases.forEach { base ->
            positions.forEach { p ->
                val d = density(base, p)
                val chrome = 2 * px(dock.padDp, d) + px(dock.iconDp, d) + px(KachiSpace.XS, d)
                val twoLines = chrome + 2 * line(dock.labelSp, d)                                   // TOGGLE/COVER/BUTTON (reserveTwoLines)
                val step = chrome + maxOf(px(KachiSpace.TOUCH_TIGHT, d).toFloat(), line(dock.valueSp, d))  // hàng [−][giá trị][+]
                listOf(KachiBars.DOCK_TILE_H to "ngang", KachiBars.DOCK_TILE_H_VERTICAL to "dọc").forEach { (h, side) ->
                    val have = px(h, d) + ROUND_PX
                    assertTrue(twoLines <= have, "@$base dpi $p % ô $side: nhãn 2 dòng ${twoLines}px > ô ${px(h, d)}px")
                    assertTrue(step <= have, "@$base dpi $p % ô $side: ô STEP ${step}px > ô ${px(h, d)}px")
                }
            }
        }
    }

    @Test
    fun `d - khe loi tat du 48dp that`() {
        bases.forEach { base ->
            val touch = px(KachiSpace.TOUCH, base * (1.0f / 160))
            positions.forEach { p ->
                val slot = maxOf(px(KachiBars.SHORTCUT_CELL, density(base, p)), touch)   // = shortcutSlotPx
                assertTrue(slot >= touch, "@$base dpi $p %")
            }
            assertEquals(px(KachiBars.SHORTCUT_CELL, base * (1.0f / 160)),
                maxOf(px(KachiBars.SHORTCUT_CELL, density(base, 100)), touch), "100 % ⇒ khe đúng 52 dp như 2.88")
        }
    }

    @Test
    fun `e - thanh co khong dai hon 100, dai don dieu theo phan tram`() {
        val ids = ControlRegistry.defaultEnabledIds()
        assertTrue(ids.any { ControlRegistry.byId(it)?.kind == ControlKind.STEP }, "tiền đề: thanh mặc định có ô STEP")
        bases.forEach { base ->
            listOf(false, true).forEach { v ->
                fun length(p: Int): Int = 2 * px(KachiBars.DOCK_PAD, density(base, p)) + ids.sumOf { id ->
                    val step = ControlRegistry.byId(id)?.kind == ControlKind.STEP && !v
                    cellAlong(base, p, v, if (step) 2 else 1)
                }
                val lens = positions.map(::length)
                assertEquals(lens.sorted(), lens, "@$base dpi ${if (v) "dọc" else "ngang"}: bề dài phải đơn điệu theo %")
                assertTrue(positions.filter { it < 100 }.all { length(it) <= length(100) }, "@$base dpi: co mà dài hơn 100 %")
            }
        }
    }

    /**
     * (f) Review 2.89 Pass 2 · vietmap-dock-r1-8 — ≠ 100 % khối lối tắt LẤP TRỌN bề dày thanh (`fillAcross`): lề của icon chỉ DỌC
     * trục (`ShortcutIconsView.cell`) ⇒ hộp vẽ (FIT_CENTER) không bao giờ nhỏ hơn cỡ icon đã co — "kéo bao nhiêu hiển thị bấy
     * nhiêu" cả ở 50 %. Thử ĐỎ: lề vuông bốn phía ⇒ 50 % ngang @240 dpi: hộp 34×31 < icon 33.
     */
    @Test
    fun `f - hop ve icon loi tat khong nho hon co icon o moi vi tri`() {
        bases.forEach { base ->
            val touch = px(KachiSpace.TOUCH, base * (1.0f / 160))
            positions.filterNot(BarScale::isIdentity).forEach { p ->
                val d = density(base, p)
                val slot = maxOf(px(KachiBars.SHORTCUT_CELL, d), touch)                     // = shortcutSlotPx
                val icon = px(KachiBars.SHORTCUT_ICON, d)
                val pad = (slot - icon) / 2
                val along = slot - 2 * pad                                                  // lề chỉ dọc trục
                val horizontal = minOf(along, px(KachiBars.DOCK_THICK, d))                  // ngang trục = trọn bề dày
                val vertical = minOf(along, px(KachiBars.DOCK_WIDE, d))
                assertTrue(horizontal >= icon, "@$base dpi $p % thanh ngang: hộp $horizontal < icon $icon")
                assertTrue(vertical >= icon, "@$base dpi $p % thanh dọc: hộp $vertical < icon $icon")
            }
        }
        // Số của bản soát: 50 % ngang @240 dpi — bề dày 69, khe 72, icon 33, lề 19 ⇒ hộp 34 (trước: 34 × 31).
        val d = density(240, 50)
        assertEquals(33, px(KachiBars.SHORTCUT_ICON, d))
        assertEquals(69, px(KachiBars.DOCK_THICK, d))
    }

    private companion object {
        /** Chiều cao một dòng / cỡ chữ (Roboto ≈ 1,17) — [ĐOÁN] cho font xe, xem KDoc lớp. */
        const val LINE_EM = 1.17f

        /** Sai số làm tròn px cho phép (các thành phần làm tròn xuống riêng lẻ, chữ đo liên tục). */
        const val ROUND_PX = 1
    }
}
