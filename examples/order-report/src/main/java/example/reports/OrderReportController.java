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
