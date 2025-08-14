package com.java_template.application.processor;

import com.java_template.application.entity.dataanalysisreport.version_1.DataAnalysisReport;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AnalysisProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AnalysisProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DataAnalysisReport for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DataAnalysisReport.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(DataAnalysisReport entity) {
        return entity != null && entity.getStatus() != null && !entity.getStatus().isEmpty();
    }

    private DataAnalysisReport processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<DataAnalysisReport> context) {
        DataAnalysisReport entity = context.entity();
        // Business logic implementation:
        // Based on functional requirements, perform data analysis on related DataDownloadJob
        // Here we assume that the actual analysis (summaryStatistics, trendAnalysis) is done by an external system or library
        // Set status to 'COMPLETED' if analysis results are present, else 'FAILED'

        if (entity.getSummaryStatistics() != null && !entity.getSummaryStatistics().isEmpty()
                && entity.getTrendAnalysis() != null && !entity.getTrendAnalysis().isEmpty()) {
            entity.setStatus("COMPLETED");
        } else {
            entity.setStatus("FAILED");
        }

        // Set createdAt timestamp if not set
        if (entity.getCreatedAt() == null || entity.getCreatedAt().isEmpty()) {
            entity.setCreatedAt(java.time.Instant.now().toString());
        }

        logger.info("AnalysisProcessor completed processing DataAnalysisReport with status: {}", entity.getStatus());
        return entity;
    }
}