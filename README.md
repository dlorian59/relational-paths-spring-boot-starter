# relational-paths-spring-boot-starter

<details name="readme-language">
<summary><picture><source media="(prefers-color-scheme: dark)" srcset="assets/lang-en-dark.svg"><img src="assets/lang-en-light.svg" width="42" height="26" alt="EN"></picture></summary>

**Version:** [0.1.0](docs/0.1.0/en.md) · [All versions](docs/index.md) · Java 17+ · Spring Boot 4.1.2 · [MIT](LICENSE)

Inject a bounded JOIN planner from application-owned metadata. The starter configures
`JoinPlanner` and `SearchLimits`; the application supplies a `Schema` bean and owns
business choices, database access and authorization. Core dependency: `relational-paths:0.3.0`.

## Situation: an order report with two addresses

An operator exports order IDs, customer names and delivery cities for their tenant.
Orders refer to both billing and shipping addresses. Picking an arbitrary shortest
route would produce the wrong report. Describe both relationships once, then explicitly
choose shipping for this report. The example builds SQL and parameters without opening a database.

## Run locally

Use full JDK 17 or newer and sibling checkouts of the core and this starter. Install the
core first because the starter resolves a normal Maven dependency, not a composite build.
These commands install locally; they do not require or imply a Maven Central release.

```sh
cd ../relational-paths
./gradlew publishToMavenLocal
cd ../relational-paths-spring-boot-starter
./gradlew build example publishToMavenLocal
```

In an existing Boot 4.1.2 application:

```kotlin
repositories { mavenLocal(); mavenCentral() }
dependencies {
    implementation("io.github.59alex:relational-paths-spring-boot-starter:0.1.0")
}
```

## Configure metadata and bounds

Copy the application-specific [ReportConfiguration](src/examples/java/example/reports/ReportConfiguration.java):
it declares approved columns and the three relationships as a `Schema` bean. It also
shows ordinary constructor injection into [OrderReport](src/examples/java/example/reports/OrderReport.java).
The starter does not scan entities or database tables. The complete
[Boot application](src/examples/java/example/reports/ReportApplication.java) loads the
starter using Boot's normal auto-configuration discovery, without component scanning its internals.

```properties
relational-paths.max-tables=32
relational-paths.max-relationships=64
relational-paths.max-edge-visits=64
```

| Setting | Default | Meaning |
| --- | --- | --- |
| `relational-paths.enabled` | `true` | Allow configuration when a Schema candidate exists |
| `relational-paths.max-tables` | `512` | Maximum logical roles; at least 1 |
| `relational-paths.max-relationships` | `8192` | Maximum relationships; zero is allowed |
| `relational-paths.max-edge-visits` | `8192` | Maximum examined edges per automatic search; zero is allowed |

No `Schema` bean means no planner. Multiple schemas require a `@Primary` candidate or
an application-owned planner; the starter never chooses a schema arbitrarily. Existing
`JoinPlanner` and `SearchLimits` beans take precedence. A custom planner needs no extra
limits bean. Set `relational-paths.enabled=false` to disable all starter configuration.
Invalid effective bounds or an oversized schema fail startup; an exhausted search bound
throws `PlanningException` at query planning time. Custom limits replace property limits.

## Business code and result

`OrderReport` precomputes its immutable plan with the approved shipping route. This
method-body excerpt is from the [annotated runnable source](src/examples/java/example/reports/OrderReport.java):

```java
// Choose shipping explicitly; a billing report would choose the other relationship.
// Явно выбираем доставку; отчёт по платёжному адресу выбрал бы другую связь.
var plan = planner.plan("orders", List.of("customer", "address"),
    Map.of("address", List.of("shipping_address")));
var query = SqlRenderer.select(plan,
    List.of(new ColumnRef("orders", "id"), new ColumnRef("customer", "name"),
        new ColumnRef("address", "city")),
    List.of(new Filter(new ColumnRef("orders", "tenant_id"), Filter.Operator.EQ, 42L),
        new Filter(new ColumnRef("customer", "name"), Filter.Operator.EQ, "Alice")));
```

The demonstration first proves that automatic address selection is ambiguous, then
prints the deliberate shipping plan and SQL. Selected output:

```text
Address choices: [[billing_address], [shipping_address]]
Bindings: [42, Alice]
```

The SQL joins `orders.customer_id` to `customer.id` and `orders.shipping_id` to
`address.id`, with matching `tenant_id` in both joins. It filters the root tenant and
customer name through `?` parameters. A value such as `Alice' OR 1=1 --` remains a
binding and never enters the SQL text. Obtain the tenant ID from authenticated server
context. This sample tenant filter is an application policy, not automatic row-level security.

## Capabilities and code

| Capability | Runnable or implementation source |
| --- | --- |
| Application-owned schema and tenant-aware composite keys | [ReportConfiguration](src/examples/java/example/reports/ReportConfiguration.java) |
| Injection and auto-configuration discovery | [ReportApplication](src/examples/java/example/reports/ReportApplication.java) |
| Explicit ambiguity resolution, reusable plan, filters and bindings | [OrderReport](src/examples/java/example/reports/OrderReport.java) |
| Default bounds, property binding, opt-out, bean precedence and schema selection | [Auto-configuration](src/main/java/io/github/alex59/relationalpaths/spring/RelationalPathsAutoConfiguration.java), [contract tests](src/test/java/io/github/alex59/relationalpaths/spring/RelationalPathsAutoConfigurationTest.java) |
| Properties metadata for IDE completion | [Properties](src/main/java/io/github/alex59/relationalpaths/spring/RelationalPathsProperties.java), [example settings](src/examples/resources/application.properties) |

## Boundaries

`SqlRenderer.select` stays a static core API: it has no configuration or lifecycle to
manage as a bean. No JDBC connection, ORM, entity scanning, migrations or transaction
manager is installed. INNER JOIN can exclude unmatched rows or multiply rows across
to-many relationships. Metadata is immutable; schema changes require replacing the
planner rather than mutating a shared instance. The host controls JDBC binding,
authorization, transactions and snapshot consistency. Search limits bound metadata
traversal, not database execution time or returned rows.

`./gradlew build` checks configuration/backoff and report behavior, builds JARs and runs
the Boot example. `-PtestJavaVersion=17` selects an installed JDK; the source/bytecode
baseline stays 17. Newer JDKs do not enable separate library features. The example is
a reusable integration scenario, not a claim of a production deployment or SQL execution.

[Core library](https://github.com/59Alex/relational-paths) · [MIT license](LICENSE) · [Third-party notices](THIRD_PARTY_NOTICES.md)

</details>

<details name="readme-language" open>
<summary><picture><source media="(prefers-color-scheme: dark)" srcset="assets/lang-ru-dark.svg"><img src="assets/lang-ru-light.svg" width="42" height="26" alt="RU"></picture></summary>

**Версия:** [0.1.0](docs/0.1.0/ru.md) · [Все версии](docs/index.md) · Java 17+ · Spring Boot 4.1.2 · [MIT](LICENSE)

Внедряйте планировщик JOIN с ограничениями поиска по метаданным приложения. Стартер
настраивает `JoinPlanner` и `SearchLimits`; приложение предоставляет `Schema` и управляет
бизнес-правилами, доступом и выполнением запросов. Основная библиотека — `relational-paths:0.3.0`.

## Ситуация: отчёт по заказам с двумя адресами

Оператор выгружает ID заказов, имена клиентов и города доставки своего арендатора.
У заказа есть платёжный адрес и адрес доставки. Произвольный выбор кратчайшего пути
дал бы неверный отчёт. Опишите обе связи один раз и явно выберите доставку для этого
отчёта. Пример строит SQL и параметры, не открывая соединение с базой данных.

## Локальный запуск

Нужны полный JDK 17 или новее и соседние копии основной библиотеки и стартера.
Сначала установите основную библиотеку: стартер подключает обычную Maven-зависимость,
без composite build. Команды устанавливают артефакты локально и не подразумевают
наличие релиза в Maven Central.

```sh
cd ../relational-paths
./gradlew publishToMavenLocal
cd ../relational-paths-spring-boot-starter
./gradlew build example publishToMavenLocal
```

В существующем приложении Boot 4.1.2:

```kotlin
repositories { mavenLocal(); mavenCentral() }
dependencies {
    implementation("io.github.59alex:relational-paths-spring-boot-starter:0.1.0")
}
```

## Настройка метаданных и лимитов

Возьмите за основу [ReportConfiguration](src/examples/java/example/reports/ReportConfiguration.java):
здесь разрешённые колонки и три связи оформлены как bean `Schema`. Там же показано
обычное внедрение через конструктор [OrderReport](src/examples/java/example/reports/OrderReport.java).
Стартер не сканирует сущности и таблицы базы. Полное
[Boot-приложение](src/examples/java/example/reports/ReportApplication.java) обнаруживает
стартер штатным механизмом автоконфигурации, без сканирования его внутренних классов.

```properties
relational-paths.max-tables=32
relational-paths.max-relationships=64
relational-paths.max-edge-visits=64
```

| Настройка | По умолчанию | Значение |
| --- | --- | --- |
| `relational-paths.enabled` | `true` | Разрешить конфигурацию, когда определена Schema |
| `relational-paths.max-tables` | `512` | Максимум логических ролей, не меньше 1 |
| `relational-paths.max-relationships` | `8192` | Максимум связей, допускается ноль |
| `relational-paths.max-edge-visits` | `8192` | Максимум просмотренных рёбер за автоматический поиск, допускается ноль |

Без `Schema` планировщик не создаётся. Для нескольких схем нужен кандидат `@Primary`
или собственный планировщик: произвольного выбора схемы нет. Пользовательские beans
`JoinPlanner` и `SearchLimits` имеют приоритет. Собственному планировщику дополнительный
bean лимитов не нужен. `relational-paths.enabled=false` отключает конфигурацию стартера.
Некорректные действующие лимиты и слишком большая схема останавливают запуск;
исчерпание лимита поиска вызывает `PlanningException` при планировании. Собственный
`SearchLimits` заменяет ограничения из properties.

## Бизнес-код и результат

`OrderReport` заранее создаёт неизменяемый план с разрешённым маршрутом доставки.
Фрагмент для тела метода из [комментированного запускаемого примера](src/examples/java/example/reports/OrderReport.java):

```java
// Choose shipping explicitly; a billing report would choose the other relationship.
// Явно выбираем доставку; отчёт по платёжному адресу выбрал бы другую связь.
var plan = planner.plan("orders", List.of("customer", "address"),
    Map.of("address", List.of("shipping_address")));
var query = SqlRenderer.select(plan,
    List.of(new ColumnRef("orders", "id"), new ColumnRef("customer", "name"),
        new ColumnRef("address", "city")),
    List.of(new Filter(new ColumnRef("orders", "tenant_id"), Filter.Operator.EQ, 42L),
        new Filter(new ColumnRef("customer", "name"), Filter.Operator.EQ, "Alice")));
```

Сначала пример проверяет неоднозначность автоматического выбора адреса, затем печатает
осознанно выбранный план доставки и SQL. Часть вывода:

```text
Address choices: [[billing_address], [shipping_address]]
Bindings: [42, Alice]
```

SQL соединяет `orders.customer_id` с `customer.id` и `orders.shipping_id` с `address.id`;
в обоих соединениях участвует `tenant_id`. Фильтры арендатора и имени клиента используют
параметры `?`. Даже `Alice' OR 1=1 --` остаётся значением параметра и не попадает в SQL.
ID арендатора берётся из проверенного серверного контекста. Фильтр арендатора — правило
приложения, а не автоматически предоставляемая защита строк.

## Возможности и код

| Возможность | Запускаемый пример или реализация |
| --- | --- |
| Схема приложения и составные ключи с арендатором | [ReportConfiguration](src/examples/java/example/reports/ReportConfiguration.java) |
| Внедрение и штатное обнаружение автоконфигурации | [ReportApplication](src/examples/java/example/reports/ReportApplication.java) |
| Явный маршрут, переиспользуемый план, фильтры и параметры | [OrderReport](src/examples/java/example/reports/OrderReport.java) |
| Лимиты, properties, отключение, приоритет beans и выбор схемы | [Автоконфигурация](src/main/java/io/github/alex59/relationalpaths/spring/RelationalPathsAutoConfiguration.java), [проверки контрактов](src/test/java/io/github/alex59/relationalpaths/spring/RelationalPathsAutoConfigurationTest.java) |
| Метаданные properties для подсказок IDE | [Properties](src/main/java/io/github/alex59/relationalpaths/spring/RelationalPathsProperties.java), [настройки примера](src/examples/resources/application.properties) |

## Границы применения

`SqlRenderer.select` остаётся статическим API основной библиотеки: у него нет настроек
или жизненного цикла для Spring bean. Стартер не добавляет JDBC, ORM, сканирование сущностей,
миграции или менеджер транзакций. `INNER JOIN` может исключать строки и размножать их
при связи один-ко-многим. Метаданные неизменяемы: при новой схеме создайте новый
планировщик. Приложение отвечает за JDBC-параметры, права, транзакции и согласованность
снимков. Лимиты ограничивают обход метаданных, а не время SQL или число строк результата.

`./gradlew build` проверяет конфигурацию, приоритет beans и поведение отчёта, собирает
JAR и запускает Boot-пример. `-PtestJavaVersion=17` выбирает установленный JDK;
база исходников и байткода остаётся Java 17. Новые JVM не включают отдельные возможности
библиотеки. Это переиспользуемый сценарий интеграции, без заявления о внедрении в production
или выполнении SQL.

[Основная библиотека](https://github.com/59Alex/relational-paths) · [MIT](LICENSE) · [Сторонние лицензии](THIRD_PARTY_NOTICES.md)

</details>
