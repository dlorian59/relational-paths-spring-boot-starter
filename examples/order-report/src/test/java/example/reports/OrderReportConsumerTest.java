package example.reports;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderReportConsumerTest {
    @Test void actualArtifactAutoConfigurationControllerJdbcAndTenantIsolation() throws Exception {
        var app = new SpringApplication(ReportApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        // A private in-memory database replaces deployment credentials only in this test.
        // Только в тесте приватная БД в памяти заменяет реквизиты рабочего окружения.
        try (var context = app.run("--spring.datasource.url=jdbc:h2:mem:reports;DB_CLOSE_DELAY=-1",
                "--spring.datasource.username=sa", "--spring.datasource.password=", "--spring.main.banner-mode=off")) {
            var jdbc = context.getBean(JdbcTemplate.class);
            jdbc.execute("CREATE SCHEMA \"sales\"");
            jdbc.execute("CREATE TABLE \"sales\".\"orders\" (\"id\" BIGINT, \"customer_id\" BIGINT, \"tenant_id\" BIGINT, \"billing_id\" BIGINT, \"shipping_id\" BIGINT)");
            jdbc.execute("CREATE TABLE \"sales\".\"customer\" (\"id\" BIGINT, \"tenant_id\" BIGINT, \"name\" VARCHAR(100))");
            jdbc.execute("CREATE TABLE \"sales\".\"address\" (\"id\" BIGINT, \"tenant_id\" BIGINT, \"city\" VARCHAR(100))");
            jdbc.update("INSERT INTO \"sales\".\"orders\" VALUES (1, 7, 42, 8, 9), (2, 7, 99, 8, 9)");
            jdbc.update("INSERT INTO \"sales\".\"customer\" VALUES (7, 42, 'Alice'), (7, 99, 'Alice')");
            jdbc.update("INSERT INTO \"sales\".\"address\" VALUES (8, 42, 'Billing'), (9, 42, 'Perm'), (9, 99, 'Other tenant')");
            var mvc = MockMvcBuilders.standaloneSetup(context.getBean(OrderReportController.class)).build();
            // The test injects verified context; a real authentication layer owns this attribute.
            // Тест передаёт проверенный контекст; в приложении атрибут принадлежит авторизации.
            mvc.perform(get("/reports/orders").param("customer", "Alice").requestAttr("authenticatedTenantId", 42L))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(1)).andExpect(jsonPath("$[0].shippingCity").value("Perm"));
            mvc.perform(get("/reports/orders").param("customer", "Alice' OR 1=1 --").requestAttr("authenticatedTenantId", 42L))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
            mvc.perform(get("/reports/orders").param("customer", "Alice").header("authenticatedTenantId", "42"))
                .andExpect(status().isBadRequest());
            assertEquals(1, context.getBean(OrderReportService.class).find(99L, "Alice").size());
            System.out.println("CONSUMER_PASS: shipping route, parameter isolation, tenant isolation, missing context rejected");
        }
    }
}
