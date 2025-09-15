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
import java.util.Arrays;
import java.util.List;

/**
 * DeliveryPersonRegistrationProcessor - Handles delivery person registration workflow transition
 * Transition: none → PENDING_VERIFICATION
 */
@Component
public class DeliveryPersonRegistrationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryPersonRegistrationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final List<String> VALID_VEHICLE_TYPES = Arrays.asList("BIKE", "CAR", "MOTORCYCLE");

    public DeliveryPersonRegistrationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing delivery person registration for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(DeliveryPerson.class)
                .validate(this::isValidEntityWithMetadata, "Invalid delivery person entity wrapper")
                .map(this::processDeliveryPersonRegistration)
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

    private EntityWithMetadata<DeliveryPerson> processDeliveryPersonRegistration(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<DeliveryPerson> context) {

        EntityWithMetadata<DeliveryPerson> entityWithMetadata = context.entityResponse();
        DeliveryPerson deliveryPerson = entityWithMetadata.entity();

        logger.debug("Processing delivery person registration: {}", deliveryPerson.getDeliveryPersonId());

        // Set registration timestamp
        deliveryPerson.setCreatedAt(LocalDateTime.now());
        deliveryPerson.setUpdatedAt(LocalDateTime.now());
        
        // Set initial availability and online status
        deliveryPerson.setIsAvailable(false);
        deliveryPerson.setIsOnline(false);
        deliveryPerson.setTotalDeliveries(0);
        deliveryPerson.setRating(0.0);

        // Validate phone format (simplified validation)
        if (deliveryPerson.getPhone() == null || !deliveryPerson.getPhone().matches("\\+?[0-9\\-\\s]+")) {
            throw new IllegalArgumentException("Phone number format is invalid");
        }

        // Validate vehicle type
        if (!VALID_VEHICLE_TYPES.contains(deliveryPerson.getVehicleType())) {
            throw new IllegalArgumentException("Vehicle type must be one of: " + String.join(", ", VALID_VEHICLE_TYPES));
        }

        // Check delivery service is active
        validateDeliveryServiceActive(deliveryPerson.getDeliveryServiceId());

        logger.info("Delivery person registered: {}", deliveryPerson.getName());
        return entityWithMetadata;
    }

    private void validateDeliveryServiceActive(String deliveryServiceId) {
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

            if (deliveryServices.isEmpty()) {
                throw new IllegalArgumentException("Delivery service with ID " + deliveryServiceId + " not found");
            }

            String serviceState = deliveryServices.get(0).getState();
            if (!"ACTIVE".equals(serviceState)) {
                throw new IllegalArgumentException("Cannot register delivery person for inactive service");
            }

        } catch (Exception e) {
            logger.error("Error validating delivery service {}: {}", deliveryServiceId, e.getMessage());
            throw new IllegalArgumentException("Cannot register delivery person for inactive service");
        }
    }
}
