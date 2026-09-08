package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Unit test THUẦN cho NavFormat — dọn/rút gọn tên đường VN + map lệnh rẽ → mã icon AMAP. */
class NavFormatTest {

    @Test fun `cleanRoadName bỏ động từ rẽ`() {
        assertEquals("Nguyễn Huệ", NavFormat.cleanRoadName("Rẽ phải vào Nguyễn Huệ"))
    }

    @Test fun `cleanRoadName bỏ tiền tố Đường-Phố`() {
        assertEquals("Nguyễn Huệ", NavFormat.cleanRoadName("Đường Nguyễn Huệ"))
    }

    @Test fun `cleanRoadName viết tắt loại đường có số`() {
        assertEquals("QL1A", NavFormat.cleanRoadName("Quốc lộ 1A"))
    }

    @Test fun `cleanRoadName GIỮ hầm-cầu (có nghĩa)`() {
        assertEquals("Hầm Thủ Thiêm", NavFormat.cleanRoadName("Hầm Thủ Thiêm"))
    }

    @Test fun `fitRoadName thang fallback — giữ từ cuối khi dạng chấm không vừa`() {
        // "N.H.Cảnh"(8) > ROAD_MAX_UNITS(7) → "NHCảnh"(6) giữ "Cảnh", KHÔNG rớt thẳng "NHC".
        assertEquals("NHCảnh", NavFormat.fitRoadName("Nguyễn Hữu Cảnh"))
    }

    @Test fun `fitRoadName giữ từ-loại hầm ở đầu`() {
        assertEquals("Hầm TT", NavFormat.fitRoadName("Hầm Thủ Thiêm"))
    }

    // ── T5: HUD street-name abbreviation — fitRoadName(road, maxUnits) + NFC (D4/F7/F8) ──

    @Test fun `fitRoadName 'Trần Trọng Kim' @7 → dotted 'T_T_Kim' (len 7)`() {
        val out = NavFormat.fitRoadName("Trần Trọng Kim", 7)
        assertEquals("T.T.Kim", out)
        assertEquals(7, out.length)
    }

    @Test fun `fitRoadName 'Võ Nguyên Giáp' @7 → 'VNGiáp' (chấm 8 gt 7 → dính-đầu giữ từ cuối)`() {
        // dotted "V.N.Giáp"=8 > 7 → gluedLast "VNGiáp"=6 ≤ 7 (giữ "Giáp"), KHÔNG rớt "VNG".
        assertEquals("VNGiáp", NavFormat.fitRoadName("Võ Nguyên Giáp", 7))
    }

    @Test fun `fitRoadName 'Võ Nguyên Giáp' @8 → dotted 'V_N_Giáp'`() {
        val out = NavFormat.fitRoadName("Võ Nguyên Giáp", 8)
        assertEquals("V.N.Giáp", out)
        assertEquals(8, out.length)
    }

    @Test fun `fitRoadName 'Nguyễn Hữu Cảnh' — thang @8 chấm @7 @6 dính @5 acronym`() {
        // Thang fallback owner 2026-08-15: dạng đọc-được-nhất còn vừa ngân sách; giữ TỪ CUỐI lâu nhất.
        assertEquals("N.H.Cảnh", NavFormat.fitRoadName("Nguyễn Hữu Cảnh", 8))   // chấm(8) vừa 8
        assertEquals("NHCảnh", NavFormat.fitRoadName("Nguyễn Hữu Cảnh", 7))      // chấm 8gt7 → dính giữ từ cuối(6)
        assertEquals("NHCảnh", NavFormat.fitRoadName("Nguyễn Hữu Cảnh", 6))      // dính vừa đúng 6
        assertEquals("NHC", NavFormat.fitRoadName("Nguyễn Hữu Cảnh", 5))         // dính 6gt5 → acronym(3)
    }

    @Test fun `fitRoadName 'Quốc lộ 1A' → 'QL1A' (viết tắt loại đường, cả @7 và default)`() {
        assertEquals("QL1A", NavFormat.fitRoadName("Quốc lộ 1A", 7))
        assertEquals("QL1A", NavFormat.fitRoadName("Quốc lộ 1A"))
    }

    @Test fun `fitRoadName 'hầm Thủ Thiêm' @7 → giữ từ-loại 'hầm TT'`() {
        // "hầm" ∈ KEEP_CLASS → giữ nguyên + viết tắt phần còn lại. "hầm TT" = 6 ≤ 7.
        assertEquals("hầm TT", NavFormat.fitRoadName("hầm Thủ Thiêm", 7))
    }

    @Test fun `fitRoadName single word — ngắn giữ nguyên, dài thì cắt`() {
        assertEquals("Láng", NavFormat.fitRoadName("Láng", 7))       // 1 từ, ≤ budget → nguyên
        assertEquals("Cati", NavFormat.fitRoadName("Catinat", 4))     // 1 từ, > budget → take(maxUnits)
    }

    @Test fun `fitRoadName empty-blank → empty`() {
        assertEquals("", NavFormat.fitRoadName("", 7))
        assertEquals("", NavFormat.fitRoadName("   ", 7))
        assertEquals("", NavFormat.fitRoadName("", NavFormat.HUD_ROAD_MAX_UNITS))
    }

    @Test fun `fitRoadName NFC-normalizes NFD input — length correct (không viết tắt oan)`() {
        // "Hà Nội": NFC = 6 code-unit (≤7 → GIỮ NGUYÊN). NFD (dấu tách rời) = 9 code-unit (>7).
        // Không normalize → tưởng dài >7 → viết tắt nhầm. Normalize xong đo đúng 6 → giữ nguyên.
        val nfc = "Hà Nội"
        val nfd = java.text.Normalizer.normalize(nfc, java.text.Normalizer.Form.NFD)
        assertTrue(nfd.length > nfc.length, "NFD phải nhiều code-unit hơn NFC")
        val out = NavFormat.fitRoadName(nfd, 7)
        assertEquals(NavFormat.fitRoadName(nfc, 7), out, "NFD phải cho KẾT QUẢ như NFC (normalize bên trong)")
        assertEquals(6, out.length, "đo theo NFC (6), KHÔNG phải NFD (9)")
        assertTrue(java.text.Normalizer.isNormalized(out, java.text.Normalizer.Form.NFC), "output ở dạng NFC")
        assertFalse(out.contains('\u0300'), "không sót combining grave (U+0300)")
    }

    @Test fun `fitRoadName NFC-normalizes NFD input — take(1) giữ dấu khi viết tắt`() {
        // "Ông Ích Khiêm" @7 >7 → thang xuống "dính-đầu + giữ từ cuối" = "ÔÍKhiêm". Chữ đầu MANG DẤU (Ô, Í):
        // take(1) trên NFD cắt base 'O'/'I' rời dấu kết hợp → mất dấu. Normalize NFC trước → giữ "ÔÍ..." nguyên vẹn.
        val nfd = java.text.Normalizer.normalize("Ông Ích Khiêm", java.text.Normalizer.Form.NFD)
        val out = NavFormat.fitRoadName(nfd, 7)
        assertEquals(NavFormat.fitRoadName("Ông Ích Khiêm", 7), out)
        assertEquals("ÔÍKhiêm", out)
        assertEquals(7, out.length, "dính-đầu 'ÔÍ' + từ cuối 'Khiêm' = 7 code-unit NFC")
        assertTrue(java.text.Normalizer.isNormalized(out, java.text.Normalizer.Form.NFC))
        assertFalse(out.contains('O'), "KHÔNG thoái hoá về ASCII 'O' (mất dấu Ô)")
        assertFalse(out.contains('\u0302'), "không sót combining circumflex (U+0302)")
    }

    @Test fun `fitRoadName byte-budget ≤ 2×maxUnits (UTF-16LE, đúng buffer BYTE của cụm-HUD)`() {
        val samples = listOf(
            "Trần Trọng Kim", "Võ Nguyên Giáp", "Nguyễn Hữu Cảnh",
            "Điện Biên Phủ", "Cách Mạng Tháng Tám", "hầm Thủ Thiêm", "Quốc lộ 1A", "Hà Nội"
        )
        for (u in listOf(7, 8)) for (r in samples) {
            val out = NavFormat.fitRoadName(r, u)
            val bytes = out.toByteArray(Charsets.UTF_16LE).size
            assertTrue(bytes <= 2 * u, "'$r'@$u → '$out' (${out.length} unit) = $bytes byte > ${2 * u}")
            assertTrue(out.length <= u, "'$r'@$u → '$out' dài ${out.length} unit > $u")
        }
    }

    @Test fun `fitRoadName default-arg == gọi tường minh ROAD_MAX_UNITS (backward-compat cụm)`() {
        val samples = listOf(
            "Nguyễn Hữu Cảnh", "Trần Trọng Kim", "Quốc lộ 1A", "Hầm Thủ Thiêm",
            "Võ Nguyên Giáp", "Hà Nội", "Láng", "", "Rẽ phải vào Nguyễn Huệ"
        )
        for (r in samples) {
            assertEquals(
                NavFormat.fitRoadName(r, NavFormat.ROAD_MAX_UNITS), NavFormat.fitRoadName(r),
                "default-arg phải == ROAD_MAX_UNITS cho '$r'"
            )
        }
    }

    @Test fun `HUD_ROAD_MAX_UNITS = 20 (v1_00 raised from 7 after SL6 evidence)`() {
        // v1.00: raised from 7 to 20 UTF-16 units after SL6 showed firmware accepts longer names.
        // Seal-05 vehicle gate will confirm 20 visually on Seal. If rejected, lower once from evidence.
        assertEquals(20, NavFormat.HUD_ROAD_MAX_UNITS)
    }

    @Test fun `roadWindow tên ngắn trả nguyên, tên dài đúng bề rộng`() {
        assertEquals("ABC", NavFormat.roadWindow("ABC", 5, 7))
        val w = NavFormat.roadWindow("ABCDEFGHIJ", 0, 5)
        assertEquals(5, w.length)
        assertEquals("ABCDE", w)
    }

    @Test fun `maneuverVerbIcon map đúng hướng`() {
        assertEquals(3, NavFormat.maneuverVerbIcon("Rẽ phải vào X"))
        assertEquals(2, NavFormat.maneuverVerbIcon("rẽ trái"))
        assertEquals(8, NavFormat.maneuverVerbIcon("quay đầu"))
        assertEquals(9, NavFormat.maneuverVerbIcon("đi thẳng"))
        assertEquals(11, NavFormat.maneuverVerbIcon("vòng xuyến"))
        assertNull(NavFormat.maneuverVerbIcon("blah blah"))
    }

    @Test fun `Track B — maneuverVerbIcon merge nhập-làn ra đi-thẳng 9 (KHÔNG chếch phải)`() {
        // Bug owner: "nhập làn / merge" từng hiện mũi tên rẽ/chếch PHẢI. 0..28 không có glyph merge → 9 = đi thẳng.
        assertEquals(9, NavFormat.maneuverVerbIcon("nhập làn"))
        assertEquals(9, NavFormat.maneuverVerbIcon("Nhập làn vào Tôn Đức Thắng"))
        assertEquals(9, NavFormat.maneuverVerbIcon("merge"))
        // "merge left onto X": nhánh merge phải xét TRƯỚC turn ("left onto") → 9, KHÔNG phải 2 (rẽ trái).
        assertEquals(9, NavFormat.maneuverVerbIcon("merge left onto Highway 1"))
    }

    @Test fun `Track B — maneuverVerbIcon tunnel-hầm ra 16 (glyph sắp vào hầm)`() {
        assertEquals(16, NavFormat.maneuverVerbIcon("vào hầm"))
        assertEquals(16, NavFormat.maneuverVerbIcon("đường hầm"))
        assertEquals(16, NavFormat.maneuverVerbIcon("enter tunnel"))
        // Rẽ có ưu tiên hơn hầm: "rẽ phải vào hầm" phải giữ rẽ phải (3), không đè thành hầm.
        assertEquals(3, NavFormat.maneuverVerbIcon("rẽ phải vào hầm X"))
    }

    @Test fun `Track B — roundaboutExit rút số nhánh cho ROUNG_ABOUT_NUM (encode vòng-xuyến lối-ra N)`() {
        // NavFormat cấp SỐ nhánh; AmapFrameBuilder ép NEW_ICON 11 + putExtra ROUNG_ABOUT_NUM=N → AmapService
        // remap CAN 25..34 (RHT/CCW lối ra N). Đây là nguồn dữ liệu cho glyph "lối ra thứ N".
        assertEquals(3, NavFormat.roundaboutExit("Đi vào vòng xuyến và ra ở lối ra thứ 3"))
        assertEquals(2, NavFormat.roundaboutExit("At the roundabout, take the 2nd exit"))
        assertEquals(1, NavFormat.roundaboutExit("take the 1st exit"))
        assertEquals(-1, NavFormat.roundaboutExit("đi thẳng"))   // không có số → -1 → builder gửi NEW_ICON thường
    }

    @Test fun `maneuverToAmapIcon fallback đi thẳng`() {
        assertEquals(9, NavFormat.maneuverToAmapIcon("chuỗi không có động từ"))
    }

    @Test fun `roundaboutExit rút số nhánh`() {
        assertEquals(3, NavFormat.roundaboutExit("lối ra thứ 3"))
        assertEquals(2, NavFormat.roundaboutExit("take the 2nd exit"))
        assertEquals(-1, NavFormat.roundaboutExit("đi thẳng"))
    }

    @Test fun `asciiToken bỏ dấu + gộp ký tự lạ`() {
        assertEquals("Nguyen_Hue", NavFormat.asciiToken("Nguyễn Huệ"))
        assertEquals("Duong", NavFormat.asciiToken("Đường"))
        assertEquals("Road", NavFormat.asciiToken("!!!"))   // rỗng sau khi lọc → fallback
    }

    @Test fun `asciiToken an toàn cho shell-arg (chỉ chữ-số-gạch dưới)`() {
        val t = NavFormat.asciiToken("Cầu Sài Gòn (Q.2); rm -rf /")
        assertTrue(Regex("^[A-Za-z0-9_]+$").matches(t), "token phải an toàn shell: '$t'")
    }
}
