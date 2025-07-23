package com.java_template.application.criterion;

import com.java_template.application.entity.CommentIngestionJob;
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
public class CommentIngestionJobValidationFailCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public CommentIngestionJobValidationFailCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("CommentIngestionJobValidationFailCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(CommentIngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "CommentIngestionJobValidationFailCriterion".equals(modelSpec.operationName()) &&
               "commentIngestionJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(CommentIngestionJob entity) {
        if (entity.getPostId() == null || entity.getPostId() <= 0) {
            return EvaluationOutcome.success(); // This criterion triggers fail transition when validation fails
        }
        if (entity.getReportEmail() == null || entity.getReportEmail().isBlank()) {
            return EvaluationOutcome.success();
        }
        if (entity.getStatus() == null || !entity.getStatus().equals("PENDING")) {
            return EvaluationOutcome.success();
        }
        return EvaluationOutcome.fail("Validation passed, should not fail", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}
