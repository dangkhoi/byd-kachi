package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ [SOÁT 2026-09-16 · P3] BẢN ĐỒ NHÃN→GÓI: không được gộp im lặng ═════════════════════════════════════════
 *
 * Luật chọn đã có bài kiểm **hành vi** ở `:core` (`VoiceAppLabelPickTest`). Bài này canh **dây nối**: đúng cái
 * mà bài kia không với tới được, vì `appsByLabel` phải có `PackageManager` mới chạy.
 *
 * Ba thứ phải cùng đúng thì lỗi *"nói tên này, mở app kia"* mới thật sự hết:
 *  1. bản đồ **không** còn dựng bằng `.toMap()` trên danh sách có thể trùng khoá;
 *  2. nó đi qua **đúng** luật chọn ở `:core` (không có bản sao thứ hai của luật ở tầng Android);
 *  3. nhãn nhập nhằng được **ghi log** — nếu không thì ngày nó xảy ra trên xe, không ai có gì để đọc.
 *
 * Và một ràng buộc cũ **không được vỡ**: bí danh sinh từ cách đọc (nay ở `VoiceAppIndex.build`, 2.91 — thay
 * `withPhonetics`) phải giữ `putIfAbsent`, tức một cách đọc suy ra được KHÔNG BAO GIỜ đè nhãn thật của app khác.
 */
class VoiceOpenAppLabelWiringContractTest {

    /** Nguồn đã **bỏ chú thích** — viết token vào comment là test xanh giả (cùng luật với mọi bài canh khác). */
    private val wiring: String =
        SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/voice/VoiceWiring.kt")

    @Test
    fun `nhan trung KHONG duoc gop im lang bang toMap`() {
        assertFalse(
            wiring.contains(".toMap()"),
            "`.toMap()` trên danh sách nhãn→gói giữ phần tử SAU CÙNG: hai app cùng nhãn (OEM + bản cài thêm) gộp " +
                "im lặng ⇒ người lái nói một tên, app khác mở lên, không một dòng log",
        )
        assertTrue(wiring.contains("VoiceAppLabelPick.of("), "phải đi qua luật chọn thuần ở :core, không tự xử ở đây")
        assertTrue(wiring.contains("VoiceAppLabelPick.Entry("), "đọc `PackageManager` xong thì đưa nguyên liệu vào luật")
    }

    @Test
    fun `nhan nhap nhang phai duoc ghi log, kem ten goi da chon`() {
        assertTrue(wiring.contains("picked.ambiguous"), "phải đọc danh sách nhãn nhập nhằng mà luật chọn nêu ra")
        assertTrue(wiring.contains("Log.w(TAG"), "ghi ở mức W: đây là một phép đoán thay người dùng, không phải tin vui")
    }

    @Test
    fun `co xet goi he thong — khong thi luat chon chi con thu tu chu cai`() {
        assertTrue(wiring.contains("FLAG_SYSTEM"), "luật (1) là 'gói KHÔNG phải hệ thống thắng' ⇒ phải đọc cờ đó")
        assertTrue(
            wiring.contains("FLAG_UPDATED_SYSTEM_APP"),
            "thiếu cờ thứ hai thì app OEM vừa nhận bản vá OTA bỗng bị coi là app người dùng tự cài",
        )
    }

    @Test
    fun `bi danh cach doc VAN khong duoc de nhan that`() {
        // 2.91 VOICE-APP-NAMES (senior review Pass 1, P3) — bản đồ nay dựng ở `VoiceAppIndex.build` (`:core`); bản đầu của
        // bài này quét `putIfAbsent` trong VoiceWiring.kt và chỉ còn xanh nhờ `withPhonetics` — hàm ĐÃ CHẾT (0 chỗ gọi). Canh
        // ĐÚNG chỗ đang chạy: VoiceWiring đi qua `VoiceAppIndex.build`, và trong `build` mọi khoá SUY (nhãn phụ + dạng đọc)
        // chỉ `putIfAbsent` sau khi nhãn thật đã cắm. Hành vi khoá ở `VoiceAppIndexTest` (`:core`).
        assertTrue(wiring.contains("VoiceAppIndex.build(picked.labels"), "bảng gọi app phải dựng qua VoiceAppIndex.build")
        assertFalse(wiring.contains("fun withPhonetics("), "đường dựng thứ hai (đã chết) không được sống lại")
        val build = SourceRoots.body(
            SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/voice/VoiceAppIndex.kt"), "fun build(",
        )
        assertTrue(build.contains("labels.forEach { (label, pkg) -> out[label] = pkg }"), "nhãn thật cắm TRƯỚC, đè được")
        assertTrue(
            build.contains("out.putIfAbsent(key, pkg)"),
            "khoá SUY phải `putIfAbsent`: nhãn là chữ người dùng NHÌN THẤY, một cách đọc suy ra được không bao giờ thắng nó",
        )
    }
}
