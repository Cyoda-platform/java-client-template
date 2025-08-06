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
        WeatherRequest entity = context.entity();

        if ((entity.getCityName() == null || entity.getCityName().isBlank()) && 
            (entity.getLatitude() == null || entity.getLongitude() == null)) {
            return EvaluationOutcome.fail("Either cityName or latitude/longitude must be provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getRequestType() == null || entity.getRequestType().isBlank()) {
            return EvaluationOutcome.fail("requestType is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Additional validation: requestType must be 'CURRENT' or 'FORECAST'
        if (!entity.getRequestType().equalsIgnoreCase("CURRENT") && !entity.getRequestType().equalsIgnoreCase("FORECAST")) {
            return EvaluationOutcome.fail("requestType must be 'CURRENT' or 'FORECAST'", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
