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
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class LinkedDigestJobExistsCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public LinkedDigestJobExistsCriterion(CriterionSerializer serializerFactory) {
        this.serializer = serializerFactory;
        logger.info("LinkedDigestJobExistsCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        // Example validation logic: Check if DigestRequest has a non-empty id and userEmail
        return serializer.withRequest(request)
                .evaluateEntity(DigestRequest.class, digestRequest -> {
                    if (digestRequest.getId() != null && !digestRequest.getId().isBlank() && 
                        digestRequest.getUserEmail() != null && !digestRequest.getUserEmail().isBlank()) {
                        // Simulate existence check logic
                        // In a real implementation, this would check linked DigestJob existence
                        return EvaluationOutcome.success();
                    } else {
                        return EvaluationOutcome.fail("DigestRequest missing required fields", StandardEvalReasonCategories.DATA_QUALITY_FAILURE.getCode());
                    }
                })
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "LinkedDigestJobExistsCriterion".equals(modelSpec.operationName()) &&
               "digestrequest".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
