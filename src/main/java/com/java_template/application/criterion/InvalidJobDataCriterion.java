package com.java_template.application.criterion;

import com.java_template.application.entity.DigestJob;
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
public class InvalidJobDataCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public InvalidJobDataCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("InvalidJobDataCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(DigestJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "InvalidJobDataCriterion".equals(modelSpec.operationName()) &&
               "digestJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(DigestJob entity) {
        // Check for invalid data conditions in DigestJob
        if (entity.getPetDataQuery() == null || entity.getPetDataQuery().isBlank()) {
            return EvaluationOutcome.fail("Pet data query is invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (entity.getEmailRecipients() == null || entity.getEmailRecipients().isEmpty()) {
            return EvaluationOutcome.fail("Email recipients list is invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
