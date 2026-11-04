package example.reports;

import io.github.alex59.relationalpaths.BoundSql;
import io.github.alex59.relationalpaths.ColumnRef;
import io.github.alex59.relationalpaths.Filter;
import io.github.alex59.relationalpaths.JoinPlan;
import io.github.alex59.relationalpaths.JoinPlanner;
import io.github.alex59.relationalpaths.SqlRenderer;

import java.util.List;
import java.util.Map;

// One report policy is reusable from HTTP, a scheduled export, or an administrative screen.
// Одно правило отчёта подходит для HTTP, экспорта по расписанию и административного экрана.
public final class OrderReport {
    private final JoinPlan shippingPlan;

    public OrderReport(JoinPlanner planner) {
        // Billing and shipping are equally short; explicitly choose shipping instead of guessing.
        // Пути к платёжному адресу и адресу доставки равны; явно выбираем доставку.
        this.shippingPlan = planner.plan("orders", List.of("customer", "address"),
            Map.of("address", List.of("shipping_address")));
    }

    public BoundSql query(long authenticatedTenantId, String customerName) {
        // Obtain tenant identity from authenticated server context, never from an untrusted request.
        // ID арендатора берите из проверенного серверного контекста, а не из непроверенного запроса.
        return SqlRenderer.select(shippingPlan,
            List.of(new ColumnRef("orders", "id"), new ColumnRef("customer", "name"),
                new ColumnRef("address", "city")),
            List.of(new Filter(new ColumnRef("orders", "tenant_id"), Filter.Operator.EQ, authenticatedTenantId),
                new Filter(new ColumnRef("customer", "name"), Filter.Operator.EQ, customerName)));
    }

    public JoinPlan plan() { return shippingPlan; }
}
