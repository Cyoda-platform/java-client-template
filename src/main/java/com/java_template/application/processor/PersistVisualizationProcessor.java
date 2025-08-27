package com.java_template.application.processor;

import com.java_template.application.entity.report.version_1.Report;
import com.java_template.application.entity.report.version_1.Report.ChartData;
import com.java_template.application.entity.report.version_1.Report.Visualizations;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PersistVisualizationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistVisualizationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PersistVisualizationProcessor(SerializerFactory serializerFactory) {
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

        // Ensure generatedAt is set (mark when visualization is persisted/published)
        if (entity.getGeneratedAt() == null || entity.getGeneratedAt().isBlank()) {
            entity.setGeneratedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        }

        // Ensure visualizations object exists and has basic chart/table data derived from report content
        Visualizations visualizations = entity.getVisualizations();
        if (visualizations == null) {
            visualizations = new Visualizations();
        }

        // Populate tableData from rows if not present
        if (visualizations.getTableData() == null) {
            visualizations.setTableData(entity.getRows());
        }

        // Build chart data points from metrics.bookingsByRange when available
        ChartData chartData = visualizations.getChartData();
        if (chartData == null) {
            chartData = new ChartData();
            chartData.setData(new ChartData.ChartDataContainer());
        } else if (chartData.getData() == null) {
            chartData.setData(new ChartData.ChartDataContainer());
        }

        // Prepare points from bookingsByRange metric
        if (entity.getMetrics() != null && entity.getMetrics().getBookingsByRange() != null) {
            Map<String, Integer> bookingsByRange = entity.getMetrics().getBookingsByRange();
            // Obtain existing points (may be null) and create a single final mutable list for lambda use
            List<List<Object>> existingPoints = chartData.getData().getPoints();
            final List<List<Object>> points = existingPoints == null ? new ArrayList<>() : new ArrayList<>(existingPoints);

            bookingsByRange.forEach((range, count) -> {
                List<Object> point = new ArrayList<>();
                point.add(range);
                point.add(count);
                points.add(point);
            });

            chartData.getData().setPoints(points);
        } else {
            // ensure at least an empty points list so downstream renderers don't NPE
            if (chartData.getData().getPoints() == null) {
                chartData.getData().setPoints(new ArrayList<>());
            }
        }

        // Default chart type if not provided
        if (chartData.getType() == null || chartData.getType().isBlank()) {
            chartData.setType("chart");
        }

        visualizations.setChartData(chartData);
        entity.setVisualizations(visualizations);

        // Return modified entity. Cyoda will persist the entity state.
        return entity;
    }
}