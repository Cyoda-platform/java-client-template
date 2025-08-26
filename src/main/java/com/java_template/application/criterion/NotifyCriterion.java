package com.java_template.application.criterion;

import com.java_template.application.entity.weatherobservation.version_1.WeatherObservation;
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
public class NotifyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public NotifyCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(WeatherObservation.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<WeatherObservation> context) {
         WeatherObservation entity = context.entity();

         if (entity == null) {
             return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Basic validation: required identity and timestamp
         if (entity.getObservationId() == null || entity.getObservationId().isBlank()) {
             return EvaluationOutcome.fail("Missing observationId", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getLocationId() == null || entity.getLocationId().isBlank()) {
             return EvaluationOutcome.fail("Missing locationId", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getTimestamp() == null || entity.getTimestamp().isBlank()) {
             return EvaluationOutcome.fail("Missing timestamp", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Processed flag must be true to consider notifications
         if (entity.getProcessed() == null || !entity.getProcessed()) {
             return EvaluationOutcome.fail("Observation not processed", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Data quality checks for numeric fields
         if (entity.getHumidity() != null) {
             int h = entity.getHumidity();
             if (h < 0 || h > 100) {
                 return EvaluationOutcome.fail("Humidity out of range (0-100)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         if (entity.getPrecipitation() != null) {
             Double p = entity.getPrecipitation();
             if (Double.isNaN(p) || Double.isInfinite(p)) {
                 return EvaluationOutcome.fail("Invalid precipitation value", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (p < 0) {
                 return EvaluationOutcome.fail("Precipitation cannot be negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         if (entity.getWindSpeed() != null) {
             Double w = entity.getWindSpeed();
             if (Double.isNaN(w) || Double.isInfinite(w)) {
                 return EvaluationOutcome.fail("Invalid windSpeed value", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (w < 0) {
                 return EvaluationOutcome.fail("windSpeed cannot be negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         if (entity.getTemperature() != null) {
             Double t = entity.getTemperature();
             if (Double.isNaN(t) || Double.isInfinite(t)) {
                 return EvaluationOutcome.fail("Invalid temperature value", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Business rules that trigger notifications:
         // - Extreme temperature: >= 40C or <= -20C
         // - High wind: >= 20 m/s
         // - Heavy precipitation: >= 50 mm
         // - Very high humidity: >= 95%
         boolean trigger = false;

         if (entity.getTemperature() != null) {
             double temp = entity.getTemperature();
             if (temp >= 40.0 || temp <= -20.0) {
                 trigger = true;
                 logger.debug("NotifyCriterion trigger: extreme temperature {}", temp);
             }
         }

         if (!trigger && entity.getWindSpeed() != null) {
             double ws = entity.getWindSpeed();
             if (ws >= 20.0) {
                 trigger = true;
                 logger.debug("NotifyCriterion trigger: high windSpeed {}", ws);
             }
         }

         if (!trigger && entity.getPrecipitation() != null) {
             double pr = entity.getPrecipitation();
             if (pr >= 50.0) {
                 trigger = true;
                 logger.debug("NotifyCriterion trigger: heavy precipitation {}", pr);
             }
         }

         if (!trigger && entity.getHumidity() != null) {
             int h = entity.getHumidity();
             if (h >= 95) {
                 trigger = true;
                 logger.debug("NotifyCriterion trigger: very high humidity {}", h);
             }
         }

         if (trigger) {
             return EvaluationOutcome.success();
         } else {
             return EvaluationOutcome.fail("No notification triggers matched", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }
    }
}