package com.java_template.application.criterion;

import com.java_template.application.entity.booking.version_1.Booking;
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
public class BookingFetchFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public BookingFetchFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        
        return serializer.withRequest(request)
            .evaluateEntity(Booking.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Booking> context) {
        Booking entity = context.entity();
        
        // Check if booking fetch failed by examining the entity state
        // If the entity has minimal data (only bookingId), it likely means fetch failed
        if (entity.getBookingId() != null && 
            (entity.getFirstname() == null || entity.getLastname() == null || 
             entity.getTotalprice() == null || entity.getRetrievedAt() == null)) {
            
            logger.warn("Booking fetch failure detected for booking ID: {}", entity.getBookingId());
            return EvaluationOutcome.fail("Booking fetch failed - incomplete data", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }
        
        // Check for invalid data that might indicate API errors
        if (entity.getTotalprice() != null && entity.getTotalprice() <= 0) {
            logger.warn("Invalid price detected for booking {}: {}", entity.getBookingId(), entity.getTotalprice());
            return EvaluationOutcome.fail("Invalid booking data received from API", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        
        // Check for malformed dates
        if (entity.getCheckin() != null && entity.getCheckout() != null && 
            !entity.getCheckin().isBefore(entity.getCheckout())) {
            logger.warn("Invalid date range for booking {}: {} to {}", 
                entity.getBookingId(), entity.getCheckin(), entity.getCheckout());
            return EvaluationOutcome.fail("Invalid date range in booking data", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        
        // If we reach here, the booking fetch appears to have been successful
        logger.debug("Booking fetch validation passed for booking {}", entity.getBookingId());
        return EvaluationOutcome.success();
    }
}
