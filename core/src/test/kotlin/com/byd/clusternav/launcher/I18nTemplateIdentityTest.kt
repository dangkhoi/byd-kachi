package com.byd.clusternav.launcher

import com.byd.clusternav.comfort.Pm25Filter
import com.byd.clusternav.launcher.voice.VoiceAppEvidence
import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.launcher.voice.VoiceClarify
import com.byd.clusternav.launcher.voice.VoiceFeatureGone
import com.byd.clusternav.launcher.voice.VoiceFeedbackPhrase
import com.byd.clusternav.launcher.voice.VoiceIntent
import com.byd.clusternav.launcher.voice.VoiceIntentParser
import com.byd.clusternav.launcher.voice.VoiceReply
import com.byd.clusternav.launcher.voice.VoiceUnknownReason
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ R-nf1 — câu CÓ BIẾN chuyển `Strings.t("…$x…")` → `Strings.f("…{0}…")` ra Y TỪNG BYTE ở VI và EN ═══════════
 *
 * Mỗi bài dựng câu mong đợi **đúng theo mã CŨ** (chuỗi mẫu `$` của Kotlin + `old()` = luật `t` cũ hai nhánh), rồi so
 * với đầu ra của mã mới qua đường công khai. Phần lớn các câu tiếng Anh này chưa từng bị test nào khoá (kiểm kê
 * 2026-10-03) — đây là chỗ một lần chuyển tay lệch một dấu cách/số ít-nhiều sẽ lộ ra.
 */
class I18nTemplateIdentityTest {

    @AfterEach
    fun `dọn`() {
        Strings.current = Lang.VI
    }

    private val both = listOf(Lang.VI, Lang.EN)

    /** Luật `Strings.t` CŨ (trước zh/th/ms). */
    private fun old(l: Lang, vi: String, en: String) = if (l == Lang.EN) en else vi

    private fun <T> at(l: Lang, block: () -> T): T = I18nPairs.inLang(l, block)

    @Test
    fun `ma la, chu ky trinh chieu, ket luan lop`() = both.forEach { l ->
        for (code in listOf(0, 7, 255)) assertEquals(old(l, "mã $code", "code $code"), TelemetryEnums.unknown(code, l))
        for (sec in listOf(15, 59, 60, 300, 3599, 3600, 7200)) {
            val exp = when {
                sec < 60 -> old(l, "$sec giây", "$sec s")
                sec < 3600 -> old(l, "${sec / 60} phút", "${sec / 60} min")
                else -> old(l, "${sec / 3600} giờ", "${sec / 3600} h")
            }
            assertEquals(exp, at(l) { Slideshow.intervalLabel(sec) })
        }
        val low = old(l, "non", "low")
        fun tyres(vararg kpa: Double) = CarStatus.Tyres(pFlKpa = kpa[0], pFrKpa = kpa[1], pRlKpa = kpa[2], pRrKpa = kpa[3])
        for ((n, t) in listOf(1 to tyres(150.0, 250.0, 250.0, 250.0), 2 to tyres(150.0, 150.0, 250.0, 250.0))) {
            val exp = old(l, "$n bánh $low", "$n ${if (n == 1) "wheel" else "wheels"} $low")
            assertEquals(exp, at(l) { TyreBoard.verdict(TyreBoard.readings(t)) })
        }
    }

    @Test
    fun `ket luan bang cua va tom tat nhom`() = both.forEach { l ->
        val shut = CarStatus(
            body = CarStatus.Body(
                doorLfOpen = false, doorRfOpen = false, doorLrOpen = false, doorRrOpen = false,
                sunroofOpen = false, sunshadePct = 0,
            ),
        )
        fun footer(s: CarStatus) = at(l) { GroupBoard.doorPlan(GroupBoard.of(CapabilityGroups.DOORS, s)).footer }
        val n = GroupBoard.doorPlan(GroupBoard.of(CapabilityGroups.DOORS, CarStatus())).parts.size
        assertEquals(old(l, "$n bộ phận · chưa đọc được", "$n parts · not read yet"), footer(CarStatus()))
        val roofUnread = shut.body.copy(sunroofOpen = null)
        assertEquals(old(l, "Đã đóng · 1 chưa đọc được", "Closed · 1 not read yet"), footer(shut.copy(body = roofUnread)))
        assertEquals(old(l, "1 cửa mở", "1 door open"), footer(shut.copy(body = shut.body.copy(doorLfOpen = true))))
        val two = roofUnread.copy(doorLfOpen = true, doorRrOpen = true)
        assertEquals(old(l, "2 cửa mở   1 chưa đọc được", "2 doors open   1 not read yet"), footer(shut.copy(body = two)))

        // Tóm tắt ô nén — đếm sắc thái từ chính ô rồi dựng câu theo mã cũ; phải chạm đủ ba nhánh (1 · nhiều · lưu ý).
        val seen = HashSet<String>()
        listOf(
            CapabilityGroups.DOORS to shut.copy(body = shut.body.copy(doorLfOpen = true)),
            CapabilityGroups.DOORS to shut.copy(body = two),
            CapabilityGroups.TYRES to CarStatus(tyres = CarStatus.Tyres(250.0, 250.0, 250.0, 210.0)),
        ).forEach { (g, s) ->
            val m = at(l) { GroupBoard.of(g, s) }
            val alerts = m.cells.count { it.tone == GroupTone.ALERT }
            val warns = m.cells.count { it.tone == GroupTone.WARN }
            val exp = when {
                alerts > 0 -> old(l, "$alerts cảnh báo", "$alerts ${if (alerts == 1) "alert" else "alerts"}")
                    .also { seen += if (alerts == 1) "alert1" else "alertN" }
                warns > 0 -> old(l, "$warns lưu ý", "$warns to note").also { seen += "warn" }
                else -> return@forEach
            }
            assertEquals(exp, at(l) { m.summary() })
        }
        assertEquals(setOf("alert1", "alertN", "warn"), seen, "phép thử không chạm đủ nhánh")
    }

    @Test
    fun `goi lenh, muc ghe, ho so, dong noi dung nhom`() = both.forEach { l ->
        fun r(vararg ok: Pair<String, Boolean>) = MacroResult("m", ok.map { MacroStepResult(it.first, it.second) })
        assertEquals(old(l, "đủ 2 bước", "all 2 steps"), at(l) { r("a" to true, "b" to true).summary() })
        assertEquals(old(l, "không bước nào ăn (2 bước)", "no step took (2 steps)"), at(l) { r("a" to false, "b" to false).summary() })
        assertEquals(
            old(l, "1/3 bước ăn; hỏng: win_lf, win_rf", "1/3 steps took; failed: win_lf, win_rf"),
            at(l) { r("a" to true, "win_lf" to false, "win_rf" to false).summary() },
        )
        val vent = { raw: Int -> at(l) { TelemetryReadout.of("seat_vent_state", CarStatus(climate = CarStatus.Climate(seatVentRaw = raw)))!!.display } }
        assertEquals(old(l, "Mức 2", "Level 2"), vent(3))
        assertEquals(old(l, "Tắt", "Off"), vent(1))

        for ((preset, slots) in listOf(LayoutPreset.TWO_COL to 1, LayoutPreset.ONE to 2, null to 3)) {
            val layout = at(l) { preset?.label } ?: old(l, "Tự vẽ", "Custom")
            val en = if (slots == 1) "1 slot filled" else "$slots slots filled"
            assertEquals(old(l, "$layout · $slots ô có nội dung", "$layout · $en"), at(l) { ProfileNames.summary(preset, slots) })
        }
        CapabilityGroups.ALL.forEach { g ->
            val reads = g.visibleReadCount
            val exp = buildString {
                append("$reads " + old(l, "mục", if (reads == 1) "item" else "items"))
                if (g.writes.isNotEmpty()) append(" · ${g.writes.size} " + old(l, "nút", if (g.writes.size == 1) "button" else "buttons"))
                val s = at(l) { g.displaySub }
                if (s.isNotEmpty()) append(" · $s")
            }
            assertEquals(exp, at(l) { g.contentLine }, g.id)
        }
    }

    @Test
    fun `phim gan nut xe va chip thanh tren`() = both.forEach { l ->
        for (id in listOf("trunk", "readl", "seatc")) {
            val def = ControlRegistry.byId(id)!!
            val n = def.labelIn(l)
            if (def.kind != ControlKind.SELECT) {
                val flip = if (def.kind == ControlKind.COVER) old(l, "Mở/đóng $n (đảo)", "Open/close $n (toggle)")
                else old(l, "Bật/tắt $n (đảo)", "Toggle $n")
                assertEquals(flip, KeyCtlTargets.displayLabel(KeyCtlTarget(id, KeyCtlAction.FLIP), l))
            }
            val fix = when (def.kind) {
                ControlKind.COVER -> old(l, "gán Mở / Đóng riêng", "bind Open / Close separately")
                ControlKind.SELECT -> old(l, "gán từng mức", "bind each level")
                else -> old(l, "gán Bật / Tắt riêng", "bind On / Off separately")
            }
            assertEquals(old(l, "Không đọc được $n — ", "Can't read $n — ") + fix, KeyCtlPlan.unreadableReply(def, l))
        }
        val spec = "ctl:zzz:on"
        assertEquals(
            old(
                l,
                "Phím gán nút xe không còn hợp lệ ($spec) — gán lại ở Cài đặt › Nút vật lý",
                "This key binding is no longer valid ($spec) — rebind it in Settings › Physical buttons",
            ),
            KeyCtlPlan.invalidReply(spec, l),
        )

        fun chip(id: String, s: CarStatus) = at(l) { TopStripChips.render(TopStripConfig(listOf(id), showLabels = false), s).single() }
        for (level in listOf(1, null)) {
            val pm = level?.let { old(l, "Tốt", "Good") } ?: TelemetryView.PLACEHOLDER
            val c = chip(TopStripConfig.PM25, CarStatus(climate = CarStatus.Climate(pm25Level = level)))
            assertEquals(old(l, "Bụi mịn trong xe: $pm", "Fine dust in the car: $pm"), c.desc)
        }
        val temp = chip(TopStripConfig.TEMP, CarStatus(climate = CarStatus.Climate(outsideTempC = 31)))
        assertEquals(old(l, "Nhiệt độ ngoài xe ${temp.text}", "Outside temperature ${temp.text}"), temp.desc)
        for (soc in listOf(80, null)) {
            val e = chip(TopStripConfig.ENERGY, CarStatus(energy = CarStatus.Energy(soc = soc, evRangeKm = 300)))
            val ru = e.text.substringAfter("% · ")
            assertEquals(
                old(l, "Pin ${soc ?: "chưa đọc được"} phần trăm, đi thêm $ru", "Battery ${soc ?: "not read yet"} per cent, $ru to go"),
                e.desc,
            )
        }
    }

    @Test
    fun `loi bo cuc tu ve`() = both.forEach { l ->
        val c = WorkspaceGrid.COLS
        val r = WorkspaceGrid.ROWS
        val mc = WorkspaceGrid.MIN_COLS
        val mr = WorkspaceGrid.MIN_ROWS
        val small = GridFrame(0, 0, 1, 1)
        assertEquals(
            listOf(old(l, "khung 1 nhỏ quá (1×1), tối thiểu " + "$mc×$mr", "frame 1 is too small (1×1), minimum " + "$mc×$mr")),
            at(l) { GridLayout(listOf(small)).problems() },
        )
        assertEquals(
            listOf(old(l, "khung 1 ra ngoài lưới $c×$r", "frame 1 falls outside the $c×$r grid")),
            at(l) { GridLayout(listOf(GridFrame(c - 1, 0, 2, 1))).problems() },
        )
        val a = GridFrame(0, 0, 2, 1)
        assertEquals(
            listOf(old(l, "khung 1 và khung 2 đè lên nhau", "frame 1 and frame 2 overlap")),
            at(l) { GridLayout(listOf(a, a)).problems() },
        )
        val two = GridLayout(listOf(a, GridFrame(2, 0, 2, 1)))
        assertEquals(
            old(l, "bố cục tự vẽ có 2 khung, bản này đỡ tối đa 1", "the custom layout has 2 frames; this build supports at most 1"),
            at(l) { EffectiveLayout.ignoredReason(two, cap = 1) },
        )
    }

    @Test
    fun `cau tra loi giong noi`() = both.forEach { l ->
        val target = VoiceAppTargets.ALL.first()
        val label = target.label
        val i = VoiceIntent.OpenApp(label)
        at(l) {
            val def = ControlRegistry.byId("readl")!!
            assertEquals(
                old(l, "Tăng ", "Increase ") + def.displayLabel + " " + old(l, "2 nấc", "by 2"),
                VoiceReply.preview(VoiceIntent.Control("readl", relative = 2)),
            )
            assertTrue(VoiceReply.preview(VoiceIntent.OpenApp(label, slot = 3)).contains(old(l, " vào ô 3", " in slot 3")))
            assertTrue(
                VoiceReply.doneActual(VoiceIntent.Control("readl", value = 1), 0)
                    .startsWith("✓ " + old(l, "Đã gửi ${def.displayLabel} 1 — xe báo 0", "Sent ${def.displayLabel} 1 — the car reports 0")),
            )
            assertTrue(VoiceReply.slotOutOfRange(i, 4).contains(old(l, "bố cục hiện chỉ có 4 ô", "the current layout only has 4 slot(s)")))
            assertTrue(VoiceReply.appNotInstalled(i, target.key).contains(old(l, "chưa cài $label trên xe", "$label is not installed")))
            assertTrue(
                VoiceReply.musicAppOpened(i, target).endsWith(
                    " — " + old(
                        l,
                        "đã mở $label; chưa có phiên nhạc nào để điều khiển — bấm Play trong app",
                        "opened $label; no music session to control yet — press Play in the app",
                    ),
                ),
            )
            val unknown = target.copy(evidence = VoiceAppEvidence.UNKNOWN)
            assertTrue(
                VoiceReply.handedOver(i, unknown).contains(old(l, "chưa kiểm cửa nhận chữ của $label", "$label's hand-over door is not verified yet")),
            )
            assertTrue(
                VoiceReply.confirmPlace(i, target, "Chợ Bến Thành")
                    .startsWith(old(l, "Dẫn đường tới «Chợ Bến Thành» trên $label?", "Navigate to «Chợ Bến Thành» on $label?") + "\n"),
            )
            assertTrue(
                VoiceReply.navOpenedNoHandover(i, target).endsWith(
                    old(l, "$label chưa nhận điểm đến bằng giọng; gõ tay trong app", "$label takes no destination from outside; type it in the app"),
                ),
            )
            assertTrue(
                VoiceReply.navNoPlace(i, target).endsWith(
                    old(l, "chưa tra được điểm đến (mạng?), mới chỉ mở $label", "could not look the place up (network?) — only opened $label"),
                ),
            )
            assertTrue(
                VoiceReply.placeNotSaved(i, "Nhà ngoại").contains(
                    old(
                        l,
                        "chưa lưu địa chỉ «Nhà ngoại» — thêm ở Cài đặt › Dẫn đường › Sổ địa chỉ",
                        "no address saved for «Nhà ngoại» — add it in Settings › Navigation › Address book",
                    ),
                ),
            )
            assertTrue(
                VoiceReply.placeNeedsCoords(i, target).endsWith(
                    old(
                        l,
                        "$label chỉ nhận toạ độ; thêm lat/lng cho mục này trong Sổ địa chỉ",
                        "$label only takes coordinates — add lat/lng to this entry in the address book",
                    ),
                ),
            )
            assertTrue(VoiceReply.cancelled(i, 2).endsWith(old(l, ", 2 việc sau không chạy", ", 2 later step(s) not run")))
        }
    }

    @Test
    fun `tinh nang da bo, gop cau doc, hoi lai`() = both.forEach { l ->
        assertTrue(VoiceFeatureGone.ALL.any { it.removed } && VoiceFeatureGone.ALL.any { !it.removed }, "cần đủ hai loại dòng")
        VoiceFeatureGone.ALL.forEach { g ->
            val exp = if (g.removed) {
                old(
                    l,
                    "Tính năng ${g.label} đã bỏ khỏi Kachi — dùng màn hình của xe",
                    "${g.labelEn.replaceFirstChar { it.uppercase() }} was removed from Kachi — use the car's own screen",
                )
            } else {
                old(l, "Kachi chưa điều khiển được ${g.label} — dùng nút trên xe", "Kachi cannot control ${g.labelEn} yet — use the car's own control")
            }
            assertEquals(exp, at(l) { VoiceFeatureGone.reply(g) })
        }
        at(l) {
            val ok = VoiceReply.done(VoiceIntent.Control("readl", value = 1))
            // Vế hỏng NGẮN: câu gộp dài quá 12 từ thì bị cắt đuôi (clampWords) — đúng phần đuôi đang cần soi.
            assertEquals(old(l, "Chưa x, 2 việc khác đã xong", "Could not x, 2 other(s) done"), VoiceFeedbackPhrase.merge(listOf("✗ X", ok, ok)))
            assertEquals(old(l, "Đã xong 6 việc", "6 things done"), VoiceFeedbackPhrase.merge(List(6) { ok }))

            assertEquals(old(l, "Bật gì?", "Bật what?"), VoiceClarify.ask(VoiceIntent.Unknown(VoiceUnknownReason.NO_OBJECT, "bật"), 0)!!.question)
            val loc = VoiceClarify.ask(VoiceIntentParser.parseOne("lọc") as VoiceIntent.Unknown, 0)!!.question
            val (a, b) = listOf("pm25", "pm25_clean_now").map { ControlRegistry.byId(it)!!.displayLabel }
            assertEquals(old(l, "Lọc nào — $a hay $b?", "Which lọc — $a or $b?"), loc)
            val kinh = VoiceClarify.ask(VoiceIntent.Unknown(VoiceUnknownReason.NO_OBJECT, "mở kính"), 0)!!.question
            val list = if (l == Lang.EN) kinh.removePrefix("Which kính — ") else kinh.removePrefix("Kính nào — ")
            assertTrue(list != kinh && list.endsWith("?"), kinh)
            val items = list.removeSuffix("?").split(", ")
            assertTrue(items.size >= 3, "ca ≥ 3 lựa chọn: $kinh")
            assertTrue(items.last().startsWith(old(l, "hay ", "or ")), "vế cuối «, hay »/«, or »: $kinh")
        }
        assertEquals("—", Pm25Filter.levelLabel(Pm25Filter.INVALID, l))
        assertEquals(old(l, "Tốt", "Good"), Pm25Filter.levelLabel(Pm25Filter.GOOD, l))
    }
}
