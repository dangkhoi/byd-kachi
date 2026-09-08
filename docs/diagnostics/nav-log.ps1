# ClusterNav — CỬA SỔ LOG (chạy ở terminal RIÊNG, để yên nhìn).
# Tail logcat đúng 5 tag của đường nav (đã verify bằng audit code):
#   CLNAV_AUTO       = harness self-test (AutotestActivity)
#   NavListener      = Chặng 1: đọc noti GMaps  → có dòng này = listener SỐNG + đang feed
#   ClusterBroadcaster = Chặng 2: bắn broadcast  → 'emit icon=.. seg=..' = đã gửi frame
#   NavConnect/NavRebind = vòng đời bind listener (báo lỗi dadb/popup nếu có)
#
# Dùng:  pwsh docs/diagnostics/nav-log.ps1            # cáp USB / wifi đã connect sẵn
#        pwsh docs/diagnostics/nav-log.ps1 -Wifi      # tự adb connect trước
# KHÔNG cài lại app, KHÔNG force-stop — chỉ đọc log.
param(
    [switch]$Wifi,
    [string]$Serial = "",
    [string]$WifiAddr = "YOUR-CAR-IP:5555"
)
if ($Wifi) { adb connect $WifiAddr | Out-Host; if (-not $Serial) { $Serial = $WifiAddr } }
$S = if ($Serial) { @("-s", $Serial) } else { @() }

& adb @S logcat -c
Write-Host "== ĐANG TAIL LOG (Ctrl+C để dừng) ==" -ForegroundColor Cyan
Write-Host "Mở Google Maps dẫn đường trên xe, rồi đọc theo mốc:" -ForegroundColor Cyan
Write-Host "  • 'NavListener: listener connected'  -> Chặng 1 bind OK" -ForegroundColor Gray
Write-Host "  • 'NavListener: nav dist=.. road=..'  -> đọc được noti GMaps" -ForegroundColor Gray
Write-Host "  • 'ClusterBroadcaster: emit icon=.. seg=..' -> đã BẮN frame lên cụm" -ForegroundColor Gray
Write-Host "  KHÔNG có dòng NavListener nào khi GMaps đang dẫn = listener KHÔNG bound (Chặng 1 chết)." -ForegroundColor Yellow
Write-Host ("-" * 70) -ForegroundColor DarkGray
& adb @S logcat -s CLNAV_AUTO:V NavListener:V ClusterBroadcaster:V NavConnect:V NavRebind:V
