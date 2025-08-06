package com.java_template.application.criterion;

import com.java_template.application.entity.WeatherRequest;
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
public class IsValidRequest implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsValidRequest(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request)
            .evaluateEntity(WeatherRequest.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<WeatherRequest> context) {

        WeatherRequest weatherRequest = context.entity();

        // Validation logic based on business requirements
        // A valid request must have either:
        // - a non-blank cityName, OR
        // - both latitude and longitude not null
        // AND requestType must be non-null and not blank

        if (weatherRequest.getRequestType() == null || weatherRequest.getRequestType().isBlank()) {
            return EvaluationOutcome.fail("Request type is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        boolean cityValid = weatherRequest.getCityName() != null && !weatherRequest.getCityName().isBlank();
        boolean latLongValid = weatherRequest.getLatitude() != null && weatherRequest.getLongitude() != null;

        if (!cityValid && !latLongValid) {
            return EvaluationOutcome.fail("Either city name or both latitude and longitude must be provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
