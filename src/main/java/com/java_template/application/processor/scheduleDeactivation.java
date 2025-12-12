package com.java_template.application.processor;

import com.java_template.application.entity.customer.version_1.Customer;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Processor to schedule customer deactivation
 * 
 * This processor is triggered during the active -> termination_pending transition.
 * It schedules the deactivation process and sets up termination workflows.
 */
@Component
public class scheduleDeactivation implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(scheduleDeactivation.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public scheduleDeactivation(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Customer.class)
                .validate(this::isValidEntityWithMetadata, "Invalid customer entity wrapper")
                .map(this::processDeactivationScheduling)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Customer> entityWithMetadata) {
        Customer customer = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        return customer != null && customer.isValid() && technicalId != null;
    }

    /**
     * Main business logic for scheduling deactivation
     */
    private EntityWithMetadata<Customer> processDeactivationScheduling(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Customer> context) {

        EntityWithMetadata<Customer> entityWithMetadata = context.entityResponse();
        Customer customer = entityWithMetadata.entity();

        logger.info("Scheduling deactivation for customer: {}", customer.getCustomerId());

        // Initialize metadata if not present
        if (customer.getMetadata() == null) {
            customer.setMetadata(new HashMap<>());
        }

        Map<String, Object> metadata = customer.getMetadata();
        
        // Create deactivation schedule
        String scheduleId = "DEACT-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDateTime scheduledFor = LocalDateTime.now().plusDays(30); // 30-day notice period
        
        Map<String, Object> deactivationSchedule = new HashMap<>();
        deactivationSchedule.put("scheduleId", scheduleId);
        deactivationSchedule.put("scheduledAt", LocalDateTime.now().toString());
        deactivationSchedule.put("scheduledFor", scheduledFor.toString());
        deactivationSchedule.put("reason", "Customer requested deactivation");
        deactivationSchedule.put("status", "scheduled");
        deactivationSchedule.put("noticePeriodDays", 30);
        deactivationSchedule.put("canCancel", true);
        
        metadata.put("deactivationSchedule", deactivationSchedule);

        // Set up notification schedule
        Map<String, Object> notifications = new HashMap<>();
        notifications.put("initialNotificationSent", false);
        notifications.put("reminderNotificationSent", false);
        notifications.put("finalNotificationSent", false);
        notifications.put("customerNotified", false);
        
        metadata.put("deactivationNotifications", notifications);

        // Add termination tracking
        metadata.put("terminationPending", true);
        metadata.put("terminationInitiatedAt", LocalDateTime.now().toString());
        metadata.put("terminationReason", "Customer request");

        // Update timestamps
        customer.setUpdatedAt(LocalDateTime.now());

        logger.info("Deactivation scheduled for customer {} with schedule ID: {} for date: {}", 
                   customer.getCustomerId(), scheduleId, scheduledFor);

        return entityWithMetadata;
    }
}
