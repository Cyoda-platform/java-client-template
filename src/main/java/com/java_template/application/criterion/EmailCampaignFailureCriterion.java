package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
import com.java_template.application.entity.emaildelivery.version_1.EmailDelivery;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * EmailCampaignFailureCriterion - Checks if the campaign execution has failed
 * Entity: EmailCampaign
 * Transition: EXECUTING → FAILED
 */
@Component
public class EmailCampaignFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EmailCampaignFailureCriterion(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking EmailCampaign failure criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(EmailCampaign.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<EmailCampaign> context) {
        EmailCampaign entity = context.entityWithMetadata().entity();

        if (entity == null || !entity.isValid()) {
            return EvaluationOutcome.fail("Invalid EmailCampaign entity", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check execution timeout (2 hours)
        if (entity.getExecutedDate() != null) {
            LocalDateTime timeoutThreshold = entity.getExecutedDate().plusHours(2);
            if (LocalDateTime.now().isAfter(timeoutThreshold)) {
                logger.warn("Campaign {} exceeded timeout threshold", entity.getCampaignName());
                return EvaluationOutcome.success(); // Success means the failure condition is met
            }
        }

        try {
            ModelSpec deliveryModelSpec = new ModelSpec()
                    .withName(EmailDelivery.ENTITY_NAME)
                    .withVersion(EmailDelivery.ENTITY_VERSION);

            SimpleCondition campaignCondition = new SimpleCondition()
                    .withJsonPath("$.campaignId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(entity.getId()));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(campaignCondition));

            List<EntityWithMetadata<EmailDelivery>> deliveries = 
                    entityService.search(deliveryModelSpec, condition, EmailDelivery.class);

            if (deliveries.isEmpty()) {
                return EvaluationOutcome.fail("No deliveries found for campaign", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            List<String> failedStates = Arrays.asList("FAILED", "BOUNCED");
            long failedCount = deliveries.stream()
                    .filter(d -> failedStates.contains(d.entity().getDeliveryStatus()))
                    .count();

            // Check if failure rate exceeds 80%
            double failureRate = (double) failedCount / deliveries.size();
            if (failureRate > 0.8) {
                logger.warn("Campaign {} has high failure rate: {}%", entity.getCampaignName(), failureRate * 100);
                return EvaluationOutcome.success(); // Success means the failure condition is met
            }

            return EvaluationOutcome.fail("Campaign failure conditions not met", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);

        } catch (Exception e) {
            logger.error("Error checking campaign failure: {}", e.getMessage());
            return EvaluationOutcome.success(); // Treat errors as failure condition met
        }
    }
}
