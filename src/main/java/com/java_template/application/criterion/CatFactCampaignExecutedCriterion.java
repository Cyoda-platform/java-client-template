package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
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
 * CatFactCampaignExecutedCriterion - Checks if the associated email campaign has been executed successfully
 * Entity: CatFact
 * Transition: SCHEDULED → SENT
 * 
 * Evaluation Logic:
 * 1. Get the cat fact ID from the entity
 * 2. Query EmailCampaign entities where catFactId matches
 * 3. Check if any campaign is in COMPLETED state
 * 4. Return true if at least one campaign completed successfully
 * 5. Return false otherwise
 */
@Component
public class CatFactCampaignExecutedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CatFactCampaignExecutedCriterion(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking CatFact campaign executed criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(CatFact.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for checking if campaign has been executed
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<CatFact> context) {
        CatFact entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("CatFact entity is null");
            return EvaluationOutcome.fail("CatFact entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!entity.isValid()) {
            logger.warn("CatFact entity is not valid");
            return EvaluationOutcome.fail("CatFact entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if cat fact ID is available
        if (entity.getId() == null || entity.getId().trim().isEmpty()) {
            logger.warn("CatFact ID is null or empty");
            return EvaluationOutcome.fail("CatFact ID is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Query EmailCampaign entities to check if any campaign using this cat fact is completed
        try {
            ModelSpec campaignModelSpec = new ModelSpec()
                    .withName(EmailCampaign.ENTITY_NAME)
                    .withVersion(EmailCampaign.ENTITY_VERSION);

            // Create condition to find campaigns using this cat fact
            SimpleCondition catFactCondition = new SimpleCondition()
                    .withJsonPath("$.catFactId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(entity.getId()));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(catFactCondition));

            List<EntityWithMetadata<EmailCampaign>> campaigns = 
                    entityService.search(campaignModelSpec, condition, EmailCampaign.class);

            // Check if any campaign is in COMPLETED state
            boolean hasCompletedCampaign = campaigns.stream()
                    .anyMatch(campaignWithMetadata -> "COMPLETED".equals(campaignWithMetadata.metadata().getState()));

            if (hasCompletedCampaign) {
                logger.debug("Found completed campaign for CatFact {}", entity.getId());
                return EvaluationOutcome.success();
            } else {
                logger.debug("No completed campaign found for CatFact {}", entity.getId());
                return EvaluationOutcome.fail("No completed campaign found for this cat fact", 
                                            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

        } catch (Exception e) {
            logger.error("Error checking campaign execution for CatFact {}: {}", entity.getId(), e.getMessage());
            return EvaluationOutcome.fail("Error checking campaign execution: " + e.getMessage(),
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
