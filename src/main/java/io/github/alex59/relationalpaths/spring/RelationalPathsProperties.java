package io.github.alex59.relationalpaths.spring;

import io.github.alex59.relationalpaths.SearchLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Immutable bounds used when the application does not provide a SearchLimits bean.
 * @param maxTables maximum logical roles
 * @param maxRelationships maximum directed relationships
 * @param maxEdgeVisits maximum examined edges per automatic search
 */
@ConfigurationProperties("relational-paths")
public record RelationalPathsProperties(
        @DefaultValue("512") int maxTables,
        @DefaultValue("8192") int maxRelationships,
        @DefaultValue("8192") int maxEdgeVisits) {

    /** Converts configuration to the core contract, including its validation.
     * @return validated hard bounds
     */
    public SearchLimits toSearchLimits() {
        return new SearchLimits(maxTables, maxRelationships, maxEdgeVisits);
    }
}
