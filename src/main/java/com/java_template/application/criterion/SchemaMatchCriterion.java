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
public class SchemaMatchCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SchemaMatchCriterion(SerializerFactory serializerFactory) {
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
        // MUST use exact criterion name (case-sensitive)
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<DataSource> context) {
         DataSource entity = context.entity();
         // Basic presence checks
         if (entity == null) {
             logger.warn("DataSource entity is null in SchemaMatchCriterion");
             return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getUrl() == null || entity.getUrl().isBlank()) {
             return EvaluationOutcome.fail("Data source URL is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSchema() == null || entity.getSchema().isBlank()) {
             return EvaluationOutcome.fail("Schema is missing or empty", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSampleHash() == null || entity.getSampleHash().isBlank()) {
             return EvaluationOutcome.fail("Sample hash not recorded for this data source", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Schema content quality checks.
         // Expected columns for our reporting/analytics: price, bedrooms, area
         String schemaLower = entity.getSchema().toLowerCase();
         String[] requiredColumns = new String[] { "price", "bedrooms", "area" };

         for (String col : requiredColumns) {
             if (!schemaLower.contains(col)) {
                 String message = String.format("Required column '%s' not present in schema", col);
                 logger.info("Schema mismatch for DataSource id={} : {}", entity.getId(), message);
                 return EvaluationOutcome.fail(message, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // If all checks pass, mark as success
         return EvaluationOutcome.success();
    }
}