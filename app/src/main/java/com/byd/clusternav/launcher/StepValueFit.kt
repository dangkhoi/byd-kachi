package com.byd.clusternav.launcher

import android.graphics.Paint
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView

/**
 * ═══ Ô GIÁ TRỊ của nút STEP: chữ dài hơn sàn chung thì **CO**, và **KHÔNG BAO GIỜ kẹt ở cỡ nhỏ** ══════════════
 *
 * Tách khỏi [ControlTileFactory] ở lượt soát 2.74 (tệp kia 499/500 dòng — CLAUDE.md §4.1) vì vai ở đây đã thành một
 * vai riêng: *"cho một `TextView` một dòng tự chọn cỡ chữ mà vẫn giữ đúng một hộp"*. Chỉ nút có
 * [ControlDef.autoId] đi qua đây, nên ô nhiệt độ/âm lượng **không đổi một pixel nào** (bảo đảm R2.4: hai nút −/+
 * đứng đúng một chỗ ở mọi ô stepper).
 *
 * ## Vì sao phải ghim CHIỀU CAO, không chỉ bật autosize
 * [ĐO máy ảo · QA lượt 2 — `docs/diagnostics/offcar-2026-09-26/visual-pass-2026-09-27.md` §1b] ô gió ở mức 1 có mực
 * cao **15 px** (ô 47×29). Bấm `−` ⇒ chữ "AUTO" co xuống sàn [MIN_SP]. Bấm `+` để rời AUTO ⇒ "2" chỉ còn **10 px**
 * (ô 47×**19**) và **không bao giờ to lại**. Con số mà người lái đọc trong lúc lái nhỏ đi một phần ba, vĩnh viễn,
 * sau đúng một lượt đi qua AUTO.
 *
 * Cơ chế: `autoSizeText` chọn cỡ lớn nhất còn vừa **khoảng trống đo được** (rộng × cao). Ô này nằm trong hàng
 * stepper với `LayoutParams(0, WRAP_CONTENT, VALUE_WEIGHT)`, nên chiều cao đo được **đi theo cỡ chữ đang vẽ**: lượt
 * vẽ ở 9sp cho ô cao 19 px, mà 19 px thì không chứa nổi cỡ gốc nữa ⇒ khoá cứng ở sàn. Đây đúng cảnh báo của tài liệu
 * Android (*"do not set layout_width or layout_height to wrap_content"* khi bật autosize), và nó **chỉ** lộ ra ở nút
 * có `autoId` vì chỉ nút ấy mới đổi độ dài chữ (`AUTO` ⇄ `3`).
 *
 * Không chữa được bằng cách đặt lại cỡ chữ mỗi lượt đổi text: [ĐO] AOSP `android-10.0.0_r47`
 * `widget/TextView.java` — `setTextSize(unit, size)` mở đầu bằng `if (!isAutoSizeEnabled())` ⇒ **no-op** khi autosize
 * đang bật. Nên phải chặn ở **khoảng trống**, không ở cỡ chữ.
 *
 * ## Vì sao ghim qua `LayoutParams`, KHÔNG qua `TextView.setHeight`
 * Lượt vá đầu (2.74, lượt soát Opus) ghim bằng `v.height = fm.bottom − fm.top`. [ĐO máy ảo · QA lượt 3, cùng tài liệu
 * §2c.3] chữ `AUTO` **xuống 2 dòng** `AUT`/`O`, mép dưới chạm đúng hàng pixel cuối của ô. Nguyên nhân —
 * [ĐO] AOSP `android-10.0.0_r47` `core/java/android/widget/TextView.java`: `maxLines` và `height` **dùng CHUNG một
 * cặp trường**, nên cái sau xoá cái trước.
 *
 * | file:line | Mã | Hệ quả |
 * |---|---|---|
 * | `:5336-5339` | `setMaxLines(n)` ⇒ `mMaximum = n; mMaxMode = LINES;` | [ControlTileFactory] đặt `maxLines = 1` |
 * | `:5438-5441` | `setHeight(px)` ⇒ `mMaximum = mMinimum = px; mMaxMode = mMinMode = PIXELS;` | **xoá** `maxLines = 1` |
 * | `:5357-5359` | `getMaxLines()` ⇒ `mMaxMode == LINES ? mMaximum : -1` | sau phép ghim trả **−1** |
 * | `:9530` | `if (maxLines != -1 && layout.getLineCount() > maxLines) return false;` | **bị bỏ qua** |
 * | `:9535` | `if (layout.getHeight() > availableSpace.bottom) return false;` | 2 dòng cỡ vừa lọt 31 px ⇒ **được nhận** |
 *
 * ⇒ autosize chọn *cỡ to hơn kèm xuống dòng* thay vì cỡ nhỏ hơn giữ một dòng. Ba hàm `setHeight` (`:5438`),
 * `setMaxHeight` (`:5376-5378`), `setMinHeight` (`:5297-5299`) đều ghi vào cùng cặp trường ⇒ **không** đường nào
 * trong số đó dùng được. Nên chiều cao được ghim **ngoài** view, ở `LayoutParams` của chỗ `addView`: hàng stepper là
 * `LinearLayout` ngang, `getChildMeasureSpec` gặp `lp.height >= 0` ⇒ đo con **EXACTLY** ([ĐO] `LinearLayout.java`
 * `:1380-1383` — cả lượt phân bổ weight), và `TextView.onMeasure` `:9394-9396` (*"Parent has told us how big to be"*)
 * lấy đúng số ấy làm `getMeasuredHeight()`, tức đúng khoảng trống mà `autoSizeText` `:9445` đọc — **mà `mMaxMode` giữ
 * nguyên `LINES`** ⇒ `maxLines = 1` còn hiệu lực ⇒ autosize buộc phải **co chữ** để vừa một dòng.
 *
 * Chiều cao ghim = đúng chỗ cho MỘT dòng ở cỡ TO NHẤT, đo bằng chính `paint` của view (nó đang mang cỡ gốc khi hàm
 * này được gọi) ⇒ con số tự đúng ở cả ba vùng [TileSize] mà không gõ một hằng dp nào. Lề trên/dưới của ô là 0
 * (`setPadding(XS, 0, XS, 0)` ở chỗ gọi) nên `fontMetricsInt` là toàn bộ chỗ cần.
 *
 * ## Và vì sao phải NHƯỜNG một nửa lề trong hai bên
 * Ghim chiều cao xong thì chữ về một dòng — nhưng [ĐO máy ảo · QA lượt 4 §2d.3] nó đọc ra **`AUT`**, mất hẳn chữ `O`.
 * Không phải lỗi của phép ghim: ảnh lượt 2 (`visual-274b/43-fan-auto.png`, cây **trước** mọi bản vá 2.74) cũng đúng
 * `AUT` ⇒ lỗi **có từ UX4**, chỉ là hai lượt QA trước đo hộp mực (27×10) mà không đếm chữ.
 *
 * Phép tính: ô rộng 47px (do `weight`, xem [ControlTileFactory]); lề trong [KachiSpace.XS] = 4dp = **6px mỗi bên** ở
 * 240dpi ⇒ chữ chỉ còn **35px**. `"AUTO"` ở **sàn** [MIN_SP] cần ~37px ⇒ **không cỡ nào** trong dải vừa một dòng ⇒
 * `findLargestTextSizeWhichFits` rơi về phần tử 0 và `StaticLayout` ngắt thành hai dòng; view chỉ cao một dòng nên
 * dòng hai nằm **ngoài vùng vẽ** ⇒ mất chữ, **không** có dấu `…` (không đặt `ellipsize`). Đúng cùng một bệnh mà KDoc
 * [TileSize.narrow] đã ghi cho nút COVER (*"`Đóng`/`Close` bị cắt cứng thành `Đ`/`C`"*), và cùng một cách chữa: cho
 * chữ **trọn chỗ của ô**.
 *
 * Nhường **một nửa** lề (3px mỗi bên) ⇒ 41px cho chữ ⇒ `AUTO` đủ bốn chữ, mực 39×11, lề mực 4px hai bên (khe tới
 * mực của nút `−`/`+` là 10–11px). Nhường **trọn** lề thì autosize nở tiếp tới mực 47×13 — chữ chạm đúng hai mép ô,
 * sát nút, nên không lấy. Con số "một nửa" đọc từ chính lề mà chỗ gọi đã đặt (`paddingLeft / 2`), không phải hằng
 * mới: đổi [KachiSpace.XS] thì nó tự đi theo. Ô không đi qua đây ([needsFit] `false`) giữ nguyên lề đầy đủ.
 */
internal object StepValueFit {

    /** UX4 — sàn cỡ chữ khi ô giá trị phải CO cho vừa; 9sp là ngưỡng còn đọc được ở khoảng cách lái xe. */
    const val MIN_SP = 9

    /**
     * Chỗ cho **đúng MỘT dòng** ở cỡ chữ mà [paint] đang mang, px. Phép tính **thuần** — không chạm view, không
     * chạm `TextView.setHeight` (xem KDoc lớp: `setHeight` xoá `maxLines`). Chỗ gọi đem số này vào `LayoutParams`.
     */
    fun cellHeightPx(paint: Paint): Int = paint.fontMetricsInt.let { it.bottom - it.top }

    /** Nút này có phải CO chữ không: chữ dài nhất của nó vượt sàn bề ngang CHUNG ([ControlVisuals.STEP_VALUE_CHARS]). */
    fun needsFit(def: ControlDef): Boolean =
        ControlVisuals.stepValueChars(def) > ControlVisuals.STEP_VALUE_CHARS

    /**
     * Bật autosize cho ô giá trị của [def] — **chỉ** khi [needsFit]; và trả về **chiều cao cho `LayoutParams`** của
     * ô ấy ở chỗ `addView`: số px của [cellHeightPx] (nút phải co) hoặc `WRAP_CONTENT` (nút khác ⇒ y hành vi trước
     * 2.74, **0 pixel đổi** — ô nhiệt độ/âm lượng không bị đụng tới).
     *
     * Nới sàn bề ngang thay vì co chữ là bóp hai nút −/+ ở **mọi** ô stepper (phép đo ở
     * [ControlVisuals.stepValueChars]) ⇒ không làm.
     *
     * Đo `paint` **trước** khi bật autosize: sau đó cỡ chữ là cỡ autosize đã chọn, không còn là cỡ gốc. Và nhường nửa
     * lề trong hai bên **trước** cả hai việc ấy — autosize đọc khoảng trống *sau* khi trừ lề, đổi lề sau là muộn.
     *
     * @param maxSp cỡ chữ gốc của vùng ([TileSize.valueSp]) — cũng là trần của autosize.
     */
    fun apply(v: TextView, def: ControlDef, maxSp: Float): Int {
        if (!needsFit(def)) return ViewGroup.LayoutParams.WRAP_CONTENT
        val sidePad = v.paddingLeft / 2
        v.setPadding(sidePad, v.paddingTop, sidePad, v.paddingBottom)
        val h = cellHeightPx(v.paint)
        v.setAutoSizeTextTypeUniformWithConfiguration(MIN_SP, maxSp.toInt(), 1, TypedValue.COMPLEX_UNIT_SP)
        return h
    }
}
