param(
    [int]$Count = 20,
    [string]$BaseUrl = 'http://localhost:8088'
)
$ErrorActionPreference = 'Stop'
$instances = @()
for ($i = 1; $i -le $Count; $i++) {
    $reply = Invoke-RestMethod -Uri "$BaseUrl/api/deliveries/instance" -Method Get
    $instances += $reply.instanceId
    Write-Host "[$i] delivery-service instance: $($reply.instanceId)"
}
Write-Host "`nDistribution:"
$instances | Group-Object | Sort-Object Name | Format-Table Count, Name -AutoSize
if (($instances | Select-Object -Unique).Count -lt 2) {
    Write-Warning 'Only one instance was observed. Scale delivery-service first.'
}
