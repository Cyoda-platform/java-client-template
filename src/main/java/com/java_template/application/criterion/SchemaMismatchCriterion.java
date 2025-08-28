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
public class SchemaMismatchCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SchemaMismatchCriterion(SerializerFactory serializerFactory) {
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
             logger.warn("SchemaMismatchCriterion: entity is null");
             return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If validationStatus explicitly indicates INVALID -> this criterion matches (schema mismatch detected)
         String validationStatus = entity.getValidationStatus();
         if (validationStatus != null && validationStatus.equalsIgnoreCase("INVALID")) {
             logger.debug("SchemaMismatchCriterion: validationStatus=INVALID for DataSource id={}", entity.getId());
             return EvaluationOutcome.success();
         }

         // If schema is missing or blank it is considered a mismatch
         String schema = entity.getSchema();
         if (schema == null || schema.isBlank()) {
             logger.debug("SchemaMismatchCriterion: missing schema for DataSource id={}", entity.getId());
             return EvaluationOutcome.success();
         }

         // If sampleHash is missing while schema exists, treat as potential mismatch/unreliable sample
         String sampleHash = entity.getSampleHash();
         if (sampleHash == null || sampleHash.isBlank()) {
             logger.debug("SchemaMismatchCriterion: missing sampleHash for DataSource id={}", entity.getId());
             return EvaluationOutcome.success();
         }

         // No mismatch detected - criterion does not match
         logger.debug("SchemaMismatchCriterion: no mismatch detected for DataSource id={}", entity.getId());
         return EvaluationOutcome.fail("Schema appears valid or validation not flagged as INVALID", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
    }
}