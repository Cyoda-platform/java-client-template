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

import java.util.List;
import java.util.regex.Pattern;

/**
 * DeliveryPersonReactivationCriterion - Ensures delivery person can be safely reactivated
 * Transition: SUSPENDED → ACTIVE
 */
@Component
public class DeliveryPersonReactivationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9\\s\\-()]{7,20}$");

    public DeliveryPersonReactivationCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking delivery person reactivation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(DeliveryPerson.class, this::validateDeliveryPersonReactivation)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateDeliveryPersonReactivation(CriterionSerializer.CriterionEntityEvaluationContext<DeliveryPerson> context) {
        DeliveryPerson deliveryPerson = context.entityWithMetadata().entity();

        // Check if entity is null
        if (deliveryPerson == null) {
            logger.warn("DeliveryPerson entity is null");
            return EvaluationOutcome.fail("DeliveryPerson entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Verify person is currently suspended (should be unavailable and offline)
        if (deliveryPerson.getIsAvailable() == null || deliveryPerson.getIsAvailable() ||
            deliveryPerson.getIsOnline() == null || deliveryPerson.getIsOnline()) {
            return EvaluationOutcome.fail("Delivery person must be suspended to reactivate", 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Verify contact information is still valid
        if (deliveryPerson.getPhone() == null || !PHONE_PATTERN.matcher(deliveryPerson.getPhone()).matches()) {
            return EvaluationOutcome.fail("Valid phone number is required for reactivation", 
                    StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Verify vehicle details are still valid for cars and motorcycles
        if ("CAR".equals(deliveryPerson.getVehicleType()) || "MOTORCYCLE".equals(deliveryPerson.getVehicleType())) {
            if (deliveryPerson.getVehicleDetails() == null ||
                deliveryPerson.getVehicleDetails().getLicensePlate() == null ||
                deliveryPerson.getVehicleDetails().getLicensePlate().trim().isEmpty() ||
                deliveryPerson.getVehicleDetails().getModel() == null ||
                deliveryPerson.getVehicleDetails().getModel().trim().isEmpty()) {
                return EvaluationOutcome.fail("Complete vehicle details are required for reactivation", 
                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        // Verify parent delivery service is active
        if (!isParentDeliveryServiceActive(deliveryPerson.getDeliveryServiceId())) {
            return EvaluationOutcome.fail("Parent delivery service must be active", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Note: In a real implementation, we would check for:
        // - Resolution of suspension reasons
        // - Updated background checks if required
        // - Completion of any required training
        // - Verification of insurance and documentation

        logger.debug("Delivery person reactivation criteria passed for: {}", deliveryPerson.getName());
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
