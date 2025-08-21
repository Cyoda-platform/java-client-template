package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.userrecord.version_1.UserRecord;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class DeduplicationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    public DeduplicationCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(UserRecord.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<UserRecord> context) {
        UserRecord user = context.entity();
        if (user == null) return EvaluationOutcome.fail("User missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        try {
            // Check by externalId
            if (user.getExternalId() != null) {
                CompletableFuture<java.util.ArrayNode> fut = entityService.getItemsByCondition(
                    UserRecord.ENTITY_NAME,
                    String.valueOf(UserRecord.ENTITY_VERSION),
                    SearchConditionRequest.group("AND", Condition.of("$.externalId", "EQUALS", String.valueOf(user.getExternalId()))),
                    true
                );
                if (fut.join() != null && fut.join().size() > 0) {
                    return EvaluationOutcome.success();
                }
            }

            // Check by email
            if (user.getEmail() != null) {
                CompletableFuture<java.util.ArrayNode> fut = entityService.getItemsByCondition(
                    UserRecord.ENTITY_NAME,
                    String.valueOf(UserRecord.ENTITY_VERSION),
                    SearchConditionRequest.group("AND", Condition.of("$.email", "IEQUALS", user.getEmail())),
                    true
                );
                if (fut.join() != null && fut.join().size() > 0) {
                    return EvaluationOutcome.success();
                }
            }

            return EvaluationOutcome.fail("No duplicate found", StandardEvalReasonCategories.VALIDATION_FAILURE);
        } catch (Exception ex) {
            logger.error("Error during DeduplicationCriterion", ex);
            return EvaluationOutcome.fail("Deduplication check failed: " + ex.getMessage(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
