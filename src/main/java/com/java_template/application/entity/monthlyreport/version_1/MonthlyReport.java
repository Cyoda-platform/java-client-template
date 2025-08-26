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
    // Add your entity fields here

    // List of admin recipient emails
    private List<String> adminRecipients;

    // Number of delivery attempts for the report
    private Integer deliveryAttempts;

    // Timestamp when report was generated (ISO-8601 string)
    private String generatedAt;

    // Count of invalid records found in the report
    private Integer invalidRecordsCount;

    // Month covered by the report (e.g., "2025-08")
    private String month;

    // Number of new users in the month
    private Integer newUsers;

    // Published status (use String for enum-like values)
    private String publishedStatus;

    // Reference to the stored report file (e.g., s3 path)
    private String reportFileRef;

    // Sample records included in the report
    private List<SampleRecord> sampleRecords;

    // Total users in the month
    private Integer totalUsers;

    // Number of updated users in the month
    private Integer updatedUsers;

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
        if (publishedStatus == null || publishedStatus.isBlank()) return false;
        if (reportFileRef == null || reportFileRef.isBlank()) return false;

        // Validate numeric fields (must be present and non-negative)
        if (totalUsers == null || totalUsers < 0) return false;
        if (newUsers == null || newUsers < 0) return false;
        if (deliveryAttempts == null || deliveryAttempts < 0) return false;
        if (invalidRecordsCount == null || invalidRecordsCount < 0) return false;
        if (updatedUsers == null || updatedUsers < 0) return false;

        // Validate admin recipients list
        if (adminRecipients == null) return false;
        for (String email : adminRecipients) {
            if (email == null || email.isBlank()) return false;
        }

        // Validate sample records
        if (sampleRecords == null) return false;
        for (SampleRecord sr : sampleRecords) {
            if (sr == null) return false;
            if (sr.getId() == null) return false;
            if (sr.getName() == null || sr.getName().isBlank()) return false;
            if (sr.getProcessingStatus() == null || sr.getProcessingStatus().isBlank()) return false;
        }

        return true;
    }

    @Data
    public static class SampleRecord {
        private Integer id;
        private String name;
        private String processingStatus;
    }
}