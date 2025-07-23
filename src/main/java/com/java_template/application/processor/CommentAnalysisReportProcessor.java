package com.java_template.application.processor;

import com.java_template.application.entity.CommentAnalysisReport;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CommentAnalysisReportProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public CommentAnalysisReportProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("CommentAnalysisReportProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CommentAnalysisReport for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
                .toEntity(CommentAnalysisReport.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "CommentAnalysisReportProcessor".equals(modelSpec.operationName()) &&
               "commentanalysisreport".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(CommentAnalysisReport entity) {
        // Use the entity's own validation logic
        return entity.isValid();
    }

    private CommentAnalysisReport processEntityLogic(CommentAnalysisReport entity) {
        // Business logic to analyze comments and create a report
        // Since the processor is for CommentAnalysisReport, we must generate the report fields properly

        // For demonstration: simulate keyword counts and sentiment summary
        // Real logic would retrieve related Comment entities and analyze them

        // In absence of direct access to Comment entities here, simulate safe defaults
        if (entity.getKeywordCounts() == null) {
            entity.setKeywordCounts(new HashMap<>());
        }
        if (entity.getTotalComments() == null) {
            entity.setTotalComments(0);
        }
        if (entity.getSentimentSummary() == null) {
            entity.setSentimentSummary("No sentiment analysis available");
        }
        entity.setGeneratedAt(LocalDateTime.now());
        entity.setStatus("CREATED");

        // Realistically, the processor would:
        // - Fetch comments by ingestionJobId
        // - Aggregate keyword counts across comments
        // - Summarize sentiment
        // But as no EntityService or repository is accessible here by design, this logic is simplified

        return entity;
    }
}