package com.java_template.application.processor;

import com.java_template.application.entity.report.version_1.Report;
import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ReportDataCollectionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReportDataCollectionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    
    @Autowired
    private EntityService entityService;

    public ReportDataCollectionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Report data collection for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Report.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Report entity) {
        return entity != null;
    }

    private Report processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Report> context) {
        Report entity = context.entity();
        
        try {
            // Fetch all bookings from the system
            List<EntityResponse<Booking>> allBookings = entityService.findAll(Booking.class);
            
            // Filter for processed bookings only
            List<EntityResponse<Booking>> processedBookings = allBookings.stream()
                .filter(bookingResponse -> "processed".equals(bookingResponse.getMetadata().getState()))
                .collect(Collectors.toList());
            
            entity.setTotalBookings(processedBookings.size());
            logger.info("Collected {} processed bookings for report {}", entity.getTotalBookings(), entity.getReportId());
            
            // Note: In a real implementation, we would store the booking data reference
            // in the report metadata or a separate storage mechanism for the next processors
            // For this implementation, we'll rely on the next processors to re-fetch the data
            
        } catch (Exception e) {
            logger.error("Failed to collect booking data for report {}: {}", entity.getReportId(), e.getMessage(), e);
            throw new RuntimeException("Report data collection failed: " + e.getMessage(), e);
        }

        return entity;
    }
}
