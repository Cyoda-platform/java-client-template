package com.java_template.application.entity.monthlyreport.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class MonthlyReport implements CyodaEntity {
    public static final String ENTITY_NAME = "MonthlyReport"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String month; // e.g., "2025-09"
    private String generatedAt; // ISO timestamp when report was generated
    private String deliveryAt; // ISO timestamp when report was delivered (optional)
    private String fileRef; // path or reference to stored file
    private String status; // e.g., "PUBLISHED"
    private Integer totalUsers;
    private Integer newUsers;
    private Integer invalidUsers;

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
        // Validate required string fields
        if (month == null || month.isBlank()) return false;
        if (generatedAt == null || generatedAt.isBlank()) return false;
        if (fileRef == null || fileRef.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        // deliveryAt may be optional, but if present it must not be blank
        if (deliveryAt != null && deliveryAt.isBlank()) return false;

        // Validate numeric fields
        if (totalUsers == null || totalUsers < 0) return false;
        if (newUsers == null || newUsers < 0) return false;
        if (invalidUsers == null || invalidUsers < 0) return false;

        // Consistency: totalUsers should equal newUsers + invalidUsers
        if (totalUsers.intValue() != (newUsers.intValue() + invalidUsers.intValue())) return false;

        return true;
    }
}