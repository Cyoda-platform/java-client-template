package com.java_template.application.processor;
import com.java_template.application.entity.reportjob.version_1.ReportJob;
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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Component
public class ValidateReportRequestProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateReportRequestProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateReportRequestProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReportJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(ReportJob.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ReportJob entity) {
        return entity != null && entity.isValid();
    }

    private ReportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ReportJob> context) {
        ReportJob entity = context.entity();

        try {
            // Ensure the job moves from PENDING -> VALIDATING (implicitly handled by workflow)
            // Perform business validation on filters (date range and price range)
            ReportJob.Filters filters = entity.getFilters();
            if (filters != null) {
                String dateFromStr = filters.getDateFrom();
                String dateToStr = filters.getDateTo();

                // Validate date parsing
                LocalDate dateFrom = null;
                LocalDate dateTo = null;
                try {
                    if (dateFromStr != null && !dateFromStr.isBlank()) {
                        dateFrom = LocalDate.parse(dateFromStr);
                    }
                    if (dateToStr != null && !dateToStr.isBlank()) {
                        dateTo = LocalDate.parse(dateToStr);
                    }
                } catch (DateTimeParseException ex) {
                    logger.warn("Invalid date format in filters: {}, {}", dateFromStr, dateToStr, ex);
                    entity.setStatus("FAILED");
                    entity.setCompletedAt(Instant.now().toString());
                    return entity;
                }

                // If both dates present, ensure from <= to
                if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
                    logger.warn("Invalid date range: dateFrom is after dateTo: {} > {}", dateFrom, dateTo);
                    entity.setStatus("FAILED");
                    entity.setCompletedAt(Instant.now().toString());
                    return entity;
                }

                // Validate price range if present
                Integer minPrice = filters.getMinPrice();
                Integer maxPrice = filters.getMaxPrice();
                if (minPrice != null && minPrice < 0) {
                    logger.warn("Invalid minPrice: {}", minPrice);
                    entity.setStatus("FAILED");
                    entity.setCompletedAt(Instant.now().toString());
                    return entity;
                }
                if (maxPrice != null && maxPrice < 0) {
                    logger.warn("Invalid maxPrice: {}", maxPrice);
                    entity.setStatus("FAILED");
                    entity.setCompletedAt(Instant.now().toString());
                    return entity;
                }
                if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
                    logger.warn("Invalid price range: minPrice > maxPrice: {} > {}", minPrice, maxPrice);
                    entity.setStatus("FAILED");
                    entity.setCompletedAt(Instant.now().toString());
                    return entity;
                }
            }

            // All validations passed -> advance workflow to FETCHING
            entity.setStatus("FETCHING");
            // Ensure completedAt is cleared if previously set
            entity.setCompletedAt(null);
            return entity;

        } catch (Exception ex) {
            logger.error("Unexpected error during validation: {}", ex.getMessage(), ex);
            entity.setStatus("FAILED");
            entity.setCompletedAt(Instant.now().toString());
            return entity;
        }
    }
}