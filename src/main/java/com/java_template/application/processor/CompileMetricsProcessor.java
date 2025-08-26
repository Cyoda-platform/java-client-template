package com.java_template.application.processor;

import com.java_template.application.entity.monthlyreport.version_1.MonthlyReport;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
public class CompileMetricsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CompileMetricsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CompileMetricsProcessor(SerializerFactory serializerFactory,
                                   EntityService entityService,
                                   ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing MonthlyReport for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(MonthlyReport.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(MonthlyReport entity) {
        return entity != null && entity.isValid();
    }

    private MonthlyReport processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<MonthlyReport> context) {
        MonthlyReport report = context.entity();

        try {
            String month = report.getMonth();
            if (month == null || month.isBlank()) {
                logger.warn("MonthlyReport month is missing; marking report as FAILED");
                report.setStatus("FAILED");
                return report;
            }

            // Fetch all users and filter by sourceFetchedAt month (ISO-8601 string prefix "YYYY-MM")
            CompletableFuture<ArrayNode> usersFuture = entityService.getItems(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION)
            );

            ArrayNode usersArray = usersFuture.join();
            int total = 0;
            int validCount = 0;
            int invalidCount = 0;

            if (usersArray != null) {
                for (JsonNode node : usersArray) {
                    if (node == null || node.isNull()) continue;

                    // sourceFetchedAt stored as ISO-8601 in JSON; check prefix YYYY-MM
                    JsonNode fetchedAtNode = node.get("sourceFetchedAt");
                    String fetchedAtText = fetchedAtNode != null && !fetchedAtNode.isNull() ? fetchedAtNode.asText() : null;
                    if (fetchedAtText == null || !fetchedAtText.startsWith(month)) {
                        continue;
                    }

                    total++;
                    JsonNode statusNode = node.get("validationStatus");
                    String status = statusNode != null && !statusNode.isNull() ? statusNode.asText() : null;
                    if ("VALID".equalsIgnoreCase(status)) {
                        validCount++;
                    } else {
                        invalidCount++;
                    }
                }
            }

            // Ensure consistency: total == valid + invalid
            if (total != (validCount + invalidCount)) {
                // Adjust invalidCount to match totals if mismatch (defensive)
                invalidCount = total - validCount;
                if (invalidCount < 0) invalidCount = 0;
            }

            report.setTotalUsers(total);
            report.setNewUsers(validCount);
            report.setInvalidUsers(invalidCount);

            // Set generatedAt if not already set
            if (report.getGeneratedAt() == null || report.getGeneratedAt().isBlank()) {
                report.setGeneratedAt(Instant.now().toString());
            }

            report.setStatus("GENERATING");
            logger.info("Compiled metrics for month {}: total={}, new={}, invalid={}", month, total, validCount, invalidCount);

        } catch (Exception ex) {
            logger.error("Error compiling metrics for MonthlyReport: {}", ex.getMessage(), ex);
            report.setStatus("FAILED");
        }

        return report;
    }
}