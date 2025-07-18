package com.java_template.application.criterion;

import com.java_template.application.entity.DigestJob;
import com.java_template.common.config.Config;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
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
public class DigestJobNotProcessingCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public DigestJobNotProcessingCriterion(CriterionSerializer serializer) {
        this.serializer = serializer;
        logger.info("DigestJobNotProcessingCriterion initialized");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
                .evaluateEntity(DigestJob.class, this::isNotProcessing)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
    }

    private EvaluationOutcome isNotProcessing(DigestJob digestJob) {
        if (!"PROCESSING".equalsIgnoreCase(digestJob.getStatus())) {
            return EvaluationOutcome.success();
        } else {
            return EvaluationOutcome.fail("DigestJob status is PROCESSING", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestJobNotProcessingCriterion".equals(modelSpec.operationName()) &&
                "digestJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}