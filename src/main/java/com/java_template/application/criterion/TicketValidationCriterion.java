package com.java_template.application.criterion;

import com.java_template.application.entity.ticket.version_1.Ticket;
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
public class TicketValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public TicketValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Ticket.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Ticket> context) {
        Ticket ticket = context.entity();
        if (ticket.getBookingId() == null || ticket.getBookingId().isEmpty()) {
            return EvaluationOutcome.fail("Booking ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (ticket.getTicketNumber() == null || ticket.getTicketNumber().isEmpty()) {
            return EvaluationOutcome.fail("Ticket number is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (ticket.getStatus() == null || ticket.getStatus().isEmpty()) {
            return EvaluationOutcome.fail("Ticket status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Additional validation logic
        return EvaluationOutcome.success();
    }
}
