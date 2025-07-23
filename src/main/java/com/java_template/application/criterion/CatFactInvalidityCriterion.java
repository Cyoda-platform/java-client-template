package com.java_template.application.criterion;

import com.java_template.application.entity.CatFact;
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
public class CatFactInvalidityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public CatFactInvalidityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("CatFactInvalidityCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(CatFact.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "CatFactInvalidityCriterion".equals(modelSpec.operationName()) &&
               "catFact".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(CatFact entity) {
        // This criterion ensures that the CatFact entity is invalid, so it expects validation failures
        if (entity.getId() == null || entity.getId().isBlank()) {
            return EvaluationOutcome.success();
        }
        if (entity.getTechnicalId() == null) {
            return EvaluationOutcome.success();
        }
        if (entity.getFact() == null || entity.getFact().isBlank()) {
            return EvaluationOutcome.success();
        }
        if (entity.getSource() == null || entity.getSource().isBlank()) {
            return EvaluationOutcome.success();
        }
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            return EvaluationOutcome.success();
        }
        // If all fields are valid, then it is not invalid, so fail this criterion
        return EvaluationOutcome.fail("CatFact entity is valid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
    }
}
