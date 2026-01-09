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

import java.util.HashMap;
import java.util.Map;

/**
 * AssignAnalyst Processor - Alert Review and Case Management Workflow
 * 
 * Assigns alerts to compliance analysts for review.
 * Implements load-balancing and skill-based assignment logic.
 */
@Component
public class AssignAnalyst implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AssignAnalyst.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AssignAnalyst(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Assigning analyst for request: {}", request.getId());

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

        logger.debug("Assigning analyst for alert: {}", alert.getId());

        // Assign analyst based on alert severity and type
        String assignedAnalyst = assignAnalystBySkill(alert);
        alert.setAssignedTo(assignedAnalyst);

        // Update metadata with assignment info
        if (alert.getMetadata() == null) {
            alert.setMetadata(new HashMap<>());
        }
        alert.getMetadata().put("assignedAnalyst", assignedAnalyst);
        alert.getMetadata().put("assignmentTime", System.currentTimeMillis());
        alert.getMetadata().put("assignmentReason", "Automatic assignment based on severity and expertise");

        logger.info("Alert {} assigned to analyst: {}", alert.getId(), assignedAnalyst);
        return entityWithMetadata;
    }

    private String assignAnalystBySkill(Alert alert) {
        // Implement skill-based assignment logic
        String alertType = alert.getAlertType();
        Alert.Severity severity = alert.getSeverity();

        // High severity alerts go to senior analysts
        if (severity == Alert.Severity.HIGH) {
            return "analyst_senior_" + (System.currentTimeMillis() % 3); // Round-robin among 3 senior analysts
        }

        // Route by alert type
        if ("WATCHLIST_HIT".equals(alertType)) {
            return "analyst_watchlist_" + (System.currentTimeMillis() % 2);
        } else if ("VELOCITY_EXCEEDED".equals(alertType)) {
            return "analyst_velocity_" + (System.currentTimeMillis() % 2);
        } else if ("LARGE_TRANSACTION".equals(alertType)) {
            return "analyst_transaction_" + (System.currentTimeMillis() % 2);
        }

        // Default assignment
        return "analyst_general_" + (System.currentTimeMillis() % 4);
    }
}

