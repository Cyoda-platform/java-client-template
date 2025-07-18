package com.java_template.application.criterion;

import com.java_template.application.entity.DigestRequest;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
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
public class FetchSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public FetchSuccessCriterion(CriterionSerializer serializer) {
        this.serializer = serializer;
        logger.info("FetchSuccessCriterion initialized with CriterionSerializer");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(DigestRequest.class, this::applyFetchSuccessCheck)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    private EvaluationOutcome applyFetchSuccessCheck(DigestRequest digestRequest) {
        // Implement your fetch success condition check here
        // For example, check if status is PROCESSING (assuming fetch success means processing started)
        if (digestRequest == null) {
            return EvaluationOutcome.fail("DigestRequest entity is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE.getCode());
        }

        if (digestRequest.getStatus() == null) {
            return EvaluationOutcome.fail("Status is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE.getCode());
        }

        if (digestRequest.getStatus() == DigestRequest.StatusEnum.PROCESSING) {
            return EvaluationOutcome.success();
        } else {
            return EvaluationOutcome.fail("Status is not PROCESSING", StandardEvalReasonCategories.DATA_QUALITY_FAILURE.getCode());
        }
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "FetchSuccessCriterion".equals(modelSpec.operationName()) &&
               "digestRequest".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
