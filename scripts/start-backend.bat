@echo off
chcp 65001 >nul
setlocal
title 羽毛球智能编排系统 - 后端服务（关闭本窗口即停止后端）

set "ROOT=%~dp0..\"
set "BACKEND=%ROOT%backend"

if not exist "%BACKEND%\pom.xml" (
  echo.
  echo   [错误] 找不到后端工程：
  echo          %BACKEND%
  echo.
  echo   请把本脚本放在项目根目录（与 backend、badminton 同级）。
  echo.
  pause
  exit /b 1
)

rem 读取端口，仅用于提示
set "PORT="
for /f "tokens=2 delims==" %%a in ('findstr /r /c:"^server.port=" "%BACKEND%\src\main\resources\application.properties"') do set "PORT=%%a"

echo.
echo   ============================================================
echo    正在启动后端服务...
echo   ============================================================
echo.
echo   工程目录 : %BACKEND%
echo   监听端口 : %PORT%   (来自 application.properties)
echo.
echo   说明：
echo     · 请保持本窗口开启，关闭窗口 = 停止后端
echo     · 首次启动需要下载依赖，可能要几分钟
echo     · 启动成功的标志是出现 "Started BadmintonApplication"
echo     · 手机联调前先跑一次「切换接口环境.bat」刷新局域网 IP
echo.
echo   ------------------------------------------------------------
echo.

cd /d "%BACKEND%"
call mvn spring-boot:run

echo.
echo   [后端已退出] 若上面的日志里有报错，请把错误信息发出来。
echo.
pause
