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
public class ValidationPassedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationPassedCriterion(SerializerFactory serializerFactory) {
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
             logger.debug("ValidationPassedCriterion: entity is null");
             return EvaluationOutcome.fail("Entity payload is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required identifiers and metadata
         if (entity.getLocationId() == null || entity.getLocationId().isBlank()) {
             return EvaluationOutcome.fail("locationId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getName() == null || entity.getName().isBlank()) {
             return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getTimezone() == null || entity.getTimezone().isBlank()) {
             return EvaluationOutcome.fail("timezone is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Active flag must be explicitly set
         if (entity.getActive() == null) {
             return EvaluationOutcome.fail("active flag must be set (true/false)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Coordinates validation
         Double latitude = entity.getLatitude();
         Double longitude = entity.getLongitude();
         if (latitude == null || longitude == null) {
             return EvaluationOutcome.fail("latitude and longitude are required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Check for NaN/Infinite and valid ranges
         if (Double.isNaN(latitude) || Double.isInfinite(latitude)) {
             return EvaluationOutcome.fail("latitude is not a valid number", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (Double.isNaN(longitude) || Double.isInfinite(longitude)) {
             return EvaluationOutcome.fail("longitude is not a valid number", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (latitude < -90.0 || latitude > 90.0) {
             return EvaluationOutcome.fail("latitude out of range (-90 to 90)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (longitude < -180.0 || longitude > 180.0) {
             return EvaluationOutcome.fail("longitude out of range (-180 to 180)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}