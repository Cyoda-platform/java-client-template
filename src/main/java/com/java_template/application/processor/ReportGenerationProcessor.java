package com.java_template.application.processor;

import com.java_template.application.entity.datasource.version_1.DataSource;
import com.java_template.application.entity.report.version_1.Report;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ReportGenerationProcessor
 * 
 * Generates a report entity based on analysis results.
 * Used in DataSource workflow transition: analysis_complete
 */
@Component
public class ReportGenerationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReportGenerationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ReportGenerationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Report generation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(DataSource.class)
                .validate(this::isValidEntityWithMetadata, "Invalid DataSource entity")
                .map(this::processReportGeneration)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<DataSource> entityWithMetadata) {
        DataSource entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic for report generation
     */
    private EntityWithMetadata<DataSource> processReportGeneration(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<DataSource> context) {

        EntityWithMetadata<DataSource> entityWithMetadata = context.entityResponse();
        DataSource dataSource = entityWithMetadata.entity();

        logger.info("Generating report for DataSource: {}", dataSource.getDataSourceId());

        try {
            // Create new Report entity
            Report report = new Report();
            report.setReportId(generateReportId());
            report.setDataSourceId(dataSource.getDataSourceId());
            report.setTitle("Analysis Report for " + dataSource.getName());
            report.setGeneratedAt(LocalDateTime.now());
            report.setReportFormat("HTML");

            // Create analysis results based on DataSource data
            Map<String, Object> analysisResults = createAnalysisResults(dataSource);
            report.setAnalysisResults(analysisResults);

            // Generate summary
            String summary = generateSummary(dataSource, analysisResults);
            report.setSummary(summary);

            // Create the Report entity using EntityService
            EntityWithMetadata<Report> createdReport = entityService.create(report);
            
            logger.info("Report created successfully with ID: {} for DataSource: {}", 
                       createdReport.metadata().getId(), dataSource.getDataSourceId());

        } catch (Exception e) {
            logger.error("Failed to generate report for DataSource: {}", dataSource.getDataSourceId(), e);
            throw new RuntimeException("Failed to generate report: " + e.getMessage(), e);
        }

        // Return the original DataSource entity (cannot update current entity)
        return entityWithMetadata;
    }

    /**
     * Generate a unique report ID
     */
    private String generateReportId() {
        return "rpt-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Create analysis results map based on DataSource data
     */
    private Map<String, Object> createAnalysisResults(DataSource dataSource) {
        Map<String, Object> results = new HashMap<>();
        
        results.put("dataSourceName", dataSource.getName());
        results.put("dataSourceUrl", dataSource.getUrl());
        results.put("recordCount", dataSource.getRecordCount());
        results.put("fileSize", dataSource.getFileSize());
        results.put("lastFetchTime", dataSource.getLastFetchTime());
        results.put("lastAnalysisTime", dataSource.getLastAnalysisTime());
        results.put("checksum", dataSource.getChecksum());
        
        return results;
    }

    /**
     * Generate executive summary based on analysis results
     */
    private String generateSummary(DataSource dataSource, Map<String, Object> analysisResults) {
        StringBuilder summary = new StringBuilder();
        
        summary.append("Data Analysis Summary for ").append(dataSource.getName()).append("\n\n");
        summary.append("Dataset contains ").append(dataSource.getRecordCount()).append(" records.\n");
        summary.append("File size: ").append(dataSource.getFileSize()).append(" bytes.\n");
        summary.append("Data fetched on: ").append(dataSource.getLastFetchTime()).append("\n");
        summary.append("Analysis completed on: ").append(dataSource.getLastAnalysisTime()).append("\n");
        
        if (dataSource.getDescription() != null) {
            summary.append("Description: ").append(dataSource.getDescription()).append("\n");
        }
        
        return summary.toString();
    }
}
