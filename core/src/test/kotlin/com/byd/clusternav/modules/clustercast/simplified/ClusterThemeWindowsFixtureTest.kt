package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * CLUSTER-THEME-SAFE B1a · test C.3 mục 8 — [ClusterThemePlan.parseWindows] trên dump THẬT của xe.
 *
 * Fixture = trích NGUYÊN VĂN `docs/diagnostics/carlog-kachi-20260914-2044/10-window-windows.txt` (Seal, 2026-09-14 20:46,
 * `adb shell dumpsys window windows`): dòng 4–40 (tiêu đề + trọn khối `Window #0` + đầu `Window #1`), cặp dòng
 * tiêu đề + `mDisplayId` của `Window #2…#15`, và dòng `mObscuringWindow=Window{…}` ở đuôi. Thư mục `carlog-*` bị gitignore
 * (bản `getprop` cùng phiên có VIN) nên trích vào đây thay vì đọc tệp — không dòng nào chứa định danh xe/người.
 * Fixture màn ảo cụm khi đang chiếu / Idle có ClusterBlack (bước đo V2–V3) [CHƯA CÓ] — 🚗.
 */
class ClusterThemeWindowsFixtureTest {

    private val dump2026_09_14 = """
        |WINDOW MANAGER WINDOWS (dumpsys window windows)
        |  Window #0 Window{739f3cf u0 InputMethod}:
        |    mDisplayId=0 stackId=0 mSession=Session{b250230 3982:u0a10062} mClient=android.os.BinderProxy@bbadc2e
        |    mOwnerUid=10062 mShowToOwnerOnly=true package=com.android.inputmethod.latin appop=NONE
        |    mAttrs={(0,0)(fillxfill) gr=BOTTOM CENTER_VERTICAL sim={adjust=pan} ty=INPUT_METHOD fmt=TRANSPARENT wanim=0x1030056
        |      fl=NOT_FOCUSABLE LAYOUT_IN_SCREEN LAYOUT_NO_LIMITS SPLIT_TOUCH HARDWARE_ACCELERATED DRAWS_SYSTEM_BAR_BACKGROUNDS
        |      vsysui=LAYOUT_STABLE LAYOUT_FULLSCREEN}
        |    Requested w=1920 h=996 mLayoutSeq=290
        |    mIsImWindow=true mIsWallpaper=false mIsFloatingLayer=true mWallpaperVisible=false
        |    mBaseLayer=151000 mSubLayer=0    mToken=WindowToken{5f0ae08 android.os.Binder@7f5a7ab}
        |    mViewVisibility=0x8 mHaveFrame=true mObscured=false
        |    mSeq=0 mSystemUiVisibility=0x500
        |    mGivenContentInsets=[0,610][0,0] mGivenVisibleInsets=[0,610][0,0]
        |    mTouchableInsets=2 mGivenInsetsPending=false
        |    touchable region=SkRegion((0,694,1920,1080))
        |    mFullConfiguration={1.0 100000010byd_theme452mcc1mnc [vi_VN] ldltr sw720dp w1280dp h604dp 240dpi lrg long land night finger -keyb/v/h -nav/h winConfig={ mBounds=Rect(0, 0 - 1920, 1080) mAppBounds=Rect(0, 0 - 1920, 990) mWindowingMode=fullscreen mDisplayWindowingMode=fullscreen mActivityType=undefined mAlwaysOnTop=undefined mRotation=ROTATION_0} s.38}
        |    mLastReportedConfiguration={1.0 100000010byd_theme452mcc1mnc [vi_VN] ldltr sw720dp w1280dp h604dp 240dpi lrg long land night finger -keyb/v/h -nav/h winConfig={ mBounds=Rect(0, 0 - 1920, 1080) mAppBounds=Rect(0, 0 - 1920, 990) mWindowingMode=fullscreen mDisplayWindowingMode=fullscreen mActivityType=undefined mAlwaysOnTop=undefined mRotation=ROTATION_0} s.38}
        |    mHasSurface=false isReadyForDisplay()=false mWindowRemovalAllowed=false
        |    Frames: containing=[0,84][1920,1080] parent=[0,84][1920,1080]
        |        display=[-10000,-10000][10000,10000] overscan=[-10000,-10000][10000,10000]
        |        content=[0,84][1920,1080] visible=[0,84][1920,1080]
        |        decor=[0,0][0,0]
        |        outset=[0,0][0,0]
        |    mFrame=[0,84][1920,1080] last=[0,84][1920,1080]
        |     cutout=DisplayCutout{insets=Rect(0, 0 - 0, 0) boundingRect={Bounds=[Rect(0, 0 - 0, 0), Rect(0, 0 - 0, 0), Rect(0, 0 - 0, 0), Rect(0, 0 - 0, 0)]}} last=DisplayCutout{insets=Rect(0, 0 - 0, 0) boundingRect={Bounds=[Rect(0, 0 - 0, 0), Rect(0, 0 - 0, 0), Rect(0, 0 - 0, 0), Rect(0, 0 - 0, 0)]}}
        |    Cur insets: overscan=[0,0][0,0] content=[0,0][0,0] visible=[0,0][0,0] stable=[0,0][0,90] outsets=[0,0][0,0]    Lst insets: overscan=[0,0][0,0] content=[0,0][0,0] visible=[0,0][0,0] stable=[0,0][0,90] outset=[0,0][0,0]
        |     surface=[0,0][0,0]
        |    WindowStateAnimator{d10115c InputMethod}:
        |      mDrawState=NO_SURFACE       mLastHidden=true
        |      mSystemDecorRect=[0,0][1920,996] mLastClipRect=[0,0][1920,996]
        |    mForceSeamlesslyRotate=false seamlesslyRotate: pending=null finishedFrameNumber=0
        |    isOnScreen=false
        |    isVisible=false
        |    mEmbeddedDisplayContents={}
        |  Window #1 Window{32a8235 u0 ScreenDecorOverlayBottom}:
        |    mDisplayId=0 stackId=0 mSession=Session{b704947 1443:u0a10056} mClient=android.os.BinderProxy@35ed11f
        |    mOwnerUid=10056 mShowToOwnerOnly=false package=com.android.systemui appop=NONE
        |  Window #2 Window{45be779 u0 ScreenDecorOverlay}:
        |    mDisplayId=0 stackId=0 mSession=Session{b704947 1443:u0a10056} mClient=android.os.BinderProxy@a878427
        |  Window #3 Window{3b4cee8 u0 NavigationBar0}:
        |    mDisplayId=0 stackId=0 mSession=Session{b704947 1443:u0a10056} mClient=android.os.BinderProxy@6c203da
        |  Window #4 Window{66522f3 u0 BydQSBar}:
        |    mDisplayId=0 stackId=0 mSession=Session{b704947 1443:u0a10056} mClient=android.os.BinderProxy@7c7a82d
        |  Window #5 Window{8ac1290 u0 StatusBar}:
        |    mDisplayId=0 stackId=0 mSession=Session{b704947 1443:u0a10056} mClient=android.os.BinderProxy@19b2542
        |  Window #6 Window{a4544b6 u0 vn.vietmap.live}:
        |    mDisplayId=0 stackId=0 mSession=Session{93ef6e6 11258:u0a10134} mClient=android.os.BinderProxy@6aec578
        |  Window #7 Window{1a73a9f u0 com.byd.vrassistant.xf.image}:
        |    mDisplayId=0 stackId=0 mSession=Session{a4bf789 1714:1000} mClient=android.os.BinderProxy@64b62f2
        |  Window #8 Window{43e07ea u0 AssistPreviewPanel}:
        |    mDisplayId=0 stackId=0 mSession=Session{b704947 1443:u0a10056} mClient=android.os.BinderProxy@e19808c
        |  Window #9 Window{d03e1f4 u0 BydLauncher_CardBarwindow}:
        |    mDisplayId=0 stackId=0 mSession=Session{3ca6afa 2650:1000} mClient=android.os.BinderProxy@9ef2506
        |  Window #10 Window{ae31e8a u0 DockedStackDivider}:
        |    mDisplayId=0 stackId=0 mSession=Session{b704947 1443:u0a10056} mClient=android.os.BinderProxy@1b4ce2c
        |  Window #11 Window{7c362a1 u0 com.android.launcher3/com.android.launcher3.home.MainActivity}:
        |    mDisplayId=0 stackId=0 mSession=Session{3ca6afa 2650:1000} mClient=android.os.BinderProxy@c705b08
        |  Window #12 Window{c27a07d u0 vn.vietmap.live/vn.vietmap.live.MainActivity}:
        |    mDisplayId=0 stackId=6 mSession=Session{93ef6e6 11258:u0a10134} mClient=android.os.BinderProxy@8c1b6d4
        |  Window #13 Window{3de0c0d u0 com.android.launcher3/com.android.launcher3.Launcher}:
        |    mDisplayId=0 stackId=4 mSession=Session{3ca6afa 2650:1000} mClient=android.os.BinderProxy@38756a4
        |  Window #14 Window{1a9b081 u0 com.byd.carsettings/com.byd.carsettings.MainActivity}:
        |    mDisplayId=0 stackId=1 mSession=Session{7efc00e 2431:1000} mClient=android.os.BinderProxy@b2f4b8b
        |  Window #15 Window{e66ca0a u0 com.byd.wallpaper.service.BydWallpaperService}:
        |    mDisplayId=0 stackId=0 mSession=Session{a4bebb9 1529:1000} mClient=android.os.BinderProxy@3b68a75
        |  mObscuringWindow=Window{e66ca0a u0 com.byd.wallpaper.service.BydWallpaperService}
        |  mSystemBooted=true mDisplayEnabled=true
    """.trimMargin()

    @Test
    fun `dump xe 14-09 - 16 cua so, tat ca tren display 0, ten dung nhu xe in`() {
        val w = ClusterThemePlan.parseWindows(dump2026_09_14)!!
        assertEquals(16, w.size, "mỗi `Window #n` một mục, dòng mObscuringWindow=Window{…} không phải tiêu đề: $w")
        assertTrue(w.all { it.displayId == 0 }, "phiên 14/09 không chiếu cụm ⇒ mọi cửa sổ ở display 0: $w")
        assertEquals("InputMethod", w.first().name)
        assertTrue(w.any { it.name == "vn.vietmap.live/vn.vietmap.live.MainActivity" })
        assertTrue(w.any { it.name == "vn.vietmap.live" }, "cửa sổ phủ của app = gói trần")
    }

    @Test
    fun `dump xe 14-09 - man ao cum 8 khong co lop nao, cong muc B se GUI`() {
        val w = ClusterThemePlan.parseWindows(dump2026_09_14)!!
        assertTrue(w.none { it.displayId == 8 })
        val home = com.byd.clusternav.modules.clustercast.StackParse.parse(
            "Stack id=0 bounds=[0,0][1920,1080] displayId=0 userId=0\n  taskId=4: com.android.launcher3/com.android.launcher3.Launcher visible=true",
        )
        assertEquals(
            ClusterThemePlan.Decision.Send,
            ClusterThemePlan.decide(30, setOf(8), 8, null, home, w, "com.byd.launcher", themeOnVacantVd = true),
        )
        // Cờ TẮT (mặc định B1a) ⇒ cùng bản đọc vẫn KHÔNG gửi.
        val off = ClusterThemePlan.decide(30, setOf(8), 8, null, home, w, "com.byd.launcher")
        assertEquals(ClusterThemePlan.Reason.VD_PRESENT, (off as ClusterThemePlan.Decision.Skip).reason)
    }
}
