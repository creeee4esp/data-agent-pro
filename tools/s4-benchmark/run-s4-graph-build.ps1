# S4-1 建图评测：核显跑 Ollama -> 全量嵌入 schema 文档 -> 写入 ES -> 建表关系图
#
# 用法：双击同目录下的 run-s4-graph-build.cmd
#
# 行为：
#   1. 结束当前 Ollama（托盘版），以“仅核显 Intel Iris Xe”模式后台重启；
#      跑完后 Ollama 仍以后台核显模式运行，要恢复托盘版，重新打开 Ollama 应用即可
#   2. 嵌入缓存保留在 data-agent-management\target\embedding-cache，中断后再次运行会续跑
#   3. Maven 日志：target\s4-benchmark\graph-build-<时间戳>.log

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root

# 3298 张表 + 20806 列；等日志里出现实际文档数时会覆盖
$defaultExpectedDocs = 24104
$pollSeconds = 5
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$logDir = Join-Path $root 'target\s4-benchmark'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$mvnLog = Join-Path $logDir "graph-build-$stamp.log"
$exitFile = "$mvnLog.exit"
$ollamaLog = Join-Path $logDir "ollama-$stamp.log"
$cacheRoot = Join-Path $root 'data-agent-management\target\embedding-cache'

function Write-Step($message) {
	Write-Host ""
	Write-Host ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $message) -ForegroundColor Cyan
}

function Get-CacheCount {
	if (-not (Test-Path $cacheRoot)) { return 0 }
	try {
		return @([System.IO.Directory]::EnumerateFiles($cacheRoot, '*', [System.IO.SearchOption]::AllDirectories)).Count
	}
	catch {
		return 0
	}
}

Write-Host "S4-1 建图评测（核显嵌入 + ES 向量库）" -ForegroundColor Green
Write-Host "项目目录: $root"

# 1. JDK 与 PATH
$jdk = 'C:\web\Java\jdk-17.0.10'
if (Test-Path $jdk) { $env:JAVA_HOME = $jdk }
$env:PATH = "C:\Windows\System32\WindowsPowerShell\v1.0;$env:PATH"
if (-not $env:JAVA_HOME) {
	Write-Host "找不到 JAVA_HOME，也没有 $jdk" -ForegroundColor Red
	exit 1
}
Write-Host "JAVA_HOME: $env:JAVA_HOME"

# 2. Elasticsearch 连通性
try {
	$null = Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 'http://127.0.0.1:9200'
	Write-Host "Elasticsearch 已连接"
}
catch {
	Write-Host "Elasticsearch 未启动（http://127.0.0.1:9200 无响应）。请先启动 ES，再重新运行本脚本。" -ForegroundColor Red
	exit 1
}

# 3. 重启 Ollama：关掉托盘的默认设备选择，改为只用核显（Vulkan）
Write-Step '重启 Ollama（仅核显 Intel Iris Xe）'
Get-Process | Where-Object { $_.ProcessName -like 'ollama*' } | ForEach-Object {
	try { $_.Kill() } catch { }
}
Start-Sleep -Seconds 2
# 托盘应用可能在服务被杀后把服务拉起来，再清一次
Get-Process | Where-Object { $_.ProcessName -like 'ollama*' } | ForEach-Object {
	try { $_.Kill() } catch { }
}
Start-Sleep -Seconds 1

$ollamaExe = Join-Path $env:LOCALAPPDATA 'Programs\Ollama\ollama.exe'
if (-not (Test-Path $ollamaExe)) {
	Write-Host "找不到 Ollama: $ollamaExe" -ForegroundColor Red
	exit 1
}

$env:OLLAMA_IGPU_ENABLE = '1'
$env:OLLAMA_VULKAN = 'true'
$env:CUDA_VISIBLE_DEVICES = '99'     # 隐藏独显 MX330（CUDA）
$env:GGML_VK_VISIBLE_DEVICES = '1'   # Vulkan 只保留核显
$env:OLLAMA_NUM_PARALLEL = '1'
$env:OLLAMA_KEEP_ALIVE = '60m'
$env:OLLAMA_HOST = 'http://127.0.0.1:11434'

$ollamaProc = Start-Process -FilePath $ollamaExe -ArgumentList 'serve' `
	-RedirectStandardOutput $ollamaLog -RedirectStandardError "$ollamaLog.err" `
	-WindowStyle Hidden -PassThru

$ready = $false
for ($i = 0; $i -lt 60; $i++) {
	try {
		$null = Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 'http://127.0.0.1:11434/api/version'
		$ready = $true
		break
	}
	catch {
		Start-Sleep -Seconds 1
	}
}
if (-not $ready) {
	Write-Host "Ollama 启动失败，日志: $ollamaLog" -ForegroundColor Red
	exit 1
}

# Ollama 把启动日志写到 stderr（stdout 里只有请求日志），两个文件都要看
$deviceLine = Get-Content -Path $ollamaLog, "$ollamaLog.err" -Encoding UTF8 -ErrorAction SilentlyContinue |
	Where-Object { $_ -match 'inference compute' } | Select-Object -First 1
if (-not $deviceLine) {
	Write-Host "Ollama 没有报告任何 GPU 设备（可能端口被旧进程占用），日志: $ollamaLog" -ForegroundColor Red
	exit 1
}
if ($deviceLine -notmatch 'Iris') {
	Write-Host "Ollama 选中的不是核显，继续跑会很慢，已中止。设备行: $deviceLine" -ForegroundColor Red
	Write-Host "日志: $ollamaLog"
	exit 1
}
Write-Host "Ollama 设备: Intel Iris Xe (Vulkan，29/29 层全上 GPU)"

# 4. 后台启动 Maven 测试，同时显示嵌入进度
Write-Step '启动 Maven 测试：嵌入 -> 写 ES -> 建图'
$runner = Join-Path $logDir "run-$stamp.cmd"
$mavenCommand = 'call mvnw.cmd -pl data-agent-management test "-Dtest=SchemaGraphBenchmarkIT" ' +
	'"-Dbenchmark.vector-store=elasticsearch" "-Dbenchmark.schema=enterprise_dw_l" ' +
	'"-Dbenchmark.verify-determinism=false" "-Djacoco.skip=true" > "' + $mvnLog + '" 2>&1'
@(
	'@echo off',
	('cd /d "' + $root + '"'),
	$mavenCommand,
	('echo %errorlevel% > "' + $exitFile + '"')
) | Set-Content -Path $runner -Encoding ASCII
$mvnProc = Start-Process -FilePath $runner -WindowStyle Hidden -PassThru

$expectedDocs = $defaultExpectedDocs
$expectedParsed = $false
$start = Get-Date
$count = Get-CacheCount

Write-Host ""
while (-not (Test-Path $exitFile)) {
	$count = Get-CacheCount

	# 日志里出现 "Storing ... columns and N tables for datasource: M" 时，用真实文档数替换默认值
	if (-not $expectedParsed -and (Test-Path $mvnLog)) {
		$storing = Select-String -Path $mvnLog -Pattern 'columns and (\d+) tables for datasource: (\d+)' -Encoding UTF8 |
			Select-Object -First 1
		if ($storing) {
			$expectedDocs = [int]$storing.Matches[0].Groups[1].Value + [int]$storing.Matches[0].Groups[2].Value
			$expectedParsed = $true
		}
	}

	$elapsed = (Get-Date) - $start
	$pct = [Math]::Min(100.0, 100.0 * $count / [Math]::Max(1, $expectedDocs))
	$filled = [int][Math]::Floor(24 * $pct / 100)
	$bar = ('#' * $filled) + ('-' * (24 - $filled))
	$rate = if ($elapsed.TotalSeconds -ge 10) { $count / $elapsed.TotalSeconds } else { 0 }
	$etaText = '--:--'
	if ($rate -gt 0) {
		$etaText = ([TimeSpan]::FromSeconds(([Math]::Max(0, $expectedDocs - $count)) / $rate)).ToString('mm\:ss')
	}
	$phase = if ($count -eq 0) { '准备中（编译 / 读 schema）' } else { '嵌入中' }
	$line = "[{0}] {1,6:N2}%  {2}/{3}  {4:N1} 条/秒  已用 {5}  剩余 {6}  {7}" -f $bar, $pct, $count, $expectedDocs, $rate, $elapsed.ToString('hh\:mm\:ss'), $etaText, $phase
	Write-Host ("`r" + $line.PadRight(100)) -NoNewline

	Start-Sleep -Seconds $pollSeconds

	# 兜底：Maven 进程没了却没写退出码文件，别让循环一直转
	if ($mvnProc.HasExited -and -not (Test-Path $exitFile)) {
		Write-Host ""
		Write-Host 'Maven 进程异常退出（没有留下退出码），日志末尾：' -ForegroundColor Red
		if (Test-Path $mvnLog) { Get-Content -Path $mvnLog -Encoding UTF8 -Tail 40 }
		break
	}
}
Write-Host ""

# 5. 汇总
$exitCode = 'unknown'
if (Test-Path $exitFile) {
	$exitCode = (Get-Content -Path $exitFile -Raw).Trim()
}
$total = (Get-Date) - $start
$count = Get-CacheCount
Write-Step ('Maven 结束：退出码 {0}，总耗时 {1}，缓存条目 {2}' -f $exitCode, $total.ToString('hh\:mm\:ss'), $count)

$graphLine = $null
if (Test-Path $mvnLog) {
	$graphLine = Select-String -Path $mvnLog -Pattern 'S4-1 graph' -Encoding UTF8 | Select-Object -Last 1
}
if ($graphLine) {
	Write-Host ""
	Write-Host '建图结果：' -ForegroundColor Green
	Write-Host ('  ' + $graphLine.Line.Trim())
}
else {
	Write-Host '没有找到建图结果行，下面是日志末尾：' -ForegroundColor Yellow
	if (Test-Path $mvnLog) { Get-Content -Path $mvnLog -Encoding UTF8 -Tail 40 }
}

Write-Host ""
Write-Host "完整日志: $mvnLog"
Write-Host "Ollama 仍在后台以核显模式运行；要恢复托盘版，重新打开 Ollama 应用即可。"
