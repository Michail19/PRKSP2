# Практическая работа №4 — RSocket (АС Поставщиков / Darkstore)

Разработаны **два независимых приложения** (сервер и клиент) на **Spring Boot 4.1.2** и **Project Reactor**. Используется порт **TCP 7000**, без REST, HTTP и базы данных. Модели предметной области взяты из выполненного BootcampLabs: `darkstore`, `good`, `delivery`, `goods_list`.

> Реальные пароли от Kintsugi / Pangolin / Keycloak не требуются, проект не использует корпоративные сервисы. Все 4 CSV буквально перенесены из практических №2–3 и содержат 5 дарксторов, 10 товаров, 20 доставок и 30 позиций.

## Требования

- JDK **17 или новее** (рекомендуется JDK 21);
- Maven **3.9+** (или встроенный Maven IntelliJ IDEA);
- Интернет для **первой** загрузки зависимостей Maven;
- Свободный локальный TCP-порт **7000**.

## Структура

```text
Practice4_DarkstoreRSocket/
├── pom.xml                       # родительский multi-module Maven
├── common/                       # общие модели клиента и сервера
│   └── src/main/java/.../ApiModels.java, CsvParser.java
├── server/
│   ├── src/main/java/.../RSocketServerApplication.java
│   ├── src/main/java/.../RSocketCatalogController.java
│   ├── src/main/java/.../CsvCatalog.java
│   ├── src/main/resources/application.properties
│   ├── src/main/resources/data/*.csv
│   └── src/test/java/.../CatalogSmokeCheck.java
├── client/
│   ├── src/main/java/.../RSocketClientApplication.java
│   └── src/main/resources/application.properties
└── evidence/
    └── data_smoke_check.txt
```

## Запуск в IntelliJ IDEA — самый простой

1. Распакуйте архив, откройте **корневой `pom.xml`** как Maven-проект и дождитесь импорта `common`, `server`, `client`.
2. Запустите класс **`ru.mirea.practice4.server.RSocketServerApplication`** — сервер остаётся запущенным и слушает TCP **7000**.
3. Не останавливая сервер, запустите класс **`ru.mirea.practice4.client.RSocketClientApplication`** — клиент выполнит все четыре сценария и завершит работу.
4. Смотрите **две консоли одновременно**. В консоли сервера видны поступившие запросы / события; в консоли клиента — объекты и время их получения.

## Быстрый запуск в Windows

В корне проекта есть три файла:

1. `build.cmd` — сборка всех модулей (нужен `mvn` в PATH).
2. `start-server.cmd` — первый терминал, запускает RSocket TCP-сервер.
3. `start-client.cmd` — второй терминал, запускает демонстрационный клиент.

**Сначала собираем, затем запускаем сервер, и только после его запуска — клиент.**

## Запуск из терминала

Из корня проекта сначала собрать все три модуля:

```bash
mvn clean package -DskipTests
```

В терминале №1:

```bash
java -jar server/target/server-1.0.0.jar
```

В терминале №2 (пока работает сервер):

```bash
java -jar client/target/client-1.0.0.jar
```

Остановить сервер: `Ctrl+C`.

При занятом порте измените `spring.rsocket.server.port` в `server/src/main/resources/application.properties`, а клиент запускайте с соответствующим портом (`-Drsocket.port=7001` **до** `-jar`).

## Четыре RSocket-модели

| Модель | Маршрут сервера | Сообщение клиента | Ответ сервера | Вызываемый метод |
|---|---|---|---|---|
| **Request-Response** | `goods.get` | `GoodRequest(1)` | `Mono<Good>` | `retrieveMono(Good.class)` |
| **Request-Stream** | `goods.stream` | `GoodsStreamRequest(5,350)` | `Flux<Good>` (пауза **350 мс**) | `retrieveFlux(Good.class)` |
| **Fire-and-Forget** | `events.report` | `ClientEvent(...)` | отсутствует | `send()` |
| **Request-Channel** | `goods.quote.channel` | поток `Flux<QuoteRequest>` | поток `Flux<QuoteReply>` | `data(flux, QuoteRequest.class).retrieveFlux(...)` |

Дополнительный **Request-Response** маршрут `deliveries.summary` использует сразу четыре таблицы через связи `delivery -> darkstore` и `goods_list -> good`.

## Последовательность демо и ожидаемые результаты

### 1. Request-Response

- `goods.get` с `id=1` возвращает `Пицца "Маргарита"`, цена `599.99`;
- `goods.get` с `id=999` вызывает обработанную ошибку: **клиент не завершается**;
- `deliveries.summary` с `id=1` возвращает **2 позиции** и **40399.28 руб.**, `darkstoreId=3`.

### 2. Request-Stream

- Один запрос `GoodsStreamRequest(5, 350)` → пять товаров **приходят порциями**, каждый примерно через 350 мс.
- Клиент печатает `[CLIENT][STREAM] +350 мс`, `+700 мс`, ... — реальные интервалы зависят от планировщика ОС.
- Клиент использует **Project Reactor**: `doOnNext → filter(price >= 200) → map(карточка) → doOnNext`.
- Среди первых 5 товаров цена супа `199.99` ниже порога, поэтому в потоке «карточек» остаётся **4** товара, а в логе `[STREAM]` видны все **5**.

### 3. Fire-and-Forget

- Клиент отправляет событие `GOOD_VIEW` без ожидания бизнес-ответа сервера.
- В **консоли сервера** появляется `[SERVER] FIRE_AND_FORGET: ...`.
- `send().block()` ждёт локального завершения отправки (это **не** подтверждение сохранения события на сервере).

### 4. Request-Channel

- Клиент отправляет **поток** из пяти `QuoteRequest` (по одному примерно через 180 мс).
- Сервер **в процессе приёма** отвечает отдельными `QuoteReply`, каждое примерно после 220 мс обработки.
- Корректные: `1 × 2 → 1199.98`, `2 × 3 → 749.97`, `6 × 4 → 1999.96`.
- Ошибочные: `goodId=999` и `quantity=0` возвращаются с `status=ERROR`, не обрывая весь канал.
- На выводе различимы `[CHANNEL ->]` (клиент → сервер) и `[CHANNEL <-]` (сервер → клиент).

## Обработка ошибок

- Нет товара `999`: `Mono.error(new IllegalArgumentException(...))` на сервере; `onErrorResume(ex -> Mono.empty())` и лог `[CLIENT][ERROR]` на клиенте.
- Некорректное количество в Channel: сервер возвращает `QuoteReply(status="ERROR", ...)`, не завершая поток.
- Неверные границы `limit` или `delayMillis`: сервер возвращает `Flux.error`.
- Сервер выключен: запросы завершаются ошибкой подключения / таймаутом, выводится клиентская ошибка.

## Как сделать скриншоты для отчёта

1. **Request-Response и обработка ошибок:** клиентские строки `[CLIENT][RESPONSE]`, `[CLIENT][ERROR]`, `[CLIENT][DELIVERY]`.
2. **Request-Stream и постепенность:** строки `[CLIENT][STREAM]` с разным временем `+... мс` и карточки `[CLIENT][REACTOR]`.
3. **Fire-and-Forget и Channel:** две консоли IntelliJ IDEA; серверный `[SERVER] FIRE_AND_FORGET`, клиентские `[CHANNEL ->]` и `[CHANNEL <-]`.
4. **BootcampLabs:** отдельный **реальный** скриншот прохождения модуля «Front-end АС Поставщиков». Его нельзя получить из этого проекта автоматически.

Скриншоты из работающей IDE нужны настоящие, в архиве не создаются фиктивные изображения.

## Вопросы на защиту (кратко)

- **RSocket** — бинарный протокол реактивного взаимодействия, работает здесь поверх TCP.
- **Mono** — 0 или 1 элемент; **Flux** — 0..N элементов.
- **Request-Response** — запрос и один ответ; **Request-Stream** — запрос и поток ответов.
- **Fire-and-Forget** — запрос без бизнес-ответа; **Channel** — двусторонний обмен потоками.
- **`@MessageMapping`** — адресация RSocket-обработчика Spring по маршруту.
- **`RSocketRequester`** — клиент для выполнения запросов и сериализации DTO.
- **`delayElements`** — задержка между элементами потока без `Thread.sleep()` в обработке.
- **Project Reactor** — `Flux`, `Mono`, `filter`, `map`, `doOnNext`, `onErrorResume`.
- **Ошибки** — `Mono.error` / `Flux.error` и `onErrorResume`, либо `status=ERROR` для элемента канала.
- **`block` / `blockLast`** — допустимы в учебном консольном клиенте, но не должны использоваться в реактивных обработчиках сервера.

## Проверка и её ограничения

Проверка сборки зависимостей Spring Boot / RSocket из этого окружения **не проводилась**: тут нет Maven и сетевого доступа к Maven Central. `common` и чистая Java-часть `CsvCatalog` **скомпилированы `javac`**, а независимый smoke-тест выполнился успешно. См. `evidence/data_smoke_check.txt`. Для подтверждения реального сетевого обмена запустите оба модуля у себя по инструкции и сделайте скриншоты.

## Источники по API

- https://docs.spring.io/spring-boot/reference/messaging/rsocket.html
- https://docs.spring.io/spring-framework/reference/rsocket.html
- https://spring.io/blog/2020/04/06/getting-started-with-rsocket-spring-boot-channels
