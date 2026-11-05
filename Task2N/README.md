# Практическая работа №2 — обработка файлов обмена АС Поставщиков (Java NIO)

Готовый учебный Maven-проект (JDK 17+). Входные данные взяты из SQL-примеров
BootcampLabs: `darkstore`, `good`, `delivery`, `goods_list`.

Вместо независимых заданий старой методички (работа с `Path`/`Files`,
`FileChannel`/`ByteBuffer`, контрольная сумма, `WatchService`) реализован
**один конвейер** в соответствии с новым заданием:

```
CSV появляется в data/incoming
          ↓
  Path/Files → поиск по .csv
          ↓
  чтение (включая FileChannel/ByteBuffer)
          ↓
  проверка структуры и обработка записей
          ↓
  SHA-256 (MessageDigest)
          ↓
  Files.move(...)
          ↓
  data/processed
```

## 1. Что есть в проекте

```
Practice2_DarkstoreNIO/
├── pom.xml
├── README.md
├── src/main/java/ru/mirea/practice2/
│   └── DarkstoreFileExchangeApp.java
├── data/
│   ├── incoming/
│   │   ├── darkstore.csv       (5 записей)
│   │   ├── good.csv            (10 записей)
│   │   ├── delivery.csv        (20 записей)
│   │   └── goods_list.csv      (30 записей)
│   └── processed/
├── examples/
│   └── delivery_new.csv       (1 дополнительная тестовая запись)
└── evidence/
    ├── initial_run.txt
    └── watchservice_run.txt
```

**Важно:** В SQL-запросах пользователя ID не указываются явно. Для CSV
предполагается первоначальное заполнение *пустой* БД, поэтому ID присвоены
в порядке INSERT начиная с 1. Если таблицы ранее заполнялись, фактические
ID БД могут оказаться другими. При этом сама БД не используется и не изменяется.

### Структура CSV

- `darkstore.csv`: `id,address`
- `good.csv`: `id,name,price`
- `delivery.csv`: `id,id_darkstore,datetime_delivery,status`
- `goods_list.csv`: `id_good,id_delivery,count`

**CSV с запятыми и кавычками поддерживается**. Например:

```csv
id,address
1,"улица Солнечная, дом 10"
```

```csv
id,name,price
1,"Пицца ""Маргарита""",599.99
```

Парсер допускает экранирование кавычек по CSV-правилам, но не поддерживает
многострочные поля внутри кавычек (для учебного набора они не нужны).
Кодировка: UTF-8. Время доставки: `yyyy-MM-dd HH:mm:ss`.

### Что именно делает обработка

1. Проверяет заголовок CSV и определяет тип записи.
2. Проверяет типы полей, непустые поля, положительные ID, цены и количество,
   допустимые статусы доставки, корректные даты, отсутствие дубликатов ключей
   внутри одного файла.
3. Показывает статистику: количество дарксторов / товаров / доставок /
   позиций состава; для `goods_list` — сумму `count`; для `delivery` —
   распределение по статусам.
4. Вычисляет SHA-256 исходного файла.
5. Перемещает файл в `processed`. При совпадении имени добавляет суффикс `_2` и т. д.

Набор данных передаётся и проверяется **без подключения к PostgreSQL, Pangolin,
Kintsugi или Keycloak**. Целостность внешних ключей *между разными* CSV-файлами
приложение не проверяет; для этого потребовался бы общий контекст импорта
или база данных, чего новое задание не требует.

## 2. Как запустить в IntelliJ IDEA

1. `File → Open` → выбрать каталог `Practice2_DarkstoreNIO`.
2. Открыть проект как Maven-проект, указать JDK 17 или 21.
3. Открыть `DarkstoreFileExchangeApp.java`.
4. Запустить метод `main`.
5. Убедиться, что **Working directory — корень проекта**, содержащий `pom.xml`.
6. В консоли появятся сведения о каталогах и 4 файлах, `Entity`, `Size`,
   `SHA-256`, `Records`, `Status: processed`.
7. Четыре CSV окажутся в `data/processed`. Программа **не завершится**:
   она будет ожидать новые файлы через `WatchService`.
8. Пока программа продолжает работать, скопировать
   `examples/delivery_new.csv` → `data/incoming/delivery_new.csv`.
9. Убедиться в сообщении `Обнаружен новый файл: delivery_new.csv` и
   последующем `Status: processed`.
10. Остановить `Stop` (в IDE) или `Ctrl+C` в терминале.

Отдельный режим **только начальной обработки**: запустить с аргументом
`--once`. Программа обработает имеющиеся файлы и завершится без ожидания событий.

## 3. Команды терминала

После открытия терминала **в корне проекта**:

```bash
mvn -q -DskipTests compile
java -Dfile.encoding=UTF-8 -cp target/classes ru.mirea.practice2.DarkstoreFileExchangeApp
```

Только начальная обработка:

```bash
java -Dfile.encoding=UTF-8 -cp target/classes ru.mirea.practice2.DarkstoreFileExchangeApp --once
```

В Windows PowerShell, во время работы WatchService:

```powershell
Copy-Item examples\delivery_new.csv data\incoming\delivery_new.csv
```

В Linux/macOS:

```bash
cp examples/delivery_new.csv data/incoming/delivery_new.csv
```

> После первого запуска исходные 4 CSV перенесены в `processed`. Чтобы
> повторить демонстрацию начальной обработки, перенесите их обратно в
> `incoming` (или распакуйте архив заново).

## 4. Соответствие новому заданию

| Пункт | Где реализован |
|---|---|
| 1. Каталоги, список, размеры, даты | `createDirectories()`, `printDirectoryContents()` |
| 2. Поиск по расширению | `findCsvFiles()`, `isCsv()` |
| 3. Чтение и обработка | `validateAndSummarize()`, `parseCsvRow()` |
| 4. Перемещение в processed | `processFile()`, `Files.move()` |
| 5. FileChannel / ByteBuffer | `readUsingFileChannel()` для первого CSV |
| 6. SHA-256 | `calculateSha256()` |
| 7. WatchService | `watchIncomingDirectory()` |

## 5. Что приложить к отчёту

*Скриншоты снимите на своей машине в IntelliJ IDEA или терминале.*
`evidence/*.txt` — журналы **проверочного запуска**, а не графические
скриншоты вашего компьютера.

1. **Каталоги и список файлов:** виден блок `Содержимое data/incoming`,
   имена, размеры и время изменения четырёх CSV.
2. **Чтение, обработка, SHA-256:** на экране видны `NIO: чтение через
   FileChannel + ByteBuffer`, `Records`, `SHA-256`, `Status: processed`.
3. **WatchService:** запущенный наблюдатель, строка
   `Обнаружен новый файл: delivery_new.csv` и итог обработки.
4. **BootcampLabs:** отдельный реальный скриншот **прохождения** модуля
   «База данных АС Поставщиков» из вашего аккаунта. Изображение с пустыми
   полями Kintsugi/Pangolin/Keycloak само по себе не подтверждает прохождение.

Дополнительно можно показать окно проводника/IDE с `data/processed`
(содержит 4, а после события — 5 файлов).

## 6. Ограничения

- `WatchService` может присылать несколько событий на одну операцию и не
  гарантирует абсолютной доставки при переполнении очереди ОС.
- После CREATE файл может ещё записываться. Реализовано короткое ожидание
  стабильного размера и времени изменения, но для гарантированной передачи
  файлов лучше сначала писать `.tmp`, а затем переименовывать в `.csv`.
- При ошибке проверки исходный CSV остаётся в `incoming`.
- Работа с CSV — стандартная Java без внешних зависимостей и БД.
- Чтение CSV разбито по физическим строкам: переносы строк внутри кавычек
  не поддерживаются.
