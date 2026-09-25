package android.hardware.bydauto.light;

/**
 * ═══ STUB BIÊN DỊCH — KHÔNG vào dex, KHÔNG vào APK ══════════════════════════════════════════════════════════
 *
 * Lớp THẬT nằm trong framework của xe (`/system/framework/*bydauto*.jar`). Stub này chỉ tồn tại để
 * {@code javac} có một khai báo lớp cha mà subclass được; lúc chạy, dex nạp lớp THẬT từ boot classpath
 * (d8 nhận stub qua {@code --classpath} nên nó KHÔNG bị nhúng vào {@code classes.dex}).
 *
 * ── VÌ SAO PHẢI KHAI ĐỦ 8 PHƯƠNG THỨC ───────────────────────────────────────────────────────────────────────
 * [ĐO 2026-09-25] Giải mã {@code kinex_hal_api_service.jar} (bản CHẠY ĐƯỢC trên xe, lấy từ
 * {@code jadx-kinex/resources/assets/flutter_assets/assets/}) → {@code com.lexwah.kinex.hal.HalLightListener}
 * {@code extends AbsBYDAutoLightListener} và override ĐÚNG 8 phương thức dưới đây.
 *
 * Nếu lớp THẬT khai một trong 8 cái là {@code abstract} mà subclass của ta không cài, lúc chạy sẽ ngã
 * {@code InstantiationError}/{@code AbstractMethodError} — mà **compile vẫn xanh** (đúng họ lỗi "compile xanh
 * ≠ chạy được" của CLAUDE.md §8). Vì thế stub khai đủ 8, thân rỗng, để subclass override an toàn cả 8.
 *
 * ⚠ Ba callback {@code onTurnLight*} là đường tín hiệu xi-nhan ĐỘC LẬP với {@code onLightOn/Off} — xem
 * {@code com.byd.clusternav.hal.KachiHalMain} về lý do phải phủ cả bốn đường.
 *
 * ⚠ NỢ KỸ THUẬT (ghi ra, không giấu): {@code app/libs/bydauto-stubs.jar} là stub THỨ HAI cho cùng lớp này và
 * chỉ có 2 phương thức ({@code onLightOn}/{@code onLightOff}) — dùng bởi {@code TurnSignalListener} (đường uid
 * app, đã chết vì {@code SecurityException BYDAUTO_LIGHT_GET}). Track này KHÔNG đụng jar đó để không phá track
 * đang sửa controller; hợp nhất hai stub là việc của lượt dọn sau.
 */
public abstract class AbsBYDAutoLightListener {

    public AbsBYDAutoLightListener() {
    }

    /** Đèn BẬT. type: 2=cos, 3=pha, **4=xi-nhan TRÁI, 5=xi-nhan PHẢI**. */
    public void onLightOn(int type) {
    }

    /** Đèn TẮT. Cùng thang {@code type} với {@link #onLightOn(int)}. */
    public void onLightOff(int type) {
    }

    /** Xi-nhan đổi trạng thái, giá trị GỘP. 0/1=tắt cả hai · 2/3=trái bật · 4/5=phải bật. */
    public void onTurnLightStateChanged(int value) {
    }

    /** Xi-nhan đổi trạng thái, dạng TỪNG BÊN. id: 1=trái, 2=phải. value != 0 ⇒ bật. */
    public void onTurnLightStateChanged(int id, int value) {
    }

    /** Nháy xi-nhan đổi trạng thái — cùng thang GỘP với {@link #onTurnLightStateChanged(int)}. */
    public void onTurnLightFlashStateChanged(int value) {
    }

    public void onLightAutoSwitchOn() {
    }

    public void onLightAutoSwitchOff() {
    }

    public void onAFSSwitchStateChange(int state) {
    }
}
