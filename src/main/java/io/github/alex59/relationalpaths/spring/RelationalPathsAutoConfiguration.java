package io.github.alex59.relationalpaths.spring;

import io.github.alex59.relationalpaths.JoinPlanner;
import io.github.alex59.relationalpaths.RelationalQueries;
import io.github.alex59.relationalpaths.Schema;
import io.github.alex59.relationalpaths.SearchLimits;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Creates a planner only when the application supplies one resolvable schema. */
@AutoConfiguration
@ConditionalOnClass(JoinPlanner.class)
@ConditionalOnSingleCandidate(Schema.class)
@ConditionalOnProperty(prefix = "relational-paths", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RelationalPathsProperties.class)
public final class RelationalPathsAutoConfiguration {

    /** Uses property bounds unless the application supplies its own bounds.
     * @param properties external configuration
     * @return validated search limits
     */
    @Bean
    @ConditionalOnMissingBean({SearchLimits.class, JoinPlanner.class})
    SearchLimits relationalPathsSearchLimits(RelationalPathsProperties properties) {
        return properties.toSearchLimits();
    }

    /** Respects application planners and the selected schema/limits beans.
     * @param schema application-owned metadata
     * @param limits hard search bounds
     * @return immutable, shareable planner
     */
    @Bean
    @ConditionalOnMissingBean
    JoinPlanner relationalPathsJoinPlanner(Schema schema, SearchLimits limits) {
        return new JoinPlanner(schema, limits);
    }

    /** Exposes the concise facade over the selected planner, preserving its limits.
     * @param planner configured or application-supplied planner
     * @return shareable facade with immutable per-query templates
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnSingleCandidate(JoinPlanner.class)
    RelationalQueries relationalQueries(JoinPlanner planner) {
        return new RelationalQueries(planner);
    }
}
