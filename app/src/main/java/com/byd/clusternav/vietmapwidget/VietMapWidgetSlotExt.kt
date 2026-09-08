package com.byd.clusternav.vietmapwidget

import com.byd.clusternav.navigation.NavApps

import android.content.ComponentName

// §7 — MỘT nguồn sự thật cho tên gói (sửa 08-23 vòng 2b). Trước đây mỗi file widget tự chép chuỗi
// "vn.vietmap.live"; `NavPackageRosterSyncTest` không canh tới đây nên bản chép này trôi im lặng.
private const val VIETMAP_PACKAGE = NavApps.VIETMAP_LIVE

/**
 * Maps each [VietMapWidgetSlot] to its Android [ComponentName].
 * Lives in the app module because core is pure JVM and cannot reference Android classes.
 */
val VietMapWidgetSlot.component: ComponentName
    get() = when (this) {
        VietMapWidgetSlot.SPEED_LIMIT ->
            ComponentName(VIETMAP_PACKAGE, "$VIETMAP_PACKAGE.homewidget.VMOnlySpeedLimitWidgetProvider")
        VietMapWidgetSlot.ALERTS ->
            ComponentName(VIETMAP_PACKAGE, "$VIETMAP_PACKAGE.homewidget.VMOnlyStickyAlertWidgetProvider")
        VietMapWidgetSlot.ALERT_FULL ->
            ComponentName(VIETMAP_PACKAGE, "$VIETMAP_PACKAGE.homewidget.VMAlertWidgetProvider")
    }
