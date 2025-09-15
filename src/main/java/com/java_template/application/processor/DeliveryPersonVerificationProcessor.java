package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.deliveryperson.version_1.DeliveryPerson;
import com.java_template.application.entity.deliveryservice.version_1.DeliveryService;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DeliveryPersonVerificationProcessor - Handles delivery person verification workflow transition
 * Transition: PENDING_VERIFICATION → ACTIVE
 */
@Component
public class DeliveryPersonVerificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryPersonVerificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public DeliveryPersonVerificationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing delivery person verification for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(DeliveryPerson.class)
                .validate(this::isValidEntityWithMetadata, "Invalid delivery person entity wrapper")
                .map(this::processDeliveryPersonVerification)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<DeliveryPerson> entityWithMetadata) {
        DeliveryPerson entity = entityWithMetadata.entity();
        return entity != null && entity.isValid();
    }

    private EntityWithMetadata<DeliveryPerson> processDeliveryPersonVerification(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<DeliveryPerson> context) {

        EntityWithMetadata<DeliveryPerson> entityWithMetadata = context.entityResponse();
        DeliveryPerson deliveryPerson = entityWithMetadata.entity();

        logger.debug("Processing delivery person verification: {}", deliveryPerson.getDeliveryPersonId());

        // Set availability and update timestamp
        deliveryPerson.setIsAvailable(true);
        deliveryPerson.setIsOnline(false); // Available but not online yet
        deliveryPerson.setUpdatedAt(LocalDateTime.now());

        // Verify required documents (simulated)
        if (deliveryPerson.getVehicleDetails() == null) {
            throw new IllegalArgumentException("Vehicle details are required for verification");
        }

        // Verify vehicle details for cars and motorcycles
        if ("CAR".equals(deliveryPerson.getVehicleType()) || "MOTORCYCLE".equals(deliveryPerson.getVehicleType())) {
            if (deliveryPerson.getVehicleDetails().getLicensePlate() == null || 
                deliveryPerson.getVehicleDetails().getLicensePlate().trim().isEmpty()) {
                throw new IllegalArgumentException("License plate is required for cars and motorcycles");
            }
            if (deliveryPerson.getVehicleDetails().getModel() == null || 
                deliveryPerson.getVehicleDetails().getModel().trim().isEmpty()) {
                throw new IllegalArgumentException("Vehicle model is required for cars and motorcycles");
            }
        }

        // Verify phone is valid (simulated verification)
        if (deliveryPerson.getPhone() == null || deliveryPerson.getPhone().trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number must be verified");
        }

        // Notify delivery service about verified delivery person
        notifyDeliveryService(deliveryPerson.getDeliveryServiceId());

        logger.info("Delivery person verified: {}", deliveryPerson.getName());
        
        return entityWithMetadata;
    }

    private void notifyDeliveryService(String deliveryServiceId) {
        try {
            ModelSpec deliveryServiceModelSpec = new ModelSpec()
                    .withName(DeliveryService.ENTITY_NAME)
                    .withVersion(DeliveryService.ENTITY_VERSION);

            SimpleCondition serviceCondition = new SimpleCondition()
                    .withJsonPath("$.deliveryServiceId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(deliveryServiceId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(serviceCondition));

            List<EntityWithMetadata<DeliveryService>> deliveryServices = entityService.search(deliveryServiceModelSpec, condition, DeliveryService.class);

            if (!deliveryServices.isEmpty()) {
                DeliveryService deliveryService = deliveryServices.get(0).entity();
                // In a real implementation, we would send delivery_person_verified_notification to delivery service
                logger.info("Notified delivery service {} about verified delivery person", deliveryService.getName());
            }

        } catch (Exception e) {
            logger.error("Error notifying delivery service {}: {}", deliveryServiceId, e.getMessage());
        }
    }
}
