package example.reports;

import io.github.alex59.relationalpaths.AmbiguousPathException;
import io.github.alex59.relationalpaths.JoinPlanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.util.List;

// A real Boot context loads the starter through AutoConfiguration.imports; no database is opened.
// Настоящий контекст Boot загружает стартер через AutoConfiguration.imports; соединений с БД нет.
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(ReportConfiguration.class)
public class ReportApplication {
    public static void main(String[] args) {
        var application = new SpringApplication(ReportApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (var context = application.run(args)) {
            var planner = context.getBean(JoinPlanner.class);
            try {
                planner.plan("orders", List.of("address"));
                throw new AssertionError("An address choice must be explicit");
            } catch (AmbiguousPathException expected) {
                System.out.println("Address choices: " + expected.candidates());
            }
            var report = context.getBean(OrderReport.class);
            var query = report.query(42L, "Alice");
            System.out.println(report.plan().explain());
            System.out.println(query.sql());
            System.out.println("Bindings: " + query.bindings());
            // JDBC execution, transaction scope and access checks belong to the host application.
            // Выполнение JDBC, границы транзакций и проверка доступа остаются в приложении.
        }
    }
}
