package com.java_template.application.criterion;

import com.java_template.application.entity.flightoption.version_1.FlightOption;
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
public class SeatsAvailableCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SeatsAvailableCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Evaluating SeatsAvailableCriterion for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(FlightOption.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<FlightOption> context) {
        FlightOption entity = context.entity();
        if (entity == null) {
            return EvaluationOutcome.fail("FlightOption entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        Integer seats = entity.getSeatAvailability();
        if (seats == null) {
            return EvaluationOutcome.fail("Seat availability unknown", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (seats > 0) {
            return EvaluationOutcome.success();
        }
        return EvaluationOutcome.fail("No seats available", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}
