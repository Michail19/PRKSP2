# Практическая работа №5 — распределённая АС доставки (Docker Compose + Traefik)

## Цель

Два самостоятельных приложения Spring Boot, взаимодействующих по REST: `Delivery Service` получает данные о доставке из `Order Service` через `RestClient`. `Order Service` хранит реальные учебные данные из таблиц `darkstore`, `good`, `delivery`, `goods_list` в PostgreSQL. Traefik — **единственная опубликованная точка входа**.

Схема:

```text
Клиент → http://localhost:8088 → Traefik
                                   ├─ /api/orders/** → Order Service → PostgreSQL
                                   └─ /api/deliveries/** → Delivery Service
                                                                 │
                                                 RestClient GET http://order-service:8080/api/orders/{id}
                                                                 └─ Order Service → PostgreSQL
```

## Состав

- `order-service/` — Spring Boot REST API + JDBC + PostgreSQL + Actuator; 5 darkstores, 10 goods, 20 deliveries/orders, 30 order items.
- `delivery-service/` — Spring Boot REST API + `RestClient` для запросов Order Service; передаёт ID своего экземпляра в каждом ответе.
- `compose.yaml` — Traefik, PostgreSQL и оба сервиса, маршрутизация с Docker Labels, проверки здоровья и зависимости от готовности.
- `source-data/*.csv` — оригинальные CSV из предыдущих практических.
- `order-service/src/main/resources/{schema.sql,data.sql}` — SQL-схема и начальные данные, импортируются при старте, вставки идемпотентны.
- `scripts/` — PowerShell-демонстрация балансировки и остановки реплики.

## Требования

- Docker Desktop (Linux containers), Docker Compose v2, доступ к Docker Hub и Maven Central при первой сборке.
- Свободен порт **8088** на хосте (можно изменить `HTTP_PORT` в `.env`).
- Для `docker compose up` не требуется устанавливать Java/Maven на Windows: оба сервиса собираются внутри Docker, через multi-stage Dockerfile.
- Чтобы собирать также локально: JDK 21 и Maven 3.9+.

## Быстрый старт (PowerShell, из корня проекта)

```powershell
docker compose up --build -d
docker compose ps
curl.exe http://localhost:8088/api/orders/1
curl.exe http://localhost:8088/api/deliveries/1
curl.exe http://localhost:8088/api/deliveries/instance
```

На самом первом запуске можно выполнить просто `docker compose up`: Compose построит отсутствующие образы автоматически. Конфигурация `.env` содержит **только учебные локальные** значения; пароль обязательно измените для иной среды.

**Базовая проверка:** заказ/доставка №1 с товарами 6 (28 шт.) и 1 (44 шт.), итоговая стоимость **40 399,28 ₽**.

Проверка ошибок (ожидается HTTP 404 и JSON с полем `error`):

```powershell
curl.exe -i http://localhost:8088/api/orders/999
curl.exe -i http://localhost:8088/api/deliveries/999
```

Список заказов:

```powershell
curl.exe http://localhost:8088/api/orders
```

## Масштабирование и балансировка Traefik

**Не указывать `container_name` и не публиковать порт Delivery Service на хосте.** Иначе обычный `--scale` конфликтует с именами или портами. Compose создаст одинаковые Docker Labels для каждой реплики; Traefik обнаружит их автоматически.

```powershell
docker compose up -d --scale delivery-service=3
docker compose ps
.\scripts\test-loadbalance.ps1 -Count 20
```

Или вручную:

```powershell
1..20 | ForEach-Object { (Invoke-RestMethod 'http://localhost:8088/api/deliveries/instance').instanceId }
```

В ответах должны встречаться **различные `instanceId`** (Docker HOSTNAME / container ID). На реальном запуске числа могут распределяться неодинаково.

## Отказ одного экземпляра

```powershell
.\scripts\test-failover.ps1
```

Скрипт проверит наличие как минимум двух контейнеров `delivery-service`, остановит один, продолжит выполнять запросы через Traefik и в конце запустит остановленный контейнер. При краткой перестройке маршрутов возможна краткая задержка; тест показывает доступность **после** обнаружения отказа. Он не доказывает отказоустойчивость Order Service или самой БД.

Вручную:

```powershell
docker compose ps -q delivery-service
# docker stop <CONTAINER_ID>
1..12 | ForEach-Object { (Invoke-RestMethod 'http://localhost:8088/api/deliveries/1').servedBy }
# docker start <CONTAINER_ID>
```

Остановить и удалить контейнеры (данные PostgreSQL сохраняются в volume):

```powershell
docker compose down
```

Полностью удалить также содержимое учебной БД:

```powershell
docker compose down -v
```

## Сборка Maven без Docker

```powershell
mvn clean package -DskipTests
```

Для самостоятельного локального запуска сервисов потребуется работающий PostgreSQL, `DB_URL`, `DB_USER`, `DB_PASSWORD` для Order Service, а также `ORDER_SERVICE_BASE_URL` для Delivery Service. В Docker эти параметры задаются **только через Compose**, а не фиксируются в исходном Java-коде.

## Соответствие требованиям задания

| № | Требование | Реализация |
|---|---|---|
| 1 | Два Spring Boot сервиса | `order-service`, `delivery-service` |
| 2 | REST + межсервисный HTTP | `GET /api/deliveries/{id}` → `RestClient` → `GET /api/orders/{id}` |
| 3 | База данных и внешние настройки | PostgreSQL + JDBC; `DB_*`, `ORDER_SERVICE_BASE_URL` |
| 4 | Dockerfile и Compose | Два `Dockerfile`, корневой `compose.yaml` |
| 5 | Traefik Labels | `PathPrefix('/api/orders')` и `PathPrefix('/api/deliveries')`, внешний порт только у Traefik |
| 6 | Healthchecks и порядок | PostgreSQL `pg_isready`, Actuator `/actuator/health`, `depends_on: service_healthy` |
| 7 | Балансировка | `docker compose up -d --scale delivery-service=3`, HTTP `/api/deliveries/instance` |
| 8 | Остановка реплики | `docker stop ...` или `scripts/test-failover.ps1` |

## Рекомендуемые реальные скриншоты для отчёта

1. `docker compose ps`: здоровые сервисы + PostgreSQL + Traefik.
2. `GET /api/deliveries/1`: демонстрация RestClient, цены, статуса, двух `servedBy`.
3. `test-loadbalance.ps1`: несколько `instanceId` после `--scale`.
4. `test-failover.ps1`: запросы через Traefik продолжаются после остановки одного контейнера.
5. Если требуется: реальный скриншот завершения модуля «BaaS для АС сервиса доставки» в BootcampLabs.

Скриншоты **не сгенерированы искусственно**: их следует снять после запуска Docker Desktop на вашем ПК.

## Архитектурные ограничения и безопасность

- Это **локальная учебная** конфигурация: в `.env` демонстрационный пароль; замените его при эксплуатации вне учебной среды.
- Traefik читает Docker API через `/var/run/docker.sock:ro`. Даже read-only mount не даёт полной изоляции: доступ к Docker socket потенциально опасен. Для production используйте socket proxy и строгие политики доступа.
- Traefik доступен на `127.0.0.1:8088` без HTTPS и аутентификации: не публикуйте этот стенд в Интернет.
- Масштабируется только `Delivery Service`: отказ единственного Order Service, PostgreSQL или Traefik делает часть/всю систему недоступной. Это **не полная отказоустойчивость системы**, а демонстрация отказа одной реплики Delivery Service.
- Агрегат `total` для отменённых и незавершённых доставок — **стоимость позиций**, не фактическая выручка.
- Spring-скрипты и SQL при перезапуске не удаляют данные: вставки выполняются через `ON CONFLICT DO NOTHING`.
