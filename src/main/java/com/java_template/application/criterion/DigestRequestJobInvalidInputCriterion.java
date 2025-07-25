package com.java_template.application.criterion;

import com.java_template.application.entity.DigestRequestJob;
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
public class DigestRequestJobInvalidInputCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public DigestRequestJobInvalidInputCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("DigestRequestJobInvalidInputCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(DigestRequestJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestRequestJobInvalidInputCriterion".equals(modelSpec.operationName()) &&
               "digestRequestJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(DigestRequestJob entity) {
        // Fail if email or metadata is blank or null
        if (entity.getEmail() == null || entity.getEmail().isBlank()) {
            return EvaluationOutcome.fail("Email must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (entity.getMetadata() == null || entity.getMetadata().isBlank()) {
            return EvaluationOutcome.fail("Metadata must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        // Possibly other data quality checks
        return EvaluationOutcome.success();
    }
}
