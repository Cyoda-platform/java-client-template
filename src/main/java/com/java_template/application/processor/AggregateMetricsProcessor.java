package com.java_template.application.processor;

import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.application.entity.report.version_1.Report;
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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class AggregateMetricsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AggregateMetricsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AggregateMetricsProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        ReportJob job = context.entity();
        // Mark as aggregating while computing
        try {
            job.setStatus("AGGREGATING");
        } catch (Exception e) {
            // ignore if cannot set for any reason - continue processing
            logger.debug("Unable to set status to AGGREGATING: {}", e.getMessage());
        }

        List<Booking> bookings = new ArrayList<>();
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                Booking.ENTITY_NAME,
                Booking.ENTITY_VERSION,
                null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    try {
                        Booking b = objectMapper.treeToValue(payload.getData(), Booking.class);
                        if (b != null) bookings.add(b);
                    } catch (Exception ex) {
                        logger.warn("Failed to convert payload to Booking: {}", ex.getMessage());
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Failed to load bookings from entity service: {}", ex.getMessage(), ex);
            // mark job as failed and return
            try { job.setStatus("FAILED"); } catch (Exception ignored) {}
            return job;
        }

        // Apply filters from job
        List<Booking> filtered = new ArrayList<>();
        ReportJob.Filters filters = job.getFilters();
        LocalDate dateFrom = null;
        LocalDate dateTo = null;
        DateTimeFormatter dtf = DateTimeFormatter.ISO_LOCAL_DATE;
        if (filters != null) {
            try {
                if (filters.getDateFrom() != null && !filters.getDateFrom().isBlank())
                    dateFrom = LocalDate.parse(filters.getDateFrom(), dtf);
            } catch (DateTimeParseException e) {
                logger.warn("Invalid dateFrom in filters: {}", filters.getDateFrom());
                dateFrom = null;
            }
            try {
                if (filters.getDateTo() != null && !filters.getDateTo().isBlank())
                    dateTo = LocalDate.parse(filters.getDateTo(), dtf);
            } catch (DateTimeParseException e) {
                logger.warn("Invalid dateTo in filters: {}", filters.getDateTo());
                dateTo = null;
            }
        }

        for (Booking b : bookings) {
            if (b == null) continue;
            // validate booking minimal validity using available getters
            if (!b.isValid()) continue;

            // date filter: check booking.checkin within range if provided
            boolean pass = true;
            if (dateFrom != null || dateTo != null) {
                try {
                    LocalDate checkin = LocalDate.parse(b.getCheckin(), dtf);
                    if (dateFrom != null && checkin.isBefore(dateFrom)) pass = false;
                    if (dateTo != null && checkin.isAfter(dateTo)) pass = false;
                } catch (DateTimeParseException e) {
                    // if booking date is invalid, skip it
                    pass = false;
                }
            }

            // price filter
            if (pass && filters != null && filters.getMinPrice() != null) {
                Double total = b.getTotalprice();
                if (total == null || total < filters.getMinPrice()) pass = false;
            }
            if (pass && filters != null && filters.getMaxPrice() != null) {
                Double total = b.getTotalprice();
                if (total == null || total > filters.getMaxPrice()) pass = false;
            }

            // deposit filter
            if (pass && filters != null && filters.getDepositPaid() != null) {
                Boolean deposit = b.getDepositpaid();
                if (deposit == null || !deposit.equals(filters.getDepositPaid())) pass = false;
            }

            if (pass) filtered.add(b);
        }

        // Compute aggregates
        double totalRevenue = 0.0;
        int count = 0;
        for (Booking b : filtered) {
            if (b.getTotalprice() != null) {
                totalRevenue += b.getTotalprice();
            }
            count++;
        }
        double average = count > 0 ? totalRevenue / count : 0.0;

        // Build Report entity (other entity) and persist it
        Report report = new Report();
        report.setReportId(UUID.randomUUID().toString());
        // job technical id from request context if available
        try {
            String reqId = context.request() != null ? context.request().getId() : null;
            report.setJobTechnicalId(reqId != null ? reqId : UUID.randomUUID().toString());
        } catch (Exception e) {
            report.setJobTechnicalId(UUID.randomUUID().toString());
        }
        report.setName(job.getName());
        report.setCreatedBy(job.getRequestedBy());
        report.setGeneratedAt(Instant.now().toString());
        report.setTotalRevenue(totalRevenue);
        report.setAverageBookingPrice(average);
        report.setBookingsCount(count);
        report.setStatus(count > 0 ? "GENERATED" : "FAILED");

        // Map criteria
        if (filters != null) {
            Report.Criteria rc = new Report.Criteria();
            rc.setDateFrom(filters.getDateFrom());
            rc.setDateTo(filters.getDateTo());
            rc.setDepositPaid(filters.getDepositPaid());
            rc.setMinPrice(filters.getMinPrice());
            rc.setMaxPrice(filters.getMaxPrice());
            report.setCriteria(rc);
        }

        // Build sample (up to 10)
        List<Report.BookingSummary> sample = new ArrayList<>();
        int sampleSize = Math.min(10, filtered.size());
        for (int i = 0; i < sampleSize; i++) {
            Booking b = filtered.get(i);
            Report.BookingSummary bs = new Report.BookingSummary();
            bs.setBookingId(b.getBookingId());
            bs.setFirstname(b.getFirstname());
            bs.setLastname(b.getLastname());
            bs.setCheckin(b.getCheckin());
            bs.setCheckout(b.getCheckout());
            bs.setPersistedAt(b.getPersistedAt());
            bs.setAdditionalneeds(b.getAdditionalneeds());
            bs.setDepositpaid(b.getDepositpaid());
            bs.setSource(b.getSource());
            bs.setTotalprice(b.getTotalprice());
            sample.add(bs);
        }
        report.setBookingsSample(sample);

        // Optionally visualizationUrl will be generated later in RenderReportProcessor
        report.setVisualizationUrl(null);

        try {
            CompletableFuture<java.util.UUID> addFuture = entityService.addItem(
                Report.ENTITY_NAME,
                Report.ENTITY_VERSION,
                report
            );
            java.util.UUID addedId = addFuture.get();
            logger.info("Created Report entity with technical id: {}", addedId);
        } catch (Exception ex) {
            logger.error("Failed to persist Report entity: {}", ex.getMessage(), ex);
            // mark job failed and return
            try { job.setStatus("FAILED"); } catch (Exception ignored) {}
            return job;
        }

        // Update job status based on data sufficiency
        if (count > 0) {
            try { job.setStatus("GENERATING"); } catch (Exception e) { logger.debug("Unable to set job status to GENERATING: {}", e.getMessage()); }
        } else {
            try { job.setStatus("FAILED"); } catch (Exception e) { logger.debug("Unable to set job status to FAILED: {}", e.getMessage()); }
        }

        return job;
    }
}