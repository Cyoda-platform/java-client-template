package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.analysisreport.version_1.AnalysisReport;
import com.java_template.application.entity.comment.version_1.Comment;
import com.java_template.application.entity.commentanalysisjob.version_1.CommentAnalysisJob;
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
import java.util.stream.Collectors;

@Component
public class GenerateReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GenerateReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public GenerateReportProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CommentAnalysisJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(CommentAnalysisJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CommentAnalysisJob entity) {
        return entity != null && entity.isValid();
    }

    private CommentAnalysisJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CommentAnalysisJob> context) {
        CommentAnalysisJob job = context.entity();

        try {
            // Build condition to find comments linked to this job's postId
            SearchConditionRequest condition = SearchConditionRequest.group(
                "AND",
                Condition.of("$.postId", "EQUALS", job.getPostId())
            );

            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                Comment.ENTITY_NAME,
                String.valueOf(Comment.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode items = itemsFuture.join();
            List<Comment> comments = new ArrayList<>();
            if (items != null) {
                for (JsonNode node : items) {
                    try {
                        Comment c = objectMapper.treeToValue(node, Comment.class);
                        if (c != null && c.isValid()) {
                            comments.add(c);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to deserialize comment node: {}", e.getMessage());
                    }
                }
            }

            // Compute metrics
            AnalysisReport.Metrics metrics = new AnalysisReport.Metrics();
            int count = comments.size();
            metrics.setCount(count);

            int avgLen = 0;
            Map<String, Integer> wordFreq = new HashMap<>();
            Set<String> stopwords = new HashSet<>(Arrays.asList(
                "the","and","a","an","of","to","is","in","that","it","for","on","with","as","this","was","are","be"
            ));
            int positiveScore = 0;
            int negativeScore = 0;
            Set<String> positive = new HashSet<>(Arrays.asList("good","great","excellent","happy","love","like","awesome","nice"));
            Set<String> negative = new HashSet<>(Arrays.asList("bad","terrible","hate","awful","sad","angry","poor","worst"));

            int totalWords = 0;
            for (Comment c : comments) {
                if (c.getBody() == null) continue;
                String[] words = c.getBody().replaceAll("[^A-Za-z0-9\\s]", " ").toLowerCase().split("\\s+");
                int wordsCount = 0;
                for (String w : words) {
                    if (w == null || w.isBlank()) continue;
                    wordsCount++;
                    totalWords++;
                    if (!stopwords.contains(w)) {
                        wordFreq.put(w, wordFreq.getOrDefault(w, 0) + 1);
                    }
                    if (positive.contains(w)) positiveScore++;
                    if (negative.contains(w)) negativeScore++;
                }
            }
            if (count > 0) {
                avgLen = totalWords > 0 ? Math.round((float) totalWords / count) : 0;
            } else {
                avgLen = 0;
            }
            metrics.setAvgLengthWords(avgLen);

            // Top words
            List<String> topWords = wordFreq.entrySet()
                .stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
            metrics.setTopWords(topWords);

            // Sentiment summary
            String sentiment = "neutral";
            if (positiveScore > negativeScore) sentiment = "positive";
            else if (negativeScore > positiveScore) sentiment = "negative";
            metrics.setSentimentSummary(sentiment);

            // Build report
            AnalysisReport report = new AnalysisReport();
            report.setReportId(UUID.randomUUID().toString());
            report.setJobId(job.getId());
            // Try to parse numeric post id, fallback to 0 to satisfy non-null postId requirement
            try {
                report.setPostId(Integer.parseInt(job.getPostId()));
            } catch (Exception ex) {
                report.setPostId(0);
            }
            report.setRecipientEmail(job.getRecipientEmail());
            report.setGeneratedAt(Instant.now().toString());
            report.setStatus("GENERATED");
            report.setSummary(String.format("%d comments analyzed. Sentiment: %s.", count, sentiment));
            report.setMetrics(metrics);

            // Persist report as a separate entity
            CompletableFuture<java.util.UUID> addFuture = entityService.addItem(
                AnalysisReport.ENTITY_NAME,
                String.valueOf(AnalysisReport.ENTITY_VERSION),
                report
            );
            addFuture.join();

            // Transition job to SENDING so downstream processors can pick it up
            job.setStatus("SENDING");

        } catch (Exception ex) {
            logger.error("Failed to generate report for job {}: {}", job.getId(), ex.getMessage(), ex);
            job.setStatus("FAILED");
        }

        return job;
    }
}