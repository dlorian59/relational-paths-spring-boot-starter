# relational-paths-spring-boot-starter

<details name="readme-language">
<summary><picture><source media="(prefers-color-scheme: dark)" srcset="assets/lang-en-dark.svg"><img src="assets/lang-en-light.svg" width="42" height="26" alt="EN"></picture></summary>

**0.1.0 · Java 17+ · Spring Boot 4.1.2 · MIT** · [Versions / Версии](docs/index.md)

Add metadata-driven reports to an existing Spring application. Declare the tables and relationships allowed for reporting; inject RelationalQueries and select business fields. The starter supplies the bounded planner and query facade. Your service receives parameterized SQL and an explanation of its joins.

Example: return order IDs, customer names and shipping cities for an authenticated tenant. Billing and shipping are both valid paths; the report chooses shipping explicitly. You write the business metadata and row mapping, not planner wiring.

## Get started in an existing project

Use an existing Boot 4.1.2 application with Java 17+. This example uses Spring MVC and blocking JDBC, not WebFlux. Boot manages framework/driver versions. The library coordinates below are not yet published to Maven Central: your configured repository must supply the artifacts; local preparation is described in CONTRIBUTING.

Maven:
```xml
<dependency>
  <groupId>io.github.59alex</groupId>
  <artifactId>relational-paths-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
<dependency>
  <groupId>org.postgresql</groupId>
  <artifactId>postgresql</artifactId>
  <scope>runtime</scope>
</dependency>
```

Gradle Kotlin:
```kotlin
dependencies {
    implementation("io.github.59alex:relational-paths-spring-boot-starter:0.1.0")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("org.postgresql:postgresql")
}
```

Keep your existing datasource, authentication and migrations. Add the three business classes below under the existing component scan:

```text
src/main/java/example/reports/
  ReportConfiguration.java     # Schema bean
  OrderReportService.java      # query + JDBC mapping
  OrderReportController.java   # GET /reports/orders
src/main/resources/application.yml
```

Search bounds are optional; no schema mutation is enabled:

```yaml
relational-paths:
  max-tables: 32
  max-relationships: 64
  max-edge-visits: 64
```

Supply a Schema bean using [ReportConfiguration.java](examples/order-report/src/main/java/example/reports/ReportConfiguration.java). It declares orders, customer and address columns with tenant-aware composite relationships.

The business query in [OrderReportService.java](examples/order-report/src/main/java/example/reports/OrderReportService.java):

```java
var query = queries.from("orders")
    .select("orders.id", "customer.name", "address.city")
    .via("address", "shipping")
    .whereEqual("orders.tenant_id", authenticatedTenantId)
    .whereEqual("customer.name", customerName).build();
```

The complete service reuses an immutable template and passes query.sql()/query.bindings() to JdbcTemplate. Copy the ready controller:

```java
package example.reports;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class OrderReportController {
    private final OrderReportService reports;
    public OrderReportController(OrderReportService reports) { this.reports = reports; }

    @GetMapping("/reports/orders")
    public List<OrderReportService.OrderRow> orders(
            // Your verified authentication layer sets this server-side attribute; no query/header fallback.
            // Проверенный слой авторизации задаёт серверный атрибут; подмены из query/header нет.
            @RequestAttribute("authenticatedTenantId") long tenantId,
            @RequestParam("customer") String customer) {
        return reports.find(tenantId, customer);
    }
}
```

Your existing verified authentication layer supplies authenticatedTenantId as a server-side request attribute. Without it the request is rejected; query parameters and headers cannot replace it. A request for Alice from tenant 42 returns only that tenant’s rows, e.g. [{"orderId":1,"customer":"Alice","shippingCity":"Perm"}].

[Full configuration + service + controller walkthrough](docs/0.1.0/integration.en.md) · [Source](examples/order-report/src/main/java/example/reports/) · [Maven consumer](examples/order-report/pom.xml)

## Capabilities

- `queries.from("orders").select("customer.name").build()` — finds the required JOIN without repeating a target list.
- `.whereEqual("customer.name", customerName)` — keeps values separate from SQL, including injection strings and null handling.
- `.via("address", "shipping")` — resolves the business choice; equal-cost alternatives otherwise fail explicitly.
- `query.explain()` — shows selected relationship IDs and equality predicates; see the report example in the guide.
- `relational-paths.max-edge-visits=64` — caps automatic traversal; hitting the bound raises an error, never a partial plan.

## Defaults and boundaries

Defaults: enabled=true, 512 roles, 8192 relationships and 8192 edge visits. Provide one Schema or a @Primary candidate. Existing JoinPlanner, SearchLimits and RelationalQueries beans take precedence; automatic facade creation needs one resolvable planner. Metadata changes require a new planner. No AOP, entity scan, schema initialization or database connection is added by the starter.

The application owns permissions, tenant rules, JDBC pooling, transactions and execution/row limits. INNER JOIN may exclude or multiply rows. Graph limits do not cap SQL runtime or result size. Java 17 is the baseline; supported 21/25 and optional 27 use the same API.

[Guide](docs/0.1.0/integration.en.md) · [API](docs/0.1.0/reference.en.md) · [CONTRIBUTING](CONTRIBUTING.md) · [MIT](LICENSE)

</details>

<details name="readme-language" open>
<summary><picture><source media="(prefers-color-scheme: dark)" srcset="assets/lang-ru-dark.svg"><img src="assets/lang-ru-light.svg" width="42" height="26" alt="RU"></picture></summary>

**0.1.0 · Java 17+ · Spring Boot 4.1.2 · MIT** · [Versions / Версии](docs/index.md)

Добавьте отчёты по метаданным в существующее Spring-приложение. Опишите разрешённые таблицы и связи; внедрите RelationalQueries и выбирайте бизнес-поля. Стартер предоставит планировщик с лимитами и фасад запросов. Сервис получит SQL с параметрами и объяснение соединений.

Пример: вернуть ID заказов, имена клиентов и города доставки для проверенного арендатора. Платёжный адрес и доставка — разные допустимые пути; отчёт явно выбирает доставку. Вы описываете бизнес-метаданные и результат строки, а не обвязку планировщика.

## Подключение к существующему проекту

Нужно существующее приложение Boot 4.1.2 с Java 17+. Пример использует Spring MVC и блокирующий JDBC, не WebFlux. Версиями framework/драйвера управляет Boot. Координаты библиотеки пока не опубликованы в Maven Central: артефакты должен предоставлять ваш репозиторий; локальная подготовка описана в CONTRIBUTING.

Maven:
```xml
<dependency>
  <groupId>io.github.59alex</groupId>
  <artifactId>relational-paths-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
<dependency>
  <groupId>org.postgresql</groupId>
  <artifactId>postgresql</artifactId>
  <scope>runtime</scope>
</dependency>
```

Gradle Kotlin:
```kotlin
dependencies {
    implementation("io.github.59alex:relational-paths-spring-boot-starter:0.1.0")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("org.postgresql:postgresql")
}
```

Сохраните существующие datasource, авторизацию и миграции. Добавьте три бизнес-класса в область сканирования приложения:

```text
src/main/java/example/reports/
  ReportConfiguration.java     # Schema bean
  OrderReportService.java      # query + JDBC mapping
  OrderReportController.java   # GET /reports/orders
src/main/resources/application.yml
```

Лимиты поиска необязательны; изменение схемы не включается:

```yaml
relational-paths:
  max-tables: 32
  max-relationships: 64
  max-edge-visits: 64
```

Предоставьте Schema bean через [ReportConfiguration.java](examples/order-report/src/main/java/example/reports/ReportConfiguration.java). Он объявляет колонки orders, customer и address со связями по составным ключам, включающим арендатора.

Бизнес-запрос в [OrderReportService.java](examples/order-report/src/main/java/example/reports/OrderReportService.java):

```java
var query = queries.from("orders")
    .select("orders.id", "customer.name", "address.city")
    .via("address", "shipping")
    .whereEqual("orders.tenant_id", authenticatedTenantId)
    .whereEqual("customer.name", customerName).build();
```

Полный сервис переиспользует неизменяемый шаблон и передаёт query.sql()/query.bindings() в JdbcTemplate. Готовый контроллер:

```java
package example.reports;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class OrderReportController {
    private final OrderReportService reports;
    public OrderReportController(OrderReportService reports) { this.reports = reports; }

    @GetMapping("/reports/orders")
    public List<OrderReportService.OrderRow> orders(
            // Your verified authentication layer sets this server-side attribute; no query/header fallback.
            // Проверенный слой авторизации задаёт серверный атрибут; подмены из query/header нет.
            @RequestAttribute("authenticatedTenantId") long tenantId,
            @RequestParam("customer") String customer) {
        return reports.find(tenantId, customer);
    }
}
```

Существующий проверенный слой авторизации задаёт authenticatedTenantId как серверный request attribute. Без него запрос отклоняется; query-параметры и заголовки не подменяют атрибут. Запрос Alice от арендатора 42 возвращает только его строки, например [{"orderId":1,"customer":"Alice","shippingCity":"Perm"}].

[Полный разбор конфигурации, сервиса и контроллера](docs/0.1.0/integration.ru.md) · [Source](examples/order-report/src/main/java/example/reports/) · [Maven consumer](examples/order-report/pom.xml)

## Возможности

- `queries.from("orders").select("customer.name").build()` — находит нужный JOIN без повторного списка целей.
- `.whereEqual("customer.name", customerName)` — отделяет значения от SQL, включая строки инъекции и обработку null.
- `.via("address", "shipping")` — разрешает бизнес-выбор; без него равные маршруты явно отклоняются.
- `query.explain()` — показывает ID выбранных связей и равенства; пример отчёта разобран в руководстве.
- `relational-paths.max-edge-visits=64` — ограничивает автоматический обход; превышение вызывает ошибку, а не частичный план.

## Настройки и границы

По умолчанию: enabled=true, 512 ролей, 8192 связи и 8192 посещения рёбер. Нужна одна Schema либо @Primary. Собственные JoinPlanner, SearchLimits и RelationalQueries имеют приоритет; автоматическому фасаду нужен один разрешимый планировщик. Изменение метаданных требует нового планировщика. Стартер не добавляет AOP, сканирование сущностей, инициализацию схемы и соединение с БД.

Приложение управляет правами, арендатором, JDBC-пулом, транзакциями, временем выполнения и числом строк. INNER JOIN может исключать и размножать строки. Лимиты графа не ограничивают время SQL и размер результата. База — Java 17; поддерживаемые 21/25 и optional 27 используют тот же API.

[Руководство](docs/0.1.0/integration.ru.md) · [API](docs/0.1.0/reference.ru.md) · [CONTRIBUTING](CONTRIBUTING.md) · [MIT](LICENSE)

</details>
