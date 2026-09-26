package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ U5 · T3 — BÀI CANH ĐA NGÔN NGỮ CHO TẦNG `:app` ═══════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-i18n-and-light-theme.html` §3.1 (R1 · R3). Năm tính chất, năm loại bằng chứng khác nhau:
 *
 *  1. **Quét MÃ NGUỒN** — không còn chuỗi tiếng Việt viết cứng trong tầng vẽ launcher (trừ danh sách loại trừ có lý do).
 *  2. **So HAI TỆP TÀI NGUYÊN** — cùng tập khoá, hai chiều.
 *  3. **Quét bản dịch** — `values-en/` không được còn dấu tiếng Việt.
 *  4. **Đếm dây nối** — đúng MỘT chỗ ghi `Strings.current`; đúng MỘT chỗ lưu lựa chọn ngôn ngữ.
 *  5. **Chốt an toàn dữ liệu** — `DEFAULT_PROFILE` không đi qua lớp dịch.
 *
 * ## ⚠⚠ Vì sao bài này nằm ở `:app`, và vì sao điều đó chưa đủ
 * Nó quét mã nguồn + tài nguyên của `:app`. Luật đã trả giá hai lần: *bài quét mã của module X phải NẰM trong module
 * X* — S1 đặt hai bài chống-rữa ở `:core` mà quét `:app` ⇒ `:core:test` báo **UP-TO-DATE** đúng ở ca chúng sinh ra để
 * bắt. Nửa còn lại của luật (G1) là **Gradle phải BIẾT thứ bài này quét**: `app/build.gradle.kts` đã khai
 * `inputs.dir("src/main/java")` **và** `inputs.dir("src/main/res")`, nên thêm một chuỗi tiếng Việt viết cứng **hoặc**
 * đổi một tệp tài nguyên đều làm task chạy lại. T3 đã tự chứng minh điều đó bằng cách thêm một chuỗi rồi chạy **không**
 * `--rerun-tasks`: bài đỏ đúng chỗ.
 *
 * ## Vì sao quét CHUỖI TRONG MÃ chứ không chỉ đếm khoá tài nguyên
 * Đếm khoá chỉ trả lời *"đã dịch những thứ đã dịch chưa"* — nó **không thể** thấy chuỗi thứ 119 mà ai đó viết thẳng
 * vào `TextView.text` ngày mai. Bệnh cần chữa là chuỗi viết cứng MỚI, nên phép kiểm phải soi mã, không soi tài nguyên.
 */
class LauncherI18nContractTest {

    // ══ (1) QUÉT MÃ NGUỒN — 0 chuỗi tiếng Việt viết cứng ═══════════════════════════════════════════════════

    /**
     * Chuỗi tiếng Việt được phép còn trong mã, **kèm lý do** (lệ [SettingsCatalog.NOT_SETTINGS]: danh sách loại trừ
     * phải bắt viết lý do, không thì nó thành chỗ làm im bài test).
     *
     * Khoá là **một mảnh** của chuỗi thật (đủ để nhận ra, ngắn để không rữa khi câu chữ đổi).
     */
    private val allowed: Map<String, String> = mapOf(
        // ── NHẬT KÝ — người dùng không bao giờ đọc; dịch nhật ký làm hỏng việc grep khi gỡ lỗi trên xe ──
        "hỏng giữa lượt chạy" to "nhật ký (Log.w) khi một bước gói lệnh ném — không hiện trên màn",
        "bỏ việc nền vì màn đã huỷ" to "nhật ký (Log.w) của cửa nền — không hiện trên màn",
        "tiến trình chính sống" to "nhãn trong dòng nhật ký đo thời gian ack của VoiceWakeHomeRelay (Log.i) — không hiện trên màn",
        "tiến trình chính lạnh" to "nhãn trong dòng nhật ký đo thời gian ack của VoiceWakeHomeRelay (Log.i) — không hiện trên màn",
        "việc nền bị từ chối" to
            "nhật ký (Log.w) của [SOÁT P3-2]: việc nền bị từ chối nên id widget vừa cấp được nhả — không hiện trên màn",
        "bỏ việc cửa sổ vì thread nền đã tắt" to "nhật ký (Log.w) của LauncherWindows — không hiện trên màn",
        "lỗi: " to "chuỗi mã lỗi shell, đi thẳng vào nhật ký của vòng kiểm quyền",
        "tự cấp " to "nhật ký (Log.i) của vòng kiểm quyền — không hiện trên màn",
        "sau khi tự cấp" to "nhật ký (Log.i) của vòng kiểm quyền — không hiện trên màn",
        "bật nhưng chưa có ảnh" to "nhật ký (Log.i) của hình nền; câu hiện trên màn là `kachi_wall_no_photos`",
        "ảnh không giải mã được" to "nhật ký (Log.w) của hình nền — không hiện trên màn",
        "không đọc được thư mục ảnh" to "nhật ký (Log.w) của WallpaperStore — không hiện trên màn",
        "không giải mã được ảnh" to "nhật ký (Log.w) của WallpaperStore — không hiện trên màn",
        // ── ÂM BÁO (1.70) — đi qua `VoiceChime.trip(why)` → `Log.w`; câu chẩn đoán cầu chì, không hiện trên màn ──
        "AudioTrack không khởi tạo được" to "lý do cầu chì âm báo (VoiceChime.trip → Log.w) — không hiện trên màn",
        "play() ném" to "lý do cầu chì âm báo (VoiceChime.trip → Log.w) — không hiện trên màn",
        "play() mất \$startMs ms (> \$MAX_START_MS)" to
            "lý do cầu chì âm báo (VoiceChime.trip → Log.w) — không hiện trên màn",
        "bỏ qua id KHÔNG thuộc host này" to
            "nhật ký (Log.w) của chốt bảo vệ badge tốc-độ VietMap: id đem thu hồi mà không thuộc host của launcher " +
                "thì bỏ qua. Ca này chỉ tới từ dữ liệu hỏng nên nó là dấu vết để GREP khi gỡ lỗi trên xe, " +
                "không phải câu nói với người lái",

        // ── LỖI LẬP TRÌNH — chỉ nổ khi mã sai, người dùng không bao giờ thấy ──
        "HomeViewModelFactory chỉ tạo HomeViewModel" to
            "thông điệp ngoại lệ cho LẬP TRÌNH VIÊN (ViewModel sai kiểu) — không phải chữ trên màn",

        // ── KHOÁ LƯU BỀN — dịch là MẤT DỮ LIỆU ──
        "Mặc định" to
            "TÊN HỒ SƠ MẶC ĐỊNH = TIỀN TỐ KHOÁ LƯU (`\"<hồ sơ>__preset\"`). Dịch thành \"Default\" làm mọi khoá cũ " +
                "(`Mặc định__preset`, `Mặc định__slot_0`…) thành mồ côi ⇒ người dùng mở lên thấy mất sạch cấu hình " +
                "mà KHÔNG có gì báo lỗi. Xem KDoc `HomeUiState.DEFAULT_PROFILE`",
    )

    /**
     * ═══ CHUỖI CHẨN ĐOÁN CHO NGƯỜI PHÁT TRIỂN — nhận ra bằng **cấu trúc**, không bằng danh sách tay ═════════
     *
     * V1 pha NGHE thêm sáu tệp `Voice*` với ~44 dòng nhật ký/ngoại lệ tiếng Việt. Chép cả 44 mảnh vào [allowed]
     * là làm danh sách ấy phình gấp bốn cho một nhóm tệp duy nhất — và mỗi mục sẽ mang đúng một lý do:
     * *"nhật ký (Log.w) — không hiện trên màn"*, y hệt 10 mục đã có ở đó. Khi một danh sách-kèm-lý-do bắt đầu
     * lặp lại cùng một lý do thì lý do ấy là một **LUẬT**, và luật thì phải viết thành mã.
     *
     * Bốn cấu trúc dưới đây **không bao giờ** là chữ trên màn:
     *  • `Log.*` — nhật ký; dự án cố ý KHÔNG dịch nó (xem KDoc `PermissionReport.logLine`: hai lần đo phải so
     *    được với nhau, nên log của máy tiếng Anh và máy tiếng Việt phải giống hệt);
     *  • `throw …(` / `error(` / `require(` / `check(` — thông điệp ngoại lệ, cũng là nhật ký;
     *  • `Lang.t(vi, en)` — **đã** là cặp song ngữ; đó chính là cơ chế mà bài này đòi, nên bắt nó là bắt nhầm.
     *
     * Phép nhận có tính tới lời gọi **nhiều dòng**: đếm ngoặc để biết lời gọi kết thúc ở đâu, thay vì chỉ soi
     * đúng dòng mang dấu hiệu (bản soi-một-dòng bỏ lọt mọi `Lang.t(` xuống dòng — tức mở một lỗ im lặng).
     */
    // `log[IDWE](` = bọc `Log.*` trong `runCatching` (HalSignalClient 2026-09-25: luồng socket chạy được trong JVM test
    // thuần, android.jar stub ném ở mọi Log.*) — vẫn là nhật ký, không hiện trên màn.
    private val DIAGNOSTIC_CALL = Regex("""(\bLog\.[a-z]+\(|\blog[IDWE]\(|\bthrow \w+\(|\berror\(|\brequire\(|\bcheck\(|\bLang\.t\()""")

    /** Chỉ số dòng nằm TRONG một lời gọi chẩn đoán (kể cả phần xuống dòng của nó). */
    private fun diagnosticLines(src: String): Set<Int> {
        val out = HashSet<Int>()
        var depth = 0
        src.lines().forEachIndexed { i, line ->
            if (depth > 0) {
                out.add(i)
                depth += line.count { it == '(' } - line.count { it == ')' }
                if (depth < 0) depth = 0
            } else if (DIAGNOSTIC_CALL.containsMatchIn(line)) {
                out.add(i)
                val start = DIAGNOSTIC_CALL.find(line)!!.range.last
                depth = line.substring(start).let { t -> t.count { it == '(' } - t.count { it == ')' } }
                if (depth < 0) depth = 0
            }
        }
        return out
    }

    @Test
    fun `0 chuoi tieng Viet viet cung trong tang ve launcher`() {
        val offenders = mutableListOf<String>()
        launcherSources().forEach { f ->
            val src = code(f)
            val diag = diagnosticLines(src)
            src.lines().forEachIndexed { i, line ->
                if (i in diag) return@forEachIndexed
                literals(line).forEach { lit ->
                    if (VN.containsMatchIn(lit) && allowed.keys.none { it in lit }) {
                        offenders += "${f.fileName}: \"$lit\""
                    }
                }
            }
        }
        assertEquals(
            emptyList<String>(), offenders,
            "chuỗi tiếng Việt viết cứng ⇒ người dùng English vẫn thấy tiếng Việt ở đúng chỗ đó. Đưa vào " +
                "`res/values/strings_kachi.xml` + `values-en/` rồi gọi `getString`; nếu thật sự KHÔNG phải chữ trên " +
                "màn (nhật ký / khoá lưu) thì khai vào `allowed` KÈM LÝ DO",
        )
    }

    /** Danh sách loại trừ không được rữa: mảnh nào không còn trong mã nữa thì phải bỏ khỏi danh sách. */
    @Test
    fun `danh sach loai tru khong bi rua`() {
        val all = launcherSources().joinToString("\n") { code(it) }
        val stale = allowed.keys.filterNot { it in all }
        assertEquals(emptyList<String>(), stale, "mảnh loại trừ không còn trong mã — bỏ khỏi `allowed` cho khỏi rữa")
        assertTrue(allowed.values.all { it.isNotBlank() }, "mỗi mục loại trừ phải kèm LÝ DO")
        // Chốt chống bộ quét hỏng mà vẫn xanh: quét rỗng thì "0 chuỗi viết cứng" là câu nói vô nghĩa.
        assertTrue(launcherSources().size >= 40, "bộ quét chỉ thấy ${launcherSources().size} tệp — nghi sai gốc quét")
    }

    // ══ (2) HAI TỆP TÀI NGUYÊN — CÙNG tập khoá, so hai chiều ═══════════════════════════════════════════════

    @Test
    fun `hai tep tai nguyen co cung tap khoa`() {
        // Dùng [declaredKeys] (gồm `<plurals>`) chứ không [stringKeys]: `kachi_drawer_place_n` là plurals, và một
        // phép so chỉ biết `<string>` sẽ bỏ nó ra khỏi phạm vi — tức khoá duy nhất có hai nhánh ngôn ngữ lại là khoá
        // KHÔNG được canh.
        val vi = declaredKeys(VI_XML)
        val en = declaredKeys(EN_XML)
        assertTrue(vi.size >= 100, "chỉ đọc được ${vi.size} khoá — nghi chính bộ đọc XML hỏng")
        assertEquals(
            emptyList<String>(), (vi - en).sorted(),
            "khoá CÓ ở values/ mà THIẾU ở values-en/ ⇒ người dùng English thấy đúng chỗ đó bằng tiếng Việt " +
                "(Android tự lùi về tệp mặc định, im lặng)",
        )
        assertEquals(
            emptyList<String>(), (en - vi).sorted(),
            "khoá CÓ ở values-en/ mà THIẾU ở values/ ⇒ `R.string` đó KHÔNG biên dịch được ở cấu hình mặc định",
        )
    }

    /** Tham số phải khớp: `%1$s` ở bản Việt mà bản Anh không có (hoặc ngược lại) là một lỗi ĐỊNH DẠNG lúc chạy. */
    @Test
    fun `tham so dinh dang khop giua hai ban dich`() {
        val vi = stringMap(VI_XML)
        val en = stringMap(EN_XML)
        // Chỉ so khoá CÓ Ở CẢ HAI: khoá thiếu một bên đã là việc của bài trên, và nếu ném ở đây thì thông điệp đỏ
        // sẽ là `NoSuchElementException` — đỏ đúng nhưng nói sai nguyên nhân.
        val bad = vi.keys.filter { it in en && args(vi.getValue(it)) != args(en.getValue(it)) }
        assertEquals(
            emptyList<String>(), bad.sorted(),
            "số/loại tham số lệch giữa hai bản dịch ⇒ `getString(...)` ném `IllegalFormatException` LÚC CHẠY, chỉ ở " +
                "một thứ tiếng. Đây là loại lỗi chỉ người dùng ngôn ngữ kia gặp",
        )
    }

    /**
     * Không có khoá MỒ CÔI theo cả hai chiều: khoá không ai dùng (chữ đã bỏ mà bản dịch còn) và mã gọi một khoá không
     * tồn tại (chỉ nổ lúc **biên dịch** với `R.string`, nhưng đọc ra trước thì rẻ hơn).
     *
     * ⚠ Chiều thứ nhất là chiều dễ rữa: xoá một dòng chữ khỏi mã thì không có gì đỏ, và một tháng sau người dịch vẫn
     * đang duy trì hai bản của một câu đã không còn hiện ra.
     */
    @Test
    fun `khong co khoa tai nguyen mo coi`() {
        val declared = declaredKeys(VI_XML)
        val used = allAppSources().flatMap { f ->
            Regex("""R\.(?:string|plurals)\.(kachi_\w+)""").findAll(code(f)).map { it.groupValues[1] }
        }.toSet()
        assertEquals(
            emptyList<String>(), (declared - used).sorted(),
            "khoá khai mà KHÔNG ai dùng ⇒ bản dịch của một câu đã bị bỏ; xoá khỏi cả hai tệp tài nguyên",
        )
        assertEquals(
            emptyList<String>(), (used - declared).sorted(),
            "mã gọi một khoá `kachi_*` không có trong values/ ⇒ hoặc khai thiếu, hoặc bộ đọc XML của bài này hỏng",
        )
    }

    // ══ (3) BẢN DỊCH không được còn tiếng Việt ═════════════════════════════════════════════════════════════

    @Test
    fun `values-en khong con dau tieng Viet`() {
        val offenders = stringMap(EN_XML).filterValues { VN.containsMatchIn(it) }.keys.sorted()
        assertEquals(
            emptyList<String>(), offenders,
            "câu tiếng Việt còn trong values-en/ ⇒ sai IM LẶNG: chỉ người dùng English gặp, và người soát bản Việt " +
                "không có cách nào thấy",
        )
    }

    /** Tiếng Anh ANH (owner đã chốt; `:core` đã dịch 123 datum theo lối đó — hai lối viết trên một màn đọc như lỗi). */
    @Test
    fun `ban dich dung tieng Anh Anh`() {
        val en = stringMap(EN_XML)
        val american = mapOf("tire" to "tyre", "color" to "colour", "license" to "licence", "trunk" to "boot")
        val offenders = en.entries.flatMap { (k, v) ->
            american.filter { (us, _) -> Regex("""\b$us""", RegexOption.IGNORE_CASE).containsMatchIn(v) }
                .map { (us, uk) -> "$k: '$us' → '$uk'" }
        }
        assertEquals(emptyList<String>(), offenders.sorted(), "dùng tiếng Anh ANH cho khớp 123 nhãn datum của :core")
    }

    // ══ (5) TẦNG VẼ không được đọc NHÃN GỐC của `:core` ═══════════════════════════════════════════════════

    /**
     * Biểu thức `.label` / `.sub` được phép còn trong tầng vẽ, **kèm lý do**.
     *
     * ## ⚠⚠ Bệnh nó chữa — [ĐO] máy ảo 2026-09-12, bằng ẢNH chứ không bằng test
     * Sau khi 118 chuỗi đã vào tài nguyên và bộ chọn ngôn ngữ đã chạy, ảnh chụp màn Cài đặt cho thấy **cột nhóm bên
     * trái tiếng VIỆT trong khi cột nội dung bên phải tiếng ANH** — trên cùng một màn hình. Nguyên nhân:
     * `SettingsPanel` đọc `group.label` (nhãn **GỐC**, luôn tiếng Việt theo giao kèo [Localized.label]) thay vì
     * `group.displayLabel`. Bài canh "0 chuỗi viết cứng" **không thể** thấy lỗi này: trong mã `:app` không có một chữ
     * tiếng Việt nào cả, chữ nằm ở `:core` và đi vào qua một thuộc tính đọc sai.
     *
     * [ĐO] cùng lối đó có **9 chỗ**: `SettingsPanel` ×2 (nhãn + câu phụ của rail) · `SettingsRows` (tên đại lượng) ·
     * `SettingsSectionsHome` + `AppDrawer` (tên lĩnh vực) · `AppDrawer` (tên widget) · `ControlTileFactory` ×2 (nhãn ô
     * + câu báo của gói lệnh) · `WorkspaceView` (tên widget trong ô). Tức đây là một **họ lỗi**, không phải một chỗ
     * lỡ tay — nên phải canh bằng máy.
     *
     * ## Vì sao canh bằng VĂN BẢN chứ không bằng kiểu
     * Bộ quét đọc mã nguồn, không có bảng kiểu, nên nó không biết `x.label` là `Localized` hay không. Đảo lại thành
     * *"mọi `.label` đều đáng ngờ, ai đúng thì khai lý do"* thì phép kiểm **không bỏ sót** — giá phải trả là danh sách
     * dưới đây, mà mỗi dòng của nó lại là một câu trả lời hữu ích cho câu hỏi *"nhãn này dịch ở đâu"*.
     */
    private val rawLabelAllowed: Map<String, String> = mapOf(
        "it.name to it.label" to
            "LayoutPreset · ImageFit · DockEdge — ba enum này TỰ dịch bên trong `:core` bằng `Strings.t`, nên " +
                "`label` của chúng ĐÃ theo ngôn ngữ (khác hẳn `Localized.label` vốn luôn là tiếng Việt gốc)",
        "it.name to it.label()" to "ThemeMode · LangMode — nhãn là HÀM, tự dịch bằng `Strings.t` bên trong `:core`",
        "m.label" to
            "`GroupCell`/`GroupActionCell` của `GroupBoard` — nhãn đã được `:core` điền từ `displayLabel`/" +
                "`displayShortLabel` lúc dựng bảng (xem `GroupBoard`), nên tầng vẽ chỉ chép lại",
        "cell.label" to "cùng lý do `m.label`: ô của `GroupBoard`, nhãn đã dịch từ trước khi tới tầng vẽ",
        // ⚠ Hai mục `c.label` (bảng sơ đồ bên) và `it.label} ·` (dòng chân bảng BOARD) đã gỡ 2026-09-16 cùng
        // `SideBoardView`/`RadarBoardView` — owner gỡ toàn bộ ADAS/an toàn nên hai ô vẽ đó không còn.
        "v.label" to
            "`TelemetryView` — `TelemetryReadout.of` điền `spec.displayLabel` vào đó, nên nhãn đã theo ngôn ngữ",
        "item.label" to "tên ứng dụng từ `PackageManager` — do HỆ THỐNG dịch, không phải chuỗi của dự án",
        "it.label," to "tên ứng dụng từ `PackageManager` (dựng `GridItem`) — cùng lý do `item.label`",
        "model.label" to
            "`SherpaModel.label` là TÊN RIÊNG của mô hình ASR (vd \"Zipformer VN (Apache-2.0, 70k h)\"), một danh " +
                "hiệu sản phẩm/giấy phép — KHÔNG phải `Localized.label` VI-gốc cần dịch. Cùng loại với `item.label` " +
                "(tên ứng dụng): danh từ riêng, không đổi theo ngôn ngữ giao diện",
        "pack.label" to
            "`VoicePack.label` — cùng lý do `model.label`, chỉ là dạng TỔNG QUÁT của nó (T8 gộp gói nghe + gói đọc " +
                "vào một hợp đồng). Vẫn là tên riêng: \"Piper VN — VAIS-1000 (medium)\"",
        // VOICE-HOTFIX 1.69 (H6 "đổi sang mô hình nhẹ") — thêm ba cách gọi của **cùng** `SherpaModel.label`, phải
        // khai riêng vì phép dò ở dưới so theo CHUỖI CON (`"model.label"` không phủ `model?.label`, dấu `?` chen
        // vào). ⚠ Hai mục `current.label` + `light.label` đã GỠ 2026-09-21 cùng khối chọn-mô-hình
        // (`VoiceModelSettings.lightModelRows`): danh mục mô hình nghe thu về một gói nên hai tên biến ấy không
        // còn tồn tại. Chính bài này bắt chúng ở lượt đó — đúng việc nó sinh ra để làm.
        "model?.label" to
            "cầu kiểm thử `state.voice_model.label`: một trường MÁY ĐỌC. Ở đây phải là tên riêng ỔN ĐỊNH, không " +
                "được đổi theo ngôn ngữ giao diện — nếu không thì một phép đo chạy ở máy tiếng Anh và một phép đo " +
                "ở máy tiếng Việt cho ra hai chuỗi khác nhau cho cùng một gói",
        "ttsPack.label" to
            "`SherpaTtsCatalog.TtsVoice.label` — tên riêng của gói giọng Piper, cùng lý do `model.label`",
        // U6 đã bỏ mục `"pick.sub"`: ngăn kéo nay đọc `pick.displaySub` (gợi ý loại + câu "gồm gì"), tức nó KHÔNG
        // còn chạm vào trường gốc nữa nên không cần được tha. Danh sách này phải tự rữa — giữ một dòng không còn ai
        // khớp là để dành sẵn một lỗ hổng cho lần sau.
        "def.args.size" to
            "SỐ LƯỢNG lựa chọn, không phải chữ — `displayArgs` lùi về `args` khi lệch số phần tử nên đếm trên `args` " +
                "là con số ổn định duy nhất; dùng `displayArgs.size` sẽ nói cùng con số nhưng che mất ý *đếm*",
    )

    @Test
    fun `tang ve khong doc nhan GOC cua core`() {
        val offenders = mutableListOf<String>()
        launcherSources().forEach { f ->
            // `args` cũng nằm đây: [ĐO] ảnh máy ảo cho thấy nút đóng/mở của ô kính hiện "Đ"/"M" (cắt từ "Đóng"/"Mở")
            // giữa một màn tiếng Anh, vì tầng vẽ đọc `def.args` thay vì `def.displayArgs`. Cùng họ lỗi với `label`:
            // `:core` ĐÃ có bản dịch, chỉ là chỗ đọc sai thuộc tính.
            Regex("""\.(label|sub|args)\b(?!\w)""").findAll(code(f)).forEach { m ->
                val line = lineAt(code(f), m.range.first)
                if (rawLabelAllowed.keys.none { it in line }) offenders += "${f.fileName}: ${line.trim()}"
            }
        }
        assertEquals(
            emptyList<String>(), offenders,
            "tầng vẽ đọc `label`/`sub`/`args` GỐC của `:core` — theo giao kèo `Localized.label` chúng LUÔN tiếng Việt, " +
                "nên chỗ đó không đổi khi người dùng chọn English (đúng lỗi rail Cài đặt tiếng Việt / nội dung tiếng " +
                "Anh mà máy ảo đã chụp được). Dùng `displayLabel`/`displaySub`/`displayArgs`; nếu nhãn đã dịch từ " +
                "trước thì khai vào `rawLabelAllowed` KÈM LÝ DO",
        )
        assertTrue(rawLabelAllowed.values.all { it.isNotBlank() }, "mỗi mục loại trừ phải kèm LÝ DO")
    }

    /**
     * ⚠⚠ **CHỖ BÍT LỖ HỔNG CỦA PHÉP ĐẾM BẰNG DẤU.** Bài `0 chuoi tieng Viet viet cung` ở trên tìm theo **dấu tiếng
     * Việt**, nên nó **không thể** thấy chữ Việt KHÔNG DẤU. [ĐO] 2026-09-12 nó bỏ sót đúng hai ca, và cả hai chỉ lộ ra
     * khi mở ảnh chụp máy ảo ra xem:
     *  • `"Xong"` — nút đóng màn Cài đặt (không một dấu nào);
     *  • `"khung"` — đơn vị đếm khung trong bảng vẽ bố cục.
     *
     * Nên bài này đảo chiều phép hỏi: **mọi** chuỗi trần đi vào một bề mặt chữ (`text =` · `hint =` · `setTitle` ·
     * `drawText` · `makeText` …) đều đáng ngờ, ai đúng thì khai lý do. Không cần biết chuỗi thuộc thứ tiếng nào — chỉ
     * cần biết nó **không đi qua tài nguyên**.
     *
     * ## ⚠⚠ [SOÁT ĐỘC LẬP 2026-09-12 · P1] BẢN ĐẦU CỦA BÀI NÀY BỎ SÓT ĐÚNG BỆNH NÓ SINH RA ĐỂ CHỮA
     * Nó chỉ soi **9 dạng gọi** (`text =`, `hint =`, `drawText(`…) — tức các bề mặt chữ *của Android*. Nhưng launcher
     * gần như không gán `text =` trực tiếp: nó vẽ chữ qua **hàm dựng view của riêng nó** (`miniCard(` · `ring(` ·
     * `label(` · `cell(` · `tv(` · `eyebrow(`), và chính kênh đó **không được quét**. [ĐO] hậu quả — ba chuỗi lọt qua
     * CẢ HAI bài canh, hai trong số đó đã **chụp được ảnh trên màn tiếng Anh**:
     *  • `"Xe"` — chữ chính của ô *Trạng thái xe* (`miniCard`), tiếng Việt không dấu ⇒ bài quét-theo-dấu mù;
     *  • `"pin"` — nhãn trong vòng đo *Năng lượng* (`ring` → `RingView.drawText`), cũng không dấu;
     *  • `"WIDGET"` — nhãn ô chưa chọn widget (`label`).
     *
     * Đau nhất: `kachi_widget_board` **ngay dòng bên cạnh** `"Xe"` đã đi đúng đường qua `getString` từ đầu ⇒ đây không
     * phải "chưa kịp làm", mà là ba chỗ bị bỏ lại trong khi chỗ thứ tư cùng loại thì đúng — đúng loại lệch mà chỉ máy
     * quét đủ rộng mới thấy. Nay [UI_SURFACE] gồm cả các hàm dựng view của launcher.
     *
     * ## Vì sao so bằng chuỗi ĐÃ BỎ NỘI SUY
     * `"${'$'}{it}µg"` và `"µg"` là **cùng một** ký hiệu đơn vị với người đọc, nên danh sách loại trừ phải nói được
     * *"µg thì tha"* một lần. Bản đầu so bằng chuỗi THÔ ⇒ mục `µg` không khớp được gì cả, tức nó là một **mục chết**
     * (đã kiểm: 0 chỗ trong mã có literal đúng bằng `"µg"`), và một mục chết trong danh sách loại trừ là chỗ người sau
     * tưởng đã được canh.
     */
    @Test
    fun `moi chu tren man deu di qua tai nguyen`() {
        val offenders = mutableListOf<String>()
        launcherSources().forEach { f ->
            code(f).lines().forEach { line ->
                if (!UI_SURFACE.containsMatchIn(line)) return@forEach
                literals(line).forEach { lit ->
                    val bare = stripInterpolation(lit)
                    val hasWord = Regex("""[A-Za-zÀ-ỹ]""").containsMatchIn(bare)
                    if (hasWord && !uiLiteralOk(bare)) offenders += "${f.fileName}: \"$lit\""
                }
            }
        }
        assertEquals(
            emptyList<String>(), offenders,
            "chuỗi trần đi thẳng vào một bề mặt chữ ⇒ nó KHÔNG đổi theo ngôn ngữ, kể cả khi không có dấu tiếng Việt " +
                "nào để phép quét theo dấu bắt được. Dùng `getString`; nếu là mẫu định dạng / ký hiệu thì khai vào " +
                "`uiLiteralAllowed` KÈM LÝ DO",
        )
    }

    /**
     * Chuỗi trần được phép đi vào bề mặt chữ — mẫu định dạng và ký hiệu, không phải câu chữ.
     *
     * ⚠ So bằng chuỗi **đã bỏ nội suy** (xem KDoc bài trên), nên mục `µg` phủ cả `"${'$'}{it}µg"`.
     */
    private val uiLiteralAllowed: Map<String, String> = mapOf(
        "HH:mm" to "mẫu định dạng giờ của SimpleDateFormat — ký hiệu API, không phải chữ cho người đọc",
        "EEEE, dd/MM" to "mẫu định dạng ngày của SimpleDateFormat; NGÔN NGỮ do `LangHost.locale()` quyết định",
        "dd/MM" to "mẫu định dạng ngày (không có tên thứ nên không phụ thuộc ngôn ngữ)",
        // Mã icon tra trong `KachiTheme.iconRes` — định danh tài nguyên, không phải chữ cho người đọc.
        // S4 · R12 thêm `ic-apps`/`ic-settings`: hai pill của thanh trên nay CHỈ có icon, và tên hình được truyền
        // thẳng vào `pill(...)` (chữ đã chuyển sang `contentDescription` lấy từ `R.string`).
        // V1 pha NGHE thêm `ic-mic`: nút thứ ba của thanh trên, cùng khuôn chỉ-icon với hai nút kia.
        *listOf("ic-sun", "ic-grid", "ic-bolt", "ic-leaf", "ic-speed", "ic-tire", "ic-music", "ic-lock",
            "ic-apps", "ic-settings", "ic-mic")
            .map { it to "mã icon tra trong `KachiTheme.iconRes`, không phải chữ" }.toTypedArray(),
        // Ký hiệu đơn vị SI + tên chuẩn của chỉ số bụi — viết y hệt ở mọi ngôn ngữ, dịch là làm sai.
        *listOf("km/h", " km/h", " km", "µg", "µg · ", "µg/m³", "PM2.5 · ", "PM2.5 ")
            .map { it to "ký hiệu đơn vị / tên chuẩn quốc tế — không dịch" }.toTypedArray(),
    )

    /**
     * Literal [bare] có được tha không.
     *
     * Hai nhóm: danh sách khai tay ở trên, **và** mã widget thật.
     *
     * ⚠ Mã widget (`w_energy`…) bị bắt vì bộ quét đọc theo DÒNG, mà nhánh `when (id)` nằm cùng dòng với lời gọi
     * `miniCard(`. Tha chúng bằng **[WidgetRegistry] thật** chứ không bằng 8 dòng khai tay: khai tay thì thêm một
     * widget mới là bài này đỏ oan, và người sửa sẽ học được rằng cách làm nó xanh là *nới danh sách loại trừ* — đúng
     * cái phản xạ làm bài canh mục ruỗng. Hỏi bộ đăng ký thì danh sách tự đúng, và một mã KHÔNG có trong đăng ký vẫn
     * bị bắt.
     */
    private fun uiLiteralOk(bare: String): Boolean =
        bare in uiLiteralAllowed.keys || WidgetRegistry.ALL.any { it.id == bare }

    /** Hai danh sách loại trừ còn lại cũng không được rữa — mục chết là chỗ người sau tưởng đã được canh. */
    @Test
    fun `hai danh sach loai tru con lai khong bi rua`() {
        val all = launcherSources().joinToString("\n") { code(it) }
        assertEquals(
            emptyList<String>(), rawLabelAllowed.keys.filterNot { it in all }.sorted(),
            "mục `rawLabelAllowed` không còn trong mã — bỏ khỏi danh sách cho khỏi rữa",
        )
        // ⚠ `uiLiteralAllowed` so theo chuỗi ĐÃ BỎ NỘI SUY, nên phép kiểm rữa cũng phải so như vậy — so bằng literal
        // thô ở đây sẽ báo mục `µg` là chết trong khi nó đang phủ `"${'$'}{it}µg"`.
        val bare = launcherSources().flatMap { literals(code(it)) }.map { stripInterpolation(it) }.toSet()
        assertEquals(
            emptyList<String>(), uiLiteralAllowed.keys.filterNot { it in bare }.sorted(),
            "mục `uiLiteralAllowed` không khớp literal nào trong mã — nó là MỤC CHẾT (đúng ca `µg` đã đo được: bản " +
                "đầu so bằng chuỗi thô nên mục đó không bao giờ khớp)",
        )
        assertTrue(uiLiteralAllowed.values.all { it.isNotBlank() }, "mỗi mục loại trừ phải kèm LÝ DO")
    }

    // ══ (6) DEFAULT_PROFILE không đi qua lớp dịch ══════════════════════════════════════════════════════════

    /**
     * Tên hồ sơ mặc định là **tiền tố khoá lưu**, không phải nhãn. Cho nó đi qua `getString`/`Strings.t`/`displayLabel`
     * là làm mọi khoá cũ thành mồ côi ⇒ người dùng thấy mất sạch cấu hình, **không có gì báo lỗi**.
     */
    @Test
    fun `DEFAULT_PROFILE khong di qua lop dich`() {
        listOf(
            "src/main/java/com/byd/clusternav/launcher/WorkspacePrefs.kt",
            "src/main/kotlin/com/byd/clusternav/launcher/HomeUiState.kt",
        ).forEach { rel ->
            val src = SourceRoots.codeOf(rel)
            val decl = Regex("""DEFAULT_PROFILE\s*=\s*([^\n]+)""").find(src)?.groupValues?.get(1)
            assertNotNull(decl, "$rel: không đọc được khai báo DEFAULT_PROFILE")
            assertEquals(
                "\"Mặc định\"", decl!!.trim(),
                "$rel: DEFAULT_PROFILE phải là chuỗi TRẦN. Bọc `getString`/`Strings.t` quanh nó = đổi tiền tố khoá " +
                    "lưu theo ngôn ngữ ⇒ mất sạch cấu hình của người dùng khi họ đổi ngôn ngữ",
            )
        }
        // Và không ai được dịch nó ở chỗ dùng.
        //
        // ⚠ Mẫu tìm phải là `getString(R.string` chứ KHÔNG phải `getString(`: [ĐO] bản đầu của bài này báo đỏ oan ở
        // `WorkspacePrefs` vì `SharedPreferences.getString(K_ACTIVE, DEFAULT_PROFILE)` — một hàm HOÀN TOÀN khác, chỉ
        // trùng tên. Đúng lối "số đo nói mã sai thì kiểm phép đo trước".
        val users = allAppSources().filter { code(it).contains("DEFAULT_PROFILE") }
        val bad = users.filter { f ->
            Regex("""(getString\(\s*R\.string|Strings\.t\(|Strings\.pick\()[^\n]*DEFAULT_PROFILE""")
                .containsMatchIn(code(f))
        }
        assertEquals(emptyList<String>(), bad.map { it.fileName.toString() }, "không được dịch DEFAULT_PROFILE")
    }

    // ── Hạ tầng ──────────────────────────────────────────────────────────────────────────────────

    private companion object {
        /** Dải dấu tiếng Việt. ⚠ KHÔNG dùng để ĐẾM (chữ không dấu như "khung" lọt) — chỉ để CHẶN. */
        val VN = Regex("[ăâđêôơưàáảãạằắẳẵặầấẩẫậèéẻẽẹềếểễệìíỉĩịòóỏõọồốổỗộờớởỡợùúủũụừứửữựỳýỷỹỵ]", RegexOption.IGNORE_CASE)

        /**
         * Bề mặt chữ: 9 dạng của Android + 6 hàm dựng view **của launcher** (xem KDoc
         * `moi chu tren man deu di qua tai nguyen` — thiếu nhóm sau là chỗ ba chuỗi P1 đã lọt).
         *
         * ⚠ `\b` KHÔNG phải trang trí: không có nó thì `ring\(` khớp **bên trong** `getString(` (…St‑`ring(`) và
         * `label\(` khớp bên trong `sectionLabel(`, làm bài này báo đỏ ở mọi lời gọi `sp.getString("preset", …)` —
         * tôi đã đo đúng lỗi đó khi dựng phép quét này (35 kết quả rác, hầu hết ở `WorkspacePrefs`).
         */
        val UI_SURFACE = Regex(
            """(text\s*=|hint\s*=|setTitle\(|sectionLabel\(|note\(|toast\(|makeText\(|drawText\(|pill\(""" +
                """|\bminiCard\(|\bcell\(|\bring\(|\blabel\(|\btv\(|\beyebrow\()""",
        )
        const val VI_XML = "src/main/res/values/strings_kachi.xml"
        const val EN_XML = "src/main/res/values-en/strings_kachi.xml"
    }

    private val host by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/LangHost.kt") }

    /** Tệp Kotlin của tầng vẽ launcher. */
    private fun launcherSources(): List<Path> =
        ktFiles(SourceRoots.path("src/main/java/com/byd/clusternav/launcher"))

    /** Cả cây `:app` — dùng cho phép đếm "đúng một chỗ ghi" (chỗ thứ hai có thể nằm ngoài `launcher/`). */
    private fun allAppSources(): List<Path> = ktFiles(SourceRoots.path("src/main/java/com/byd/clusternav"))

    private fun ktFiles(root: Path): List<Path> = Files.walk(root).use { s ->
        s.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.sorted().toList()
    }

    /** MÃ đã bỏ chú thích — KDoc của dự án viết bằng tiếng Việt nên quét thô sẽ báo sai gần như mọi tệp. */
    private fun code(f: Path): String = f.toFile().readText()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .lines().joinToString("\n") { line ->
            // Cắt `//` chỉ khi nó KHÔNG nằm trong một chuỗi (đếm dấu ngoặc kép phía trước).
            var i = line.indexOf("//")
            while (i >= 0 && line.take(i).count { it == '"' } % 2 != 0) i = line.indexOf("//", i + 1)
            if (i >= 0) line.take(i) else line
        }

    /** Chuỗi trần trong mã (bỏ chuỗi nhiều dòng — launcher không dùng). */
    private fun literals(src: String): List<String> =
        Regex(""""(?:\\.|[^"\\\n])*"""").findAll(src).map { it.value.trim('"') }.toList()

    /**
     * Bỏ phần NỘI SUY (`${…}` / `$x`) khỏi một chuỗi mẫu.
     *
     * Cần thiết vì `"$verdict · nhiệt"` và `"${a}km"` đều có chữ cái **của tên biến**, không phải chữ cho người đọc.
     * Không bỏ thì bài canh báo đỏ ở mọi chuỗi ghép — nhiễu tới mức phải tắt, tức bài canh chết.
     */
    private fun stripInterpolation(lit: String): String =
        lit.replace(Regex("""\$\{[^}]*}"""), "").replace(Regex("""\$\w+"""), "")

    /** Dòng chứa vị trí [at] — để phép kiểm nói ĐÚNG chỗ và để đối chiếu với danh sách loại trừ theo biểu thức. */
    private fun lineAt(src: String, at: Int): String {
        val start = src.lastIndexOf('\n', at).let { if (it < 0) 0 else it + 1 }
        val end = src.indexOf('\n', at).let { if (it < 0) src.length else it }
        return src.substring(start, end)
    }

    /**
     * MỌI khoá khai trong một tệp — gồm cả `<plurals>`.
     *
     * `<plurals>` phải được tính: `kachi_drawer_place_n` chuyển sang dạng đó (số ít/số nhiều là việc của Android, xem
     * chú thích trong `values-en/`), và một bộ đọc chỉ biết `<string>` sẽ coi nó là khoá mồ côi ⇒ đỏ oan.
     */
    private fun declaredKeys(rel: String): Set<String> =
        Regex("""<(?:string|plurals) name="([^"]+)"""").findAll(SourceRoots.text(rel))
            .map { it.groupValues[1] }.toSet()

    private fun stringMap(rel: String): Map<String, String> =
        Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(SourceRoots.text(rel))
            .associate { it.groupValues[1] to it.groupValues[2] }

    /** Tham số định dạng theo THỨ TỰ (`%1$s`, `%2$d`) — dùng để so hai bản dịch. */
    private fun args(value: String): Set<String> =
        Regex("""%(\d+)\$([sdf])""").findAll(value).map { "${it.groupValues[1]}${it.groupValues[2]}" }.toSet()
}
