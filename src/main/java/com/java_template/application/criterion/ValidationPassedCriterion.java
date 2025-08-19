package com.java_template.application.criterion;

import com.java_template.application.entity.flightSearch.version_1.FlightSearch;
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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

@Component
public class ValidationPassedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private static final Pattern IATA = Pattern.compile("^[A-Z]{3}$");

    public ValidationPassedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Evaluating ValidationPassedCriterion for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(FlightSearch.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<FlightSearch> context) {
        FlightSearch entity = context.entity();
        if (entity == null) {
            return EvaluationOutcome.fail("FlightSearch entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getOriginAirportCode() == null || entity.getOriginAirportCode().isEmpty()) {
            return EvaluationOutcome.fail("originAirportCode is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        try {
            if (!IATA.matcher(entity.getOriginAirportCode()).matches()) {
                return EvaluationOutcome.fail("originAirportCode must be a valid IATA code (3 uppercase letters)", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } catch (Exception ex) {
            return EvaluationOutcome.fail("Invalid originAirportCode format", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getDepartureDate() == null || entity.getDepartureDate().isEmpty()) {
            return EvaluationOutcome.fail("departureDate is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        try {
            LocalDate departure = LocalDate.parse(entity.getDepartureDate());
            if (entity.getReturnDate() != null && !entity.getReturnDate().isEmpty()) {
                LocalDate ret = LocalDate.parse(entity.getReturnDate());
                if (departure.isAfter(ret)) {
                    return EvaluationOutcome.fail("departureDate must be before or equal to returnDate", StandardEvalReasonCategories.VALIDATION_FAILURE);
                }
            }
        } catch (DateTimeParseException ex) {
            return EvaluationOutcome.fail("Dates must be ISO-8601 (yyyy-MM-dd)", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        Integer pax = entity.getPassengerCount();
        if (pax == null || pax < 1) {
            return EvaluationOutcome.fail("passengerCount must be >= 1", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (pax > 9) {
            return EvaluationOutcome.fail("passengerCount exceeds allowed maximum of 9", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getCabinClass() != null && !entity.getCabinClass().isEmpty()) {
            String cls = entity.getCabinClass();
            if (!("ECONOMY".equalsIgnoreCase(cls) || "PREMIUM_ECONOMY".equalsIgnoreCase(cls)
                || "BUSINESS".equalsIgnoreCase(cls) || "FIRST".equalsIgnoreCase(cls))) {
                return EvaluationOutcome.fail("Invalid cabinClass value", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        // If all checks pass
        return EvaluationOutcome.success();
    }
}
