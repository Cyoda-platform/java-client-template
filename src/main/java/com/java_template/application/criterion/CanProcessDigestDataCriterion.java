package com.java_template.application.criterion;

import com.java_template.application.entity.DigestData;
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
public class CanProcessDigestDataCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public CanProcessDigestDataCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("CanProcessDigestDataCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(DigestData.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "CanProcessDigestDataCriterion".equals(modelSpec.operationName()) &&
               "digestData".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(DigestData entity) {
        // Validation logic based on business requirements from functional spec
        // DigestData must have status RETRIEVED before allowing processing

        if (entity == null) {
            return EvaluationOutcome.fail("DigestData entity is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        if (entity.getStatus() == null) {
            return EvaluationOutcome.fail("Status is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Only allow processing if status is RETRIEVED
        if (!"RETRIEVED".equals(entity.getStatus().name())) {
            return EvaluationOutcome.fail("DigestData status must be RETRIEVED to process", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
