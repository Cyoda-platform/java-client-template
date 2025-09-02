package com.java_template.application.processor;

import com.java_template.application.entity.commentanalysisrequest.version_1.CommentAnalysisRequest;
import com.java_template.application.entity.emailreport.version_1.EmailReport;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Component
public class CommentAnalysisRequestStartEmailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CommentAnalysisRequestStartEmailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CommentAnalysisRequestStartEmailProcessor(SerializerFactory serializerFactory, 
                                                    EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CommentAnalysisRequest start email for request: {}", request.getId());

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
            // Get EmailReport entity by requestId
            Condition requestIdCondition = Condition.of("$.requestId", "EQUALS", entity.getRequestId());
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(java.util.List.of(requestIdCondition));

            Optional<com.java_template.common.dto.EntityResponse<EmailReport>> emailReportResponse = 
                entityService.getFirstItemByCondition(
                    EmailReport.class, 
                    EmailReport.ENTITY_NAME, 
                    EmailReport.ENTITY_VERSION, 
                    condition, 
                    true
                );

            if (emailReportResponse.isPresent()) {
                EmailReport emailReport = emailReportResponse.get().getData();
                
                // Update EmailReport entity with transition "send_email"
                entityService.update(emailReportResponse.get().getMetadata().getId(), emailReport, "send_email");
                
                logger.info("Updated EmailReport for sending email for requestId: {}", entity.getRequestId());
            } else {
                logger.error("EmailReport not found for requestId: {}", entity.getRequestId());
                throw new RuntimeException("EmailReport not found for requestId: " + entity.getRequestId());
            }
            
        } catch (Exception e) {
            logger.error("Failed to update EmailReport for requestId: {}", entity.getRequestId(), e);
            throw new RuntimeException("Failed to update EmailReport: " + e.getMessage(), e);
        }

        return entity;
    }
}
