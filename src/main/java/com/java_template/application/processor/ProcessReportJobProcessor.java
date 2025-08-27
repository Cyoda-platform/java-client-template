package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.application.entity.report.version_1.Report;
import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class ProcessReportJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessReportJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ProcessReportJobProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        // mark processing
        job.setStatus("PROCESSING");

        try {
            // Fetch all bookings (we filter in-memory to support flexible criteria)
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                Booking.ENTITY_NAME,
                String.valueOf(Booking.ENTITY_VERSION)
            );
            ArrayNode nodes = itemsFuture.join();

            List<Booking> allBookings = new ArrayList<>();
            if (nodes != null) {
                for (JsonNode node : nodes) {
                    try {
                        Booking b = objectMapper.treeToValue(node, Booking.class);
                        if (b != null) allBookings.add(b);
                    } catch (Exception e) {
                        logger.warn("Failed to convert booking node to Booking class: {}", e.getMessage());
                    }
                }
            }

            // Apply filter criteria from job
            List<Booking> filtered = new ArrayList<>();
            ReportJob.FilterCriteria fc = job.getFilterCriteria();
            String from = null, to = null;
            Double minPrice = null, maxPrice = null;
            String depositStatus = null, customerName = null;
            if (fc != null) {
                if (fc.getDateRange() != null) {
                    from = fc.getDateRange().getFrom();
                    to = fc.getDateRange().getTo();
                }
                minPrice = fc.getMinPrice();
                maxPrice = fc.getMaxPrice();
                depositStatus = fc.getDepositStatus();
                customerName = fc.getCustomerName();
            }

            for (Booking b : allBookings) {
                boolean keep = true;
                // Date filtering (ISO date strings compare lexicographically)
                if (from != null && from.isBlank() == false) {
                    String checkin = b.getBookingDates() != null ? b.getBookingDates().getCheckin() : null;
                    if (checkin == null || checkin.isBlank()) { keep = false; }
                    else if (checkin.compareTo(from) < 0) { keep = false; }
                }
                if (keep && to != null && to.isBlank() == false) {
                    String checkin = b.getBookingDates() != null ? b.getBookingDates().getCheckin() : null;
                    if (checkin == null || checkin.isBlank()) { keep = false; }
                    else if (checkin.compareTo(to) > 0) { keep = false; }
                }

                // Price filtering
                if (keep && minPrice != null) {
                    Double price = b.getTotalPrice();
                    if (price == null || price < minPrice) keep = false;
                }
                if (keep && maxPrice != null) {
                    Double price = b.getTotalPrice();
                    if (price == null || price > maxPrice) keep = false;
                }

                // Deposit status filtering
                if (keep && depositStatus != null && depositStatus.isBlank() == false) {
                    String ds = depositStatus.trim().toLowerCase();
                    Boolean dep = b.getDepositPaid();
                    if ("paid".equals(ds)) {
                        if (dep == null || !dep) keep = false;
                    } else if ("unpaid".equals(ds) || "not_paid".equals(ds) || "false".equals(ds)) {
                        if (dep == null || dep) keep = false;
                    }
                }

                // Customer name filtering (contains on first or last name, case-insensitive)
                if (keep && customerName != null && customerName.isBlank() == false) {
                    String target = customerName.trim().toLowerCase();
                    String fn = b.getFirstName() != null ? b.getFirstName().toLowerCase() : "";
                    String ln = b.getLastName() != null ? b.getLastName().toLowerCase() : "";
                    String full = (fn + " " + ln).trim();
                    if (!fn.contains(target) && !ln.contains(target) && !full.contains(target)) {
                        keep = false;
                    }
                }

                if (keep) filtered.add(b);
            }

            // Build report metrics
            Report report = new Report();
            String reportId = "report-" + UUID.randomUUID().toString();
            report.setReportId(reportId);
            report.setGeneratedAt(Instant.now().toString());
            report.setJobReference(job.getJobId());

            // Rows
            List<Report.BookingRow> rows = new ArrayList<>();
            for (Booking b : filtered) {
                Report.BookingRow row = new Report.BookingRow();
                row.setBookingId(b.getBookingId());
                Report.BookingDates bd = new Report.BookingDates();
                if (b.getBookingDates() != null) {
                    bd.setCheckin(b.getBookingDates().getCheckin());
                    bd.setCheckout(b.getBookingDates().getCheckout());
                }
                row.setBookingDates(bd);
                row.setDepositPaid(b.getDepositPaid());
                row.setFirstName(b.getFirstName());
                row.setLastName(b.getLastName());
                row.setTotalPrice(b.getTotalPrice());
                rows.add(row);
            }
            report.setRows(rows);

            // Metrics
            Report.Metrics metrics = new Report.Metrics();
            metrics.setBookingCount(filtered.size());
            double totalRevenue = 0.0;
            for (Booking b : filtered) {
                if (b.getTotalPrice() != null) totalRevenue += b.getTotalPrice();
            }
            metrics.setTotalRevenue(totalRevenue);
            if (filtered.size() > 0) {
                metrics.setAveragePrice(totalRevenue / filtered.size());
            } else {
                metrics.setAveragePrice(0.0);
            }

            Map<String, Integer> bookingsByRange = new LinkedHashMap<>();
            if (from != null || to != null) {
                String key = ((from != null) ? from : "") + "_to_" + ((to != null) ? to : "");
                bookingsByRange.put(key, filtered.size());
            } else {
                bookingsByRange.put("all", filtered.size());
            }
            metrics.setBookingsByRange(bookingsByRange);
            report.setMetrics(metrics);

            // Scope mapping
            Report.FilterCriteria rfc = new Report.FilterCriteria();
            if (fc != null) {
                rfc.setCustomerName(fc.getCustomerName());
                if (fc.getDateRange() != null) {
                    Report.DateRange dr = new Report.DateRange();
                    dr.setFrom(fc.getDateRange().getFrom());
                    dr.setTo(fc.getDateRange().getTo());
                    rfc.setDateRange(dr);
                }
                if (fc.getDepositStatus() != null) rfc.setDepositStatus(fc.getDepositStatus());
                if (fc.getMinPrice() != null) rfc.setMinPrice(fc.getMinPrice().intValue());
                if (fc.getMaxPrice() != null) rfc.setMaxPrice(fc.getMaxPrice().intValue());
            }
            report.setScope(rfc);

            // Visualizations: chart points from bookingsByRange and table data
            Report.Visualizations viz = new Report.Visualizations();
            Report.ChartData chart = new Report.ChartData();
            Report.ChartData.ChartDataContainer container = new Report.ChartData.ChartDataContainer();
            List<List<Object>> points = new ArrayList<>();
            for (Map.Entry<String, Integer> e : bookingsByRange.entrySet()) {
                List<Object> point = new ArrayList<>();
                point.add(e.getKey());
                point.add(e.getValue());
                points.add(point);
            }
            container.setPoints(points);
            chart.setData(container);
            chart.setType(job.getVisualization() != null ? job.getVisualization() : "table");
            viz.setChartData(chart);
            viz.setTableData(rows);
            report.setVisualizations(viz);

            // Persist report (add new entity)
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                Report.ENTITY_NAME,
                String.valueOf(Report.ENTITY_VERSION),
                report
            );
            idFuture.join();

            // Update job with result and mark completed (job entity will be persisted by Cyoda)
            job.setResultReportId(report.getReportId());
            job.setStatus("COMPLETED");
            logger.info("ReportJob {} completed, generated report {}", job.getJobId(), report.getReportId());
        } catch (Exception ex) {
            logger.error("Failed processing ReportJob {}: {}", job.getJobId(), ex.getMessage(), ex);
            job.setStatus("FAILED");
        }

        return job;
    }
}