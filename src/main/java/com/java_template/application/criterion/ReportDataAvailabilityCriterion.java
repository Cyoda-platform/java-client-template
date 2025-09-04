package com.java_template.application.criterion;

import com.java_template.application.entity.report.version_1.Report;
import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ReportDataAvailabilityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    
    @Autowired
    private EntityService entityService;

    public ReportDataAvailabilityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        
        return serializer.withRequest(request)
            .evaluateEntity(Report.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Report> context) {
        Report entity = context.entity();
        
        try {
            // Check if sufficient booking data is available
            List<EntityResponse<Booking>> allBookings = entityService.findAll(Booking.class);
            
            // Filter for processed bookings only
            List<EntityResponse<Booking>> processedBookings = allBookings.stream()
                .filter(bookingResponse -> "processed".equals(bookingResponse.getMetadata().getState()))
                .collect(Collectors.toList());
            
            // Check if at least one booking exists in processed state
            if (processedBookings.isEmpty()) {
                logger.warn("No processed bookings available for report generation");
                return EvaluationOutcome.fail("No processed booking data available", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
            
            // Check data freshness (bookings not older than 30 days)
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            long freshBookings = processedBookings.stream()
                .filter(bookingResponse -> {
                    Booking booking = bookingResponse.getData();
                    return booking.getRetrievedAt() != null && booking.getRetrievedAt().isAfter(thirtyDaysAgo);
                })
                .count();
            
            if (freshBookings == 0) {
                logger.warn("All processed bookings are older than 30 days");
                return EvaluationOutcome.fail("Booking data is too old for reliable reporting", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
            
            // Check for data integrity - ensure required fields are populated
            long validBookings = processedBookings.stream()
                .filter(bookingResponse -> {
                    Booking booking = bookingResponse.getData();
                    return booking.getFirstname() != null && 
                           booking.getLastname() != null && 
                           booking.getTotalprice() != null && 
                           booking.getDepositpaid() != null &&
                           booking.getCheckin() != null &&
                           booking.getCheckout() != null;
                })
                .count();
            
            if (validBookings < processedBookings.size() * 0.8) { // At least 80% should be valid
                logger.warn("Too many bookings have incomplete data: {} valid out of {} total", 
                    validBookings, processedBookings.size());
                return EvaluationOutcome.fail("Insufficient data quality for reliable reporting", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
            
            logger.info("Data availability check passed: {} processed bookings, {} fresh, {} valid", 
                processedBookings.size(), freshBookings, validBookings);
            return EvaluationOutcome.success();
            
        } catch (Exception e) {
            logger.error("Error checking data availability for report {}: {}", entity.getReportId(), e.getMessage(), e);
            return EvaluationOutcome.fail("Error accessing booking data: " + e.getMessage(), StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }
    }
}
