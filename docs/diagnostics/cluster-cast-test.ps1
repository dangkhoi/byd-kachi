# ClusterNav — TEST phương pháp DashCast (launch freeform + wm density) trên xe, chạy TAY từng bước.
# Trả lời gate: `am start --display N --windowingMode 5` từ uid-2000 có QUA không (hết SecurityException)?
#   QUA  -> shell freeform là fix đúng (v0.22 placeAppOnVd đã code).
#   CHẶN -> cần ký platform / daemon (xem dashcast-projection-recipe.md).
#
# Dùng:  pwsh docs/diagnostics/cluster-cast-test.ps1 -Wifi -Pkg anddea.youtube
#        pwsh docs/diagnostics/cluster-cast-test.ps1 -Pkg com.google.android.apps.maps -Dpi 160
param(
    [switch]$Wifi,
    [string]$Serial   = "",
    [string]$WifiAddr = "YOUR-CAR-IP:5555",
    [string]$Pkg      = "anddea.youtube",   # app test (YouTube ReVanced trên xe); đổi sang maps để thử GMaps
    [int]$Dpi         = 160
)
if ($Wifi) { adb connect $WifiAddr | Out-Host; if (-not $Serial) { $Serial = $WifiAddr } }
$S = if ($Serial) { @("-s", $Serial) } else { @() }
function ADB { & adb @S @args }
function SH($c) { ADB shell $c }
function Look($m) { Write-Host "`n>>> NHÌN CỤM: $m" -ForegroundColor Cyan; Read-Host "    (Enter để tiếp)" | Out-Null }

Write-Host "== thiết bị ==" -ForegroundColor Cyan; ADB devices | Out-Host

function Get-CID {
    $g = SH "dumpsys display | grep -iE 'Display [0-9]+:|fission|xdja'"
    $cid = -1; $cur = -1
    foreach ($ln in ($g -split "`n")) {
        if ($ln -match 'Display (\d+):') { $cur = [int]$Matches[1] }
        if ($ln -match '(?i)fission|xdja') { if ($cur -ge 1) { $cid = $cur } }
    }
    return $cid
}

Write-Host "`n① dò VD cụm hiện có..." -ForegroundColor Green
$CID = Get-CID
if ($CID -lt 1) {
    Write-Host "  chưa có VD → activate 30→16→35 (Seal/DL3)" -ForegroundColor Yellow
    SH 'service call AutoContainer 2 i32 1000 i32 30 s16 ""' | Out-Null; Start-Sleep 3
    SH 'service call AutoContainer 2 i32 1000 i32 16 s16 ""' | Out-Null; Start-Sleep 3
    SH 'service call AutoContainer 2 i32 1000 i32 35 s16 ""' | Out-Null; Start-Sleep 2
    $CID = Get-CID
}
if ($CID -lt 1) { Write-Host "❌ không dò được VD cụm — dừng." -ForegroundColor Red; return }
Write-Host "  VD cụm = display $CID" -ForegroundColor Green

Write-Host "`n② resolve launcher activity của $Pkg" -ForegroundColor Green
$comp = (SH "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $Pkg" |
         Where-Object { $_ -match '/' -and $_ -notmatch ' ' } | Select-Object -Last 1)
if (-not $comp) { Write-Host "❌ không resolve được component cho $Pkg" -ForegroundColor Red; return }
Write-Host "  comp = $comp" -ForegroundColor Green

Write-Host "`n③ wm density $Dpi -d $CID (fix scale — KHÔNG bao giờ -d 0)" -ForegroundColor Green
SH "wm density $Dpi -d $CID" | Out-Host
SH "settings put global force_resizable_activities 0" | Out-Null
Start-Sleep -Milliseconds 200

Write-Host "`n④ LAUNCH FREEFORM (gate SecurityException) — force-stop + am start --display $CID --windowingMode 5" -ForegroundColor Green
SH "am force-stop $Pkg" | Out-Null
$out = SH "am start --display $CID --windowingMode 5 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $comp --activity-clear-task"
$out | Out-Host
if ("$out" -match 'SecurityException|Permission Denial') {
    Write-Host "  ⚠ GATE = CHẶN: shell uid-2000 vẫn bị SecurityException với --windowingMode 5." -ForegroundColor Red
    Write-Host "     -> shell freeform KHÔNG dùng được; cần ký platform / daemon (xem recipe). Ghi lại kết quả này." -ForegroundColor Red
} else {
    Write-Host "  ✓ GATE = QUA (không SecurityException). Kiểm cụm xem app lên full/đúng không." -ForegroundColor Green
}
Start-Sleep 1
Write-Host "-- app đang ở display nào --"
SH "am stack list" | Select-String -Pattern "$([regex]::Escape($Pkg))|displayId=" | Out-Host

Look "app '$Pkg' có LÊN CỤM full + đúng (không trắng, không nhỏ) không? Ghi lại."

Write-Host "`n⑤ (tuỳ) overscan khung 0,90,0,90 -d $CID" -ForegroundColor Green
SH "wm overscan 0,90,0,90 -d $CID" | Out-Null
Look "sau overscan, khung có gọn hơn không (chỉ mỹ thuật)?"

Write-Host "`n⑥ TEARDOWN sạch (app off VD -> reset density/overscan -> 18 -> 0)" -ForegroundColor Green
SH "am start --display 0 $comp" | Out-Null
SH "wm density reset -d $CID" | Out-Null
SH "wm overscan reset -d $CID" | Out-Null
SH 'service call AutoContainer 2 i32 1000 i32 18 s16 ""' | Out-Null; Start-Sleep 1
SH 'service call AutoContainer 2 i32 1000 i32 0 s16 ""' | Out-Null
Look "cụm về đồng hồ gốc chưa (teardown có kẹt không)?"
Write-Host "`n== XONG. Báo lại: GATE qua/chặn · app lên full/trắng/nhỏ · teardown sạch/kẹt ==" -ForegroundColor Magenta
