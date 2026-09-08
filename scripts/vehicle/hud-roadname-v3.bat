@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
title HUD TEN DUONG v3 - re-fire L1-L7 (log rc) + config-gate + display-content + read-map

echo ==================================================================
echo   HUD TEN DUONG v3  (ClusterNav)
echo   Matrix che rc (>nul) nen KHONG biet 7 lever da GHI (rc=0) hay bi
echo   TU CHOI (not-provisioned = chua thuc su test). v3 khac phuc:
echo     * READ-MAP full ho HUD-nav config/status
echo     * BAN LAI L1-L7 (7 lever matrix) + LOG rc tung cai
echo     * N1 GHI CONFIG GATE 0x38B00030 (matrix chi ghi 0x32B1102E, KHAC)
echo     * N2 DISPLAY_CONTENT 0x38B00042 (chon noi dung hien)
echo     * N3 CP_MAP_NAVIGATION_TIPS 0x40C0B026  * N4 OVERSEA 0x1F7A1008
echo     * N6 chuoi STRICT theo thu tu
echo   *** MOI WRITE LOG rc. Ten duong ban = 0x43FA1008 (CJK showroom).
echo   *** DO XE, TAT Nav+HUD tren app ClusterNav.
echo ==================================================================
echo.

set "NAVJAR=%~dp0navopen.jar"
if not exist "%NAVJAR%" ( echo [LOI] thieu navopen.jar & pause & exit /b 1 )
set "ADB="
where adb >nul 2>nul && set "ADB=adb"
if not defined ADB if exist "%~dp0adb.exe" set "ADB=%~dp0adb.exe"
if not defined ADB if exist "%~dp0platform-tools\adb.exe" set "ADB=%~dp0platform-tools\adb.exe"
if not defined ADB for %%P in ("%USERPROFILE%\Desktop\platform-tools\adb.exe" "%USERPROFILE%\Downloads\platform-tools\adb.exe" "C:\platform-tools\adb.exe") do if exist "%%~P" set "ADB=%%~P"
if not defined ADB ( echo KEO-THA adb.exe vao day roi Enter. & set /p "ADB=Duong dan adb.exe: " )
set "ADB=%ADB:"=%"

set /p "IP=IP xe (WiFi) - de TRONG neu USB: "
set "TGT=-d"
if not "%IP%"=="" ( "%ADB%" connect %IP%:5555 >nul & set "TGT=-s %IP%:5555" )

set "LOG=%~dp0hud-roadname-v3.txt"
set "TMPF=%TEMP%\navv3_%RANDOM%.txt"
set "NAVCMD=CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen"
REM CJK control = WuYiDaDaoNan (chuoi showroom), UTF-16LE hex:
set "ROAD=944e004e275953905753"

"%ADB%" %TGT% push "%NAVJAR%" /data/local/tmp/navopen.jar >nul 2>&1 && echo -- navopen pushed || ( echo [LOI] khong push duoc & pause & exit /b 1 )
echo ### HUD TEN DUONG v3  %date% %time%> "%LOG%"

echo. & echo Xac nhan: xe DO + TAT Nav+HUD app ClusterNav + KHONG can mo GMaps/VietMap (navopen tu bom nav). Enter de bat dau.
pause >nul

echo === PRIME: navistate 2 + nav-screen 3 ===
call :run navistate 2
call :run setraw setting 4C10E015 3

REM ============ BASELINE READ-MAP (provisioning ho HUD-nav) ============
echo. & echo ================= BASELINE READ-MAP =================
echo ================= BASELINE READ-MAP =================>> "%LOG%"
echo   (gia tri -2147482648 = NOT-PROVISIONED)>> "%LOG%"
call :rd 420A1010 "ROAD_NAME_CHECK_STATE (muc tieu: 0 sang 1/2)"
call :rd 38B00030 "HUD_NAV_MAP_CONFIG (gate - doc hien tai)"
call :rd 30100030 "HUD_NAV_MAP_CONFIG_STATUS"
call :rd 38B0002E "HUD_NAV_MAP_STATUS"
call :rd 30100031 "NAV_MAP_RESOLUTION_STATUS"
call :rd 38B00042 "DISPLAY_CONTENT_FUNCTION_CONFIG"
call :rd 30100042 "DISPLAY_CONTENT_FUNCTION_CONFIG_STATUS"
call :rd 30100015 "HUD_CONFIG_STATUS"
call :rd 3010000D "HUD_MODE_FEEDBACK_STATUS"
call :rd 40C0103B "GET_NAVI_DESTINATION"

REM ============ BAN LAI 7 LEVER MATRIX (L1-L7) + LOG rc ============
echo. & echo ***** RE-FIRE L1-L7 (log rc de biet ap vs bi tu choi) *****
echo ***** RE-FIRE L1-L7 (log rc) *****>> "%LOG%"
call :lever "L1 SEND_DESTINATION_STATUS=2" 43E00038 2
call :lever "L2 DYNAMIC_NAVI_FUNCTION=1"   38B0002A 1
call :lever "L3 MAP_TRANSFER_FLAG=1"       40500025 1
call :lever "L4 GUIDE_ROAD_AHEAD_DIST=250" 43F01030 250
call :lever "L5 GUIDE_ADVANCED_ACTION=1"   43F08030 1
call :lever "L6 HUD_NAVIGATION_MAP_SET=2"  32B1102E 2
call :lever "L7 ARRIVAL_PASSPOINT=1"       43FFF030 1
echo.
call :obs "L1-L7 (co case nao ten duong HIEN khong? xem cot rc trong log)"

REM ============ N1: GHI CONFIG GATE 0x38B00030 ============
echo. & echo ================= N1 WRITE CONFIG GATE 38B00030=1 =================
echo ================= N1 WRITE CONFIG GATE 38B00030=1 =================>> "%LOG%"
call :run setraw instr 38B00030 1
call :rd 38B00030 "readback CONFIG (co nhan 1 khong?)"
call :rd 30100030 "CONFIG_STATUS (con -2147482648?)"
call :rd 38B0002E "MAP_STATUS (con -2147482648?)"
call :seqroad
call :rd 420A1010 "check-state sau N1"
call :obs "N1 CONFIG GATE 38B00030=1"
call :run setraw instr 38B00030 0

REM ============ N2: DISPLAY_CONTENT_CONFIG 0x38B00042 = 1,2,3 ============
echo. & echo ================= N2 DISPLAY_CONTENT 38B00042 =================
echo ================= N2 DISPLAY_CONTENT 38B00042 =================>> "%LOG%"
for %%V in (1 2 3) do (
  call :run setraw instr 38B00042 %%V
  call :rd 30100042 "DC_STATUS sau =%%V"
  call :seqroad
  call :rd 420A1010 "check-state DC=%%V"
)
call :obs "N2 DISPLAY_CONTENT 38B00042 (1/2/3)"
call :run setraw instr 38B00042 0

REM ============ N3: CP_MAP_NAVIGATION_TIPS 0x40C0B026 (text) ============
echo. & echo ================= N3 CP_MAP_NAVIGATION_TIPS 40C0B026 =================
echo ================= N3 CP_MAP_NAVIGATION_TIPS 40C0B026 =================>> "%LOG%"
call :run navistate 2
call :run setbytes instr 40C0B026 %ROAD%
call :rd 420A1010 "check-state sau N3"
call :obs "N3 CP_MAP_NAVIGATION_TIPS (ten duong hien o dau khong?)"

REM ============ N4: OVERSEA pathname 0x1F7A1008 + guide 0x1F701010 ============
echo. & echo ================= N4 OVERSEA pathname 1F7A1008 =================
echo ================= N4 OVERSEA pathname 1F7A1008 =================>> "%LOG%"
call :run navistate 2
call :run setraw instr 1F701010 20
call :run setbytes instr 1F7A1008 %ROAD%
call :rd 420A1010 "check-state sau N4"
call :obs "N4 OVERSEA pathname+guide"

REM ============ N6: chuoi STRICT theo thu tu ============
echo. & echo ================= N6 STRICT SEQUENCE =================
echo ================= N6 STRICT SEQUENCE (config,navi,dest,guide,dist,pathname) =================>> "%LOG%"
call :run setraw instr 38B00030 1
call :run navistate 2
call :run setraw instr 43E00038 2
call :run setraw instr 43F01010 20
call :run setraw instr 43F01018 250
call :run setbytes instr 43FA1008 %ROAD%
call :rd 420A1010 "check-state sau N6"
call :obs "N6 STRICT SEQUENCE"
call :run setraw instr 38B00030 0
call :run setraw instr 43E00038 0

echo. & echo === DONE: navistate 4 (tat nav) ===
call :run navistate 4
echo. & echo === XONG. Gui lai file: %LOG% ===
echo Log: %LOG%
pause >nul
endlocal
exit /b 0

REM ===== helpers =====
REM :run <navopen args...> - chay + LOG dong ket qua (rc / gia tri), KHONG che
:run
"%ADB%" %TGT% shell "%NAVCMD% %*" > "%TMPF%" 2>&1
findstr /C:"-> rc=" /C:") = " "%TMPF%" >> "%LOG%"
findstr /C:"-> rc=" /C:") = " "%TMPF%"
goto :eof

REM :rd <hexid> <nhan> - doc 1 register instr + LOG kem nhan
:rd
echo   [%~2]>> "%LOG%"
echo   [%~2]
call :run getraw instr %~1
goto :eof

REM :seqroad - bom 1 nhip guidance + pathname (domestic) de co du lieu danh gia
:seqroad
call :run frame 20 250 RD 8 5000
call :run setbytes instr 43FA1008 %ROAD%
goto :eof

REM :lever <ten> <hexid> <val> - set lever (log rc) TRUOC roi bom guidance+pathname, doc check-state, reset
:lever
echo. & echo ================= %~1 =================
echo ================= %~1 =================>> "%LOG%"
call :run setraw instr %~2 %~3
call :seqroad
call :rd 420A1010 "check-state sau %~1"
call :run setraw instr %~2 0
goto :eof

REM :obs <ten> - hoi quan sat + ghi log
:obs
echo.
set "OBS=" & set /p "OBS=   ten duong co HIEN khong? + ghi 420A1010 (vd 'khong-hien 420A=0' / 'CO-HIEN'): "
echo    [quan sat %~1]: !OBS!>> "%LOG%"
goto :eof
