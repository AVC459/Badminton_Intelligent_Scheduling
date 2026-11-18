@echo off
chcp 65001 >nul
setlocal
title 接口环境 / 局域网 IP - 羽毛球智能编排系统

rem ============================================================
rem  真机预览时，手机要通过「电脑的局域网 IP」访问后端。
rem  换 WiFi / 路由器重启后 IP 会变，变了手机就连不上。
rem  本脚本可一键把当前 WLAN 的 IP 写入前端配置。
rem ============================================================

set "ROOT=%~dp0..\"
set "REQ=%ROOT%badminton\utils\request.js"

if not exist "%REQ%" (
  echo.
  echo   [错误] 找不到前端请求文件：
  echo          %REQ%
  echo.
  echo   请把本脚本放在项目根目录（与 badminton 文件夹同级）。
  echo.
  pause
  exit /b 1
)

:menu
cls
echo.
echo   ============================================================
echo    接口环境 / 局域网 IP 管理
echo    API Env / LAN IP Manager
echo   ============================================================
echo.
echo    当前环境模式 / current mode：
findstr /n /c:"const CURRENT_ENV" "%REQ%"
echo.
echo    当前接口地址 / current endpoints：
findstr /n /c:"dev: " /c:"lan: " /c:"prod: " "%REQ%"
echo.
echo   ------------------------------------------------------------
echo     [1] 刷新局域网 IP   Refresh LAN IP     ★ 换WiFi后先做这个
echo     [2] 查看所有网卡 IP Show all local IPs
echo     [3] 手动编辑        Edit request.js
echo     [0] 退出            Exit
echo   ------------------------------------------------------------
echo.
echo    说明：环境模式默认 auto，会自动区分
echo          模拟器 (dev 127.0.0.1) 与 真机 (lan 局域网IP)，
echo          所以平时只需在换 WiFi 之后执行一次 [1]。
echo    Note: mode "auto" picks dev in simulator and lan on a real
echo          phone, so you normally only need [1] after a WiFi change.
echo.
choice /c 1230 /n /m "   Select / 请选择: "
set "SEL=%errorlevel%"
if "%SEL%"=="1" goto refresh
if "%SEL%"=="2" goto showip
if "%SEL%"=="3" goto edit
if "%SEL%"=="4" exit /b 0
goto menu

:refresh
cls
echo.
echo   正在读取当前 WLAN 的局域网 IP ...
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\refresh-lan-ip.ps1"
echo.
echo   记得在微信开发者工具里点「重新编译」让改动生效。
echo.
pause
goto menu

:showip
cls
echo.
echo   本机所有网卡 IP。
echo   真机预览只认 WLAN 那条，其它是 VMware / VirtualBox / VPN / 加速器，
echo   手机连不上。
echo.
ipconfig
echo.
pause
goto menu

:edit
start "" notepad "%REQ%"
goto menu
