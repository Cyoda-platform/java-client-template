package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.dataanalysis.version_1.DataAnalysis;
import com.java_template.application.entity.emailnotification.version_1.EmailNotification;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * FormatEmailReportProcessor
 * Format analysis data into readable email content
 */
@Component
public class FormatEmailReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FormatEmailReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public FormatEmailReportProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailNotification formatting for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(EmailNotification.class)
                .validate(this::isValidEntityWithMetadata, "Invalid entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<EmailNotification> entityWithMetadata) {
        EmailNotification entity = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic processing method
     */
    private EntityWithMetadata<EmailNotification> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EmailNotification> context) {

        EntityWithMetadata<EmailNotification> entityWithMetadata = context.entityResponse();
        EmailNotification entity = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing EmailNotification formatting: {} in state: {}", entity.getNotificationId(), currentState);

        try {
            // Get the analysis data
            DataAnalysis analysis = getDataAnalysis(entity.getAnalysisId());
            
            // Format the email content
            String emailBody = formatEmailReport(analysis);
            String emailSubject = "London Housing Market Analysis Report - " + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            // Update the email notification entity
            entity.setEmailSubject(emailSubject);
            entity.setEmailBody(emailBody);
            entity.setUpdatedAt(LocalDateTime.now());
            
            logger.info("EmailNotification {} formatting completed successfully", entity.getNotificationId());
            
        } catch (Exception e) {
            logger.error("Failed to format email for EmailNotification: {}", entity.getNotificationId(), e);
            throw new RuntimeException("Email formatting failed: " + e.getMessage(), e);
        }

        return entityWithMetadata;
    }

    /**
     * Get the data analysis by business ID
     */
    private DataAnalysis getDataAnalysis(String analysisId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(DataAnalysis.ENTITY_NAME).withVersion(DataAnalysis.ENTITY_VERSION);
            
            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.analysisId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(analysisId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<DataAnalysis>> analyses = entityService.search(modelSpec, condition, DataAnalysis.class);
            
            if (analyses.isEmpty()) {
                throw new RuntimeException("DataAnalysis not found for analysisId: " + analysisId);
            }

            return analyses.get(0).entity();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to get DataAnalysis: " + e.getMessage(), e);
        }
    }

    /**
     * Format analysis data into readable email content
     */
    private String formatEmailReport(DataAnalysis analysis) {
        try {
            StringBuilder emailBody = new StringBuilder();
            
            emailBody.append("London Housing Market Analysis Report\n");
            emailBody.append("=====================================\n\n");
            
            if (analysis.getReportData() != null && !analysis.getReportData().trim().isEmpty()) {
                JsonNode reportData = objectMapper.readTree(analysis.getReportData());
                
                // Summary section
                emailBody.append("Summary:\n");
                emailBody.append("--------\n");
                
                if (reportData.has("totalRecords")) {
                    emailBody.append("• Total Properties Analyzed: ").append(reportData.get("totalRecords").asText()).append("\n");
                }
                
                if (reportData.has("averagePrice")) {
                    emailBody.append("• Average Price: £").append(String.format("%.2f", reportData.get("averagePrice").asDouble())).append("\n");
                }
                
                if (reportData.has("minPrice") && reportData.has("maxPrice")) {
                    emailBody.append("• Price Range: £").append(String.format("%.2f", reportData.get("minPrice").asDouble()))
                             .append(" - £").append(String.format("%.2f", reportData.get("maxPrice").asDouble())).append("\n");
                }
                
                if (reportData.has("medianPrice")) {
                    emailBody.append("• Median Price: £").append(String.format("%.2f", reportData.get("medianPrice").asDouble())).append("\n");
                }
                
                emailBody.append("\n");
                
                // Top areas section
                if (reportData.has("topAreas")) {
                    emailBody.append("Top Areas by Property Count:\n");
                    emailBody.append("----------------------------\n");
                    
                    JsonNode topAreas = reportData.get("topAreas");
                    if (topAreas.isArray()) {
                        for (JsonNode area : topAreas) {
                            if (area.has("area") && area.has("count")) {
                                emailBody.append("• ").append(area.get("area").asText())
                                         .append(": ").append(area.get("count").asText()).append(" properties\n");
                            }
                        }
                    }
                    emailBody.append("\n");
                }
                
                // Price by bedrooms section
                if (reportData.has("priceByBedrooms")) {
                    emailBody.append("Average Price by Bedrooms:\n");
                    emailBody.append("-------------------------\n");
                    
                    JsonNode priceByBedrooms = reportData.get("priceByBedrooms");
                    if (priceByBedrooms.isObject()) {
                        priceByBedrooms.fields().forEachRemaining(entry -> {
                            String bedrooms = entry.getKey();
                            double avgPrice = entry.getValue().asDouble();
                            emailBody.append("• ").append(bedrooms).append(" bedroom(s): £")
                                     .append(String.format("%.2f", avgPrice)).append("\n");
                        });
                    }
                    emailBody.append("\n");
                }
                
            } else {
                emailBody.append("No analysis data available.\n\n");
            }
            
            // Footer
            emailBody.append("Generated on: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
            emailBody.append("\nThis is an automated report from the Cyoda Housing Analysis System.");
            
            return emailBody.toString();
            
        } catch (Exception e) {
            logger.error("Failed to format email report", e);
            return "Error formatting report: " + e.getMessage();
        }
    }
}
