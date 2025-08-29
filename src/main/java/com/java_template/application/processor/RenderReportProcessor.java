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

import java.util.concurrent.CompletableFuture;
import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

@Component
public class RenderReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RenderReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public RenderReportProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        if (job == null) return job;

        try {
            // Fetch all persisted bookings
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                Booking.ENTITY_NAME,
                Booking.ENTITY_VERSION,
                null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<Booking> bookings = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    try {
                        Object dataNode = payload.getData();
                        // objectMapper can convert JsonNode/ObjectNode to Booking
                        Booking b = objectMapper.treeToValue((com.fasterxml.jackson.databind.JsonNode) payload.getData(), Booking.class);
                        if (b != null) bookings.add(b);
                    } catch (Exception ex) {
                        logger.warn("Skipping a booking record due to mapping error: {}", ex.getMessage());
                    }
                }
            }

            // Apply filters from job
            ReportJob.Filters filters = job.getFilters();
            List<Booking> filtered = new ArrayList<>();
            LocalDate dateFrom = null;
            LocalDate dateTo = null;
            DateTimeFormatter df = DateTimeFormatter.ISO_LOCAL_DATE;
            if (filters != null) {
                try {
                    if (filters.getDateFrom() != null && !filters.getDateFrom().isBlank()) dateFrom = LocalDate.parse(filters.getDateFrom(), df);
                } catch (Exception ex) {
                    logger.warn("Invalid dateFrom in filters: {}", filters.getDateFrom());
                }
                try {
                    if (filters.getDateTo() != null && !filters.getDateTo().isBlank()) dateTo = LocalDate.parse(filters.getDateTo(), df);
                } catch (Exception ex) {
                    logger.warn("Invalid dateTo in filters: {}", filters.getDateTo());
                }
            }

            for (Booking b : bookings) {
                boolean ok = true;
                // Date filtering (based on checkin date)
                if (dateFrom != null || dateTo != null) {
                    try {
                        LocalDate checkin = b.getCheckin() != null ? LocalDate.parse(b.getCheckin(), df) : null;
                        if (checkin == null) {
                            ok = false;
                        } else {
                            if (dateFrom != null && checkin.isBefore(dateFrom)) ok = false;
                            if (dateTo != null && checkin.isAfter(dateTo)) ok = false;
                        }
                    } catch (Exception ex) {
                        ok = false;
                    }
                }
                // Price filtering
                if (ok && filters != null) {
                    Integer minP = filters.getMinPrice();
                    Integer maxP = filters.getMaxPrice();
                    if (minP != null) {
                        if (b.getTotalprice() == null || b.getTotalprice() < minP) ok = false;
                    }
                    if (maxP != null) {
                        if (b.getTotalprice() == null || b.getTotalprice() > maxP) ok = false;
                    }
                    // Deposit filter (nullable meaning any)
                    if (filters.getDepositPaid() != null) {
                        if (b.getDepositpaid() == null || !b.getDepositpaid().equals(filters.getDepositPaid())) ok = false;
                    }
                }
                if (ok) filtered.add(b);
            }

            // Aggregation
            double totalRevenue = 0.0;
            int count = 0;
            for (Booking b : filtered) {
                if (b.getTotalprice() != null) {
                    totalRevenue += b.getTotalprice();
                }
                count++;
            }
            Double average = null;
            if (count > 0) average = totalRevenue / count;

            // Build report
            Report report = new Report();
            report.setName(job.getName());
            report.setCreatedBy(job.getRequestedBy());
            String generatedAt = Instant.now().toString();
            report.setGeneratedAt(generatedAt);
            report.setJobTechnicalId(context.request() != null ? context.request().getEntityId() : null);
            report.setReportId(UUID.randomUUID().toString());
            report.setTotalRevenue(totalRevenue);
            report.setBookingsCount(count);
            report.setAverageBookingPrice(average);
            Report.Criteria criteria = new Report.Criteria();
            if (filters != null) {
                criteria.setDateFrom(filters.getDateFrom());
                criteria.setDateTo(filters.getDateTo());
                criteria.setDepositPaid(filters.getDepositPaid());
                criteria.setMaxPrice(filters.getMaxPrice());
                criteria.setMinPrice(filters.getMinPrice());
            }
            report.setCriteria(criteria);

            // Sample up to 5 bookings
            List<Report.BookingSummary> samples = new ArrayList<>();
            int sampleSize = Math.min(5, filtered.size());
            for (int i = 0; i < sampleSize; i++) {
                Booking b = filtered.get(i);
                Report.BookingSummary bs = new Report.BookingSummary();
                bs.setAdditionalneeds(b.getAdditionalneeds());
                bs.setBookingId(b.getBookingId());
                bs.setCheckin(b.getCheckin());
                bs.setCheckout(b.getCheckout());
                bs.setDepositpaid(b.getDepositpaid());
                bs.setFirstname(b.getFirstname());
                bs.setLastname(b.getLastname());
                bs.setPersistedAt(b.getPersistedAt());
                bs.setSource(b.getSource());
                bs.setTotalprice(b.getTotalprice());
                samples.add(bs);
            }
            report.setBookingsSample(samples);

            // Visualization: if includeCharts true, generate a placeholder URL (actual generation is out of scope)
            if (Boolean.TRUE.equals(job.getIncludeCharts())) {
                report.setVisualizationUrl("/artifacts/" + report.getReportId() + "/chart.png");
            }

            // Set report status
            if (count > 0) {
                report.setStatus("GENERATED");
            } else {
                report.setStatus("FAILED");
            }

            // Persist report (allowed - different entity)
            try {
                CompletableFuture<UUID> idFuture = entityService.addItem(
                    Report.ENTITY_NAME,
                    Report.ENTITY_VERSION,
                    report
                );
                UUID persistedReportId = idFuture.get();
                logger.info("Persisted Report with technical id: {}", persistedReportId);
            } catch (Exception ex) {
                logger.error("Failed to persist Report entity: {}", ex.getMessage(), ex);
                // mark report as failed if persistence fails
                report.setStatus("FAILED");
            }

            // Update job state (do not call entityService.updateItem on this job; modify and return - will be persisted by Cyoda)
            if ("GENERATED".equals(report.getStatus())) {
                job.setStatus("COMPLETED");
                job.setCompletedAt(generatedAt);
            } else {
                job.setStatus("FAILED");
                job.setCompletedAt(generatedAt);
            }

        } catch (Exception e) {
            logger.error("Error while rendering report for job {}: {}", job.getName(), e.getMessage(), e);
            job.setStatus("FAILED");
            job.setCompletedAt(Instant.now().toString());
        }

        return job;
    }
}