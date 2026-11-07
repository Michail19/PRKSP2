$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8088'
Write-Host '--- Before failure ---'
Invoke-RestMethod "$base/api/system/health" | ConvertTo-Json
Write-Host '--- Stop ONLY supplier business service (do not stop database) ---'
docker compose stop supplier-service
Write-Host '--- System health with supplier unavailable ---'
Invoke-RestMethod "$base/api/system/health" | ConvertTo-Json
Write-Host '--- Order POST should fail quickly with HTTP 503 ---'
$timer=[Diagnostics.Stopwatch]::StartNew()
try {
  Invoke-RestMethod -Method Post -Uri "$base/api/orders" -ContentType 'application/json' `
      -Body '{"productId":1,"quantity":2}' | ConvertTo-Json
} catch {
  Write-Host "Expected error: $($_.Exception.Message)"
}
$timer.Stop()
Write-Host "Elapsed: $($timer.Elapsed.TotalSeconds) seconds"
Write-Host '--- Restore service ---'
docker compose start supplier-service
Write-Host 'Service is starting; check: docker compose ps'
Write-Host 'Circuit breaker may remain OPEN for 8 seconds before half-open probe.'
