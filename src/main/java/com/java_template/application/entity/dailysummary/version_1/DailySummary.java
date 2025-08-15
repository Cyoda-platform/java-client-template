package com.java_template.application.entity.dailysummary.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class DailySummary implements CyodaEntity {
    public static final String ENTITY_NAME = "DailySummary";
    public static final Integer ENTITY_VERSION = 1;
    // DailySummary fields
    private String date; // YYYY-MM-DD
    private String gamesSummary; // JSON array of per-game summary objects as String
    private String generatedAt; // ISO-8601 timestamp
    private String sourceFetchJobId; // reference to FetchJob (serialized UUID or request_date)
    private String summaryId; // domain id for the summary

    public DailySummary() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // date and summaryId required
        return date != null && !date.isBlank()
            && summaryId != null && !summaryId.isBlank();
    }
}
