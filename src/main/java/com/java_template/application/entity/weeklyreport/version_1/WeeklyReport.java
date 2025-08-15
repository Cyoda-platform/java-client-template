package com.java_template.application.entity.weeklyreport.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class WeeklyReport implements CyodaEntity {
    public static final String ENTITY_NAME = "WeeklyReport";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String report_id; // report identifier
    private String period_start; // timestamp
    private String period_end; // timestamp
    private String generated_on; // timestamp
    private Map<String, Object> summary_metrics; // KPIs: sales_volume, revenue_by_product, inventory_turnover
    private List<String> top_products; // top N products by sales (product_ids)
    private List<String> restock_list; // products below restock threshold (product_ids)
    private String attachment_url; // link to generated report file
    private List<String> recipients; // emails
    private String status; // CREATED | GENERATING | SENT | FAILED

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
        if (report_id == null || report_id.isBlank()) return false;
        if (period_start == null || period_start.isBlank()) return false;
        if (period_end == null || period_end.isBlank()) return false;
        if (generated_on == null || generated_on.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (recipients == null || recipients.isEmpty()) return false;
        return true;
    }
}