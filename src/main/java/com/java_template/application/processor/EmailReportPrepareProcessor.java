package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.emailreport.version_1.EmailReport;
import com.java_template.application.entity.commentanalysis.version_1.CommentAnalysis;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class EmailReportPrepareProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailReportPrepareProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EmailReportPrepareProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailReport prepare for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(EmailReport.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(EmailReport entity) {
        return entity != null && entity.isValid();
    }

    private EmailReport processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<EmailReport> context) {
        EmailReport entity = context.entity();

        try {
            // Get CommentAnalysis entity by requestId
            Condition requestIdCondition = Condition.of("$.requestId", "EQUALS", entity.getRequestId());
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(requestIdCondition));

            Optional<com.java_template.common.dto.EntityResponse<CommentAnalysis>> analysisResponse = 
                entityService.getFirstItemByCondition(
                    CommentAnalysis.class, 
                    CommentAnalysis.ENTITY_NAME, 
                    CommentAnalysis.ENTITY_VERSION, 
                    condition, 
                    true
                );

            if (!analysisResponse.isPresent()) {
                throw new RuntimeException("CommentAnalysis not found for requestId: " + entity.getRequestId());
            }

            CommentAnalysis analysis = analysisResponse.get().getData();

            // Generate email subject
            entity.setSubject("Comment Analysis Report for Post " + getPostIdFromAnalysis(analysis));

            // Create HTML email content
            String htmlContent = generateHtmlContent(analysis);
            entity.setHtmlContent(htmlContent);

            // Create plain text version
            String textContent = generateTextContent(analysis);
            entity.setTextContent(textContent);

            logger.info("Prepared email content for EmailReport with reportId: {} and requestId: {}", 
                       entity.getReportId(), entity.getRequestId());

        } catch (Exception e) {
            logger.error("Failed to prepare email content for requestId: {}", entity.getRequestId(), e);
            throw new RuntimeException("Failed to prepare email content: " + e.getMessage(), e);
        }

        return entity;
    }

    private String getPostIdFromAnalysis(CommentAnalysis analysis) {
        // Since we don't have postId in CommentAnalysis, we'll extract it from the first comment
        // For now, we'll use a placeholder
        return "N/A";
    }

    private String generateHtmlContent(CommentAnalysis analysis) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Comment Analysis Report</title></head><body>");
        html.append("<h1>Comment Analysis Report</h1>");
        
        // Header with analysis summary
        html.append("<h2>Analysis Summary</h2>");
        html.append("<p>Analysis completed at: ").append(analysis.getAnalysisCompletedAt()).append("</p>");
        
        // Table with metrics
        html.append("<h2>Metrics</h2>");
        html.append("<table border='1' style='border-collapse: collapse;'>");
        html.append("<tr><th>Metric</th><th>Value</th></tr>");
        html.append("<tr><td>Total Comments</td><td>").append(analysis.getTotalComments()).append("</td></tr>");
        html.append("<tr><td>Average Comment Length</td><td>").append(String.format("%.2f", analysis.getAverageCommentLength())).append("</td></tr>");
        html.append("<tr><td>Unique Authors</td><td>").append(analysis.getUniqueAuthors()).append("</td></tr>");
        html.append("</table>");
        
        // Top keywords section
        html.append("<h2>Top Keywords</h2>");
        html.append("<p>").append(formatKeywords(analysis.getTopKeywords())).append("</p>");
        
        // Sentiment analysis section
        html.append("<h2>Sentiment Analysis</h2>");
        html.append("<p>").append(analysis.getSentimentSummary()).append("</p>");
        
        // Footer with timestamp
        html.append("<hr>");
        html.append("<p><small>Report generated on ").append(java.time.LocalDateTime.now()).append("</small></p>");
        html.append("</body></html>");
        
        return html.toString();
    }

    private String generateTextContent(CommentAnalysis analysis) {
        StringBuilder text = new StringBuilder();
        text.append("COMMENT ANALYSIS REPORT\n");
        text.append("======================\n\n");
        
        text.append("Analysis Summary:\n");
        text.append("Analysis completed at: ").append(analysis.getAnalysisCompletedAt()).append("\n\n");
        
        text.append("Metrics:\n");
        text.append("- Total Comments: ").append(analysis.getTotalComments()).append("\n");
        text.append("- Average Comment Length: ").append(String.format("%.2f", analysis.getAverageCommentLength())).append("\n");
        text.append("- Unique Authors: ").append(analysis.getUniqueAuthors()).append("\n\n");
        
        text.append("Top Keywords:\n");
        text.append(formatKeywords(analysis.getTopKeywords())).append("\n\n");
        
        text.append("Sentiment Analysis:\n");
        text.append(analysis.getSentimentSummary()).append("\n\n");
        
        text.append("---\n");
        text.append("Report generated on ").append(java.time.LocalDateTime.now()).append("\n");
        
        return text.toString();
    }

    private String formatKeywords(String topKeywordsJson) {
        try {
            JsonNode keywordsNode = objectMapper.readTree(topKeywordsJson);
            StringBuilder formatted = new StringBuilder();
            keywordsNode.fields().forEachRemaining(entry -> {
                if (formatted.length() > 0) formatted.append(", ");
                formatted.append(entry.getKey()).append(" (").append(entry.getValue().asInt()).append(")");
            });
            return formatted.toString();
        } catch (Exception e) {
            logger.warn("Failed to format keywords JSON: {}", topKeywordsJson, e);
            return topKeywordsJson;
        }
    }
}
