package com.java_template.application.criterion;

import com.java_template.application.entity.PetJob;
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
public class SourceUrlValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public SourceUrlValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("SourceUrlValidCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(PetJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "SourceUrlValidCriterion".equals(modelSpec.operationName()) &&
               "petJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(PetJob entity) {
        // Validate sourceUrl is not null or blank
        if (entity.getSourceUrl() == null || entity.getSourceUrl().isBlank()) {
            return EvaluationOutcome.fail("Source URL must be provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate sourceUrl format (basic check)
        String url = entity.getSourceUrl();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return EvaluationOutcome.fail("Source URL must start with http:// or https://", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Additional business rule: URL must contain petstore.swagger.io
        if (!url.contains("petstore.swagger.io")) {
            return EvaluationOutcome.fail("Source URL must be a valid Petstore API endpoint", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
