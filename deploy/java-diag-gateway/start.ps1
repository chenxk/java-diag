# Java Diag Gateway（java-diag-gateway）Windows 启停脚本（jar / properties 与脚本同目录）
#
# 用法（推荐 start.cmd）:
#   start.cmd start
#   start.cmd stop
#   start.cmd restart
#   start.cmd status
#   start.cmd foreground
#   或直接: .\start.ps1 <命令>
#
param(
    [Parameter(Position = 0)]
    [ValidateSet("start", "stop", "restart", "status", "foreground", "fg", "help", "")]
    [string]$Command = ""
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

$AppName = "Java Diag Gateway"
$Jar = Join-Path $ScriptDir "java-diag-gateway.jar"
$Config = Join-Path $ScriptDir "java-diag-gateway.properties"
$PidFile = Join-Path $ScriptDir "gateway.pid"
$LogDir = Join-Path $ScriptDir "logs"
$LogFile = Join-Path $LogDir "gateway.out"

function Get-JavaExe {
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path $candidate) { return $candidate }
    }
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw "未找到 Java（请安装 JDK 17+ 或设置 JAVA_HOME）"
}

function Get-JavaMajor([string]$JavaExe) {
    # java -version 写到 stderr；$ErrorActionPreference=Stop 时会被当成 terminating error
    $prevEap = $ErrorActionPreference
    try {
        $ErrorActionPreference = "SilentlyContinue"
        $raw = cmd /c "`"$JavaExe`" -version 2>&1"
        $out = ($raw | Out-String).Trim()
        if (-not $out) {
            $lines = @(& $JavaExe -version 2>&1)
            $out = ($lines | ForEach-Object {
                    if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.ToString() } else { "$_" }
                }) -join "`n"
        }
    } finally {
        $ErrorActionPreference = $prevEap
    }

    $ver = $null
    if ($out -match 'version\s+"([^"]+)"') {
        $ver = $Matches[1]
    } elseif ($out -match '(?m)^(?:openjdk|java)\s+(\d+(?:\.\d+)*)\b') {
        # 无引号形态: openjdk 17 2024-07-16 LTS
        $ver = $Matches[1]
    }
    if ($ver -match '^1\.(\d+)') { return [int]$Matches[1] }
    if ($ver -match '^(\d+)') { return [int]$Matches[1] }
    throw "无法解析 Java 版本: $out"
}

function Show-Usage {
    @"
用法: start.cmd {start|stop|restart|status|foreground}  （推荐）
      或 .\start.ps1 <同上命令>

  start       后台启动（PID → gateway.pid，日志 → logs\gateway.out）
  stop        停止后台进程
  restart     先 stop 再 start
  status      查看运行状态
  foreground  前台运行（Ctrl+C 停止）
"@ | Write-Host
}

function Read-Pid {
    if (-not (Test-Path $PidFile)) { return $null }
    $raw = (Get-Content $PidFile -Raw -ErrorAction SilentlyContinue)
    if (-not $raw) { return $null }
    $t = $raw.Trim()
    if ($t -match '^\d+$') { return [int]$t }
    return $null
}

function Test-Running([Nullable[int]]$ProcessId) {
    if (-not $ProcessId) { return $false }
    try {
        $p = Get-Process -Id $ProcessId -ErrorAction Stop
        return $null -ne $p
    } catch {
        return $false
    }
}

function Assert-JavaAndFiles {
    $java = Get-JavaExe
    $major = Get-JavaMajor $java
    if ($major -lt 17) {
        throw "需要 Java 17 或更高版本，当前 major=$major"
    }
    if (-not (Test-Path $Jar)) { throw "缺少 jar: $Jar" }
    if (-not (Test-Path $Config)) { throw "缺少配置: $Config" }
    return $java
}

function Invoke-Status {
    $pidVal = Read-Pid
    if (Test-Running $pidVal) {
        Write-Host "$AppName 正在运行 pid=$pidVal"
        Write-Host "  配置: $Config"
        Write-Host "  日志: $LogFile"
        exit 0
    }
    if ($pidVal) {
        Write-Host "$AppName 未运行（PID 文件残留 pid=$pidVal，可执行 stop 清理）"
        exit 3
    }
    Write-Host "$AppName 未运行"
    exit 3
}

function Invoke-Start {
    $java = Assert-JavaAndFiles
    $pidVal = Read-Pid
    if (Test-Running $pidVal) {
        throw "$AppName 已在运行 (pid=$pidVal)。可先: .\start.ps1 status | .\start.ps1 stop"
    }
    if ($pidVal) { Remove-Item $PidFile -Force -ErrorAction SilentlyContinue }

    if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir | Out-Null }
    $stamp = Get-Date -Format "yyyy-MM-ddTHH:mm:ss"
    Add-Content -Path $LogFile -Value "==== $stamp start java-diag-gateway ===="

    # PowerShell 不允许 stdout/stderr 重定向到同一文件，stderr 写入 .err
    $errFile = Join-Path $LogDir "gateway.err"
    $proc = Start-Process -FilePath $java `
        -ArgumentList @("-jar", $Jar, "--config", $Config) `
        -WorkingDirectory $ScriptDir `
        -RedirectStandardOutput $LogFile `
        -RedirectStandardError $errFile `
        -WindowStyle Hidden `
        -PassThru

    Set-Content -Path $PidFile -Value $proc.Id -NoNewline
    Start-Sleep -Milliseconds 500
    if (-not (Test-Running $proc.Id)) {
        Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
        throw "启动失败，请查看日志: $LogFile"
    }
    Write-Host "$AppName 已启动 pid=$($proc.Id)"
    Write-Host "  日志: $LogFile"
}

function Invoke-Stop {
    $pidVal = Read-Pid
    if (-not $pidVal) {
        Write-Host "未找到 $PidFile，$AppName 可能未以后台方式启动。"
        return
    }
    if (-not (Test-Running $pidVal)) {
        Write-Host "进程 $pidVal 不存在，清理 $PidFile"
        Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
        return
    }

    Write-Host "正在停止 $AppName (pid=$pidVal)..."
    try { Stop-Process -Id $pidVal -ErrorAction Stop } catch { }

    for ($i = 0; $i -lt 30; $i++) {
        if (-not (Test-Running $pidVal)) {
            Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
            Write-Host "已停止。"
            return
        }
        Start-Sleep -Seconds 1
    }

    if (Test-Running $pidVal) {
        Write-Host "优雅停止超时，强制结束..."
        Stop-Process -Id $pidVal -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 1
    }
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
    Write-Host "已停止。"
}

function Invoke-Foreground {
    $java = Assert-JavaAndFiles
    $pidVal = Read-Pid
    if (Test-Running $pidVal) {
        throw "$AppName 已在后台运行 (pid=$pidVal)。请先: .\start.ps1 stop"
    }
    Write-Host "前台启动 $AppName（Ctrl+C 停止）..."
    & $java -jar $Jar --config $Config
    exit $LASTEXITCODE
}

try {
    switch ($Command) {
        "start" { Invoke-Start }
        "stop" { Invoke-Stop }
        "restart" { Invoke-Stop; Invoke-Start }
        "status" { Invoke-Status }
        { $_ -in @("foreground", "fg") } { Invoke-Foreground }
        { $_ -in @("help", "") } {
            Show-Usage
            if (-not $Command) { exit 1 }
        }
        default {
            Write-Host "未知命令: $Command" -ForegroundColor Red
            Show-Usage
            exit 1
        }
    }
} catch {
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
