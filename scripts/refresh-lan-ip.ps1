# ============================================================
#  refresh-lan-ip.ps1
#  自动读取本机「正在上网」的局域网 IPv4，写入前端的 lan 配置
#
#  背景：真机预览时手机必须通过电脑的局域网 IP 访问后端。
#        换 WiFi / 路由器重启 / 重新联网后 IP 会变，变了手机就连不上，
#        报错表现为「无法连接后端」。手动改容易忘，故做成脚本。
#
#  用法：由「切换接口环境.bat」调用，也可直接右键 → 使用 PowerShell 运行
# ============================================================

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

# 项目根 = 本脚本所在目录
$root = Split-Path -Parent $PSScriptRoot
$req  = Join-Path $root 'badminton\utils\request.js'

Write-Host ''
if (-not (Test-Path $req)) {
    Write-Host "  [错误] 找不到前端请求文件：" -ForegroundColor Red
    Write-Host "         $req" -ForegroundColor Red
    Write-Host "         请确认本脚本放在项目根目录（与 badminton 同级）。"
    exit 1
}

# ---- 1. 找出真实的局域网 IP ----
# 排除虚拟网卡（VMware / VirtualBox / VPN / 加速器 等），优先无线网卡
$exclude = 'VMware|VirtualBox|Radmin|TAP|Hyper-V|Loopback|Bluetooth|VPN|加速器|Virtual'

$cands = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object {
        $_.IPAddress -notlike '127.*' -and
        $_.IPAddress -notlike '169.254.*' -and
        $_.InterfaceAlias -notmatch $exclude
    }

# 优先 WLAN / Wi-Fi / 无线
$picked = $cands | Where-Object { $_.InterfaceAlias -match 'WLAN|Wi-?Fi|无线' } | Select-Object -First 1
if (-not $picked) { $picked = $cands | Select-Object -First 1 }

if (-not $picked) {
    Write-Host '  [失败] 没有找到可用的局域网 IP。' -ForegroundColor Yellow
    Write-Host '         请确认电脑已连接 WiFi 或网线。'
    Write-Host '         也可手动打开 badminton\utils\request.js 修改 lan 地址。'
    exit 2
}

$ip   = $picked.IPAddress
$name = $picked.InterfaceAlias

if ($ip -notmatch '^\d{1,3}(\.\d{1,3}){3}$') {
    Write-Host "  [失败] 取到的值不是合法 IPv4：$ip" -ForegroundColor Red
    exit 3
}

# ---- 2. 读出后端端口 ----
# 端口以 backend 的 application.properties 为准，避免两边写死不一致
# （历史上脚本写死 8080，后端改成 8083 后真机就连不上了）
$props = Join-Path $root 'backend\src\main\resources\application.properties'
$port  = '8080'
if (Test-Path $props) {
    $m = Select-String -Path $props -Pattern '^\s*server\.port\s*=\s*(\d+)' -Encoding UTF8 |
        Select-Object -First 1
    if ($m) { $port = $m.Matches[0].Groups[1].Value }
}

# ---- 3. 读出旧值 ----
$content = Get-Content -Raw -Encoding UTF8 $req
if ($content -notmatch "lan:\s*'http://([^']*)'") {
    Write-Host "  [失败] 在 request.js 里没找到 lan 配置行。" -ForegroundColor Red
    Write-Host "         期望形如： lan: 'http://127.0.0.1:8080/api',"
    exit 4
}
$old = $Matches[1]

# ---- 4. 替换（用 ${ip} / ${port} 避免 PowerShell 把 $ip: 当成作用域） ----
$new = [regex]::Replace($content, "lan:\s*'http://[^']*'", "lan: 'http://${ip}:${port}/api'")

if ($new -ne $content) {
    # 无 BOM 写入，避免 BOM 干扰打包器
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($req, $new, $utf8NoBom)
}

# ---- 5. 回显 ----
Write-Host '  网卡    : ' -NoNewline; Write-Host $name -ForegroundColor Cyan
Write-Host '  后端端口: ' -NoNewline; Write-Host $port -ForegroundColor Cyan
Write-Host '  旧地址  : ' -NoNewline; Write-Host "http://$old" -ForegroundColor DarkGray
Write-Host '  新地址  : ' -NoNewline; Write-Host "http://${ip}:${port}/api" -ForegroundColor Green
Write-Host ''

if ($new -eq $content) {
    Write-Host '  [提示] 地址本来就是最新的，文件未改动。' -ForegroundColor Yellow
} else {
    Write-Host '  [完成] 已写入 badminton\utils\request.js' -ForegroundColor Green
}

Write-Host ''
Write-Host '  ── 手机能连上后端的前提（缺一不可） ──' -ForegroundColor DarkGray
Write-Host "    1. 后端已启动，且监听端口与上面一致（$port）" -ForegroundColor DarkGray
Write-Host "    2. 手机与电脑连同一个 WiFi（校园网若开了 AP 隔离则互相访问不了）" -ForegroundColor DarkGray
Write-Host "    3. Windows 防火墙已放行 $port（专用 + 公用网络）" -ForegroundColor DarkGray
Write-Host '    4. 必须用「真机调试」而不是「预览」，并勾选「不校验合法域名」' -ForegroundColor DarkGray
Write-Host '       （微信禁止真机直连 http://IP，只有真机调试会放行；详见下方说明）' -ForegroundColor DarkGray
Write-Host ''
Write-Host "    自测：用手机浏览器打开  http://${ip}:${port}/api/user/login" -ForegroundColor DarkGray
Write-Host '         能看到 JSON 就说明网络通了（连不上就是防火墙/AP 隔离问题，' -ForegroundColor DarkGray
Write-Host '         与小程序无关；能打开但小程序仍报域名未放行 → 是第 4 条没做对）。' -ForegroundColor DarkGray
Write-Host ''

exit 0
