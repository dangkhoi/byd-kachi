package com.byd.clusternav.launcher

import com.byd.clusternav.modules.clustercast.simplified.CastEnableDeferral
import com.byd.clusternav.modules.clustercast.simplified.CastGeometryGuard

/**
 * ═══ FIX286 · PI3/PI5 — tóm tắt PHẦN CHIẾU CỤM của một lượt nhập hồ sơ (spec `kachi-286-field-fixes.html` §3.3) ══════
 *
 * Báo cáo hiện trường 2.84 (`FIELD-285-0310`): nhập hồ sơ xong *"phải bật lại + chỉnh khung tay"*, và app không nói câu
 * nào — tệp cũ không có phần cụm, tệp mới thiếu khung của app đang chiếu, đều im lặng. Lớp này đếm THEO DỮ LIỆU CỦA TỆP
 * (không theo số phiên bản) để hộp thoại sau khi nhập và dòng log nói thật cùng một điều. Thuần ⇒ test off-device.
 *
 * @property hasClusterPart tệp có mốc họ (xuất từ ≥ 2.84); `false` ⇒ hồ sơ nhập giữ toàn bộ khung của xe nhận.
 * @property castOn lựa chọn chiếu cụm của hồ sơ nhập SAU merge (vắng ⇒ mặc định [CastEnableDeferral.DEFAULT]).
 * @property castFromFile tệp mang giá trị chiếu cụm; `false` ⇒ [castOn] là giá trị đang chạy của xe nhận (PI2).
 * @property fileRecords số KHUNG (bản ghi = một app × toàn cụm/nửa cụm) trong tệp · [replacingRecords] trong số đó xe nhận
 *   đang có khung KHÁC (đổi sang hồ sơ này sẽ thay) · [keptRecords] khung chỉ xe nhận có (giữ lại — PI1). Hộp thoại đếm
 *   theo KHUNG: đếm theo app thì "giữ khung cho 1 app mà tệp không có" là SAI khi tệp có Maps toàn cụm còn xe giữ Maps
 *   nửa trái [ĐO máy ảo 02/10, V-PI tệp (c)].
 * @property fileApps / [replacingApps] / [keptApps] cùng số đếm theo gói (dòng log — "khung của mấy app").
 * @property dropped số giá trị của xe nhận bị bỏ vì hỏng.
 */
data class ClusterImportSummary(
    val hasClusterPart: Boolean,
    val castOn: Boolean,
    val castFromFile: Boolean,
    val fileApps: Int,
    val replacingApps: Int,
    val keptApps: Int,
    val fileRecords: Int,
    val replacingRecords: Int,
    val keptRecords: Int,
    val dropped: Int,
) {
    /** PI5 — dòng log lúc nhập (tag `KachiProfile`). */
    fun logLine(profile: String, kind: ProfileTransfer.Kind): String {
        val part = if (hasClusterPart) "có phần cụm" else "KHÔNG có phần cụm (tệp trước 2.84)"
        val cast = (if (castFromFile) "tệp:" else "xe(giữ):") + if (castOn) "BẬT" else "TẮT"
        return "import «$profile» kind=$kind · $part · cast_enabled=$cast · " +
            "khung tệp $fileApps app/$fileRecords bản ghi (thay $replacingApps app/$replacingRecords bản ghi) · " +
            "giữ của xe $keptApps app/$keptRecords bản ghi · bỏ $dropped"
    }

    companion object {
        /** Không có tệp `simple_cast_prefs` nào để merge (ca không xảy ra với bảng hôm nay) — nói đúng "không có gì". */
        val NONE = ClusterImportSummary(false, CastEnableDeferral.DEFAULT, false, 0, 0, 0, 0, 0, 0, 0)

        /** Tóm tắt từ kết quả [ClusterSnapshotPlan.mergeImport] của tệp `simple_cast_prefs`. */
        fun of(merge: ClusterSnapshotPlan.ImportMerge): ClusterImportSummary {
            fun apps(records: Set<String>) = records.map(CastGeometryGuard::appOfRecord).toSet().size
            return ClusterImportSummary(
                hasClusterPart = merge.fileHadFamily,
                castOn = merge.values[CastEnableDeferral.LIVE_KEY] as? Boolean ?: CastEnableDeferral.DEFAULT,
                castFromFile = CastEnableDeferral.LIVE_KEY !in merge.deferredFromCar,
                fileApps = apps(merge.fromFile),
                replacingApps = apps(merge.replacing),
                keptApps = apps(merge.fromCar),
                fileRecords = merge.fromFile.size,
                replacingRecords = merge.replacing.size,
                keptRecords = merge.fromCar.size,
                dropped = merge.dropped.size,
            )
        }
    }
}

/**
 * FIX286 · PI3 — kết quả một lượt nhập cho tầng UI: tên hồ sơ VỪA TẠO (khoá gốc, chưa dịch) + kiểu tệp + phần cụm.
 * Thay `String?` (chỉ tên) — hộp thoại cần nói thật nội dung tệp, không chỉ "đã nhập".
 */
data class ProfileImportReport(
    val name: String,
    val kind: ProfileTransfer.Kind,
    val cluster: ClusterImportSummary,
)

/** [WorkspaceRepository.importProfileData]: state mới (đã có hồ sơ nhập) + báo cáo của lượt nhập. */
data class ProfileImported(val state: HomeUiState, val report: ProfileImportReport)
