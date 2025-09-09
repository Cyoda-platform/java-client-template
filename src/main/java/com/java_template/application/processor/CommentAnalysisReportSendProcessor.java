package com.java_template.application.processor;

import com.java_template.application.entity.commentanalysisreport.version_1.CommentAnalysisReport;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CommentAnalysisReportSendProcessor
 * 
 * Marks report as sent after email delivery.
 */
@Component
public class CommentAnalysisReportSendProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CommentAnalysisReportSendProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CommentAnalysisReportSendProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Marking report as sent for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(CommentAnalysisReport.class)
                .validate(this::isValidEntityWithMetadata, "Invalid CommentAnalysisReport")
                .map(this::processReportSent)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<CommentAnalysisReport> entityWithMetadata) {
        CommentAnalysisReport report = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        return report != null && report.isValid() && technicalId != null;
    }

    private EntityWithMetadata<CommentAnalysisReport> processReportSent(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<CommentAnalysisReport> context) {

        EntityWithMetadata<CommentAnalysisReport> entityWithMetadata = context.entityResponse();
        CommentAnalysisReport report = entityWithMetadata.entity();
        UUID reportId = entityWithMetadata.metadata().getId();

        logger.info("Marking report as sent: {}", reportId);

        // Set report as sent timestamp
        report.setSentAt(LocalDateTime.now());

        logger.info("Report {} marked as sent at {}", reportId, report.getSentAt());

        return entityWithMetadata;
    }
}
