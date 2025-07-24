package com.java_template.application.criterion;

import com.java_template.application.entity.DigestEmail;
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
public class DigestEmailSentCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public DigestEmailSentCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("DigestEmailSentCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(DigestEmail.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestEmailSentCriterion".equals(modelSpec.operationName()) &&
               "digestEmail".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(DigestEmail entity) {
        if (entity.getDeliveryStatus() == null || entity.getDeliveryStatus().isBlank()) {
            return EvaluationOutcome.fail("Delivery status is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (!"SENT".equalsIgnoreCase(entity.getDeliveryStatus())) {
            return EvaluationOutcome.fail("Delivery status is not SENT", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
