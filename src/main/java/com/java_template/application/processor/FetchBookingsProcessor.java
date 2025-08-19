package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class FetchBookingsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchBookingsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public FetchBookingsProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Fetching bookings for ReportJob request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ReportJob.class)
            .validate(this::isValidEntity, "Invalid ReportJob for fetching bookings")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ReportJob job) {
        return job != null && job.getTechnicalId() != null && !job.getTechnicalId().isEmpty();
    }

    private ReportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ReportJob> context) {
        ReportJob job = context.entity();
        // Query persisted bookings that match job filters and source=RestfulBooker
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.source", "EQUALS", "RestfulBooker")
            );
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                Booking.ENTITY_NAME,
                String.valueOf(Booking.ENTITY_VERSION),
                condition,
                true
            );
            ArrayNode items = itemsFuture.get();
            List<Booking> matched = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                try {
                    Booking b = (Booking) entityService.objectMapper().treeToValue(items.get(i), Booking.class);
                    // Apply job filters: filterDateFrom/to, min/max price, depositPaid
                    if (!matchesFilters(b, job)) {
                        continue;
                    }
                    matched.add(b);
                } catch (Exception e) {
                    logger.warn("Skipping booking due to conversion error: {}", e.getMessage());
                }
            }

            // Compute simple aggregates
            double totalRevenue = 0.0;
            int count = 0;
            for (Booking b : matched) {
                if (b.getStatus() != null && b.getStatus().equalsIgnoreCase("CANCELLED")) {
                    continue; // skip cancellations
                }
                if (b.getTotalPrice() != null) {
                    totalRevenue += b.getTotalPrice();
                }
                count++;
            }
            double avg = count == 0 ? 0.0 : totalRevenue / count;

            // Create a Report entity and persist via PersistReportProcessor using entityService
            // Build minimal Report object via Json to persist
            com.fasterxml.jackson.databind.node.ObjectNode reportNode = entityService.objectMapper().createObjectNode();
            String reportId = "report_" + java.util.UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8);
            reportNode.put("reportId", reportId);
            reportNode.put("technicalId", reportId);
            reportNode.put("jobRef", job.getTechnicalId());
            reportNode.put("periodFrom", job.getFilterDateFrom());
            reportNode.put("periodTo", job.getFilterDateTo());
            com.fasterxml.jackson.databind.node.ObjectNode metrics = entityService.objectMapper().createObjectNode();
            metrics.put("totalRevenue", totalRevenue);
            metrics.put("avgPrice", avg);
            metrics.put("bookingCount", count);
            metrics.put("currency", "USD");
            reportNode.set("metrics", metrics);
            reportNode.put("grouping", job.getGrouping() == null ? "MONTHLY" : job.getGrouping());
            reportNode.put("presentationType", job.getPresentationType() == null ? "TABLE" : job.getPresentationType());
            reportNode.put("generatedAt", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

            CompletableFuture<java.util.UUID> addFuture = entityService.addItem(
                "Report",
                "1",
                reportNode
            );
            java.util.UUID persistedReportId = addFuture.get();
            logger.info("Report persisted with id: {}", persistedReportId);

            // Update job with completed status and reportId
            job.setStatus("COMPLETED");
            job.setCompletedAt(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            job.setReportId(reportId);

        } catch (Exception e) {
            logger.error("Error during fetching or aggregation: {}", e.getMessage());
            job.setStatus("FAILED");
            job.setErrorDetails(e.getMessage());
            job.setCompletedAt(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
        return job;
    }

    private boolean matchesFilters(Booking b, ReportJob job) {
        try {
            if (job.getFilterDateFrom() != null && b.getCheckOutDate() != null) {
                if (b.getCheckOutDate().compareTo(job.getFilterDateFrom()) < 0) {
                    return false;
                }
            }
            if (job.getFilterDateTo() != null && b.getCheckInDate() != null) {
                if (b.getCheckInDate().compareTo(job.getFilterDateTo()) > 0) {
                    return false;
                }
            }
            if (job.getMinPrice() != null && b.getTotalPrice() != null) {
                if (b.getTotalPrice() < job.getMinPrice()) {
                    return false;
                }
            }
            if (job.getMaxPrice() != null && b.getTotalPrice() != null) {
                if (b.getTotalPrice() > job.getMaxPrice()) {
                    return false;
                }
            }
            if (job.getDepositPaid() != null && b.getDepositPaid() != null) {
                if (!job.getDepositPaid().equals(b.getDepositPaid())) {
                    return false;
                }
            }
        } catch (Exception e) {
            logger.debug("Error applying filters: {}", e.getMessage());
            return false;
        }
        return true;
    }
}
