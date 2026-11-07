$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8088'
$rid = 'pr8-' + [guid]::NewGuid().ToString('N')
Write-Host "Using requestId=$rid"
Write-Host '--- Current system health ---'
Invoke-RestMethod "$base/api/system/health" | ConvertTo-Json
Write-Host '--- Products (from Supplier Service) ---'
(Invoke-RestMethod "$base/api/products")[0..2] | Format-Table
Write-Host '--- Create order with synchronous supplier validation ---'
$order = Invoke-RestMethod -Method Post -Uri "$base/api/orders" -Headers @{ 'X-Request-ID'=$rid } `
  -ContentType 'application/json; charset=utf-8' -Body '{"productId":1,"quantity":2}'
$order | ConvertTo-Json
Write-Host "--- Waiting for RabbitMQ delivery of OrderCreated (order=$($order.id)) ---"
$delivery=$null
for($i=0;$i -lt 25;$i++) {
    try {$delivery=Invoke-RestMethod "$base/api/deliveries/by-order/$($order.id)"; break}
    catch {Start-Sleep -Seconds 1}
}
if(-not $delivery){throw 'Delivery not created. Inspect order-service and delivery-service logs.'}
$delivery | ConvertTo-Json
Write-Host '--- Replay identical eventId; delivery row must remain single ---'
Invoke-RestMethod -Method Post "$base/api/orders/$($order.id)/replay-event" | ConvertTo-Json
Start-Sleep -Seconds 2
(Invoke-RestMethod "$base/api/deliveries/by-order/$($order.id)") | ConvertTo-Json
Write-Host '--- Update CREATED -> ACCEPTED -> IN_TRANSIT -> DELIVERED ---'
foreach($status in @('ACCEPTED','IN_TRANSIT','DELIVERED')) {
  $body = @{status=$status} | ConvertTo-Json
  Invoke-RestMethod -Method Put -Uri "$base/api/deliveries/$($delivery.id)/status" `
    -Headers @{'X-Request-ID'=$rid} -ContentType 'application/json; charset=utf-8' -Body $body | ConvertTo-Json
  Start-Sleep -Seconds 2
}
Write-Host "For event log: docker compose logs --since 5m api-gateway order-service supplier-service delivery-service | Select-String $rid"
Write-Host "To demonstrate SSE on a NEW order, open another PowerShell first: curl.exe -N $base/api/deliveries/<deliveryId>/events"
