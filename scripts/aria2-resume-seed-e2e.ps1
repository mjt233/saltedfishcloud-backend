#requires -Version 7.0
<#
.SYNOPSIS
    aria2 停机续传 + 做种保留：P5 真实环境联调脚本（正式 AC 版，P1~P4 完成后执行）。

.DESCRIPTION
    按 Lead 下发的正式 AC 表与
    microservices/sfc-download-service/docs/resume-seed-acceptance-checklist.md 编排真实环境联调。
    阶段与 AC 对应关系：
      Preflight     环境/工具/端口/镜像/配置预检（不启动任何东西）
      Build         主机构建 jar（mvn -B -DskipTests package）
      StackUp       compose 启动 nacos/mysql/redis/core/下载服务/aria2（jar 只读卷覆盖，不重建镜像）
      Configure     管理员登录、写统一配置（P2P 开启、seed=0 供 AC1 阶段干净清理）、能力与基线
      Smoke         BT 网络冒烟（失败则 P2P 类 AC 记 NOTRUN）
      Resume        AC1-1 优雅停机不杀 aria2 + AC1-2 重启接管不重复下载
      OfflineComplete AC1-4 离线期间下载完成，重启后续接上报
      CrashRecovery AC1-3 docker kill 崩溃恢复
      Cancel        AC1-5 用户取消清理
      Regression    AC1-7 HTTP / Git / core 内嵌 Worker 回归（seed=0 清理证据见 Resume）
      SeedExpire    AC2-2 做种到期清理 + AC2-3 做种期间重启（小值 seed-time 加速）
      SeedZero      AC2-4 seed-time-minutes=0 旧行为
      CrossInstance AC1-6 MySQL 数据源 + 两实例跨实例接管（切库后自动切回 SQLite）
      Seed          AC2-1 默认 10080 保留做种 + AC2-5 列表字段 + AC2-6 同 infohash 复用
      Teardown      停止并移除本轮创建的容器（保留 nacos 与镜像）

    安全阀：除 Preflight/Build/StackUp 外的阶段需要显式 -ConfirmRun。
    证据统一写入 %TEMP%\sfc-p5\evidence-<时间戳>\（不写仓库，避免误提交）。不执行任何 git 写命令。

.PARAMETER Phase
    要执行的阶段（可多选）。默认仅 Preflight。

.PARAMETER ConfirmRun
    确认执行会启动容器 / 创建真实下载任务的阶段。

.PARAMETER SkipBuild
    跳过 Build（复用已存在 jar）。

.PARAMETER KeepStack
    All 结束时保留容器不执行 Teardown。

.PARAMETER SkipNetworkSmoke
    跳过 BT 冒烟（不推荐）。

.PARAMETER TorrentUrl
    主测试资源（种子文件 URL；默认历史联调验证可用的 webtorrent Sintel）。

.PARAMETER TorrentUrlAlt
    备用资源（用于避免做种保留与后续用例的 infohash 复用冲突）；为空时相关用例按需降级。

.PARAMETER MagnetUri
    可选磁力链；用于 AC1-5 失败清理（无 peer 快速失败）等场景。

.PARAMETER SmallSeedMinutes
    AC2-2/AC2-3 加速用的小 seed-time（默认 2 分钟）。

.PARAMETER Aria2Secret
    测试专用 aria2 RPC 令牌（写入 QA_DATA/aria2-config/aria2.conf 并注入 compose）。

.PARAMETER TaskTimeoutSec
    单任务等待上限（默认 900 秒）。

.EXAMPLE
    pwsh scripts/aria2-resume-seed-e2e.ps1 -Phase Preflight
    pwsh scripts/aria2-resume-seed-e2e.ps1 -Phase All -ConfirmRun

.NOTES
    标有 "# FINAL-CODE:" 的断言依赖最终实现细节，执行前需核对；证据目录不写入仓库。
#>
[CmdletBinding()]
param(
    [ValidateSet('Preflight', 'Build', 'StackUp', 'Configure', 'Smoke', 'Resume', 'OfflineComplete', 'CrashRecovery',
        'Cancel', 'Regression', 'SeedExpire', 'SeedZero', 'CrossInstance', 'Seed', 'Teardown', 'All')]
    [string[]]$Phase = @('Preflight'),

    [switch]$ConfirmRun,
    [switch]$SkipBuild,
    [switch]$KeepStack,
    [switch]$SkipNetworkSmoke,
    [switch]$ResetRun,

    [string]$TorrentUrl = 'https://webtorrent.io/torrents/sintel.torrent',
    [string]$TorrentUrlAlt = '',
    [string]$MagnetUri = '',

    [string]$EvidenceRoot = '',
    [string]$QaDataRoot = '',
    [string]$Aria2Secret = 'sfc-p5-aria2-secret',
    [int]$SmallSeedMinutes = 2,
    [int]$TaskTimeoutSec = 900,
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# ============================================================================
# 常量与运行态
# ============================================================================
$script:RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
$script:CorePort = 18087
$script:DownloadPort = 18092
$script:Download2Port = 18093
$script:Aria2Port = 16800
$script:Aria2BtPort = 16888
$script:CoreBase = "http://127.0.0.1:$script:CorePort"
$script:DownloadBase = "http://127.0.0.1:$script:DownloadPort"
$script:Download2Base = "http://127.0.0.1:$script:Download2Port"
$script:Aria2RpcUrl = "http://127.0.0.1:$script:Aria2Port/jsonrpc"
$script:ContainerCore = 'sfc-core'
$script:ContainerDownload = 'sfc-download-service'
$script:ContainerDownload2 = 'sfc-download-service-2'
$script:ContainerAria2 = 'sfc-aria2'
$script:ContainerMysql = 'sfc-p5-mysql'
$script:ContainerRedis = 'sfc-p5-redis'
$script:ComposeBase = Join-Path $script:RepoRoot 'docker-compose-microservice-full.yml'
$script:ComposeQa = Join-Path $script:RepoRoot 'scripts/qa-p5-compose.override.yml'
$script:ComposeMysql = Join-Path $script:RepoRoot 'scripts/qa-p5-compose.mysql.override.yml'
$script:SqliteHelper = Join-Path $script:RepoRoot 'scripts/qa-p5-sqlite-query.py'
$script:AdminUser = 'admin'
$script:AdminPassword = 'admin666'   # docs/quick-start/index.md 默认管理员；仅本地测试，不打印
$script:SavePath = '/'
$script:DefaultSeedMinutes = 10080   # AC2-1 默认值
$script:DownloadDbMode = 'sqlite'    # 'sqlite' | 'mysql'（AC1-6 期间切换）
$script:MysqlRootPassword = $null
$script:Checks = [System.Collections.Generic.List[object]]::new()
$script:Results = [System.Collections.Generic.List[object]]::new()
$script:WorkerNameCache = @{}
$script:CoreSession = $null
$script:CoreCtx = $null

if (-not $QaDataRoot) { $QaDataRoot = Join-Path $env:TEMP 'sfc-p5\run' }
$script:QaData = [System.IO.Path]::GetFullPath($QaDataRoot)
if (-not $EvidenceRoot) { $EvidenceRoot = Join-Path $env:TEMP ('sfc-p5\evidence-' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
$script:EvidenceDir = [System.IO.Path]::GetFullPath($EvidenceRoot)
$script:StateFile = Join-Path $script:QaData 'p5-state.json'
$script:State = $null

# ============================================================================
# 基础输出 / 断言 / 证据
# ============================================================================
function Write-Step([string]$Message) { Write-Host ''; Write-Host "==> $Message" -ForegroundColor Cyan }
function Write-Ok([string]$Message) { Write-Host "    [OK] $Message" -ForegroundColor Green }
function Write-Warn2([string]$Message) { Write-Host "    [WARN] $Message" -ForegroundColor Yellow }
function Write-Info([string]$Message) { Write-Host "    $Message" -ForegroundColor Gray }

function New-Check([string]$Name, [bool]$Ok, [string]$Detail = '') {
    $obj = [pscustomobject]@{ name = $Name; ok = $Ok; detail = $Detail }
    $script:Checks.Add($obj)
    if ($Ok) { Write-Host "    [PASS] $Name" -ForegroundColor Green }
    else { Write-Host "    [FAIL] $Name $(if ($Detail) { '— ' + $Detail })" -ForegroundColor Red }
    return $obj
}

function Add-Result([string]$Ac, [string]$Status, [string]$Detail, [string]$Evidence = '') {
    $script:Results.Add([pscustomobject]@{
            ac = $Ac; status = $Status; detail = $Detail; evidence = $Evidence; at = (Get-Date).ToString('o')
        })
    $color = @{ PASS = 'Green'; FAIL = 'Red'; NOTRUN = 'Yellow'; SKIP = 'DarkYellow'; NA = 'DarkGray' }[$Status]
    if (-not $color) { $color = 'White' }
    Write-Host ("[{0}] {1} — {2}" -f $Status, $Ac, $Detail) -ForegroundColor $color
}

# 用一组 checks 汇总出一个 AC 结论
function Complete-Checks([string]$Ac, [object[]]$Checks, [string]$Evidence = '') {
    $all = @($Checks | Where-Object { $_ })
    $failed = @($all | Where-Object { -not $_.ok })
    if ($failed.Count -gt 0) {
        $detail = ($failed | ForEach-Object { $_.name + $(if ($_.detail) { '(' + $_.detail + ')' } else { '' }) }) -join '；'
        Add-Result -Ac $Ac -Status 'FAIL' -Detail "失败子项: $detail" -Evidence $Evidence
    }
    else {
        Add-Result -Ac $Ac -Status 'PASS' -Detail "全部子项通过（$($all.Count) 项）" -Evidence $Evidence
    }
}

function Write-Ev([string]$Name, [object]$Content) {
    if (-not (Test-Path -LiteralPath $script:EvidenceDir)) { New-Item -ItemType Directory -Force -Path $script:EvidenceDir | Out-Null }
    $path = Join-Path $script:EvidenceDir $Name
    $dir = Split-Path -Parent $path
    if ($dir -and -not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    if ($Content -is [string]) { Set-Content -LiteralPath $path -Value $Content -Encoding utf8 }
    else { $Content | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $path -Encoding utf8 }
    Write-Info "证据已保存: $path"
}

function Wait-Until {
    param([string]$Desc, [scriptblock]$Test, [int]$TimeoutSec = 120, [int]$IntervalSec = 3)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try { $r = & $Test; if ($r) { return $true } } catch { Write-Verbose "等待 '$Desc' 时出现异常: $_" }
        Start-Sleep -Seconds $IntervalSec
    }
    Write-Warn2 "等待超时（${TimeoutSec}s）: $Desc"
    return $false
}

function Test-HttpReady([string]$Url, [int]$TimeoutSec = 5) {
    try {
        $r = Invoke-WebRequest -Uri $Url -TimeoutSec $TimeoutSec -SkipCertificateCheck -UseBasicParsing -ErrorAction Stop
        return ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500)
    }
    catch {
        if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -ge 300) { return $true }
        return $false
    }
}

function Test-PortListening([int]$Port) {
    try { return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) }
    catch { return [bool](netstat -ano | Select-String -Pattern ":$Port\s+.*LISTENING") }
}

function Get-FileSha256([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { return $null }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Save-PhaseEvidence([string]$Name) { Write-Ev "results-$Name.json" $script:Results }

function Save-Summary {
    $md = New-Object System.Text.StringBuilder
    [void]$md.AppendLine('# P5 联调执行摘要（脚本自动生成）')
    [void]$md.AppendLine('')
    [void]$md.AppendLine("> 生成时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')")
    [void]$md.AppendLine('')
    [void]$md.AppendLine('| AC/用例 | 结论 | 说明 | 证据 |')
    [void]$md.AppendLine('|---|---|---|---|')
    foreach ($r in $script:Results) {
        $ev = if ($r.evidence) { Split-Path -Leaf $r.evidence } else { '' }
        [void]$md.AppendLine("| $($r.ac) | $($r.status) | $($r.detail -replace "`|", '\|') | $ev |")
    }
    Write-Ev 'summary.md' $md.ToString()
    Write-Ev 'summary.json' $script:Results
}

# ============================================================================
# 状态持久化
# ============================================================================
function Save-State {
    $dir = Split-Path -Parent $script:StateFile
    if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    $script:State | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $script:StateFile -Encoding utf8
}
function Load-State {
    if (Test-Path -LiteralPath $script:StateFile) {
        $raw = Get-Content -LiteralPath $script:StateFile -Raw -Encoding utf8
        if ($raw.Trim()) { $script:State = $raw | ConvertFrom-Json; return }
    }
    $script:State = [pscustomobject]@{
        createdAt = (Get-Date).ToString('o'); evidenceDir = $script:EvidenceDir; qaData = $script:QaData
        coreJar = ''; downloadJar = ''; adminUid = 0; baseline = $null; networkOk = $null
        tasks = @(); lastStopAt = $null; stackRunning = $false; dbMode = 'sqlite'
    }
}
function Get-StateValue([string]$Name, $Default = $null) {
    if ($script:State -and ($script:State.PSObject.Properties.Name -contains $Name)) {
        $v = $script:State.$Name
        if ($null -ne $v) { return $v }
    }
    return $Default
}
function Set-StateValue([string]$Name, $Value) {
    if ($script:State.PSObject.Properties.Name -contains $Name) { $script:State.$Name = $Value }
    else { $script:State | Add-Member -NotePropertyName $Name -NotePropertyValue $Value -Force }
    Save-State
}

# ============================================================================
# 环境 / compose / 容器
# ============================================================================
function Read-DotEnv {
    param([string]$Path)
    $map = @{}
    if (-not (Test-Path -LiteralPath $Path)) { return $map }
    foreach ($line in Get-Content -LiteralPath $Path -Encoding utf8) {
        $t = $line.Trim()
        if (-not $t -or $t.StartsWith('#')) { continue }
        $idx = $t.IndexOf('=')
        if ($idx -lt 1) { continue }
        $map[$t.Substring(0, $idx).Trim()] = $t.Substring($idx + 1)
    }
    return $map
}

function Set-ComposeEnvironment {
    $dotenv = Read-DotEnv (Join-Path $script:RepoRoot '.env')
    $env:QA_DATA = $script:QaData
    $env:QA_CORE_JAR = [string](Get-StateValue 'coreJar')
    $env:QA_DOWNLOAD_JAR = [string](Get-StateValue 'downloadJar')
    $env:ARIA2_RPC_SECRET = $script:Aria2Secret
    foreach ($k in @('SFC_JWT_SECRET', 'SFC_INTERNAL_KEY', 'NACOS_USERNAME', 'NACOS_PASSWORD', 'MAVEN_MIRROR_URL', 'MYSQL_ROOT_PASSWORD', 'MYSQL_DATABASE')) {
        if ($dotenv.ContainsKey($k) -and -not [Environment]::GetEnvironmentVariable($k)) {
            Set-Item -Path "Env:$k" -Value $dotenv[$k]
        }
    }
    if (-not $env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD = 'test' }
    if (-not $env:MYSQL_DATABASE) { $env:MYSQL_DATABASE = 'xyy' }
}

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    Set-ComposeEnvironment
    & docker compose -f $script:ComposeBase -f $script:ComposeQa @Arguments
    if ($LASTEXITCODE -ne 0) { throw "docker compose $($Arguments -join ' ') 失败（exit=$LASTEXITCODE）" }
}

function Invoke-ComposeMysql {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    Set-ComposeEnvironment
    & docker compose -f $script:ComposeBase -f $script:ComposeQa -f $script:ComposeMysql @Arguments
    if ($LASTEXITCODE -ne 0) { throw "docker compose(MySQL) $($Arguments -join ' ') 失败（exit=$LASTEXITCODE）" }
}

function Get-ContainerHealth([string]$Name) {
    $v = & docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $Name 2>$null
    if ($LASTEXITCODE -ne 0) { return 'missing' }
    return ($v | Out-String).Trim()
}

# 校验 compose 文件（基础 + QA override，以及 + MySQL override）合并是否有效
function Test-ComposeConfigValid {
    Set-ComposeEnvironment
    if (-not $env:QA_DATA) { $env:QA_DATA = $script:QaData }
    if (-not $env:QA_CORE_JAR) { $env:QA_CORE_JAR = 'C:\qa-fake\sfc-core.jar' }
    if (-not $env:QA_DOWNLOAD_JAR) { $env:QA_DOWNLOAD_JAR = 'C:\qa-fake\sfc-download-service.jar' }
    if (-not $env:ARIA2_RPC_SECRET) { $env:ARIA2_RPC_SECRET = $script:Aria2Secret }
    if (-not $env:SFC_INTERNAL_KEY) { $env:SFC_INTERNAL_KEY = '0123456789abcdef0123456789abcdef' }
    if (-not $env:NACOS_PASSWORD) { $env:NACOS_PASSWORD = 'qa-dummy' }
    if (-not $env:SFC_JWT_SECRET) { $env:SFC_JWT_SECRET = '0123456789abcdef0123456789abcdef' }
    $r1 = & docker compose -f $script:ComposeBase -f $script:ComposeQa config 2>&1
    $ok1 = $LASTEXITCODE -eq 0
    $r2 = & docker compose -f $script:ComposeBase -f $script:ComposeQa -f $script:ComposeMysql config 2>&1
    $ok2 = $LASTEXITCODE -eq 0
    if (-not $ok1) { Write-Ev 'compose-config-qa-error.log' (($r1 | Out-String)) }
    if (-not $ok2) { Write-Ev 'compose-config-mysql-error.log' (($r2 | Out-String)) }
    return ($ok1 -and $ok2)
}

function Get-ContainerLogs([string]$Name, [string]$Since = '') {
    if ($Since) { return (& docker logs --since $Since $Name 2>&1 | Out-String) }
    return (& docker logs $Name 2>&1 | Out-String)
}

function Get-ContainerIp([string]$Name) {
    $v = & docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' $Name 2>$null
    if ($LASTEXITCODE -ne 0) { return '' }
    foreach ($part in ($v | Out-String).Trim().Split(' ', [System.StringSplitOptions]::RemoveEmptyEntries)) {
        if ($part -match '^\d+\.\d+\.\d+\.\d+$') { return $part }
    }
    return ''
}

function Wait-StackReady {
    Write-Step '等待 compose 栈就绪'
    if (-not (Wait-Until -Desc 'mysql healthy' -TimeoutSec 180 -IntervalSec 5 -Test { (Get-ContainerHealth $script:ContainerMysql) -eq 'healthy' })) { throw 'MySQL 健康检查超时' }
    if (-not (Wait-Until -Desc 'nacos healthy' -TimeoutSec 180 -IntervalSec 5 -Test { (Get-ContainerHealth 'nacos') -eq 'healthy' })) { throw 'Nacos 健康检查超时' }
    if (-not (Wait-Until -Desc 'core HTTP 就绪' -TimeoutSec 240 -IntervalSec 5 -Test { Test-HttpReady "$script:CoreBase/api/hello/feature" })) { throw 'sfc-core HTTP 未就绪' }
    Write-Ok 'core HTTP 就绪'
    if (-not (Wait-Until -Desc '下载服务 HTTP 就绪' -TimeoutSec 180 -IntervalSec 5 -Test { Test-HttpReady "$script:DownloadBase/static/index.umd.js" })) { throw 'sfc-download-service HTTP 未就绪' }
    Write-Ok '下载服务 HTTP 就绪'
    if (-not (Wait-Until -Desc 'aria2 RPC 就绪' -TimeoutSec 120 -IntervalSec 3 -Test {
                try { $null = Invoke-Aria2RpcCore 'aria2.getVersion' @(); return $true } catch { return $false }
            })) { throw 'aria2 RPC 未就绪' }
    Write-Ok 'aria2 RPC 就绪'
}

# ============================================================================
# core 管理端
# ============================================================================
function Connect-Core {
    # 登录响应体 data 即 JWT；core 的登录接口不写 Set-Cookie，后续管理接口必须显式带 Cookie: token=<jwt>
    $login = Invoke-RestMethod -Method Post -Uri "$script:CoreBase/api/user/token" `
        -Body @{ user = $script:AdminUser; passwd = $script:AdminPassword } `
        -ContentType 'application/x-www-form-urlencoded' -TimeoutSec 30
    if ($login.code -ne 200 -or -not $login.data) { throw "core 管理员登录失败: $($login | ConvertTo-Json -Compress)" }
    $cookie = "token=$($login.data)"
    $me = Invoke-RestMethod -Method Get -Uri "$script:CoreBase/api/user" -Headers @{ Cookie = $cookie } -TimeoutSec 30
    if ($me.code -ne 200 -or -not $me.data -or -not $me.data.id) { throw '获取当前管理员信息失败' }
    $uid = [long]$me.data.id
    Set-StateValue 'adminUid' $uid
    Write-Ok "core 管理员登录成功（uid=$uid，不输出令牌）"
    return [pscustomobject]@{ session = $cookie; uid = $uid }
}

function Invoke-CoreApi {
    param([string]$Method, [string]$Path, [object]$Body = $null, $Session = $null)
    $p = @{ Method = $Method; Uri = "$script:CoreBase$Path"; TimeoutSec = 120 }
    if ($Session -is [string] -and $Session) { $p.Headers = @{ Cookie = $Session } }
    elseif ($Session) { $p.WebSession = $Session }
    if ($null -ne $Body) {
        $p.ContentType = 'application/json'
        # 注意：PowerShell 对「单元素数组」默认序列化为对象；-AsArray 保证接口要求的 JSON 数组形态
        if ($Body -is [System.Collections.IList]) { $p.Body = ($Body | ConvertTo-Json -Depth 10 -Compress -AsArray) }
        else { $p.Body = ($Body | ConvertTo-Json -Depth 10 -Compress) }
    }
    return Invoke-RestMethod @p
}

function Set-CoreConfig([hashtable]$Configs) {
    $items = @()
    foreach ($k in $Configs.Keys) { $items += @{ name = $k; value = [string]$Configs[$k] } }
    $resp = Invoke-CoreApi -Method Put -Path '/api/admin/sys/config/batchSetConfig' -Body @($items) -Session $script:CoreSession
    if ($resp.code -ne 200) { throw "写入 core 统一配置失败: $($resp | ConvertTo-Json -Compress)" }
    $keys = ($Configs.Keys | Sort-Object) -join ', '
    Write-Ok "已写入 core 统一配置: $keys（值不打印敏感项）"
}

function Ensure-CoreSession {
    if (-not $script:CoreSession) {
        $script:CoreCtx = Connect-Core
        $script:CoreSession = $script:CoreCtx.session
    }
    return [long](Get-StateValue 'adminUid')
}

function Get-WorkerInfos {
    $resp = Invoke-CoreApi -Method Get -Path '/api/asyncTask/listWorker' -Session $script:CoreSession
    if ($resp.code -ne 200) { return @() }
    return @($resp.data)
}

function Wait-DownloadWorkers([int]$Count, [int]$TimeoutSec = 180) {
    return Wait-Until -Desc "下载服务执行者实例数 >= $Count" -TimeoutSec $TimeoutSec -IntervalSec 5 -Test {
        $workers = @(Get-WorkerInfos | Where-Object { $_.serviceId -eq 'sfc-download-service' })
        return ($workers.Count -ge $Count)
    }
}

function Get-WorkerContainerName([string]$WorkerId) {
    if (-not $WorkerId) { return $null }
    if ($script:WorkerNameCache.ContainsKey($WorkerId)) { return $script:WorkerNameCache[$WorkerId] }
    $hostOnly = $WorkerId.Split(':')[0]
    foreach ($c in @($script:ContainerDownload, $script:ContainerDownload2)) {
        $ip = Get-ContainerIp $c
        if ($ip -and $ip -eq $hostOnly) { $script:WorkerNameCache[$WorkerId] = $c; return $c }
    }
    return $null
}

# ============================================================================
# 下载服务 API
# ============================================================================
function Invoke-DownloadApi {
    param([string]$Method, [string]$Path, [object]$Body = $null, [long]$Uid = 0, [string]$BaseUrl = '')
    if (-not $BaseUrl) { $BaseUrl = $script:DownloadBase }
    $p = @{ Method = $Method; Uri = "$BaseUrl$Path"; TimeoutSec = 120; Headers = @{ 'X-Sfc-Uid' = [string]$Uid } }
    if ($null -ne $Body) {
        $p.ContentType = 'application/json'
        # 注意：PowerShell 对「单元素数组」默认序列化为对象；-AsArray 保证接口要求的 JSON 数组形态
        if ($Body -is [System.Collections.IList]) { $p.Body = ($Body | ConvertTo-Json -Depth 10 -Compress -AsArray) }
        else { $p.Body = ($Body | ConvertTo-Json -Depth 10 -Compress) }
    }
    return Invoke-RestMethod @p
}

function Get-DownloadCapabilities([long]$Uid, [bool]$Refresh = $true) {
    $resp = Invoke-DownloadApi -Method Get -Path "/api/task/download/capabilities?refresh=$($Refresh.ToString().ToLowerInvariant())" -Uid $Uid
    if ($resp.code -ne 200) { throw "获取下载能力失败: $($resp | ConvertTo-Json -Compress)" }
    return $resp.data
}

function New-DownloadTask([long]$Uid, [string]$Type, [string]$Url, [string]$SavePath = $script:SavePath, [hashtable]$Options = $null) {
    $body = @{ type = $Type; url = $Url; uid = $Uid; savePath = $SavePath }
    if ($Options) { $body.options = $Options }
    $resp = Invoke-DownloadApi -Method Post -Path '/api/task/download' -Body $body -Uid $Uid
    if ($resp.code -ne 200) { throw "创建下载任务失败: code=$($resp.code), msg=$($resp.msg)" }
    $downloadId = [long]$resp.data
    Write-Ok "已创建下载任务 downloadId=$downloadId（type=$Type）"
    return $downloadId
}

function Stop-DownloadTask([long]$Uid, [long]$DownloadId) {
    return Invoke-DownloadApi -Method Delete -Path "/api/task/download?taskId=$DownloadId" -Uid $Uid
}

function Get-DownloadTaskList([long]$Uid, [int]$Size = 200) {
    $resp = Invoke-DownloadApi -Method Get -Path "/api/task/download?uid=$Uid&page=1&size=$Size&type=ALL" -Uid $Uid
    if ($resp.code -ne 200) { throw "查询下载任务列表失败: $($resp | ConvertTo-Json -Compress)" }
    return $resp.data
}

# ============================================================================
# DB：core（MySQL sfc-p5-mysql / xyy）与下载服务（SQLite 或 MySQL sfc_download）
# ============================================================================
function Get-MysqlRootPassword {
    if ($script:MysqlRootPassword) { return $script:MysqlRootPassword }
    $lines = & docker inspect $script:ContainerMysql --format '{{range .Config.Env}}{{println .}}{{end}}' 2>$null
    $line = $lines | Where-Object { $_ -like 'MYSQL_ROOT_PASSWORD=*' } | Select-Object -First 1
    $script:MysqlRootPassword = if ($line) { $line.Substring('MYSQL_ROOT_PASSWORD='.Length) } else { 'test' }
    return $script:MysqlRootPassword
}

function Get-MysqlSql([string]$Database, [string]$Sql) {
    $pwd = Get-MysqlRootPassword
    $lines = & docker exec $script:ContainerMysql mysql -B -uroot "-p$pwd" -e $Sql $Database 2>$null
    if ($LASTEXITCODE -ne 0) { throw "MySQL 查询失败($Database): $Sql" }
    if (-not $lines) { return @() }
    if ($lines.Count -le 1) { return @() }
    $headers = $lines[0] -split "`t"
    $rows = @()
    foreach ($line in $lines[1..($lines.Count - 1)]) {
        if (-not $line) { continue }
        $vals = $line -split "`t"
        $o = [ordered]@{}
        for ($i = 0; $i -lt $headers.Count; $i++) { $o[$headers[$i]] = if ($i -lt $vals.Count) { $vals[$i] } else { $null } }
        $rows += [pscustomobject]$o
    }
    return $rows
}

function Get-CoreSql([string]$Sql) { return Get-MysqlSql 'xyy' $Sql }

function Copy-DownloadDb {
    $dir = Join-Path $script:EvidenceDir 'db'
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $dest = Join-Path $dir 'download.db'
    Remove-Item -LiteralPath $dest -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath "$dest-wal" -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath "$dest-shm" -Force -ErrorAction SilentlyContinue
    & docker cp "$($script:ContainerDownload):/data/download.db" $dest 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '复制下载服务 SQLite 库失败（容器未创建或已切 MySQL 模式）' }
    & docker cp "$($script:ContainerDownload):/data/download.db-wal" "$dest-wal" 2>$null | Out-Null
    & docker cp "$($script:ContainerDownload):/data/download.db-shm" "$dest-shm" 2>$null | Out-Null
    return $dest
}

function Get-DownloadSql([string]$Sql) {
    if ($script:DownloadDbMode -eq 'mysql') { return Get-MysqlSql 'sfc_download' $Sql }
    $db = Copy-DownloadDb
    $json = & python $script:SqliteHelper $db $Sql
    if ($LASTEXITCODE -ne 0) { throw "下载服务 SQLite 查询失败: $Sql" }
    if (-not $json) { return @() }
    return @($json | ConvertFrom-Json)
}

function Get-CoreTaskRow([long]$TaskId) {
    $rows = Get-CoreSql "SELECT id,task_type,name,status,retry_count,claimed_service,claimed_worker,claimed_role,claimed_at,last_report_at,finish_date,failed_date FROM async_task_record WHERE id=$TaskId"
    if (-not $rows -or $rows.Count -eq 0) { return $null }
    return $rows[0]
}

function Get-CheckpointRow([long]$TaskId) {
    $rows = Get-CoreSql "SELECT task_id,task_type,LENGTH(checkpoint) AS checkpoint_len,checkpoint,update_at FROM async_task_checkpoint WHERE task_id=$TaskId"
    if (-not $rows -or $rows.Count -eq 0) { return $null }
    return $rows[0]
}

function Get-DownloadTaskRow([long]$DownloadId) {
    $rows = Get-DownloadSql "SELECT id,uid,type,task_id,status,loaded,size,name,save_path,finish_at,message FROM download_task WHERE id=$DownloadId"
    if (-not $rows -or $rows.Count -eq 0) { return $null }
    return $rows[0]
}

function Get-SeedTaskRow([long]$DownloadId) {
    $rows = Get-DownloadSql "SELECT id,download_id,content_gid,infohash,torrent_sha256,aria2_dir,local_dir,seed_start_at,seed_end_at,status,message,finish_at FROM download_seed_task WHERE download_id=$DownloadId"
    if (-not $rows -or $rows.Count -eq 0) { return $null }
    return $rows[0]
}

function Get-DownloadBaseline {
    $maxDl = @(Get-DownloadSql 'SELECT COALESCE(MAX(id),0) AS v FROM download_task') | Select-Object -First 1
    $maxSeed = @(Get-DownloadSql 'SELECT COALESCE(MAX(id),0) AS v FROM download_seed_task') | Select-Object -First 1
    $maxTask = @(Get-CoreSql 'SELECT COALESCE(MAX(id),0) AS v FROM async_task_record') | Select-Object -First 1
    return [pscustomobject]@{
        maxDownloadId = [long]$(if ($maxDl) { $maxDl.v } else { 0 })
        maxSeedId     = [long]$(if ($maxSeed) { $maxSeed.v } else { 0 })
        maxTaskId     = [long]$(if ($maxTask) { $maxTask.v } else { 0 })
    }
}

# ============================================================================
# aria2 JSON-RPC
# ============================================================================
function Invoke-Aria2RpcCore {
    param([string]$Method, [System.Collections.IList]$RpcParams = $null, [switch]$IgnoreError)
    $arr = New-Object System.Collections.ArrayList
    [void]$arr.Add("token:$($script:Aria2Secret)")
    if ($null -ne $RpcParams) { foreach ($p in $RpcParams) { [void]$arr.Add($p) } }
    $payload = [ordered]@{ jsonrpc = '2.0'; id = [guid]::NewGuid().ToString(); method = $Method; params = $arr }
    $json = $payload | ConvertTo-Json -Depth 12 -Compress
    try {
        $resp = Invoke-RestMethod -Uri $script:Aria2RpcUrl -Method Post -ContentType 'application/json' -Body $json -TimeoutSec 20
    }
    catch {
        if ($IgnoreError) { return $null }
        throw "aria2 RPC 请求失败($Method): $($_.Exception.Message)"
    }
    if ($resp.PSObject.Properties.Name -contains 'error' -and $resp.error) {
        if ($IgnoreError) { return $null }
        throw "aria2 RPC 错误($Method): code=$($resp.error.code), message=$($resp.error.message)"
    }
    if ($resp.PSObject.Properties.Name -contains 'result') { return $resp.result }
    return $null
}

function Invoke-Aria2Rpc {
    param([string]$Method, [object[]]$Params = @(), [switch]$IgnoreError)
    $list = New-Object System.Collections.ArrayList
    foreach ($p in $Params) { [void]$list.Add($p) }
    return Invoke-Aria2RpcCore -Method $Method -RpcParams $list -IgnoreError:$IgnoreError
}

function Get-Aria2Version { return (Invoke-Aria2RpcCore -Method 'aria2.getVersion' -RpcParams $null).version }

function Get-Aria2Active {
    $keys = @('gid', 'status', 'totalLength', 'completedLength', 'downloadSpeed', 'dir', 'seeder', 'bittorrent', 'files', 'followedBy', 'errorMessage')
    return @(Invoke-Aria2RpcCore -Method 'aria2.tellActive' -RpcParams ([System.Collections.ArrayList]@(, $keys)))
}

function Get-Aria2Status([string]$Gid) {
    try {
        $keys = @('gid', 'status', 'totalLength', 'completedLength', 'downloadSpeed', 'uploadSpeed', 'dir', 'seeder', 'bittorrent', 'files', 'followedBy', 'errorMessage')
        return Invoke-Aria2RpcCore -Method 'aria2.tellStatus' -RpcParams ([System.Collections.ArrayList]@($Gid, $keys))
    }
    catch { return $null }
}

function Get-Aria2NewActiveGids([string[]]$BeforeGids) {
    $now = @(Get-Aria2Active)
    $before = @($BeforeGids)
    return @($now | Where-Object { $before -notcontains $_.gid })
}

# ============================================================================
# 文件系统 / 路径映射
# ============================================================================
function Convert-SharedToHostPath([string]$ContainerPath) {
    if (-not $ContainerPath) { return $null }
    $sharedHost = Join-Path $script:QaData 'shared'
    $p = $ContainerPath -replace '\\', '/'
    foreach ($prefix in @('/shared/download', '/downloads')) {
        if ($p.StartsWith($prefix)) {
            $rel = $p.Substring($prefix.Length).TrimStart('/')
            return (Join-Path $sharedHost ($rel -replace '/', [System.IO.Path]::DirectorySeparatorChar))
        }
    }
    return $null
}

function Get-TaskWorkDirs([long]$DownloadId) {
    $root = Join-Path $script:QaData 'shared/offline-download'
    if (-not (Test-Path -LiteralPath $root)) { return @() }
    return @(Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "$DownloadId-*" })
}

function Find-StoreFile([string]$NamePattern) {
    $store = Join-Path $script:QaData 'core/store'
    if (-not (Test-Path -LiteralPath $store)) { return @() }
    return @(Get-ChildItem -LiteralPath $store -Recurse -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -like $NamePattern })
}

# ============================================================================
# 统一等待器
# ============================================================================
function Wait-DownloadStatus([long]$DownloadId, [int[]]$Statuses, [int]$TimeoutSec = 600) {
    return Wait-Until -Desc "下载任务 $DownloadId 状态进入 [$($Statuses -join ',')]" -TimeoutSec $TimeoutSec -IntervalSec 3 -Test {
        $row = Get-DownloadTaskRow -DownloadId $DownloadId
        return ($row -and ($Statuses -contains [int]$row.status))
    }
}

function Wait-CoreTaskStatus([long]$TaskId, [int[]]$Statuses, [int]$TimeoutSec = 600) {
    return Wait-Until -Desc "异步任务 $TaskId 状态进入 [$($Statuses -join ',')]" -TimeoutSec $TimeoutSec -IntervalSec 3 -Test {
        $row = Get-CoreTaskRow -TaskId $TaskId
        return ($row -and ($Statuses -contains [int]$row.status))
    }
}

function Wait-Aria2AllGidsDone([string[]]$Gids, [int]$TimeoutSec = 600) {
    return Wait-Until -Desc 'aria2 全部 gid 结束（非 active/waiting/paused）' -TimeoutSec $TimeoutSec -IntervalSec 5 -Test {
        $all = @($Gids | ForEach-Object { Get-Aria2Status $_ } | Where-Object { $_ })
        if ($all.Count -eq 0) { return $false }
        $statuses = @($all | ForEach-Object { [string]$_.status })
        return ($statuses -notcontains 'active' -and $statuses -notcontains 'waiting' -and $statuses -notcontains 'paused')
    }
}

# ============================================================================
# 下载服务启停 / DB 模式切换
# ============================================================================
function Stop-ServiceGracefully([string]$ContainerName = $script:ContainerDownload, [int]$DockerStopTimeoutSec = 45) {
    $since = (Get-Date).AddSeconds(-5).ToString('o')
    Write-Info "docker stop -t $DockerStopTimeoutSec $ContainerName（优雅停机，触发 JVM shutdown hook）"
    & docker stop -t $DockerStopTimeoutSec $ContainerName | Out-Null
    $exit = (& docker inspect -f '{{.State.ExitCode}}' $ContainerName | Out-String).Trim()
    $logs = Get-ContainerLogs $ContainerName $since
    Write-Ev "shutdown-$ContainerName.log" $logs
    Write-Ok "$ContainerName 已停止（exit=$exit）"
    return [pscustomobject]@{ Log = $logs; SinceUtc = (Get-Date).AddSeconds(-60).ToString('o'); Exit = $exit }
}

function Start-ServiceAndWait([string]$ContainerName = $script:ContainerDownload, [string]$ReadyUrl = $script:DownloadBase) {
    Write-Info "docker start $ContainerName"
    & docker start $ContainerName | Out-Null
    $ok = Wait-Until -Desc "$ContainerName HTTP 就绪" -TimeoutSec 180 -IntervalSec 5 -Test { Test-HttpReady "$ReadyUrl/static/index.umd.js" }
    if (-not $ok) { throw "$ContainerName 重启后 HTTP 未就绪" }
    Write-Ok "$ContainerName 已重启并就绪"
}

function Switch-DownloadDbMode([string]$Mode) {
    Write-Step "切换下载服务数据源模式 -> $Mode"
    if ($Mode -eq 'mysql') {
        # 先确保数据库存在（URL 也已带 createDatabaseIfNotExist=true，双保险）
        $pwd = Get-MysqlRootPassword
        & docker exec $script:ContainerMysql mysql -uroot "-p$pwd" -e "CREATE DATABASE IF NOT EXISTS sfc_download CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci" 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) { throw '创建 MySQL sfc_download 库失败' }
        Invoke-ComposeMysql up --detach --force-recreate sfc-download-service sfc-download-service-2 | Out-Host
        if (-not (Wait-Until -Desc '实例1 MySQL 模式 HTTP 就绪' -TimeoutSec 240 -IntervalSec 5 -Test { Test-HttpReady "$script:DownloadBase/static/index.umd.js" })) { throw '实例1 未就绪（MySQL 模式）' }
        if (-not (Wait-Until -Desc '实例2 MySQL 模式 HTTP 就绪' -TimeoutSec 240 -IntervalSec 5 -Test { Test-HttpReady "$script:Download2Base/static/index.umd.js" })) { throw '实例2 未就绪（MySQL 模式）' }
        $script:DownloadDbMode = 'mysql'
        New-Item -ItemType Directory -Force -Path (Join-Path $script:QaData 'download2') | Out-Null
        Write-Ok '已切换到 MySQL 数据源（两实例）'
    }
    else {
        Invoke-Compose up --detach --force-recreate sfc-download-service | Out-Host
        if (-not (Wait-Until -Desc '实例1 SQLite 模式 HTTP 就绪' -TimeoutSec 240 -IntervalSec 5 -Test { Test-HttpReady "$script:DownloadBase/static/index.umd.js" })) { throw '实例1 未就绪（SQLite 模式）' }
        $script:DownloadDbMode = 'sqlite'
        Write-Ok '已切回 SQLite 数据源（单实例）'
    }
    Set-StateValue 'dbMode' $script:DownloadDbMode
}

# ============================================================================
# 前置：aria2 配置
# ============================================================================
function Initialize-Aria2Config {
    $confDir = Join-Path $script:QaData 'aria2-config'
    New-Item -ItemType Directory -Force -Path $confDir | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $script:QaData 'shared') | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $script:QaData 'download') | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $script:QaData 'download2') | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $script:QaData 'core/store') | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $script:QaData 'mysql') | Out-Null
    $conf = @"
# P5 联调专用 aria2 配置（脚本生成，rpc-secret 与 compose 注入一致）
dir=/downloads
continue=true
enable-rpc=true
rpc-listen-all=true
rpc-allow-origin-all=true
rpc-listen-port=6800
rpc-secret=$($script:Aria2Secret)
seed-time=0
bt-save-metadata=true
file-allocation=none
max-concurrent-downloads=5
enable-dht=true
enable-peer-exchange=true
bt-enable-lpd=true
listen-port=16888
"@
    Set-Content -LiteralPath (Join-Path $confDir 'aria2.conf') -Value $conf -Encoding utf8
    Write-Ok "已生成 aria2.conf: $(Join-Path $confDir 'aria2.conf')"
}

# ============================================================================
# 各阶段实现
# ============================================================================
function Invoke-PhasePreflight {
    Write-Step 'Phase Preflight：环境预检'
    $checks = @()

    $javaOut = (& java -version 2>&1 | Out-String)
    $checks += New-Check 'JDK 25 可用' ($javaOut -match 'version "25') ($javaOut.Trim().Split("`n")[0])
    $mvnOut = (& mvn -version 2>&1 | Out-String)
    $checks += New-Check 'Maven 可用' ($LASTEXITCODE -eq 0) ($mvnOut.Trim().Split("`n")[0])
    $dockerOut = (& docker version --format '{{.Server.Version}}' 2>&1 | Out-String).Trim()
    $checks += New-Check 'Docker daemon 可用' ($LASTEXITCODE -eq 0 -and $dockerOut) "server=$dockerOut"
    $pyOut = ((& python --version 2>&1) | Out-String).Trim()
    $checks += New-Check 'python 可用（SQLite 查询辅助）' ($pyOut -match 'Python 3') $pyOut

    $checks += New-Check 'compose 基础文件存在' (Test-Path -LiteralPath $script:ComposeBase) $script:ComposeBase
    $checks += New-Check 'compose QA override 存在' (Test-Path -LiteralPath $script:ComposeQa) $script:ComposeQa
    $checks += New-Check 'compose MySQL override 存在' (Test-Path -LiteralPath $script:ComposeMysql) $script:ComposeMysql
    $checks += New-Check '.env 存在' (Test-Path -LiteralPath (Join-Path $script:RepoRoot '.env')) ''
    $dotenv = Read-DotEnv (Join-Path $script:RepoRoot '.env')
    foreach ($k in @('SFC_JWT_SECRET', 'SFC_INTERNAL_KEY', 'NACOS_PASSWORD')) {
        $checks += New-Check ".env 含 $k" $dotenv.ContainsKey($k) '值不打印'
    }

    foreach ($img in @('sfc/sfc-core:latest', 'sfc/sfc-download-service:latest', 'p3terx/aria2-pro', 'mysql:8.0', 'redis:alpine', 'nacos/nacos-server:latest')) {
        & docker image inspect $img 2>$null | Out-Null
        $checks += New-Check "镜像存在: $img" ($LASTEXITCODE -eq 0) ''
    }

    foreach ($port in @($script:CorePort, $script:DownloadPort, $script:Download2Port, $script:Aria2Port, $script:Aria2BtPort)) {
        $busy = Test-PortListening $port
        $checks += New-Check "端口 $port 空闲" (-not $busy) '被占用时先排查上次未清理的测试栈'
    }
    if (Test-PortListening 6800) {
        Write-Warn2 '本机 6800 已有进程监听（原生 aria2c，属预期）；测试使用 16800'
    }

    $checks += New-Check 'compose 配置合并有效（QA / MySQL override）' (Test-ComposeConfigValid) ''
    $checks += New-Check 'QA 数据目录可写' $(try {
            New-Item -ItemType Directory -Force -Path $script:QaData | Out-Null
            New-Item -ItemType Directory -Force -Path $script:EvidenceDir | Out-Null
            $true
        }
        catch { $false }) $script:QaData

    Write-Ev 'preflight-tools.json' $checks
    Complete-Checks 'P0-环境' $checks
}

function Invoke-PhaseBuild {
    Write-Step 'Phase Build：构建最新 jar'
    if ($SkipBuild) { Add-Result 'P0-构建' 'SKIP' '-SkipBuild，复用现有 jar' }
    else {
        $lock = Join-Path $env:TEMP 'sfc-backend-build.lock'
        if (Test-Path -LiteralPath $lock) {
            $age = (Get-Date) - (Get-Item -LiteralPath $lock).LastWriteTime
            if ($age.TotalMinutes -lt 30) { throw "构建锁被占用（$([int]$age.TotalMinutes) 分钟前）：可能有开发成员正在 mvn，禁止并发构建。" }
            Write-Warn2 '发现陈旧构建锁，移除后继续'
            Remove-Item -LiteralPath $lock -Recurse -Force
        }
        New-Item -ItemType Directory -Force -Path $lock | Out-Null
        try {
            Write-Info '执行: mvn -B -DskipTests package（根 reactor）'
            $log = Join-Path $script:EvidenceDir 'build.log'
            $prev = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            $tail = & mvn -B -DskipTests package 2>&1 | Tee-Object -FilePath $log | Select-Object -Last 5
            $exit = $LASTEXITCODE
            $ErrorActionPreference = $prev
            $tail | Write-Host
            if ($exit -ne 0) { throw "mvn 构建失败（exit=$exit），见 $log" }
            Add-Result 'P0-构建' 'PASS' 'mvn -B -DskipTests package 成功'
        }
        finally { Remove-Item -LiteralPath $lock -Recurse -Force -ErrorAction SilentlyContinue }
    }

    $coreJar = Join-Path $script:RepoRoot 'release/sfc-core.jar'
    if (-not (Test-Path -LiteralPath $coreJar)) {
        $alt = Get-ChildItem -LiteralPath (Join-Path $script:RepoRoot 'sfc-core/target') -Filter 'sfc-core-*.jar' -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notlike '*-sources.jar' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($alt) { $coreJar = $alt.FullName }
    }
    $downloadJarDir = Join-Path $script:RepoRoot 'microservices/sfc-download-service/target'
    $downloadJar = Get-ChildItem -LiteralPath $downloadJarDir -Filter 'sfc-download-service-*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike '*-sources.jar' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not (Test-Path -LiteralPath $coreJar)) { throw "找不到 sfc-core jar: $coreJar（先完整构建或去掉 -SkipBuild）" }
    if (-not $downloadJar) { throw "找不到 sfc-download-service jar（目录: $downloadJarDir，先完整构建）" }
    Set-StateValue 'coreJar' $coreJar
    Set-StateValue 'downloadJar' $downloadJar.FullName
    Write-Ok "core jar: $coreJar（$((Get-Item $coreJar).LastWriteTime)）"
    Write-Ok "download jar: $($downloadJar.FullName)（$($downloadJar.LastWriteTime)）"
    Add-Result 'P0-jar' 'PASS' '两侧 jar 已定位并记录'
}

function Invoke-PhaseStackUp {
    Write-Step 'Phase StackUp：启动 compose 栈（SQLite 模式）'
    $coreJar = Get-StateValue 'coreJar'
    $downloadJar = Get-StateValue 'downloadJar'
    if (-not $coreJar -or -not (Test-Path -LiteralPath $coreJar)) { throw '缺少 core jar：先执行 -Phase Build' }
    if (-not $downloadJar -or -not (Test-Path -LiteralPath $downloadJar)) { throw '缺少下载服务 jar：先执行 -Phase Build' }
    Initialize-Aria2Config
    Write-Info 'docker compose up --detach mysql redis nacos sfc-core sfc-download-service aria2'
    Invoke-Compose up --detach mysql redis nacos sfc-core sfc-download-service aria2 | Out-Host
    Set-StateValue 'stackRunning' $true
    Wait-StackReady
    Write-Ev 'compose-ps.json' (Invoke-Compose ps --format json)
    Add-Result 'P0-栈启动' 'PASS' 'nacos/mysql/redis/core/下载服务/aria2 就绪（SQLite 模式）'
}

function Invoke-PhaseConfigure {
    Write-Step 'Phase Configure：登录 + 统一配置 + 能力/基线'
    $script:CoreCtx = Connect-Core
    $script:CoreSession = $script:CoreCtx.session
    $uid = $script:CoreCtx.uid

    # AC1 阶段统一种子配置=0（每个 P2P 任务 FINISH 后立即清理，避免做种记录/文件影响后续用例；
    # 该配置下的清理行为同时作为 AC1-7「seed=0 完成即清理」与 AC2-4 的证据）
    Set-CoreConfig @{
        'download.aria2.enabled'           = 'true'
        'download.aria2.poll-interval'     = '1000'
        'download.aria2.stall-timeout'     = '0'   # 测试期关闭无进度超时，避免慢资源误判
        'download.aria2.torrent-max-size'  = '16'
        'download.aria2.seed-time-minutes' = '0'
    }
    Write-Info '等待配置快照生效（≤8 秒）…'
    Start-Sleep -Seconds 8
    $caps = Get-DownloadCapabilities -Uid $uid -Refresh $true
    Write-Ev 'capabilities.json' $caps
    $p2p = @($caps | Where-Object { $_.type -eq 'MAGNET' -or $_.type -eq 'TORRENT' })
    $available = @($p2p | Where-Object { $_.available -eq $true })
    $checks = @()
    $checks += New-Check 'MAGNET/TORRENT 能力可用' ($available.Count -gt 0) ("available=" + (($available | ForEach-Object { $_.type }) -join ','))
    if ($available.Count -eq 0) {
        $reasons = ($p2p | ForEach-Object { "$($_.type):$($_.reason)" }) -join ' | '
        Complete-Checks 'P0-配置' $checks
        Add-Result 'P0-配置原因' 'NOTRUN' $reasons
        return
    }
    Set-StateValue 'baseline' (Get-DownloadBaseline)
    Add-Result 'P0-配置' 'PASS' '能力可用；seed-time-minutes=0（AC1 阶段）；基线已记录'
}

function Invoke-PhaseSmoke {
    Write-Step 'Phase Smoke：BT 连通性冒烟'
    if ($SkipNetworkSmoke) { Add-Result 'P0-网络' 'SKIP' '-SkipNetworkSmoke'; return }
    $uid = [long](Get-StateValue 'adminUid')
    $downloadId = New-DownloadTask -Uid $uid -Type 'TORRENT' -Url $TorrentUrl
    $progressed = Wait-Until -Desc 'BT 下载出现进度' -TimeoutSec 240 -IntervalSec 5 -Test {
        $row = Get-DownloadTaskRow -DownloadId $downloadId
        return ($row -and ([long]$row.loaded -gt 0 -or [int]$row.status -eq 2))
    }
    $row = Get-DownloadTaskRow -DownloadId $downloadId
    Write-Ev 'smoke-task.json' $row
    if ($progressed) {
        Set-StateValue 'networkOk' $true
        Add-Result 'P0-网络' 'PASS' "BT 资源可下载（loaded=$($row.loaded)/size=$($row.size)）"
    }
    else {
        Set-StateValue 'networkOk' $false
        Add-Result 'P0-网络' 'NOTRUN' 'BT 资源 240 秒内无进度：后续 P2P 类 AC 记未执行，需更换资源/检查网络'
    }
    $null = Stop-DownloadTask -Uid $uid -DownloadId $downloadId
    Start-Sleep -Seconds 3
}

# ---------------- AC1-1 + AC1-2：优雅停机 detach → 重启接管 ----------------
function Invoke-PhaseResume {
    Write-Step 'Phase Resume：AC1-1 优雅停机不杀 aria2 + AC1-2 重启接管不重复下载'
    if ((Get-StateValue 'networkOk') -eq $false) { Add-Result 'AC1-1/1-2' 'NOTRUN' 'BT 网络不可用（Smoke 失败）'; return }
    $uid = Ensure-CoreSession
    $beforeGids = @(@(Get-Aria2Active) | ForEach-Object { $_.gid })
    # 限速 1MB/s：制造可控的「下载中」窗口，使优雅停机/接管可被观测（仅测试用）
    $downloadId = New-DownloadTask -Uid $uid -Type 'TORRENT' -Url $TorrentUrl -Options @{ aria2Options = '{"max-overall-download-limit":"1M"}' }

    # 进度口径以 aria2 为准（P2 实现只在校验点/收尾回写 download_task，轮询期间不回写 loaded/size）
    $started = Wait-Until -Desc '任务下载中且有进度' -TimeoutSec 180 -IntervalSec 2 -Test {
        $newGids = @(Get-Aria2NewActiveGids -BeforeGids $beforeGids)
        if ($newGids.Count -eq 0) { return $false }
        $st = Get-Aria2Status ([string]$newGids[0].gid)
        if (-not $st) { return $false }
        $done = [long]$st.completedLength
        $total = [long]$st.totalLength
        return ($done -gt 0 -and ($total -le 0 -or $done -lt $total))
    }
    $taskRow = Get-DownloadTaskRow -DownloadId $downloadId
    if (-not $started -or -not $taskRow -or -not $taskRow.task_id) {
        Add-Result 'AC1-1/1-2' 'NOTRUN' '任务未进入「下载中+有进度」状态，无法验证停机/接管（区分资源/网络/实现问题）'
        $null = Stop-DownloadTask -Uid $uid -DownloadId $downloadId
        return
    }
    $taskId = [long]$taskRow.task_id
    $gids = @(@(Get-Aria2NewActiveGids -BeforeGids $beforeGids) | ForEach-Object { $_.gid })
    $coreBefore = Get-CoreTaskRow -TaskId $taskId
    $cpBefore = Get-CheckpointRow -TaskId $taskId
    $cpJsonBefore = if ($cpBefore -and $cpBefore.checkpoint) { $cpBefore.checkpoint } else { $null }
    $dirsBefore = Get-TaskWorkDirs -DownloadId $downloadId
    $loadedAtStop = [long]0
    foreach ($gd in $gids) { $st = Get-Aria2Status $gd; if ($st -and $st.completedLength) { $loadedAtStop += [long]$st.completedLength } }
    Write-Ev 'ac1-before.json' ([ordered]@{
            downloadTask = $taskRow; coreTask = $coreBefore; gids = $gids
            checkpointLen = $(if ($cpBefore) { $cpBefore.checkpoint_len } else { $null })
            checkpoint = $cpJsonBefore; workDirs = @($dirsBefore | Select-Object FullName)
        })

    # 优雅停机
    $stopAt = (Get-Date).ToString('o')
    $shutdown = Stop-ServiceGracefully -ContainerName $script:ContainerDownload
    Start-Sleep -Seconds 3
    $coreLogs = Get-ContainerLogs $script:ContainerCore $stopAt
    Write-Ev 'core-log-after-stop.log' $coreLogs

    # AC1-1 判定
    $aria2AfterStop = @(Get-Aria2Active)
    $stillActive = @($gids | Where-Object { $gid = $_; $aria2AfterStop | Where-Object { $_.gid -eq $gid } })
    $checks1 = @()
    $checks1 += New-Check 'aria2 gid 仍 active/waiting（未被杀）' ($stillActive.Count -gt 0) ("active=" + (($stillActive | ForEach-Object { $_.gid }) -join ','))
    $requeued = Wait-Until -Desc 'core 任务回等待中(0)' -TimeoutSec 90 -IntervalSec 3 -Test {
        $r = Get-CoreTaskRow -TaskId $taskId; return ($r -and [int]$r.status -eq 0)
    }
    $coreAfterStop = Get-CoreTaskRow -TaskId $taskId
    $checks1 += New-Check 'async_task_record 回到等待中(0)' ([bool]($requeued -and [int]$coreAfterStop.status -eq 0)) ("status=$($coreAfterStop.status)")
    $checks1 += New-Check 'retry_count 未增加' ([string]$coreAfterStop.retry_count -eq [string]$coreBefore.retry_count) ("before=$($coreBefore.retry_count), after=$($coreAfterStop.retry_count)")
    $checks1 += New-Check 'claimed_* 已清空' ([string]::IsNullOrEmpty([string]$coreAfterStop.claimed_worker)) ''
    $cpAfterStop = Get-CheckpointRow -TaskId $taskId
    $checks1 += New-Check '检查点保留' ([bool]$cpAfterStop) ''
    $dirsAfterStop = Get-TaskWorkDirs -DownloadId $downloadId
    $checks1 += New-Check '任务目录保留' ($dirsAfterStop.Count -ge 1) ("dirs=$($dirsAfterStop.Count)")
    $checks1 += New-Check '下载服务日志出现「已请求任务[x]挂起」' ($shutdown.Log -match '已请求任务.*挂起|挂起') 'FINAL-CODE: 关键字已按 P1 实现核对'
    $checks1 += New-Check 'core 日志出现「已挂起并重新排队（不消耗重试次数）」' ($coreLogs -match '已挂起并重新排队|不消耗重试次数|按检查点重新排队') 'FINAL-CODE: 关键字已按 P1 实现核对'
    $checks1 += New-Check '停机期间未上报 FINISH' ([int]$coreAfterStop.status -ne 2) ''
    Write-Ev 'ac1-after-stop.json' ([ordered]@{ coreTask = $coreAfterStop; aria2Active = $aria2AfterStop; checkpoint = $cpAfterStop })

    # 尽快重启，观察接管后进度继续（AC1-2）
    Start-ServiceAndWait -ContainerName $script:ContainerDownload
    $resumeAt = (Get-Date).ToString('o')
    $reclaimed = Wait-Until -Desc '任务被重新认领(1)' -TimeoutSec 240 -IntervalSec 3 -Test {
        $r = Get-CoreTaskRow -TaskId $taskId; return ($r -and [int]$r.status -eq 1)
    }
    $cpDuring = $null
    $null = Wait-Until -Desc '接管后检查点更新' -TimeoutSec 120 -IntervalSec 2 -Test {
        $r = Get-CheckpointRow -TaskId $taskId
        if ($r -and $r.checkpoint) { $script:__cpResume = $r; return $true }
        return $false
    }
    if ($script:__cpResume) { $cpDuring = $script:__cpResume }
    Write-Ev 'ac1-resume-checkpoint.json' $cpDuring

    # 等待下载推进（重启后 loaded 增加），再等 FINISH
    $progressed = Wait-Until -Desc '重启后进度继续增长' -TimeoutSec 300 -IntervalSec 5 -Test {
        $row = Get-DownloadTaskRow -DownloadId $downloadId
        if ($row -and [int]$row.status -eq 2) { return $true }
        $cur = [long]0
        foreach ($gd in $gids) { $st = Get-Aria2Status $gd; if ($st -and $st.completedLength) { $cur += [long]$st.completedLength } }
        return ($cur -gt $loadedAtStop)
    }
    $finished = Wait-DownloadStatus -DownloadId $downloadId -Statuses @(2) -TimeoutSec $TaskTimeoutSec
    $taskAfter = Get-DownloadTaskRow -DownloadId $downloadId
    $coreAfter = Get-CoreTaskRow -TaskId $taskId
    $serviceLogs = Get-ContainerLogs $script:ContainerDownload $resumeAt
    Write-Ev 'ac1-resume-after.json' ([ordered]@{ downloadTask = $taskAfter; coreTask = $coreAfter; resumedCheckpoint = $cpDuring })
    Write-Ev 'download-service-after-restart.log' $serviceLogs

    $checks2 = @()
    $checks2 += New-Check '任务被重新认领' $reclaimed ''
    $checks2 += New-Check '日志出现「已接管既有 aria2 任务：gid=...」' ($serviceLogs -match '已接管.*aria2 任务.*gid=') 'FINAL-CODE: 关键字已按 P2 实现核对'
    $checks2 += New-Check '无新的 addUri/addTorrent 提交' (-not ($serviceLogs -match '已提交 aria2 下载任务|addUri|addTorrent')) 'FINAL-CODE: 关键字以最终实现为准'
    $checks2 += New-Check '重启后进度继续增长' ([bool]$progressed) ("stop时 loaded=$loadedAtStop")
    $checks2 += New-Check '最终 FINISH' ([bool]($finished -and [int]$taskAfter.status -eq 2)) ("status=$($taskAfter.status), msg=$($taskAfter.message)")
    $dirsAfter = Get-TaskWorkDirs -DownloadId $downloadId
    $checks2 += New-Check '工作目录只有原目录（未新建）' ($dirsAfter.Count -eq $dirsBefore.Count) ("before=$($dirsBefore.Count), after=$($dirsAfter.Count)")
    if ($cpJsonBefore -and $cpDuring -and $cpDuring.checkpoint) {
        try {
            $a = $cpJsonBefore | ConvertFrom-Json
            $b = $cpDuring.checkpoint | ConvertFrom-Json
            $checks2 += New-Check '检查点 resumeCount +1' ([int]$b.resumeCount -eq ([int]$a.resumeCount + 1)) ("before=$($a.resumeCount), during=$($b.resumeCount)")
            $checks2 += New-Check '检查点 localDir 不变' ([string]$a.localDir -eq [string]$b.localDir) ''
        }
        catch { $checks2 += New-Check '检查点 JSON 可解析' $false $_.Exception.Message }
    }
    else { $checks2 += New-Check '接管瞬间检查点可读' $false 'FINISH 后检查点已删除，需在接管时抓取' }

    # AC1-7 子项：seed=0 时 FINISH 后目录删除、aria2 结果清理、无做种记录
    $checksZero = @()
    Start-Sleep -Seconds 3
    $dirsFinal = Get-TaskWorkDirs -DownloadId $downloadId
    $checksZero += New-Check 'seed=0：完成后任务目录已删除' ($dirsFinal.Count -eq 0) ("残留 dirs=$($dirsFinal.Count)")
    $checksZero += New-Check 'seed=0：无 download_seed_task 记录' (-not [bool](Get-SeedTaskRow -DownloadId $downloadId)) ''
    $aria2Residual = @($gids | ForEach-Object { $g = $_; $s = Get-Aria2Status $g; if ($s -and @('active', 'waiting', 'paused') -contains [string]$s.status) { $s } })
    $checksZero += New-Check 'seed=0：aria2 无残留 active' ($aria2Residual.Count -eq 0) ''

    Complete-Checks 'AC1-1' $checks1 (Join-Path $script:EvidenceDir 'ac1-after-stop.json')
    Complete-Checks 'AC1-2' $checks2 (Join-Path $script:EvidenceDir 'ac1-resume-after.json')
    Complete-Checks 'AC1-7(seed=0清理)' $checksZero (Join-Path $script:EvidenceDir 'ac1-resume-after.json')
}

# ---------------- AC1-4：离线期间下载完成 ----------------
function Invoke-PhaseOfflineComplete {
    Write-Step 'Phase OfflineComplete：AC1-4 离线期间下载完成，重启后续接上报'
    if ((Get-StateValue 'networkOk') -eq $false) { Add-Result 'AC1-4' 'NOTRUN' 'BT 网络不可用'; return }
    $uid = Ensure-CoreSession
    $checks = @()
    $beforeGids = @(@(Get-Aria2Active) | ForEach-Object { $_.gid })
    $downloadId = New-DownloadTask -Uid $uid -Type 'TORRENT' -Url $TorrentUrl
    $started = Wait-Until -Desc '任务下载中' -TimeoutSec 300 -IntervalSec 3 -Test {
        $row = Get-DownloadTaskRow -DownloadId $downloadId
        return ($row -and [int]$row.status -eq 1 -and [long]$row.loaded -gt 0)
    }
    $taskRow = Get-DownloadTaskRow -DownloadId $downloadId
    if (-not $started -or -not $taskRow -or -not $taskRow.task_id) {
        Add-Result 'AC1-4' 'NOTRUN' '任务未进入下载中，无法验证离线完成场景'
        $null = Stop-DownloadTask -Uid $uid -DownloadId $downloadId
        return
    }
    $taskId = [long]$taskRow.task_id
    $gids = @(@(Get-Aria2NewActiveGids -BeforeGids $beforeGids) | ForEach-Object { $_.gid })
    $cp = Get-CheckpointRow -TaskId $taskId
    Write-Ev 'ac4-before.json' ([ordered]@{ task = $taskRow; gids = $gids; checkpointLen = $(if ($cp) { $cp.checkpoint_len } else { $null }) })

    $null = Stop-ServiceGracefully -ContainerName $script:ContainerDownload
    Write-Info '保持停机，等待 aria2 完成下载…'
    $completed = Wait-Aria2AllGidsDone -Gids $gids -TimeoutSec $TaskTimeoutSec
    $aria2Done = @($gids | ForEach-Object { Get-Aria2Status $_ } | Where-Object { $_ })
    Write-Ev 'ac4-aria2-completed.json' $aria2Done
    $checks += New-Check '停机期间 aria2 完成下载' $completed ("status=" + (($aria2Done | ForEach-Object { $_.status }) -join ','))
    $totalBytes = [long]0
    foreach ($s in $aria2Done) { if ($s.totalLength) { $totalBytes += [long]$s.totalLength } }

    Start-ServiceAndWait -ContainerName $script:ContainerDownload
    $resumeAt = (Get-Date).ToString('o')
    $finished = Wait-DownloadStatus -DownloadId $downloadId -Statuses @(2) -TimeoutSec $TaskTimeoutSec
    $taskAfter = Get-DownloadTaskRow -DownloadId $downloadId
    $coreAfter = Get-CoreTaskRow -TaskId $taskId
    $logs = Get-ContainerLogs $script:ContainerDownload $resumeAt
    Write-Ev 'ac4-after.json' ([ordered]@{ task = $taskAfter; coreTask = $coreAfter })
    Write-Ev 'ac4-resume.log' $logs
    $checks += New-Check '重启后直接 FINISH' ([bool]($finished -and [int]$taskAfter.status -eq 2)) ("status=$($taskAfter.status), msg=$($taskAfter.message)")
    $checks += New-Check '日志无重新提交 aria2' (-not ($logs -match '已提交 aria2 下载任务|addUri|addTorrent')) 'FINAL-CODE: 以最终日志为准'
    $checks += New-Check 'FINISH 后检查点已删除' (-not [bool](Get-CheckpointRow -TaskId $taskId)) ''
    $checks += New-Check '大小与 aria2 totalLength 一致' ([long]$taskAfter.size -eq $totalBytes) ("download=$($taskAfter.size), aria2=$totalBytes")
    $storeFiles = @(Find-StoreFile '*')
    $checks += New-Check '网盘存在落盘产物' ($storeFiles.Count -gt 0) ("store 文件数=$($storeFiles.Count)")
    Complete-Checks 'AC1-4' $checks (Join-Path $script:EvidenceDir 'ac4-after.json')
}

# ---------------- AC1-3：崩溃恢复（docker kill） ----------------
function Invoke-PhaseCrashRecovery {
    Write-Step 'Phase CrashRecovery：AC1-3 docker kill 后按检查点恢复'
    if ((Get-StateValue 'networkOk') -eq $false) { Add-Result 'AC1-3' 'NOTRUN' 'BT 网络不可用'; return }
    $uid = Ensure-CoreSession
    $checks = @()
    $beforeGids = @(@(Get-Aria2Active) | ForEach-Object { $_.gid })
    $downloadId = New-DownloadTask -Uid $uid -Type 'TORRENT' -Url $TorrentUrl
    $started = Wait-Until -Desc '任务下载中' -TimeoutSec 300 -IntervalSec 3 -Test {
        $row = Get-DownloadTaskRow -DownloadId $downloadId
        return ($row -and [int]$row.status -eq 1 -and [long]$row.loaded -gt 0)
    }
    $taskRow = Get-DownloadTaskRow -DownloadId $downloadId
    if (-not $started -or -not $taskRow -or -not $taskRow.task_id) {
        Add-Result 'AC1-3' 'NOTRUN' '任务未进入下载中，无法验证崩溃恢复'
        $null = Stop-DownloadTask -Uid $uid -DownloadId $downloadId
        return
    }
    $taskId = [long]$taskRow.task_id
    $coreBefore = Get-CoreTaskRow -TaskId $taskId
    $cpBefore = Get-CheckpointRow -TaskId $taskId
    $gids = @(@(Get-Aria2NewActiveGids -BeforeGids $beforeGids) | ForEach-Object { $_.gid })
    Write-Ev 'ac3-before.json' ([ordered]@{ task = $taskRow; coreTask = $coreBefore; gids = $gids })

    $killAt = (Get-Date).ToString('o')
    Write-Info "docker kill $($script:ContainerDownload)（SIGKILL，模拟 kill -9）"
    & docker kill $script:ContainerDownload | Out-Null
    Start-Sleep -Seconds 2
    Write-Info '等待 core 健康检查回收（TaskHealthChecker 周期约 30s）…'
    $reclaimed = Wait-Until -Desc 'core 回收任务为等待中' -TimeoutSec 180 -IntervalSec 5 -Test {
        $r = Get-CoreTaskRow -TaskId $taskId
        return ($r -and [int]$r.status -eq 0)
    }
    $coreAfterKill = Get-CoreTaskRow -TaskId $taskId
    $coreLogs = Get-ContainerLogs $script:ContainerCore $killAt
    Write-Ev 'ac3-core-requeue.json' $coreAfterKill
    Write-Ev 'ac3-core-reclaim.log' $coreLogs
    $checks += New-Check 'core 回收任务为等待中(0)' ([bool]($reclaimed -and [int]$coreAfterKill.status -eq 0)) ("status=$($coreAfterKill.status)")
    $checks += New-Check 'retry_count 未增加' ([string]$coreAfterKill.retry_count -eq [string]$coreBefore.retry_count) ("before=$($coreBefore.retry_count), after=$($coreAfterKill.retry_count)")
    $checks += New-Check 'core 日志按检查点重新排队' ($coreLogs -match '已按检查点重新排队|按检查点重新排队|不消耗重试次数') 'FINAL-CODE: 关键字已按 P1 实现核对'
    $checks += New-Check '检查点未被删除' ([bool](Get-CheckpointRow -TaskId $taskId)) ''

    Start-ServiceAndWait -ContainerName $script:ContainerDownload
    $resumeAt = (Get-Date).ToString('o')
    $reshaped = Wait-Until -Desc '重启后被重新认领' -TimeoutSec 240 -IntervalSec 3 -Test {
        $r = Get-CoreTaskRow -TaskId $taskId; return ($r -and [int]$r.status -eq 1)
    }
    $finished = Wait-DownloadStatus -DownloadId $downloadId -Statuses @(2) -TimeoutSec $TaskTimeoutSec
    $taskAfter = Get-DownloadTaskRow -DownloadId $downloadId
    $logs = Get-ContainerLogs $script:ContainerDownload $resumeAt
    Write-Ev 'ac3-after.json' ([ordered]@{ task = $taskAfter; coreTask = (Get-CoreTaskRow -TaskId $taskId) })
    Write-Ev 'ac3-resume.log' $logs
    $checks += New-Check '重启后被重新认领' $reshaped ''
    $checks += New-Check '按检查点接管（日志）' ($logs -match '已接管.*aria2 任务|接管 P2P 任务') 'FINAL-CODE: 以最终日志为准'
    $checks += New-Check '最终 FINISH' ([bool]($finished -and [int]$taskAfter.status -eq 2)) ("status=$($taskAfter.status), msg=$($taskAfter.message)")
    Complete-Checks 'AC1-3' $checks (Join-Path $script:EvidenceDir 'ac3-after.json')
}

# ---------------- AC1-5：用户取消清理 ----------------
function Invoke-PhaseCancel {
    Write-Step 'Phase Cancel：AC1-5 用户取消清理'
    if ((Get-StateValue 'networkOk') -eq $false) { Add-Result 'AC1-5' 'NOTRUN' 'BT 网络不可用'; return }
    $uid = Ensure-CoreSession
    $checks = @()
    $beforeGids = @(@(Get-Aria2Active) | ForEach-Object { $_.gid })
    $downloadId = New-DownloadTask -Uid $uid -Type 'TORRENT' -Url $TorrentUrl
    $started = Wait-Until -Desc '任务下载中' -TimeoutSec 300 -IntervalSec 3 -Test {
        $row = Get-DownloadTaskRow -DownloadId $downloadId
        return ($row -and [int]$row.status -eq 1)
    }
    $taskRow = Get-DownloadTaskRow -DownloadId $downloadId
    if (-not $started -or -not $taskRow) {
        Add-Result 'AC1-5' 'NOTRUN' '任务未进入下载中，无法验证取消'
        return
    }
    $taskId = [long]$taskRow.task_id
    $gids = @(@(Get-Aria2NewActiveGids -BeforeGids $beforeGids) | ForEach-Object { $_.gid })
    Write-Ev 'ac5-before.json' ([ordered]@{ task = $taskRow; gids = $gids })
    $resp = Stop-DownloadTask -Uid $uid -DownloadId $downloadId
    Write-Ev 'ac5-response.json' $resp
    $checks += New-Check '取消接口成功' ([int]$resp.code -eq 200) ("code=$($resp.code), msg=$($resp.msg)")
    $cancelled = Wait-DownloadStatus -DownloadId $downloadId -Statuses @(4) -TimeoutSec 120
    $rowAfter = Get-DownloadTaskRow -DownloadId $downloadId
    $checks += New-Check 'download_task.status=4（CANCEL）' ([bool]($cancelled -and [int]$rowAfter.status -eq 4)) ("status=$($rowAfter.status)")
    if ($taskId) { $null = Wait-CoreTaskStatus -TaskId $taskId -Statuses @(4, 3) -TimeoutSec 120 }
    Start-Sleep -Seconds 3
    $residual = @($gids | ForEach-Object { $g = $_; $s = Get-Aria2Status $g; if ($s -and @('active', 'waiting', 'paused') -contains [string]$s.status) { $s } })
    $checks += New-Check 'aria2 gid 已 forceRemove/不存在' ($residual.Count -eq 0) ("residual=" + (@($residual | ForEach-Object { $_.gid }) -join ','))
    $dirs = Get-TaskWorkDirs -DownloadId $downloadId
    $checks += New-Check '本地任务目录已删除' ($dirs.Count -eq 0) ("残留 dirs=$($dirs.Count)")
    $checks += New-Check '检查点已删除' (-not [bool](Get-CheckpointRow -TaskId $taskId)) ''
    Complete-Checks 'AC1-5' $checks (Join-Path $script:EvidenceDir 'ac5-response.json')
}

# ---------------- AC1-7：HTTP / Git / core 内嵌 Worker 回归 ----------------
function Invoke-PhaseRegression {
    Write-Step 'Phase Regression：AC1-7 HTTP / Git / core 内嵌 Worker'
    $uid = Ensure-CoreSession
    $checks = @()

    # 本地 HTTP 下载源（宿主机 python http.server；容器经 host.docker.internal 访问）
    $srcDir = Join-Path $env:TEMP 'sfc-p5\http-src'
    New-Item -ItemType Directory -Force -Path $srcDir | Out-Null
    $srcFile = Join-Path $srcDir 'p5-http-source.bin'
    if (-not (Test-Path -LiteralPath $srcFile)) {
        $bytes = New-Object byte[] (1024 * 1024)
        (New-Object Random 42).NextBytes($bytes)
        [System.IO.File]::WriteAllBytes($srcFile, $bytes)
    }
    $srcSha = Get-FileSha256 $srcFile
    $httpPort = 18081
    $httpJob = Start-Job -ScriptBlock {
        param($dir, $port)
        Set-Location $dir
        python -m http.server $port --bind 0.0.0.0
    } -ArgumentList $srcDir, $httpPort
    $httpOk = $false
    try {
        $null = Wait-Until -Desc '本地 HTTP 源就绪' -TimeoutSec 30 -IntervalSec 2 -Test { Test-HttpReady "http://127.0.0.1:$httpPort/p5-http-source.bin" }
        $httpId = New-DownloadTask -Uid $uid -Type 'HTTP' -Url "http://host.docker.internal:$httpPort/p5-http-source.bin" -SavePath $script:SavePath
        $httpOk = Wait-DownloadStatus -DownloadId $httpId -Statuses @(2) -TimeoutSec 300
        $httpRow = Get-DownloadTaskRow -DownloadId $httpId
        Write-Ev 'ac7-http.json' $httpRow
        $checks += New-Check 'HTTP 下载 FINISH' ([bool]($httpOk -and [int]$httpRow.status -eq 2)) ("status=$($httpRow.status), size=$($httpRow.size)")
        $stored = @(Find-StoreFile 'p5-http-source*')
        if ($stored.Count -gt 0) {
            $storedSha = Get-FileSha256 $stored[0].FullName
            $checks += New-Check 'HTTP 落盘文件 SHA256 与源一致' ($storedSha -eq $srcSha) $stored[0].FullName
        }
        else { $checks += New-Check '找到 HTTP 落盘文件' $false 'FINAL-CODE: store 目录布局以实际为准' }
    }
    catch { $checks += New-Check 'HTTP 下载' $false $_.Exception.Message }
    finally { Stop-Job $httpJob -ErrorAction SilentlyContinue; Remove-Job $httpJob -Force -ErrorAction SilentlyContinue }

    # Git 下载
    try {
        $gitId = New-DownloadTask -Uid $uid -Type 'GIT' -Url 'https://github.com/octocat/Hello-World.git' -SavePath $script:SavePath
        $gitDone = Wait-Until -Desc 'Git 任务终态' -TimeoutSec 300 -IntervalSec 5 -Test {
            $r = Get-DownloadTaskRow -DownloadId $gitId; return ($r -and @(2, 3) -contains [int]$r.status)
        }
        $gitRow = Get-DownloadTaskRow -DownloadId $gitId
        Write-Ev 'ac7-git.json' $gitRow
        $checks += New-Check 'Git 克隆 FINISH' ([bool]($gitDone -and [int]$gitRow.status -eq 2)) ("status=$($gitRow.status), msg=$($gitRow.message)")
    }
    catch { $checks += New-Check 'Git 克隆' $false $_.Exception.Message }

    # core 内嵌 Worker：asyncCopy 触发 file-copy 任务（CORE 角色执行）
    try {
        $srcName = $null
        $files = @(Find-StoreFile 'p5-http-source*')
        if ($files.Count -gt 0) { $srcName = $files[0].Name }
        if ($srcName) {
            $copyBody = @{
                sourceUid = $uid; sourcePath = $script:SavePath; files = @($srcName)
                targetUid = $uid; targetPath = '/'; isOverwrite = $true
            }
            $copyResp = Invoke-CoreApi -Method Post -Path "/api/file/$uid/asyncCopy" -Body $copyBody -Session $script:CoreSession
            Write-Ev 'ac7-asynccopy-response.json' $copyResp
            if ([int]$copyResp.code -eq 200 -and $copyResp.data -and $copyResp.data.id) {
                $copyTaskId = [long]$copyResp.data.id
                $copyDone = Wait-CoreTaskStatus -TaskId $copyTaskId -Statuses @(2, 3, 4) -TimeoutSec 300
                $copyRow = Get-CoreTaskRow -TaskId $copyTaskId
                Write-Ev 'ac7-asynccopy-task.json' $copyRow
                $checks += New-Check 'core 内嵌 Worker 任务 FINISH' ([bool]($copyDone -and [int]$copyRow.status -eq 2)) ("status=$($copyRow.status)")
                $checks += New-Check '任务由 CORE 角色执行' ([string]$copyRow.claimed_role -eq 'CORE') ("claimed_role=$($copyRow.claimed_role)")
            }
            else { $checks += New-Check '提交 asyncCopy 任务' $false ("code=$($copyResp.code), msg=$($copyResp.msg)") }
        }
        else { $checks += New-Check '存在可复制的网盘文件' $false 'HTTP 未落盘则无法复制' }
    }
    catch { $checks += New-Check 'core 内嵌 Worker 回归' $false $_.Exception.Message }

    Complete-Checks 'AC1-7' $checks (Join-Path $script:EvidenceDir 'ac7-http.json')
}

# ---------------- AC2-2 + AC2-3：到期清理 / 做种期间重启 ----------------
function Invoke-PhaseSeedExpire {
    Write-Step 'Phase SeedExpire：AC2-2 到期清理 + AC2-3 做种期间重启'
    if ((Get-StateValue 'networkOk') -eq $false) { Add-Result 'AC2-2/2-3' 'NOTRUN' 'BT 网络不可用'; return }
    $uid = Ensure-CoreSession
    Set-CoreConfig @{ 'download.aria2.seed-time-minutes' = [string]$SmallSeedMinutes }
    Start-Sleep -Seconds 8
    $checks = @()
    $downloadId = New-DownloadTask -Uid $uid -Type 'TORRENT' -Url $TorrentUrl
    $fin = Wait-DownloadStatus -DownloadId $downloadId -Statuses @(2) -TimeoutSec $TaskTimeoutSec
    $task = Get-DownloadTaskRow -DownloadId $downloadId
    $seed = Get-SeedTaskRow -DownloadId $downloadId
    Write-Ev 'ac2-seed-registered.json' $seed
    $checks += New-Check '任务 FINISH 且登记做种' ([bool]($fin -and $task -and [int]$task.status -eq 2 -and $seed)) ''
    if (-not $seed) { Complete-Checks 'AC2-2/2-3' $checks; return }
    $checks += New-Check '做种记录 status=0' ([int]$seed.status -eq 0) ("status=$($seed.status)")
    $seedEndBefore = [string]$seed.seed_end_at

    # AC2-3：做种中重启下载服务
    $null = Stop-ServiceGracefully -ContainerName $script:ContainerDownload
    Start-ServiceAndWait -ContainerName $script:ContainerDownload
    Start-Sleep -Seconds 5
    $seedAfterRestart = Get-SeedTaskRow -DownloadId $downloadId
    Write-Ev 'ac2-seed-after-restart.json' $seedAfterRestart
    $checks += New-Check '重启后 seed_end_at 不变（按落库时间）' ([string]$seedAfterRestart.seed_end_at -eq $seedEndBefore) ("before=$seedEndBefore, after=$($seedAfterRestart.seed_end_at)")

    # AC2-2：等到期 + 清理周期
    $waitSec = [Math]::Max(40, [int]$SmallSeedMinutes * 60 + 120)
    Write-Info "等待做种到期 + 清理周期（最多 ${waitSec}s）…"
    $cleaned = Wait-Until -Desc '做种记录被清理(status=2)' -TimeoutSec $waitSec -IntervalSec 10 -Test {
        $r = Get-SeedTaskRow -DownloadId $downloadId; return ($r -and [int]$r.status -eq 2)
    }
    $seedAfter = Get-SeedTaskRow -DownloadId $downloadId
    $aria2After = if ($seedAfter.content_gid) { Get-Aria2Status ([string]$seedAfter.content_gid) } else { $null }
    $localHost = Convert-SharedToHostPath ([string]$seedAfter.local_dir)
    $dirsLeft = @($localHost | Where-Object { $_ -and (Test-Path -LiteralPath $_) })
    $logs = Get-ContainerLogs $script:ContainerDownload ''
    Write-Ev 'ac2-seed-cleaned.json' $seedAfter
    Write-Ev 'ac2-cleanup.log' $logs
    $checks += New-Check 'SeedTaskKeeper 清理完成(status=2)' ([bool]($cleaned -and [int]$seedAfter.status -eq 2)) ("status=$($seedAfter.status), msg=$($seedAfter.message)")
    $checks += New-Check 'finish_at 非空' ([bool]$seedAfter.finish_at) ''
    $aria2StatusText = if ($aria2After) { [string]$aria2After.status } else { 'null(removed)' }
    $checks += New-Check 'aria2 做种任务已移除' ([bool](-not $aria2After -or [string]$aria2After.status -eq 'removed')) ("status=$aria2StatusText")
    $checks += New-Check '本地做种目录已删除' ($dirsLeft.Count -eq 0) "hostPath=$localHost"
    $checks += New-Check '清理日志关键字' ($logs -match '做种记录已清理|到期的做种记录') 'FINAL-CODE: 关键字已按 P3 实现核对'

    # 幂等：再等一轮，无异常、记录不变
    Start-Sleep -Seconds 25
    $seedAgain = Get-SeedTaskRow -DownloadId $downloadId
    $logs2 = Get-ContainerLogs $script:ContainerDownload ''
    $checks += New-Check '重复触发无副作用（记录不变）' ([int]$seedAgain.status -eq 2 -and [string]$seedAgain.finish_at -eq [string]$seedAfter.finish_at) ''
    $checks += New-Check '第二轮日志无异常堆栈' (-not ($logs2 -match '做种记录清理失败|清理本轮扫描异常')) 'FINAL-CODE: 以实际日志为准'
    Complete-Checks 'AC2-2/2-3' $checks (Join-Path $script:EvidenceDir 'ac2-seed-cleaned.json')
}

# ---------------- AC2-4：seed-time-minutes=0 旧行为 ----------------
function Invoke-PhaseSeedZero {
    Write-Step 'Phase SeedZero：AC2-4 seed-time-minutes=0 旧行为（录制 seed=0 时完成即清理）'
    if ((Get-StateValue 'networkOk') -eq $false) { Add-Result 'AC2-4' 'NOTRUN' 'BT 网络不可用'; return }
    $uid = Ensure-CoreSession
    Set-CoreConfig @{ 'download.aria2.seed-time-minutes' = '0' }
    Start-Sleep -Seconds 8
    $checks = @()
    $beforeGids = @(@(Get-Aria2Active) | ForEach-Object { $_.gid })
    $downloadId = New-DownloadTask -Uid $uid -Type 'TORRENT' -Url $TorrentUrl
    $fin = Wait-DownloadStatus -DownloadId $downloadId -Statuses @(2) -TimeoutSec $TaskTimeoutSec
    $task = Get-DownloadTaskRow -DownloadId $downloadId
    Write-Ev 'ac24-task.json' $task
    $checks += New-Check '任务 FINISH' ([bool]($fin -and [int]$task.status -eq 2)) ("status=$($task.status)")
    Start-Sleep -Seconds 5
    $seed = Get-SeedTaskRow -DownloadId $downloadId
    $checks += New-Check '不产生 download_seed_task 记录' (-not [bool]$seed) ''
    $dirs = Get-TaskWorkDirs -DownloadId $downloadId
    $checks += New-Check '本地目录已删除' ($dirs.Count -eq 0) ("残留 dirs=$($dirs.Count)")
    $newGids = @(@(Get-Aria2NewActiveGids -BeforeGids $beforeGids) | ForEach-Object { $_.gid })
    $residual = @($newGids | ForEach-Object { $g = $_; $s = Get-Aria2Status $g; if ($s -and @('active', 'waiting', 'paused') -contains [string]$s.status) { $s } })
    $checks += New-Check 'aria2 无残留 active 下载' ($residual.Count -eq 0) ''
    Complete-Checks 'AC2-4' $checks (Join-Path $script:EvidenceDir 'ac24-task.json')
}

# ---------------- AC1-6：MySQL 跨实例接管 ----------------
function Invoke-PhaseCrossInstance {
    Write-Step 'Phase CrossInstance：AC1-6 MySQL 数据源 + 两实例跨实例接管'
    if ((Get-StateValue 'networkOk') -eq $false) { Add-Result 'AC1-6' 'NOTRUN' 'BT 网络不可用'; return }
    $uid = Ensure-CoreSession
    $checks = @()
    $restoreNeeded = $false
    try {
        Switch-DownloadDbMode 'mysql'
        $restoreNeeded = $true
        $instancesOk = Wait-DownloadWorkers -Count 2 -TimeoutSec 240
        $workers = @(Get-WorkerInfos | Where-Object { $_.serviceId -eq 'sfc-download-service' })
        Write-Ev 'ac16-workers.json' $workers
        $checks += New-Check '两实例均注册可见' ([bool]($instancesOk -and $workers.Count -ge 2)) ("count=$($workers.Count), workerIds=" + (($workers | ForEach-Object { $_.workerId }) -join ','))

        $beforeGids = @(@(Get-Aria2Active) | ForEach-Object { $_.gid })
        $downloadId = New-DownloadTask -Uid $uid -Type 'TORRENT' -Url $TorrentUrl
        $started = Wait-Until -Desc '任务被某实例认领并开始下载' -TimeoutSec 300 -IntervalSec 3 -Test {
            $row = Get-DownloadTaskRow -DownloadId $downloadId
            return ($row -and [int]$row.status -eq 1 -and [long]$row.loaded -gt 0)
        }
        $taskRow = Get-DownloadTaskRow -DownloadId $downloadId
        if (-not $started -or -not $taskRow -or -not $taskRow.task_id) {
            $checks += New-Check '任务进入下载中' $false '任务未进入下载中，无法验证跨实例接管'
            Complete-Checks 'AC1-6' $checks
            return
        }
        $taskId = [long]$taskRow.task_id
        $coreBefore = Get-CoreTaskRow -TaskId $taskId
        $claimedWorker = [string]$coreBefore.claimed_worker
        $claimedContainer = Get-WorkerContainerName $claimedWorker
        $otherContainer = if ($claimedContainer -eq $script:ContainerDownload) { $script:ContainerDownload2 } else { $script:ContainerDownload }
        $gids = @(@(Get-Aria2NewActiveGids -BeforeGids $beforeGids) | ForEach-Object { $_.gid })
        $cpBefore = Get-CheckpointRow -TaskId $taskId
        $cpJsonBefore = if ($cpBefore -and $cpBefore.checkpoint) { $cpBefore.checkpoint } else { $null }
        Write-Ev 'ac16-before.json' ([ordered]@{ task = $taskRow; coreTask = $coreBefore; claimedContainer = $claimedContainer; gids = $gids; checkpoint = $cpJsonBefore })
        $checks += New-Check '任务被实例认领（可映射到容器）' ([bool]($claimedWorker -and $claimedContainer)) ("claimed_worker=$claimedWorker, container=$claimedContainer")

        $null = Stop-ServiceGracefully -ContainerName $claimedContainer
        $requeued = Wait-Until -Desc '任务回到等待中' -TimeoutSec 120 -IntervalSec 3 -Test {
            $r = Get-CoreTaskRow -TaskId $taskId; return ($r -and [int]$r.status -eq 0)
        }
        $checks += New-Check '停实例 A 后任务回等待中(0)' ([bool]$requeued) ''

        $takeover = Wait-Until -Desc '实例 B 认领接管' -TimeoutSec 240 -IntervalSec 3 -Test {
            $r = Get-CoreTaskRow -TaskId $taskId
            if (-not $r) { return $false }
            return ([int]$r.status -eq 1 -and [string]$r.claimed_worker -ne $claimedWorker)
        }
        $coreTakeover = Get-CoreTaskRow -TaskId $taskId
        $takeoverLogs = Get-ContainerLogs $otherContainer ''
        Write-Ev 'ac16-takeover.json' $coreTakeover
        Write-Ev "ac16-$otherContainer.log" $takeoverLogs
        $checks += New-Check '实例 B 认领（claimed_worker 变化）' ([bool]($takeover -and [string]$coreTakeover.claimed_worker -ne $claimedWorker)) ("new=$($coreTakeover.claimed_worker)")
        $checks += New-Check 'B 日志出现「已接管既有 aria2 任务」' ($takeoverLogs -match '已接管.*aria2 任务|接管 P2P 任务') 'FINAL-CODE: 以最终日志为准'
        $cpDuring = Get-CheckpointRow -TaskId $taskId
        if ($cpJsonBefore -and $cpDuring -and $cpDuring.checkpoint) {
            try {
                $a = $cpJsonBefore | ConvertFrom-Json
                $b = $cpDuring.checkpoint | ConvertFrom-Json
                $checks += New-Check 'gid 与停机前一致（未重新提交）' (([string]::Join(',', @($b.gids))) -eq ([string]::Join(',', @($a.gids)))) ''
                $checks += New-Check 'localDir 未变化' ([string]$a.localDir -eq [string]$b.localDir) ''
            }
            catch { $checks += New-Check '检查点 JSON 可解析' $false $_.Exception.Message }
        }
        $finished = Wait-DownloadStatus -DownloadId $downloadId -Statuses @(2) -TimeoutSec $TaskTimeoutSec
        $taskAfter = Get-DownloadTaskRow -DownloadId $downloadId
        Write-Ev 'ac16-after.json' $taskAfter
        $checks += New-Check '跨实例接管后 FINISH' ([bool]($finished -and [int]$taskAfter.status -eq 2)) ("status=$($taskAfter.status), msg=$($taskAfter.message)")
    }
    catch {
        $checks += New-Check 'AC1-6 执行环境' $false $_.Exception.Message
    }
    finally {
        if ($restoreNeeded) {
            try { Switch-DownloadDbMode 'sqlite' } catch { Write-Warn2 "切回 SQLite 失败（后续 Seed 阶段会受影响）: $($_.Exception.Message)" }
        }
    }
    Complete-Checks 'AC1-6' $checks (Join-Path $script:EvidenceDir 'ac16-workers.json')
}

# ---------------- AC2-1 / AC2-5 / AC2-6：默认 10080 保留做种 + 列表 + 复用 ----------------
function Invoke-PhaseSeed {
    Write-Step 'Phase Seed：AC2-1 默认 10080 保留做种 + AC2-5 列表字段 + AC2-6 同 infohash 复用'
    if ((Get-StateValue 'networkOk') -eq $false) { Add-Result 'AC2-1/2-5/2-6' 'NOTRUN' 'BT 网络不可用'; return }
    $uid = Ensure-CoreSession
    Set-CoreConfig @{ 'download.aria2.seed-time-minutes' = [string]$script:DefaultSeedMinutes }
    Start-Sleep -Seconds 8
    $checks1 = @()
    $downloadId = New-DownloadTask -Uid $uid -Type 'TORRENT' -Url $TorrentUrl
    $fin = Wait-DownloadStatus -DownloadId $downloadId -Statuses @(2) -TimeoutSec $TaskTimeoutSec
    $task = Get-DownloadTaskRow -DownloadId $downloadId
    $seed = Get-SeedTaskRow -DownloadId $downloadId
    Write-Ev 'ac21-seed.json' $seed
    $checks1 += New-Check '任务 FINISH（status=2）' ([bool]($fin -and $task -and [int]$task.status -eq 2)) ("status=$($task.status)")
    $checks1 += New-Check '已登记 download_seed_task 且 status=0' ([bool]($seed -and [int]$seed.status -eq 0)) ''
    if ($seed) {
        $checks1 += New-Check 'content_gid 非空' ([bool]$seed.content_gid) ("gid=$($seed.content_gid)")
        $checks1 += New-Check 'infohash / torrent_sha256 至少其一非空' ([bool]($seed.infohash -or $seed.torrent_sha256)) ''
        $aria = if ($seed.content_gid) { Get-Aria2Status ([string]$seed.content_gid) } else { $null }
        Write-Ev 'ac21-aria2.json' $aria
        $checks1 += New-Check 'aria2 仍 active 且 seeder=true' ([bool]($aria -and @('active', 'waiting') -contains [string]$aria.status -and [string]$aria.seeder -eq 'true')) ("status=$($aria.status), seeder=$($aria.seeder)")
        $localHost = Convert-SharedToHostPath ([string]$seed.local_dir)
        $checks1 += New-Check '本地任务目录保留' ([bool]($localHost -and (Test-Path -LiteralPath $localHost))) "hostPath=$localHost"
        try {
            $startAt = [datetime]$seed.seed_start_at
            $endAt = [datetime]$seed.seed_end_at
            $deltaMinutes = [math]::Round(($endAt - $startAt).TotalMinutes, 1)
            $checks1 += New-Check 'seed_end_at - seed_start_at ≈ 10080 分钟' ([math]::Abs($deltaMinutes - $script:DefaultSeedMinutes) -le 2) "delta=$deltaMinutes 分钟"
        }
        catch { $checks1 += New-Check 'seed 时间字段可解析' $false $_.Exception.Message }
    }
    Complete-Checks 'AC2-1' $checks1 (Join-Path $script:EvidenceDir 'ac21-seed.json')

    # AC2-5：列表字段（做种中任务 true；非 P2P 任务 false/null）
    $checks5 = @()
    try {
        $list = Get-DownloadTaskList -Uid $uid
        $item = @($list.content | Where-Object { [string]$_.id -eq [string]$downloadId }) | Select-Object -First 1
        $httpTask = @($list.content | Where-Object { $_.type -eq 'HTTP' }) | Select-Object -First 1
        Write-Ev 'ac25-list.json' ([ordered]@{ seedingItem = $item; httpItem = $httpTask })
        $checks5 += New-Check '做种中任务 seeding=true' ([bool]($item -and $item.seeding -eq $true)) ''
        $checks5 += New-Check '做种中任务 seedEndAt 非空' ([bool]($item -and $item.seedEndAt)) ''
        if ($httpTask) {
            $checks5 += New-Check '非 P2P 任务 seeding=false/null' ([bool]($httpTask.seeding -ne $true)) ''
            $checks5 += New-Check '非 P2P 任务 seedEndAt=null' ([bool](-not $httpTask.seedEndAt)) ''
        }
        else { $checks5 += New-Check '列表中存在非 P2P 任务用于对照' $false '前序 HTTP 任务可能已随切库丢失，需补充' }
    }
    catch { $checks5 += New-Check '列表查询' $false $_.Exception.Message }
    Complete-Checks 'AC2-5' $checks5 (Join-Path $script:EvidenceDir 'ac25-list.json')

    # AC2-6：同 infohash 复用
    $checks6 = @()
    try {
        $beforeGids = @(@(Get-Aria2Active) | ForEach-Object { $_.gid })
        $id2 = New-DownloadTask -Uid $uid -Type 'TORRENT' -Url $TorrentUrl
        $fin2 = Wait-DownloadStatus -DownloadId $id2 -Statuses @(2) -TimeoutSec $TaskTimeoutSec
        $task2 = Get-DownloadTaskRow -DownloadId $id2
        $seed1After = Get-SeedTaskRow -DownloadId $downloadId
        $logs = Get-ContainerLogs $script:ContainerDownload ''
        $newGids = @(@(Get-Aria2NewActiveGids -BeforeGids $beforeGids) | ForEach-Object { $_.gid })
        Write-Ev 'ac26-reuse.json' ([ordered]@{ task2 = $task2; seed1After = $seed1After; newGids = $newGids })
        $checks6 += New-Check '复用任务 FINISH' ([bool]($fin2 -and [int]$task2.status -eq 2)) ("status=$($task2.status)")
        $checks6 += New-Check '未新建 aria2 下载（无新 gid）' ($newGids.Count -eq 0) ("newGids=" + (@($newGids | ForEach-Object { $_.gid }) -join ','))
        $checks6 += New-Check '日志出现复用命中' ($logs -match '命中可复用的做种保留记录|复用做种中的文件，无需重新下载') 'FINAL-CODE: 关键字已按 P2/P3 实现核对'
        $checks6 += New-Check '无 InfoHash is already registered 失败' (-not ($logs -match 'InfoHash.*already registered.*失败')) ''
        $checks6 += New-Check '原做种记录 seed_end_at 不变' ([string]$seed1After.seed_end_at -eq [string]$seed.seed_end_at) ''
    }
    catch { $checks6 += New-Check '同 infohash 复用' $false $_.Exception.Message }
    Complete-Checks 'AC2-6' $checks6 (Join-Path $script:EvidenceDir 'ac26-reuse.json')
}

function Invoke-PhaseTeardown {
    Write-Step 'Phase Teardown：停止并移除本轮创建的容器（保留 nacos 与镜像）'
    try { $null = Invoke-Aria2Rpc -Method 'aria2.purgeDownloadResult' -IgnoreError } catch { }
    $targets = @($script:ContainerCore, $script:ContainerDownload, $script:ContainerDownload2, $script:ContainerAria2, $script:ContainerMysql, $script:ContainerRedis)
    foreach ($c in $targets) {
        & docker inspect $c 2>$null | Out-Null
        $exists = ($LASTEXITCODE -eq 0)
        if ($exists) {
            & docker stop $c 2>$null | Out-Null
            & docker rm -f $c 2>$null | Out-Null
            Write-Info "已移除容器 $c"
        }
    }
    Add-Result 'P9-清理' 'PASS' '本轮测试容器已停止并移除；nacos 与既有镜像保留'
}

# ============================================================================
# 主流程
# ============================================================================
function Assert-PhaseAllowed {
    $needConfirm = @($Phase | Where-Object { $_ -notin @('Preflight', 'Build', 'StackUp', 'Teardown') })
    if ($needConfirm.Count -gt 0 -and -not $ConfirmRun) {
        throw "阶段 [$($needConfirm -join ',')] 会启动容器并创建真实下载任务，必须显式加 -ConfirmRun 才执行。"
    }
}

function Invoke-AllPhases {
    Invoke-PhasePreflight
    Invoke-PhaseBuild
    Invoke-PhaseStackUp
    Invoke-PhaseConfigure
    Invoke-PhaseSmoke
    Invoke-PhaseResume
    Invoke-PhaseOfflineComplete
    Invoke-PhaseCrashRecovery
    Invoke-PhaseCancel
    Invoke-PhaseRegression
    Invoke-PhaseSeedExpire
    Invoke-PhaseSeedZero
    Invoke-PhaseCrossInstance
    Invoke-PhaseSeed
    if (-not $KeepStack) { Invoke-PhaseTeardown }
}

function Main {
    if ($ResetRun) {
        if (Test-Path -LiteralPath $script:QaData) { Remove-Item -LiteralPath $script:QaData -Recurse -Force }
        Write-Ok "已重置 QA_DATA: $($script:QaData)"
    }
    New-Item -ItemType Directory -Force -Path $script:QaData | Out-Null
    New-Item -ItemType Directory -Force -Path $script:EvidenceDir | Out-Null
    Load-State
    Assert-PhaseAllowed
    if ($Phase -contains 'All') { Invoke-AllPhases }
    else {
        foreach ($p in $Phase) {
            switch ($p) {
                'Preflight' { Invoke-PhasePreflight }
                'Build' { Invoke-PhaseBuild }
                'StackUp' { Invoke-PhaseStackUp }
                'Configure' { Invoke-PhaseConfigure }
                'Smoke' { Invoke-PhaseSmoke }
                'Resume' { Invoke-PhaseResume }
                'OfflineComplete' { Invoke-PhaseOfflineComplete }
                'CrashRecovery' { Invoke-PhaseCrashRecovery }
                'Cancel' { Invoke-PhaseCancel }
                'Regression' { Invoke-PhaseRegression }
                'SeedExpire' { Invoke-PhaseSeedExpire }
                'SeedZero' { Invoke-PhaseSeedZero }
                'CrossInstance' { Invoke-PhaseCrossInstance }
                'Seed' { Invoke-PhaseSeed }
                'Teardown' { Invoke-PhaseTeardown }
            }
        }
    }

    Write-Step '执行结果汇总'
    $script:Results | Format-Table ac, status, detail -AutoSize -Wrap | Out-String | Write-Host
    Save-PhaseEvidence 'final'
    Save-Summary
    Write-Host ''
    Write-Host "证据目录: $($script:EvidenceDir)" -ForegroundColor Cyan
    Write-Host "QA 数据目录: $($script:QaData)" -ForegroundColor Cyan
    $failed = @($script:Results | Where-Object { $_.status -eq 'FAIL' })
    $notrun = @($script:Results | Where-Object { $_.status -eq 'NOTRUN' })
    $passed = @($script:Results | Where-Object { $_.status -eq 'PASS' })
    Write-Host ("结论: PASS={0}, FAIL={1}, NOTRUN={2}" -f $passed.Count, $failed.Count, $notrun.Count) -ForegroundColor Cyan
    return $failed.Count
}

$exitCode = 0
if (-not $env:SFC_P5_SKIP_MAIN) {
    try { $exitCode = Main }
    catch {
        Write-Host ''
        Write-Host "脚本中止: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host $_.ScriptStackTrace -ForegroundColor DarkGray
        try {
            Add-Result 'SCRIPT' 'FAIL' $_.Exception.Message
            Save-PhaseEvidence 'final-on-error'
            Save-Summary
        }
        catch { }
        $exitCode = 2
    }
}
if (-not $env:SFC_P5_SKIP_MAIN) { exit $exitCode }














