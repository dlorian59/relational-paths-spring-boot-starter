# 0.1.0 — отчёт по заказам в вашем Spring-приложении

[README](../../README.md) · [Versions](../index.md) · [English](integration.en.md) · [Русский](integration.ru.md) · [API](reference.ru.md)

## Начните с бизнес-задачи

Оператору нужны ID заказов, имена клиентов и города доставки своего арендатора. Метаданные описывают, как связаны поля. Bean Schema — разрешённая приложением карта связей; RelationalQueries превращает карту и выбранные поля/фильтры в объяснимый запрос. В примере есть второй маршрут billing, поэтому случайный выбор адреса не может пройти незаметно.

Это удобно для меняющихся отчётов и API по метаданным. Если отчёт никогда не меняется, фиксированный SQL с параметрами может быть проще. Стартер не заменяет доменную авторизацию, миграции и ORM.

## 1. Зависимости и конфигурация

Используйте блок Maven или Gradle из README с BOM/parent Boot 4.1.2. Полный consumer pom.xml включает стартер, MVC, JDBC, драйвер PostgreSQL и H2 только для теста. Публикация в публичный реестр ожидается: исходники и локально проверенные артефакты не означают доступность в реестре.

[Полный pom.xml](../../examples/order-report/pom.xml)

```yaml
spring:
  datasource:
    url: ${REPORT_DB_URL}
    username: ${REPORT_DB_USER}
    password: ${REPORT_DB_PASSWORD}
  sql:
    init:
      mode: never
relational-paths:
  max-tables: 32
  max-relationships: 64
  max-edge-visits: 64
```

В существующем проекте сохраните ваши DataSource и миграции вместо дублирования. Переменные окружения выше — реквизиты приложения; у стартера своих нет. sql.init.mode=never запрещает примеру создавать рабочие таблицы. Нужны sales.orders/customer/address, описанные ниже.

## 2. Структура и разрешённая схема

```text
src/main/java/example/reports/
  ReportConfiguration.java     # Schema bean
  OrderReportService.java      # query + JDBC mapping
  OrderReportController.java   # GET /reports/orders
src/main/resources/application.yml
```

```java
package example.reports;

import io.github.alex59.relationalpaths.ColumnPair;
import io.github.alex59.relationalpaths.Schema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ReportConfiguration {
    @Bean
    Schema reportSchema() {
        // Declare approved business columns; include tenant identity in every relationship.
        // Объявите разрешённые бизнес-колонки; включайте арендатора в каждую связь.
        return Schema.builder()
            .table("orders", "sales.orders", "id", "customer_id", "tenant_id", "billing_id", "shipping_id")
            .table("customer", "sales.customer", "id", "tenant_id", "name")
            .table("address", "sales.address", "id", "tenant_id", "city")
            .relationship("customer", "orders", "customer",
                new ColumnPair("customer_id", "id"), new ColumnPair("tenant_id", "tenant_id"))
            .relationship("billing", "orders", "address",
                new ColumnPair("billing_id", "id"), new ColumnPair("tenant_id", "tenant_id"))
            .relationship("shipping", "orders", "address",
                new ColumnPair("shipping_id", "id"), new ColumnPair("tenant_id", "tenant_id"))
            .build();
    }
}
```

Builder использует стоимость связи 1 и проверяет концы/колонки при build. Арендатор входит в каждую связь, поэтому одинаковые ID другого арендатора не соединяются. У одной роли address два входящих варианта; отчёт выбирает shipping. Чтобы вернуть оба адреса, создайте отдельные роли billing/shipping одной физической таблицы.

## 3. Сервис: фиксированное правило, значения запроса

```java
package example.reports;

import io.github.alex59.relationalpaths.RelationalQueries;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class OrderReportService {
    private final RelationalQueries.Select shipping;
    private final JdbcTemplate jdbc;

    public OrderReportService(RelationalQueries queries, JdbcTemplate jdbc) {
        // An immutable template keeps the business route fixed across requests.
        // Неизменяемый шаблон сохраняет выбранный бизнес-маршрут между запросами.
        this.shipping = queries.from("orders")
            .select("orders.id", "customer.name", "address.city").via("address", "shipping");
        this.jdbc = jdbc;
    }

    public List<OrderRow> find(long authenticatedTenantId, String customerName) {
        var query = shipping.whereEqual("orders.tenant_id", authenticatedTenantId)
            .whereEqual("customer.name", customerName).build();
        // JdbcTemplate binds each value; never interpolate SQL or accept raw column names from HTTP.
        // JdbcTemplate связывает значения; не подставляйте SQL и имена колонок из HTTP.
        return jdbc.query(query.sql(), (rows, index) -> new OrderRow(
            rows.getLong("c0"), rows.getString("c1"), rows.getString("c2")), query.bindings().toArray());
    }

    public record OrderRow(long orderId, String customer, String shippingCity) { }
}
```

Внедрённый фасад содержит выбранный планировщик с лимитами. Сервис создаёт один неизменяемый шаблон проекции/маршрута. Каждый запрос добавляет арендатора и имя в новый объект: параллельные запросы не меняют фильтры друг друга. Цели выводятся из полей и фильтров. Строгий role.column не принимает SQL-выражения и schema.table.column.

JdbcTemplate получает SQL и значения отдельно и отображает c0/c1/c2 в бизнес-запись. Общий reflection mapper и склейка SQL не нужны. Соединениями и ресурсами управляет существующий JDBC-слой; см. [Spring JDBC](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html).

## 4. Контроллер и граница идентификации

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

Это endpoint Spring MVC с блокирующим JDBC. Существующий слой аутентификации/авторизации должен задать серверный атрибут после проверки личности и доступа к отчётам. Библиотека не доверяет арендатору из заголовка или query-параметра. Отсутствующий контекст отклоняется до вызова сервиса. Пример не предоставляет сервер входа.

GET /reports/orders?customer=Alice с проверенным арендатором 42 возвращает:

```json
[{"orderId":1,"customer":"Alice","shippingCity":"Perm"}]
```

Контроллер использует стандартный [Spring MVC](https://docs.spring.io/spring-boot/reference/web/servlet.html). Не вызывайте блокирующий JDBC-сервис на event-loop WebFlux. Сам планировщик независим от транспорта; реактивный адаптер выполнения не входит в пример.

## 5. Настройки и альтернативы

| Property | Default | Значение |
|---|---|---|
| relational-paths.enabled | true | Включить автоконфигурацию |
| relational-paths.max-tables | 512 | Максимум ролей, не менее 1 |
| relational-paths.max-relationships | 8192 | Максимум связей, допустим ноль |
| relational-paths.max-edge-visits | 8192 | Рёбра автоматического поиска, допустим ноль |

Требуется одна Schema либо схема @Primary. Без этого стартер не выбирает произвольно. Собственные JoinPlanner/SearchLimits/RelationalQueries имеют независимый приоритет; фасаду нужен один разрешимый планировщик; пользовательскому планировщику не нужны неиспользуемые стандартные лимиты. Отключение стартера убирает его beans, не пользовательские. Прямые конструкторы и низкоуровневые планировщик/рендерер сохранены.

query.explain() показывает ID маршрутов, алиасы и равенства. Для бизнес-неоднозначности используйте via: не выбирайте пример маршрута автоматически. Лимиты поиска ограничивают работу с метаданными, не выполнение SQL, память строк БД и доступ арендатора.

## 6. Ошибки и границы выполнения

AmbiguousPathException означает два пути минимальной стоимости: выберите бизнес-маршрут либо разные роли. PlanningException означает несвязность, конфликт путей или исчерпание лимита. IllegalArgumentException указывает на колонки, синтаксис, проекцию или повторный via. Некорректные действующие лимиты и слишком большая схема останавливают запуск.

Ошибки БД обрабатывает JDBC/error-слой приложения. Там задаются тайм-ауты, лимит строк и изоляция транзакций; пример — небольшой отчёт, не неограниченный production-экспорт. INNER JOIN может пропускать строки без соответствий и размножать связи один-ко-многим. Автоматической дедупликации, пагинации и оптимизации стоимости БД нет.

У планирования нет фонового жизненного цикла. Отмена HTTP не доказывает отмену JDBC: настройте поведение сервера/драйвера. Для детерминированного планирования в памяти AOP не нужен. Обычный Spring-сервис может использовать ваш transactional advice, но стартер его не добавляет.

## Проверка и что нужно знать

Полный исходник скопирован в отдельный Maven consumer и собран с установленными core/starter на Java 17. Настоящий контекст Boot, приватная H2 через JDBC и MVC dispatcher проверили результат доставки, составные ключи арендатора, инъекцию как параметр и отказ без серверной идентификации. Это функциональная проверка H2, не новый тест PostgreSQL или сетевой отмены.

Инвариант простой: метаданные задают допустимые пути, сервис фиксирует бизнес-выбор, параметры передают значения. Удобный API убирает повторные списки целей и обвязку, сохраняя явные неоднозначность и лимиты. Авторизация, долговечность БД и бюджет ресурсов запроса остаются приложению.

[Тест consumer](../../examples/order-report/src/test/java/example/reports/OrderReportConsumerTest.java) · [CONTRIBUTING](../../CONTRIBUTING.md) · [API](reference.ru.md) · [Расширенное руководство](ru.md) · [README](../../README.md)
