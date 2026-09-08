@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
title Ma tran kich hoat TEN DUONG HUD (nhieu lever + log status - chay khi DO XE)

echo ==================================================================
echo   MA TRAN KICH HOAT TEN DUONG len HUD  (ClusterNav)
echo   v1: KHONG phai charset (CJK control cung khong hien), check-state=0.
echo   Ma tran nay THU NHIEU LEVER, moi cai doc cum-status + quan sat:
echo     status doc moi case: 420A1010(check) 38B0002E(navmap) 30100030(cfg) 40C0103B(dest)
echo   Road = chuoi CJK showroom (WuYiDaDaoNan) - neu 1 lever lam no HIEN
echo   hoac check-state doi 0-^>1/2 => tim ra don bay. Moi lever co ROLLBACK.
echo   *** DO XE. TAT Nav+HUD tren app ClusterNav.
echo ==================================================================
echo.

set "NAVJAR=%~dp0navopen.jar"
if not exist "%NAVJAR%" ( echo [LOI] thieu navopen.jar & pause & exit /b 1 )
set "ADB="
where adb >nul 2>nul && set "ADB=adb"
if not defined ADB if exist "%~dp0adb.exe" set "ADB=%~dp0adb.exe"
if not defined ADB if exist "%~dp0platform-tools\adb.exe" set "ADB=%~dp0platform-tools\adb.exe"
if not defined ADB for %%P in ("%USERPROFILE%\Desktop\platform-tools\adb.exe" "%USERPROFILE%\Downloads\platform-tools\adb.exe" "C:\platform-tools\adb.exe" "D:\clusternav\platform-tools\adb.exe") do if exist "%%~P" set "ADB=%%~P"
if not defined ADB ( echo KEO-THA adb.exe vao day roi Enter. & set /p "ADB=Duong dan adb.exe: " )
set "ADB=%ADB:"=%"

set /p "IP=IP xe (WiFi) - de TRONG neu USB: "
set "TGT=-d"
if not "%IP%"=="" ( "%ADB%" connect %IP%:5555 >nul & set "TGT=-s %IP%:5555" )

set "LOG=%~dp0hud-roadname-matrix.txt"
set "TMPF=%TEMP%\navm_%RANDOM%.txt"
set "NAVCMD=CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen"
REM CJK control = WuYiDaDaoNan (chuoi showroom), UTF-16LE hex:
set "ROAD=944e004e275953905753"

"%ADB%" %TGT% push "%NAVJAR%" /data/local/tmp/navopen.jar >nul 2>&1 && echo -- navopen pushed || ( echo [LOI] khong push duoc & pause & exit /b 1 )
echo ### MA TRAN TEN DUONG HUD  %date% %time%> "%LOG%"

echo. & echo Xac nhan xe DANG DO + TAT Nav+HUD tren app. Enter de bat dau.
pause >nul

echo === PRIME: navistate 2 + nav-screen 3 ===
call :run navistate 2
call :run setraw setting 4C10E015 3

REM ===== BASELINE (khong lever, chi road) - tai lap v1 lam doi chung =====
call :case "BASELINE (chi road, khong lever)" - - -
REM ===== quet tung LEVER (dev id val) =====
call :case "L1 SEND_DESTINATION_STATUS=2" instr 43E00038 2
call :case "L2 DYNAMIC_NAVI_FUNCTION=1"   instr 38B0002A 1
call :case "L3 MAP_TRANSFER_FLAG=1"       instr 40500025 1
call :case "L4 GUIDE_ROAD_AHEAD_DIST=250" instr 43F01030 250
call :case "L5 GUIDE_ADVANCED_ACTION=1"   instr 43F08030 1
call :case "L6 HUD_NAVIGATION_MAP_SET=2"  instr 32B1102E 2
call :case "L7 ARRIVAL_PASSPOINT_STATUS=1" instr 43FFF030 1
REM ===== COMBO: L1+L2+L3 cung luc =====
call :combo

echo. & echo === DON DEP ===
call :run navistate 4
echo.
echo ==================================================================
echo XONG. Gui lai file: %LOG%
echo   Doc: case nao lam 420A1010 doi 0-^>1/2, hoac ten duong HIEN tren kinh.
echo   (mui ten+cu ly la doi chung - luon hien)
echo ==================================================================
pause >nul
del "%TMPF%" >nul 2>&1
exit /b 0

REM :case <ten> <dev|-> <id|-> <val|->  - set lever (neu co) + road, doc status, quan sat, rollback
:case
echo. & echo ================= %~1 =================
echo ================= %~1 =================>> "%LOG%"
set "LDEV=%~2" & set "LID=%~3" & set "LVAL=%~4"
REM giu ~14s: frame + lever (neu co) + road
for /l %%i in (1,1,7) do (
  "%ADB%" %TGT% shell "%NAVCMD% frame 20 250 RD 8 5000" >nul 2>&1
  if not "%LDEV%"=="-" "%ADB%" %TGT% shell "%NAVCMD% setraw %LDEV% %LID% %LVAL%" >nul 2>&1
  "%ADB%" %TGT% shell "%NAVCMD% setbytes instr 43FA1008 %ROAD%" >nul 2>&1
)
call :status
echo.
set "OBS=" & set /p "OBS=   ten duong co HIEN khong? + ghi 420A1010 (vd 'khong-hien 420A=0' / 'co-hien'): "
echo    [quan sat %~1]: !OBS!>> "%LOG%"
REM cach ly: reset lever ve 0 truoc case sau (navistate 4 cuoi don sach nav)
if not "%LDEV%"=="-" ( "%ADB%" %TGT% shell "%NAVCMD% setraw %LDEV% %LID% 0" >nul 2>&1 & echo    [reset %LID% = 0]>> "%LOG%" )
goto :eof

:combo
echo. & echo ================= COMBO L1+L2+L3 =================
echo ================= COMBO L1+L2+L3 =================>> "%LOG%"
for /l %%i in (1,1,7) do (
  "%ADB%" %TGT% shell "%NAVCMD% frame 20 250 RD 8 5000" >nul 2>&1
  "%ADB%" %TGT% shell "%NAVCMD% setraw instr 43E00038 2" >nul 2>&1
  "%ADB%" %TGT% shell "%NAVCMD% setraw instr 38B0002A 1" >nul 2>&1
  "%ADB%" %TGT% shell "%NAVCMD% setraw instr 40500025 1" >nul 2>&1
  "%ADB%" %TGT% shell "%NAVCMD% setbytes instr 43FA1008 %ROAD%" >nul 2>&1
)
call :status
echo.
set "OBS=" & set /p "OBS=   COMBO: ten duong HIEN? + 420A1010: "
echo    [quan sat COMBO]: !OBS!>> "%LOG%"
goto :eof

REM doc cum status (log ca 4)
:status
call :run getraw instr 420A1010
call :run getraw instr 38B0002E
call :run getraw instr 30100030
call :run getraw instr 40C0103B
goto :eof

:run
"%ADB%" %TGT% shell "%NAVCMD% %*" > "%TMPF%" 2>&1
type "%TMPF%" | find "= "
type "%TMPF%" >> "%LOG%"
goto :eof
