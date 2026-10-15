package android.hardware;

import android.hardware.bydauto.BYDAutoEventValue;

/**
 * ═══ STUB BIÊN DỊCH — KHÔNG vào dex (d8 nhận qua {@code --classpath}) ═══════════════════════════════════════════
 *
 * Giao diện THẬT ở framework xe. [ĐO nguồn fw 2606 {@code android/hardware/IBYDAutoListener.java}] đúng BA phương thức
 * dưới đây — helper cài đủ cả ba (thiếu một ⇒ {@code AbstractMethodError} lúc HAL gọi, trong khi compile vẫn xanh).
 * {@code AbsBYDAutoDevice.onPostEvent} gọi {@code onDataChanged} RỒI {@code onDataEventChanged} cho mỗi listener
 * khớp id (fw 2606 {@code AbsBYDAutoDevice.java:221-236}).
 */
public interface IBYDAutoListener {
    void onDataChanged(IBYDAutoEvent event);

    void onDataEventChanged(int featureId, BYDAutoEventValue value);

    void onError(int code, String message);
}
