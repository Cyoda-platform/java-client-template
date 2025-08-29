package com.java_template.application.processor;

import com.java_template.application.entity.reportjob.version_1.ReportJob;
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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class FilterBookingsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FilterBookingsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public FilterBookingsProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
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

        // Business logic: load persisted Bookings and filter them according to ReportJob.filters
        ReportJob.Filters filters = entity.getFilters();
        List<Booking> filtered = new ArrayList<>();

        try {
            // Fetch all bookings (we'll filter in-memory based on available filter criteria)
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                Booking.ENTITY_NAME,
                Booking.ENTITY_VERSION,
                null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();

            if (dataPayloads == null || dataPayloads.isEmpty()) {
                logger.info("No bookings persisted in the system to filter for job: {}", entity.getName());
            } else {
                for (DataPayload payload : dataPayloads) {
                    try {
                        Booking booking = objectMapper.treeToValue(payload.getData(), Booking.class);
                        if (booking == null) continue;

                        // Apply filters if present
                        boolean matches = true;

                        if (filters != null) {
                            // Date range filter (inclusive)
                            if (filters.getDateFrom() != null && !filters.getDateFrom().isBlank()) {
                                try {
                                    LocalDate from = LocalDate.parse(filters.getDateFrom());
                                    LocalDate checkin = LocalDate.parse(booking.getCheckin());
                                    if (checkin.isBefore(from)) {
                                        matches = false;
                                    }
                                } catch (DateTimeParseException ex) {
                                    logger.warn("Invalid date parsing while filtering (dateFrom/checkin): {}", ex.getMessage());
                                    matches = false;
                                }
                            }
                            if (matches && filters.getDateTo() != null && !filters.getDateTo().isBlank()) {
                                try {
                                    LocalDate to = LocalDate.parse(filters.getDateTo());
                                    LocalDate checkout = LocalDate.parse(booking.getCheckout());
                                    if (checkout.isAfter(to)) {
                                        matches = false;
                                    }
                                } catch (DateTimeParseException ex) {
                                    logger.warn("Invalid date parsing while filtering (dateTo/checkout): {}", ex.getMessage());
                                    matches = false;
                                }
                            }

                            // Price range filter (inclusive)
                            if (matches && filters.getMinPrice() != null) {
                                Double min = filters.getMinPrice().doubleValue();
                                if (booking.getTotalprice() == null || booking.getTotalprice() < min) {
                                    matches = false;
                                }
                            }
                            if (matches && filters.getMaxPrice() != null) {
                                Double max = filters.getMaxPrice().doubleValue();
                                if (booking.getTotalprice() == null || booking.getTotalprice() > max) {
                                    matches = false;
                                }
                            }

                            // Deposit paid filter (can be null meaning any)
                            if (matches && filters.getDepositPaid() != null) {
                                if (booking.getDepositpaid() == null || !booking.getDepositpaid().equals(filters.getDepositPaid())) {
                                    matches = false;
                                }
                            }
                        }

                        if (matches) {
                            filtered.add(booking);
                        }
                    } catch (Exception ex) {
                        logger.warn("Failed to convert payload to Booking or filter it: {}", ex.getMessage());
                    }
                }
            }

            logger.info("Filtered bookings for job '{}': found {} matching entries", entity.getName(), filtered.size());

            // Update job status to indicate next step in workflow
            entity.setStatus("AGGREGATING");

            // Optionally, if needed in future processors, we could persist a derived artifact here (e.g., temporary list),
            // but per rules we should not add/update/delete the triggering entity via EntityService.
            // The filtered results will be re-computed/used by subsequent processors (AggregateMetricsProcessor).

        } catch (InterruptedException | ExecutionException ex) {
            logger.error("Failed to fetch bookings while filtering: {}", ex.getMessage(), ex);
            // mark job as FAILED so workflow can stop or retry
            entity.setStatus("FAILED");
        } catch (Exception ex) {
            logger.error("Unexpected error in FilterBookingsProcessor: {}", ex.getMessage(), ex);
            entity.setStatus("FAILED");
        }

        return entity;
    }
}