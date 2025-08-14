package com.java_template.application.processor;

import com.java_template.application.entity.commentanalysisreport.version_1.CommentAnalysisReport;
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

@Component
public class SendEmailReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendEmailReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SendEmailReportProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CommentAnalysisReport for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CommentAnalysisReport.class)
            .validate(this::isValidEntity, "Invalid CommentAnalysisReport entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CommentAnalysisReport entity) {
        return entity != null && entity.getPostId() != null && entity.getHtmlReport() != null;
    }

    private CommentAnalysisReport processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CommentAnalysisReport> context) {
        CommentAnalysisReport report = context.entity();
        // TODO: Implement email sending logic
        logger.info("Sending email report for postId: {}", report.getPostId());

        // Simulate email sending success
        // In real implementation, integrate email service to send report.getHtmlReport()

        return report;
    }
}
