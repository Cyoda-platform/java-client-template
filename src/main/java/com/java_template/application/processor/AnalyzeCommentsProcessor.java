package com.java_template.application.processor;

import com.java_template.application.entity.commentanalysisjob.version_1.CommentAnalysisJob;
import com.java_template.application.entity.comment.version_1.Comment;
import com.java_template.application.entity.analysisreport.version_1.AnalysisReport;
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

import static com.java_template.common.config.Config.*;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.concurrent.CompletionException;
import java.util.UUID;

@Component
public class AnalyzeCommentsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AnalyzeCommentsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public AnalyzeCommentsProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
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
        CommentAnalysisJob entity = context.entity();

        try {
            // Build search condition to find comments for this postId
            SearchConditionRequest condition = SearchConditionRequest.group(
                "AND",
                Condition.of("$.postId", "EQUALS", entity.getPostId())
            );

            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                Comment.ENTITY_NAME,
                String.valueOf(Comment.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode commentsArray = itemsFuture.join();

            int count = commentsArray == null ? 0 : commentsArray.size();
            int totalWords = 0;
            Map<String, Integer> wordFreq = new HashMap<>();
            Set<String> uniqueEmails = new HashSet<>();
            int positiveMatches = 0;
            int negativeMatches = 0;

            // simple sentiment word lists
            Set<String> positiveWords = new HashSet<>(Arrays.asList("good", "great", "excellent", "love", "like", "awesome", "nice", "happy", "pleasant"));
            Set<String> negativeWords = new HashSet<>(Arrays.asList("bad", "terrible", "hate", "awful", "sad", "angry", "poor", "worst", "problem"));

            if (commentsArray != null) {
                for (JsonNode node : commentsArray) {
                    String body = node.hasNonNull("body") ? node.get("body").asText("") : "";
                    String email = node.hasNonNull("email") ? node.get("email").asText("") : "";
                    if (!email.isBlank()) uniqueEmails.add(email);

                    if (!body.isBlank()) {
                        String[] tokens = body.split("\\W+");
                        int wordsInThis = 0;
                        for (String t : tokens) {
                            if (t == null) continue;
                            String w = t.trim().toLowerCase();
                            if (w.isEmpty()) continue;
                            wordsInThis++;
                            // update frequency map
                            wordFreq.put(w, wordFreq.getOrDefault(w, 0) + 1);
                            if (positiveWords.contains(w)) positiveMatches++;
                            if (negativeWords.contains(w)) negativeMatches++;
                        }
                        totalWords += wordsInThis;
                    }
                }
            }

            int avgLengthWords = count > 0 ? (totalWords / Math.max(1, count)) : 0;

            // compute top words (take top 5)
            List<String> topWords = wordFreq.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

            String sentimentSummary;
            if (positiveMatches > negativeMatches && positiveMatches > 0) sentimentSummary = "positive";
            else if (negativeMatches > positiveMatches && negativeMatches > 0) sentimentSummary = "negative";
            else sentimentSummary = "neutral";

            // Determine which metrics to include based on metricsConfig (if provided)
            Set<String> requestedMetrics = new HashSet<>();
            try {
                if (entity.getMetricsConfig() != null && entity.getMetricsConfig().getMetrics() != null) {
                    for (String m : entity.getMetricsConfig().getMetrics()) {
                        if (m != null && !m.isBlank()) requestedMetrics.add(m.trim().toLowerCase());
                    }
                }
            } catch (Exception ex) {
                // in case metricsConfig implementation changes; default to computing all
                requestedMetrics.clear();
            }

            boolean includeCount = requestedMetrics.isEmpty() || requestedMetrics.contains("count");
            boolean includeAvg = requestedMetrics.isEmpty() || requestedMetrics.contains("avg_length_words");
            boolean includeTopWords = requestedMetrics.isEmpty() || requestedMetrics.contains("top_words");
            boolean includeSentiment = requestedMetrics.isEmpty() || requestedMetrics.contains("sentiment_summary");
            boolean includeUniqueEmailsSummary = requestedMetrics.isEmpty() || requestedMetrics.contains("unique_emails");

            // Build AnalysisReport
            AnalysisReport report = new AnalysisReport();
            report.setReportId(UUID.randomUUID().toString());
            report.setJobId(entity.getId() != null ? entity.getId() : "");

            Integer postIdInt = 0;
            try {
                if (entity.getPostId() != null && !entity.getPostId().isBlank()) {
                    postIdInt = Integer.valueOf(entity.getPostId());
                } else if (commentsArray != null && commentsArray.size() > 0) {
                    JsonNode first = commentsArray.get(0);
                    if (first.hasNonNull("postId")) {
                        postIdInt = Integer.valueOf(first.get("postId").asText("0"));
                    }
                }
            } catch (Exception ex) {
                postIdInt = 0;
            }
            report.setPostId(postIdInt);

            report.setRecipientEmail(entity.getRecipientEmail());
            report.setGeneratedAt(Instant.now().toString());
            report.setSentAt(null);
            report.setStatus("GENERATED");

            String summary = String.format("%d comments analyzed. Unique emails: %d. Sentiment: %s. Top words: %s",
                count, uniqueEmails.size(), sentimentSummary, topWords.stream().collect(Collectors.joining(", ")));
            report.setSummary(summary);

            AnalysisReport.Metrics metrics = new AnalysisReport.Metrics();
            if (includeCount) metrics.setCount(count);
            else metrics.setCount(null);

            if (includeAvg) metrics.setAvgLengthWords(avgLengthWords);
            else metrics.setAvgLengthWords(null);

            if (includeSentiment) metrics.setSentimentSummary(sentimentSummary);
            else metrics.setSentimentSummary(null);

            if (includeTopWords) metrics.setTopWords(topWords);
            else metrics.setTopWords(Collections.emptyList());

            report.setMetrics(metrics);

            // Persist the report (add new entity)
            CompletableFuture<java.util.UUID> addFuture = entityService.addItem(
                AnalysisReport.ENTITY_NAME,
                String.valueOf(AnalysisReport.ENTITY_VERSION),
                report
            );
            // ensure add completes
            addFuture.join();

            // Update job status to READY_TO_SEND. Do NOT call entityService.updateItem on this entity.
            entity.setStatus("READY_TO_SEND");

            return entity;

        } catch (CompletionException ce) {
            logger.error("Error while analyzing comments for job {}: {}", entity.getId(), ce.getMessage(), ce);
            entity.setStatus("FAILED");
            entity.setCompletedAt(Instant.now().toString());
            return entity;
        } catch (Exception e) {
            logger.error("Unexpected error in AnalyzeCommentsProcessor for job {}: {}", entity.getId(), e.getMessage(), e);
            entity.setStatus("FAILED");
            entity.setCompletedAt(Instant.now().toString());
            return entity;
        }
    }
}