package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * Mục **"Cảnh"** trong Cài đặt → Màn hình chính (P7 + P6 · T2).
 *
 * Tách thành tệp riêng vì [SettingsHomeSection] đã gom bốn mảng (bố cục · hình nền · chip · thanh nút) và trần tệp
 * của dự án là **500 dòng** — cùng lý do nhóm "Màn hình chính" tách khỏi [SettingsSections] ngay từ S1.
 *
 * ## Đặt ở nhóm "Màn hình chính", KHÔNG mở nhóm mới — và vì sao
 * Một cảnh là **cả bộ** thứ mà các mục khác của nhóm này đặt riêng lẻ: bố cục ([SettingsGroup.HOME] có `preset` +
 * `grid_layout`), nội dung từng ô, và thanh nút (`dock_edge` + `dock_enabled`). Gộp-và-các-phần phải nằm cạnh nhau,
 * không thì người dùng chỉnh bố cục ở trang này rồi phải đi tìm một trang khác để lưu lại. Mở nhóm thứ tám cũng làm
 * rail dài thêm cho một tính năng vốn là *đường tắt* của những gì ở ngay dưới nó.
 *
 * ## Ba thứ bắt buộc phải NÓI RA trên màn này
 *  1. **đủ trần** ⇒ câu nhắc (không chặn im lặng — luật đã vá ba lần trong dự án);
 *  2. **cảnh lúc nổ máy là gì** ⇒ nói thẳng hợp đồng *"launcher lên bằng cảnh này mỗi lần khởi động, cách bố trí chưa
 *     lưu sẽ bị thay"*. Đây là chỗ trả giá cho ngữ nghĩa đã chọn ở `PrefsWorkspaceRepository.load` — người dùng phải
 *     biết trước, không được ngạc nhiên;
 *  3. **chưa có cảnh nào** ⇒ nói cách tạo, không để một khoảng trắng bí ẩn.
 */
class SettingsSceneSection(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {

    fun build(body: LinearLayout) {
        val book = deps.state().scenes
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_sec_scenes)))
        body.addView(rows.note(context.getString(R.string.kachi_scenes_note)))

        if (book.scenes.isEmpty()) {
            body.addView(rows.note(context.getString(R.string.kachi_scenes_empty)))
        } else {
            book.scenes.forEach { scene ->
                body.addView(sceneRow(scene, boot = scene.id == book.bootSceneId), rowLp())
            }
            body.addView(bootLine(book))
        }

        // Câu đếm ĐỔI MÀU khi đủ trần: dòng này vốn đã hiện sẵn, nên nếu chỉ đổi chữ mà không đổi hình thì cú bấm bị
        // từ chối trông y như không có gì xảy ra (bài học của dòng nhắc trong ngăn kéo).
        body.addView(TextView(context).apply {
            text = if (book.full) context.getString(R.string.kachi_scenes_full, SceneBook.CAP)
            else context.getString(R.string.kachi_scenes_count, book.scenes.size, SceneBook.CAP)
            setTextColor(c(if (book.full) KachiTheme.AMBER else KachiTheme.MUT2))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiType.CAPTION)
            setPadding(0, 0, 0, dpi(context, Sp.S))
        })
        body.addView(rows.button(context.getString(R.string.kachi_scene_save)) { deps.scenes.save() }, wrapLp())
    }

    /**
     * Hợp đồng của cảnh lúc nổ máy — nói thẳng, cả khi CHƯA chọn.
     *
     * Nói cả ca "chưa chọn" là cố ý: không có nó thì người dùng không biết dấu nổ máy tồn tại, và cũng không biết
     * rằng **mặc định** launcher lên đúng cách bố trí lúc tắt (tức tính năng này không âm thầm bật).
     */
    private fun bootLine(book: SceneBook): View = rows.note(
        book.bootScene()?.let { context.getString(R.string.kachi_scene_boot_contract, it.name) }
            ?: context.getString(R.string.kachi_scene_boot_none),
    )

    /**
     * Một cảnh: tên + dòng phụ, rồi ba việc — **nổ máy** · **đổi tên** · **xoá**. Chạm phần tên = gọi lại cảnh.
     *
     * Cùng hình dạng với hàng hồ sơ ([SettingsSections] `profileRow`): tên + dòng phụ bên trái, việc làm bên phải,
     * chạm cả hàng là hành động chính. Ba nút chữ chứ không nhấn-giữ — nhấn-giữ thì không ai biết nó có ở đó, và dự án
     * đã bỏ đúng đường nhấn-giữ đó ở avatar hồ sơ vì lý do này.
     */
    private fun sceneRow(scene: Scene, boot: Boolean): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = KachiTheme.card(context, Sp.RADIUS_L, KachiTheme.FIELD)
        val p = dpi(context, Sp.M)
        setPadding(p, p, p, p)
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = scene.name
                    setTextColor(c(KachiTheme.INK)); typeface = Typeface.DEFAULT_BOLD
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiType.BODY)
                })
                addView(TextView(context).apply {
                    text = context.getString(
                        if (boot) R.string.kachi_scene_boot_on else R.string.kachi_scene_tap_recall,
                    )
                    setTextColor(c(if (boot) KachiTheme.GREEN else KachiTheme.MUT))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiType.CAPTION)
                })
                // Đích chạm của hành động CHÍNH (gọi lại cảnh) nằm ở khối tên, không ở cả hàng: cả hàng thì ba nút
                // bên phải nằm TRONG vùng chạm đó và một cú chạm lệch sẽ vừa gọi cảnh vừa bấm nút.
                setOnClickListener { deps.scenes.apply(scene.id) }
                minimumHeight = dpi(context, Sp.TOUCH)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        // Chạm lại dấu đang bật = BỎ dấu ⇒ có đường quay về "lên như lúc tắt máy" mà không phải xoá cảnh.
        addView(action(
            context.getString(R.string.kachi_scene_boot_mark),
            if (boot) KachiTheme.GREEN else KachiTheme.MUT2,
        ) { deps.scenes.setBoot(if (boot) null else scene.id) })
        addView(action(context.getString(R.string.kachi_scene_rename), KachiTheme.MUT) {
            deps.scenes.rename(scene.id)
        })
        addView(action(context.getString(R.string.kachi_delete), KachiTheme.RED) { deps.scenes.delete(scene.id) })
    }

    private fun action(text: String, colour: String, onClick: () -> Unit): View = TextView(context).apply {
        this.text = text
        setTextColor(c(colour))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiType.BODY)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(dpi(context, Sp.M), dpi(context, Sp.S), dpi(context, Sp.M), dpi(context, Sp.S))
        minHeight = dpi(context, Sp.TOUCH)
        setOnClickListener { onClick() }
    }

    private fun rowLp() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    ).also { it.bottomMargin = dpi(context, Sp.S) }

    private fun wrapLp() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    ).also { it.bottomMargin = dpi(context, Sp.M) }
}
