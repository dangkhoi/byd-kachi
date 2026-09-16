package com.byd.clusternav.launcher

/**
 * ═══ CÔNG CỤ KIỂM TRA TỪNG NÚT TRÊN XE (owner 2026-09-15) ══════════════════════════════════════════════════
 *
 * Owner muốn một bảng đi hết **mọi thông tin + hành động** của xe, bấm chạy từng cái, tự tay chấm **OK / Không OK**,
 * ghi lại — để lên xe soát cạn từng nút một. Đây là mô hình THUẦN ở `:core` (dựng danh sách + chấm + mã hoá nhật ký),
 * UI ở `:app` chỉ hiện ra. Spec `docs/specs/kachi-capability-test.html`.
 *
 * ## Vì sao dựng từ [TelemetryRegistry]/[ControlRegistry] chứ không chép tay
 * Hai bộ đăng ký là NGUỒN SỰ THẬT của 123 datum + 64 nút. Chép tay ra một danh sách thứ hai là mở đúng cái bẫy
 * hai-bản-sao: thêm nút mới mà quên bảng test ⇒ nút không bao giờ được soát. Dựng động ⇒ mọi capability tự có mặt.
 * Diễn giải một dòng lấy từ [CapabilityDescriptions] (tách riêng để không đụng phép đếm nhãn của hai registry).
 */

/** Loại mục kiểm tra: [INFO] chỉ ĐỌC (hiện giá trị, không có nút Chạy) · [ACTION] có HÀNH ĐỘNG (nút Chạy). */
enum class CapTestKind { INFO, ACTION }

/** Kết quả chấm của một mục. [UNTESTED] = chưa soát. */
enum class CapTestVerdict { UNTESTED, OK, NOT_OK }

/**
 * Một mục trong bảng kiểm tra — gói đủ thứ UI cần để hiện popup "đang chạy nút X → diễn giải → Chạy → OK/Không OK".
 *
 * @property runArg tham số CHẠY cho [ACTION] (mặt "làm việc" của nút: bật/mở/bấm — [HalBindingTable.defaultPrimary]).
 *   [INFO] để 0 (không dùng).
 * @property needsConfirm nút chạm phần cứng thân xe (kính/cửa/cốp/ca-pô/nóc) — UI phải cảnh báo trước khi Chạy.
 * @property bindingKey khoá HAL — UI hiện "đã map / chưa map (chờ đo)" để biết nút nào còn NEEDS_CAR.
 */
data class CapTestItem(
    val id: String,
    val kind: CapTestKind,
    val label: String,
    val labelEn: String,
    val descVi: String,
    val descEn: String,
    val domain: Domain,
    val needsConfirm: Boolean,
    val runArg: Int,
    val bindingKey: String,
) {
    /** Nhãn theo ngôn ngữ đang chọn. */
    val displayLabel: String get() = Strings.t(label, labelEn)

    /** Diễn giải theo ngôn ngữ đang chọn. */
    val displayDesc: String get() = Strings.t(descVi, descEn)

    /** Khoá HAL đã map thật chưa (rỗng / lệnh-bọc UPPER_SNAKE = chưa) — dùng cho huy hiệu "chờ đo". */
    val isRouted: Boolean get() = HalBindingTable.routeOf(bindingKey) !is BindingRoute.None
}

/** Dựng danh sách mục kiểm tra từ hai bộ đăng ký — thứ tự theo DOMAIN, trong mỗi domain: thông tin trước, hành động sau. */
object CapabilityTestPlan {

    /** Toàn bộ mục kiểm tra, đã xếp theo domain (khối vật lý) để lên xe soát gọn từng cụm. */
    fun items(): List<CapTestItem> = buildList {
        Domain.values().forEach { d ->
            TelemetryRegistry.ALL.filter { it.domain == d }.forEach { t ->
                val desc = CapabilityDescriptions.of(t.id)
                val en = t.labelEn ?: t.label
                add(
                    CapTestItem(
                        id = t.id, kind = CapTestKind.INFO, label = t.label, labelEn = en,
                        descVi = desc?.vi ?: t.label, descEn = desc?.en ?: en,
                        domain = d, needsConfirm = false, runArg = 0, bindingKey = t.bindingKey,
                    ),
                )
            }
            ControlRegistry.ALL.filter { it.domain == d }.forEach { c ->
                val desc = CapabilityDescriptions.of(c.id)
                val en = c.labelEn ?: c.label
                add(
                    CapTestItem(
                        id = c.id, kind = CapTestKind.ACTION, label = c.label, labelEn = en,
                        descVi = desc?.vi ?: c.label, descEn = desc?.en ?: en,
                        domain = d, needsConfirm = CtlSafetyPolicy.needsConfirm(c.id),
                        runArg = HalBindingTable.defaultPrimary(c), bindingKey = c.bindingKey,
                    ),
                )
            }
        }
    }

    /**
     * Tổng số mục — đếm THẲNG trên hai bộ đăng ký thay vì dựng lại cả 190 [CapTestItem] (mỗi cái kéo theo một
     * lượt tra [CapabilityDescriptions] + [CtlSafetyPolicy]) chỉ để lấy một con số.
     */
    fun total(): Int = TelemetryRegistry.ALL.size + ControlRegistry.ALL.size
}

/** Một dòng nhật ký chấm điểm. */
data class CapTestResult(val id: String, val verdict: CapTestVerdict, val tsMillis: Long, val note: String = "")

/**
 * Mã hoá/giải mã nhật ký chấm điểm sang chuỗi bền (một khối, lưu vào prefs `kachi_captest`). THUẦN ⇒ test off-car.
 * Phân tách TRƯỜNG bằng TAB (`\t`) và DÒNG bằng `\n` — cả hai đều là ký tự XML hợp lệ (an toàn qua
 * SharedPreferences XML) và không xuất hiện trong id/verdict/ts; ghi chú bị lọc bỏ hai ký tự này khi mã hoá.
 */
object CapTestCodec {
    private const val FS = "\t"

    fun encode(r: CapTestResult): String =
        listOf(r.id, r.verdict.name, r.tsMillis.toString(), scrub(r.note)).joinToString(FS)

    /**
     * ⚠ Phải lọc CẢ `\r`, không chỉ `\n`: [decodeAll] tách bản ghi bằng `lineSequence()`, và hàm đó coi `\r`,
     * `\n` và `\r\n` **đều** là hết dòng. Một ghi chú dán từ máy Windows (hoặc từ một lần chép log) mang `\r`
     * sẽ cắt bản ghi làm hai ⇒ nửa sau rớt ở `p.size < 3` và ghi chú bị cụt **trong im lặng** — đúng họ lỗi mà
     * `SlotCodec.SEP` đã trả giá một lần.
     */
    private fun scrub(note: String): String =
        note.replace('\n', ' ').replace('\r', ' ').replace(FS, " ")

    fun decode(line: String): CapTestResult? {
        val p = line.split(FS)
        if (p.size < 3) return null
        val verdict = runCatching { CapTestVerdict.valueOf(p[1]) }.getOrNull() ?: return null
        val ts = p[2].toLongOrNull() ?: return null
        return CapTestResult(p[0], verdict, ts, p.getOrElse(3) { "" })
    }

    fun encodeAll(map: Map<String, CapTestResult>): String =
        map.values.sortedBy { it.id }.joinToString("\n") { encode(it) }

    fun decodeAll(blob: String): Map<String, CapTestResult> =
        blob.lineSequence().mapNotNull { decode(it) }.associateBy { it.id }
}

/**
 * Dựng BÁO CÁO chữ của một lượt soát — pure ở `:core` (dùng [Strings.t] + nhãn song ngữ) để KHÔNG dính chuỗi Việt
 * viết cứng ở `:app` (bài canh i18n) và test được off-car. Đây là "tài liệu" owner muốn, luôn tươi theo kết quả.
 */
object CapTestReport {
    fun build(results: Map<String, CapTestResult>): String {
        val items = CapabilityTestPlan.items()
        val sum = CapTestSummary.of(items, results)
        val sb = StringBuilder()
        sb.append(Strings.t("KIỂM TRA TỪNG NÚT — Kachi", "CAR FUNCTION CHECK — Kachi")).append('\n')
        sb.append(Strings.t("OK ", "OK ")).append(sum.ok)
            .append(Strings.t(" · Không OK ", " · Not OK ")).append(sum.notOk)
            .append(Strings.t(" · Chưa soát ", " · Not checked ")).append(sum.untested)
            .append(" / ").append(sum.total).append("\n\n")
        var lastDomain: Domain? = null
        items.forEach { item ->
            if (item.domain != lastDomain) {
                sb.append("── ").append(item.domain.displayLabel).append(" ──\n")
                lastDomain = item.domain
            }
            val mark = when (results[item.id]?.verdict) {
                CapTestVerdict.OK -> "[OK] "
                CapTestVerdict.NOT_OK -> "[X]  "
                else -> "[ ]  "
            }
            val kind = if (item.kind == CapTestKind.INFO) Strings.t("(tin)", "(info)") else Strings.t("(nút)", "(action)")
            sb.append(mark).append(kind).append(' ').append(item.displayLabel)
                .append(" — ").append(item.displayDesc)
            results[item.id]?.note?.takeIf { it.isNotBlank() }
                ?.let { sb.append(Strings.t("  · ghi chú: ", "  · note: ")).append(it) }
            sb.append('\n')
        }
        return sb.toString()
    }
}

/** Tổng kết một lượt soát: số mục OK / Không OK / chưa soát, trên tổng số. */
data class CapTestSummary(val total: Int, val ok: Int, val notOk: Int) {
    val tested: Int get() = ok + notOk
    val untested: Int get() = total - tested

    companion object {
        fun of(items: List<CapTestItem>, results: Map<String, CapTestResult>): CapTestSummary {
            var ok = 0
            var notOk = 0
            items.forEach { item ->
                when (results[item.id]?.verdict) {
                    CapTestVerdict.OK -> ok++
                    CapTestVerdict.NOT_OK -> notOk++
                    else -> {}
                }
            }
            return CapTestSummary(items.size, ok, notOk)
        }
    }
}
