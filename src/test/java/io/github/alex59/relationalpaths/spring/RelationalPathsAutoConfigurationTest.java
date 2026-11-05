package io.github.alex59.relationalpaths.spring;

import example.reports.OrderReport;
import example.reports.ReportApplication;
import example.reports.ReportConfiguration;
import io.github.alex59.relationalpaths.AmbiguousPathException;
import io.github.alex59.relationalpaths.JoinPlanner;
import io.github.alex59.relationalpaths.RelationalQueries;
import io.github.alex59.relationalpaths.PlanningException;
import io.github.alex59.relationalpaths.Schema;
import io.github.alex59.relationalpaths.SearchLimits;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelationalPathsAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(RelationalPathsAutoConfiguration.class));

    @Test void activatesOnlyWhenSchemaIsPresentAndCanBeDisabled() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(JoinPlanner.class);
            assertThat(context).doesNotHaveBean(SearchLimits.class);
        });
        runner.withUserConfiguration(ReportConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(JoinPlanner.class);
            assertThat(context.getBean(SearchLimits.class)).isEqualTo(SearchLimits.DEFAULT);
        });
        runner.withBean(Schema.class, () -> new ReportConfiguration().reportingSchema())
            .withPropertyValues("relational-paths.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(JoinPlanner.class));
    }

    @Test void bindsLimitsAndFailsInsteadOfReturningATruncatedPlan() {
        runner.withBean(Schema.class, () -> new ReportConfiguration().reportingSchema())
            .withPropertyValues("relational-paths.max-tables=3", "relational-paths.max-relationships=3",
                "relational-paths.max-edge-visits=0")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(SearchLimits.class)).isEqualTo(new SearchLimits(3, 3, 0));
                assertThatThrownBy(() -> context.getBean(JoinPlanner.class).plan("orders", List.of("customer")))
                    .isInstanceOf(PlanningException.class).hasMessageContaining("maxEdgeVisits=0");
            });
    }

    @Test void invalidBoundsAndSchemaSizeLimitFailStartup() {
        runner.withBean(Schema.class, () -> new ReportConfiguration().reportingSchema())
            .withPropertyValues("relational-paths.max-tables=0")
            .run(context -> assertThat(context).hasFailed());
        runner.withBean(Schema.class, () -> new ReportConfiguration().reportingSchema())
            .withPropertyValues("relational-paths.max-tables=2")
            .run(context -> assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(PlanningException.class));
    }

    @Test void applicationPlannerAndLimitsTakePrecedence() {
        var schema = new ReportConfiguration().reportingSchema();
        var limits = new SearchLimits(3, 3, 1);
        var planner = new JoinPlanner(schema, limits);
        runner.withBean(Schema.class, () -> schema)
            .withBean(SearchLimits.class, () -> limits)
            .withBean(JoinPlanner.class, () -> planner)
            .withPropertyValues("relational-paths.max-tables=0")
            .run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(JoinPlanner.class).hasSingleBean(SearchLimits.class);
                assertThat(context.getBean(JoinPlanner.class)).isSameAs(planner);
                assertThat(context.getBean(SearchLimits.class)).isSameAs(limits);
            });
    }

    @Test void customPlannerNeedsNoUnusedDefaultBounds() {
        var schema = new ReportConfiguration().reportingSchema();
        var planner = new JoinPlanner(schema);
        runner.withBean(Schema.class, () -> schema).withBean(JoinPlanner.class, () -> planner)
            .withPropertyValues("relational-paths.max-tables=0")
            .run(context -> {
                assertThat(context).hasNotFailed().doesNotHaveBean(SearchLimits.class);
                assertThat(context.getBean(JoinPlanner.class)).isSameAs(planner);
            });
    }

    @Test void customLimitsAreAppliedToTheAutoConfiguredPlanner() {
        runner.withBean(Schema.class, () -> new ReportConfiguration().reportingSchema())
            .withBean(SearchLimits.class, () -> new SearchLimits(3, 3, 0))
            .run(context -> assertThatThrownBy(() -> context.getBean(JoinPlanner.class)
                .plan("orders", List.of("customer"))).isInstanceOf(PlanningException.class));
    }

    @Test void multipleSchemasAreNeverChosenArbitrarily() {
        runner.withBean("first", Schema.class, () -> new ReportConfiguration().reportingSchema())
            .withBean("second", Schema.class, () -> new ReportConfiguration().reportingSchema())
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(JoinPlanner.class);
            });
        runner.withUserConfiguration(PrimarySchema.class)
            .withBean("second", Schema.class, () -> new ReportConfiguration().reportingSchema())
            .run(context -> assertThat(context).hasSingleBean(JoinPlanner.class));
    }

    @Test void realBootDiscoveryInjectsReportAndKeepsAmbiguityAndBindingsExplicit() {
        new ApplicationContextRunner().withUserConfiguration(ReportApplication.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(JoinPlanner.class).hasSingleBean(OrderReport.class);
            var planner = context.getBean(JoinPlanner.class);
            assertThatThrownBy(() -> planner.plan("orders", List.of("address")))
                .isInstanceOf(AmbiguousPathException.class);
            var report = context.getBean(OrderReport.class);
            assertThat(report.plan().routes().get("address")).containsExactly("shipping_address");
            var query = report.query(42L, "Alice' OR 1=1 --");
            assertThat(query.bindings()).containsExactly(42L, "Alice' OR 1=1 --");
            assertThat(query.sql()).doesNotContain("Alice", "billing_id")
                .contains("\"t0\".\"shipping_id\" = \"t2\".\"id\"",
                    "\"t0\".\"tenant_id\" = \"t1\".\"tenant_id\"",
                    "\"t0\".\"tenant_id\" = \"t2\".\"tenant_id\"",
                    "WHERE \"t0\".\"tenant_id\" = ? AND \"t1\".\"name\" = ?");
        });
    }

    @Test void facadeUsesCustomPlannerLimitsAndRespectsApplicationFacade() {
        var schema = new ReportConfiguration().reportingSchema();
        var planner = new JoinPlanner(schema, new SearchLimits(3, 3, 0));
        runner.withBean(Schema.class, () -> schema).withBean(JoinPlanner.class, () -> planner)
            .run(context -> assertThatThrownBy(() -> context.getBean(RelationalQueries.class)
                .from("orders").select("customer.name").build()).isInstanceOf(PlanningException.class));
        var facade = new RelationalQueries(schema);
        runner.withBean(Schema.class, () -> schema).withBean(RelationalQueries.class, () -> facade)
            .run(context -> assertThat(context.getBean(RelationalQueries.class)).isSameAs(facade));
    }

    @Test void multipleApplicationPlannersDoNotAcquireAnAmbiguousFacadeDependency() {
        var schema = new ReportConfiguration().reportingSchema();
        runner.withBean(Schema.class, () -> schema)
            .withBean("firstPlanner", JoinPlanner.class, () -> new JoinPlanner(schema))
            .withBean("secondPlanner", JoinPlanner.class, () -> new JoinPlanner(schema))
            .run(context -> {
                assertThat(context).hasNotFailed().doesNotHaveBean(RelationalQueries.class);
                assertThat(context.getBeansOfType(JoinPlanner.class)).hasSize(2);
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class PrimarySchema {
        @Bean @Primary Schema primarySchema() { return new ReportConfiguration().reportingSchema(); }
    }
}
