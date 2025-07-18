package com.java_template.application.criterion;

import com.java_template.application.entity.DigestJob;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IsInvalidDigestJobCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public IsInvalidDigestJobCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("IsInvalidDigestJobCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(DigestJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "IsInvalidDigestJobCriterion".equals(modelSpec.operationName()) &&
               "digestJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(DigestJob entity) {
        if (entity.getPetDataQuery() == null || entity.getPetDataQuery().isBlank()) {
            return EvaluationOutcome.success(); // This criterion is about invalid jobs, so success means it's invalid
        }
        if (entity.getEmailRecipients() == null || entity.getEmailRecipients().isEmpty()) {
            return EvaluationOutcome.success();
        }
        if (entity.getStatus() == null || !"PENDING".equalsIgnoreCase(entity.getStatus())) {
            return EvaluationOutcome.success();
        }
        return EvaluationOutcome.fail("Digest job is valid", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}
