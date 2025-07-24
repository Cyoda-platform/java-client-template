package com.java_template.application.criterion;

import com.java_template.application.entity.CompanySearchJob;
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
public class CompanySearchJobValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public CompanySearchJobValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("CompanySearchJobValidationCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(CompanySearchJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "CompanySearchJobValidationCriterion".equals(modelSpec.operationName()) &&
               "companySearchJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(CompanySearchJob entity) {
        if (entity.getCompanyName() == null || entity.getCompanyName().isBlank()) {
            return EvaluationOutcome.fail("companyName is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getOutputFormat() == null || entity.getOutputFormat().isBlank()) {
            return EvaluationOutcome.fail("outputFormat is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        String outputFormat = entity.getOutputFormat().toUpperCase();
        if (!outputFormat.equals("JSON") && !outputFormat.equals("CSV")) {
            return EvaluationOutcome.fail("outputFormat must be either JSON or CSV", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Additional validation rules can be added here
        return EvaluationOutcome.success();
    }
}
