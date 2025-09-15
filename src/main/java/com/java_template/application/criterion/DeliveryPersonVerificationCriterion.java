package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.deliveryperson.version_1.DeliveryPerson;
import com.java_template.application.entity.deliveryservice.version_1.DeliveryService;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * DeliveryPersonVerificationCriterion - Validates delivery person meets verification requirements
 * Transition: PENDING_VERIFICATION → ACTIVE
 */
@Component
public class DeliveryPersonVerificationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final List<String> VALID_VEHICLE_TYPES = Arrays.asList("BIKE", "CAR", "MOTORCYCLE");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9\\s\\-()]{7,20}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");

    public DeliveryPersonVerificationCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking delivery person verification criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(DeliveryPerson.class, this::validateDeliveryPersonVerification)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateDeliveryPersonVerification(CriterionSerializer.CriterionEntityEvaluationContext<DeliveryPerson> context) {
        DeliveryPerson deliveryPerson = context.entityWithMetadata().entity();

        // Check if entity is null
        if (deliveryPerson == null) {
            logger.warn("DeliveryPerson entity is null");
            return EvaluationOutcome.fail("DeliveryPerson entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Verify name is not empty
        if (deliveryPerson.getName() == null || deliveryPerson.getName().trim().isEmpty()) {
            return EvaluationOutcome.fail("Delivery person name cannot be empty", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Verify phone number format
        if (deliveryPerson.getPhone() == null || !PHONE_PATTERN.matcher(deliveryPerson.getPhone()).matches()) {
            return EvaluationOutcome.fail("Valid phone number is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Verify email format if provided
        if (deliveryPerson.getEmail() != null && !deliveryPerson.getEmail().trim().isEmpty()) {
            if (!EMAIL_PATTERN.matcher(deliveryPerson.getEmail()).matches()) {
                return EvaluationOutcome.fail("Valid email address is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        // Verify vehicle type is valid
        if (!VALID_VEHICLE_TYPES.contains(deliveryPerson.getVehicleType())) {
            return EvaluationOutcome.fail("Vehicle type must be one of: " + String.join(", ", VALID_VEHICLE_TYPES), 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Verify vehicle details for cars and motorcycles
        if ("CAR".equals(deliveryPerson.getVehicleType()) || "MOTORCYCLE".equals(deliveryPerson.getVehicleType())) {
            if (deliveryPerson.getVehicleDetails() == null) {
                return EvaluationOutcome.fail("Vehicle details are required for cars and motorcycles", 
                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            DeliveryPerson.VehicleDetails vehicleDetails = deliveryPerson.getVehicleDetails();
            
            if (vehicleDetails.getLicensePlate() == null || vehicleDetails.getLicensePlate().trim().isEmpty()) {
                return EvaluationOutcome.fail("License plate is required for cars and motorcycles", 
                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            if (vehicleDetails.getModel() == null || vehicleDetails.getModel().trim().isEmpty()) {
                return EvaluationOutcome.fail("Vehicle model is required for cars and motorcycles", 
                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        // Verify parent delivery service is active
        if (!isParentDeliveryServiceActive(deliveryPerson.getDeliveryServiceId())) {
            return EvaluationOutcome.fail("Parent delivery service must be active", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Note: In a real implementation, we would verify:
        // - Background check results
        // - Driver's license validity
        // - Insurance coverage
        // - Vehicle registration

        logger.debug("Delivery person verification criteria passed for: {}", deliveryPerson.getName());
        return EvaluationOutcome.success();
    }

    private boolean isParentDeliveryServiceActive(String deliveryServiceId) {
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

            return !deliveryServices.isEmpty() && "ACTIVE".equals(deliveryServices.get(0).getState());

        } catch (Exception e) {
            logger.error("Error checking parent delivery service status for {}: {}", deliveryServiceId, e.getMessage());
            return false;
        }
    }
}
