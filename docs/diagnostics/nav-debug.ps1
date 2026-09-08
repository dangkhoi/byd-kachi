# ClusterNav — BISECT "cluster nav đơn giản KHÔNG lên" (km + mũi tên + tên đường).
# Chẩn đoán CHẶNG NÀO gãy trong 3 chặng, KHÔNG cài lại app (cài lại = rớt quyền notification = chính nghi phạm #1).
#
#   Chặng 1  đọc noti GMaps   (NavNotificationListener)  -> phải BOUND + Prefs.enabled
#   Chặng 2  bắn broadcast     (ClusterBroadcaster.emit)  -> audit xác nhận CÒN NGUYÊN
#   Chặng 3  firmware vẽ cụm    (AmapService)              -> cần cờ mIsBYDMapNaving không kẹt + profile đúng
#
# CÁCH DÙNG:
#   1) Mở terminal #1:  pwsh docs/diagnostics/nav-log.ps1 -Wifi     (để yên nhìn log)
#   2) Mở terminal #2:  pwsh docs/diagnostics/nav-debug.ps1 -Wifi   (chạy bisect có hướng dẫn)
#   Chạy lẻ 1 bước:     pwsh docs/diagnostics/nav-debug.ps1 -Step check|fix|render|clean
#
# Quy ước icon frame giả:  2 = mũi tên TRÁI, 3 = mũi tên PHẢI.
param(
    [switch]$Wifi,
    [string]$Serial = "",
    [string]$WifiAddr = "YOUR-CAR-IP:5555",
    [ValidateSet("all","check","fix","render","clean")]
    [string]$Step = "all"
)
$ErrorActionPreference = "Stop"
$pkg  = "com.byd.clusternav"
$act  = "$pkg/.modules.AutotestActivity"
$comp = "$pkg/$pkg.NavNotificationListener"     # verify: NavConnect.COMP

if ($Wifi) { adb connect $WifiAddr | Out-Host; if (-not $Serial) { $Serial = $WifiAddr } }
$S = if ($Serial) { @("-s", $Serial) } else { @() }
function ADB { & adb @S @args }

function Ask($q) {
    while ($true) {
        $a = (Read-Host "$q  [y/n]").Trim().ToLower()
        if ($a -in @("y","yes","c","co","có")) { return $true }
        if ($a -in @("n","no","k","khong","không")) { return $false }
    }
}
function Look($msg) { Write-Host "`n>>> NHÌN CỤM: $msg" -ForegroundColor Cyan; Read-Host "    (xong bấm Enter)" | Out-Null }

# ── Bước CHECK: listener status + bơm frame giả (broadcast) — cắt đôi Chặng1 vs Chặng3 ──
function Invoke-Check {
    Write-Host "`n== CHECK: self-test + bơm frame giả lên cụm ==" -ForegroundColor Green
    ADB logcat -c | Out-Null
    ADB shell am start -n $act | Out-Null           # KHÔNG force-stop (giữ binding)
    Write-Host "   ...đang chạy self-test (chờ ~26s, đừng đụng)..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 26
    $log = ADB logcat -d -s CLNAV_AUTO
    Write-Host "`n--- dòng chốt trong report ---" -ForegroundColor Yellow
    ($log | Select-String -Pattern 'notif-listener bật|nav enabled=|\(1\) BROADCAST|\(2\) IN-PROC|===== DONE') | ForEach-Object { Write-Host "   $_" }
    # kéo report đầy đủ về repo
    $dest = Join-Path $PSScriptRoot "log-nav-debug.txt"
    ADB pull "/sdcard/Android/data/$pkg/files/autotest-report.txt" $dest 2>$null | Out-Null
    if (Test-Path $dest) { Write-Host "   report đầy đủ -> $dest" -ForegroundColor Green }
    # trả về trạng thái listener (true/false/unknown)
    $line = ($log | Select-String -Pattern 'notif-listener bật:\s*(\w+)')
    if ($line -and $line.Matches.Count) { return $line.Matches[0].Groups[1].Value.ToLower() }
    $l2 = ($log | Select-String -Pattern 'notif-listener bật:\s*(true|false)')
    if ($l2) { return ($l2 -match 'true') ? 'true' : 'false' }
    return "unknown"
}

# ── Bước FIX: cấp + ép rebind quyền notification TỪ PC (uid shell, KHÔNG popup, KHÔNG cần tcpip5555) ──
function Invoke-Fix {
    Write-Host "`n== FIX: cấp + ép rebind listener từ PC (mirror NavConnect.doReconnect) ==" -ForegroundColor Green
    ADB shell cmd notification disallow_listener $comp | Out-Host
    Start-Sleep -Milliseconds 1200
    ADB shell cmd notification allow_listener $comp | Out-Host
    Write-Host "   -> đã disallow->allow. Listener phải bound lại ngay (không cần mở app / bấm popup)." -ForegroundColor Gray
}

# ── Bước RENDER: bơm frame giả qua etabroadcast — TỰ reset cờ kẹt mIsBYDMapNaving trước ──
function Invoke-Render {
    Write-Host "`n== RENDER: etabroadcast byd=false (reset cờ kẹt rồi bắn '500m Nguyễn Huệ' 22 lần) ==" -ForegroundColor Green
    Write-Host "   byd=false = ĐÚNG recipe đã chứng minh 2026-06-23. TUYỆT ĐỐI không dùng byd=true." -ForegroundColor DarkGray
    ADB shell am start -n $act --es mode etabroadcast --es byd false | Out-Null
}

# ── Bước CLEAN: tắt sạch nav cụm về trạng thái biết-rõ (không sửa được profile — cần reboot) ──
function Invoke-Clean {
    Write-Host "`n== CLEAN: clearnav (status=4, screen=0, mapSend=0) ==" -ForegroundColor Green
    ADB shell am start -n $act --es mode clearnav | Out-Null
    Start-Sleep -Seconds 3
    Write-Host "   Nếu render VẪN chết sau clean -> profile cụm kẹt (vd 31 do lần chiếu-app trước)." -ForegroundColor Yellow
    Write-Host "   Broadcast KHÔNG đổi được profile. Cách thoát DUY NHẤT: adb reboot (KHÔNG cài lại app!)." -ForegroundColor Yellow
    Write-Host '   >>> adb reboot   (đợi xe khởi động lại rồi chạy -Step fix, KHÔNG uninstall/reinstall)' -ForegroundColor Gray
}

Write-Host "== thiết bị ==" -ForegroundColor Cyan
ADB devices | Out-Host

switch ($Step) {
    "check"  { Invoke-Check | Out-Null; break }
    "fix"    { Invoke-Fix;             break }
    "render" { Invoke-Render; Look "frame '500m / Nguyễn Huệ' có hiện không?"; break }
    "clean"  { Invoke-Clean;           break }
    "all" {
        Write-Host "`n### BISECT CÓ HƯỚNG DẪN — đảm bảo terminal #1 đang chạy nav-log.ps1 ###" -ForegroundColor Magenta
        Write-Host "Gợi ý: nếu vừa test chiếu-app xong, cân nhắc 'adb reboot' cho cụm sạch trước khi bisect." -ForegroundColor DarkGray

        $listener = Invoke-Check
        $frame1 = Ask ">>> Cụm CÓ hiện 'phải + 250m + Nguyễn Huệ' (frame broadcast (1)) không?"

        if ($listener -eq "false") {
            Write-Host "`n[GT#1] notif-listener = FALSE -> quyền notification bị rớt (thường do cài lại APK)." -ForegroundColor Red
            Invoke-Fix
            Write-Host "-> Chạy lại CHECK để xác nhận..." -ForegroundColor Cyan
            $listener = Invoke-Check
            if ($listener -eq "true") { Write-Host "OK: listener đã true. Giờ mở GMaps dẫn đường, xem terminal #1 có dòng 'NavListener: nav dist=..'." -ForegroundColor Green }
            else { Write-Host "VẪN không true -> xem log NavConnect (dadb/popup) hoặc thử -Step fix lại." -ForegroundColor Yellow }
        }
        elseif ($frame1) {
            Write-Host "`n[Chặng 2+3 OK] Broadcast vẽ được -> lỗi ở CHẶNG 1 (đọc noti)." -ForegroundColor Green
            Write-Host "Mở GMaps dẫn đường thật, nhìn terminal #1:" -ForegroundColor Cyan
            $feed = Ask ">>> Có thấy dòng 'NavListener: nav dist=.. road=..' khi GMaps đang dẫn không?"
            if (-not $feed) {
                Write-Host "[GT#2] listener granted nhưng KHÔNG feed (BYD bỏ qua requestRebind). Ép rebind từ PC:" -ForegroundColor Red
                Invoke-Fix
                Write-Host "-> Dẫn GMaps lại, xem terminal #1 giờ có 'nav dist=..' + cụm lên nav chưa." -ForegroundColor Cyan
            } else {
                Write-Host "Listener CÓ feed + broadcast vẽ được -> nav thật phải lên. Nếu vẫn không, chụp log gửi lại." -ForegroundColor Green
            }
        }
        else {
            Write-Host "`n[Chặng 3 nghi] Broadcast (1) KHÔNG vẽ dù listener=$listener -> thử reset cờ kẹt:" -ForegroundColor Yellow
            Invoke-Render
            $frame2 = Ask ">>> Frame '500m / Nguyễn Huệ' (etabroadcast) có hiện không?"
            if ($frame2) {
                Write-Host "[GT#3] Cờ mIsBYDMapNaving bị kẹt -> reset xong là vẽ được. Nav thật giờ phải lên." -ForegroundColor Green
            } else {
                Write-Host "[GT#4] Vẫn trống sau reset -> profile cụm kẹt (vd 31 do lần chiếu-app trước)." -ForegroundColor Red
                Invoke-Clean
            }
        }
        Write-Host "`n### XONG BISECT — báo lại: listener=$listener, frame(1)=$frame1 + kết quả các nhánh ###" -ForegroundColor Magenta
        break
    }
}
