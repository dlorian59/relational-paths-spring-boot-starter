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
