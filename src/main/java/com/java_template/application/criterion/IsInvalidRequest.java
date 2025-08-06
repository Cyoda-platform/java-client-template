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
public class IsInvalidRequest implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsInvalidRequest(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
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

        // Inverse logic of IsValidRequest
        if (weatherRequest.getRequestType() == null || weatherRequest.getRequestType().isBlank()) {
            return EvaluationOutcome.success(); // This is invalid because requestType missing
        }

        boolean cityValid = weatherRequest.getCityName() != null && !weatherRequest.getCityName().isBlank();
        boolean latLongValid = weatherRequest.getLatitude() != null && weatherRequest.getLongitude() != null;

        if (!cityValid && !latLongValid) {
            return EvaluationOutcome.success(); // Invalid because location info missing
        }

        return EvaluationOutcome.fail("Request is valid", StandardEvalReasonCategories.VALIDATION_FAILURE); // Fail because it is valid
    }
}
