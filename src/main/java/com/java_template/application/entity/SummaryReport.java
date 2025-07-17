package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.util.List;

@Data
public class SummaryReport implements CyodaEntity {
    private String status;
    private String reportDate;
    private int totalBooks;
    private int totalPageCount;
    private PublicationDateRange publicationDateRange;
    private List<Book> popularTitles;
    private String summaryText;

    public SummaryReport() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("summaryReport");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "summaryReport");
    }

    @Override
    public boolean isValid() {
        // Basic validation: status and reportDate should not be empty, totalBooks non-negative
        return status != null && !status.isEmpty() && reportDate != null && !reportDate.isEmpty() && totalBooks >= 0;
    }
}