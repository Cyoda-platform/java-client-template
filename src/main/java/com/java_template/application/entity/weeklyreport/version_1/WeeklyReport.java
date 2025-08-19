package com.java_template.application.entity.weeklyreport.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
public class WeeklyReport implements CyodaEntity {
    public static final String ENTITY_NAME = "WeeklyReport";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String fetchJobId;
    private OffsetDateTime weekStartDate;
    private OffsetDateTime weekEndDate;
    private Integer totalBooks;
    private Integer totalPages;
    private Double avgPages;
    private List<Map<String, Object>> topTitles; // id, title, pageCount
    private List<Map<String, Object>> popularTitles; // id, title, descriptionSnippet, excerptSnippet
    private Map<String, Object> publicationSummary; // newest, oldest, countsByYear
    private OffsetDateTime generationTimestamp;
    private String reportStatus; // generated/sent/failed
    private Map<String, Object> deliveryInfo; // recipients, emailStatus

    public WeeklyReport() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (weekStartDate == null) return false;
        if (weekEndDate == null) return false;
        if (generationTimestamp == null) return false;
        if (totalBooks == null || totalBooks < 0) return false;
        return true;
    }
}
