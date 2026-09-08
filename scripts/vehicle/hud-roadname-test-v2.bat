@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
title Test TEN DUONG len HUD v2 - them DESTINATION_STATUS (chay khi DO XE)

echo ==================================================================
echo   TEST TEN DUONG HUD v2  (ClusterNav)
echo   v1 da chung minh: KHONG phai loi charset (chuoi CJK showroom cung
echo   khong hien) + check-state 0x420A1010 = 0 (chua vao pipeline).
echo   v2 THU DON BAY MOI: set SEND_DESTINATION_STATUS 0x43E00038 = 2
echo   (SET_DONE) TRUOC ten duong -> xem MCU co mo o ten-duong khong.
echo.
echo   *** DO XE. TAT Nav+HUD tren app ClusterNav (de app khong ghi de).
echo ==================================================================
echo.

set "NAVJAR=%~dp0navopen.jar"
if not exist "%NAVJAR%" ( echo [LOI] thieu navopen.jar canh .bat & pause & exit /b 1 )
set "ADB="
where adb >nul 2>nul && set "ADB=adb"
if not defined ADB if exist "%~dp0adb.exe" set "ADB=%~dp0adb.exe"
if not defined ADB if exist "%~dp0platform-tools\adb.exe" set "ADB=%~dp0platform-tools\adb.exe"
if not defined ADB for %%P in (
  "%USERPROFILE%\Desktop\platform-tools\adb.exe" "%USERPROFILE%\Downloads\platform-tools\adb.exe"
  "C:\platform-tools\adb.exe" "D:\platform-tools\adb.exe" "D:\clusternav\platform-tools\adb.exe"
) do if exist "%%~P" set "ADB=%%~P"
if not defined ADB ( echo KEO-THA adb.exe vao day roi Enter. & set /p "ADB=Duong dan adb.exe: " )
set "ADB=%ADB:"=%"

set /p "IP=IP xe (WiFi) - de TRONG neu USB: "
set "TGT=-d"
if not "%IP%"=="" ( "%ADB%" connect %IP%:5555 >nul & set "TGT=-s %IP%:5555" )

set "LOG=%~dp0hud-roadname-test-v2.txt"
set "TMPF=%TEMP%\navout2_%RANDOM%.txt"
set "NAVCMD=CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen"

echo === push navopen ===
"%ADB%" %TGT% push "%NAVJAR%" /data/local/tmp/navopen.jar >nul 2>&1 && echo   ok || ( echo   [LOI] khong push duoc & pause & exit /b 1 )
echo ### TEST TEN DUONG HUD v2 (DESTINATION_STATUS)  %date% %time%> "%LOG%"

echo.
echo Xac nhan: xe DANG DO + da TAT Nav+HUD tren app. Enter de bat dau.
pause >nul

echo === PRIME: nav-screen + DESTINATION_STATUS=2 ===
call :run navistate 2
call :run setraw setting 4C10E015 3
call :run setraw instr 43E00038 2
echo -- context: doc GET_NAVI_DESTINATION + check-state truoc khi ghi ten:
call :run getraw instr 40C0103B
call :run getraw instr 420A1010

REM 2 bien the: ASCII + CJK control (chuoi showroom). Neu check-state doi 0->1/2 = da kich hoat.
call :variant "A_ASCII (Le Loi)"                4c00650020004c006f006900
call :variant "C_CJK-showroom (WuYiDaDaoNan)"   944e004e275953905753

echo. & echo === DON DEP ===
call :run navistate 4

echo.
echo ==================================================================
echo XONG. Gui lai file: %LOG%
echo   CAU HOI CHINH: sau khi set DESTINATION_STATUS=2, check-state 0x420A1010
echo   con = 0 khong, hay doi thanh 1/2? Va ten duong co HIEN tren HUD khong?
echo     - doi 0-^>1/2  = da kich hoat pipeline (dot pha, se sua app tuong tu)
echo     - van = 0      = gate provisioning (khong phai chuoi kich hoat) -^> nhanh coding
echo ==================================================================
pause >nul
del "%TMPF%" >nul 2>&1
exit /b 0

:variant
echo. & echo ================= BIEN THE %~1 =================
echo.>> "%LOG%"
echo ================= %~1 =================>> "%LOG%"
echo   Giu ~16s (re-assert DESTINATION_STATUS + ten duong). NHIN KINH.
call :hold %~2
call :run getraw instr 420A1010
call :run getbytes instr 43FA1008
echo.
set "OBS="
set /p "OBS=   check-state ket qua? ten duong co hien? (vd '420A=2 khong-hien' / 'hien'): "
echo    [quan sat %~1]: !OBS!>> "%LOG%"
goto :eof

REM :hold <roadHex> -- re-assert khung nav + DESTINATION_STATUS + ten duong ~8 lan de MCU giu
:hold
for /l %%i in (1,1,8) do (
  "%ADB%" %TGT% shell "%NAVCMD% frame 20 250 RD 8 5000" >nul 2>&1
  "%ADB%" %TGT% shell "%NAVCMD% setraw instr 43E00038 2" >nul 2>&1
  "%ADB%" %TGT% shell "%NAVCMD% setbytes instr 43FA1008 %~1" >nul 2>&1
)
goto :eof

:run
"%ADB%" %TGT% shell "%NAVCMD% %*" > "%TMPF%" 2>&1
type "%TMPF%"
type "%TMPF%">> "%LOG%"
goto :eof
