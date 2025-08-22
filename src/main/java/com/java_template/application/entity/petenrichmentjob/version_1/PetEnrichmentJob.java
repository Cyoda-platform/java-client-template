package com.java_template.application.entity.petenrichmentjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PetEnrichmentJob implements CyodaEntity {
    public static final String ENTITY_NAME = "PetEnrichmentJob"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String jobId; // technical id for the job (serialized UUID or string)
    private String createdAt; // ISO-8601 timestamp string
    private String petSource; // source endpoint or identifier (string)
    private String requestedBy; // foreign key reference to User (serialized UUID string)
    private String status; // status as string (use string for enums)
    private Integer fetchedCount; // number of fetched records
    private List<String> errors; // list of error messages

    public PetEnrichmentJob() {
        this.errors = new ArrayList<>();
    } 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields using isBlank()
        if (this.jobId == null || this.jobId.isBlank()) return false;
        if (this.requestedBy == null || this.requestedBy.isBlank()) return false;
        if (this.petSource == null || this.petSource.isBlank()) return false;
        if (this.createdAt == null || this.createdAt.isBlank()) return false;
        if (this.status == null || this.status.isBlank()) return false;

        // Validate numeric fields
        if (this.fetchedCount == null || this.fetchedCount < 0) return false;

        // Ensure errors list is non-null (can be empty)
        if (this.errors == null) return false;

        return true;
    }
}