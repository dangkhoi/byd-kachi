@echo off
cd /d "%~dp0"
title Thu thap data HUD - Cluster Nav (chi DOC, khong sua xe)
echo ==================================================================
echo   THU THAP DATA HUD  (READ-ONLY - chi DOC, KHONG sua gi tren xe)
echo   Chay tu THU MUC BAT KY. Chi can navopen.jar nam CANH file .bat.
echo   Cong cu tu tim adb; neu khong thay se cho keo-tha adb.exe vao.
echo   Truoc khi chay: mo GMaps dan + HUD dang hien nav.
echo ==================================================================
echo.

REM --- navopen.jar phai nam cung thu muc voi .bat nay ---
set "NAVJAR=%~dp0navopen.jar"
if not exist "%NAVJAR%" (
  echo [LOI] Khong thay navopen.jar canh file .bat nay. Hay de 2 file cung 1 thu muc.
  echo.
  pause
  exit /b 1
)

REM --- Tim adb.exe (PATH -^> canh .bat -^> cac thu muc thuong gap -^> keo-tha) ---
set "ADB="
where adb >nul 2>nul && set "ADB=adb"
if not defined ADB if exist "%~dp0adb.exe" set "ADB=%~dp0adb.exe"
if not defined ADB if exist "%~dp0platform-tools\adb.exe" set "ADB=%~dp0platform-tools\adb.exe"
if not defined ADB for %%P in (
  "%USERPROFILE%\Desktop\platform-tools\adb.exe"
  "%USERPROFILE%\Downloads\platform-tools\adb.exe"
  "%USERPROFILE%\platform-tools\adb.exe"
  "C:\platform-tools\adb.exe"
  "D:\platform-tools\adb.exe"
  "D:\clusternav\platform-tools\adb.exe"
) do if exist "%%~P" set "ADB=%%~P"
if not defined ADB (
  echo Khong tu tim thay adb.exe.
  echo   KEO-THA file adb.exe ^(trong thu muc platform-tools^) vao cua so nay roi Enter.
  echo   ^(Chua co adb? Xem "Huong dan lay log Windows" - Phan B de tai ve.^)
  set /p "ADB=Duong dan adb.exe: "
)
REM bo dau ngoac kep neu keo-tha them vao
set "ADB=%ADB:"=%"
if /I not "%ADB%"=="adb" if not exist "%ADB%" (
  echo [LOI] Khong dung duoc adb tai: %ADB%
  echo       Cai adb theo huong dan roi chay lai.
  pause
  exit /b 1
)
echo -- Dung adb: %ADB%
echo.

echo Nhap IP xe (vd 192.168.1.50) neu ket noi qua WiFi.
echo Neu dung CAP USB thi cu de TRONG, bam Enter.
set /p "IP=IP xe: "

set "TGT=-d"
if not "%IP%"=="" set "TGT=-s %IP%:5555"
echo.
if not "%IP%"=="" (
  echo [1/6] Ket noi WiFi %IP%:5555 ...
  "%ADB%" connect %IP%:5555
) else (
  echo [1/6] Che do USB ^(dung cap^).
)
echo -- trang thai thiet bi --
"%ADB%" %TGT% get-state
if errorlevel 1 (
  echo.
  echo [LOI] CHUA KET NOI DUOC XE.
  echo   - Neu WiFi: laptop + xe phai CUNG WiFi, dung IP; hoac de TRONG IP de dung CAP USB.
  echo   - Neu USB: bat "Go loi USB" tren xe + bam "Cho phep" neu co popup.
  echo.
  pause
  exit /b 1
)

REM Ghi ket qua vao D:\clusternav (thu muc da tao san tren may nhan file)
set "OUTDIR=D:\clusternav"
if not exist "%OUTDIR%" mkdir "%OUTDIR%" 2>nul
set "OUT=%OUTDIR%\hud-compare.txt"
set "NAV=CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen getraw"

echo.
echo [2/6] Day cong cu doc (navopen.jar) len xe ...
"%ADB%" %TGT% push "%NAVJAR%" /data/local/tmp/navopen.jar

echo ### HUD PROVISIONING COMPARE  TGT=%TGT%  %DATE% %TIME% > "%OUT%"
echo ### rc: 0..N = gia tri that ^| -10011 = write-only ^| -2147482648 = NOT provisioned >> "%OUT%"

echo. >> "%OUT%"
echo ==== 0. DEVICE / VARIANT / REGION ==== >> "%OUT%"
"%ADB%" %TGT% shell getprop > "%TEMP%\gp.txt" 2>&1
findstr /I "ro.product ro.build.fingerprint ro.build.version.release region country market vehicle_40d dilink byd.bluetooth_name hud navi" "%TEMP%\gp.txt" >> "%OUT%"
echo -- app version (co the trong neu xe chua cai app) -- >> "%OUT%"
"%ADB%" %TGT% shell dumpsys package com.byd.clusternav2 2>&1 | findstr /I "versionName versionCode" >> "%OUT%"
"%ADB%" %TGT% shell dumpsys package com.byd.clusternav 2>&1 | findstr /I "versionName versionCode" >> "%OUT%"

echo.
echo [3/6] Doc cac co HUD (quan trong nhat)...
echo. >> "%OUT%"
echo ==== 1. HUD CODING FLAGS (CO QUYET DINH) ==== >> "%OUT%"
echo --- 38B00030 HUD_NAV_MAP_CONFIG (ky vong = 1 vi xe co HUD nav) --- >> "%OUT%"
"%ADB%" %TGT% shell "%NAV% instr 38B00030" >> "%OUT%" 2>&1
echo --- 38B0002E HUD_NAV_MAP_STATUS --- >> "%OUT%"
"%ADB%" %TGT% shell "%NAV% instr 38B0002E" >> "%OUT%" 2>&1
echo --- 38B00015 HUD_CONFIG 0=none/1=W/2=AR --- >> "%OUT%"
"%ADB%" %TGT% shell "%NAV% setting 38B00015" >> "%OUT%" 2>&1
echo --- 38B0001C HUD_SWITCH_STATUS --- >> "%OUT%"
"%ADB%" %TGT% shell "%NAV% setting 38B0001C" >> "%OUT%" 2>&1
echo --- 38B00028 HUD_NAV_CONTENT_STATUS --- >> "%OUT%"
"%ADB%" %TGT% shell "%NAV% setting 38B00028" >> "%OUT%" 2>&1
echo --- 38B0001E HUD_ADAS_STATUS --- >> "%OUT%"
"%ADB%" %TGT% shell "%NAV% setting 38B0001E" >> "%OUT%" 2>&1
echo --- 38B00020 (xe minh: provisioned=0) --- >> "%OUT%"
"%ADB%" %TGT% shell "%NAV% setting 38B00020" >> "%OUT%" 2>&1
echo --- 38B00022 (xe minh: provisioned=0) --- >> "%OUT%"
"%ADB%" %TGT% shell "%NAV% setting 38B00022" >> "%OUT%" 2>&1
echo --- 38B00018 (xe minh: -10013) --- >> "%OUT%"
"%ADB%" %TGT% shell "%NAV% setting 38B00018" >> "%OUT%" 2>&1

echo.
echo [4/6] Doc guide oversea/domestic...
echo. >> "%OUT%"
echo ==== 2. GUIDE oversea (1F7) / domestic (43F) ==== >> "%OUT%"
for %%I in (1F701010 1F701018 1F704010 1F7A1008 1F702010 1F705018) do (
  echo --- instr %%I oversea --- >> "%OUT%"
  "%ADB%" %TGT% shell "%NAV% instr %%I" >> "%OUT%" 2>&1
)
for %%I in (43F01010 43F01018 43F01030 43FA1008 43F02018) do (
  echo --- instr %%I domestic --- >> "%OUT%"
  "%ADB%" %TGT% shell "%NAV% instr %%I" >> "%OUT%" 2>&1
)
echo --- 40C03032 NAVI_TYPE / 4C10E015 NAVI_SCREEN --- >> "%OUT%"
"%ADB%" %TGT% shell "%NAV% instr 40C03032" >> "%OUT%" 2>&1
"%ADB%" %TGT% shell "%NAV% setting 4C10E015" >> "%OUT%" 2>&1

echo.
echo [5/6] Chup device codes (truoc khi xoa log) ...
echo. >> "%OUT%"
echo ==== 3. DEVICE CODES + permission (snapshot truoc khi clear; addDevice hay chi hien luc boot) ==== >> "%OUT%"
"%ADB%" %TGT% logcat -d > "%TEMP%\lc0.txt" 2>&1
findstr /I /C:"addDevice" /C:"no permission" /C:"with this device" "%TEMP%\lc0.txt" >> "%OUT%"

echo.
echo [6/6] Ghi logcat luc HUD dang hien nav (giu GMaps dan)...
echo. >> "%OUT%"
echo ==== 4. LOGCAT luc HUD hien nav ==== >> "%OUT%"
"%ADB%" %TGT% logcat -c
echo    ... GIU GMaps dan + HUD dang hien nav, doi 12 giay ...
timeout /t 12 /nobreak >nul
"%ADB%" %TGT% logcat -d > "%TEMP%\lc.txt" 2>&1
echo -- [A] Feature GHI THANH CONG (set featureId) -- >> "%OUT%"
findstr /I /C:"set featureId" "%TEMP%\lc.txt" >> "%OUT%"
echo -- [B] Feature BI TU CHOI -- >> "%OUT%"
findstr /I /C:"no permission" /C:"with this device" "%TEMP%\lc.txt" >> "%OUT%"
echo -- [C] SDK guidance / NavigationHudOwner / naviState -- >> "%OUT%"
findstr /I "sendSimpleGuidance sendNextPath NavigationHudOwner GuideInfo naviState" "%TEMP%\lc.txt" >> "%OUT%"

echo. >> "%OUT%"
echo DONE >> "%OUT%"
echo.
echo ==================================================================
echo   XONG!  Ket qua luu tai file:
echo   %OUT%
echo   Mo thu muc D:\clusternav va GUI LAI file hud-compare.txt.
echo   Neu man hinh co dong [LOI] mau: chup lai gui ky thuat.
echo ==================================================================
pause
