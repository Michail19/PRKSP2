# Практическая работа №7 — Darkstore WebFlux + Project Reactor

## Назначение

Реактивный REST API для доставок с поддержкой **HTTP GET / Mono**, **HTTP/SSE / Flux**, CRUD, обработки ошибок, вызова **Order Service через WebClient**, реактивной H2 (R2DBC), демонстрации backpressure и тестов.

Модели и исходные данные перенесены из практических №3–5: `darkstore` (5), `good` (10), `delivery` (20), `goods_list` (30). Данные не требуют настоящего BootcampLabs/backend. Роль `orderId` в учебной модели выполняет идентификатор исторической доставки, к которой относятся позиции `goods_list`.

## Запуск

Требуется **JDK 21** и **Maven 3.9+**. В корне проекта:

```powershell
mvn clean package
```

Откройте два окна PowerShell, оба — в корне проекта:

```powershell
java -jar order-service/target/order-service-1.0.0.jar
```

```powershell
java -jar delivery-service/target/delivery-service-1.0.0.jar
```

Либо `build.cmd`, `start-order.cmd` и `start-delivery.cmd` на Windows.

- Order Service: http://localhost:8081
- Delivery Service: http://localhost:8080
- Адрес Order Service берётся из `ORDER_SERVICE_URL` (по умолчанию http://localhost:8081).
- Реляционная база данных встроенная **H2 через R2DBC**: исходные 20 доставок создаются заново при запуске. Это учебная память, не production-хранилище. Зависимость от PostgreSQL отсутствует.

## REST API

| Метод | URL | Результат |
|---|---|---|
| GET | `/api/deliveries` | `Flux<DeliveryDto>` → HTTP JSON-массив |
| GET | `/api/deliveries?status=COMPLETED` | Фильтрация через `filter()` |
| GET | `/api/deliveries/{id}` | `Mono<DeliveryDto>` |
| POST | `/api/deliveries` | Создаёт доставку `CREATED` (201) |
| PUT | `/api/deliveries/{id}` | Следующий допустимый статус |
| DELETE | `/api/deliveries/{id}` | Удаление (204) |
| GET | `/api/deliveries/{id}/stream` | Настоящий SSE: `SNAPSHOT` + изменения статуса от PUT |
| GET | `/api/deliveries/{id}/demo-stream` | Демонстрационный SSE, 4 события через 1 секунду, БД не изменяется |
| GET | `/api/deliveries/{id}/with-order` | `WebClient` → Order Service (порт 8081) |
| GET | `/api/deliveries/backpressure-demo?count=10` | Учебный `limitRate()` + SSE |
| GET | `http://localhost:8081/api/orders/1` | Отдельный Order Service |

### Примеры PowerShell (НЕ требуется разрешать выполнение .ps1)

```powershell
# 1. Список исторических доставок
Invoke-RestMethod http://localhost:8080/api/deliveries
# 2. Одна доставка (номер 1 соответствует исходным CSV)
Invoke-RestMethod http://localhost:8080/api/deliveries/1
# 3. Межсервисный вызов: Delivery -> WebClient -> Order
Invoke-RestMethod http://localhost:8080/api/deliveries/1/with-order | ConvertTo-Json -Depth 7
# 4. Несуществующая доставка: ожидается HTTP 404
curl.exe -i http://localhost:8080/api/deliveries/99999
# 5. Отдельный сервер заказов
Invoke-RestMethod http://localhost:8081/api/orders/1 | ConvertTo-Json -Depth 6
```

Важно: **историческая доставка №1 уже `COMPLETED`**, это конечное состояние. Не делайте для неё PUT. Для демонстрации переходов создайте новую доставку.

### POST + PUT + DELETE

В первом PowerShell:

```powershell
# Создаём новую доставку с orderId=1 (это ссылка на существующий исторический заказ)
$created = Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/deliveries -ContentType 'application/json' -Body '{"orderId":1,"darkstoreId":3,"address":"Тестовый адрес","priceKopecks":4039928}'
$created
$id = $created.id
# Статус новой доставки — CREATED
Invoke-RestMethod -Method PUT -Uri "http://localhost:8080/api/deliveries/$id" -ContentType 'application/json' -Body '{"status":"ACCEPTED"}'
Invoke-RestMethod -Method PUT -Uri "http://localhost:8080/api/deliveries/$id" -ContentType 'application/json' -Body '{"status":"IN_TRANSIT"}'
Invoke-RestMethod -Method PUT -Uri "http://localhost:8080/api/deliveries/$id" -ContentType 'application/json' -Body '{"status":"DELIVERED"}'
# Запрещённый переход: terminal DELIVERED -> CREATED, ожидается 409
curl.exe -i -X PUT -H "Content-Type: application/json" --data-binary '{"status":"CREATED"}' "http://localhost:8080/api/deliveries/$id"
# Удаление (проверяйте после окончания SSE-демонстрации)
Invoke-WebRequest -Method DELETE -Uri "http://localhost:8080/api/deliveries/$id" | Select-Object StatusCode
```

Статусы новых доставок: `CREATED -> ACCEPTED -> IN_TRANSIT -> DELIVERED`, с `CANCELLED` из `CREATED`/`ACCEPTED`. Исторические статусы `SUBMITTED -> ASSEMBLY -> DELIVERY -> COMPLETED`, `CANCELLED` сохраняются как в BootcampLabs.

### Настоящий SSE: изменения из отдельного окна

1. Создайте доставку POST и запишите её `id`.
2. **До обновления** откройте второй PowerShell и запустите (замените 21 на нужный ID):

```powershell
curl.exe -N -H "Accept: text/event-stream" http://localhost:8080/api/deliveries/21/stream
```

3. Первый ответ `event:SNAPSHOT`, далее SSE-соединение **остаётся открытым**. Это нормально.
4. В первом PowerShell последовательно вызывайте PUT `ACCEPTED`, `IN_TRANSIT`, `DELIVERED`. Во втором окне появятся `event:STATUS_CHANGED` без повторных GET.
5. Для закрытия соединения нажмите Ctrl+C во втором окне.

Альтернатива для мгновенного скриншота, **не меняет данные в БД**:

```powershell
curl.exe -N -H "Accept: text/event-stream" http://localhost:8080/api/deliveries/1/demo-stream
```

Появятся `CREATED`, `ACCEPTED`, `IN_TRANSIT`, `DELIVERED` примерно по одному в секунду, и поток завершится.

### Backpressure

```powershell
curl.exe -N http://localhost:8080/api/deliveries/backpressure-demo?count=10
```

`limitRate(2)` задаёт предвыборку, а `doOnRequest` регистрирует спрос. Тест `DeliveryServiceTest.backpressureRequestsAreExplicit` через `StepVerifier` проверяет запрос по 2, затем 2 и 1 элементу. **Этот учебный пример не гарантирует сдерживание произвольного внешнего источника**, он демонстрирует механизм запроса в Reactive Streams.

### Тесты

```powershell
mvn test
```

- `CsvCatalogTest` проверяет все 4 исходных CSV и сумму заказа №1 (40 399,28 ₽).
- `DeliveryHttpTest` проверяет GET/404/POST через `WebTestClient` с настоящей реактивной H2.
- `DeliveryServiceTest` проверяет переходы, событие и backpressure через `StepVerifier`.

## WebFlux vs RSocket: 5 отличий (для отчёта)

| Критерий | Spring WebFlux | RSocket |
|---|---|---|
| Протокол | HTTP, HTTP streaming / SSE | Отдельный бинарный протокол поверх TCP/WebSocket и др. |
| Один объект | HTTP GET + `Mono<T>` | Request-Response |
| Поток объектов | HTTP GET + SSE + `Flux<T>` | Request-Stream |
| Двусторонний поток | Не является частью обычного SSE (SSE сервер → клиент) | Request-Channel — полноценный двусторонний обмен |
| Однонаправленное событие | HTTP POST с обычной семантикой HTTP | Fire-and-Forget — отдельная модель без бизнес-ответа |

В WebFlux и RSocket используются общие типы Reactor `Mono`/`Flux`, однако это **не делает протоколы одинаковыми**. Более того, HTTP SSE обеспечивает поток **в одном направлении**, а Fire-and-Forget и Request-Channel не являются встроенными моделями обычного REST/SSE API.

## Что скриншотить для отчёта

1. GET `/api/deliveries/1` + GET `/api/deliveries?status=COMPLETED` (ответы JSON).
2. POST (201) → PUT (статус меняется) → при желании 409 при нарушении перехода.
3. Два PowerShell: SSE `curl.exe -N` открыт, в соседнем окне отправлены PUT, события появляются постепенно. Либо `/demo-stream` с отметками времени для демонстрации периодических сообщений.
4. GET `/api/deliveries/1/with-order`, отдельно можно показать запущенный Order Service на порту 8081.
5. **Отдельный реальный скриншот текущего прогресса BootcampLabs** — проект не содержит сведений о личном прогрессе пользователя.

Не имитируйте скриншоты успешного запуска: снимайте реальные результаты на своём ПК.

## Технические примечания

- Для учебных сценариев хватает in-memory R2DBC H2. Для долговременного хранения нужна отдельная БД (например R2DBC PostgreSQL).
- `Sinks.Many.replay().limit(1)` хранит **последнее событие** по доставке для SSE-клиентов: при новой подписке после `SNAPSHOT` возможен повтор последнего статуса. SSE не гарантирует восстановление всех прошлых событий; это демонстрационный механизм, не журнал событий.
- Обновление статуса реализовано как чтение + сохранение; при конкурентных PUT возможен lost update. Для production нужны версионирование (`@Version`), транзакции и более строгая модель консистентности.
- CSV Order Service загружаются при старте синхронно, а HTTP-обработка и WebClient работают через Reactor. В запросах **нет вызовов `block()`**.
- Непосредственно реализованы аннотационные контроллеры (`@RestController`); функциональные маршруты (`RouterFunction`) рассмотрены в исходной методичке, но не обязательны в обновлённом задании.
