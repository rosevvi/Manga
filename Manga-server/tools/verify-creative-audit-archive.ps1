#requires -Version 7.0
<#
Manga 审计归档离线校验与解码演练。不连接数据库，不覆盖业务数据。
输入可为管理员归档接口的完整响应，或其中的 data 对象。
#>
param([Parameter(Mandatory)][string]$Path)
$ErrorActionPreference = 'Stop'
$archiveDocument = Get-Content -LiteralPath $Path -Raw -Encoding utf8 | ConvertFrom-Json
$archiveValue = if ($archiveDocument.PSObject.Properties['data']) { $archiveDocument.data } else { $archiveDocument }
if ($archiveValue.schemaVersion -ne 1 -or [string]::IsNullOrWhiteSpace($archiveValue.payloadJson)) { throw 'Unsupported or empty archive.' }
$archiveBytes = [Text.Encoding]::UTF8.GetBytes($archiveValue.payloadJson)
$archiveHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($archiveBytes)).ToLowerInvariant()
if ($archiveHash -cne $archiveValue.sha256) { throw 'Archive SHA-256 mismatch.' }
$restoredAudit = $archiveValue.payloadJson | ConvertFrom-Json
if ($restoredAudit.schemaVersion -ne 1 -or $restoredAudit.run.runId -cne $archiveValue.runId) { throw 'Archive identity mismatch.' }
[pscustomobject]@{
    Verified = $true
    RunId = $archiveValue.runId
    Steps = @($restoredAudit.steps).Count
    Events = @($restoredAudit.events).Count
    Messages = @($restoredAudit.messages).Count
    Changes = @($restoredAudit.changes).Count
    Scope = 'audit-decode-only; database and object-store restore are separate'
} | ConvertTo-Json
