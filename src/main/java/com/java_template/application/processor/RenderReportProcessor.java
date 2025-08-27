package com.java_template.application.processor;
import com.java_template.application.entity.report.version_1.Report;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RenderReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RenderReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RenderReportProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Report for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Report.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Report entity) {
        return entity != null && entity.isValid();
    }

    private Report processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Report> context) {
        Report entity = context.entity();

        // Ensure visualizations object exists
        Report.Visualizations visualizations = entity.getVisualizations();
        if (visualizations == null) {
            visualizations = new Report.Visualizations();
            entity.setVisualizations(visualizations);
        }

        // Ensure tableData is present: reuse rows as table data if not explicitly provided
        if (visualizations.getTableData() == null) {
            // Defensive copy to avoid accidental shared mutation
            List<Report.BookingRow> rows = entity.getRows();
            if (rows != null) {
                List<Report.BookingRow> table = new ArrayList<>(rows);
                visualizations.setTableData(table);
            } else {
                visualizations.setTableData(new ArrayList<>());
            }
        }

        // Prepare chart data based on metrics.bookingsByRange if present, otherwise derive from rows by checkin date
        Report.ChartData chartData = visualizations.getChartData();
        if (chartData == null) {
            chartData = new Report.ChartData();
            visualizations.setChartData(chartData);
        }

        Report.ChartData.ChartDataContainer container = chartData.getData();
        if (container == null) {
            container = new Report.ChartData.ChartDataContainer();
            chartData.setData(container);
        }

        List<List<Object>> points = container.getPoints();
        if (points == null || points.isEmpty()) {
            Map<String, Integer> bookingsByRange = null;
            if (entity.getMetrics() != null) {
                bookingsByRange = entity.getMetrics().getBookingsByRange();
            }

            Map<String, Integer> computed = new HashMap<>();
            if (bookingsByRange != null && !bookingsByRange.isEmpty()) {
                // copy provided bookingsByRange into computed map to preserve ordering/values
                computed.putAll(bookingsByRange);
            } else {
                // derive counts by checkin date from rows
                List<Report.BookingRow> rows = entity.getRows();
                if (rows != null) {
                    for (Report.BookingRow row : rows) {
                        if (row == null) continue;
                        Report.BookingDates bd = row.getBookingDates();
                        String key = (bd != null && bd.getCheckin() != null && !bd.getCheckin().isBlank())
                                ? bd.getCheckin()
                                : "unknown";
                        computed.put(key, computed.getOrDefault(key, 0) + 1);
                    }
                }
            }

            // build points list from computed map
            List<List<Object>> built = new ArrayList<>();
            for (Map.Entry<String, Integer> e : computed.entrySet()) {
                List<Object> point = new ArrayList<>(2);
                point.add(e.getKey());
                point.add(e.getValue());
                built.add(point);
            }
            container.setPoints(built);
        }

        // Ensure chart type is set; default to "bar" if absent
        if (chartData.getType() == null || chartData.getType().isBlank()) {
            chartData.setType("bar");
        }

        // attach container back to chartData and visualizations (already done via setters above)
        chartData.setData(container);
        visualizations.setChartData(chartData);
        entity.setVisualizations(visualizations);

        logger.debug("Rendered visualizations for reportId={}", entity.getReportId());
        return entity;
    }
}