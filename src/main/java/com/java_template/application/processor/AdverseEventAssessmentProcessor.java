package com.java_template.application.processor;

import com.java_template.application.entity.adverse_event.version_1.AdverseEvent;
import com.java_template.common.dto.EntityWithMetadata;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Processor for assessing adverse events and determining SAE status
 * Handles initial assessment and SAE flagging with notifications
 */
@Component
public class AdverseEventAssessmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdverseEventAssessmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AdverseEventAssessmentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(AdverseEvent.class)
                .validate(this::isValidEntityWithMetadata, "Invalid adverse event wrapper")
                .map(this::processAdverseEventAssessment)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<AdverseEvent> entityWithMetadata) {
        AdverseEvent entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<AdverseEvent> processAdverseEventAssessment(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<AdverseEvent> context) {

        EntityWithMetadata<AdverseEvent> entityWithMetadata = context.entityResponse();
        AdverseEvent adverseEvent = entityWithMetadata.entity();

        logger.debug("Assessing adverse event: {}", adverseEvent.getAdverseEventId());

        // Assess SAE status
        assessSAEStatus(adverseEvent);
        
        // Set follow-up requirements
        setFollowUpRequirements(adverseEvent);
        
        // Update timestamps
        adverseEvent.setUpdatedAt(LocalDateTime.now());

        // Log SAE alert if applicable
        if (Boolean.TRUE.equals(adverseEvent.getIsSAE())) {
            logger.warn("SAE ALERT: Serious Adverse Event reported for subject {} - AE ID: {}", 
                       adverseEvent.getSubjectId(), adverseEvent.getAdverseEventId());
            // In a real implementation, this would trigger email notifications
        }

        logger.info("Adverse event {} assessment completed", adverseEvent.getAdverseEventId());

        return entityWithMetadata;
    }

    private void assessSAEStatus(AdverseEvent adverseEvent) {
        // Determine if this is an SAE based on seriousness
        boolean isSAE = "serious".equalsIgnoreCase(adverseEvent.getSeriousness());
        
        // Additional SAE criteria could be checked here:
        // - Death
        // - Life-threatening
        // - Hospitalization required
        // - Persistent or significant disability
        // - Congenital anomaly
        // - Other medically important events
        
        adverseEvent.setIsSAE(isSAE);
        
        logger.debug("AE {} SAE status determined: {}", 
                    adverseEvent.getAdverseEventId(), isSAE);
    }

    private void setFollowUpRequirements(AdverseEvent adverseEvent) {
        // Set follow-up due date based on SAE status and severity
        if (Boolean.TRUE.equals(adverseEvent.getIsSAE())) {
            // SAEs require immediate follow-up (24-48 hours)
            adverseEvent.setFollowUpDueDate(LocalDate.now().plusDays(1));
        } else if ("severe".equalsIgnoreCase(adverseEvent.getSeverity())) {
            // Severe non-SAE events require follow-up within 7 days
            adverseEvent.setFollowUpDueDate(LocalDate.now().plusDays(7));
        } else if ("moderate".equalsIgnoreCase(adverseEvent.getSeverity())) {
            // Moderate events require follow-up within 14 days
            adverseEvent.setFollowUpDueDate(LocalDate.now().plusDays(14));
        }
        // Mild events may not require specific follow-up
        
        logger.debug("Follow-up due date set for AE {}: {}", 
                    adverseEvent.getAdverseEventId(), adverseEvent.getFollowUpDueDate());
    }
}
