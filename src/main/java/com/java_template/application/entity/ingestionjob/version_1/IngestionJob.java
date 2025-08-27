package com.java_template.application.entity.ingestionjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class IngestionJob implements CyodaEntity {
    public static final String ENTITY_NAME = "IngestionJob"; 
    public static final Integer ENTITY_VERSION = 1;

    // Technical id (returned by POST endpoints)
    private String id;

    // Timestamps and metadata
    private String createdAt;    // e.g., "2025-08-25T08:00:00Z"
    private String createdBy;    // e.g., "admin@example.com"

    // Source and scheduling
    private String sourceUrl;    // e.g., "https://petstore.swagger.io/v2/store"
    private String dataFormats;  // e.g., "JSON,XML"
    private String scheduleDay;  // e.g., "Monday"
    private String scheduleTime; // e.g., "08:00"

    // Status and window
    private String status;       // use String for enum-like values e.g., "COMPLETED"
    private Integer timeWindowDays; // e.g., 7

    public IngestionJob() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // id may be null for newly created entities; if present it must not be blank
        if (id != null && id.isBlank()) return false;

        // Validate required string fields using isBlank()
        if (createdAt == null || createdAt.isBlank()) return false;
        if (createdBy == null || createdBy.isBlank()) return false;
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        if (dataFormats == null || dataFormats.isBlank()) return false;
        if (scheduleDay == null || scheduleDay.isBlank()) return false;
        if (scheduleTime == null || scheduleTime.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // Validate numeric fields
        if (timeWindowDays == null || timeWindowDays < 0) return false;

        return true;
    }
}