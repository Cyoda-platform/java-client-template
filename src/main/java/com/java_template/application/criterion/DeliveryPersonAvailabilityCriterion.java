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

/**
 * DeliveryPersonAvailabilityCriterion - Ensures delivery person can go online/be assigned
 * Transition: OFFLINE → ACTIVE or ACTIVE → BUSY
 */
@Component
public class DeliveryPersonAvailabilityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public DeliveryPersonAvailabilityCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking delivery person availability criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(DeliveryPerson.class, this::validateDeliveryPersonAvailability)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateDeliveryPersonAvailability(CriterionSerializer.CriterionEntityEvaluationContext<DeliveryPerson> context) {
        DeliveryPerson deliveryPerson = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().getState();

        // Check if entity is null
        if (deliveryPerson == null) {
            logger.warn("DeliveryPerson entity is null");
            return EvaluationOutcome.fail("DeliveryPerson entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Verify parent delivery service is active
        if (!isParentDeliveryServiceActive(deliveryPerson.getDeliveryServiceId())) {
            return EvaluationOutcome.fail("Parent delivery service must be active", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check availability based on current state
        if ("OFFLINE".equals(currentState)) {
            // Going online - check if person is available
            if (deliveryPerson.getIsAvailable() == null || !deliveryPerson.getIsAvailable()) {
                return EvaluationOutcome.fail("Delivery person must be available to go online", 
                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        } else if ("ACTIVE".equals(currentState)) {
            // Being assigned - check if person is online and available
            if (deliveryPerson.getIsOnline() == null || !deliveryPerson.getIsOnline()) {
                return EvaluationOutcome.fail("Delivery person must be online to be assigned", 
                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            if (deliveryPerson.getIsAvailable() == null || !deliveryPerson.getIsAvailable()) {
                return EvaluationOutcome.fail("Delivery person must be available to be assigned", 
                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        // Verify vehicle details are complete for assignment
        if ("ACTIVE".equals(currentState) && 
            ("CAR".equals(deliveryPerson.getVehicleType()) || "MOTORCYCLE".equals(deliveryPerson.getVehicleType()))) {
            
            if (deliveryPerson.getVehicleDetails() == null ||
                deliveryPerson.getVehicleDetails().getLicensePlate() == null ||
                deliveryPerson.getVehicleDetails().getLicensePlate().trim().isEmpty()) {
                return EvaluationOutcome.fail("Vehicle details must be complete for assignment", 
                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        logger.debug("Delivery person availability criteria passed for: {}", deliveryPerson.getName());
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
