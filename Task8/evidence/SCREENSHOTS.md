# Фиксация доказательств

Скриншоты нужно делать только после реального запуска Docker на своём ПК.

1. `01-containers.png`: `docker compose ps`, gateway, three services, three postgres, rabbitmq, traefik.
2. `02-order.png`: запрос POST /api/orders, статус 201, orderId.
3. `03-rabbit.png`: Rabbit Management queues/exchanges, рядом docker compose logs с eventReceived / deliveryCreated.
4. `04-sse.png`: две PowerShell-консоли, первая с curl.exe -N /api/deliveries/{id}/events, вторая с тремя PUT.
5. `05-request-id.png`: docker compose logs ... | Select-String pr8-trace-001 по всем сервисам.
6. `06-failure.png`: Supplier OFF и GET /api/system/health -> DOWN, HTTP 503 после POST.
7. `07-recovery.png`: Supplier снова UP и новый POST -> 201.
8. `08-bootcamp.png`: реальный завершённый курс BootcampLabs (не подделывать).

Пользовательское требование: 3–5 скриншотов полного сквозного сценария плюс отдельно контейнеры, requestId, брокер, отказ и завершение BootcampLabs.
