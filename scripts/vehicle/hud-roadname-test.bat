@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
title Test ban TEN DUONG len HUD - ClusterNav (chay khi DO XE)

echo ==================================================================
echo   TEST BAN TEN DUONG LEN HUD  (ClusterNav)
echo   Xe cua ban da hien MUI TEN + KHOANG CACH tren HUD.
echo   Script tu bom NAV GIA (khong can dan duong that) va thu 6 kieu
echo   ma hoa TEN DUONG, giu hien thi ~16s/kieu de ban NHIN KINH.
echo.
echo   *** LAM DUNG THU TU:
echo       1) DO XE (so P, phanh tay).
echo       2) TAT HAN Nav+HUD trong app ClusterNav (de app khong ghi de).
echo       3) KHONG can mo dan duong GMaps - script tu bom nav.
echo ==================================================================
echo.

set "NAVJAR=%~dp0navopen.jar"
if not exist "%NAVJAR%" ( echo [LOI] Thieu navopen.jar canh file .bat. & pause & exit /b 1 )

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

echo.
set /p "IP=IP xe (WiFi, vd 192.168.1.50) - de TRONG neu dung CAP USB: "
set "TGT=-d"
if not "%IP%"=="" ( "%ADB%" connect %IP%:5555 >nul & set "TGT=-s %IP%:5555" )

set "LOG=%~dp0hud-roadname-test.txt"
set "TMPF=%TEMP%\navout_%RANDOM%.txt"
set "NAVCMD=CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen"

echo === push navopen ===
"%ADB%" %TGT% push "%NAVJAR%" /data/local/tmp/navopen.jar >nul 2>&1 && echo   ok || ( echo   [LOI] khong push duoc - kiem tra ket noi xe & pause & exit /b 1 )
echo ### TEST TEN DUONG HUD  %date% %time%> "%LOG%"

echo. & echo -- Doc context truoc (khong ghi):
call :run getraw instr 420A1010
call :run getraw instr 38B00030
echo.
echo Xac nhan: xe DANG DO + da TAT Nav+HUD tren app ClusterNav. Bam Enter.
pause >nul

echo.
echo === PRIME: mo che do nav tren cum/HUD ===
call :run navistate 2
call :run setraw setting 4C10E015 3

echo.
echo === SANITY: giu 1 khung nav ~10s. NHIN KINH: co MUI TEN + KHOANG CACH khong? ===
call :hold SANITY 5400650073007400
call :run getraw instr 420A1010
set "OBS="
set /p "OBS=   HUD co hien mui ten + khoang cach? (co / khong): "
echo    [sanity mui-ten+cu-ly]: !OBS!>> "%LOG%"
echo    (Neu KHONG -> setup chua vao che do nav, bao lai truoc khi test tiep.)

REM ===== 6 bien the ma hoa ten duong (hex = UTF-16LE) =====
call :variant "A_ASCII (Le Loi)"              4c00650020004c006f006900
call :variant "B_VN-co-dau (Le Loi co dau)"   4c00ea0020004c00e31e6900
call :variant "C_CJK-showroom (giong showroom)" 944e004e275953905753
call :variant "D_dau-cach-dan"                20004c00650020004c006f006900
call :variant "E_NUL-cuoi"                    4c00650020004c006f0069000000
call :variant "F_ngan (Kim)"                  4b0069006d00

echo. & echo === DON DEP (ket thuc nav gia) ===
call :run navistate 4

echo.
echo ==================================================================
echo XONG. Gui lai file:  %LOG%
echo   - Bien the nao (A/B/C/D/E/F) lam TEN DUONG HIEN? Noi dung ra sao?
echo   - Moi bien the: check-state 420A1010 = 1 (VALID) hay 2 (INVALID)?
echo   - Mui ten + khoang cach la doi chung (nen luon hien).
echo ==================================================================
pause >nul
del "%TMPF%" >nul 2>&1
exit /b 0

REM ---------- ham ----------
:variant
echo. & echo ================= BIEN THE %~1 =================
echo.>> "%LOG%"
echo ================= %~1 =================>> "%LOG%"
echo   Dang giu ~16s. NHIN KINH: TEN DUONG co hien khong?
call :hold RD %~2
call :run getraw instr 420A1010
call :run getbytes instr 43FA1008
echo.
set "OBS="
set /p "OBS=   Ten duong hien? (hien-dung / khong-hien / loi-font / hien-sai): "
echo    [quan sat %~1]: !OBS!>> "%LOG%"
goto :eof

REM :hold <placeholderRoad> <roadHex> -- re-assert khung nav ~8 lan de HUD giu hien thi
:hold
for /l %%i in (1,1,8) do (
  "%ADB%" %TGT% shell "%NAVCMD% frame 20 250 %~1 8 5000" >nul 2>&1
  "%ADB%" %TGT% shell "%NAVCMD% setbytes instr 43FA1008 %~2" >nul 2>&1
)
goto :eof

:run
"%ADB%" %TGT% shell "%NAVCMD% %*" > "%TMPF%" 2>&1
type "%TMPF%"
type "%TMPF%">> "%LOG%"
goto :eof
