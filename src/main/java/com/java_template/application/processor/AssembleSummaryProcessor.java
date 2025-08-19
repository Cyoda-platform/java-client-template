package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
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

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@Component
public class AssembleSummaryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AssembleSummaryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;

    public AssembleSummaryProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AssembleSummary for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(WeeklyReport.class)
                .validate(this::isValidEntity, "Invalid WeeklyReport state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(WeeklyReport report) {
        return report != null && report.isValid();
    }

    private WeeklyReport processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<WeeklyReport> context) {
        WeeklyReport report = context.entity();
        try {
            StringJoiner sj = new StringJoiner("\n");
            sj.add("Weekly Report Generated: " + report.getGenerationTimestamp());
            sj.add("Fetch Job: " + report.getFetchJobId());
            sj.add("Period: " + report.getWeekStartDate() + " - " + report.getWeekEndDate());
            sj.add("Total Books: " + report.getTotalBooks());
            sj.add("Total Pages: " + report.getTotalPages());
            sj.add("Average Pages: " + String.format("%.2f", report.getAvgPages()));

            if (report.getTopTitles() != null && !report.getTopTitles().isEmpty()) {
                sj.add("Top Titles:");
                int idx = 1;
                for (Map<String, Object> t : report.getTopTitles()) {
                    sj.add(idx++ + ". " + t.get("title") + " (pages: " + t.get("pageCount") + ", score: " + t.get("popularityScore") + ")");
                }
            }

            // Build CSV attachment for top titles
            StringBuilder csv = new StringBuilder();
            csv.append("id,title,pageCount,popularityScore\n");
            if (report.getTopTitles() != null) {
                for (Map<String, Object> t : report.getTopTitles()) {
                    csv.append(t.get("id")).append(',').append('"').append(t.get("title")).append('"').append(',').append(t.get("pageCount")).append(',').append(t.get("popularityScore")).append('\n');
                }
            }

            Map<String, Object> delivery = report.getDeliveryInfo();
            if (delivery == null) delivery = new java.util.LinkedHashMap<>();
            delivery.put("summaryText", sj.toString());
            delivery.put("csvAttachment", csv.toString());
            report.setDeliveryInfo(delivery);
            report.setReportStatus("prepared");
        } catch (Exception ex) {
            logger.error("AssembleSummaryProcessor: error assembling summary", ex);
            report.setReportStatus("failed");
        }
        return report;
    }
}
