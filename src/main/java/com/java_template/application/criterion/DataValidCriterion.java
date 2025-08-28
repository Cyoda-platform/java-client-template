package com.java_template.application.criterion;

import com.java_template.application.entity.datasource.version_1.DataSource;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
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
public class DataValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DataValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(DataSource.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<DataSource> context) {
         DataSource entity = context.entity();
         if (entity == null) {
             return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required basic fields
         if (entity.getId() == null || entity.getId().isBlank()) {
             return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getUrl() == null || entity.getUrl().isBlank()) {
             return EvaluationOutcome.fail("url is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // validationStatus must be present
         String validationStatus = entity.getValidationStatus();
         if (validationStatus == null || validationStatus.isBlank()) {
             return EvaluationOutcome.fail("validation_status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: Data is considered valid only when validation_status == "VALID"
         if (!"VALID".equalsIgnoreCase(validationStatus)) {
             return EvaluationOutcome.fail("Data source validation_status is not VALID", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Additional data quality checks for validated sources
         if (entity.getSchema() == null || entity.getSchema().isBlank()) {
             return EvaluationOutcome.fail("schema missing for validated data source", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getSampleHash() == null || entity.getSampleHash().isBlank()) {
             return EvaluationOutcome.fail("sample_hash missing for validated data source", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}