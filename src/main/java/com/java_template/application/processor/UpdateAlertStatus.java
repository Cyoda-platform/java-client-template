package com.java_template.application.processor;

import com.example.application.entity.alert.version_1.Alert;
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * UpdateAlertStatus Processor - Alert Review and Case Management Workflow
 * 
 * Updates alert status based on investigation outcomes.
 * Transitions alerts through resolution states.
 */
@Component
public class UpdateAlertStatus implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpdateAlertStatus.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public UpdateAlertStatus(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Updating alert status for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Alert.class)
                .validate(this::isValidEntityWithMetadata, "Invalid alert entity")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Alert> entityWithMetadata) {
        Alert entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    private EntityWithMetadata<Alert> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Alert> context) {

        EntityWithMetadata<Alert> entityWithMetadata = context.entityResponse();
        Alert alert = entityWithMetadata.entity();

        logger.debug("Updating status for alert: {}", alert.getId());

        // Update alert status based on investigation outcome
        updateAlertStatus(alert);

        // Update metadata
        if (alert.getMetadata() == null) {
            alert.setMetadata(new HashMap<>());
        }
        alert.getMetadata().put("statusUpdated", true);
        alert.getMetadata().put("statusUpdateTime", LocalDateTime.now().toString());

        logger.info("Alert {} status updated to: {}", alert.getId(), alert.getStatus());
        return entityWithMetadata;
    }

    private void updateAlertStatus(Alert alert) {
        // Determine new status based on current state and investigation
        Alert.Status newStatus = determineNewStatus(alert);
        alert.setStatus(newStatus);

        // Add status transition to metadata
        if (alert.getMetadata() == null) {
            alert.setMetadata(new HashMap<>());
        }

        Map<String, Object> statusTransition = new HashMap<>();
        statusTransition.put("previousStatus", alert.getStatus());
        statusTransition.put("newStatus", newStatus);
        statusTransition.put("transitionTime", LocalDateTime.now().toString());
        statusTransition.put("reason", getStatusTransitionReason(newStatus));

        alert.getMetadata().put("lastStatusTransition", statusTransition);
    }

    private Alert.Status determineNewStatus(Alert alert) {
        // Simulate status determination based on investigation
        // In production, this would check case status and investigation findings
        
        if (alert.getRelatedCaseId() != null) {
            // If case exists, alert is in review
            if (alert.getStatus() == Alert.Status.OPEN) {
                return Alert.Status.IN_REVIEW;
            } else if (alert.getStatus() == Alert.Status.IN_REVIEW) {
                // Simulate investigation completion
                return Alert.Status.CLOSED;
            }
        }

        // Default: close the alert
        return Alert.Status.CLOSED;
    }

    private String getStatusTransitionReason(Alert.Status status) {
        return switch (status) {
            case OPEN -> "Alert created and awaiting review";
            case IN_REVIEW -> "Alert assigned to analyst for investigation";
            case ESCALATED -> "Alert escalated to senior management";
            case CLOSED -> "Alert investigation completed";
            default -> "Status updated";
        };
    }
}

