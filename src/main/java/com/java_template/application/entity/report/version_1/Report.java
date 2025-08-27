package com.java_template.application.entity.report.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Report implements CyodaEntity {
    public static final String ENTITY_NAME = "Report";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String format;
    private String generatedAt; // ISO timestamp
    private String periodEnd; // ISO date
    private String periodStart; // ISO date
    private List<BookSummary> popularTitles;
    private String reportId;
    private String sentAt; // ISO timestamp
    private String status;
    private String titleInsights;
    private Integer totalBooks;
    private Integer totalPageCount;

    public Report() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (reportId == null || reportId.isBlank()) return false;
        if (format == null || format.isBlank()) return false;
        if (generatedAt == null || generatedAt.isBlank()) return false;
        if (periodStart == null || periodStart.isBlank()) return false;
        if (periodEnd == null || periodEnd.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (totalBooks == null || totalBooks < 0) return false;
        if (totalPageCount == null || totalPageCount < 0) return false;
        if (popularTitles != null) {
            for (BookSummary bs : popularTitles) {
                if (bs == null || !bs.isValid()) return false;
            }
        }
        // titleInsights and sentAt can be optional
        return true;
    }

    @Data
    public static class BookSummary {
        private String description;
        private String excerpt;
        private Integer pageCount;
        private String publishDate; // ISO date
        private String title;

        public boolean isValid() {
            if (title == null || title.isBlank()) return false;
            if (pageCount != null && pageCount < 0) return false;
            // other fields optional
            return true;
        }
    }
}