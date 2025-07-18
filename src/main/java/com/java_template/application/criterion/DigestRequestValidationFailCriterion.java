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
public class DigestRequestValidationFailCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public DigestRequestValidationFailCriterion(CriterionSerializer serializer) {
        this.serializer = serializer;
        logger.info("DigestRequestValidationFailCriterion initialized with CriterionSerializer");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(DigestRequest.class, this::validateDigestRequestFail)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    private EvaluationOutcome validateDigestRequestFail(DigestRequest digestRequest) {
        if (digestRequest == null) {
            return EvaluationOutcome.fail("DigestRequest entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE.getCode());
        }

        if (digestRequest.getExternalApiUrl() == null || digestRequest.getExternalApiUrl().isBlank()) {
            return EvaluationOutcome.fail("externalApiUrl is missing or blank", StandardEvalReasonCategories.VALIDATION_FAILURE.getCode());
        }

        if (digestRequest.getEmailRecipients() == null || digestRequest.getEmailRecipients().isEmpty()) {
            return EvaluationOutcome.fail("emailRecipients list is empty or null", StandardEvalReasonCategories.VALIDATION_FAILURE.getCode());
        }

        return EvaluationOutcome.success();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestRequestValidationFailCriterion".equals(modelSpec.operationName()) &&
               "digestRequest".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
