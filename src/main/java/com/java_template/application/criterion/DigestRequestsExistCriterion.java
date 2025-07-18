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
public class DigestRequestsExistCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public DigestRequestsExistCriterion(CriterionSerializer serializer) {
        this.serializer = serializer;
        logger.info("DigestRequestsExistCriterion initialized");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
                .evaluateEntity(DigestJob.class, this::requestsExist)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
    }

    private EvaluationOutcome requestsExist(DigestJob digestJob) {
        // We assume requestMetadata is a JSON string that when parsed contains info about requests
        String metadata = digestJob.getRequestMetadata();
        if (metadata != null && !metadata.isBlank() && metadata.contains("request")) {
            return EvaluationOutcome.success();
        } else {
            return EvaluationOutcome.fail("No digest requests found in requestMetadata", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestRequestsExistCriterion".equals(modelSpec.operationName()) &&
                "digestJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
