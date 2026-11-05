param([string]$BaseUrl = 'http://localhost:8088')
$ErrorActionPreference = 'Stop'
$containers = @(docker compose ps -q delivery-service)
if ($containers.Count -lt 2) {
    throw 'Need at least 2 delivery-service containers. Run: docker compose up -d --scale delivery-service=3'
}
$stopped = $containers[0]
Write-Host "Stopping one delivery-service replica: $stopped"
docker stop $stopped | Out-Host
try {
    Start-Sleep -Seconds 8
    1..12 | ForEach-Object {
        $response = Invoke-RestMethod "$BaseUrl/api/deliveries/1"
        Write-Host "[$_] HTTP OK; delivery=$($response.deliveryId); instance=$($response.servedBy); total=$($response.total)"
    }
} finally {
    Write-Host 'Starting previously stopped replica again...'
    docker start $stopped | Out-Host
}
