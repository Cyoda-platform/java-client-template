package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.time.Instant;

@Data
public class ReportMetadata implements CyodaEntity {
    private String reportId;
    private Instant generatedAt;
    private ReportSummary summary;
    private String reportDownloadLink;

    public ReportMetadata() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("reportMetadata");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "reportMetadata");
    }

    @Override
    public boolean isValid() {
        return reportId != null && !reportId.isEmpty() && generatedAt != null;
    }

    @Data
    public static class ReportSummary {
        private ProductPerformance[] topSellingProducts;
        private InventoryStatus[] restockItems;
        private String performanceInsights;

        public ReportSummary() {}

        @Data
        public static class ProductPerformance {
            private int productId;
            private String name;
            private int salesVolume;

            public ProductPerformance() {}
        }

        @Data
        public static class InventoryStatus {
            private int productId;
            private String name;
            private int stockLevel;

            public InventoryStatus() {}
        }
    }
}}