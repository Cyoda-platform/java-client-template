package com.java_template.application.criterion;

import com.java_template.application.entity.DigestContent;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
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
public class LinkedDigestRequestExistsCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public LinkedDigestRequestExistsCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("LinkedDigestRequestExistsCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(DigestContent.class, this::linkedRequestExists)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    private EvaluationOutcome linkedRequestExists(DigestContent digestContent) {
        if (digestContent.getRequestId() == null || digestContent.getRequestId().isBlank()) {
            return EvaluationOutcome.fail("Linked request ID is missing", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE.getCode());
        }
        return EvaluationOutcome.success();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "LinkedDigestRequestExistsCriterion".equals(modelSpec.operationName()) &&
               "digestContent".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
