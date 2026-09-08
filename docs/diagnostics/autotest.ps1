# ClusterNav — 1 lượt automation khi cắm máy vào xe (đỗ P).
# Cài APK mới nhất → chạy full self-test (mọi module) → 2 mode headless (nội suy + booster) → kéo report.
# CHỈ đọc/self-test + bơm 1 frame nav mẫu (đã chứng minh an toàn). KHÔNG root, KHÔNG ghi gì vĩnh viễn.
#
# Dùng:  pwsh docs/diagnostics/autotest.ps1            # tự dò thiết bị adb (cáp hoặc wifi đã connect)
#        pwsh docs/diagnostics/autotest.ps1 -Wifi      # tự connect YOUR-CAR-IP:5555 trước
param(
    [switch]$Wifi,
    [string]$Serial = "",
    [string]$WifiAddr = "YOUR-CAR-IP:5555"
)
$ErrorActionPreference = "Stop"
$pkg = "com.byd.clusternav"
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent   # .../ClusterNav
$apk = Join-Path $root "app/build/outputs/apk/debug/app-debug.apk"

if ($Wifi) { adb connect $WifiAddr | Out-Host; if (-not $Serial) { $Serial = $WifiAddr } }
$S = if ($Serial) { @("-s", $Serial) } else { @() }

function ADB { & adb @S @args }

Write-Host "== thiết bị ==" -ForegroundColor Cyan
ADB devices | Out-Host

if (Test-Path $apk) {
    Write-Host "== cài APK mới ($([math]::Round((Get-Item $apk).Length/1MB,1)) MB) ==" -ForegroundColor Cyan
    ADB install -r $apk | Out-Host
} else {
    Write-Host "!! chưa thấy APK — chạy: ./gradlew :app:assembleDebug" -ForegroundColor Yellow
}

function Run-Mode($mode, $extra) {
    Write-Host "`n== mode: $mode ==" -ForegroundColor Cyan
    ADB shell am force-stop $pkg
    ADB logcat -c
    $args = @("shell","am","start","-n","$pkg/.modules.AutotestActivity")
    if ($mode) { $args += @("--es","mode",$mode) }
    if ($extra) { $args += $extra }
    ADB @args | Out-Null
    Start-Sleep -Seconds $(if ($mode) { 6 } else { 22 })
    ADB logcat -d -s CLNAV_AUTO | Out-Host
}

# 1) FULL: self-test mọi module (gồm NavRealtime + Booster) + recon HAL + live-read + bơm 1 frame mẫu.
Run-Mode "" $null
$report = "/sdcard/Android/data/$pkg/files/autotest-report.txt"
$dest = Join-Path $root "log-autotest.txt"
ADB pull $report $dest 2>$null | Out-Host
if (Test-Path $dest) { Write-Host "report → $dest" -ForegroundColor Green }

# 2) NỘI SUY headless — chứng minh toán đếm ngược (xe đứng yên vẫn test).
Run-Mode "navrealtime" @("--es","kmh","54")

# 3) BOOSTER accessibility — soi đã bật quyền chưa / đọc được gì.
Run-Mode "accstatus" $null

# 4) NHỊP NOTI — chứng minh noti sống + đo độ thô (chạy sau khi đã mở GMaps dẫn đường 1 lúc).
Run-Mode "notiftrace" $null

# 5) REMOTEVIEWS — bóc field ẩn noti GMaps gần nhất (cần đã có noti GMaps).
Run-Mode "remoteviews" $null

# 6) AUDIO CUE — usage dẫn đường (=12) có lộ trên ROM BYD không (nghe 15s).
Run-Mode "audiocue" @("--es","secs","15")

Write-Host "`n== NHẮC TAY (1 lần/đời máy) ==" -ForegroundColor Yellow
Write-Host " • Quyền notification: mở app ClusterNav → bật 'Notification Access'." -ForegroundColor Yellow
Write-Host " • Booster bản đồ:    Cài đặt > Hỗ trợ (Accessibility) > ClusterNav → BẬT." -ForegroundColor Yellow
Write-Host " • Test thật: đỗ P, mở Google Maps dẫn đường → xem cụm; mở module trong app để soi số." -ForegroundColor Yellow

Write-Host "`n== TEST LÁI XE (chạy riêng, cần đang lái) ==" -ForegroundColor Cyan
Write-Host " FIRMWARE tự đếm? Giữ cố định 800m trong 90s, lái đều, xem cụm đứng yên hay tự tụt:" -ForegroundColor Cyan
Write-Host '   adb shell am start -n com.byd.clusternav/.modules.AutotestActivity --es mode holddist --es m 800 --es secs 90' -ForegroundColor Gray
Write-Host " → Đứng yên 800m = firmware KHÔNG smooth (GIỮ nội suy). Tự tụt = firmware smooth (BỎ nội suy)." -ForegroundColor Cyan

Write-Host "`n== RECON DEAD-RECKONING / GPS HẦM (Phase 0 — chạy riêng, theo thứ tự) ==" -ForegroundColor Magenta
Write-Host " B1. MOCK đè được GMaps không (GO/NO-GO cả kế hoạch). Cần: Developer Options > Chọn app vị trí mô phỏng = ClusterNav." -ForegroundColor Magenta
Write-Host '     adb shell am start -n com.byd.clusternav/.modules.AutotestActivity --es mode mockprobe' -ForegroundColor Gray
Write-Host "     → bơm 60s, MỞ GMaps: chấm nhảy về Hồ Gươm HN = ĐI; đứng nguyên = head unit dùng GPS OEM riêng." -ForegroundColor Magenta
Write-Host " B2. Nguồn HƯỚNG: gyro (rẽ trái/phải) + HAL góc lái (quay vô-lăng):" -ForegroundColor Magenta
Write-Host '     adb shell am start -n com.byd.clusternav/.modules.AutotestActivity --es mode sensorscan' -ForegroundColor Gray
Write-Host '     adb shell am start -n com.byd.clusternav/.modules.AutotestActivity --es mode steerscan' -ForegroundColor Gray
Write-Host " B3. GNSS mất-fix trong hầm (lái qua hầm/gầm cầu, 120s):" -ForegroundColor Magenta
Write-Host '     adb shell am start -n com.byd.clusternav/.modules.AutotestActivity --es mode gnsslog --es secs 120' -ForegroundColor Gray
Write-Host " (drsim = kiểm công thức tại nhà: --es mode drsim --es kmh 50)" -ForegroundColor DarkGray

# CarPlay đã DROP: nav CarPlay là video thuần, không tạo notification trên head unit -> chạy nền là mất sạch,
# không kênh nào sống. Capture/OCR chỉ chạy foreground = đúng case không cần app. Nguồn nav lên cụm = GMaps head unit.
