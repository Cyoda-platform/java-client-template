package com.java_template.application.processor;

import com.java_template.application.entity.commentanalysisrequest.version_1.CommentAnalysisRequest;
import com.java_template.application.entity.emailreport.version_1.EmailReport;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Component
public class CommentAnalysisRequestStartReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CommentAnalysisRequestStartReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CommentAnalysisRequestStartReportProcessor(SerializerFactory serializerFactory, 
                                                     EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CommentAnalysisRequest start report for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CommentAnalysisRequest.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CommentAnalysisRequest entity) {
        return entity != null && entity.isValid();
    }

    private CommentAnalysisRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CommentAnalysisRequest> context) {
        CommentAnalysisRequest entity = context.entity();

        try {
            // Create new EmailReport entity
            EmailReport emailReport = new EmailReport();
            
            // Set requestId reference
            emailReport.setRequestId(entity.getRequestId());
            
            // Set recipientEmail from request
            emailReport.setRecipientEmail(entity.getRecipientEmail());
            
            // Generate unique reportId
            emailReport.setReportId(UUID.randomUUID().toString());
            
            // Save EmailReport entity with transition "prepare_email"
            entityService.save(emailReport);
            
            logger.info("Created EmailReport with reportId: {} for requestId: {}", 
                       emailReport.getReportId(), entity.getRequestId());
            
        } catch (Exception e) {
            logger.error("Failed to create EmailReport for requestId: {}", entity.getRequestId(), e);
            throw new RuntimeException("Failed to create EmailReport: " + e.getMessage(), e);
        }

        return entity;
    }
}
