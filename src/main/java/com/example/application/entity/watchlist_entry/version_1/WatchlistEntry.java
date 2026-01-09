package com.example.application.entity.watchlist_entry.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * WatchlistEntry Entity for Compliance Management Platform
 * 
 * Represents a watchlist entry from various compliance sources (OFAC, EU Sanctions, Custom).
 * Tracks individuals, entities, and vessels with risk assessment and identification information.
 */
@Data
public class WatchlistEntry implements CyodaEntity {
    public static final String ENTITY_NAME = "WatchlistEntry";
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String id;

    // Core watchlist information
    private Source source;
    private String name;
    private List<String> aliases;
    private Type type;
    private List<Identifier> identifiers;
    private RiskLevel riskLevel;

    // Status and audit fields
    private Boolean active;
    private LocalDateTime addedAt;
    private String rawRecord;
    private Map<String, Object> metadata;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        // Validate required business identifiers
        return id != null && !id.isBlank() && name != null && !name.isBlank();
    }

    /**
     * Nested class for identifier information
     */
    @Data
    public static class Identifier {
        private String type;
        private String value;
    }

    /**
     * Watchlist source enumeration
     */
    public enum Source {
        OFAC,
        EU_SANCTIONS,
        CUSTOM
    }

    /**
     * Entity type enumeration
     */
    public enum Type {
        INDIVIDUAL,
        ENTITY,
        VESSEL
    }

    /**
     * Risk level enumeration
     */
    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}

