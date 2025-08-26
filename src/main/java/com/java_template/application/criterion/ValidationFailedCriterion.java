package com.java_template.application.criterion;

import com.java_template.application.entity.location.version_1.Location;
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
public class ValidationFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationFailedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Location.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Location> context) {
         Location entity = context.entity();
         if (entity == null) {
             logger.debug("Location entity is null");
             return EvaluationOutcome.fail("Location entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required identifier
         if (entity.getLocationId() == null || entity.getLocationId().isBlank()) {
             return EvaluationOutcome.fail("locationId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required human name
         if (entity.getName() == null || entity.getName().isBlank()) {
             return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Timezone must be present
         if (entity.getTimezone() == null || entity.getTimezone().isBlank()) {
             return EvaluationOutcome.fail("timezone is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Active flag must be explicitly set
         if (entity.getActive() == null) {
             return EvaluationOutcome.fail("active flag must be set", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Coordinates must be present
         if (entity.getLatitude() == null || entity.getLongitude() == null) {
             return EvaluationOutcome.fail("latitude and longitude must both be provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate ranges for coordinates (data quality)
         Double lat = entity.getLatitude();
         Double lon = entity.getLongitude();
         if (Double.isNaN(lat) || Double.isInfinite(lat) || lat < -90.0 || lat > 90.0) {
             return EvaluationOutcome.fail("latitude out of range (-90.0 to 90.0)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (Double.isNaN(lon) || Double.isInfinite(lon) || lon < -180.0 || lon > 180.0) {
             return EvaluationOutcome.fail("longitude out of range (-180.0 to 180.0)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed -> no validation failure
         return EvaluationOutcome.success();
    }
}