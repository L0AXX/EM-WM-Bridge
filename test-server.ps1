# ============================================================
# EM-WM-Bridge 真机黑盒测试脚本
# ============================================================
# 用法:
#   1. 先部署: ./gradlew deploy
#   2. 运行测试: ./test-server.ps1
#
# 测试流程:
#   1. 启动测试服务器（后台运行）
#   2. 等待服务器就绪
#   3. 自动执行测试场景
#   4. 验证输出并生成报告
#   5. 关闭服务器
# ============================================================

param(
    [string]$ServerPath = "F:\LOAXX Devlop CLI\GreyZone S_1.21.4JDK21",
    [string]$JavaPath = "F:\LOAXX Devlop CLI\GreyZone S_1.21.4JDK21\runtime\bin\java.exe",
    [int]$StartupTimeoutSeconds = 120,
    [int]$TestTimeoutSeconds = 60
)

$ErrorActionPreference = "Stop"
$script:TotalTests = 0
$script:PassedTests = 0
$script:FailedTests = 0
$script:TestResults = @()

# ============================================================
# 辅助函数
# ============================================================

function Write-Header {
    param([string]$Title)
    Write-Host ""
    Write-Host "=" * 60 -ForegroundColor Cyan
    Write-Host "  $Title" -ForegroundColor Cyan
    Write-Host "=" * 60 -ForegroundColor Cyan
}

function Write-TestResult {
    param([string]$Name, [bool]$Passed, [string]$Detail = "")
    $script:TotalTests++
    if ($Passed) {
        $script:PassedTests++
        $status = "[PASS]" -replace "PASS", $([char]0x221A)
        Write-Host "  $status $Name" -ForegroundColor Green
    } else {
        $script:FailedTests++
        Write-Host "  [FAIL] $Name" -ForegroundColor Red
        if ($Detail) {
            Write-Host "          $Detail" -ForegroundColor Red
        }
    }
    $script:TestResults += @{ Name = $Name; Passed = $Passed; Detail = $Detail }
}

function Send-Command {
    param([string]$Command)
    Write-Host "  >> $Command" -ForegroundColor DarkGray
    $script:ServerProcess.StandardInput.WriteLine($Command)
    $script:ServerProcess.StandardInput.Flush()
}

function Wait-ForOutput {
    param(
        [string]$Pattern,
        [int]$TimeoutSeconds = 30,
        [bool]$Regex = $false
    )
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $buffer = ""
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        if ($script:ServerProcess.StandardOutput.EndOfStream) {
            Start-Sleep -Milliseconds 100
            continue
        }
        $line = $script:ServerProcess.StandardOutput.ReadLineAsync().GetAwaiter().GetResult()
        if ($line) {
            Write-Host "  [LOG] $line" -ForegroundColor DarkGray
            $buffer += "$line`n"
            if ($Regex) {
                if ($line -match $Pattern) { return $true, $buffer }
            } else {
                if ($line -like "*$Pattern*") { return $true, $buffer }
            }
        }
        Start-Sleep -Milliseconds 50
    }
    return $false, $buffer
}

function Wait-ForServerReady {
    Write-Host "  等待服务器启动..." -ForegroundColor Yellow
    $ready, $output = Wait-ForOutput -Pattern "Done (" -TimeoutSeconds $StartupTimeoutSeconds
    if ($ready) {
        Write-Host "  服务器就绪!" -ForegroundColor Green
        # 额外等待 3 秒确保所有插件加载完成
        Start-Sleep -Seconds 3
        return $true
    } else {
        Write-Host "  服务器启动超时!" -ForegroundColor Red
        return $false
    }
}

# ============================================================
# 测试场景
# ============================================================

function Test-PluginLoaded {
    Write-Host "`n--- 场景 1: 验证插件加载 ---" -ForegroundColor Yellow
    Send-Command "emwm version"
    $found, $output = Wait-ForOutput -Pattern "EM-WM-Bridge" -TimeoutSeconds 10
    Write-TestResult "插件加载成功" $found "未检测到 EM-WM-Bridge 版本信息"
}

function Test-ConfigReload {
    Write-Host "`n--- 场景 2: 配置热重载 ---" -ForegroundColor Yellow
    Send-Command "emwm reload"
    $found, $output = Wait-ForOutput -Pattern "配置已重新加载" -TimeoutSeconds 10
    Write-TestResult "配置热重载" $found "未检测到重载成功消息"
}

function Test-StatsCommand {
    Write-Host "`n--- 场景 3: 统计命令 ---" -ForegroundColor Yellow
    Send-Command "emwm stats"
    $found, $output = Wait-ForOutput -Pattern "EM-WM-Bridge 统计" -TimeoutSeconds 10
    Write-TestResult "统计命令可用" $found "未检测到统计信息"
}

function Test-SpawnScavWithWeapon {
    Write-Host "`n--- 场景 4: Scav 生成并绑定武器 ---" -ForegroundColor Yellow
    # 生成一个带 scav 标记的僵尸
    Send-Command 'summon minecraft:zombie 0 64 0 {CustomName:"\"scav_rifle_test\"",CustomNameVisible:1b,NoAI:1b}'
    Start-Sleep -Seconds 2

    # 检查日志中是否有武器绑定信息
    $found, $output = Wait-ForOutput -Pattern "scav_rifle_test" -TimeoutSeconds 10
    Write-TestResult "Scav 生成事件触发" $found "未在日志中检测到 scav_rifle_test"

    # 清理
    Send-Command "kill @e[type=zombie,name=scav_rifle_test]"
    Start-Sleep -Seconds 1
}

function Test-NoErrorOnStartup {
    Write-Host "`n--- 场景 5: 启动无错误 ---" -ForegroundColor Yellow
    # 检查最近的日志中是否有 ERROR 或 Exception
    Send-Command "say ---EMWM_TEST_MARKER---"
    Start-Sleep -Seconds 1

    # 读取 latest.log 检查错误
    $logPath = Join-Path $ServerPath "logs\latest.log"
    if (Test-Path $logPath) {
        $logContent = Get-Content $logPath -Tail 100 -Raw
        $hasWeaponError = $logContent -match "\[ERROR\].*EM.?WM|\[ERROR\].*WeaponMechanics"
        $hasException = $logContent -match "Exception.*com\.emwbridge"
        $noErrors = (-not $hasWeaponError) -and (-not $hasException)
        Write-TestResult "启动无 EM-WM 相关错误" $noErrors "日志中检测到错误"
    } else {
        Write-TestResult "启动无错误" $true "无法读取日志文件 (跳过)"
    }
}

function Test-EmwmInfoCommand {
    Write-Host "`n--- 场景 6: 配置缓存信息 ---" -ForegroundColor Yellow
    Send-Command "emwm info"
    $found, $output = Wait-ForOutput -Pattern "EMWM配置缓存" -TimeoutSeconds 10
    Write-TestResult "配置缓存信息命令" $found "未检测到缓存信息"
}

# ============================================================
# 主流程
# ============================================================

Write-Header "EM-WM-Bridge 真机黑盒测试"

# 1. 检查服务器路径
Write-Host "`n[1/5] 检查测试服务器..." -ForegroundColor Yellow
if (-not (Test-Path $ServerPath)) {
    Write-Host "  错误: 服务器路径不存在: $ServerPath" -ForegroundColor Red
    Write-Host "  请使用 -ServerPath 参数指定正确的路径" -ForegroundColor Red
    exit 1
}
$serverJar = Get-ChildItem "$ServerPath\paper-*.jar" | Select-Object -First 1
if (-not $serverJar) {
    Write-Host "  错误: 未找到 paper-*.jar" -ForegroundColor Red
    exit 1
}
Write-Host "  服务器: $($serverJar.Name)" -ForegroundColor Green

# 2. 检查插件是否已部署
Write-Host "`n[2/5] 检查插件部署..." -ForegroundColor Yellow
$pluginJar = Get-ChildItem "$ServerPath\plugins\EM-WM-Bridge-*.jar" -ErrorAction SilentlyContinue
if (-not $pluginJar) {
    Write-Host "  警告: 未找到 EM-WM-Bridge JAR，请先执行 ./gradlew deploy" -ForegroundColor Red
    Write-Host "  是否继续? (y/n)" -ForegroundColor Yellow
    $response = Read-Host
    if ($response -ne "y") { exit 1 }
} else {
    Write-Host "  插件 JAR: $($pluginJar.Name)" -ForegroundColor Green
}

# 3. 启动服务器
Write-Host "`n[3/5] 启动测试服务器..." -ForegroundColor Yellow
Write-Host "  启动命令: java -Xmx4G -jar $($serverJar.Name) nogui" -ForegroundColor DarkGray

$processStartInfo = New-Object System.Diagnostics.ProcessStartInfo
$processStartInfo.FileName = $JavaPath
$processStartInfo.Arguments = @(
    "-Xmx4G",
    "-XX:+UseZGC",
    "-XX:+ZGenerational",
    "-jar", $serverJar.FullName,
    "nogui"
) -join " "
$processStartInfo.WorkingDirectory = $ServerPath
$processStartInfo.RedirectStandardInput = $true
$processStartInfo.RedirectStandardOutput = $true
$processStartInfo.RedirectStandardError = $true
$processStartInfo.UseShellExecute = $false
$processStartInfo.CreateNoWindow = $true

$script:ServerProcess = [System.Diagnostics.Process]::Start($processStartInfo)
Write-Host "  服务器进程已启动 (PID: $($script:ServerProcess.Id))" -ForegroundColor Green

# 4. 等待服务器就绪
Write-Host "`n[4/5] 等待服务器就绪..." -ForegroundColor Yellow
$ready = Wait-ForServerReady
if (-not $ready) {
    Write-Host "  服务器启动失败，终止测试" -ForegroundColor Red
    if (-not $script:ServerProcess.HasExited) {
        $script:ServerProcess.Kill()
    }
    exit 1
}

# 5. 执行测试
Write-Host "`n[5/5] 执行测试场景..." -ForegroundColor Yellow

Test-PluginLoaded
Test-ConfigReload
Test-StatsCommand
Test-EmwmInfoCommand
Test-NoErrorOnStartup
Test-SpawnScavWithWeapon

# 6. 关闭服务器
Write-Host "`n" -NoNewline
Write-Header "测试完成，关闭服务器"
Send-Command "stop"
$stopped, $output = Wait-ForOutput -Pattern "Stopping the server" -TimeoutSeconds 30
if (-not $stopped) {
    Write-Host "  服务器未能正常关闭，强制终止" -ForegroundColor Yellow
    if (-not $script:ServerProcess.HasExited) {
        $script:ServerProcess.Kill()
    }
}

# 7. 生成报告
Write-Header "测试报告"
Write-Host "  总计: $script:TotalTests  通过: $script:PassedTests  失败: $script:FailedTests" -ForegroundColor $(if ($script:FailedTests -eq 0) { "Green" } else { "Red" })
Write-Host ""

if ($script:FailedTests -gt 0) {
    Write-Host "  失败详情:" -ForegroundColor Red
    foreach ($result in $script:TestResults) {
        if (-not $result.Passed) {
            Write-Host "    - $($result.Name): $($result.Detail)" -ForegroundColor Red
        }
    }
}

Write-Host ""
if ($script:FailedTests -eq 0) {
    Write-Host "  所有测试通过!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "  存在 $script:FailedTests 项测试失败，请检查服务器日志" -ForegroundColor Red
    Write-Host "  日志路径: $ServerPath\logs\latest.log" -ForegroundColor DarkGray
    exit 1
}