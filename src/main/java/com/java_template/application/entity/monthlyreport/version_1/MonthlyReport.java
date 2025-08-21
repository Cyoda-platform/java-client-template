package com.java_template.application.entity.monthlyreport.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class MonthlyReport implements CyodaEntity {
    public static final String ENTITY_NAME = "MonthlyReport";
    public static final Integer ENTITY_VERSION = 1;

    private String month; // YYYY-MM, report month
    private String generatedAt; // ISO datetime
    private Integer totalUsers;
    private Integer newUsers;
    private Integer changedUsers;
    private String reportUrl; // storage link or path
    private String status; // GENERATING, READY, PUBLISHING, PUBLISHED, FAILED
    private List<String> deliveredTo;
    private String deliveryStatus; // SENT, FAILED

    public MonthlyReport() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (month == null || month.isBlank()) return false;
        if (generatedAt == null || generatedAt.isBlank()) return false;
        if (totalUsers == null) return false;
        if (newUsers == null) return false;
        if (changedUsers == null) return false;
        return true;
    }
}