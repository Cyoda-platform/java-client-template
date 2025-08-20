package com.java_template.application.criterion;

import com.java_template.application.entity.coverphoto.version_1.CoverPhoto;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DuplicateDetectedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DuplicateDetectedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(CoverPhoto.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<CoverPhoto> context) {
        CoverPhoto entity = context.entity();
        if (entity == null) return EvaluationOutcome.fail("Entity null", StandardEvalReasonCategories.VALIDATION_FAILURE);

        // If duplicateOf is set, criterion passes (duplicate detected)
        if (entity.getDuplicateOf() != null && !entity.getDuplicateOf().trim().isEmpty()) {
            logger.info("Duplicate detected for CoverPhoto {} referencing {}", entity.getTechnicalId(), entity.getDuplicateOf());
            return EvaluationOutcome.success();
        }

        // Alternatively if status is ARCHIVED and not errorFlag
        if ("ARCHIVED".equals(entity.getStatus())) {
            return EvaluationOutcome.success();
        }

        return EvaluationOutcome.fail("No duplicate detected", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
    }
}
