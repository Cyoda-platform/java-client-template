package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.application.entity.report.version_1.Report;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Component
public class AggregateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AggregateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public AggregateProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("AggregateProcessor invoked for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ReportJob.class)
            .validate(this::isValidEntity, "Invalid ReportJob for aggregation")
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
        try {
            // Query bookings for source RestfulBooker and matching filters
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
                    if (!matchesFilters(b, job)) continue;
                    if (b.getStatus() != null && b.getStatus().equalsIgnoreCase("CANCELLED")) continue;
                    matched.add(b);
                } catch (Exception e) {
                    logger.warn("Skipping booking due to conversion error: {}", e.getMessage());
                }
            }

            // Grouping
            Map<String, List<Booking>> groups = new LinkedHashMap<>();
            DateTimeFormatter isoDate = DateTimeFormatter.ISO_LOCAL_DATE;
            for (Booking b : matched) {
                String label = "";
                if (job.getGrouping() == null || job.getGrouping().isEmpty() || job.getGrouping().equalsIgnoreCase("MONTHLY")) {
                    LocalDate d = LocalDate.parse(b.getCheckInDate());
                    label = d.format(DateTimeFormatter.ofPattern("yyyy-MM"));
                } else if (job.getGrouping().equalsIgnoreCase("DAILY")) {
                    LocalDate d = LocalDate.parse(b.getCheckInDate());
                    label = d.format(isoDate);
                } else if (job.getGrouping().equalsIgnoreCase("WEEKLY")) {
                    LocalDate d = LocalDate.parse(b.getCheckInDate());
                    java.time.temporal.WeekFields wf = java.time.temporal.WeekFields.ISO;
                    int w = d.get(wf.weekOfWeekBasedYear());
                    label = d.getYear() + "-W" + String.format("%02d", w);
                }
                groups.computeIfAbsent(label, k -> new ArrayList<>()).add(b);
            }

            // Build report
            ObjectNode reportNode = entityService.objectMapper().createObjectNode();
            String reportId = "report_" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8);
            reportNode.put("reportId", reportId);
            reportNode.put("technicalId", reportId);
            reportNode.put("jobRef", job.getTechnicalId());
            reportNode.put("periodFrom", job.getFilterDateFrom());
            reportNode.put("periodTo", job.getFilterDateTo());
            reportNode.put("grouping", job.getGrouping() == null ? "MONTHLY" : job.getGrouping());
            reportNode.put("presentationType", job.getPresentationType() == null ? "TABLE" : job.getPresentationType());
            reportNode.put("generatedAt", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

            ObjectNode metrics = entityService.objectMapper().createObjectNode();
            double totalRevenue = 0.0;
            int bookingCount = 0;
            for (List<Booking> list : groups.values()) {
                for (Booking b : list) {
                    if (b.getTotalPrice() != null) totalRevenue += b.getTotalPrice();
                    bookingCount++;
                }
            }
            metrics.put("totalRevenue", totalRevenue);
            metrics.put("bookingCount", bookingCount);
            metrics.put("avgPrice", bookingCount == 0 ? 0.0 : totalRevenue / bookingCount);
            metrics.put("currency", "USD");
            reportNode.set("metrics", metrics);

            // groupingBuckets
            ArrayNode buckets = entityService.objectMapper().createArrayNode();
            for (Map.Entry<String, List<Booking>> e : groups.entrySet()) {
                ObjectNode bnode = entityService.objectMapper().createObjectNode();
                double tr = 0.0; int bc = 0;
                for (Booking b : e.getValue()) {
                    if (b.getTotalPrice() != null) tr += b.getTotalPrice();
                    bc++;
                }
                bnode.put("label", e.getKey());
                bnode.put("totalRevenue", tr);
                bnode.put("bookingCount", bc);
                buckets.add(bnode);
            }
            reportNode.set("groupingBuckets", buckets);

            // Persist report
            CompletableFuture<java.util.UUID> addFuture = entityService.addItem(
                "Report",
                "1",
                reportNode
            );
            java.util.UUID persistedReportId = addFuture.get();
            logger.info("Report persisted with id: {}", persistedReportId);

            // Update job
            job.setStatus("COMPLETED");
            job.setCompletedAt(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            job.setReportId(reportId);

        } catch (Exception e) {
            logger.error("Aggregation failed: {}", e.getMessage());
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
