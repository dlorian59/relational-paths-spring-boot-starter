# relational-paths-spring-boot-starter 0.1.0 — complete reference

[← Integration guide](en.md) · [Versions](../index.md) · [README](../../README.md) · [Русский](reference.ru.md)

This starter integrates `relational-paths:0.3.0` with Spring Boot 4.1.2. It configures plain immutable planning objects, not a database, ORM or AOP interceptor. Read the [complete order-report scenario](../../README.md#situation-an-order-report-with-two-addresses) first.

## Public types and integration points

| Surface | Purpose and usage | Boundary |
| --- | --- | --- |
| [RelationalPathsAutoConfiguration](../../src/main/java/io/github/alex59/relationalpaths/spring/RelationalPathsAutoConfiguration.java) | Discovered through [AutoConfiguration.imports](../../src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports); normal Boot startup is sufficient. | Do not instantiate or component-scan it manually. Bean factory methods are internal; override by declaring beans of the core types. |
| [RelationalPathsProperties](../../src/main/java/io/github/alex59/relationalpaths/spring/RelationalPathsProperties.java) | Immutable record with `maxTables()`, `maxRelationships()`, `maxEdgeVisits()` and standard record accessors/equality. | Boot binding provides missing-value defaults; direct `new RelationalPathsProperties(...)` requires all three ints and does not itself validate core bounds. |
| [toSearchLimits()](../../src/main/java/io/github/alex59/relationalpaths/spring/RelationalPathsProperties.java) | Creates core `SearchLimits` and validates its numeric bounds. | No schema validation until a planner is constructed. This method is not a dynamic refresh mechanism. |
| Application `Schema` bean | [ReportConfiguration](../../src/examples/java/example/reports/ReportConfiguration.java) declares permitted roles, columns and relationships. | Application owns metadata, tenant policy and access; no scanning or inferred authorization. |
| Injected `JoinPlanner` / optional custom `SearchLimits` | Constructor injection into [OrderReport](../../src/examples/java/example/reports/OrderReport.java); see activation matrix below. | No new core API; `SqlRenderer.select` remains static. |

## Properties and defaults

| Property | Default | Contract |
| --- | --- | --- |
| `relational-paths.enabled` | `true` | Auto-configuration condition; use true/false. It is not a component of RelationalPathsProperties. Missing means enabled, but a Schema candidate is still required. |
| `relational-paths.max-tables` | `512` | Java int ≥ 1 when effective SearchLimits is created; all schema roles count. Oversized schema fails JoinPlanner construction. |
| `relational-paths.max-relationships` | `8192` | Java int ≥ 0; all declared relationships count. Zero permits an edge-free schema. |
| `relational-paths.max-edge-visits` | `8192` | Java int ≥ 0; examined outgoing edges per automatic search. Zero permits root-only or fully explicit valid plans, but no examined edge. |

Use application.properties/YAML or standard Boot external configuration. Example settings and bilingual comments are in [application.properties](../../src/examples/resources/application.properties). Unknown property names are ignored by standard binding, so a typo leaves the intended setting at its default; check spelling against the [generated configuration metadata source](../../src/main/resources/META-INF/additional-spring-configuration-metadata.json) and [Properties class](../../src/main/java/io/github/alex59/relationalpaths/spring/RelationalPathsProperties.java). Core bounds are validated only when converted to effective SearchLimits. A custom bean does not imply all property binding has been disabled.

```properties
# These are hard metadata traversal bounds, not SQL row or query-time limits.
# Это жёсткие границы обхода метаданных, а не число строк или время SQL.
relational-paths.enabled=true
relational-paths.max-tables=32
relational-paths.max-relationships=64
relational-paths.max-edge-visits=64
```

## Activation and bean precedence

The table describes only beans created by the starter; application beans remain in the context. Conditions and precedence are exercised by the [existing context tests](../../src/test/java/io/github/alex59/relationalpaths/spring/RelationalPathsAutoConfigurationTest.java).

| Application state | Auto-created beans | Consequence |
| --- | --- | --- |
| No Schema / disabled / core class unavailable | None | The application must still satisfy its own JoinPlanner injection points. |
| One Schema; no planner or limits | SearchLimits from properties + JoinPlanner | Default bounds: 512 / 8192 / 8192. |
| Multiple Schema beans; exactly one @Primary | Same as one selected Schema | The primary schema is selected by ordinary Spring injection. |
| Multiple Schema beans; no single candidate | None | No arbitrary schema selection. Add @Primary or supply application planners explicitly. |
| Selected Schema + custom SearchLimits, no planner | JoinPlanner using that limits bean | No default SearchLimits; properties do not overwrite the custom object. Multiple limits need an injectable primary/unique candidate. |
| Selected Schema + custom JoinPlanner | Neither default planner nor default limits | Custom planner construction, metadata and bounds belong to the application. Existing custom limits are untouched. |

With active auto-configuration, the immutable properties bean is registered even when an application planner replaces the defaults. Default bean names are `relationalPathsSearchLimits` and `relationalPathsJoinPlanner`; replacement is by type, not by naming. Use a single `@Bean` for custom limits in your normal application configuration:

```java
// A custom limits bean replaces property-derived defaults for the auto-configured planner.
// Собственный bean лимитов заменяет defaults из properties у автоматического планировщика.
@Bean
SearchLimits reportLimits() {
    return new SearchLimits(32, 64, 64);
}
```

This method belongs in a `@Configuration` class with imports for `Bean` and core `SearchLimits`. To manage several planners, create explicitly named `JoinPlanner` beans using the corresponding schemas/limits and inject them with `@Qualifier`; the starter backs off as soon as any planner bean exists. It does not create a registry or one planner per schema.

## Lifecycle, ownership and AOP

| Concern | Contract |
| --- | --- |
| Scope | Default Spring singleton per context; immutable schema, limits and planner. No executor, database resource or shutdown hook is allocated. |
| Configuration changes | No hot reload. Replace the application-owned planner/context deliberately; existing report plans retain their original immutable schema. |
| Bound values | SQL/binding collections are separate; mutable bound values remain caller-owned. Do not mutate them concurrently with JDBC execution. |
| Policy | No SQL execution, authorization, transaction advice, retry, cache or ambiguity resolution is installed. These belong to the host service. |
| Proxies | No AOP enablement or aspect is supplied. Plain final beans work without proxies. `proxyBeanMethods=false` on sample configuration only avoids inter-bean configuration interception; it is not an AOP switch for the application. |

If an independent service-level timing/audit/transaction concern needs AOP, place it on an application service with a suitable interface or non-final proxyable methods. Do not apply a broad class-based pointcut to final JoinPlanner or static SqlRenderer calls. Invoke through the service proxy; self-invocation bypasses advice. See [official Spring proxy rules](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html). Retrying a deterministic ambiguity is not recovery and must not choose a route silently.

## Errors and recovery

| Error/symptom | Cause | Action |
| --- | --- | --- |
| No qualifying JoinPlanner bean | No Schema, multiple unqualified schemas, disabled configuration or missing starter discovery. | Provide Schema/@Primary or your own planner, enable configuration, and verify the starter JAR is on the runtime classpath. |
| Failed to bind properties under relational-paths | A numeric property has invalid syntax or is outside the Java int range. | Correct/remove the property. Binding may still happen with a custom planner/limits when auto-configuration is active. |
| Invalid search bounds | An effective limit is negative, or max-tables is zero. | Use max-tables≥1 and other limits≥0; @DefaultValue applies to missing properties, not invalid explicit values. |
| Schema exceeds configured table/relationship limits | Selected schema exceeds effective SearchLimits. | Review the approved metadata or deliberately raise the corresponding bound. |
| Search exceeded maxEdgeVisits=… | Automatic planning examined too many edges. | Narrow the schema, use deliberate explicit paths, or raise the bound. Failure occurs at plan(); an eager report bean may therefore fail startup. |
| Ambiguous path to … | Business metadata offers two equal-cost choices. | Catch AmbiguousPathException before PlanningException and make an explicit business choice; do not retry/pick a witness automatically. |
| NoUniqueBeanDefinitionException for SearchLimits | Several custom limits exist without a single injectable choice. | Mark one @Primary or build your own named planners with @Qualifier for their schemas/limits. |
| Cannot subclass final class / advice does not run | A host pointcut tries to class-proxy core objects, or a service invokes itself. | Keep advice on a suitable application service boundary and call through that service proxy; the starter installs no aspect. |
| Could not find io.github.59alex:relational-paths:0.3.0 | Core artifact was not installed where the consuming build resolves it. | Run publishToMavenLocal in the sibling core checkout; keep mavenLocal() in the consumer. No Central availability is implied. |
| No matching Java installation / UnsupportedClassVersionError | Selected toolchain absent, or runtime older than the artifact baseline. | Use a full JDK 17+; by default the build uses the current JVM version. -PtestJavaVersion selects an installed toolchain; release/target remain 17. |

## Runnable coverage and boundaries

| Capability | Runnable source / check |
| --- | --- |
| Normal Boot discovery | [ReportApplication](../../src/examples/java/example/reports/ReportApplication.java) |
| Schema and composite tenant equality | [ReportConfiguration](../../src/examples/java/example/reports/ReportConfiguration.java) |
| Explicit shipping route, reusable plan, tenant/name bindings | [OrderReport](../../src/examples/java/example/reports/OrderReport.java) |
| Defaults, opt-out, @Primary, custom bean precedence, startup limits, search failure, report SQL | [Context tests](../../src/test/java/io/github/alex59/relationalpaths/spring/RelationalPathsAutoConfigurationTest.java) |

`./gradlew test example` runs the existing checks and report; it performs no SQL execution. Install the sibling core first using the [README commands](../../README.md#run-locally). The starter contains auto-configuration discovery and IDE metadata; source/Javadoc JARs are generated by build. Java 17 is the API/bytecode baseline; running on 21/25/27 adds no starter-specific language feature. The core metadata graph and SQL restrictions remain unchanged: INNER JOIN only, no cardinality guarantees, no row-level security and no automatic schema refresh.

For every core type/operator/error, see [relational-paths full reference](https://github.com/dlorian59/relational-paths/blob/main/docs/0.3.0/reference.en.md). For publication/version state, use [versions](../index.md); Maven Local commands do not imply Central availability.

[← Integration guide](en.md)
