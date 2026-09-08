@echo off
REM ==========================================================================
REM  LAY LOG LAI THU ClusterNav 2.0 (Windows)  --  cho nguoi KHONG ranh IT
REM  Chay tu THU MUC BAT KY: double-click (xe noi bang cap USB)
REM        hoac:  pull-drive-logs.bat 192.168.1.50   (xe cung WiFi, thay IP)
REM  Log keo ve D:\clusternav\logs-<ngaygio>, roi tu mo ra.
REM  Cong cu tu tim adb; khong thay se cho keo-tha adb.exe vao.
REM ==========================================================================
setlocal
set "PKG=com.byd.clusternav2"
set "SRC=/sdcard/Android/data/%PKG%/files"

echo == ClusterNav 2.0 -- lay log lai thu ==

REM --- Tim adb.exe (chay tu thu muc bat ky) ---
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
  echo Khong tu tim thay adb.exe. KEO-THA adb.exe vao cua so nay roi Enter.
  echo   ^(Chua co adb? Xem "Huong dan lay log Windows" - Phan B de tai ve.^)
  set /p "ADB=Duong dan adb.exe: "
)
set "ADB=%ADB:"=%"
if /I not "%ADB%"=="adb" if not exist "%ADB%" (
  echo [X] Khong dung duoc adb tai: %ADB%  -- cai adb theo huong dan roi chay lai.
  pause & exit /b 1
)

if not "%~1"=="" (
  echo -^> Noi toi xe qua WiFi: %~1:5555
  "%ADB%" connect %~1:5555
)

"%ADB%" get-state >nul 2>nul
if errorlevel 1 (
  echo [X] Khong thay xe. Kiem tra: cap USB da cam ^(bam 'Allow' tren man xe^), HOAC nhap dung IP WiFi.
  pause & exit /b 1
)

set "DT="
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value 2^>nul ^| find "="') do set "DT=%%I"
if defined DT (set "STAMP=%DT:~0,8%-%DT:~8,6%") else (set "STAMP=%RANDOM%")
set "DEST=D:\clusternav\logs-%STAMP%"
mkdir "%DEST%" 2>nul
if not exist "%DEST%" (
  echo [X] Khong tao duoc %DEST% -- kiem tra o D: co ton tai khong.
  pause & exit /b 1
)
echo -^> Dang lay log ve: %DEST%
"%ADB%" pull %SRC% "%DEST%"
if errorlevel 1 (
  echo [X] Khong lay duoc log. Thuong do: app CHUA bat 'Nhat ky chi tiet', hoac chua lai nen chua co log.
  pause & exit /b 1
)
echo [OK] XONG. Log nam o: %DEST%
echo     Gom: nav_notif_*.csv, nav_log_*.csv, vietmap_*.csv + diag\ (anh man chinh/cum).
explorer "%DEST%"
pause
endlocal
