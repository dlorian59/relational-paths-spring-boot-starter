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
