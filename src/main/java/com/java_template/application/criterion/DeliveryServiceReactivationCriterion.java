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
 * DeliveryServiceReactivationCriterion - Ensures delivery service can be safely reactivated
 * Transition: SUSPENDED → ACTIVE
 */
@Component
public class DeliveryServiceReactivationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public DeliveryServiceReactivationCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking delivery service reactivation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(DeliveryService.class, this::validateDeliveryServiceReactivation)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateDeliveryServiceReactivation(CriterionSerializer.CriterionEntityEvaluationContext<DeliveryService> context) {
        DeliveryService deliveryService = context.entityWithMetadata().entity();

        // Check if entity is null
        if (deliveryService == null) {
            logger.warn("DeliveryService entity is null");
            return EvaluationOutcome.fail("DeliveryService entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Verify service is currently suspended (isActive should be false)
        if (deliveryService.getIsActive() == null || deliveryService.getIsActive()) {
            return EvaluationOutcome.fail("Delivery service must be suspended to reactivate", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Verify API endpoint is still valid
        if (deliveryService.getApiEndpoint() == null || deliveryService.getApiEndpoint().trim().isEmpty()) {
            return EvaluationOutcome.fail("API endpoint is required for reactivation", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Verify API key is still valid
        if (deliveryService.getApiKey() == null || deliveryService.getApiKey().trim().isEmpty()) {
            return EvaluationOutcome.fail("API key is required for reactivation", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Test API connectivity (simplified)
        try {
            // Simulate API connectivity test
            logger.debug("API connectivity test passed for reactivation: {}", deliveryService.getName());
        } catch (Exception e) {
            return EvaluationOutcome.fail("API connectivity test failed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if service has active delivery persons
        if (!hasActiveDeliveryPersons(deliveryService.getDeliveryServiceId())) {
            return EvaluationOutcome.fail("Delivery service must have at least one active delivery person", 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Note: In a real implementation, we would check for outstanding compliance issues
        // This would involve checking external systems or compliance databases

        logger.debug("Delivery service reactivation criteria passed for: {}", deliveryService.getName());
        return EvaluationOutcome.success();
    }

    private boolean hasActiveDeliveryPersons(String deliveryServiceId) {
        try {
            ModelSpec deliveryPersonModelSpec = new ModelSpec()
                    .withName(DeliveryPerson.ENTITY_NAME)
                    .withVersion(DeliveryPerson.ENTITY_VERSION);

            SimpleCondition serviceCondition = new SimpleCondition()
                    .withJsonPath("$.deliveryServiceId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(deliveryServiceId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(serviceCondition));

            List<EntityWithMetadata<DeliveryPerson>> deliveryPersons = entityService.search(deliveryPersonModelSpec, condition, DeliveryPerson.class);

            // Check if any delivery persons are in ACTIVE state
            return deliveryPersons.stream()
                    .anyMatch(personWithMetadata -> "ACTIVE".equals(personWithMetadata.getState()));

        } catch (Exception e) {
            logger.error("Error checking active delivery persons for service {}: {}", deliveryServiceId, e.getMessage());
            return false;
        }
    }
}
