#Requires -RunAsAdministrator
$exe = Join-Path $PSScriptRoot "Collab3DServer.exe"
if (-not (Test-Path $exe)) { throw "Collab3DServer.exe not found next to this script." }

foreach ($rule in @(
    @{ Name = "Collab3DServer-TCP"; Protocol = "TCP" },
    @{ Name = "Collab3DServer-UDP"; Protocol = "UDP" }
)) {
    if (-not (Get-NetFirewallRule -DisplayName $rule.Name -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -DisplayName $rule.Name -Direction Inbound -Action Allow `
            -Program $exe -Protocol $rule.Protocol | Out-Null
        Write-Host "Added rule: $($rule.Name)"
    } else {
        Write-Host "Rule already exists: $($rule.Name)"
    }
}