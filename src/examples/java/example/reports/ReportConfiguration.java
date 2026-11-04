package example.reports;

import io.github.alex59.relationalpaths.ColumnPair;
import io.github.alex59.relationalpaths.JoinPlanner;
import io.github.alex59.relationalpaths.Relationship;
import io.github.alex59.relationalpaths.Schema;
import io.github.alex59.relationalpaths.Table;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

// The application describes only approved roles and relationships; no schema scanning is needed.
// Приложение описывает разрешённые роли и связи; сканировать схему базы данных не требуется.
@Configuration(proxyBeanMethods = false)
public class ReportConfiguration {
    @Bean
    public Schema reportingSchema() {
        return new Schema(List.of(
            new Table("orders", "sales.orders",
                Set.of("id", "customer_id", "tenant_id", "billing_id", "shipping_id")),
            new Table("customer", "sales.customer", Set.of("id", "tenant_id", "name")),
            new Table("address", "sales.address", Set.of("id", "tenant_id", "city"))
        ), List.of(
            // Tenant is part of every join key, so equal IDs from another tenant cannot join.
            // Tenant входит в каждый ключ соединения: одинаковые ID другого арендатора не совпадут.
            new Relationship("order_customer", "orders", "customer", 1, List.of(
                new ColumnPair("customer_id", "id"), new ColumnPair("tenant_id", "tenant_id"))),
            new Relationship("billing_address", "orders", "address", 1, List.of(
                new ColumnPair("billing_id", "id"), new ColumnPair("tenant_id", "tenant_id"))),
            new Relationship("shipping_address", "orders", "address", 1, List.of(
                new ColumnPair("shipping_id", "id"), new ColumnPair("tenant_id", "tenant_id")))
        ));
    }

    @Bean
    public OrderReport orderReport(JoinPlanner planner) {
        // The starter supplies the planner; the report owns the business choice of address.
        // Стартер предоставляет планировщик; отчёт определяет, какой адрес нужен бизнесу.
        return new OrderReport(planner);
    }
}
