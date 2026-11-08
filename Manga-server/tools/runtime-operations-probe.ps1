#requires -Version 7.0
<#
Manga 运维只读接口并发探测。仅测读取路径，不调用模型或创建 Run。
令牌由进程环境变量 MANGA_OPERATIONS_TOKEN 提供，不写入报告或命令行参数。
#>
param(
    [Parameter(Mandatory)][uri]$BaseUrl,
    [ValidateRange(1, 10000)][int]$Requests = 200,
    [ValidateRange(1, 32)][int]$Concurrency = 8
)
$ErrorActionPreference = 'Stop'
if ($BaseUrl.Scheme -notin @('http', 'https') -or $BaseUrl.UserInfo) { throw 'BaseUrl must be HTTP(S) without embedded credentials.' }
$probeToken = $env:MANGA_OPERATIONS_TOKEN
if ([string]::IsNullOrWhiteSpace($probeToken)) { throw 'Set MANGA_OPERATIONS_TOKEN before running this probe.' }
$probeUri = $BaseUrl.AbsoluteUri.TrimEnd('/') + '/api/v1/admin/creative-operations'
$probeWatch = [Diagnostics.Stopwatch]::StartNew()
$probeResults = @(1..$Requests | ForEach-Object -Parallel {
    $singleWatch = [Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-WebRequest -Uri $using:probeUri -Headers @{ Authorization = "Bearer $using:probeToken" } -SkipHttpErrorCheck -TimeoutSec 30
        [pscustomobject]@{ Status = [int]$response.StatusCode; Milliseconds = $singleWatch.Elapsed.TotalMilliseconds }
    } catch {
        # 不输出异常正文，防止代理响应和认证头进入报告。
        [pscustomobject]@{ Status = 0; Milliseconds = $singleWatch.Elapsed.TotalMilliseconds }
    }
} -ThrottleLimit $Concurrency)
$probeWatch.Stop()
$probeLatencies = @($probeResults.Milliseconds | Sort-Object)
[pscustomobject]@{
    Scope = 'operations-read-only'
    Requests = $Requests
    Concurrency = $Concurrency
    ElapsedSeconds = [math]::Round($probeWatch.Elapsed.TotalSeconds, 3)
    RequestsPerSecond = [math]::Round($Requests / $probeWatch.Elapsed.TotalSeconds, 2)
    P50Milliseconds = [math]::Round($probeLatencies[[math]::Floor(($Requests - 1) * 0.50)], 2)
    P95Milliseconds = [math]::Round($probeLatencies[[math]::Floor(($Requests - 1) * 0.95)], 2)
    Failed = @($probeResults | Where-Object Status -ne 200).Count
    StatusCounts = @($probeResults | Group-Object Status | Select-Object Name, Count)
} | ConvertTo-Json -Depth 4
if (@($probeResults | Where-Object Status -ne 200).Count -gt 0) { exit 1 }
