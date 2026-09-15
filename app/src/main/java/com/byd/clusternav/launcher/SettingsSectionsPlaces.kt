package com.byd.clusternav.launcher

import android.content.Context
import android.widget.LinearLayout
import com.byd.clusternav.R

/**
 * **SỔ ĐỊA CHỈ** — mục con đầu tiên của nhóm *Dẫn đường & cụm đồng hồ*.
 *
 * Spec `docs/specs/kachi-voice-addresses.html` R1. Owner 2026-09-15: *"Thêm vào hồ sơ địa chỉ nữa — công ty, nhà,
 * whatever user lưu lại — để khi voice thì dẫn đúng đến địa chỉ đấy."*
 *
 * ## Vì sao ở nhóm DẪN ĐƯỜNG mà dữ liệu lại theo HỒ SƠ
 * Nhóm chia theo **thứ người dùng đang nghĩ tới** (KDoc [SettingsGroup]), không theo nơi lưu: người ta vào *Dẫn
 * đường* để sửa địa chỉ nhà, không vào *Hồ sơ tài xế* — dù sổ đi theo hồ sơ (`<hồ sơ>__saved_places`, xem
 * [ProfileScope.LAUNCHER_PERSONAL_SUFFIXES]). Đây đúng đường biên mà KDoc [SettingsGroup] mô tả cho ca [CAR]:
 * khoá nằm ở một chỗ, mục hiện ở nhóm người dùng nghĩ tới.
 *
 * ## Vì sao đứng ĐẦU trang, trước công tắc dẫn đường
 * KDoc [SettingsNavSection] nói *"công tắc chính đứng đầu vì hai mục dưới chỉ có nghĩa khi nó bật"* — lập luận ấy
 * xếp thứ tự **giữa ba khối cụm với nhau** (công tắc → biển báo → bong bóng), và nó vẫn nguyên. Sổ địa chỉ không
 * thuộc chuỗi đó: nó chạy được kể cả khi dẫn-đường-lên-cụm đang tắt (nó chỉ bắn một ý-định sang app bản đồ). Đặt
 * nó sau ba khối kia là chôn một tính năng người dùng dùng hằng ngày dưới ~2,7 màn cuộn cấu hình cụm.
 *
 * ## Danh sách dựng lại TẠI CHỖ, không dựng lại cả trang
 * Trang Cài đặt được **nhớ lại** ([SettingsPanel.pages]) nên nó không tự làm mới. Giữ [placeList] và chỉ nạp lại
 * mình nó ([rebuild]) — cùng cách [SettingsKeysSection] làm, và cùng lý do: dựng lại cả trang là vứt chỗ đang cuộn.
 */
class SettingsPlacesSection(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {

    /** Khung chứa các hàng địa điểm — giữ tham chiếu để [rebuild] nạp lại đúng nó (xem KDoc lớp). */
    private val placeList = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    fun build(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_sec_places)))
        body.addView(rows.note(context.getString(R.string.kachi_places_note)))
        body.addView(placeList)
        rebuild()
        body.addView(rows.button(context.getString(R.string.kachi_places_add)) { edit(null) })
    }

    /**
     * Nạp lại danh sách từ **nguồn sự thật** (`state().savedPlaces`, đã nạp ở `WorkspaceRepository.load`).
     *
     * ⚠ Đọc lại `deps.state()` mỗi lượt chứ không giữ một bản chụp: mỗi lần ghi đi qua `HomeViewModel` sẽ thay
     * state, và một bản chụp cũ ở đây là cách chắc chắn để xoá một mục rồi thấy nó quay lại ở lượt sửa tiếp theo.
     */
    private fun rebuild() {
        placeList.removeAllViews()
        val places = deps.state().savedPlaces
        if (places.isEmpty()) {
            placeList.addView(rows.note(context.getString(R.string.kachi_places_empty)))
            return
        }
        places.forEach { place ->
            placeList.addView(
                rows.listRow(
                    title = place.name,
                    sub = subtitle(place),
                    // Nhãn hành động là **Sửa**, không phải Xoá: sửa là việc hay làm (đổi địa chỉ, thêm toạ độ),
                    // còn xoá là việc làm một lần — và xoá nằm trong chính hộp sửa, sau khi người dùng đã nhìn
                    // thấy mình đang đứng ở mục nào. Hàng này generic đúng như KDoc [SettingsRows.listRow] hứa.
                    actionLabel = context.getString(R.string.kachi_edit),
                ) { edit(place) },
            )
        }
        if (places.size >= SavedPlaces.MAX) {
            placeList.addView(rows.note(context.getString(R.string.kachi_places_full, SavedPlaces.MAX)))
        }
    }

    /**
     * Dòng phụ của một mục: địa chỉ, **và nói ra có toạ độ hay không**.
     *
     * Không phải trang trí: [ĐO] VietMap chỉ nhận toạ độ (`VoiceAppTargets`), nên *"mục này có lat/lng chưa"*
     * quyết định thẳng việc câu *"về nhà"* dẫn được bằng app nào. Giấu nó đi thì người dùng không bao giờ hiểu vì
     * sao một mục mở được VietMap còn mục kia thì không.
     */
    private fun subtitle(place: SavedPlace): String {
        val coords = if (place.hasCoords) {
            context.getString(R.string.kachi_places_has_coords, SavedPlaces.formatCoords(place))
        } else {
            context.getString(R.string.kachi_places_no_coords)
        }
        return place.query + " · " + coords
    }

    /**
     * Thêm ([place] = `null`) hoặc sửa một mục.
     *
     * Ghi **cả danh sách** đã chốt qua một cổng duy nhất ([SettingsDeps.onSavedPlaces]); phép thêm/sửa/xoá là hàm
     * thuần ở `:core` ([SavedPlaces.upsert]/[SavedPlaces.remove]) nên chúng kiểm được off-car và tầng vẽ này không
     * giữ một luật nào.
     */
    private fun edit(place: SavedPlace?) {
        val current = deps.state().savedPlaces
        if (place == null && current.size >= SavedPlaces.MAX) {
            SettingsDialogs.notice(
                context,
                context.getString(R.string.kachi_places_add),
                context.getString(R.string.kachi_places_full, SavedPlaces.MAX),
            )
            return
        }
        SettingsDialogs.askPlace(
            context = context,
            title = context.getString(if (place == null) R.string.kachi_places_add else R.string.kachi_places_edit),
            initial = place,
            onDelete = place?.let { { apply(SavedPlaces.remove(deps.state().savedPlaces, it.name)) } },
        ) { label, address, coords ->
            // Thiếu nhãn hoặc thiếu địa chỉ ⇒ `null`: một mục như thế vừa không gọi được bằng giọng vừa không dẫn
            // đi đâu (xem KDoc [SavedPlaces.of]). Nói ra thay vì lặng lẽ bỏ qua cú bấm Lưu.
            val made = SavedPlaces.of(label, address, coords)
            if (made == null) {
                SettingsDialogs.notice(
                    context,
                    context.getString(R.string.kachi_places_add),
                    context.getString(R.string.kachi_places_need_fields),
                )
                return@askPlace
            }
            // Đổi TÊN một mục = bỏ tên cũ rồi thêm tên mới (upsert khớp theo nhãn). Thiếu bước xoá thì sửa nhãn
            // "Nhà" thành "Nhà riêng" sẽ để lại **hai** mục, và câu *"về nhà"* vẫn đi theo mục cũ.
            val base = if (place != null && SavedPlaces.keyOf(place.name) != SavedPlaces.keyOf(made.name)) {
                SavedPlaces.remove(deps.state().savedPlaces, place.name)
            } else {
                deps.state().savedPlaces
            }
            apply(SavedPlaces.upsert(base, made))
        }
    }

    private fun apply(places: List<SavedPlace>) {
        deps.onSavedPlaces(places)
        rebuild()
    }
}
