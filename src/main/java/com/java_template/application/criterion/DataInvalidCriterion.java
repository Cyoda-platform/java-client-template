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
public class DataInvalidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DataInvalidCriterion(SerializerFactory serializerFactory) {
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
             logger.warn("DataInvalidCriterion: entity is null");
             return EvaluationOutcome.fail("Entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Ensure required fields exist before making decision
         if (entity.getId() == null || entity.getId().isBlank()) {
             return EvaluationOutcome.fail("DataSource.id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getUrl() == null || entity.getUrl().isBlank()) {
             return EvaluationOutcome.fail("DataSource.url is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSampleHash() == null || entity.getSampleHash().isBlank()) {
             return EvaluationOutcome.fail("DataSource.sampleHash is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSchema() == null || entity.getSchema().isBlank()) {
             return EvaluationOutcome.fail("DataSource.schema is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // validationStatus determines whether data is considered valid
         String status = entity.getValidationStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("DataSource.validationStatus is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if ("VALID".equalsIgnoreCase(status.trim())) {
             // Data is valid -> this criterion is not a failing condition
             return EvaluationOutcome.success();
         }

         // Any non-VALID status is treated as data quality failure
         return EvaluationOutcome.fail("DataSource marked as invalid or failed validation", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
    }
}