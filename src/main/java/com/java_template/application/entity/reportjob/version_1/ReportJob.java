package com.java_template.application.entity.reportjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class ReportJob implements CyodaEntity {
    public static final String ENTITY_NAME = "ReportJob"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // Technical id (serialized UUID)
    private String id;
    private String createdAt;
    private String generatedUrl;
    private String name;
    private String outputFormats;
    private String periodStart;
    private String periodEnd;
    private String recipients;
    private String status;
    private String templateId;

    public ReportJob() {} 

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
        if (name == null || name.isBlank()) return false;
        if (templateId == null || templateId.isBlank()) return false;
        if (periodStart == null || periodStart.isBlank()) return false;
        if (periodEnd == null || periodEnd.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // id may be assigned by the system (POST responses return only technical id), so don't require it here
        return true;
    }
}