# 0.1.0 — an order report in your Spring application

[README](../../README.md) · [Versions](../index.md) · [English](integration.en.md) · [Русский](integration.ru.md) · [API](reference.en.md)

## Start with the business question

An operator needs order IDs, customer names and shipping cities for their tenant. The metadata describes how these fields connect. A Schema bean is the application’s approved relationship map; RelationalQueries turns that map plus selected fields/filters into an explainable query. The example deliberately includes billing as a second route so an accidental address choice cannot silently pass.

This is useful for changing reports and metadata-driven APIs. If a report never changes, a fixed prepared SQL statement may be simpler. The starter does not replace domain authorization, database migrations or an ORM.

## 1. Dependencies and configuration

Use the Maven or Gradle dependency block in the README with the Boot 4.1.2 BOM/parent. The full consumer pom.xml includes the starter, MVC, JDBC, PostgreSQL driver, and test-only H2. Public registry publication is pending; source and locally verified artifacts are separate from registry availability.

[Complete pom.xml](../../examples/order-report/pom.xml)

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

In an existing project retain your configured DataSource and migrations instead of duplicating them. Environment variables above are application credentials; the starter has none. sql.init.mode=never prevents this sample from creating production tables. The example needs sales.orders/customer/address as declared below.

## 2. File layout and approved schema

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

The builder defaults relationship cost to 1 and validates endpoints/columns when built. The tenant key is part of each relationship, so equal IDs from another tenant do not join. One logical address role has two incoming choices; the report selects shipping. To return both addresses, introduce separate billing/shipping roles referencing the same physical table.

## 3. Service: fixed policy, per-request values

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

The injected facade holds the selected bounded planner. The service creates one immutable projection/route template. Each request adds tenant and name filters to a new value, so concurrent requests cannot overwrite one another’s filters. Target roles are inferred from selected and filtered columns. Strict role.column notation accepts no SQL expressions or schema.table.column.

JdbcTemplate receives SQL and values separately and maps c0/c1/c2 into a business record. No generic reflection mapper or SQL concatenation is needed. Connections/resources are handled by the existing JDBC layer; see [Spring JDBC](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html).

## 4. Controller and identity boundary

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

This is a Spring MVC endpoint with blocking JDBC. The existing authentication/authorization layer must establish the server attribute after validating identity and access to reports. The library does not trust a tenant ID supplied in a header or query parameter. Missing context is rejected before the service is called. This sample does not provide a login server.

GET /reports/orders?customer=Alice with verified tenant 42 returns:

```json
[{"orderId":1,"customer":"Alice","shippingCity":"Perm"}]
```

The example controller is standard [Spring MVC](https://docs.spring.io/spring-boot/reference/web/servlet.html). Do not place this blocking JDBC service on a WebFlux event-loop thread. The pure planner itself is transport-independent; a reactive execution adapter is outside this sample.

## 5. Defaults and alternatives

| Property | Default | Meaning |
|---|---|---|
| relational-paths.enabled | true | Enable auto-configuration |
| relational-paths.max-tables | 512 | Maximum roles, at least 1 |
| relational-paths.max-relationships | 8192 | Maximum edges, zero allowed |
| relational-paths.max-edge-visits | 8192 | Edges examined per automatic search, zero allowed |

One Schema or a @Primary schema is required. Without it the starter does not choose arbitrarily. Existing JoinPlanner/SearchLimits/RelationalQueries beans win independently; the facade requires one resolvable planner; a custom planner needs no unused default limits. Disabling the starter removes its beans, not user beans. Direct constructors and the low-level planner/renderer remain available.

Use query.explain() to inspect route IDs, aliases and join equality. Use via for business ambiguity; do not select a witness route automatically. Search bounds limit metadata work, not SQL execution, memory consumed by database rows or tenant access.

## 6. Failure and execution boundaries

AmbiguousPathException means two minimum-cost paths: choose the business route or use distinct roles. PlanningException means disconnected/conflicting paths or exhausted bounds. IllegalArgumentException identifies invalid columns, notation, projection or duplicate via assignment. Startup fails for invalid effective bounds or oversized schema.

Database errors are handled by the host JDBC/error layer. Configure statement/query timeouts, row limits and transaction isolation there; this sample is a small report, not an unbounded production export. INNER JOIN may omit unmatched rows and multiply to-many matches. There is no automatic deduplication, pagination or database cost optimization.

Planning owns no background lifecycle. HTTP cancellation does not prove JDBC cancellation; configure that behavior in your server/driver. No AOP is needed for deterministic in-memory planning. An ordinary Spring service may use your normal transaction advice, but the starter adds none.

## Verification and what to retain

The complete source was copied into an independent Maven consumer and built against installed core/starter artifacts on Java 17. A real Boot context, private H2 JDBC database and MVC dispatcher verified the shipping result, matching tenant composite keys, injection as a parameter and rejection without server identity. This is functional H2 verification, not a new PostgreSQL or network cancellation test.

The invariant is simple: metadata controls legal routes, the service fixes business choices, and parameter bindings carry values. The convenience API removes duplicated target lists and wiring while keeping ambiguity and caps visible. Keep authorization, database durability and query resource budgets in the application.

[Consumer test](../../examples/order-report/src/test/java/example/reports/OrderReportConsumerTest.java) · [CONTRIBUTING](../../CONTRIBUTING.md) · [API](reference.en.md) · [Advanced guide](en.md) · [README](../../README.md)
