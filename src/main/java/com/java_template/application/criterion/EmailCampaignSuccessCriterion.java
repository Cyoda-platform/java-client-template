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

import java.util.Arrays;
import java.util.List;

/**
 * EmailCampaignSuccessCriterion - Checks if the campaign execution has completed successfully
 * Entity: EmailCampaign
 * Transition: EXECUTING → COMPLETED
 */
@Component
public class EmailCampaignSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EmailCampaignSuccessCriterion(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking EmailCampaign success criteria for request: {}", request.getId());
        
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

            List<String> finalStates = Arrays.asList("DELIVERED", "OPENED", "CLICKED", "FAILED", "BOUNCED");
            List<String> successStates = Arrays.asList("DELIVERED", "OPENED", "CLICKED");

            long finalCount = deliveries.stream()
                    .filter(d -> finalStates.contains(d.entity().getDeliveryStatus()))
                    .count();

            long successCount = deliveries.stream()
                    .filter(d -> successStates.contains(d.entity().getDeliveryStatus()))
                    .count();

            // Check if all deliveries are processed
            if (finalCount < deliveries.size()) {
                return EvaluationOutcome.fail("Not all deliveries are processed yet", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            // Check if at least 50% of deliveries were successful
            double successRate = (double) successCount / deliveries.size();
            if (successRate < 0.5) {
                return EvaluationOutcome.fail("Success rate below 50%: " + (successRate * 100) + "%", 
                                            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            logger.debug("Campaign {} completed successfully with {}% success rate", 
                        entity.getCampaignName(), successRate * 100);
            return EvaluationOutcome.success();

        } catch (Exception e) {
            logger.error("Error checking campaign success: {}", e.getMessage());
            return EvaluationOutcome.fail("Error checking campaign success: " + e.getMessage(),
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
