package example.reports;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Keep these components under your existing application's component scan.
// Разместите компоненты внутри области сканирования существующего приложения.
@SpringBootApplication
public class ReportApplication {
    public static void main(String[] args) { SpringApplication.run(ReportApplication.class, args); }
}
